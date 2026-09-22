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

class ScalingTest extends AnyFreeSpec with Matchers with ChiselSim {

  val n         = 16
  val maxCycles = 200000

  implicit val testConfig: Configuration = Configuration.large16x16()
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

  def runScalingCase(name: String): Unit = {
    val eaacPath = s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.eaac"
    val hexPath  = s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.memhex"

    val initData = mcc.MccHexReader(hexPath)

    val asm = new Assembler(AssemblerConfig(
      semaphoreGenerationWidth = testConfig.semaphoreGenerationWidth,
    ))
    val assembled = asm.assemble(ByteBuffer.wrap(Files.readAllBytes(Paths.get(eaacPath))))
    val fn = assembled.functions.head

    case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
    case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

    val jsonStr = Source.fromFile(s"/home/karlhk/dtu/Thesis/hardware/ATAN/test/${name}/${name}.reference.json").mkString
    val parsed = for {
      json  <- parse(jsonStr)
      model <- json.as[ModelIO]
    } yield model

    val inputArrays: Seq[Seq[Int]]  = parsed.toOption.get.inputs.map(_.data)
    val outputTensors                = parsed.toOption.get.outputs
    val outputArrays: Seq[Seq[Int]] = outputTensors.map(_.data)
    val outputElemBits: Seq[Int]    = outputTensors.map(t => TestUtil.elemBitsOf(t.element_type))
    val outputBeatsPerTile: Seq[Int] = outputElemBits.map(bits => (n * n * bits) / testConfig.axiStreamWidth)

    info(s"instructions: ${fn.instructions.length}, inputs: ${inputArrays.length}, outputs: ${outputArrays.length}")

    simulate(new AtanMcc16x16DUT(testConfig, memCfgBase = msCfg)) { dut =>
      val ops = new AtanTestOps(dut.clock, maxCycles)

      dut.reset.poke(true.B)
      ops.step(5)
      dut.reset.poke(false.B)

      fn.preloads.foreach { preload =>
        ops.preloadMem(dut.io.hostIn, preload, msCfg)
      }
      println("Preload finished !!!")

      ops.loadImem(dut.io.hostInRiscV, initData)

      ops.waitFor(dut.io.mccInitDone.peek().litToBoolean, "mcc imem init done")
      println(s"mcc imem loaded after ${ops.totalCycles} cycles")

      fn.instructions.foreach(ops.sendInst(dut.io.AXIST_inInst, _))
      println(s"instructions loaded after ${ops.totalCycles} cycles")

      inputArrays.zipWithIndex.foreach { case (arr, argIdx) =>
        val rows = (0 until n).map { i =>
          TestUtil.packRow((0 until n).map(col => arr(i * n + col)))
        }
        ops.feedLoadData(dut.io.AXIST_inData, rows)
      }
      info(s"input data loaded after ${ops.totalCycles} cycles")

      val outputBeats = ops.collectStoreBeats(dut.io.AXIST_out, outputBeatsPerTile.sum)
      info(s"total cycles: ${ops.totalCycles}")
      /*
      var totalMismatches = 0
      var beatOffset = 0
      for (tileIdx <- outputArrays.indices) {
        val elemBits  = outputElemBits(tileIdx)
        val nBeats    = outputBeatsPerTile(tileIdx)
        val tileBeats = outputBeats.slice(beatOffset, beatOffset + nBeats)
        beatOffset += nBeats
        val rows = tileBeats.flatMap(TestUtil.unpackBeat(_, elemBits, testConfig.axiStreamWidth)).grouped(n).toIndexedSeq
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
