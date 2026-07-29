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
 * Full-stack test for the compiler-generated riscv-1x16-coexecute program
 * on the larger systolic-array configuration (Configuration.large16x16():
 * 16-wide array, 64-byte/512-bit scratchpad bus, 32-bit accumulator,
 * 128-bit AXI-Stream, MemSystemConfig.large() tiers) --
 *   - test/riscv-1x16-coexecute.eaac (accelerator instructions, no preloads)
 *   - test/riscv-1x16-coexecute/riscv-1x16-coexecute.ll (two chained mcc
 *     kernels: relu on the i32 accumulator, then requantize to i8)
 *   - test/riscv-1x16-coexecute.reference.json (golden inputs/outputs)
 *
 * Unlike Riscv16x16CoexecuteE2ETest, this matmul is *not* square: arg0 (X)
 * is 1x16 and arg1 (Y) is 16x16, producing a 1x16 output. The contraction
 * width (16, matching arrayDim) and output row count (1) differ here, so
 * the two operands need different row counts fed in -- X contributes a
 * single row, Y still contributes all 16 rows. mcc relu's and requantizes
 * the accumulator down to i8, handing the result back via a second
 * semaphore for the accelerator to stream out.
 *
 * axiStreamWidth (128 bits = 16 bytes) exactly matches one array row. Unlike
 * the single-preloaded-weight tests, both matmul operands here are real,
 * runtime-loaded tensors, so the feeding convention differs: arg0 (X) is fed
 * in natural row order and arg1 (Y) is fed row-reversed (see feed loop
 * below), matching the convention confirmed in Riscv16x16CoexecuteE2ETest.
 */
class Riscv1x16CoexecuteE2ETest extends AnyFreeSpec with Matchers with ChiselSim {

  val mRows     = 1  // arg0 (X) / output row count
  val n         = 16 // contraction width == arg1 (Y) row count == column width
  val maxCycles = 200000

  val testConfig: Configuration = Configuration.large16x16()
    .withBus(_.copy(sourceWidth = 8))
    .withSemaphore(_.copy(generationWidth = 2))
    .copy(riscv = MccParams(enabled = true))

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

  // One axiStreamWidth-wide AXI word == one full array row here (128 bits
  // = 16 bytes = n elements), matching AssemblerE2ETest's proven pattern.
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

  "accelerator + mcc co-execute the compiler-generated 16x16-config program and match the reference output" in {
    val eaacPath = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/riscv-1x16-coexecute.eaac"
    val hexPath  = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/riscv-1x16-coexecute/riscv-1x16-coexecute.memhex"

    val initData = mcc.MccHexReader(hexPath)

    val asm = new Assembler(AssemblerConfig(
      semaphoreGenerationWidth = testConfig.semaphoreGenerationWidth,
    ))
    val assembled = asm.assemble(ByteBuffer.wrap(Files.readAllBytes(Paths.get(eaacPath))))
    val fn = assembled.functions.head

    ////////// Reference import //////////

    case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
    case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

    val jsonStr = Source.fromFile("/home/karlhk/dtu/Thesis/hardware/ATAN/test/riscv-1x16-coexecute.reference.json").mkString
    val parsed = for {
      json  <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    val inputArrays: Seq[Seq[Int]]  = parsed.toOption.get.inputs.map(_.data)
    val outputArrays: Seq[Seq[Int]] = parsed.toOption.get.outputs.map(_.data)

    require(fn.preloads.isEmpty, s"unexpected preloads: ${fn.preloads}")

    simulate(new AtanMcc16x16DUT(testConfig, memCfgBase = msCfg, mccInitData = initData)) { dut =>
      totalCycles = 0L

      dut.reset.poke(true.B)
      step(dut, 5)
      dut.reset.poke(false.B)

      fn.instructions.foreach(sendInst(dut, _))

      // arg0 (X/activation, mRows=1 row) is fed in natural row order;
      // arg1 (Y/weight, n=16 rows) is fed row-reversed to match the
      // systolic array's weight-stationary shift-in order. Confirmed
      // against the raw pre-mcc accumulator (buf2) in
      // Riscv16x16CoexecuteE2ETest, which then matches plain A @ B exactly
      // with no output-side reversal either.
      inputArrays.zipWithIndex.foreach { case (inputArr, argIdx) =>
        val reverseRows = argIdx == 1
        val numRows = if (argIdx == 0) mRows else n
        val rows = (0 until numRows).map { i =>
          val srcRow = if (reverseRows) (numRows - 1) - i else i
          val rowData = (0 until n).map(col => inputArr(srcRow * n + col))
          packRow(rowData)
        }
        feedLoadData(dut, rows)
      }

      waitFor(dut)(dut.io.mccInitDone.peek().litToBoolean, "mcc imem init done")
      info(s"mcc imem loaded after $totalCycles cycles")

      val outputRows = collectStoreData(dut, mRows)
      info(s"total cycles: $totalCycles")

      for (row <- 0 until mRows) {
        val got = unpackRow(outputRows(row))
        for (col <- 0 until n) {
          val exp = outputArrays(0)(row * n + col)
          assert(got(col) == exp, s"Mismatch at ($row,$col): got ${got(col)}, expected $exp")
        }
      }
    }
  }
}
