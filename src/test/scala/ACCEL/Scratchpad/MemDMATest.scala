package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class MemDMATest extends AnyFreeSpec with Matchers with ChiselSim {

  val maxCycles = 500

  "MemDMA should accept a descriptor" in {
    implicit val c = Configuration.default()
    simulate(new MemDMA()) { dut =>

      // Default pokes for TL ports (test acts as TL device on both ports)
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
      dut.io.interface.response.ready.poke(false.B)

      // Verify idle: descriptor ready
      dut.io.interface.descriptor.ready.expect(true.B)

      // Submit 2-element descriptor Vec
      dut.io.interface.descriptor.valid.poke(true.B)

      // Descriptor 0: write to 0x100, size=2
      dut.io.interface.descriptor.bits(0).addr.poke(0x100.U)
      dut.io.interface.descriptor.bits(0).size.poke(2.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)

      // Descriptor 1: write to 0x200, size=2
      dut.io.interface.descriptor.bits(1).addr.poke(0x200.U)
      dut.io.interface.descriptor.bits(1).size.poke(2.U)
      dut.io.interface.descriptor.bits(1).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(1).source.poke(0.U)
      dut.io.interface.descriptor.bits(1).sink.poke(0.U)

      dut.clock.step() // Latch descriptors, transition to state 1
      dut.io.interface.descriptor.valid.poke(false.B)

      // After acceptance, descriptor.ready should be false (DUT in state 1)
      dut.io.interface.descriptor.ready.expect(false.B)
    }
  }

  "MemDMA should dispatch descriptors to both pipelines" in {
    implicit val c = Configuration.default()
    simulate(new MemDMA()) { dut =>

      def waitFor(cond: => Boolean, msg: String): Unit = {
        var cycles = 0
        while (!cond) {
          require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
          dut.clock.step()
          cycles += 1
        }
      }

      // Default pokes
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
      dut.io.interface.response.ready.poke(false.B)

      // Submit descriptors: both are writes
      dut.io.interface.descriptor.valid.poke(true.B)

      dut.io.interface.descriptor.bits(0).addr.poke(0x100.U)
      dut.io.interface.descriptor.bits(0).size.poke(1.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)

      dut.io.interface.descriptor.bits(1).addr.poke(0x200.U)
      dut.io.interface.descriptor.bits(1).size.poke(1.U)
      dut.io.interface.descriptor.bits(1).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(1).source.poke(0.U)
      dut.io.interface.descriptor.bits(1).sink.poke(0.U)

      dut.clock.step() // MemDMA latches descriptors, goes to state 1

      dut.io.interface.descriptor.valid.poke(false.B)

      // State 1: MemDMA dispatches to both pipelines.
      // Pipelines accept in the same cycle and transition to their write state.
      // Step once more so the pipelines are in state 1 and assert tl.a.valid.
      dut.clock.step()

      // Both pipelines should now present PutFullData on their A channels
      waitFor(dut.io.portA.a.valid.peek().litToBoolean, "portA.a.valid")
      dut.io.portA.a.bits.opcode.expect(0.U) // PutFullData
      dut.io.portA.a.bits.address.expect(0x100.U)

      waitFor(dut.io.portB.a.valid.peek().litToBoolean, "portB.a.valid")
      dut.io.portB.a.bits.opcode.expect(0.U) // PutFullData
      dut.io.portB.a.bits.address.expect(0x200.U)
    }
  }
}
