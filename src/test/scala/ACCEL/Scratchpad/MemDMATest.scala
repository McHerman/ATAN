package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import util.random

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
  "MemDMA should correctly transfer data" in {
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

      val r = new scala.util.Random
      val random = Seq.fill(128)(r.nextInt(255))

      dut.io.interface.response.ready.poke(false.B)

      // Verify idle: descriptor ready
      dut.io.interface.descriptor.ready.expect(true.B)

      // Submit 2-element descriptor Vec
      dut.io.interface.descriptor.valid.poke(true.B)

      // Descriptor 0: write to 0x100, size=2
      dut.io.interface.descriptor.bits(0).addr.poke(0x100.U)
      dut.io.interface.descriptor.bits(0).size.poke(127.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(false.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)

      // Descriptor 1: write to 0x200, size=2
      dut.io.interface.descriptor.bits(1).addr.poke(0x200.U)
      dut.io.interface.descriptor.bits(1).size.poke(127.U)
      dut.io.interface.descriptor.bits(1).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(1).source.poke(0.U)
      dut.io.interface.descriptor.bits(1).sink.poke(0.U)

      dut.clock.step() // Latch descriptors, transition to state 1
      dut.io.interface.descriptor.valid.poke(false.B)

      // After acceptance, descriptor.ready should be false (DUT in state 1)
      dut.io.interface.descriptor.ready.expect(false.B)


      dut.clock.step()

      dut.io.portA.a.valid.expect(true.B)
      dut.io.portA.a.ready.poke(true.B)

      dut.io.portA.a.bits.opcode.expect(TilelinkOpcodes.Get)  // Get
      dut.io.portA.a.bits.param.expect(0.U)
      dut.io.portA.a.bits.size.expect(127.U)  // Locking size
      dut.io.portA.a.bits.source.expect(0.U)
      dut.io.portA.a.bits.address.expect(0x100.U)
      //dut.io.portA.a.bits.mask.expect(0x0.U)  // mask is 2 bits
      //dut.io.portA.a.bits.data.expect(0x0.U)
      dut.io.portA.a.bits.corrupt.expect(0.U)

      dut.clock.step()

      dut.io.portA.d.ready.expect(true.B)
      dut.io.portA.d.valid.poke(true.B)

      dut.io.portA.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)  // AccessAckData
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
      //dut.io.portB.a.bits.mask.expect(0x0.U)
      dut.io.portB.a.bits.data.expect(random(0).asUInt)
      dut.io.portB.a.bits.corrupt.expect(0.U)


      /*
      for (i <- 1 until 127) {
        dut.io.portA.d.ready.expect(true.B)
        dut.io.portA.d.valid.poke(true.B)

        dut.io.portA.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)  // AccessAckData
        dut.io.portA.d.bits.param.poke(0.U)
        dut.io.portA.d.bits.size.poke(127.U)
        dut.io.portA.d.bits.source.poke(0.U)
        dut.io.portA.d.bits.sink.poke(0.U)
        dut.io.portA.d.bits.denied.poke(0.U)
        dut.io.portA.d.bits.data.poke(random(i).asUInt)
        dut.io.portA.d.bits.corrupt.poke(0.U)


        dut.io.portB.a.valid.expect(true.B)
        dut.io.portB.a.ready.poke(true.B)

        dut.io.portB.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
        dut.io.portB.a.bits.param.expect(0.U)
        dut.io.portB.a.bits.size.expect(127.U)
        dut.io.portB.a.bits.address.expect(0x200.U)
        //dut.io.portB.a.bits.mask.expect(0x0.U)
        dut.io.portB.a.bits.data.expect(random(i-1).asUInt)
        dut.io.portB.a.bits.corrupt.expect(0.U)

        dut.clock.step()
      }

      dut.io.portA.d.valid.poke(false.B)

      dut.io.portB.a.valid.expect(true.B)
      dut.io.portB.a.ready.poke(true.B)

      dut.io.portB.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      dut.io.portB.a.bits.param.expect(0.U)
      dut.io.portB.a.bits.size.expect(127.U)
      dut.io.portB.a.bits.address.expect(0x200.U)
      dut.io.portB.a.bits.mask.expect(0x0.U)
      dut.io.portB.a.bits.data.expect(random(127).asUInt)
      dut.io.portB.a.bits.corrupt.expect(0.U)
      */
    }
  }
}
