// Drop into ATAN at: src/test/scala/ACCEL/MLIR/MLIRMemrefUnitScaleTest.scala
package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class MLIRMemrefUnitScaleTest extends AnyFreeSpec with Matchers
    with ChiselSim with MLIRMemrefUnitTestHelpers {

  override val maxCycles = 8000

  implicit val c: Configuration =
    Configuration.default().withBus(_.copy(dataBusSize = 4))

  val scaleSv =
    "/home/karlhk/dtu/Thesis/MLIR/circt-experimentation/examples/scale.sv"

  def newUnit() = new MLIRMemrefUnit(scaleSv, "scale", Seq(8), Seq(8))

  // Shared inputs / outputs across the sem-variant tests.
  val input  = Seq(0x01L, 0x02L, 0x03L, 0x04L, 0x05L, 0x06L, 0x07L, 0x08L)
  val output = input.map(_ * 2)

  "MLIRMemrefUnit(scale, 1x1) scales an 8-element vector" in {
    simulate(newUnit()) { dut =>
      setupIdle(dut); dut.clock.step()
      dut.io.in.ready.expect(true.B)

      sendInstr(dut,
        srcs = Seq(AddrSpec(0x100)),
        dsts = Seq(AddrSpec(0x200)))

      respondTLGet(dut, dut.io.scratchIn(0),  0x100, input,  "scratchIn(0)")
      respondTLPut(dut, dut.io.scratchOut(0), 0x200, output, "scratchOut(0)")

      waitFor(dut)(dut.io.in.ready.peek().litToBoolean, "back to idle")
    }
  }

  "MLIRMemrefUnit(scale) handles input semaphore" in {
    simulate(newUnit()) { dut =>
      setupIdle(dut); dut.clock.step()

      val semBase = 0x10L  // consumer: acquire/decrement @+0, release @+1.
      sendInstr(dut,
        srcs = Seq(AddrSpec(0x100, semBase = Some(semBase))),
        dsts = Seq(AddrSpec(0x200)))

      // Read DMA: sem acquire+decrement, then Get, then sem release.
      respondSem(dut, dut.io.readSemaphoreIF(0), semBase,
                 ArithmeticDataParam.AQGREQ, "readSem(0) ACQGREQ")
      respondSem(dut, dut.io.readSemaphoreIF(0), semBase,
                 ArithmeticDataParam.SUBU,   "readSem(0) SUBU")
      respondTLGet(dut, dut.io.scratchIn(0), 0x100, input, "scratchIn(0)")
      respondSem(dut, dut.io.readSemaphoreIF(0), semBase + 1,
                 ArithmeticDataParam.ADDU,   "readSem(0) ADDU")

      // Store phase: plain (no write sem).
      respondTLPut(dut, dut.io.scratchOut(0), 0x200, output, "scratchOut(0)")

      waitFor(dut)(dut.io.in.ready.peek().litToBoolean, "back to idle")
    }
  }

  "MLIRMemrefUnit(scale) handles output semaphore" in {
    simulate(newUnit()) { dut =>
      setupIdle(dut); dut.clock.step()

      val semBase = 0x20L  // producer: acquire/decrement @+1, release @+0.
      sendInstr(dut,
        srcs = Seq(AddrSpec(0x100)),
        dsts = Seq(AddrSpec(0x200, semBase = Some(semBase))))

      // Load phase: plain (no read sem).
      respondTLGet(dut, dut.io.scratchIn(0), 0x100, input, "scratchIn(0)")

      // Write DMA: sem acquire+decrement, then Put burst, then sem release.
      respondSem(dut, dut.io.writeSemaphoreIF(0), semBase + 1,
                 ArithmeticDataParam.AQGREQ, "writeSem(0) ACQGREQ")
      respondSem(dut, dut.io.writeSemaphoreIF(0), semBase + 1,
                 ArithmeticDataParam.SUBU,   "writeSem(0) SUBU")
      respondTLPut(dut, dut.io.scratchOut(0), 0x200, output, "scratchOut(0)")
      respondSem(dut, dut.io.writeSemaphoreIF(0), semBase,
                 ArithmeticDataParam.ADDU,   "writeSem(0) ADDU")

      waitFor(dut)(dut.io.in.ready.peek().litToBoolean, "back to idle")
    }
  }

  "MLIRMemrefUnit(scale) handles both semaphores" in {
    simulate(newUnit()) { dut =>
      setupIdle(dut); dut.clock.step()

      val readBase  = 0x10L
      val writeBase = 0x20L
      sendInstr(dut,
        srcs = Seq(AddrSpec(0x100, semBase = Some(readBase))),
        dsts = Seq(AddrSpec(0x200, semBase = Some(writeBase))))

      // Read sem (during sLoad).
      respondSem(dut, dut.io.readSemaphoreIF(0), readBase,
                 ArithmeticDataParam.AQGREQ, "readSem(0) ACQGREQ")
      respondSem(dut, dut.io.readSemaphoreIF(0), readBase,
                 ArithmeticDataParam.SUBU,   "readSem(0) SUBU")
      respondTLGet(dut, dut.io.scratchIn(0), 0x100, input, "scratchIn(0)")
      respondSem(dut, dut.io.readSemaphoreIF(0), readBase + 1,
                 ArithmeticDataParam.ADDU,   "readSem(0) ADDU")

      // Write sem (during sStore).
      respondSem(dut, dut.io.writeSemaphoreIF(0), writeBase + 1,
                 ArithmeticDataParam.AQGREQ, "writeSem(0) ACQGREQ")
      respondSem(dut, dut.io.writeSemaphoreIF(0), writeBase + 1,
                 ArithmeticDataParam.SUBU,   "writeSem(0) SUBU")
      respondTLPut(dut, dut.io.scratchOut(0), 0x200, output, "scratchOut(0)")
      respondSem(dut, dut.io.writeSemaphoreIF(0), writeBase,
                 ArithmeticDataParam.ADDU,   "writeSem(0) ADDU")

      waitFor(dut)(dut.io.in.ready.peek().litToBoolean, "back to idle")
    }
  }
}
