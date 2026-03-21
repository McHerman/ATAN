package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import scala.util.Random
import scala.io.Source

class ScratchpadTest extends AnyFreeSpec with Matchers with ChiselSim {

  val n = 8
  val maxCycles = 500

  val WMatrix: Array[Array[Int]] = Array(
    Array(1, 2, 3, 4, 5, 6, 7, 8),
    Array(1, 2, 3, 4, 5, 6, 7, 8),
    Array(1, 2, 3, 4, 5, 6, 7, 8),
    Array(1, 2, 3, 4, 5, 6, 7, 8),
    Array(1, 2, 3, 4, 5, 6, 7, 8),
    Array(1, 2, 3, 4, 5, 6, 7, 8),
    Array(1, 2, 3, 4, 5, 6, 7, 8),
    Array(1, 2, 3, 4, 5, 6, 7, 8)
  )

  def rowToUInt(row: Array[Int]): BigInt = {
    row.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (byte, i)) =>
      acc | (BigInt(byte & 0xFF) << (i * 8))
    }
  }

  "Scratchpad should read aligned via TileLink" in {
    implicit val c = Configuration.default()
    simulate(new ScratchpadWrapper()) { dut =>

      def waitFor(cond: => Boolean, msg: String): Unit = {
        var cycles = 0
        while (!cond) {
          require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
          dut.clock.step()
          cycles += 1
        }
      }

      // Write: send n A beats (PutFull)
      dut.io.WritePorts(0).a.ready.expect(true.B)

      // Send first A beat
      dut.io.WritePorts(0).a.bits.opcode.poke(0.U) // PutFullData
      dut.io.WritePorts(0).a.bits.param.poke(0.U)
      dut.io.WritePorts(0).a.bits.address.poke(0.U)
      dut.io.WritePorts(0).a.bits.size.poke(n.U)
      dut.io.WritePorts(0).a.bits.source.poke(0.U)
      dut.io.WritePorts(0).a.bits.data.poke(rowToUInt(WMatrix(0)).U)
      dut.io.WritePorts(0).a.bits.mask.poke(0xFF.U)
      dut.io.WritePorts(0).a.bits.corrupt.poke(0.U)

      dut.io.WritePorts(0).a.valid.poke(true.B)
      dut.clock.step(1)

      // Send remaining A beats
      for (i <- 1 until n) {
        dut.io.WritePorts(0).a.bits.opcode.poke(0.U) // PutFullData
        dut.io.WritePorts(0).a.bits.param.poke(0.U)
        dut.io.WritePorts(0).a.bits.address.poke(i.U)
        dut.io.WritePorts(0).a.bits.size.poke(n.U)
        dut.io.WritePorts(0).a.bits.source.poke(0.U)
        dut.io.WritePorts(0).a.bits.data.poke(rowToUInt(WMatrix(i)).U)
        dut.io.WritePorts(0).a.bits.mask.poke(0xFF.U)
        dut.io.WritePorts(0).a.bits.corrupt.poke(0.U)

        dut.io.WritePorts(0).a.valid.poke(true.B)
        dut.clock.step(1)
      }

      dut.io.WritePorts(0).a.valid.poke(false.B)

      // Wait for D AccessAck
      dut.io.WritePorts(0).d.ready.poke(true.B)

      waitFor(dut.io.WritePorts(0).d.valid.peek().litToBoolean, "WritePorts(0).d.valid")

      dut.io.WritePorts(0).d.bits.opcode.expect(0.U) // AccessAck

      dut.clock.step(1)

      // Read: send Get on A channel
      dut.io.ReadPorts(0).a.ready.expect(true.B)

      dut.io.ReadPorts(0).a.bits.opcode.poke(4.U) // Get
      dut.io.ReadPorts(0).a.bits.param.poke(0.U)
      dut.io.ReadPorts(0).a.bits.address.poke(0.U)
      dut.io.ReadPorts(0).a.bits.size.poke(n.U)
      dut.io.ReadPorts(0).a.bits.source.poke(0.U)
      dut.io.ReadPorts(0).a.bits.mask.poke(0xFF.U)
      dut.io.ReadPorts(0).a.bits.data.poke(0.U)
      dut.io.ReadPorts(0).a.bits.corrupt.poke(0.U)

      dut.io.ReadPorts(0).a.valid.poke(true.B)
      dut.clock.step(1)
      dut.io.ReadPorts(0).a.valid.poke(false.B)

      // Accept D beats (AccessAckData)
      dut.io.ReadPorts(0).d.ready.poke(true.B)

      waitFor(dut.io.ReadPorts(0).d.valid.peek().litToBoolean, "ReadPorts(0).d.valid")

      for (i <- 0 until n) {
        dut.io.ReadPorts(0).d.valid.expect(true.B)
        dut.io.ReadPorts(0).d.bits.opcode.expect(1.U) // AccessAckData

        dut.io.ReadPorts(0).d.bits.data.expect(rowToUInt(WMatrix(i)).U)

        dut.clock.step(1)
      }
    }
  }
}
