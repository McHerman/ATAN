// Drop into ATAN at: src/test/scala/ACCEL/MLIR/MLIRMemrefUnitConv2DTest.scala
package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class MLIRMemrefUnitConv2DTest extends AnyFreeSpec with Matchers
    with ChiselSim with MLIRMemrefUnitTestHelpers {

  override val maxCycles = 12000

  implicit val c: Configuration =
    Configuration.default().withBus(_.copy(dataBusSize = 4))

  val convSv =
    "/home/karlhk/dtu/Thesis/MLIR/circt-experimentation/examples/conv_2d.sv"

  def newUnit() = new MLIRMemrefUnit(convSv, "conv2d", Seq(16, 9), Seq(4))

  val image  = (1L to 16L).toSeq
  val filter = Seq.fill(9)(1L)
  val output = Seq(
    1+2+3+5+6+7+9+10+11,
    2+3+4+6+7+8+10+11+12,
    5+6+7+9+10+11+13+14+15,
    6+7+8+10+11+12+14+15+16,
  ).map(_.toLong)

  "MLIRMemrefUnit(conv2d, 2x1) computes a 4x4 * 3x3 -> 2x2 convolution" in {
    simulate(newUnit()) { dut =>
      setupIdle(dut); dut.clock.step()
      dut.io.in.ready.expect(true.B)

      sendInstr(dut,
        srcs = Seq(AddrSpec(0x100), AddrSpec(0x200)),
        dsts = Seq(AddrSpec(0x300)))

      respondTLGet(dut, dut.io.scratchIn(0),  0x100, image,  "scratchIn(0) image")
      respondTLGet(dut, dut.io.scratchIn(1),  0x200, filter, "scratchIn(1) filter")
      respondTLPut(dut, dut.io.scratchOut(0), 0x300, output, "scratchOut(0)")

      waitFor(dut)(dut.io.in.ready.peek().litToBoolean, "back to idle")
    }
  }

  "MLIRMemrefUnit(conv2d) handles semaphore on image input only" in {
    simulate(newUnit()) { dut =>
      setupIdle(dut); dut.clock.step()

      val imageSemBase = 0x10L
      sendInstr(dut,
        srcs = Seq(
          AddrSpec(0x100, semBase = Some(imageSemBase)),
          AddrSpec(0x200),  // filter unguarded
        ),
        dsts = Seq(AddrSpec(0x300)))

      // Image DMA acquire+decrement happen first; filter DMA holds its Get
      // on scratchIn(1) in the meantime.
      respondSem(dut, dut.io.readSemaphoreIF(0), imageSemBase,
                 ArithmeticDataParam.AQGREQ, "imageSem ACQGREQ")
      respondSem(dut, dut.io.readSemaphoreIF(0), imageSemBase,
                 ArithmeticDataParam.SUBU,   "imageSem SUBU")

      // Now both DMAs can issue their Gets in parallel; service in either
      // order (each DMA holds A.valid until accepted).
      respondTLGet(dut, dut.io.scratchIn(0),  0x100, image,  "scratchIn(0) image")
      respondTLGet(dut, dut.io.scratchIn(1),  0x200, filter, "scratchIn(1) filter")

      // Image release after its data finishes.
      respondSem(dut, dut.io.readSemaphoreIF(0), imageSemBase + 1,
                 ArithmeticDataParam.ADDU,   "imageSem ADDU")

      respondTLPut(dut, dut.io.scratchOut(0), 0x300, output, "scratchOut(0)")

      waitFor(dut)(dut.io.in.ready.peek().litToBoolean, "back to idle")
    }
  }

  "MLIRMemrefUnit(conv2d) handles semaphores on all three buses" in {
    simulate(newUnit()) { dut =>
      setupIdle(dut); dut.clock.step()

      val imageBase  = 0x10L
      val filterBase = 0x14L
      val writeBase  = 0x20L
      sendInstr(dut,
        srcs = Seq(
          AddrSpec(0x100, semBase = Some(imageBase)),
          AddrSpec(0x200, semBase = Some(filterBase)),
        ),
        dsts = Seq(AddrSpec(0x300, semBase = Some(writeBase))))

      // Both read DMAs do acquire+decrement concurrently (independent buses).
      respondSem(dut, dut.io.readSemaphoreIF(0), imageBase,
                 ArithmeticDataParam.AQGREQ, "imageSem ACQGREQ")
      respondSem(dut, dut.io.readSemaphoreIF(1), filterBase,
                 ArithmeticDataParam.AQGREQ, "filterSem ACQGREQ")
      respondSem(dut, dut.io.readSemaphoreIF(0), imageBase,
                 ArithmeticDataParam.SUBU,   "imageSem SUBU")
      respondSem(dut, dut.io.readSemaphoreIF(1), filterBase,
                 ArithmeticDataParam.SUBU,   "filterSem SUBU")

      respondTLGet(dut, dut.io.scratchIn(0),  0x100, image,  "scratchIn(0) image")
      respondTLGet(dut, dut.io.scratchIn(1),  0x200, filter, "scratchIn(1) filter")

      respondSem(dut, dut.io.readSemaphoreIF(0), imageBase + 1,
                 ArithmeticDataParam.ADDU,   "imageSem ADDU")
      respondSem(dut, dut.io.readSemaphoreIF(1), filterBase + 1,
                 ArithmeticDataParam.ADDU,   "filterSem ADDU")

      // Write side runs in sStore after compute.
      respondSem(dut, dut.io.writeSemaphoreIF(0), writeBase + 1,
                 ArithmeticDataParam.AQGREQ, "writeSem ACQGREQ")
      respondSem(dut, dut.io.writeSemaphoreIF(0), writeBase + 1,
                 ArithmeticDataParam.SUBU,   "writeSem SUBU")
      respondTLPut(dut, dut.io.scratchOut(0), 0x300, output, "scratchOut(0)")
      respondSem(dut, dut.io.writeSemaphoreIF(0), writeBase,
                 ArithmeticDataParam.ADDU,   "writeSem ADDU")

      waitFor(dut)(dut.io.in.ready.peek().litToBoolean, "back to idle")
    }
  }
}
