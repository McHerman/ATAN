/*
package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import scala.util.Random


class LoadTest extends AnyFreeSpec with Matchers with ChiselSim {

  val n = 8
  val maxCycles = 500

  val matrix: Seq[UInt] = Seq(
    "h0102030401020304".U(64.W),
    "h0102030401020304".U(64.W),
    "h0102030401020304".U(64.W),
    "h0102030401020304".U(64.W),
    "h0102030401020304".U(64.W),
    "h0102030401020304".U(64.W),
    "h0102030401020304".U(64.W),
    "h0102030401020304".U(64.W)
  )

  val matrix3: Array[Array[Int]] = Array(
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4)
  )

  "Load should load" in {
    implicit val c = Configuration.default()

    simulate(new Load()) { dut =>

      def waitFor(cond: => Boolean, msg: String): Unit = {
        var cycles = 0
        while (!cond) {
          require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
          dut.clock.step()
          cycles += 1
        }
      }

      dut.io.instructionStream.ready.expect(true.B)
      dut.io.instructionStream.valid.poke(true.B)

      dut.io.instructionStream.bits.op.poke(0.U)
      dut.io.instructionStream.bits.mode.poke(0.U)

      dut.io.instructionStream.bits.size.poke(8.U)

      dut.io.instructionStream.bits.addrd(0).addr.poke(1.U)
      dut.io.instructionStream.bits.addrd(0).tag.poke(1.U)

      dut.clock.step(10)

      // Make scratchOut A channel ready AND provide AXI-S data simultaneously
      // LoadController needs both a.ready and tvalid to assert a.valid
      dut.io.scratchOut.a.ready.poke(true.B)
      dut.io.AXIST.tvalid.poke(true.B)
      dut.io.AXIST.tstrb.poke(255.U)
      dut.io.AXIST.tdata.poke(matrix(0))

      waitFor(dut.io.scratchOut.a.valid.peek().litToBoolean, "scratchOut.a.valid")

      dut.io.scratchOut.a.bits.opcode.expect(0.U) // PutFullData
      dut.io.scratchOut.a.bits.address.expect(1.U)

      // First beat is already being consumed this cycle, send remaining data
      for (row <- 0 until n) {
        dut.io.AXIST.tdata.poke(matrix(row))
        dut.io.AXIST.tstrb.poke(255.U)

        if (row == n - 1) {
          dut.io.AXIST.tlast.poke(true.B)
        }
        dut.clock.step(1)
      }

      dut.io.AXIST.tvalid.poke(false.B)
      dut.io.AXIST.tlast.poke(false.B)

      // Wait for D AccessAck
      dut.io.scratchOut.d.valid.poke(true.B)
      dut.io.scratchOut.d.bits.opcode.poke(0.U) // AccessAck
      dut.io.scratchOut.d.bits.param.poke(0.U)
      dut.io.scratchOut.d.bits.size.poke(0.U)
      dut.io.scratchOut.d.bits.source.poke(0.U)
      dut.io.scratchOut.d.bits.sink.poke(0.U)
      dut.io.scratchOut.d.bits.denied.poke(0.U)
      dut.io.scratchOut.d.bits.data.poke(0.U)
      dut.io.scratchOut.d.bits.corrupt.poke(0.U)

      waitFor(dut.io.scratchOut.d.ready.peek().litToBoolean, "scratchOut.d.ready")

      dut.clock.step(1)

      dut.io.scratchOut.d.valid.poke(false.B)

      // Wait for event
      waitFor(dut.io.event.valid.peek().litToBoolean, "event.valid")

      dut.io.event.valid.expect(true.B)
      dut.io.event.bits.tag.expect(1.U)
    }
  }
}
*/
