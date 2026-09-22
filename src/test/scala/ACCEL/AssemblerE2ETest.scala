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

  def smallArrayConfig: Configuration = Configuration.default()

  // ── Test matrix (same as ATA8Test) ────────────────────────────────────

  "End-to-end 16x16 single input" in {
    val name = "input_16x16"

    val n16 = 16
    implicit val testConfig: Configuration = Configuration.large16x16()
      .withBus(_.copy(sourceWidth = 8))
      .withSemaphore(_.copy(generationWidth = 2))
    val msCfg = MemSystemConfig.large().copy(
      dataBusSize = testConfig.dataBusSize,
      sourceWidth = testConfig.sourceWidth,
      semGenWidth = testConfig.semaphoreGenerationWidth,
    )
    val asm = new Assembler(AssemblerConfig(
      semaphoreGenerationWidth = testConfig.semaphoreGenerationWidth,
      //verbose                  = true,
    ))

    // Phase 1: Build FlatBuffer program
    val inputPath = s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.eaac"

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

    val jsonStr = Source.fromFile(s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.reference.json").mkString

    val parsed = for {
      json <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    /*
    val inputArrays: Seq[Seq[Int]] =
      parsed.toOption.get.inputs.map(_.data)

    val outputArrays: Seq[Seq[Int]] =
      parsed.toOption.get.outputs.map(_.data)
    */

    val inputArrays: Seq[Seq[Int]]  = parsed.toOption.get.inputs.map(_.data)
    val outputTensors                = parsed.toOption.get.outputs
    val outputArrays: Seq[Seq[Int]] = outputTensors.map(_.data)
    val outputElemBits: Seq[Int]    = outputTensors.map(t => TestUtil.elemBitsOf(t.element_type))
    val outputBeatsPerTile: Seq[Int] = outputElemBits.map(bits => (n * n * bits) / testConfig.axiStreamWidth)


    // Phase 3: Simulate hardware
    simulate(new ATA8(testConfig, msCfg)) { dut =>
      val ops = new AtanTestOps(dut.clock, maxCycles)

      // Preload memory for each preload entry (the constant operand)
      fn.preloads.foreach { preload =>
        ops.preloadMem(dut.io.hostIn, preload, msCfg, reverseBeats = false, sizeAsBeatSpan = true)
      }

      // Stream all assembled instructions to the hardware
      for (inst <- insts) {
        ops.sendInst(dut.io.AXIST_inInst, inst)
      }

      val execStart = ops.totalCycles

      inputArrays.zipWithIndex.foreach { case (arr, argIdx) =>
        val rows = (0 until n16).map { i =>
          TestUtil.packRow((0 until n16).map(col => arr(i * n16 + col)))
        }
        ops.feedLoadData(dut.io.AXIST_inData, rows)
      }

      // Collect output from AXIST_out
      //val outputRows = ops.collectStoreRows(dut.io.AXIST_out, n16)
      val outputBeats = ops.collectStoreBeats(dut.io.AXIST_out, outputBeatsPerTile.sum)
      val execEnd = ops.totalCycles

      for (tileIdx <- outputArrays.indices) {
        var beatOffset = 0

        val elemBits  = outputElemBits(tileIdx)
        val nBeats    = outputBeatsPerTile(tileIdx)
        val tileBeats = outputBeats.slice(beatOffset, beatOffset + nBeats)
        beatOffset += nBeats
        val rows = tileBeats.flatMap(TestUtil.unpackBeat(_, elemBits, testConfig.axiStreamWidth)).grouped(n).toIndexedSeq

        val totalMismatches = ops.compareOutput(outputArrays(tileIdx), rows)
        info(s"total mismatches: $totalMismatches / ${outputArrays.length * n * n}")
        assert(totalMismatches == 0, s"$totalMismatches mismatches found (see error matrices above)")
      }
    }
  }

  "End-to-end with arguments" in {
    val name = "input_8_8x8"

    implicit val testConfig: Configuration = smallArrayConfig
      .withBus(_.copy(sourceWidth = 8))
      .withSemaphore(_.copy(nSemaphores = 8))
      .withSemaphore(_.copy(generationWidth = 2))
    val msCfg = MemSystemConfig.large().copy(
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
    val inputPath = s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.eaac"


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

    val jsonStr = Source.fromFile(s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.reference.json").mkString
    
    val parsed = for {
      json <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    /*
    val inputArrays: Seq[Seq[Int]] =
      parsed.toOption.get.inputs.map(_.data)
    
    val outputArrays: Seq[Seq[Int]] =
      parsed.toOption.get.outputs.map(_.data)
    */

    //val inputsWithShape =
    //  parsed.inputs.map(t => (t.shape, t.data))
    //
    //val outputsWithShape =
    //  parsed.outputs.map(t => (t.shape, t.data))
    
    val inputArrays: Seq[Seq[Int]]  = parsed.toOption.get.inputs.map(_.data)
    val outputTensors                = parsed.toOption.get.outputs
    val outputArrays: Seq[Seq[Int]] = outputTensors.map(_.data)
    val outputElemBits: Seq[Int]    = outputTensors.map(t => TestUtil.elemBitsOf(t.element_type))
    val outputBeatsPerTile: Seq[Int] = outputElemBits.map(bits => (n * n * bits) / testConfig.axiStreamWidth)

    // Phase 3: Simulate hardware
    simulate(new ATA8(testConfig, msCfg)) { dut =>
      val ops = new AtanTestOps(dut.clock, maxCycles)

      // Preload memory for each preload entry
      fn.preloads.foreach { preload =>
        ops.preloadMem(dut.io.hostIn, preload, msCfg, reverseBeats = false, sizeAsBeatSpan = true)
      }

      // Stream all assembled instructions to the hardware
      for (inst <- insts) {
        ops.sendInst(dut.io.AXIST_inInst, inst)
      }

      val execStart = ops.totalCycles

      // Feed each input array via AXIST_inData; rows are reversed to match
      // the output access pattern (((n - 1) - row) * n + col).
      inputArrays.foreach { inputArr =>
        val rows = (0 until n).map { i =>
          //val rowData = (0 until n).map(col => inputArr(((n - 1) - i) * n + col))
          TestUtil.packRow((0 until n).map(col => inputArr(i * n + col)))
        }
        ops.feedLoadData(dut.io.AXIST_inData, rows)
      }

      // Collect output from AXIST_out
      val outputBeats = ops.collectStoreRows(dut.io.AXIST_out, n)
      val execEnd = ops.totalCycles

      println(f"[E2E] total cycles     : ${ops.totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      // Phase 4: Verify against golden reference
      /*
      for (row <- 0 until n) {
        val got = TestUtil.unpackRow(outputRows(row))
        for (col <- 0 until n) {
          assert(got(col) == (outputArrays(0)(((n - 1) - row) * n + col)),
            s"Mismatch at ($row,$col): got ${got(col)}, expected ${outputArrays(0)(((n - 1) - row) * n + col)}")
        }
      }
      */

      for (tileIdx <- outputArrays.indices) {
        var beatOffset = 0

        val elemBits  = outputElemBits(tileIdx)
        val nBeats    = outputBeatsPerTile(tileIdx)
        val tileBeats = outputBeats.slice(beatOffset, beatOffset + nBeats)
        beatOffset += nBeats
        val rows = tileBeats.flatMap(TestUtil.unpackBeat(_, elemBits, testConfig.axiStreamWidth)).grouped(n).toIndexedSeq

        val totalMismatches = ops.compareOutput(outputArrays(tileIdx), rows)
        info(s"total mismatches: $totalMismatches / ${outputArrays.length * n * n}")
        assert(totalMismatches == 0, s"$totalMismatches mismatches found (see error matrices above)")
      }
    }
  }



  "End-to-end large with arguments" in {
    /*
    val testConfig = Configuration.default().withBus(_.copy(sourceWidth = 8)
      .withSemaphore(_.copy(nSemaphores = 32))
    )*/

    val name = "input_8_nn"

    implicit val testConfig: Configuration = smallArrayConfig
      .withBus(_.copy(sourceWidth = 8))
      .withSemaphore(_.copy(nSemaphores = 16))
      .withSemaphore(_.copy(generationWidth = 2))
      .withSemaphore(_.copy(queueSize = 8))
      .withTrigger(_.copy(rows = 64, opMemDepth = 64))
    val msCfg = MemSystemConfig.large().copy(
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
    val inputPath = s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.eaac"


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

    val jsonStr = Source.fromFile(s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.reference.json").mkString
    
    val parsed = for {
      json <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    val inputArrays: Seq[Seq[Int]]  = parsed.toOption.get.inputs.map(_.data)
    val outputTensors                = parsed.toOption.get.outputs
    val outputArrays: Seq[Seq[Int]] = outputTensors.map(_.data)
    val outputElemBits: Seq[Int]    = outputTensors.map(t => TestUtil.elemBitsOf(t.element_type))
    val outputBeatsPerTile: Seq[Int] = outputElemBits.map(bits => (n * n * bits) / testConfig.axiStreamWidth)

    // Phase 3: Simulate hardware
    simulate(new ATA8(testConfig, msCfg)) { dut =>
      val ops = new AtanTestOps(dut.clock, maxCycles)

      // Preload memory for each preload entry
      fn.preloads.foreach { preload =>
        ops.preloadMem(dut.io.hostIn, preload, msCfg, reverseBeats = false, sizeAsBeatSpan = true)
      }

      // Stream all assembled instructions to the hardware
      for (inst <- insts) {
        ops.sendInst(dut.io.AXIST_inInst, inst)
      }

      val execStart = ops.totalCycles

      // Feed each input array via AXIST_inData; rows are reversed to match
      // the output access pattern (((n - 1) - row) * n + col).
      inputArrays.foreach { inputArr =>
        val rows = (0 until n).map { i =>
          val rowData = (0 until n).map(col => inputArr(i * n + col))
          TestUtil.packRow(rowData)
        }
        ops.feedLoadData(dut.io.AXIST_inData, rows)
      }

      // Collect output from AXIST_out
      val outputBeats = ops.collectStoreRows(dut.io.AXIST_out, n)
      val execEnd = ops.totalCycles

      println(f"[E2E] total cycles     : ${ops.totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      // Phase 4: Verify against golden reference
      for (tileIdx <- outputArrays.indices) {
        var beatOffset = 0

        val elemBits  = outputElemBits(tileIdx)
        val nBeats    = outputBeatsPerTile(tileIdx)
        val tileBeats = outputBeats.slice(beatOffset, beatOffset + nBeats)
        beatOffset += nBeats
        val rows = tileBeats.flatMap(TestUtil.unpackBeat(_, elemBits, testConfig.axiStreamWidth)).grouped(n).toIndexedSeq

        val totalMismatches = ops.compareOutput(outputArrays(tileIdx), rows)
        info(s"total mismatches: $totalMismatches / ${outputArrays.length * n * n}")
        assert(totalMismatches == 0, s"$totalMismatches mismatches found (see error matrices above)")
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
      val ops = new AtanTestOps(dut.clock, maxCycles)

      fn.preloads.foreach { preload =>
        ops.preloadMem(dut.io.hostIn, preload, msCfg, reverseBeats = true, sizeAsBeatSpan = true)
      }

      for (inst <- insts) {
        ops.sendInst(dut.io.AXIST_inInst, inst)
      }

      val execStart = ops.totalCycles

      inputArrays.foreach { inputArr =>
        val rows = (0 until n).map { i =>
          val rowData = (0 until n).map(col => inputArr(((n - 1) - i) * n + col))
          TestUtil.packRow(rowData)
        }
        ops.feedLoadData(dut.io.AXIST_inData, rows)
      }

      val outputRows = ops.collectStoreRows(dut.io.AXIST_out, n)
      val execEnd = ops.totalCycles

      println(f"[E2E] total cycles     : ${ops.totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      for (row <- 0 until n) {
        val got = TestUtil.unpackRow(outputRows(row))
        for (col <- 0 until n) {
          assert(got(col) == (outputArrays(0)(((n - 1) - row) * n + col)),
            s"Mismatch at ($row,$col): got ${got(col)}, expected ${outputArrays(0)(((n - 1) - row) * n + col)}")
        }
      }
    }
  }
  */

  "End-to-end small mem" in {
    val name = "input_8_8x8_small_mem"

    implicit val testConfig: Configuration = smallArrayConfig
      .withBus(_.copy(sourceWidth = 8))
      .withSemaphore(_.copy(nSemaphores = 8))
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
    val inputPath = s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.eaac"


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

    val jsonStr = Source.fromFile(s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.reference.json").mkString
    
    val parsed = for {
      json <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    val inputArrays: Seq[Seq[Int]]  = parsed.toOption.get.inputs.map(_.data)
    val outputTensors                = parsed.toOption.get.outputs
    val outputArrays: Seq[Seq[Int]] = outputTensors.map(_.data)
    val outputElemBits: Seq[Int]    = outputTensors.map(t => TestUtil.elemBitsOf(t.element_type))
    val outputBeatsPerTile: Seq[Int] = outputElemBits.map(bits => (n * n * bits) / testConfig.axiStreamWidth)

    // Phase 3: Simulate hardware
    simulate(new ATA8(testConfig, msCfg)) { dut =>
      val ops = new AtanTestOps(dut.clock, maxCycles)

      // Preload memory for each preload entry
      fn.preloads.foreach { preload =>
        ops.preloadMem(dut.io.hostIn, preload, msCfg, reverseBeats = false, sizeAsBeatSpan = true)
      }

      // Stream all assembled instructions to the hardware
      for (inst <- insts) {
        ops.sendInst(dut.io.AXIST_inInst, inst)
      }

      val execStart = ops.totalCycles

      // Feed each input array via AXIST_inData; rows are reversed to match
      // the output access pattern (((n - 1) - row) * n + col).
      inputArrays.foreach { inputArr =>
        val rows = (0 until n).map { i =>
          val rowData = (0 until n).map(col => inputArr(i * n + col))
          TestUtil.packRow(rowData)
        }
        ops.feedLoadData(dut.io.AXIST_inData, rows)
      }

      // Collect output from AXIST_out
      val outputBeats = ops.collectStoreRows(dut.io.AXIST_out, n)
      val execEnd = ops.totalCycles

      println(f"[E2E] total cycles     : ${ops.totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      // Phase 4: Verify against golden reference
      for (tileIdx <- outputArrays.indices) {
        var beatOffset = 0

        val elemBits  = outputElemBits(tileIdx)
        val nBeats    = outputBeatsPerTile(tileIdx)
        val tileBeats = outputBeats.slice(beatOffset, beatOffset + nBeats)
        beatOffset += nBeats
        val rows = tileBeats.flatMap(TestUtil.unpackBeat(_, elemBits, testConfig.axiStreamWidth)).grouped(n).toIndexedSeq

        val totalMismatches = ops.compareOutput(outputArrays(tileIdx), rows)
        info(s"total mismatches: $totalMismatches / ${outputArrays.length * n * n}")
        assert(totalMismatches == 0, s"$totalMismatches mismatches found (see error matrices above)")
      }
    }
  }


  "End-to-end broadcast" in {
    val name = "input_8_broadcast"

    implicit val testConfig: Configuration = smallArrayConfig
      .withBus(_.copy(sourceWidth = 8))
      .withSemaphore(_.copy(nSemaphores = 8))
      .withSemaphore(_.copy(generationWidth = 2))
    val msCfg = MemSystemConfig.large().copy(
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
    val inputPath = s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.eaac"


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

    val jsonStr = Source.fromFile(s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.reference.json").mkString
    
    val parsed = for {
      json <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    val inputArrays: Seq[Seq[Int]]  = parsed.toOption.get.inputs.map(_.data)
    val outputTensors                = parsed.toOption.get.outputs
    val outputArrays: Seq[Seq[Int]] = outputTensors.map(_.data)
    val outputElemBits: Seq[Int]    = outputTensors.map(t => TestUtil.elemBitsOf(t.element_type))
    val outputBeatsPerTile: Seq[Int] = outputElemBits.map(bits => (n * n * bits) / testConfig.axiStreamWidth)

    // Phase 3: Simulate hardware
    simulate(new ATA8(testConfig, msCfg)) { dut =>
      val ops = new AtanTestOps(dut.clock, maxCycles)

      // Preload memory for each preload entry
      fn.preloads.foreach { preload =>
        ops.preloadMem(dut.io.hostIn, preload, msCfg, reverseBeats = false, sizeAsBeatSpan = true)
      }

      // Stream all assembled instructions to the hardware
      for (inst <- insts) {
        ops.sendInst(dut.io.AXIST_inInst, inst)
      }

      val execStart = ops.totalCycles

      // Feed each input array via AXIST_inData; rows are reversed to match
      // the output access pattern (((n - 1) - row) * n + col).
      inputArrays.foreach { inputArr =>
        val rows = (0 until n).map { i =>
          TestUtil.packRow((0 until n).map(col => inputArr(i * n + col)))
        }
        ops.feedLoadData(dut.io.AXIST_inData, rows)
      }

      // Collect output from AXIST_out
      val outputBeats = ops.collectStoreRows(dut.io.AXIST_out, n)
      val execEnd = ops.totalCycles

      println(f"[E2E] total cycles     : ${ops.totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      // Phase 4: Verify against golden reference
      for (tileIdx <- outputArrays.indices) {
        var beatOffset = 0

        val elemBits  = outputElemBits(tileIdx)
        val nBeats    = outputBeatsPerTile(tileIdx)
        val tileBeats = outputBeats.slice(beatOffset, beatOffset + nBeats)
        beatOffset += nBeats
        val rows = tileBeats.flatMap(TestUtil.unpackBeat(_, elemBits, testConfig.axiStreamWidth)).grouped(n).toIndexedSeq

        val totalMismatches = ops.compareOutput(outputArrays(tileIdx), rows)
        info(s"total mismatches: $totalMismatches / ${outputArrays.length * n * n}")
        assert(totalMismatches == 0, s"$totalMismatches mismatches found (see error matrices above)")
      }
    }
  }


  "End-to-end broadcast large" in {
    val name = "input_8_broadcast_large"

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
    val inputPath = s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.eaac"


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

    val jsonStr = Source.fromFile(s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.reference.json").mkString
    
    val parsed = for {
      json <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    val inputArrays: Seq[Seq[Int]]  = parsed.toOption.get.inputs.map(_.data)
    val outputTensors                = parsed.toOption.get.outputs
    val outputArrays: Seq[Seq[Int]] = outputTensors.map(_.data)
    val outputElemBits: Seq[Int]    = outputTensors.map(t => TestUtil.elemBitsOf(t.element_type))
    val outputBeatsPerTile: Seq[Int] = outputElemBits.map(bits => (n * n * bits) / testConfig.axiStreamWidth)

    // Phase 3: Simulate hardware
    simulate(new ATA8(testConfig, msCfg)) { dut =>
      val ops = new AtanTestOps(dut.clock, maxCycles)

      // Preload memory for each preload entry
      fn.preloads.foreach { preload =>
        ops.preloadMem(dut.io.hostIn, preload, msCfg, reverseBeats = false, sizeAsBeatSpan = true)
      }

      // Stream all assembled instructions to the hardware
      for (inst <- insts) {
        ops.sendInst(dut.io.AXIST_inInst, inst)
      }

      val execStart = ops.totalCycles

      // Feed each input array via AXIST_inData; rows are reversed to match
      // the output access pattern (((n - 1) - row) * n + col).
      inputArrays.foreach { inputArr =>
        val rows = (0 until n).map { i =>
          TestUtil.packRow((0 until n).map(col => inputArr(i * n + col)))
        }
        ops.feedLoadData(dut.io.AXIST_inData, rows)
      }

      // Collect output from AXIST_out
      val outputBeats = ops.collectStoreRows(dut.io.AXIST_out, n)
      val execEnd = ops.totalCycles

      println(f"[E2E] total cycles     : ${ops.totalCycles}%d")
      println(f"[E2E] execution cycles : ${execEnd - execStart}%d (first load beat → last store beat)")

      // Phase 4: Verify against golden reference
      for (tileIdx <- outputArrays.indices) {
        var beatOffset = 0

        val elemBits  = outputElemBits(tileIdx)
        val nBeats    = outputBeatsPerTile(tileIdx)
        val tileBeats = outputBeats.slice(beatOffset, beatOffset + nBeats)
        beatOffset += nBeats
        val rows = tileBeats.flatMap(TestUtil.unpackBeat(_, elemBits, testConfig.axiStreamWidth)).grouped(n).toIndexedSeq

        val totalMismatches = ops.compareOutput(outputArrays(tileIdx), rows)
        info(s"total mismatches: $totalMismatches / ${outputArrays.length * n * n}")
        assert(totalMismatches == 0, s"$totalMismatches mismatches found (see error matrices above)")
      }
    }
  }
}
