package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import com.google.flatbuffers.FlatBufferBuilder
import eaac.assembler.{Assembler, AssemblerConfig}
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
  val maxCycles = 200000

  // These fixtures (input_8_*.eaac) were built against the pre-refactor
  // hardware: an 8x8 array with dataBusSize == arrayDim and accDataWidth ==
  // arithDataWidth (accumulation quantized to 8 bits internally, no
  // dedicated wider accumulator) -- i.e. exactly Configuration.default().
  def smallArrayConfig: Configuration = Configuration.default()

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

  // Output rows are accDataWidth bits/element, arrayDim elements wide.
  def unpackRow(value: BigInt)(implicit c: Configuration): Seq[Int] = {
    val mask = (BigInt(1) << c.accDataWidth) - 1
    (0 until c.arrayDim).map(i => ((value >> (i * c.accDataWidth)) & mask).toInt)
  }

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

  // Assumes axiStreamWidth == arrayDim*arithDataWidth, i.e. one AXI-Stream
  // word carries exactly one array row of input data (true for every
  // Configuration used in this file).
  def feedLoadData(dut: ATA8, rows: Seq[BigInt])(implicit c: Configuration): Unit = {
    val allOnes = (BigInt(1) << (c.axiStreamWidth / 8)) - 1
    for ((row, i) <- rows.zipWithIndex) {
      dut.io.AXIST_inData.tdata.poke(row.U(c.axiStreamWidth.W))
      dut.io.AXIST_inData.tstrb.poke(allOnes.U)
      dut.io.AXIST_inData.tkeep.poke(allOnes.U)
      dut.io.AXIST_inData.tvalid.poke(true.B)
      dut.io.AXIST_inData.tlast.poke((i == rows.length - 1).B)

      var cycles = 0
      do {
        require(cycles < maxCycles,
          s"Timeout waiting for AXIST_inData.tready beat $i (after $maxCycles cycles)")
        stepN(dut.clock)
        cycles += 1
      } while (!dut.io.AXIST_inData.tready.peek().litToBoolean)
    }
    dut.io.AXIST_inData.tvalid.poke(false.B)
    dut.io.AXIST_inData.tlast.poke(false.B)
  }

  // Each output row (arrayDim elements * accDataWidth bits) is streamed as
  // wordsPerRow sequential axiStreamWidth-bit AXI-Stream words (LSB word
  // first, matching StoreController's split order); reassemble before
  // returning one BigInt per row.
  def collectStoreData(dut: ATA8, nRows: Int)(implicit c: Configuration): Seq[BigInt] = {
    require((c.arrayDim * c.accDataWidth) % c.axiStreamWidth == 0,
      "collectStoreData assumes a whole number of AXI words per output row")
    val wordsPerRow = (c.arrayDim * c.accDataWidth) / c.axiStreamWidth

    dut.io.AXIST_out.tready.poke(true.B)
    val words = scala.collection.mutable.ArrayBuffer[BigInt]()
    while (words.length < nRows * wordsPerRow) {
      waitFor(dut.clock)(dut.io.AXIST_out.tvalid.peek().litToBoolean,
        s"AXIST_out.tvalid word ${words.length}")
      words += dut.io.AXIST_out.tdata.peek().litValue
      stepN(dut.clock)
    }
    dut.io.AXIST_out.tready.poke(false.B)

    words.grouped(wordsPerRow).map { ws =>
      ws.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (w, i)) => acc | (w << (i * c.axiStreamWidth)) }
    }.toSeq
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
      dut.io.hostIn.a.bits.size.poke((nBeats * msCfg.dataBusSize).U)
      dut.io.hostIn.a.bits.source.poke(0.U)
      dut.io.hostIn.a.bits.data.poke(beat.U)
      dut.io.hostIn.a.bits.mask.poke(((BigInt(1) << msCfg.dataBusSize) - 1).U)
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


  "End-to-end 16x16 single input" in {
    // Real target Configuration (16x16 array, 512-bit bus, 32-bit
    // accumulator, 128-bit AXI-Stream). The program loads a single input
    // tensor from the host, multiplies it against an embedded constant, and
    // stores the (16x16xi32) result.
    val n16 = 16
    implicit val testConfig: Configuration = Configuration.large16x16()
      .withBus(_.copy(sourceWidth = 8))
      .withSemaphore(_.copy(generationWidth = 2))
    val msCfg = MemSystemConfig.default().copy(
      dataBusSize = testConfig.dataBusSize,
      sourceWidth = testConfig.sourceWidth,
      semGenWidth = testConfig.semaphoreGenerationWidth,
    )
    val asm = new Assembler(AssemblerConfig(
      semaphoreGenerationWidth = testConfig.semaphoreGenerationWidth,
      //verbose                  = true,
    ))

    // Phase 1: Build FlatBuffer program
    val inputPath = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_16x16.eaac"

    val bytes = Files.readAllBytes(Paths.get(inputPath))
    val buf = ByteBuffer.wrap(bytes)

    // Phase 2: Assemble into instruction words
    val assembled = asm.assemble(buf)

    val fn = assembled.functions.head
    val insts = fn.instructions

    fn.preloads.foreach { preload =>
      println(f"Preload to address: ${preload.offsetAddress}, tier: ${preload.tier}")
    }

    ////////// Reference import //////////

    case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
    case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

    val jsonStr = Source.fromFile("/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_16x16.reference.json").mkString

    val parsed = for {
      json <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    val inputArrays: Seq[Seq[Int]] =
      parsed.toOption.get.inputs.map(_.data)

    val outputArrays: Seq[Seq[Int]] =
      parsed.toOption.get.outputs.map(_.data)

    // Phase 3: Simulate hardware
    simulate(new ATA8(testConfig, msCfg)) { dut =>
      totalCycles = 0L

      // Preload memory for each preload entry (the constant operand)
      fn.preloads.foreach { preload =>
        preloadMem(dut, preload, msCfg)
      }

      // Stream all assembled instructions to the hardware
      for (inst <- insts) {
        sendInst(dut, inst)
      }

      val execStart = totalCycles

      // Feed the single input tensor via AXIST_inData; rows are reversed to
      // match the output access pattern (((n-1)-row)*n+col), consistent
      // with "End-to-end with arguments".
      inputArrays.foreach { inputArr =>
        val rows = (0 until n16).map { i =>
          val rowData = (0 until n16).map(col => inputArr(((n16 - 1) - i) * n16 + col))
          packRow(rowData)
        }
        feedLoadData(dut, rows)
      }

      // Collect output from AXIST_out
      val outputRows = collectStoreData(dut, n16)
      val execEnd = totalCycles

      println(f"[E2E] total cycles     : ${totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      // Phase 4: Verify against a directly-recomputed expected value rather
      // than a naive (n16-1-row) reindex of the reference JSON. The WS
      // wavefront convention means PE column k ends up stationary with
      // weight row (n16-1-k) (confirmed in GrainWideBeatTest/Grain-level
      // tests), and the natural tier0 load/store order row-reverses the fed
      // activation tensor -- so the hardware computes
      //   hw[row][col] = sum_k A[n16-1-row][k] * B[n16-1-k][col]
      // (signed int8 x int8 -> int32, matching the now-signed PE
      // multiplier), which is *not* the same as reindexing a plain A @ B
      // reference by row.
      def toSignedByte(v: Int): Int = { val b = v & 0xFF; if (b >= 128) b - 256 else b }
      val constantBytes = fn.preloads.head.data
      val A = Array.tabulate(n16, n16)((r, c) => toSignedByte(inputArrays.head(r * n16 + c)))
      val B = Array.tabulate(n16, n16)((r, c) => constantBytes(r * n16 + c).toInt)

      for (row <- 0 until n16) {
        val got = unpackRow(outputRows(row))
        for (col <- 0 until n16) {
          val exp = (0 until n16).map(k => A(n16 - 1 - row)(k) * B(n16 - 1 - k)(col)).sum
          if (got(col) != exp) println(f"DEBUG Mismatch at ($row,$col): got ${got(col)}, expected $exp")
        }
      }
      for (row <- 0 until n16) {
        val got = unpackRow(outputRows(row))
        for (col <- 0 until n16) {
          val exp = (0 until n16).map(k => A(n16 - 1 - row)(k) * B(n16 - 1 - k)(col)).sum
          assert(got(col) == exp, s"Mismatch at ($row,$col): got ${got(col)}, expected $exp")
        }
      }
    }
  }

  "End-to-end with arguments" in {
    implicit val testConfig: Configuration = smallArrayConfig
      .withBus(_.copy(sourceWidth = 8))
      .withSemaphore(_.copy(nSemaphores = 16))
      .withSemaphore(_.copy(generationWidth = 2))
    val msCfg = MemSystemConfig.default().copy(
      dataBusSize = testConfig.dataBusSize,
      sourceWidth = testConfig.sourceWidth,
      semGenWidth = testConfig.semaphoreGenerationWidth,
    )
    val asm = new Assembler(AssemblerConfig(
      semaphoreGenerationWidth = testConfig.semaphoreGenerationWidth,
      //verbose                  = true,
    ))

    // Phase 1: Build FlatBuffer program
    //val programBuf = buildMatmulProgram()
    val inputPath = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_8x8.eaac"


    val bytes = Files.readAllBytes(Paths.get(inputPath))
    val buf = ByteBuffer.wrap(bytes)

    // Phase 2: Assemble into instruction words
    val assembled = asm.assemble(buf)

    val fn = assembled.functions.head
    val insts = fn.instructions

    fn.preloads.foreach { preload =>
      println(f"Preload to address: ${preload.offsetAddress}, tier: ${preload.tier}")
    }


    ////////// Reference import //////////

    case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
    case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

    val jsonStr = Source.fromFile("/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_8x8.reference.json").mkString
    
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
    simulate(new ATA8(testConfig, msCfg)) { dut =>
      totalCycles = 0L

      // Preload memory for each preload entry
      fn.preloads.foreach { preload =>
        preloadMem(dut, preload, msCfg)
      }

      // Stream all assembled instructions to the hardware
      for (inst <- insts) {
        sendInst(dut, inst)
      }

      val execStart = totalCycles

      // Feed each input array via AXIST_inData; rows are reversed to match
      // the output access pattern (((n - 1) - row) * n + col).
      inputArrays.foreach { inputArr =>
        val rows = (0 until n).map { i =>
          val rowData = (0 until n).map(col => inputArr(((n - 1) - i) * n + col))
          packRow(rowData)
        }
        feedLoadData(dut, rows)
      }

      // Collect output from AXIST_out
      val outputRows = collectStoreData(dut, n)
      val execEnd = totalCycles

      println(f"[E2E] total cycles     : ${totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      // Phase 4: Verify against golden reference
      for (row <- 0 until n) {
        val got = unpackRow(outputRows(row))
        for (col <- 0 until n) {
          assert(got(col) == (outputArrays(0)(((n - 1) - row) * n + col)),
            s"Mismatch at ($row,$col): got ${got(col)}, expected ${outputArrays(0)(((n - 1) - row) * n + col)}")
        }
      }
    }
  }



  "End-to-end large with arguments" in {
    /*
    val testConfig = Configuration.default().withBus(_.copy(sourceWidth = 8)
      .withSemaphore(_.copy(nSemaphores = 32))
    )*/

    implicit val testConfig: Configuration = smallArrayConfig
      .withBus(_.copy(sourceWidth = 8))
      .withSemaphore(_.copy(nSemaphores = 16))
      .withSemaphore(_.copy(generationWidth = 2))
      .withSemaphore(_.copy(queueSize = 8))
      .withTrigger(_.copy(rows = 64, opMemDepth = 64))
    val msCfg = MemSystemConfig.default().copy(
      dataBusSize = testConfig.dataBusSize,
      sourceWidth = testConfig.sourceWidth,
      semGenWidth = testConfig.semaphoreGenerationWidth,
    )
    val asm = new Assembler(AssemblerConfig(
      semaphoreGenerationWidth = testConfig.semaphoreGenerationWidth,
      //verbose                  = true,
    ))

    // Phase 1: Build FlatBuffer program
    //val programBuf = buildMatmulProgram()
    val inputPath = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_nn.eaac"


    val bytes = Files.readAllBytes(Paths.get(inputPath))
    val buf = ByteBuffer.wrap(bytes)

    // Phase 2: Assemble into instruction words
    val assembled = asm.assemble(buf)

    val fn = assembled.functions.head
    val insts = fn.instructions

    fn.preloads.foreach { preload =>
      println(f"Preload to address: ${preload.offsetAddress}, tier: ${preload.tier}")
    }


    ////////// Reference import //////////

    case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
    case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

    val jsonStr = Source.fromFile("/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_nn.reference.json").mkString
    
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
    simulate(new ATA8(testConfig, msCfg)) { dut =>
      totalCycles = 0L

      // Preload memory for each preload entry
      fn.preloads.foreach { preload =>
        preloadMem(dut, preload, msCfg)
      }

      // Stream all assembled instructions to the hardware
      for (inst <- insts) {
        sendInst(dut, inst)
      }

      val execStart = totalCycles

      // Feed each input array via AXIST_inData; rows are reversed to match
      // the output access pattern (((n - 1) - row) * n + col).
      inputArrays.foreach { inputArr =>
        val rows = (0 until n).map { i =>
          val rowData = (0 until n).map(col => inputArr(((n - 1) - i) * n + col))
          packRow(rowData)
        }
        feedLoadData(dut, rows)
      }

      // Collect output from AXIST_out
      val outputRows = collectStoreData(dut, n)
      val execEnd = totalCycles

      println(f"[E2E] total cycles     : ${totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      // Phase 4: Verify against golden reference
      for (row <- 0 until n) {
        val got = unpackRow(outputRows(row))
        for (col <- 0 until n) {
          assert(got(col) == (outputArrays(0)(((n - 1) - row) * n + col)),
            s"Mismatch at ($row,$col): got ${got(col)}, expected ${outputArrays(0)(((n - 1) - row) * n + col)}")
        }
      }
    }
  }

  /*
  "End-to-end XL" in {
    implicit val testConfig: Configuration = smallArrayConfig
      .withBus(_.copy(sourceWidth = 8))
      .withSemaphore(_.copy(nSemaphores = 16))
      .withSemaphore(_.copy(generationWidth = 2))
      .withSemaphore(_.copy(queueSize = 8))
      .withTrigger(_.copy(rows = 64, opMemDepth = 64))
    val msCfg = MemSystemConfig.default().copy(
      dataBusSize = testConfig.dataBusSize,
      sourceWidth = testConfig.sourceWidth,
      semGenWidth = testConfig.semaphoreGenerationWidth,
    )
    val asm = new Assembler(AssemblerConfig(
      semaphoreGenerationWidth = testConfig.semaphoreGenerationWidth,
      verbose                  = true,
    ))

    val inputPath = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_nn_xl.eaac"

    val bytes = Files.readAllBytes(Paths.get(inputPath))
    val buf = ByteBuffer.wrap(bytes)

    val assembled = asm.assemble(buf)
    val fn = assembled.functions.head
    val insts = fn.instructions

    fn.preloads.foreach { preload =>
      println(f"Preload to address: ${preload.offsetAddress}, tier: ${preload.tier}")
    }

    ////////// Reference import //////////

    case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
    case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

    val jsonStr = Source.fromFile("/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_nn_xl.reference.json").mkString

    val parsed = for {
      json <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    val inputArrays: Seq[Seq[Int]] =
      parsed.toOption.get.inputs.map(_.data)

    val outputArrays: Seq[Seq[Int]] =
      parsed.toOption.get.outputs.map(_.data)

    simulate(new ATA8(testConfig, msCfg)) { dut =>
      totalCycles = 0L

      fn.preloads.foreach { preload =>
        preloadMem(dut, preload, msCfg)
      }

      for (inst <- insts) {
        sendInst(dut, inst)
      }

      val execStart = totalCycles

      inputArrays.foreach { inputArr =>
        val rows = (0 until n).map { i =>
          val rowData = (0 until n).map(col => inputArr(((n - 1) - i) * n + col))
          packRow(rowData)
        }
        feedLoadData(dut, rows)
      }

      val outputRows = collectStoreData(dut, n)
      val execEnd = totalCycles

      println(f"[E2E] total cycles     : ${totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      for (row <- 0 until n) {
        val got = unpackRow(outputRows(row))
        for (col <- 0 until n) {
          assert(got(col) == (outputArrays(0)(((n - 1) - row) * n + col)),
            s"Mismatch at ($row,$col): got ${got(col)}, expected ${outputArrays(0)(((n - 1) - row) * n + col)}")
        }
      }
    }
  }
  */

  "End-to-end small mem" in {
    implicit val testConfig: Configuration = smallArrayConfig
      .withBus(_.copy(sourceWidth = 8))
      .withSemaphore(_.copy(nSemaphores = 32))
      .withSemaphore(_.copy(generationWidth = 2))
    val msCfg = MemSystemConfig.small().copy(
      dataBusSize = testConfig.dataBusSize,
      sourceWidth = testConfig.sourceWidth,
      semGenWidth = testConfig.semaphoreGenerationWidth,
    )
    val asm = new Assembler(AssemblerConfig(
      semaphoreGenerationWidth = testConfig.semaphoreGenerationWidth,
      //verbose                  = true,
    ))

    // Phase 1: Build FlatBuffer program
    //val programBuf = buildMatmulProgram()
    val inputPath = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_8x8_small_mem.eaac"


    val bytes = Files.readAllBytes(Paths.get(inputPath))
    val buf = ByteBuffer.wrap(bytes)

    // Phase 2: Assemble into instruction words
    val assembled = asm.assemble(buf)

    val fn = assembled.functions.head
    val insts = fn.instructions

    fn.preloads.foreach { preload =>
      println(f"Preload to address: ${preload.offsetAddress}, tier: ${preload.tier}")
    }


    ////////// Reference import //////////

    case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
    case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

    val jsonStr = Source.fromFile("/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_8x8_small_mem.reference.json").mkString
    
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
    simulate(new ATA8(testConfig, msCfg)) { dut =>
      totalCycles = 0L

      // Preload memory for each preload entry
      fn.preloads.foreach { preload =>
        preloadMem(dut, preload, msCfg)
      }

      // Stream all assembled instructions to the hardware
      for (inst <- insts) {
        sendInst(dut, inst)
      }

      val execStart = totalCycles

      // Feed each input array via AXIST_inData; rows are reversed to match
      // the output access pattern (((n - 1) - row) * n + col).
      inputArrays.foreach { inputArr =>
        val rows = (0 until n).map { i =>
          val rowData = (0 until n).map(col => inputArr(((n - 1) - i) * n + col))
          packRow(rowData)
        }
        feedLoadData(dut, rows)
      }

      // Collect output from AXIST_out
      val outputRows = collectStoreData(dut, n)
      val execEnd = totalCycles

      println(f"[E2E] total cycles     : ${totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      // Phase 4: Verify against golden reference
      for (row <- 0 until n) {
        val got = unpackRow(outputRows(row))
        for (col <- 0 until n) {
          assert(got(col) == (outputArrays(0)(((n - 1) - row) * n + col)),
            s"Mismatch at ($row,$col): got ${got(col)}, expected ${outputArrays(0)(((n - 1) - row) * n + col)}")
        }
      }
    }
  }


  "End-to-end broadcast" in {
    implicit val testConfig: Configuration = smallArrayConfig
      .withBus(_.copy(sourceWidth = 8))
      .withSemaphore(_.copy(nSemaphores = 32))
      .withSemaphore(_.copy(generationWidth = 2))
    val msCfg = MemSystemConfig.small().copy(
      dataBusSize = testConfig.dataBusSize,
      sourceWidth = testConfig.sourceWidth,
      semGenWidth = testConfig.semaphoreGenerationWidth,
    )
    val asm = new Assembler(AssemblerConfig(
      semaphoreGenerationWidth = testConfig.semaphoreGenerationWidth,
      //verbose                  = true,
    ))

    // Phase 1: Build FlatBuffer program
    //val programBuf = buildMatmulProgram()
    val inputPath = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_broadcast.eaac"


    val bytes = Files.readAllBytes(Paths.get(inputPath))
    val buf = ByteBuffer.wrap(bytes)

    // Phase 2: Assemble into instruction words
    val assembled = asm.assemble(buf)

    val fn = assembled.functions.head
    val insts = fn.instructions

    fn.preloads.foreach { preload =>
      println(f"Preload to address: ${preload.offsetAddress}, tier: ${preload.tier}")
    }


    ////////// Reference import //////////

    case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
    case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

    val jsonStr = Source.fromFile("/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_broadcast.reference.json").mkString
    
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
    simulate(new ATA8(testConfig, msCfg)) { dut =>
      totalCycles = 0L

      // Preload memory for each preload entry
      fn.preloads.foreach { preload =>
        preloadMem(dut, preload, msCfg)
      }

      // Stream all assembled instructions to the hardware
      for (inst <- insts) {
        sendInst(dut, inst)
      }

      val execStart = totalCycles

      // Feed each input array via AXIST_inData; rows are reversed to match
      // the output access pattern (((n - 1) - row) * n + col).
      inputArrays.foreach { inputArr =>
        val rows = (0 until n).map { i =>
          val rowData = (0 until n).map(col => inputArr(((n - 1) - i) * n + col))
          packRow(rowData)
        }
        feedLoadData(dut, rows)
      }

      // Collect output from AXIST_out
      val outputRows = collectStoreData(dut, n)
      val execEnd = totalCycles

      println(f"[E2E] total cycles     : ${totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      // Phase 4: Verify against golden reference
      for (row <- 0 until n) {
        val got = unpackRow(outputRows(row))
        for (col <- 0 until n) {
          assert(got(col) == (outputArrays(0)(((n - 1) - row) * n + col)),
            s"Mismatch at ($row,$col): got ${got(col)}, expected ${outputArrays(0)(((n - 1) - row) * n + col)}")
        }
      }
    }
  }


  "End-to-end broadcast large" in {
    implicit val testConfig: Configuration = smallArrayConfig
      .withBus(_.copy(sourceWidth = 8))
      .withSemaphore(_.copy(nSemaphores = 32))
      .withSemaphore(_.copy(generationWidth = 2))
    val msCfg = MemSystemConfig.small().copy(
      dataBusSize = testConfig.dataBusSize,
      sourceWidth = testConfig.sourceWidth,
      semGenWidth = testConfig.semaphoreGenerationWidth,
    )
    val asm = new Assembler(AssemblerConfig(
      semaphoreGenerationWidth = testConfig.semaphoreGenerationWidth,
      //verbose                  = true,
    ))

    // Phase 1: Build FlatBuffer program
    //val programBuf = buildMatmulProgram()
    val inputPath = "/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_broadcast_large.eaac"


    val bytes = Files.readAllBytes(Paths.get(inputPath))
    val buf = ByteBuffer.wrap(bytes)

    // Phase 2: Assemble into instruction words
    val assembled = asm.assemble(buf)

    val fn = assembled.functions.head
    val insts = fn.instructions

    fn.preloads.foreach { preload =>
      println(f"Preload to address: ${preload.offsetAddress}, tier: ${preload.tier}")
    }


    ////////// Reference import //////////

    case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
    case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

    val jsonStr = Source.fromFile("/home/karlhk/dtu/Thesis/hardware/ATAN/test/input_8_broadcast_large.reference.json").mkString
    
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
    simulate(new ATA8(testConfig, msCfg)) { dut =>
      totalCycles = 0L

      // Preload memory for each preload entry
      fn.preloads.foreach { preload =>
        preloadMem(dut, preload, msCfg)
      }

      // Stream all assembled instructions to the hardware
      for (inst <- insts) {
        sendInst(dut, inst)
      }

      val execStart = totalCycles

      // Feed each input array via AXIST_inData; rows are reversed to match
      // the output access pattern (((n - 1) - row) * n + col).
      inputArrays.foreach { inputArr =>
        val rows = (0 until n).map { i =>
          val rowData = (0 until n).map(col => inputArr(((n - 1) - i) * n + col))
          packRow(rowData)
        }
        feedLoadData(dut, rows)
      }

      // Collect output from AXIST_out
      val outputRows = collectStoreData(dut, n)
      val execEnd = totalCycles

      println(f"[E2E] total cycles     : ${totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      // Phase 4: Verify against golden reference
      for (row <- 0 until n) {
        val got = unpackRow(outputRows(row))
        for (col <- 0 until n) {
          assert(got(col) == (outputArrays(0)(((n - 1) - row) * n + col)),
            s"Mismatch at ($row,$col): got ${got(col)}, expected ${outputArrays(0)(((n - 1) - row) * n + col)}")
        }
      }
    }
  }

}
