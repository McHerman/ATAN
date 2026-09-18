package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import eaac.assembler.{Assembler, AssemblerConfig}
import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import scala.io.Source

import io.circe.generic.auto._
import io.circe.parser._

/**
 * Scaling probe for the full-16x16 ad_int8-shaped "unit of work" (K
 * independent 16x16@16x16 matmuls, reduced via a linear chain of K-1
 * elementwise adds, a bias add, ReLU, then requantize -- see
 * eaac-dialect/tools/gen_unit16.py). Each K gets its own permanent
 * test/scaling_kN.{mlir,eaac,memhex,reference.json} artifact set and its
 * own named test case here, rather than one file/case reused in place, so
 * every data point stays reproducible. tier0 is held fixed; only K (and
 * tier1/tier2) vary between artifact sets.
 */
class ScalingTest extends AnyFreeSpec with Matchers with ChiselSim {

  val n         = 16
  val maxCycles = 200000 

  val testConfig: Configuration = Configuration.large16x16()
    .withBus(_.copy(sourceWidth = 8))
    .withSemaphore(_.copy(nSemaphores = 32, generationWidth = 2, queueSize = 256))
    .withTrigger(_.copy(rows = 16))
    .copy(riscv = MccParams(enabled = true, imemWords = 16384, semBase = 65536))

  val msCfg = MemSystemConfig.extra_large().copy(
    dataBusSize = testConfig.dataBusSize,
    addrWidth   = testConfig.addrWidth,
    sourceWidth = testConfig.sourceWidth,
    semGenWidth = testConfig.semaphoreGenerationWidth,
  )

