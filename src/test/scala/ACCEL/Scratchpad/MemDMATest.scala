package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class MemDMATest extends AnyFreeSpec with Matchers with ChiselSim {

  val maxCycles = 500

  implicit val c: Configuration = Configuration.default()

  // ── Helpers ─────────────────────────────────────────────────────────────────

  def waitFor(dut: MemDMA)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      dut.clock.step()
      cycles += 1
    }
  }

  /** Poke both TileLink memory ports to idle (test acts as TL device). */
  def pokeMemPortsIdle(dut: MemDMA): Unit = {
    for (port <- Seq(dut.io.portA, dut.io.portB)) {
      port.a.ready.poke(false.B)
      port.d.valid.poke(false.B)
      port.d.bits.opcode.poke(0.U)
      port.d.bits.param.poke(0.U)
      port.d.bits.size.poke(0.U)
      port.d.bits.source.poke(0.U)
      port.d.bits.sink.poke(0.U)
      port.d.bits.denied.poke(0.U)
      port.d.bits.data.poke(0.U)
      port.d.bits.corrupt.poke(0.U)
    }
  }

  /** Poke both semaphore pass-through ports to idle. */
  def pokeSemPortsIdle(dut: MemDMA): Unit = {
    for (semPort <- Seq(dut.io.semaphoreA, dut.io.semaphoreB)) {
      semPort.a.ready.poke(false.B)
      semPort.d.valid.poke(false.B)
      semPort.d.bits.opcode.poke(0.U)
      semPort.d.bits.param.poke(0.U)
      semPort.d.bits.size.poke(0.U)
      semPort.d.bits.source.poke(0.U)
      semPort.d.bits.sink.poke(0.U)
      semPort.d.bits.denied.poke(0.U)
      semPort.d.bits.data.poke(0.U)
      semPort.d.bits.corrupt.poke(0.U)
    }
  }

  /** Poke semaphore fields of descriptor index i to disabled/zero. */
  def pokeDescNoSem(dut: MemDMA, i: Int): Unit = {
    dut.io.interface.descriptor.bits(i).semaphore.get.semEnable.poke(false.B)
    dut.io.interface.descriptor.bits(i).semaphore.get.semAddr.poke(0.U)
    dut.io.interface.descriptor.bits(i).semaphore.get.semStepSize.poke(0.U)
    dut.io.interface.descriptor.bits(i).semaphore.get.mode.poke(0.U)
  }

  // ── Tests ────────────────────────────────────────────────────────────────────

  "MemDMA should accept a descriptor" in {
    simulate(new MemDMA()) { dut =>
      pokeMemPortsIdle(dut)
      pokeSemPortsIdle(dut)
      dut.io.interface.response.ready.poke(false.B)

      dut.io.interface.descriptor.ready.expect(true.B)

      dut.io.interface.descriptor.valid.poke(true.B)

      dut.io.interface.descriptor.bits(0).addr.poke(0x100.U)
      dut.io.interface.descriptor.bits(0).size.poke(2.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)
      pokeDescNoSem(dut, 0)

      dut.io.interface.descriptor.bits(1).addr.poke(0x200.U)
      dut.io.interface.descriptor.bits(1).size.poke(2.U)
      dut.io.interface.descriptor.bits(1).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(1).source.poke(0.U)
      dut.io.interface.descriptor.bits(1).sink.poke(0.U)
      pokeDescNoSem(dut, 1)

      dut.clock.step()
      dut.io.interface.descriptor.valid.poke(false.B)

      dut.io.interface.descriptor.ready.expect(false.B)
    }
  }

  "MemDMA should dispatch descriptors to both pipelines" in {
    simulate(new MemDMA()) { dut =>
      pokeMemPortsIdle(dut)
      pokeSemPortsIdle(dut)
      dut.io.interface.response.ready.poke(false.B)

      dut.io.interface.descriptor.valid.poke(true.B)

      // Pipeline A reads from portA; data flows via AtoB FIFO into pipeline B
      dut.io.interface.descriptor.bits(0).addr.poke(0x100.U)
      dut.io.interface.descriptor.bits(0).size.poke(1.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(false.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)
      pokeDescNoSem(dut, 0)

      // Pipeline B writes to portB using data from AtoB FIFO
      dut.io.interface.descriptor.bits(1).addr.poke(0x200.U)
      dut.io.interface.descriptor.bits(1).size.poke(1.U)
      dut.io.interface.descriptor.bits(1).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(1).source.poke(0.U)
      dut.io.interface.descriptor.bits(1).sink.poke(0.U)
      pokeDescNoSem(dut, 1)

      dut.clock.step()
      dut.io.interface.descriptor.valid.poke(false.B)
      dut.clock.step()

      // Pipeline A issues a Get on portA
      waitFor(dut)(dut.io.portA.a.valid.peek().litToBoolean, "portA.a.valid")
      dut.io.portA.a.bits.opcode.expect(TilelinkOpcodes.Get)
      dut.io.portA.a.bits.address.expect(0x100.U)

      // Accept the read and supply data so AtoB FIFO gets filled
      dut.io.portA.a.ready.poke(true.B)
      dut.clock.step()
      dut.io.portA.a.ready.poke(false.B)
      dut.io.portA.d.valid.poke(true.B)
      dut.io.portA.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
      dut.io.portA.d.bits.data.poke(0x42.U)
      dut.clock.step()
      dut.io.portA.d.valid.poke(false.B)
      dut.clock.step()

      // Pipeline B now has data and issues PutFullData on portB
      waitFor(dut)(dut.io.portB.a.valid.peek().litToBoolean, "portB.a.valid")
      dut.io.portB.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      dut.io.portB.a.bits.address.expect(0x200.U)
    }
  }

  "MemDMA should correctly transfer data" in {
    simulate(new MemDMA()) { dut =>
      val r      = new scala.util.Random
      val random = Seq.fill(128)(r.nextInt(255))

      pokeMemPortsIdle(dut)
      pokeSemPortsIdle(dut)
      dut.io.interface.response.ready.poke(false.B)

      dut.io.interface.descriptor.ready.expect(true.B)

      dut.io.interface.descriptor.valid.poke(true.B)

      // Descriptor 0: read from 0x100, size=127
      dut.io.interface.descriptor.bits(0).addr.poke(0x100.U)
      dut.io.interface.descriptor.bits(0).size.poke(127.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(false.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)
      pokeDescNoSem(dut, 0)

      // Descriptor 1: write to 0x200, size=127
      dut.io.interface.descriptor.bits(1).addr.poke(0x200.U)
      dut.io.interface.descriptor.bits(1).size.poke(127.U)
      dut.io.interface.descriptor.bits(1).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(1).source.poke(0.U)
      dut.io.interface.descriptor.bits(1).sink.poke(0.U)
      pokeDescNoSem(dut, 1)

      dut.clock.step()
      dut.io.interface.descriptor.valid.poke(false.B)

      dut.io.interface.descriptor.ready.expect(false.B)

      dut.clock.step()

      dut.io.portA.a.valid.expect(true.B)
      dut.io.portA.a.ready.poke(true.B)

      dut.io.portA.a.bits.opcode.expect(TilelinkOpcodes.Get)
      dut.io.portA.a.bits.param.expect(0.U)
      dut.io.portA.a.bits.size.expect(127.U)
      dut.io.portA.a.bits.source.expect(0.U)
      dut.io.portA.a.bits.address.expect(0x100.U)
      dut.io.portA.a.bits.corrupt.expect(0.U)

      dut.clock.step()

      dut.io.portA.d.ready.expect(true.B)
      dut.io.portA.d.valid.poke(true.B)
      dut.io.portA.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
      dut.io.portA.d.bits.param.poke(0.U)
      dut.io.portA.d.bits.size.poke(127.U)
      dut.io.portA.d.bits.source.poke(0.U)
      dut.io.portA.d.bits.sink.poke(0.U)
      dut.io.portA.d.bits.denied.poke(0.U)
      dut.io.portA.d.bits.data.poke(random(0).asUInt)
      dut.io.portA.d.bits.corrupt.poke(0.U)

      dut.clock.step()
      dut.clock.step()

      dut.io.portB.a.valid.expect(true.B)
      dut.io.portB.a.ready.poke(true.B)

      dut.io.portB.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      dut.io.portB.a.bits.param.expect(0.U)
      dut.io.portB.a.bits.size.expect(127.U)
      dut.io.portB.a.bits.address.expect(0x200.U)
      dut.io.portB.a.bits.data.expect(random(0).asUInt)
      dut.io.portB.a.bits.corrupt.expect(0.U)
    }
  }

  "MemDMA should pass ADDU semaphore requests through both pipeline ports after transfer" in {
    simulate(new MemDMA()) { dut =>
      //pokeMemPortsIdle(dut)
      //pokeSemPortsIdle(dut)

      val size = 8 // One beat
      dut.io.interface.response.ready.poke(false.B)

      // Pipeline A reads from portA; pipeline B writes to portB using data from AtoB FIFO
      dut.io.interface.descriptor.valid.poke(true.B)
      dut.io.interface.descriptor.bits(0).addr.poke(0x100.U)
      dut.io.interface.descriptor.bits(0).size.poke(size.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(false.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)
      dut.io.interface.descriptor.bits(0).semaphore.get.semEnable.poke(true.B)
      dut.io.interface.descriptor.bits(0).semaphore.get.mode.poke(0.U)
      dut.io.interface.descriptor.bits(0).semaphore.get.semAddr.poke(0x10.U)
      dut.io.interface.descriptor.bits(0).semaphore.get.semStepSize.poke(size.U)

      dut.io.interface.descriptor.bits(1).addr.poke(0x200.U)
      dut.io.interface.descriptor.bits(1).size.poke(size.U)
      dut.io.interface.descriptor.bits(1).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(1).source.poke(0.U)
      dut.io.interface.descriptor.bits(1).sink.poke(0.U)
      dut.io.interface.descriptor.bits(1).semaphore.get.semEnable.poke(true.B)
      dut.io.interface.descriptor.bits(1).semaphore.get.mode.poke(0.U)
      dut.io.interface.descriptor.bits(1).semaphore.get.semAddr.poke(0x20.U)
      dut.io.interface.descriptor.bits(1).semaphore.get.semStepSize.poke(size.U)

      dut.clock.step()
      dut.io.interface.descriptor.valid.poke(false.B)
      dut.clock.step()

      // Pipeline A: writeEn=false → consumer → AQGREQ on base+0
      // Pipeline B: writeEn=true  → producer → AQGREQ on base+1
      waitFor(dut)(dut.io.semaphoreA.a.valid.peek().litToBoolean, "semaphoreA AQGREQ")
      dut.io.semaphoreA.a.bits.param.expect(ArithmeticDataParam.AQGREQ)
      dut.io.semaphoreA.a.bits.address.expect(0x10.U)  // consumer: base+0

      waitFor(dut)(dut.io.semaphoreB.a.valid.peek().litToBoolean, "semaphoreB AQGREQ")
      dut.io.semaphoreB.a.bits.param.expect(ArithmeticDataParam.AQGREQ)
      dut.io.semaphoreB.a.bits.address.expect(0x21.U)  // producer: base+1

      // Complete both acquires
      dut.io.semaphoreA.a.ready.poke(true.B)
      dut.io.semaphoreB.a.ready.poke(true.B)
      dut.clock.step()
      dut.io.semaphoreA.a.ready.poke(false.B)
      dut.io.semaphoreB.a.ready.poke(false.B)
      dut.io.semaphoreA.d.valid.poke(true.B)
      dut.io.semaphoreA.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
      dut.io.semaphoreB.d.valid.poke(true.B)
      dut.io.semaphoreB.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
      dut.clock.step()
      dut.io.semaphoreA.d.valid.poke(false.B)
      dut.io.semaphoreB.d.valid.poke(false.B)

      // Complete both decrements (SUBU)
      waitFor(dut)(dut.io.semaphoreA.a.valid.peek().litToBoolean, "semaphoreA SUBU")
      dut.io.semaphoreA.a.bits.param.expect(ArithmeticDataParam.SUBU)
      dut.io.semaphoreA.a.ready.poke(true.B)
      waitFor(dut)(dut.io.semaphoreB.a.valid.peek().litToBoolean, "semaphoreB SUBU")
      dut.io.semaphoreB.a.bits.param.expect(ArithmeticDataParam.SUBU)
      dut.io.semaphoreB.a.ready.poke(true.B)
      dut.clock.step()
      dut.io.semaphoreA.a.ready.poke(false.B)
      dut.io.semaphoreB.a.ready.poke(false.B)
      dut.io.semaphoreA.d.valid.poke(true.B)
      dut.io.semaphoreA.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
      dut.io.semaphoreB.d.valid.poke(true.B)
      dut.io.semaphoreB.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
      dut.clock.step()
      dut.io.semaphoreA.d.valid.poke(false.B)
      dut.io.semaphoreB.d.valid.poke(false.B)

      // After acquires+decrements: pipeline A reads portA (Get), pipeline B writes portB (PutFullData)
      // Service portA read: respond with AccessAckData to populate AtoB FIFO for pipeline B
      waitFor(dut)(dut.io.portA.a.valid.peek().litToBoolean, "portA.a.valid")
      dut.io.portA.a.ready.poke(true.B)
      dut.clock.step()
      dut.io.portA.a.ready.poke(false.B)
      dut.io.portA.d.valid.poke(true.B)
      dut.io.portA.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
      dut.io.portA.d.bits.data.poke(0x42.U)
      dut.clock.step()
      dut.io.portA.d.valid.poke(false.B)
      dut.clock.step()

      // Service portB write: respond with AccessAck
      waitFor(dut)(dut.io.portB.a.valid.peek().litToBoolean, "portB.a.valid")
      dut.io.portB.a.ready.poke(true.B)
      dut.clock.step()
      dut.io.portB.a.ready.poke(false.B)
      dut.io.portB.d.valid.poke(true.B)
      dut.io.portB.d.bits.opcode.poke(TilelinkOpcodes.AccessAck)
      dut.clock.step()
      dut.io.portB.d.valid.poke(false.B)

      // Pipeline A (consumer): ADDU on base+1 (emptyReg)
      waitFor(dut)(dut.io.semaphoreA.a.valid.peek().litToBoolean, "semaphoreA ADDU")
      dut.io.semaphoreA.a.bits.param.expect(ArithmeticDataParam.ADDU)
      dut.io.semaphoreA.a.bits.address.expect(0x11.U)

      // Pipeline B (producer): ADDU on base+0 (fullReg)
      waitFor(dut)(dut.io.semaphoreB.a.valid.peek().litToBoolean, "semaphoreB ADDU")
      dut.io.semaphoreB.a.bits.param.expect(ArithmeticDataParam.ADDU)
      dut.io.semaphoreB.a.bits.address.expect(0x20.U)
    }
  }
}
