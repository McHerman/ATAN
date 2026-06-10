package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class SemaphoreEventArbiterTest extends AnyFreeSpec with Matchers with ChiselSim {

  def cfg = Configuration.default()

  def driveEvent(dut: SemaphoreEventArbiter, idx: Int, addr: Int, code: Int): Unit = {
    dut.io.in(idx).valid.poke(true.B)
    dut.io.in(idx).bits.addr.poke(addr.U)
    dut.io.in(idx).bits.eventCode.poke(code.U)
  }

  def expectGrant(dut: SemaphoreEventArbiter, idx: Int, addr: Int, code: Int): Unit = {
    dut.io.out.valid.expect(true.B)
    dut.io.out.bits.addr.expect(addr.U)
    dut.io.out.bits.eventCode.expect(code.U)
    for (i <- 0 until dut.io.in.length) {
      if (i == idx) dut.io.in(i).ready.expect(true.B)
      else          dut.io.in(i).ready.expect(false.B)
    }
  }

  "Single valid input passes straight through" in {
    implicit val c = cfg
    simulate(new SemaphoreEventArbiter(4)) { dut =>
      driveEvent(dut, idx = 2, addr = 7, code = SemaphoreEventCodes.Complete.litValue.toInt)
      dut.io.out.ready.poke(true.B)
      expectGrant(dut, idx = 2, addr = 7, code = SemaphoreEventCodes.Complete.litValue.toInt)
    }
  }

  "Lowest-index input wins on first cycle when several are valid" in {
    implicit val c = cfg
    simulate(new SemaphoreEventArbiter(4)) { dut =>
      driveEvent(dut, idx = 1, addr = 11, code = 0)
      driveEvent(dut, idx = 3, addr = 33, code = 0)
      dut.io.out.ready.poke(true.B)
      expectGrant(dut, idx = 1, addr = 11, code = 0)
    }
  }

  "Round-robin: previously granted input drops to lowest priority" in {
    implicit val c = cfg
    simulate(new SemaphoreEventArbiter(4)) { dut =>
      driveEvent(dut, idx = 0, addr = 100, code = 0)
      driveEvent(dut, idx = 2, addr = 200, code = 0)
      dut.io.out.ready.poke(true.B)

      expectGrant(dut, idx = 0, addr = 100, code = 0)
      dut.clock.step()

      expectGrant(dut, idx = 2, addr = 200, code = 0)
      dut.clock.step()

      expectGrant(dut, idx = 0, addr = 100, code = 0)
    }
  }

  "Output stalls when out.ready is low; ready transparently propagates to granted input" in {
    implicit val c = cfg
    simulate(new SemaphoreEventArbiter(4)) { dut =>
      driveEvent(dut, idx = 1, addr = 42, code = 0)

      dut.io.out.ready.poke(false.B)
      dut.io.out.valid.expect(true.B)
      dut.io.in(1).ready.expect(false.B)

      dut.clock.step(3)
      dut.io.out.valid.expect(true.B)
      dut.io.in(1).ready.expect(false.B)

      dut.io.out.ready.poke(true.B)
      dut.io.in(1).ready.expect(true.B)
    }
  }

  "n=1 degenerate config: input wires straight to output" in {
    implicit val c = cfg
    simulate(new SemaphoreEventArbiter(1)) { dut =>
      driveEvent(dut, idx = 0, addr = 5, code = 0)
      dut.io.out.ready.poke(true.B)
      expectGrant(dut, idx = 0, addr = 5, code = 0)
    }
  }
}
