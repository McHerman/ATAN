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
 * Config 3 growth step: the real AD-model bottleneck-*expansion* layer
 * (op index 5, 8 -> 128, padded 16 -> 128), M=16 rows. K_TILES=1 (a single
 * K-tile, no cross-tile accumulation) x N_TILES=8 -- 8 independent
 * load->matmul->relu->requantize->store units sharing one streamed X tile,
 * deliberately chosen (see EAAC_Benchmark/gen/build_ad_bottleneck_8tile.py)
 * to keep peak concurrent semaphore liveness low enough to fit the
 * project's 16-physical-semaphore ceiling -- unlike the 32-tile
 * (8 K-tile x 4 N-tile, full accumulation + wide fan-out) attempt, which
 * couldn't be allocated even with 256 logical (address, generation) slots.
 *
 * Real trained weights + real quantization params (M0, shift, zero-point)
 * from ad01_int8.tflite; X is fixed-seed random (no real captured
 * activation available for this hidden layer in this environment). No
 * preloads -- every operand (X, 8 W tiles) is a runtime-streamed function
 * argument, same design choice as Ad16x16RealE2ETest.
 *
 * Uses AdInt8SingleLayerE2ETest's concurrent instruction/data/output
 * interleaving (runProgram) rather than the simpler sequential
 * "send all instructions, then feed all data" pattern: that document
 * pattern deadlocks once the front-end's single-entry Dispatch latch holds
 * a Load waiting on data that hasn't been sent yet, which is much likelier
 * to bite here than in the single-tile tests given eight independent load
 * groups.
 */
class AdBottleneck8TileE2ETest extends AnyFreeSpec with Matchers with ChiselSim {

  val n         = 16
  val maxCycles = 200000

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

  // Concurrent instruction/data/output driving, adapted from
  // AdInt8SingleLayerE2ETest.runProgram but generalized to multi-beat
  // (16-beat, full 16x16) tiles instead of single-beat (1x16) tiles: each
  // element of `inputRows` is one beat, with tlast asserted per the
  // `tlastAt` predicate (true at the last beat of each individual tile,
  // not just the very last beat of the whole stream).
  def runProgram(
    dut: AtanMcc16x16DUT,
    instructions: IndexedSeq[BigInt],
    inputRows: IndexedSeq[BigInt],
    tlastAt: Int => Boolean,
    nOutputBeats: Int,
  ): IndexedSeq[BigInt] = {
    val allOnesData = (BigInt(1) << (testConfig.axiStreamWidth / 8)) - 1

    var instIdx = 0
    var dataIdx = 0
    val outputs = scala.collection.mutable.ArrayBuffer[BigInt]()

    def armInst(idx: Int): Unit = {
      dut.io.AXIST_inInst.tdata.poke(instructions(idx).U(128.W))
      dut.io.AXIST_inInst.tvalid.poke(true.B)
      dut.io.AXIST_inInst.tkeep.poke("hffff".U)
      dut.io.AXIST_inInst.tstrb.poke("hffff".U)
    }
    def armData(idx: Int): Unit = {
      dut.io.AXIST_inData.tdata.poke(inputRows(idx).U(testConfig.axiStreamWidth.W))
      dut.io.AXIST_inData.tstrb.poke(allOnesData.U)
      dut.io.AXIST_inData.tkeep.poke(allOnesData.U)
      dut.io.AXIST_inData.tvalid.poke(true.B)
      dut.io.AXIST_inData.tlast.poke(tlastAt(idx).B)
    }

    if (instructions.nonEmpty) armInst(0) else dut.io.AXIST_inInst.tvalid.poke(false.B)
    if (inputRows.nonEmpty) armData(0) else dut.io.AXIST_inData.tvalid.poke(false.B)
    dut.io.AXIST_out.tready.poke(true.B)

    var cycles = 0
    while (instIdx < instructions.length || dataIdx < inputRows.length || outputs.length < nOutputBeats) {
      require(cycles < maxCycles,
        s"Timeout: instIdx=$instIdx/${instructions.length} dataIdx=$dataIdx/${inputRows.length} " +
          s"outputs=${outputs.length}/$nOutputBeats after $cycles cycles ($totalCycles total)")

      val instFire = instIdx < instructions.length && dut.io.AXIST_inInst.tready.peek().litToBoolean
      val dataFire = dataIdx < inputRows.length && dut.io.AXIST_inData.tready.peek().litToBoolean
      val outFire  = outputs.length < nOutputBeats && dut.io.AXIST_out.tvalid.peek().litToBoolean
      if (outFire) outputs += dut.io.AXIST_out.tdata.peek().litValue

      step(dut)
      cycles += 1

      if (instFire) {
        instIdx += 1
        if (instIdx < instructions.length) armInst(instIdx) else dut.io.AXIST_inInst.tvalid.poke(false.B)
      }
      if (dataFire) {
        dataIdx += 1
        if (dataIdx < inputRows.length) armData(dataIdx)
        else { dut.io.AXIST_inData.tvalid.poke(false.B); dut.io.AXIST_inData.tlast.poke(false.B) }
      }
    }
    dut.io.AXIST_out.tready.poke(false.B)
    outputs.toIndexedSeq
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

    // Flatten every input tile into beats: inputArrays(0) is X (natural row
    // order), inputArrays(1..8) are the 8 W tiles (row-reversed per tile,
    // matching the established weight-stationary feed convention). tlast
    // fires on the last (16th) beat of each individual tile.
    val inputRows = inputArrays.zipWithIndex.flatMap { case (arr, argIdx) =>
      val reverseRows = argIdx >= 1
      (0 until n).map { i =>
        val srcRow = if (reverseRows) (n - 1) - i else i
        packRow((0 until n).map(col => arr(srcRow * n + col)))
      }
    }.toIndexedSeq
    def tlastAt(beatIdx: Int): Boolean = (beatIdx + 1) % n == 0

    simulate(new AtanMcc16x16DUT(testConfig, memCfgBase = msCfg, mccInitData = initData)) { dut =>
      totalCycles = 0L

      dut.reset.poke(true.B)
      step(dut, 5)
      dut.reset.poke(false.B)

      val outputBeats = runProgram(
        dut,
        fn.instructions.toIndexedSeq,
        inputRows,
        tlastAt,
        outputArrays.length * n,
      )
      info(s"total cycles: $totalCycles")

      // Per-tile (row x col) error matrix: each cell is (got - expected), so 0
      // reads as a match and anything else shows both that a cell is wrong and
      // by how much (sign/magnitude can hint at e.g. off-by-one-row vs. garbage).
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
