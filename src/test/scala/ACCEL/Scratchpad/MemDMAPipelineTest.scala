package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class MemDMAPipelineTest extends AnyFreeSpec with Matchers with ChiselSim {

  val maxCycles = 500

  "MemDMAPipeline should be idle on reset" in {
    implicit val c = Configuration.default()
    simulate(new MemDMAPipeline()) { dut =>
      // State 0: descriptor ready, no TL activity, no response
      dut.io.interface.descriptor.ready.expect(true.B)
      dut.io.tl.a.valid.expect(false.B)
      dut.io.interface.response.valid.expect(false.B)
    }
  }

  "MemDMAPipeline should handle single-beat write" in {
    implicit val c = Configuration.default()
    simulate(new MemDMAPipeline()) { dut =>

      def waitFor(cond: => Boolean, msg: String): Unit = {
        var cycles = 0
        while (!cond) {
          require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
          dut.clock.step()
          cycles += 1
        }
      }

      // Default pokes for TL D channel (test acts as TL device)
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

      // Default pokes for other inputs
      dut.io.interface.response.ready.poke(false.B)
      dut.io.dataIn.bits.poke(0.U)
      dut.io.dataIn.valid.poke(false.B)
      dut.io.dataOut.ready.poke(false.B)

      // Verify idle state
      dut.io.interface.descriptor.ready.expect(true.B)

      // Submit write descriptor: addr=0x100, size=1, writeEn=true
      dut.io.interface.descriptor.valid.poke(true.B)
      dut.io.interface.descriptor.bits(0).addr.poke(0x100.U)
      dut.io.interface.descriptor.bits(0).size.poke(1.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)

      dut.clock.step() // Latch descriptor, transition to state 1
      dut.io.interface.descriptor.valid.poke(false.B)

      // State 1: DUT presents PutFullData on A channel
      // Provide data on dataIn
      dut.io.dataIn.bits.poke(0xAB.U)

      waitFor(dut.io.tl.a.valid.peek().litToBoolean, "tl.a.valid in state 1")

      // Verify A channel fields
      dut.io.tl.a.bits.opcode.expect(0.U) // PutFullData
      dut.io.tl.a.bits.address.expect(0x100.U)
      dut.io.tl.a.bits.size.expect(1.U)
      dut.io.tl.a.bits.data.expect(0xAB.U)

      // Accept the A channel beat
      dut.io.tl.a.ready.poke(true.B)
      dut.clock.step() // A fires, size=1 so transition to state 3
      dut.io.tl.a.ready.poke(false.B)

      // State 3: DUT waits for D channel AccessAck
      waitFor(dut.io.tl.d.ready.peek().litToBoolean, "tl.d.ready in state 3")

      // Send AccessAck on D channel
      dut.io.tl.d.valid.poke(true.B)
      dut.io.tl.d.bits.opcode.poke(0.U) // AccessAck

      dut.clock.step() // D fires, transition to state 4
      dut.io.tl.d.valid.poke(false.B)

      // State 4: DUT sends response
      waitFor(dut.io.interface.response.valid.peek().litToBoolean, "response.valid in state 4")

      // Accept response
      dut.io.interface.response.ready.poke(true.B)
      dut.clock.step() // Response fires, back to state 0
      dut.io.interface.response.ready.poke(false.B)

      // Verify return to idle
      dut.io.interface.descriptor.ready.expect(true.B)
      dut.io.tl.a.valid.expect(false.B)
      dut.io.interface.response.valid.expect(false.B)
    }
  }

  "MemDMAPipeline should handle multi-beat write" in {
    implicit val c = Configuration.default()
    simulate(new MemDMAPipeline()) { dut =>

      def waitFor(cond: => Boolean, msg: String): Unit = {
        var cycles = 0
        while (!cond) {
          require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
          dut.clock.step()
          cycles += 1
        }
      }

      val baseAddr = 0x200
      val size = 4
      val beatData = Array(0x10, 0x20, 0x30, 0x40)

      // Default pokes
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
      dut.io.interface.response.ready.poke(false.B)
      dut.io.dataIn.bits.poke(0.U)
      dut.io.dataIn.valid.poke(false.B)
      dut.io.dataOut.ready.poke(false.B)

      // Submit write descriptor: addr=baseAddr, size=4, writeEn=true
      dut.io.interface.descriptor.valid.poke(true.B)
      dut.io.interface.descriptor.bits(0).addr.poke(baseAddr.U)
      dut.io.interface.descriptor.bits(0).size.poke(size.U)
      dut.io.interface.descriptor.bits(0).writeEn.poke(true.B)
      dut.io.interface.descriptor.bits(0).source.poke(0.U)
      dut.io.interface.descriptor.bits(0).sink.poke(0.U)

      dut.clock.step() // Latch descriptor, transition to state 1
      dut.io.interface.descriptor.valid.poke(false.B)

      // --- Beat 0 (state 1) ---
      dut.io.dataIn.bits.poke(beatData(0).U)

      waitFor(dut.io.tl.a.valid.peek().litToBoolean, "tl.a.valid for beat 0")

      dut.io.tl.a.bits.opcode.expect(0.U) // PutFullData
      dut.io.tl.a.bits.address.expect(baseAddr.U)
      dut.io.tl.a.bits.data.expect(beatData(0).U)

      dut.io.tl.a.ready.poke(true.B)
      dut.clock.step() // A fires, size>1 so transition to state 2, beatCnt=1
      dut.io.tl.a.ready.poke(false.B)

      // --- Beats 1..3 (state 2) ---
      // In state 2, tl.a.valid is gated by io.dataIn.ready.
      // The DUT defaults dataIn.ready to true; in MemDMA context it's
      // overridden by the BufferFIFO connection.
      for (beat <- 1 until size) {
        dut.io.dataIn.bits.poke(beatData(beat).U)

        waitFor(dut.io.tl.a.valid.peek().litToBoolean, s"tl.a.valid for beat $beat")

        dut.io.tl.a.bits.opcode.expect(0.U) // PutFullData
        dut.io.tl.a.bits.address.expect((baseAddr + beat).U)
        dut.io.tl.a.bits.data.expect(beatData(beat).U)

        dut.io.tl.a.ready.poke(true.B)
        dut.clock.step()
        dut.io.tl.a.ready.poke(false.B)
      }

      // State 3: wait for AccessAck
      waitFor(dut.io.tl.d.ready.peek().litToBoolean, "tl.d.ready in state 3")

      dut.io.tl.d.valid.poke(true.B)
      dut.io.tl.d.bits.opcode.poke(0.U) // AccessAck

      dut.clock.step()
      dut.io.tl.d.valid.poke(false.B)

      // State 4: accept response
      waitFor(dut.io.interface.response.valid.peek().litToBoolean, "response.valid")

      dut.io.interface.response.ready.poke(true.B)
      dut.clock.step()
      dut.io.interface.response.ready.poke(false.B)

      // Verify return to idle
      dut.io.interface.descriptor.ready.expect(true.B)
      dut.io.tl.a.valid.expect(false.B)
    }
  }
}
