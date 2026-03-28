package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class MemDMAPipelineTest extends AnyFreeSpec with Matchers with ChiselSim {

  val maxCycles = 500

  implicit val c: Configuration = Configuration.default()

  // ── Helpers ─────────────────────────────────────────────────────────────────

  def waitFor(dut: MemDMAPipeline)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      dut.clock.step()
      cycles += 1
    }
  }

  def pokeTLDIdle(dut: MemDMAPipeline): Unit = {
    dut.io.tl.a.ready.poke(false.B)
    dut.io.tl.d.valid.poke(false.B)
    dut.io.tl.d.bits.opcode.poke(0.U)
    dut.io.tl.d.bits.param.poke(0.U)
    dut.io.tl.d.bits.size.poke(0.U)
    dut.io.tl.d.bits.source.poke(0.U)
    dut.io.tl.d.bits.sink.poke(0.U)
    dut.io.tl.d.bits.denied.poke(0.U)
    dut.io.tl.d.bits.data.poke(0.U)
    dut.io.tl.d.bits.corrupt.poke(0.U)
  }

  def pokeSemIdle(dut: MemDMAPipeline): Unit = {
    dut.io.semaphoreIF.a.ready.poke(false.B)
    dut.io.semaphoreIF.d.valid.poke(false.B)
    dut.io.semaphoreIF.d.bits.opcode.poke(0.U)
    dut.io.semaphoreIF.d.bits.param.poke(0.U)
    dut.io.semaphoreIF.d.bits.size.poke(0.U)
    dut.io.semaphoreIF.d.bits.source.poke(0.U)
    dut.io.semaphoreIF.d.bits.sink.poke(0.U)
    dut.io.semaphoreIF.d.bits.denied.poke(0.U)
    dut.io.semaphoreIF.d.bits.data.poke(0.U)
    dut.io.semaphoreIF.d.bits.corrupt.poke(0.U)
  }

  def pokeDescNoSem(dut: MemDMAPipeline): Unit = {
    dut.io.interface.descriptor.bits(0).semaphore.semEnable.poke(false.B)
    dut.io.interface.descriptor.bits(0).semaphore.semAddr.poke(0.U)
    dut.io.interface.descriptor.bits(0).semaphore.semStepSize.poke(0.U)
    dut.io.interface.descriptor.bits(0).semaphore.mode.poke(0.U)
  }

  /** Accept a semaphoreIF A-channel request and return an AccessAckData. */
  def completeSemOp(dut: MemDMAPipeline): Unit = {
    dut.io.semaphoreIF.a.ready.poke(true.B)
    waitFor(dut)(dut.io.semaphoreIF.a.valid.peek().litToBoolean, "semaphoreIF.a.valid")
    dut.clock.step()
    dut.io.semaphoreIF.a.ready.poke(false.B)

    waitFor(dut)(dut.io.semaphoreIF.d.ready.peek().litToBoolean, "semaphoreIF.d.ready")
    dut.io.semaphoreIF.d.valid.poke(true.B)
    dut.io.semaphoreIF.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
    dut.clock.step()
    dut.io.semaphoreIF.d.valid.poke(false.B)
  }

  // ── Tests ────────────────────────────────────────────────────────────────────

  "MemDMAPipeline should be idle on reset" in {
    simulate(new MemDMAPipeline()) { dut =>
      dut.io.interface.descriptor.ready.expect(true.B)
      dut.io.tl.a.valid.expect(false.B)
      dut.io.interface.response.valid.expect(false.B)
    }
  }

  "MemDMAPipeline should handle single-beat write without semaphores" in {
    simulate(new MemDMAPipeline()) { dut =>
      pokeTLDIdle(dut)
      pokeSemIdle(dut)
      dut.io.interface.response.ready.poke(false.B)
      dut.io.dataIn.bits.poke(0.U)
      dut.io.dataIn.valid.poke(false.B)
      dut.io.dataOut.ready.poke(false.B)

      dut.io.interface.descriptor.ready.expect(true.B)

      dut.io.interface.descriptor.valid.poke(true.B)
      dut.io.interface.descriptor.bits(0).addr.poke(0x100.U)
      dut.io.interface.descriptor.bits(0).size.poke(1.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)
      pokeDescNoSem(dut)

      dut.clock.step()
      dut.io.interface.descriptor.valid.poke(false.B)

      dut.io.dataIn.bits.poke(0xAB.U)
      dut.io.dataIn.valid.poke(true.B)

      waitFor(dut)(dut.io.tl.a.valid.peek().litToBoolean, "tl.a.valid")
      dut.io.tl.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      dut.io.tl.a.bits.address.expect(0x100.U)
      dut.io.tl.a.bits.size.expect(1.U)
      dut.io.tl.a.bits.data.expect(0xAB.U)

      dut.io.tl.a.ready.poke(true.B)
      dut.clock.step()
      dut.io.tl.a.ready.poke(false.B)

      waitFor(dut)(dut.io.tl.d.ready.peek().litToBoolean, "tl.d.ready")
      dut.io.tl.d.valid.poke(true.B)
      dut.io.tl.d.bits.opcode.poke(TilelinkOpcodes.AccessAck)
      dut.clock.step()
      dut.io.tl.d.valid.poke(false.B)

      waitFor(dut)(dut.io.interface.response.valid.peek().litToBoolean, "response.valid")
      dut.io.interface.response.ready.poke(true.B)
      dut.clock.step()
      dut.io.interface.response.ready.poke(false.B)

      dut.io.interface.descriptor.ready.expect(true.B)
    }
  }

  "MemDMAPipeline should handle multi-beat write without semaphores" in {
    simulate(new MemDMAPipeline()) { dut =>
      val baseAddr = 0x200
      val size     = 4
      val beatData = Array(0x10, 0x20, 0x30, 0x40)

      pokeTLDIdle(dut)
      pokeSemIdle(dut)
      dut.io.interface.response.ready.poke(false.B)
      dut.io.dataIn.bits.poke(0.U)
      dut.io.dataIn.valid.poke(false.B)
      dut.io.dataOut.ready.poke(false.B)

      dut.io.interface.descriptor.valid.poke(true.B)
      dut.io.interface.descriptor.bits(0).addr.poke(baseAddr.U)
      dut.io.interface.descriptor.bits(0).size.poke(size.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)
      pokeDescNoSem(dut)

      dut.clock.step()
      dut.io.interface.descriptor.valid.poke(false.B)

      for (beat <- 0 until size) {
        dut.io.dataIn.bits.poke(beatData(beat).U)
        dut.io.dataIn.valid.poke(true.B)
        waitFor(dut)(dut.io.tl.a.valid.peek().litToBoolean, s"tl.a.valid beat $beat")
        dut.io.tl.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
        dut.io.tl.a.bits.address.expect(baseAddr.U)
        dut.io.tl.a.bits.data.expect(beatData(beat).U)
        dut.io.tl.a.ready.poke(true.B)
        dut.clock.step()
        dut.io.tl.a.ready.poke(false.B)
      }

      waitFor(dut)(dut.io.tl.d.ready.peek().litToBoolean, "tl.d.ready")
      dut.io.tl.d.valid.poke(true.B)
      dut.io.tl.d.bits.opcode.poke(TilelinkOpcodes.AccessAck)
      dut.clock.step()
      dut.io.tl.d.valid.poke(false.B)

      waitFor(dut)(dut.io.interface.response.valid.peek().litToBoolean, "response.valid")
      dut.io.interface.response.ready.poke(true.B)
      dut.clock.step()
      dut.io.interface.response.ready.poke(false.B)

      dut.io.interface.descriptor.ready.expect(true.B)
    }
  }

  "MemDMAPipeline RestartOnStep: single-step write follows acquire→write→release" in {
    simulate(new MemDMAPipeline()) { dut =>
      val semAddr  = 0x06
      val stepSize = 4

      pokeTLDIdle(dut)
      pokeSemIdle(dut)
      dut.io.interface.response.ready.poke(false.B)
      dut.io.dataIn.bits.poke(0xCD.U)
      dut.io.dataIn.valid.poke(true.B)
      dut.io.dataOut.ready.poke(false.B)

      dut.io.interface.descriptor.valid.poke(true.B)
      dut.io.interface.descriptor.bits(0).addr.poke(0x200.U)
      dut.io.interface.descriptor.bits(0).size.poke(stepSize.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)
      dut.io.interface.descriptor.bits(0).semaphore.semEnable.poke(true.B)
      dut.io.interface.descriptor.bits(0).semaphore.semAddr.poke(semAddr.U)
      dut.io.interface.descriptor.bits(0).semaphore.semStepSize.poke(stepSize.U)
      dut.io.interface.descriptor.bits(0).semaphore.mode.poke(0.U)
      dut.clock.step()
      dut.io.interface.descriptor.valid.poke(false.B)

      // 1. Acquire
      waitFor(dut)(dut.io.semaphoreIF.a.valid.peek().litToBoolean, "AQGREQ")
      dut.io.semaphoreIF.a.bits.param.expect(ArithmeticDataParam.AQGREQ)
      dut.io.semaphoreIF.a.bits.address.expect(semAddr.U)
      dut.io.tl.a.valid.expect(false.B)
      completeSemOp(dut)

      // 2. Write
      for (_ <- 0 until stepSize) {
        waitFor(dut)(dut.io.tl.a.valid.peek().litToBoolean, "tl.a beat")
        dut.io.tl.a.ready.poke(true.B)
        dut.clock.step()
        dut.io.tl.a.ready.poke(false.B)
      }
      waitFor(dut)(dut.io.tl.d.ready.peek().litToBoolean, "tl.d.ready")
      dut.io.tl.d.valid.poke(true.B)
      dut.io.tl.d.bits.opcode.poke(TilelinkOpcodes.AccessAck)
      dut.clock.step()
      dut.io.tl.d.valid.poke(false.B)

      // 3. Release (remaining → 0, so goes to respond)
      waitFor(dut)(dut.io.semaphoreIF.a.valid.peek().litToBoolean, "ADDU")
      dut.io.semaphoreIF.a.bits.param.expect(ArithmeticDataParam.ADDU)
      dut.io.semaphoreIF.a.bits.address.expect(semAddr.U)
      dut.io.semaphoreIF.a.bits.data.expect(stepSize.U)
      completeSemOp(dut)

      waitFor(dut)(dut.io.interface.response.valid.peek().litToBoolean, "response.valid")
      dut.io.interface.response.ready.poke(true.B)
      dut.clock.step()
      dut.io.interface.response.ready.poke(false.B)

      dut.io.interface.descriptor.ready.expect(true.B)
    }
  }

  "MemDMAPipeline RestartOnStep: multi-step read repeats acquire→read→release per step" in {
    simulate(new MemDMAPipeline()) { dut =>
      val baseAddr  = 0x300
      val semAddr   = 0x08
      val stepSize  = 2
      val nSteps    = 3
      val totalSize = stepSize * nSteps

      pokeTLDIdle(dut)
      pokeSemIdle(dut)
      dut.io.interface.response.ready.poke(false.B)
      dut.io.dataIn.valid.poke(false.B)
      dut.io.dataOut.ready.poke(true.B)

      dut.io.interface.descriptor.valid.poke(true.B)
      dut.io.interface.descriptor.bits(0).addr.poke(baseAddr.U)
      dut.io.interface.descriptor.bits(0).size.poke(totalSize.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(false.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)
      dut.io.interface.descriptor.bits(0).semaphore.semEnable.poke(true.B)
      dut.io.interface.descriptor.bits(0).semaphore.semAddr.poke(semAddr.U)
      dut.io.interface.descriptor.bits(0).semaphore.semStepSize.poke(stepSize.U)
      dut.io.interface.descriptor.bits(0).semaphore.mode.poke(0.U)
      dut.clock.step()
      dut.io.interface.descriptor.valid.poke(false.B)

      for (step <- 0 until nSteps) {
        val expectedAddr = (baseAddr + step * stepSize).U

        // 1. Acquire
        waitFor(dut)(dut.io.semaphoreIF.a.valid.peek().litToBoolean, s"AQGREQ step $step")
        dut.io.semaphoreIF.a.bits.param.expect(ArithmeticDataParam.AQGREQ)
        dut.io.semaphoreIF.a.bits.address.expect(semAddr.U)
        completeSemOp(dut)

        // 2. Read at advancing address
        waitFor(dut)(dut.io.tl.a.valid.peek().litToBoolean, s"Get step $step")
        dut.io.tl.a.bits.opcode.expect(TilelinkOpcodes.Get)
        dut.io.tl.a.bits.address.expect(expectedAddr)
        dut.io.tl.a.bits.size.expect(stepSize.U)
        dut.io.tl.a.ready.poke(true.B)
        dut.clock.step()
        dut.io.tl.a.ready.poke(false.B)

        for (beat <- 0 until stepSize) {
          waitFor(dut)(dut.io.tl.d.ready.peek().litToBoolean, s"D beat $step/$beat")
          dut.io.tl.d.valid.poke(true.B)
          dut.io.tl.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
          dut.io.tl.d.bits.data.poke((step * stepSize + beat).U)
          dut.clock.step()
          dut.io.tl.d.valid.poke(false.B)
        }

        // 3. Release
        waitFor(dut)(dut.io.semaphoreIF.a.valid.peek().litToBoolean, s"ADDU step $step")
        dut.io.semaphoreIF.a.bits.param.expect(ArithmeticDataParam.ADDU)
        dut.io.semaphoreIF.a.bits.address.expect(semAddr.U)
        completeSemOp(dut)
      }

      waitFor(dut)(dut.io.interface.response.valid.peek().litToBoolean, "response.valid")
      dut.io.interface.response.ready.poke(true.B)
      dut.clock.step()
      dut.io.interface.response.ready.poke(false.B)

      dut.io.interface.descriptor.ready.expect(true.B)
    }
  }
}
