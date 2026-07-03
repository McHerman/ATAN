package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class MemDMAPipelineTest extends AnyFreeSpec with Matchers with ChiselSim {

  val maxCycles = 500

  implicit val c: Configuration = Configuration.default()

  val fullConfig = TLDMAConfig(read = true, write = true, semaphore = true)

  // ── Helpers ─────────────────────────────────────────────────────────────────

  def waitFor(dut: TLDMA)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      dut.clock.step()
      cycles += 1
    }
  }

  def pokeTLDIdle(dut: TLDMA): Unit = {
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

  def pokeSemIdle(dut: TLDMA): Unit = {
    val sem = dut.io.semaphoreIF.get
    sem.a.ready.poke(false.B)
    sem.d.valid.poke(false.B)
    sem.d.bits.opcode.poke(0.U)
    sem.d.bits.param.poke(0.U)
    sem.d.bits.size.poke(0.U)
    sem.d.bits.source.poke(0.U)
    sem.d.bits.sink.poke(0.U)
    sem.d.bits.denied.poke(0.U)
    sem.d.bits.data.poke(0.U)
    sem.d.bits.corrupt.poke(0.U)
  }

  def pokeDescNoSem(dut: TLDMA): Unit = {
    dut.io.interface.descriptor.bits(0).semaphore.get.semEnable.poke(false.B)
    dut.io.interface.descriptor.bits(0).semaphore.get.semAddr.poke(0.U)
    dut.io.interface.descriptor.bits(0).semaphore.get.semStepSize.poke(0.U)
    dut.io.interface.descriptor.bits(0).semaphore.get.mode.poke(0.U)
  }

  /** Accept a semaphoreIF A-channel request and return an AccessAckData. */
  def completeSemOp(dut: TLDMA): Unit = {
    val sem = dut.io.semaphoreIF.get
    sem.a.ready.poke(true.B)
    waitFor(dut)(sem.a.valid.peek().litToBoolean, "semaphoreIF.a.valid")
    dut.clock.step()
    sem.a.ready.poke(false.B)

    waitFor(dut)(sem.d.ready.peek().litToBoolean, "semaphoreIF.d.ready")
    sem.d.valid.poke(true.B)
    sem.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
    dut.clock.step()
    sem.d.valid.poke(false.B)
  }

  // ── Tests ────────────────────────────────────────────────────────────────────

  "MemDMAPipeline should be idle on reset" in {
    simulate(new TLDMA(fullConfig)) { dut =>
      dut.io.interface.descriptor.ready.expect(true.B)
      dut.io.tl.a.valid.expect(false.B)
      dut.io.interface.response.valid.expect(false.B)
    }
  }

  "MemDMAPipeline should handle single-beat write without semaphores" in {
    simulate(new TLDMA(fullConfig)) { dut =>
      pokeTLDIdle(dut)
      pokeSemIdle(dut)
      dut.io.interface.response.ready.poke(false.B)
      dut.io.dataIn.get.response.bits.readData.poke(0.U)
      dut.io.dataIn.get.request.ready.poke(false.B)
      dut.io.dataOut.get.ready.poke(false.B)

      dut.io.interface.descriptor.ready.expect(true.B)

      dut.io.interface.descriptor.valid.poke(true.B)
      dut.io.interface.descriptor.bits(0).addr.poke(0x100.U)
      dut.io.interface.descriptor.bits(0).size.poke(c.dataBusSize.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)
      pokeDescNoSem(dut)

      dut.clock.step()
      dut.io.interface.descriptor.valid.poke(false.B)

      dut.io.dataIn.get.response.bits.readData.poke(0xAB.U)
      dut.io.dataIn.get.request.ready.poke(true.B)

      waitFor(dut)(dut.io.tl.a.valid.peek().litToBoolean, "tl.a.valid")
      dut.io.tl.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      dut.io.tl.a.bits.address.expect(0x100.U)
      dut.io.tl.a.bits.size.expect(c.dataBusSize.U)
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
    simulate(new TLDMA(fullConfig)) { dut =>
      val baseAddr = 0x200
      val nBeats   = 4
      val size     = nBeats * c.dataBusSize
      val beatData = Array(0x10, 0x20, 0x30, 0x40)

      pokeTLDIdle(dut)
      pokeSemIdle(dut)
      dut.io.interface.response.ready.poke(false.B)
      dut.io.dataIn.get.response.bits.readData.poke(0.U)
      dut.io.dataIn.get.request.ready.poke(false.B)
      dut.io.dataOut.get.ready.poke(false.B)

      dut.io.interface.descriptor.valid.poke(true.B)
      dut.io.interface.descriptor.bits(0).addr.poke(baseAddr.U)
      dut.io.interface.descriptor.bits(0).size.poke(size.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)
      pokeDescNoSem(dut)

      dut.clock.step()
      dut.io.interface.descriptor.valid.poke(false.B)

      for (beat <- 0 until nBeats) {
        dut.io.dataIn.get.response.bits.readData.poke(beatData(beat).U)
        dut.io.dataIn.get.request.ready.poke(true.B)
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
    simulate(new TLDMA(fullConfig)) { dut =>
      val semAddr      = 0x06
      val beatsPerStep = 4
      val stepSize     = beatsPerStep * c.dataBusSize

      pokeTLDIdle(dut)
      pokeSemIdle(dut)
      dut.io.interface.response.ready.poke(false.B)
      dut.io.dataIn.get.response.bits.readData.poke(0xCD.U)
      dut.io.dataIn.get.request.ready.poke(true.B)
      dut.io.dataOut.get.ready.poke(false.B)

      dut.io.interface.descriptor.valid.poke(true.B)
      dut.io.interface.descriptor.bits(0).addr.poke(0x200.U)
      dut.io.interface.descriptor.bits(0).size.poke(stepSize.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)
      dut.io.interface.descriptor.bits(0).semaphore.get.semEnable.poke(true.B)
      dut.io.interface.descriptor.bits(0).semaphore.get.semAddr.poke(semAddr.U)
      dut.io.interface.descriptor.bits(0).semaphore.get.semStepSize.poke(stepSize.U)
      dut.io.interface.descriptor.bits(0).semaphore.get.mode.poke(0.U)
      dut.clock.step()
      dut.io.interface.descriptor.valid.poke(false.B)

      // writeEn=true → producer: acquire on base+1 (emptyReg), release on base+0 (fullReg)

      // 1. Acquire (AQGREQ on emptyReg)
      waitFor(dut)(dut.io.semaphoreIF.get.a.valid.peek().litToBoolean, "AQGREQ")
      dut.io.semaphoreIF.get.a.bits.param.expect(ArithmeticDataParam.AQGREQ)
      dut.io.semaphoreIF.get.a.bits.address.expect((semAddr + 1).U)
      dut.io.tl.a.valid.expect(false.B)
      completeSemOp(dut)

      // 1b. Decrement (ADD negative on emptyReg)
      waitFor(dut)(dut.io.semaphoreIF.get.a.valid.peek().litToBoolean, "ADD decrement")
      dut.io.semaphoreIF.get.a.bits.param.expect(ArithmeticDataParam.ADD)
      dut.io.semaphoreIF.get.a.bits.address.expect((semAddr + 1).U)
      completeSemOp(dut)

      // 2. Write
      for (_ <- 0 until beatsPerStep) {
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

      // 3. Release (ADD positive on fullReg, remaining → 0, so goes to respond)
      waitFor(dut)(dut.io.semaphoreIF.get.a.valid.peek().litToBoolean, "ADD release")
      dut.io.semaphoreIF.get.a.bits.param.expect(ArithmeticDataParam.ADD)
      dut.io.semaphoreIF.get.a.bits.address.expect(semAddr.U)
      dut.io.semaphoreIF.get.a.bits.data.expect(stepSize.U)
      completeSemOp(dut)

      waitFor(dut)(dut.io.interface.response.valid.peek().litToBoolean, "response.valid")
      dut.io.interface.response.ready.poke(true.B)
      dut.clock.step()
      dut.io.interface.response.ready.poke(false.B)

      dut.io.interface.descriptor.ready.expect(true.B)
    }
  }

  "MemDMAPipeline RestartOnStep: multi-step read repeats acquire→read→release per step" in {
    simulate(new TLDMA(fullConfig)) { dut =>
      val baseAddr     = 0x300
      val semAddr      = 0x08
      val beatsPerStep = 2
      val nSteps       = 3
      val stepSize     = beatsPerStep * c.dataBusSize
      val totalSize    = stepSize * nSteps

      pokeTLDIdle(dut)
      pokeSemIdle(dut)
      dut.io.interface.response.ready.poke(false.B)
      dut.io.dataIn.get.request.ready.poke(false.B)
      dut.io.dataOut.get.ready.poke(true.B)

      dut.io.interface.descriptor.valid.poke(true.B)
      dut.io.interface.descriptor.bits(0).addr.poke(baseAddr.U)
      dut.io.interface.descriptor.bits(0).size.poke(totalSize.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(false.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)
      dut.io.interface.descriptor.bits(0).semaphore.get.semEnable.poke(true.B)
      dut.io.interface.descriptor.bits(0).semaphore.get.semAddr.poke(semAddr.U)
      dut.io.interface.descriptor.bits(0).semaphore.get.semStepSize.poke(stepSize.U)
      dut.io.interface.descriptor.bits(0).semaphore.get.mode.poke(0.U)
      dut.clock.step()
      dut.io.interface.descriptor.valid.poke(false.B)

      // writeEn=false → consumer: acquire on base+0 (fullReg), release on base+1 (emptyReg)

      for (step <- 0 until nSteps) {
        val expectedAddr = (baseAddr + step * stepSize).U

        // 1. Acquire (AQGREQ on fullReg)
        waitFor(dut)(dut.io.semaphoreIF.get.a.valid.peek().litToBoolean, s"AQGREQ step $step")
        dut.io.semaphoreIF.get.a.bits.param.expect(ArithmeticDataParam.AQGREQ)
        dut.io.semaphoreIF.get.a.bits.address.expect(semAddr.U)
        completeSemOp(dut)

        // 1b. Decrement (ADD negative on fullReg)
        waitFor(dut)(dut.io.semaphoreIF.get.a.valid.peek().litToBoolean, s"ADD decrement step $step")
        dut.io.semaphoreIF.get.a.bits.param.expect(ArithmeticDataParam.ADD)
        dut.io.semaphoreIF.get.a.bits.address.expect(semAddr.U)
        completeSemOp(dut)

        // 2. Read at advancing address
        waitFor(dut)(dut.io.tl.a.valid.peek().litToBoolean, s"Get step $step")
        dut.io.tl.a.bits.opcode.expect(TilelinkOpcodes.Get)
        dut.io.tl.a.bits.address.expect(expectedAddr)
        dut.io.tl.a.bits.size.expect(stepSize.U)
        dut.io.tl.a.ready.poke(true.B)
        dut.clock.step()
        dut.io.tl.a.ready.poke(false.B)

        for (beat <- 0 until beatsPerStep) {
          waitFor(dut)(dut.io.tl.d.ready.peek().litToBoolean, s"D beat $step/$beat")
          dut.io.tl.d.valid.poke(true.B)
          dut.io.tl.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
          dut.io.tl.d.bits.data.poke((step * stepSize + beat).U)
          dut.clock.step()
          dut.io.tl.d.valid.poke(false.B)
        }

        // 3. Release (ADD positive on emptyReg)
        waitFor(dut)(dut.io.semaphoreIF.get.a.valid.peek().litToBoolean, s"ADD release step $step")
        dut.io.semaphoreIF.get.a.bits.param.expect(ArithmeticDataParam.ADD)
        dut.io.semaphoreIF.get.a.bits.address.expect((semAddr + 1).U)
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