  def packRow(elements: Seq[Int]): BigInt =
    elements.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (elem, i)) => acc | (BigInt(elem & 0xFF) << (i * 8)) }

  def elemBitsOf(elementType: String): Int = elementType.toLowerCase match {
    case "i8"  => 8
    case "i16" => 16
    case "i32" => 32
    case other => throw new IllegalArgumentException(s"unsupported element_type: $other")
  }

  def unpackBeat(value: BigInt, elemBits: Int): Seq[Int] = {
    val elemsPerBeat = testConfig.axiStreamWidth / elemBits
    val mask = (BigInt(1) << elemBits) - 1
    val sign = BigInt(1) << (elemBits - 1)
    (0 until elemsPerBeat).map { i =>
      val v = (value >> (i * elemBits)) & mask
      val signed = elemBits > 8 && v >= sign
      (if (signed) v - (mask + 1) else v).toInt
    }
  }

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

  def stepN(clock: chisel3.Clock, n: Int = 1): Unit = {
    clock.step(n)
    totalCycles += n
  }

  def waitFor(clock: chisel3.Clock)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      clock.step()
      cycles += 1
    }
  }

  def preloadMem(dut: AtanMcc16x16DUT, preload: Assembler#Preload, msCfg: MemSystemConfig): Unit = {
    print(s"preloadTier ${preload.tier} \n")

    val addr = preload.offsetAddress + msCfg.tierBases(preload.tier).toInt

    val beats: Seq[BigInt] = preload.data
      .grouped(msCfg.dataBusSize)
      .map { bytes =>
        bytes.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (b, i)) =>
          acc | (BigInt(b & 0xFF) << (i * 8))
        }
      }
      .toSeq

    print(f"Writing preload to address: ${preload.offsetAddress}, tier: ${msCfg.tierBases(preload.tier).toInt} \n")

    dut.io.hostIn.d.ready.poke(true.B)

    for ((beat, i) <- beats.zipWithIndex) {
      dut.io.hostIn.a.bits.opcode.poke(0.U) // PutFullData
      dut.io.hostIn.a.bits.param.poke(0.U)
      dut.io.hostIn.a.bits.address.poke(addr.U)
      dut.io.hostIn.a.bits.size.poke(preload.data.size.U)
      dut.io.hostIn.a.bits.source.poke(0.U)
      dut.io.hostIn.a.bits.data.poke(beat.U)
      dut.io.hostIn.a.bits.mask.poke(((BigInt(1) << msCfg.dataBusSize) - 1).U)
      dut.io.hostIn.a.bits.corrupt.poke(0.U)

      dut.io.hostIn.a.valid.poke(true.B)

      waitFor(dut.clock)(dut.io.hostIn.a.ready.peek().litToBoolean,
        s"hostIn.a.ready preload beat $i \n")
      stepN(dut.clock)
    }

    dut.io.hostIn.a.valid.poke(false.B)

    waitFor(dut.clock)(dut.io.hostIn.d.valid.peek().litToBoolean, "hostIn.d.valid")
    dut.io.hostIn.d.bits.opcode.expect(0.U) // AccessAck
    stepN(dut.clock)
  }

  /** Runs one named scaling artifact set (test/scaling_kN.*) end to end and
   * asserts zero output mismatches. `name` selects the artifact set
   * (e.g. "scaling_k8" -> test/scaling_k8.eaac, test/scaling_k8/scaling_k8.memhex,
   * test/scaling_k8.reference.json).
   */
  def runScalingCase(name: String): Unit = {
    val eaacPath = s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}.eaac"
    val hexPath  = s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.memhex"

    val initData = mcc.MccHexReader(hexPath)

    val asm = new Assembler(AssemblerConfig(
      semaphoreGenerationWidth = testConfig.semaphoreGenerationWidth,
    ))
    val assembled = asm.assemble(ByteBuffer.wrap(Files.readAllBytes(Paths.get(eaacPath))))
    val fn = assembled.functions.head

    case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
    case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

    val jsonStr = Source.fromFile(s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}.reference.json").mkString
    val parsed = for {
      json  <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    val inputArrays: Seq[Seq[Int]]  = parsed.toOption.get.inputs.map(_.data)
    val outputTensors                = parsed.toOption.get.outputs
    val outputArrays: Seq[Seq[Int]] = outputTensors.map(_.data)
    val outputElemBits: Seq[Int]    = outputTensors.map(t => elemBitsOf(t.element_type))
    val outputBeatsPerTile: Seq[Int] = outputElemBits.map(bits => (n * n * bits) / testConfig.axiStreamWidth)

    info(s"instructions: ${fn.instructions.length}, inputs: ${inputArrays.length}, outputs: ${outputArrays.length}")

    simulate(new AtanMcc16x16DUT(testConfig, memCfgBase = msCfg)) { dut =>
      totalCycles = 0L

      dut.reset.poke(true.B)
      step(dut, 5)
      dut.reset.poke(false.B)

      fn.preloads.foreach { preload =>
        preloadMem(dut, preload, msCfg)
      }
      println("Preload finished !!!")

      loadImem(dut, initData)

      waitFor(dut)(dut.io.mccInitDone.peek().litToBoolean, "mcc imem init done")
      println(s"mcc imem loaded after $totalCycles cycles")

      fn.instructions.foreach(sendInst(dut, _))
      println(s"instructions loaded after $totalCycles cycles")

      inputArrays.zipWithIndex.foreach { case (arr, argIdx) =>
        val rows = (0 until n).map { i =>
          packRow((0 until n).map(col => arr(i * n + col)))
        }
        feedLoadData(dut, rows)
      }
      info(s"input data loaded after $totalCycles cycles")

      val outputBeats = collectStoreData(dut, outputBeatsPerTile.sum)
      info(s"total cycles: $totalCycles")

      var totalMismatches = 0
      var beatOffset = 0
      for (tileIdx <- outputArrays.indices) {
        val elemBits  = outputElemBits(tileIdx)
        val nBeats    = outputBeatsPerTile(tileIdx)
        val tileBeats = outputBeats.slice(beatOffset, beatOffset + nBeats)
        beatOffset += nBeats
        val rows = tileBeats.flatMap(unpackBeat(_, elemBits)).grouped(n).toIndexedSeq
        val grid = Array.tabulate(n, n) { (row, col) =>
          val got = rows(row)(col)
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

  "K=8 unit of work" in {
    runScalingCase("scaling_k8")
  }

  "K=12 unit of work" in {
    runScalingCase("scaling_k12")
  }

  "K=14 unit of work" in {
    runScalingCase("scaling_k14")
  }

  "K=16 unit of work" in {
    runScalingCase("scaling_k16")
  }

  "K=24 unit of work" in {
    runScalingCase("scaling_k24")
  }

  /*
  "K=25 unit of work" in {
    runScalingCase("scaling_k25")
  }

  "K=32 unit of work" in {
    runScalingCase("scaling_k32")
  }
  */
}
