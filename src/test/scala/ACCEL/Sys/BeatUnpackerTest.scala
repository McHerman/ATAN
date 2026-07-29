package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class BeatUnpackerTest extends AnyFreeSpec with Matchers with ChiselSim {

  // Real target configuration: arrayDim=16, dataBusSize=64 -> rowsPerBeat=4.
  implicit val c: Configuration = Configuration.default()
  val rowsPerBeat = c.dataBusSize / c.arrayDim

  def driveBeat(dut: BeatUnpacker, base: Int): Unit = {
    dut.io.beatIn.ready.expect(true.B)
    dut.io.beatIn.valid.poke(true.B)
    (0 until c.dataBusSize).foreach(i => dut.io.beatIn.bits(i).poke((base + i).U(8.W)))
    dut.clock.step()
    dut.io.beatIn.valid.poke(false.B)
  }

  def expectSubRow(dut: BeatUnpacker, base: Int, r: Int): Unit = {
    dut.io.subRowValid.expect(true.B)
    (0 until c.arrayDim).foreach { lane =>
      dut.io.subRow(lane).expect((base + r * c.arrayDim + lane).U(8.W))
    }
  }

  "BeatUnpacker deserializes one wide beat into ordered sub-rows, holding ready low while draining" in {
    simulate(new BeatUnpacker()) { dut =>
      dut.io.size.poke(c.arrayDim.U)
      dut.io.subRowReady.poke(true.B)
      dut.io.beatIn.valid.poke(false.B)
      dut.clock.step()

      driveBeat(dut, base = 0)

      for (r <- 0 until rowsPerBeat) {
        dut.io.beatIn.ready.expect(false.B)
        expectSubRow(dut, base = 0, r)
        dut.clock.step()
      }

      dut.io.beatIn.ready.expect(true.B)
      dut.io.subRowValid.expect(false.B)
    }
  }

  "BeatUnpacker back-to-back beats don't drop a sub-row at the boundary" in {
    simulate(new BeatUnpacker()) { dut =>
      dut.io.size.poke(c.arrayDim.U)
      dut.io.subRowReady.poke(true.B)
      dut.io.beatIn.valid.poke(false.B)
      dut.clock.step()

      driveBeat(dut, base = 0)
      for (r <- 0 until rowsPerBeat) {
        expectSubRow(dut, base = 0, r)
        dut.clock.step()
      }

      driveBeat(dut, base = 100)
      for (r <- 0 until rowsPerBeat) {
        expectSubRow(dut, base = 100, r)
        dut.clock.step()
      }

      dut.io.beatIn.ready.expect(true.B)
      dut.io.subRowValid.expect(false.B)
    }
  }

  "BeatUnpacker zero-fills lanes beyond a runtime size smaller than arrayDim" in {
    // size=4 on an arrayDim=8 array: half the lanes carry real bytes packed
    // tightly at 4 bytes/row (matching the assembler's unpadded layout), the
    // other half must read back as zero regardless of what garbage sits in
    // the underlying latched beat there.
    val size = 4
    val rowsPerBeat = c.dataBusSize / size

    simulate(new BeatUnpacker()) { dut =>
      dut.io.size.poke(size.U)
      dut.io.subRowReady.poke(true.B)
      dut.io.beatIn.valid.poke(false.B)
      dut.clock.step()

      driveBeat(dut, base = 0)

      for (r <- 0 until rowsPerBeat) {
        dut.io.subRowValid.expect(true.B)
        for (lane <- 0 until c.arrayDim) {
          val expected = if (lane < size) (r * size + lane) else 0
          dut.io.subRow(lane).expect(expected.U(8.W))
        }
        dut.clock.step()
      }

      dut.io.beatIn.ready.expect(true.B)
      dut.io.subRowValid.expect(false.B)
    }
  }

  "BeatUnpacker holds a sub-row when the downstream consumer stalls" in {
    simulate(new BeatUnpacker()) { dut =>
      dut.io.size.poke(c.arrayDim.U)
      dut.io.subRowReady.poke(false.B)
      dut.io.beatIn.valid.poke(false.B)
      dut.clock.step()

      driveBeat(dut, base = 0)

      // Downstream not ready: the first sub-row must stay presented unchanged.
      for (_ <- 0 until 3) {
        expectSubRow(dut, base = 0, r = 0)
        dut.clock.step()
      }

      // Once downstream accepts, draining proceeds normally.
      dut.io.subRowReady.poke(true.B)
      for (r <- 0 until rowsPerBeat) {
        expectSubRow(dut, base = 0, r)
        dut.clock.step()
      }

      dut.io.beatIn.ready.expect(true.B)
    }
  }
}
