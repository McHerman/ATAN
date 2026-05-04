package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import com.google.flatbuffers.FlatBufferBuilder
import eaac.assembler.{Assembler, AssemblerConfig, PrettyPrinter}
import eaac_fb._
import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import scala.io.Source

import io.circe.generic.auto._
import io.circe.parser._

/** End-to-end test: build a FlatBuffer program, assemble it into
  * 128-bit instructions, stream them into the ATA8 hardware, feed
  * input data, and verify the output matches a golden reference.
  */
class AssemblerE2ETest extends AnyFreeSpec with Matchers with ChiselSim {

  val n = 8
  val maxCycles = 5000

  // ── Test matrix (same as ATA8Test) ────────────────────────────────────

  val matrix: Array[Array[Int]] = Array.fill(n)(Array(1, 2, 3, 4, 1, 2, 3, 4))

  def matrixDotProduct(A: Array[Array[Int]], B: Array[Array[Int]]): Array[Array[Int]] =
    Array.tabulate(A.length, A.length) { (i, j) =>
      (0 until A.length).map(k => A(i)(k) * B(k)(j)).sum
    }

  def packRow(elements: Seq[Int]): BigInt =
    elements.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (elem, i)) =>
      acc | (BigInt(elem & 0xFF) << (i * 8))
    }

  def unpackRow(value: BigInt): Seq[Int] =
    (0 until 8).map(i => ((value >> (i * 8)) & 0xFF).toInt)

  // ── Cycle tracking ────────────────────────────────────────────────────

  var totalCycles: Long = 0L

  def stepN(clock: chisel3.Clock, steps: Int = 1): Unit = {
    clock.step(steps)
    totalCycles += steps
  }

  def waitFor(clock: chisel3.Clock)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      stepN(clock)
      cycles += 1
    }
  }

  // ── Hardware interface helpers ────────────────────────────────────────

  def sendInst(dut: ATA8, raw: BigInt): Unit = {
    dut.io.AXIST_inInst.tdata.poke(raw.U(128.W))
    dut.io.AXIST_inInst.tvalid.poke(true.B)
    dut.io.AXIST_inInst.tkeep.poke("hffff".U)
    dut.io.AXIST_inInst.tstrb.poke("hffff".U)
    waitFor(dut.clock)(dut.io.AXIST_inInst.tready.peek().litToBoolean, "AXIST_inInst.tready")
    stepN(dut.clock)
    dut.io.AXIST_inInst.tvalid.poke(false.B)
  }

  def feedLoadData(dut: ATA8, rows: Seq[BigInt]): Unit = {
    for ((row, i) <- rows.zipWithIndex) {
      dut.io.AXIST_inData.tdata.poke(row.U(64.W))
      dut.io.AXIST_inData.tstrb.poke("hff".U)
      dut.io.AXIST_inData.tkeep.poke("hff".U)
      dut.io.AXIST_inData.tvalid.poke(true.B)
      dut.io.AXIST_inData.tlast.poke((i == rows.length - 1).B)
      waitFor(dut.clock)(dut.io.AXIST_inData.tready.peek().litToBoolean,
        s"AXIST_inData.tready beat $i")
      stepN(dut.clock)
    }
    dut.io.AXIST_inData.tvalid.poke(false.B)
    dut.io.AXIST_inData.tlast.poke(false.B)
  }

  def collectStoreData(dut: ATA8, nRows: Int): Seq[BigInt] = {
    dut.io.AXIST_out.tready.poke(true.B)
    val collected = scala.collection.mutable.ArrayBuffer[BigInt]()
    while (collected.length < nRows) {
      waitFor(dut.clock)(dut.io.AXIST_out.tvalid.peek().litToBoolean,
        s"AXIST_out.tvalid beat ${collected.length}")
      collected += dut.io.AXIST_out.tdata.peek().litValue
      stepN(dut.clock)
    }
    dut.io.AXIST_out.tready.poke(false.B)
    collected.toSeq
  }

  def preloadMem(dut: ATA8, preload: Assembler#Preload, msCfg: MemSystemConfig): Unit = {

    val addr = preload.offsetAddress + msCfg.tierBases(preload.tier).toInt

    // Pack raw bytes into per-beat BigInts (little-endian)
    val beats: Seq[BigInt] = preload.data
      .grouped(msCfg.dataBusSize)
      .map { bytes =>
        bytes.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (b, i)) =>
          acc | (BigInt(b & 0xFF) << (i * 8))
        }
      }
      .toSeq

    print(f"Writing preload to address: ${preload.offsetAddress}, tier: ${msCfg.tierBases(preload.tier).toInt}")

    val nBeats = beats.length

    // Write: send nBeats A beats (PutFull)
    dut.io.hostIn.d.ready.poke(true.B)

    for ((beat, i) <- beats.reverse.zipWithIndex) {
      dut.io.hostIn.a.bits.opcode.poke(0.U) // PutFullData
      dut.io.hostIn.a.bits.param.poke(0.U)
      dut.io.hostIn.a.bits.address.poke(addr.U)
      dut.io.hostIn.a.bits.size.poke(nBeats.U)
      dut.io.hostIn.a.bits.source.poke(0.U)
      dut.io.hostIn.a.bits.data.poke(beat.U)
      dut.io.hostIn.a.bits.mask.poke(0xFF.U)
      dut.io.hostIn.a.bits.corrupt.poke(0.U)

      dut.io.hostIn.a.valid.poke(true.B)

      waitFor(dut.clock)(dut.io.hostIn.a.ready.peek().litToBoolean,
        s"hostIn.a.ready preload beat $i")
      stepN(dut.clock)
    }

    dut.io.hostIn.a.valid.poke(false.B)

    // Wait for D AccessAck
    waitFor(dut.clock)(dut.io.hostIn.d.valid.peek().litToBoolean, "hostIn.d.valid")
    dut.io.hostIn.d.bits.opcode.expect(0.U) // AccessAck
    stepN(dut.clock)
  }


  "End-to-end no arg" in {
    val testConfig = Configuration.default().copy(sourceWidth = 8)
    val msCfg = MemSystemConfig.default().copy(sourceWidth = testConfig.sourceWidth)
    val asm = new Assembler(AssemblerConfig(dataBusBytes = testConfig.dataBusSize))

    // Phase 1: Build FlatBuffer program
    //val programBuf = buildMatmulProgram()
    val inputPath = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_8x8_no_arg.eaac"


    val bytes = Files.readAllBytes(Paths.get(inputPath))
    val buf = ByteBuffer.wrap(bytes)

    // Phase 2: Assemble into instruction words
    val assembled = asm.assemble(buf)

    println(PrettyPrinter.prettyPrint(assembled))

    val fn = assembled.functions.head
    val insts = fn.instructions

    fn.preloads.foreach { preload =>
      println(f"Preload to address: ${preload.offsetAddress}, tier: ${preload.tier}")
    }


    ////////// Reference import //////////

    case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
    case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

    val jsonStr = Source.fromFile("/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_8x8_no_arg.reference.json").mkString
    
    val parsed = for {
      json <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    val inputArrays: Seq[Seq[Int]] =
      parsed.toOption.get.inputs.map(_.data)
    
    val outputArrays: Seq[Seq[Int]] =
      parsed.toOption.get.outputs.map(_.data)

    //val inputsWithShape =
    //  parsed.inputs.map(t => (t.shape, t.data))
    //
    //val outputsWithShape =
    //  parsed.outputs.map(t => (t.shape, t.data))

    // Phase 3: Simulate hardware
    simulate(new ATA8(testConfig)) { dut =>
      totalCycles = 0L

      // Preload memory for each preload entry
      fn.preloads.foreach { preload =>
        preloadMem(dut, preload, msCfg)
      }

      // Stream all assembled instructions to the hardware
      for (inst <- insts) {
        sendInst(dut, inst)
      }

      // Feed matrix data (A then B) via AXIST_inData
      val matrixRows = (0 until n).map(i => packRow(matrix(i).toSeq))

      val execStart = totalCycles
      //feedLoadData(dut, matrixRows) // matrix A
      //feedLoadData(dut, matrixRows) // matrix B

      // Collect output from AXIST_out
      val outputRows = collectStoreData(dut, n)
      val execEnd = totalCycles

      println(f"[E2E] total cycles     : ${totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      /*
      val expected = Array(
                      Array(30, 204, 219, 194, 17, 204, 118, 92), 
                      Array(156, 46, 27, 203, 233, 83, 186, 128), 
                      Array(200, 133, 186, 128, 185, 174, 150, 162), 
                      Array(209, 193, 25, 21, 236, 147, 67, 68), 
                      Array(212, 144, 72, 17, 154, 76, 60, 204), 
                      Array(34, 172, 110, 144, 108, 3, 38, 40), 
                      Array(243, 126, 108, 185, 132, 168, 227, 22), 
                      Array(63, 29, 13, 62, 192, 239, 149, 22))
      */


      // Phase 4: Verify against golden reference


      val get = Array()

      for (row <- 0 until n) {
        val got = unpackRow(outputRows(row))
        for (col <- 0 until n) {
          //assert(got(col) == (expected((n - 1) - row)(col) & 0xFF),
          //  s"Mismatch at ($row,$col): got ${got(col)}, expected ${expected((n - 1) - row)(col) & 0xFF}")
          //print(f"got ${got(col)} expected ${(expected((n - 1) - row)(col) & 0xFF)}")



          assert(got(col) == (outputArrays(0)(((n - 1) - row) * n + col)),
            s"Mismatch at ($row,$col): got ${got(col)}, expected ${outputArrays(0)(((n - 1) - row) * n + col)}")
        }
      }

      //println("[E2E] Output matches golden reference!")
    }
  }

}
