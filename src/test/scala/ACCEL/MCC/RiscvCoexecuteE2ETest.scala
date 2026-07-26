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
 * Full-stack test for the compiler-generated riscv-coexecute-test program:
 * the GEMM accelerator and mcc run compiler output as-is (no hand-authored
 * EAAC program, LLVM IR, or reference data) --
 *   - test/riscv-coexecute-test.eaac (accelerator instructions/preloads)
 *   - src/test/scala/ACCEL/MCC/riscv-coexecute-test.ll (mcc kernel source)
 *   - test/riscv-coexecute-test.reference.json (golden inputs/outputs)
 * mirroring AssemblerE2ETest's "End-to-end with arguments" harness: both
 * reference inputs are streamed in via AXIST_inData in order, and the
 * collected AXIST_out beats are compared against the reference's outputs.
 */
class RiscvCoexecuteE2ETest extends AnyFreeSpec with Matchers with ChiselSim {

  val n         = 8
  val maxCycles = 200000

  val testConfig: Configuration = Configuration.default()
    .withBus(_.copy(sourceWidth = 8))
    .withSemaphore(_.copy(nSemaphores = 16, generationWidth = 2))
    .copy(riscv = MccParams(enabled = true))

  val msCfg = MemSystemConfig.default().copy(
    sourceWidth = testConfig.sourceWidth,
    semGenWidth = testConfig.semaphoreGenerationWidth,
  )

  def packRow(elements: Seq[Int]): BigInt =
    elements.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (elem, i)) =>
      acc | (BigInt(elem & 0xFF) << (i * 8))
    }

  def unpackRow(value: BigInt): Seq[Int] =
    (0 until n).map(i => ((value >> (i * 8)) & 0xFF).toInt)

  var totalCycles: Long = 0L

  def step(dut: AtanMccDUT, k: Int = 1): Unit = {
    for (_ <- 0 until k) {
      dut.clock.step(1)
      totalCycles += 1
    }
  }

  def waitFor(dut: AtanMccDUT)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      step(dut)
      cycles += 1
    }
  }

  def sendInst(dut: AtanMccDUT, raw: BigInt): Unit = {
    dut.io.AXIST_inInst.tdata.poke(raw.U(128.W))
    dut.io.AXIST_inInst.tvalid.poke(true.B)
    dut.io.AXIST_inInst.tkeep.poke("hffff".U)
    dut.io.AXIST_inInst.tstrb.poke("hffff".U)
    waitFor(dut)(dut.io.AXIST_inInst.tready.peek().litToBoolean, "AXIST_inInst.tready")
    step(dut)
    dut.io.AXIST_inInst.tvalid.poke(false.B)
  }

  def feedLoadData(dut: AtanMccDUT, rows: Seq[BigInt]): Unit = {
    for ((row, i) <- rows.zipWithIndex) {
      dut.io.AXIST_inData.tdata.poke(row.U(64.W))
      dut.io.AXIST_inData.tstrb.poke("hff".U)
      dut.io.AXIST_inData.tkeep.poke("hff".U)
      dut.io.AXIST_inData.tvalid.poke(true.B)
      dut.io.AXIST_inData.tlast.poke((i == rows.length - 1).B)
      var c = 0
      do {
        require(c < maxCycles, s"Timeout waiting for AXIST_inData.tready beat $i")
        step(dut)
        c += 1
      } while (!dut.io.AXIST_inData.tready.peek().litToBoolean)
    }
    dut.io.AXIST_inData.tvalid.poke(false.B)
    dut.io.AXIST_inData.tlast.poke(false.B)
  }

  def preloadMem(dut: AtanMccDUT, preload: Assembler#Preload): Unit = {
    val addr = preload.offsetAddress + msCfg.tierBases(preload.tier).toInt
    val beats: Seq[BigInt] = preload.data
      .grouped(msCfg.dataBusSize)
      .map(bytes => bytes.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (b, i)) => acc | (BigInt(b & 0xFF) << (i * 8)) })
      .toSeq
    val nBeats = beats.length
    dut.io.hostIn.d.ready.poke(true.B)
    for ((beat, i) <- beats.reverse.zipWithIndex) {
      dut.io.hostIn.a.bits.opcode.poke(0.U)
      dut.io.hostIn.a.bits.param.poke(0.U)
      dut.io.hostIn.a.bits.address.poke(addr.U)
      dut.io.hostIn.a.bits.size.poke((nBeats * msCfg.dataBusSize).U)
      dut.io.hostIn.a.bits.source.poke(0.U)
      dut.io.hostIn.a.bits.data.poke(beat.U)
      dut.io.hostIn.a.bits.mask.poke(0xFF.U)
      dut.io.hostIn.a.bits.corrupt.poke(0.U)
      dut.io.hostIn.a.valid.poke(true.B)
      waitFor(dut)(dut.io.hostIn.a.ready.peek().litToBoolean, s"hostIn.a.ready preload beat $i")
      step(dut)
    }
    dut.io.hostIn.a.valid.poke(false.B)
    waitFor(dut)(dut.io.hostIn.d.valid.peek().litToBoolean, "hostIn.d.valid")
    step(dut)
  }

  def collectStoreData(dut: AtanMccDUT, nRows: Int): Seq[BigInt] = {
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

  "accelerator + mcc co-execute the compiler-generated program and match the reference output" in {
    val eaacPath = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/riscv-coexecute-test.eaac"
    val hexPath  = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/riscv-coexecute-test/riscv-coexecute-test.memhex"

    val initData = mcc.MccHexReader(hexPath)

    val asm = new Assembler(AssemblerConfig(
      dataBusBytes             = testConfig.dataBusSize,
      semaphoreGenerationWidth = testConfig.semaphoreGenerationWidth,
    ))
    val assembled = asm.assemble(ByteBuffer.wrap(Files.readAllBytes(Paths.get(eaacPath))))
    val fn = assembled.functions.head

    ////////// Reference import //////////

    case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
    case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

    val jsonStr = Source.fromFile("/home/karlhk/dtu/Thesis/hardware/ATAN/test/riscv-coexecute-test.reference.json").mkString
    val parsed = for {
      json  <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    val inputArrays: Seq[Seq[Int]]  = parsed.toOption.get.inputs.map(_.data)
    val outputArrays: Seq[Seq[Int]] = parsed.toOption.get.outputs.map(_.data)

    simulate(new AtanMccDUT(testConfig, mccInitData = initData)) { dut =>
      totalCycles = 0L

      dut.reset.poke(true.B)
      step(dut, 5)
      dut.reset.poke(false.B)

      fn.preloads.foreach(preloadMem(dut, _))
      fn.instructions.foreach(sendInst(dut, _))

      inputArrays.foreach { inputArr =>
        val rows = (0 until n).map { i =>
          val rowData = (0 until n).map(col => inputArr(((n - 1) - i) * n + col))
          packRow(rowData)
        }
        feedLoadData(dut, rows)
      }

      waitFor(dut)(dut.io.mccInitDone.peek().litToBoolean, "mcc imem init done")
      info(s"mcc imem loaded after $totalCycles cycles")

      val outputRows = collectStoreData(dut, n)
      info(s"total cycles: $totalCycles")

      for (row <- 0 until n) {
        val got = unpackRow(outputRows(row))
        for (col <- 0 until n) {
          val exp = outputArrays(0)(((n - 1) - row) * n + col)
          assert(got(col) == exp, s"Mismatch at ($row,$col): got ${got(col)}, expected $exp")
        }
      }
    }
  }
}
