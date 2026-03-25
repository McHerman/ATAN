/*
package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import scala.util.Random


class ExecuteTest extends AnyFreeSpec with Matchers with ChiselSim {

  val n = 8
  val maxCycles = 500

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


  def matrixDotProduct(A: Array[Array[Int]], B: Array[Array[Int]]): Array[Array[Int]] = {
    val n = A.length
    Array.tabulate(n, n) { (i, j) =>
      (0 until n).map(k => A(i)(k) * B(k)(j)).sum
    }
  }

  implicit val c = Configuration.default()

  "Execute should execute with 1 missing depend" in {
    simulate(new Execute()) { dut =>

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
      dut.io.instructionStream.bits.grainSize.poke(0.U)

      dut.io.instructionStream.bits.addrs(0).addr.poke(0.U)
      dut.io.instructionStream.bits.addrs(0).depend.tag.poke(1.U)
      dut.io.instructionStream.bits.addrs(0).depend.ready.poke(true.B)

      dut.io.instructionStream.bits.addrs(1).addr.poke(64.U)
      dut.io.instructionStream.bits.addrs(1).depend.tag.poke(2.U)
      dut.io.instructionStream.bits.addrs(1).depend.ready.poke(false.B)

      dut.io.instructionStream.bits.addrd(0).addr.poke(128.U)

      dut.io.instructionStream.bits.size.poke(8.U)

      dut.clock.step(1)

      dut.io.instructionStream.ready.expect(true.B)
      dut.io.instructionStream.valid.poke(true.B)

      dut.clock.step(10)

      dut.io.eventIn.valid.poke(true.B)
      dut.io.eventIn.bits.tag.poke(2.U)

      dut.clock.step(1)

      // Wait for A-channel Get requests on scratchIn TileLink ports
      dut.io.scratchIn(0).a.ready.poke(true.B)
      dut.io.scratchIn(1).a.ready.poke(true.B)

      waitFor(dut.io.scratchIn(0).a.valid.peek().litToBoolean, "scratchIn(0).a.valid")

      dut.io.scratchIn(0).a.bits.opcode.expect(4.U) // Get
      dut.io.scratchIn(0).a.bits.address.expect(0.U)
      dut.io.scratchIn(0).a.bits.size.expect(8.U)

      dut.io.scratchIn(1).a.bits.opcode.expect(4.U) // Get
      dut.io.scratchIn(1).a.bits.address.expect(64.U)
      dut.io.scratchIn(1).a.bits.size.expect(8.U)

      dut.clock.step(1)

      // Respond with D-channel AccessAckData beats
      dut.io.scratchIn(0).d.valid.poke(true.B)
      dut.io.scratchIn(1).d.valid.poke(true.B)
      dut.io.scratchIn(0).d.bits.opcode.poke(1.U) // AccessAckData
      dut.io.scratchIn(1).d.bits.opcode.poke(1.U)

      def rowToUInt(row: Array[Int]): BigInt = {
        row.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (byte, i)) =>
          acc | (BigInt(byte & 0xFF) << (i * 8))
        }
      }

      for (i <- 0 until n) {
        dut.io.scratchIn(0).d.bits.data.poke(rowToUInt(matrix3(i)).U)
        dut.io.scratchIn(1).d.bits.data.poke(rowToUInt(matrix3(i)).U)
        dut.io.scratchIn(0).d.bits.param.poke(0.U)
        dut.io.scratchIn(1).d.bits.param.poke(0.U)
        dut.io.scratchIn(0).d.bits.size.poke(8.U)
        dut.io.scratchIn(1).d.bits.size.poke(8.U)
        dut.io.scratchIn(0).d.bits.source.poke(0.U)
        dut.io.scratchIn(1).d.bits.source.poke(0.U)
        dut.io.scratchIn(0).d.bits.sink.poke(0.U)
        dut.io.scratchIn(1).d.bits.sink.poke(0.U)
        dut.io.scratchIn(0).d.bits.denied.poke(0.U)
        dut.io.scratchIn(1).d.bits.denied.poke(0.U)
        dut.io.scratchIn(0).d.bits.corrupt.poke(0.U)
        dut.io.scratchIn(1).d.bits.corrupt.poke(0.U)

        dut.clock.step()
      }

      dut.io.scratchIn(0).d.valid.poke(false.B)
      dut.io.scratchIn(1).d.valid.poke(false.B)

      // Wait for write-back A-channel requests on scratchOut
      dut.io.scratchOut(0).a.ready.poke(true.B)

      waitFor(dut.io.scratchOut(0).a.valid.peek().litToBoolean, "scratchOut(0).a.valid")

      val expectedResult = matrixDotProduct(matrix3, matrix3)

      dut.io.scratchOut(0).a.bits.opcode.expect(0.U) // PutFullData
      dut.io.scratchOut(0).a.bits.address.expect(128.U)
      dut.io.scratchOut(0).a.bits.size.expect(8.U)

      // Accept write data beats
      for (row <- 0 until n) {
        dut.io.scratchOut(0).a.bits.data.expect(rowToUInt(expectedResult(row)).U)

        dut.clock.step()
      }

      // Send D AccessAck and keep it asserted until event fires
      dut.io.scratchOut(0).d.valid.poke(true.B)
      dut.io.scratchOut(0).d.bits.opcode.poke(0.U) // AccessAck
      dut.io.scratchOut(0).d.bits.param.poke(0.U)
      dut.io.scratchOut(0).d.bits.size.poke(0.U)
      dut.io.scratchOut(0).d.bits.source.poke(0.U)
      dut.io.scratchOut(0).d.bits.sink.poke(0.U)
      dut.io.scratchOut(0).d.bits.denied.poke(0.U)
      dut.io.scratchOut(0).d.bits.data.poke(0.U)
      dut.io.scratchOut(0).d.bits.corrupt.poke(0.U)

      // Wait for event (keep d.valid high)
      waitFor(dut.io.eventOut.valid.peek().litToBoolean, "eventOut.valid")

      dut.io.eventOut.valid.expect(true.B)
      dut.io.eventOut.bits.tag.expect(0.U)
    }
  }
}
*/
