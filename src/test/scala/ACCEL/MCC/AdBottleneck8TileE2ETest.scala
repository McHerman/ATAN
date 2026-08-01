package ATA8

import chisel3._
import chisel3.util._
import chisel3.simulator.scalatest.ChiselSim
import eaac.assembler.{Assembler, AssemblerConfig}
import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import scala.io.Source

import io.circe.generic.auto._
import io.circe.parser._

class AdBottleneck8TileE2ETest extends AnyFreeSpec with Matchers with ChiselSim {

  val n         = 16
  val maxCycles = 200000
  //val maxCycles = 30000 

  val testConfig: Configuration = Configuration.large16x16()
    .withBus(_.copy(sourceWidth = 8))
    .withSemaphore(_.copy(nSemaphores = 16, generationWidth = 2, queueSize = 256))
    .withTrigger(_.copy(rows = 256))
    .copy(riscv = MccParams(enabled = true, imemWords = 16384))

  val msCfg = MemSystemConfig.large().copy(
    dataBusSize = testConfig.dataBusSize,
    addrWidth   = testConfig.addrWidth,
    sourceWidth = testConfig.sourceWidth,
    semGenWidth = testConfig.semaphoreGenerationWidth,
  )

  def packRow(elements: Seq[Int]): BigInt =
    elements.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (elem, i)) => acc | (BigInt(elem & 0xFF) << (i * 8)) }

  def unpackRow(value: BigInt): Seq[Int] =
    (0 until n).map(i => ((value >> (i * 8)) & 0xFF).toInt)

  var totalCycles: Long = 0L

  def step(dut: AtanMcc16x16DUT, k: Int = 1): Unit = {
    for (_ <- 0 until k) {
      dut.clock.step(1)
      totalCycles += 1
    }
  }

  def waitFor(dut: AtanMcc16x16DUT)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      step(dut)
      cycles += 1
    }
  }

  def sendInst(dut: AtanMcc16x16DUT, raw: BigInt): Unit = {
    dut.io.AXIST_inInst.tdata.poke(raw.U(128.W))
    dut.io.AXIST_inInst.tvalid.poke(true.B)
    dut.io.AXIST_inInst.tkeep.poke("hffff".U)
    dut.io.AXIST_inInst.tstrb.poke("hffff".U)
    waitFor(dut)(dut.io.AXIST_inInst.tready.peek().litToBoolean, "AXIST_inInst.tready")
    step(dut)
    dut.io.AXIST_inInst.tvalid.poke(false.B)
  }

  def feedLoadData(dut: AtanMcc16x16DUT, rows: Seq[BigInt]): Unit = {
    val allOnes = (BigInt(1) << (testConfig.axiStreamWidth / 8)) - 1
    for ((row, i) <- rows.zipWithIndex) {
      dut.io.AXIST_inData.tdata.poke(row.U(testConfig.axiStreamWidth.W))
      dut.io.AXIST_inData.tstrb.poke(allOnes.U)
      dut.io.AXIST_inData.tkeep.poke(allOnes.U)
      dut.io.AXIST_inData.tvalid.poke(true.B)
      dut.io.AXIST_inData.tlast.poke((i == rows.length - 1).B)
      var c = 0
      do {
        require(c < maxCycles, s"Timeout waiting for AXIST_inData.tready row $i")
        step(dut)
        c += 1
      } while (!dut.io.AXIST_inData.tready.peek().litToBoolean)
    }
    dut.io.AXIST_inData.tvalid.poke(false.B)
    dut.io.AXIST_inData.tlast.poke(false.B)
  }

  def loadImem(dut: AtanMcc16x16DUT, initData: Map[Int, BigInt]): Unit = {
    val InitDoneAddr = 0xF000L

    dut.io.hostInRiscV.d.ready.poke(true.B)
    dut.io.hostInRiscV.a.bits.opcode.poke(TilelinkOpcodes.PutFullData)
    dut.io.hostInRiscV.a.bits.param.poke(0.U)
    dut.io.hostInRiscV.a.bits.size.poke(4.U)
    dut.io.hostInRiscV.a.bits.source.poke(0.U)
    dut.io.hostInRiscV.a.bits.mask.poke("b1111".U)
    dut.io.hostInRiscV.a.bits.corrupt.poke(0.U)

    def put(addr: Long, data: BigInt): Unit = {
      dut.io.hostInRiscV.a.bits.address.poke(addr.U)
      dut.io.hostInRiscV.a.bits.data.poke(data.U(32.W))
      dut.io.hostInRiscV.a.valid.poke(true.B)
      waitFor(dut)(dut.io.hostInRiscV.a.ready.peek().litToBoolean, s"hostInRiscV.a.ready (addr=$addr)")
      step(dut)
      dut.io.hostInRiscV.a.valid.poke(false.B)
      waitFor(dut)(dut.io.hostInRiscV.d.valid.peek().litToBoolean, s"hostInRiscV.d.valid (addr=$addr)")
      step(dut)
    }

    for ((wordIdx, word) <- initData.toSeq.sortBy(_._1)) put(wordIdx.toLong * 4, word)
    put(InitDoneAddr, BigInt(1))
  }

  def collectStoreData(dut: AtanMcc16x16DUT, nRows: Int): Seq[BigInt] = {
    dut.io.AXIST_out.tready.poke(true.B)
    val collected = scala.collection.mutable.ArrayBuffer[BigInt]()
    while (collected.length < nRows) {
      waitFor(dut)(dut.io.AXIST_out.tvalid.peek().litToBoolean, s"AXIST_out.tvalid beat ${collected.length}")
      collected += dut.io.AXIST_out.tdata.peek().litValue
      step(dut)
    }
    dut.io.AXIST_out.tready.poke(false.B)
    collected.toSeq
  }

  "accelerator + mcc co-execute the real AD-model bottleneck-expansion layer (8 independent tiles) and match the golden reference" in {
    val eaacPath = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/ad-bottleneck-8tile.eaac"
    val hexPath  = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/ad-bottleneck-8tile/ad-bottleneck-8tile.memhex"

    val initData = mcc.MccHexReader(hexPath)

    val asm = new Assembler(AssemblerConfig(
      semaphoreGenerationWidth = testConfig.semaphoreGenerationWidth,
    ))
    val assembled = asm.assemble(ByteBuffer.wrap(Files.readAllBytes(Paths.get(eaacPath))))
    val fn = assembled.functions.head

    case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
    case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

    val jsonStr = Source.fromFile("/home/karlhk/dtu/Thesis/hardware/ATAN/test/ad-bottleneck-8tile.reference.json").mkString
    val parsed = for {
      json  <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    val inputArrays: Seq[Seq[Int]]  = parsed.toOption.get.inputs.map(_.data)
    val outputArrays: Seq[Seq[Int]] = parsed.toOption.get.outputs.map(_.data)

    require(fn.preloads.isEmpty, s"unexpected preloads: ${fn.preloads}")
    info(s"instructions: ${fn.instructions.length}, inputs: ${inputArrays.length}, outputs: ${outputArrays.length}")

    simulate(new AtanMcc16x16DUT(testConfig, memCfgBase = msCfg)) { dut =>
      totalCycles = 0L

      dut.reset.poke(true.B)
      step(dut, 5)
      dut.reset.poke(false.B)

      loadImem(dut, initData)

      waitFor(dut)(dut.io.mccInitDone.peek().litToBoolean, "mcc imem init done")
      info(s"mcc imem loaded after $totalCycles cycles")

      fn.instructions.foreach(sendInst(dut, _))
      info(s"instructions loaded after $totalCycles cycles")

      inputArrays.zipWithIndex.foreach { case (arr, argIdx) =>
        val reverseRows = argIdx >= 1
        val rows = (0 until n).map { i =>
          val srcRow = if (reverseRows) (n - 1) - i else i
          packRow((0 until n).map(col => arr(srcRow * n + col)))
        }
        feedLoadData(dut, rows)
      }
      info(s"input data loaded after $totalCycles cycles")


      val outputBeats = collectStoreData(dut, outputArrays.length * n)
      info(s"total cycles: $totalCycles")

      var totalMismatches = 0
      for (tileIdx <- outputArrays.indices) {
        val grid = Array.tabulate(n, n) { (row, col) =>
          val got = unpackRow(outputBeats(tileIdx * n + row))(col)
          val exp = outputArrays(tileIdx)(row * n + col)
          got - exp
        }
        val tileMismatches = grid.iterator.flatten.count(_ != 0)
        totalMismatches += tileMismatches
        if (tileMismatches > 0) {
          info(s"tile=$tileIdx: $tileMismatches/${n * n} mismatches (cell = got - expected, '.' = match)")
          for (row <- 0 until n) {
            val cells = (0 until n).map(col => if (grid(row)(col) == 0) "   ." else f"${grid(row)(col)}%4d").mkString(" ")
            info(f"  row $row%2d: $cells")
          }
        }
      }
      info(s"total mismatches: $totalMismatches / ${outputArrays.length * n * n}")
      assert(totalMismatches == 0, s"$totalMismatches mismatches found (see error matrices above)")
    }
  }
}
