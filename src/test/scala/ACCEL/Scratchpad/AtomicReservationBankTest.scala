package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class AtomicReservationBankTest extends AnyFreeSpec with Matchers with ChiselSim {

  implicit val c: Configuration = Configuration.default()

  "single port acquires reservation when address is free" in {
    simulate(new AtomicReservationBank(2)) { dut =>
      dut.io.reserve(0).valid.poke(true.B)
      dut.io.reserve(0).bits.address.poke(0x100.U)
      dut.io.reserve(0).bits.opcode.poke(amoReservationOp.acquire)
      dut.io.reserve(1).valid.poke(false.B)

      // roundRobin starts at 0 → port 0 is the winning slot, no conflict
      dut.io.reserve(0).ready.expect(true.B)
      dut.io.reservations(0).valid.expect(false.B)

      dut.clock.step()  // fires → reservationReg(0) set, roundRobin → 1

      dut.io.reservations(0).valid.expect(true.B)
      dut.io.reservations(0).bits.expect(0x100.U)
      // port 0 already holds a reservation so it is no longer a candidate
      dut.io.reserve(0).ready.expect(false.B)
    }
  }

  "reservation is released by release opcode" in {
    simulate(new AtomicReservationBank(2)) { dut =>
      dut.io.reserve(0).valid.poke(true.B)
      dut.io.reserve(0).bits.address.poke(0x200.U)
      dut.io.reserve(0).bits.opcode.poke(amoReservationOp.acquire)
      dut.io.reserve(1).valid.poke(false.B)

      dut.clock.step()  // reservationReg(0) set, roundRobin → 1

      dut.io.reserve(0).valid.poke(true.B)
      dut.io.reserve(0).bits.address.poke(0x200.U)
      dut.io.reserve(0).bits.opcode.poke(amoReservationOp.release)

      dut.clock.step()  // reservationReg(0) cleared, roundRobin → 0

      dut.io.reservations(0).valid.expect(false.B)

      // port 0 can re-acquire (roundRobin=0 again)
      dut.io.reserve(0).valid.poke(true.B)
      dut.io.reserve(0).bits.address.poke(0x200.U)
      dut.io.reserve(0).bits.opcode.poke(amoReservationOp.acquire)

      dut.io.reserve(0).ready.expect(true.B)
    }
  }

  "release fires regardless of roundRobin slot" in {
    simulate(new AtomicReservationBank(2)) { dut =>
      // Advance roundRobin to 1 so port 1 can acquire
      dut.io.reserve(0).valid.poke(false.B)
      dut.io.reserve(1).valid.poke(false.B)
      dut.clock.step()  // roundRobin → 1

      dut.io.reserve(1).valid.poke(true.B)
      dut.io.reserve(1).bits.address.poke(0x300.U)
      dut.io.reserve(1).bits.opcode.poke(amoReservationOp.acquire)
      dut.io.reserve(0).valid.poke(false.B)
      dut.clock.step()  // reservationReg(1) set, roundRobin → 0

      // roundRobin=0 — release on port 1 should still clear its reservation
      dut.io.reserve(1).valid.poke(true.B)
      dut.io.reserve(1).bits.address.poke(0x300.U)
      dut.io.reserve(1).bits.opcode.poke(amoReservationOp.release)
      dut.io.reserve(0).valid.poke(false.B)
      dut.clock.step()

      dut.io.reservations(1).valid.expect(false.B)
    }
  }

  "internal conflict: second port blocked when same address already held" in {
    simulate(new AtomicReservationBank(2)) { dut =>
      dut.io.reserve(0).valid.poke(true.B)
      dut.io.reserve(0).bits.address.poke(0x400.U)
      dut.io.reserve(0).bits.opcode.poke(amoReservationOp.acquire)
      dut.io.reserve(1).valid.poke(false.B)

      dut.io.reserve(0).ready.expect(true.B)
      dut.clock.step()  // reservationReg(0) = {true, 0x400}, roundRobin → 1

      dut.io.reserve(1).valid.poke(true.B)
      dut.io.reserve(1).bits.address.poke(0x400.U)
      dut.io.reserve(1).bits.opcode.poke(amoReservationOp.acquire)
      dut.io.reserve(0).valid.poke(false.B)

      // roundRobin=1 → port 1 is the candidate, but internal conflict blocks grant
      dut.io.reserve(1).ready.expect(false.B)
    }
  }

  "two ports on different addresses both proceed without conflict" in {
    simulate(new AtomicReservationBank(2)) { dut =>
      dut.io.reserve(0).valid.poke(true.B)
      dut.io.reserve(0).bits.address.poke(0x500.U)
      dut.io.reserve(0).bits.opcode.poke(amoReservationOp.acquire)
      dut.io.reserve(1).valid.poke(false.B)

      dut.clock.step()  // reservationReg(0) set, roundRobin → 1

      dut.io.reserve(1).valid.poke(true.B)
      dut.io.reserve(1).bits.address.poke(0x501.U)
      dut.io.reserve(1).bits.opcode.poke(amoReservationOp.acquire)
      dut.io.reserve(0).valid.poke(false.B)

      // roundRobin=1 → port 1 wins, different address → no conflict
      dut.io.reserve(1).ready.expect(true.B)
    }
  }

  "round-robin: port 1 wins after port 0 was served" in {
    simulate(new AtomicReservationBank(2)) { dut =>
      dut.io.reserve(0).valid.poke(true.B)
      dut.io.reserve(0).bits.address.poke(0x600.U)
      dut.io.reserve(0).bits.opcode.poke(amoReservationOp.acquire)
      dut.io.reserve(1).valid.poke(true.B)
      dut.io.reserve(1).bits.address.poke(0x601.U)
      dut.io.reserve(1).bits.opcode.poke(amoReservationOp.acquire)

      // roundRobin=0 → port 0 is priority
      dut.io.reserve(0).ready.expect(true.B)
      dut.io.reserve(1).ready.expect(false.B)
      dut.clock.step()  // port 0 fires, roundRobin → 1

      dut.io.reserve(0).valid.poke(false.B)
      dut.io.reserve(1).valid.poke(true.B)
      dut.io.reserve(1).bits.address.poke(0x601.U)
      dut.io.reserve(1).bits.opcode.poke(amoReservationOp.acquire)

      // roundRobin=1 → port 1 wins, different address → no conflict
      dut.io.reserve(1).ready.expect(true.B)
      dut.io.reserve(0).ready.expect(false.B)
    }
  }

  "no valid candidates: no grants issued" in {
    simulate(new AtomicReservationBank(2)) { dut =>
      dut.io.reserve(0).valid.poke(false.B)
      dut.io.reserve(1).valid.poke(false.B)

      dut.io.reserve(0).ready.expect(false.B)
      dut.io.reserve(1).ready.expect(false.B)
      dut.io.reservations(0).valid.expect(false.B)
      dut.io.reservations(1).valid.expect(false.B)
    }
  }

  "three ports, same address: only one granted per cycle" in {
    simulate(new AtomicReservationBank(3)) { dut =>
      for (i <- 0 until 3) {
        dut.io.reserve(i).valid.poke(true.B)
        dut.io.reserve(i).bits.address.poke(0x700.U)
        dut.io.reserve(i).bits.opcode.poke(amoReservationOp.acquire)
      }

      val r0 = dut.io.reserve(0).ready.peek().litToBoolean
      val r1 = dut.io.reserve(1).ready.peek().litToBoolean
      val r2 = dut.io.reserve(2).ready.peek().litToBoolean

      assert(Seq(r0, r1, r2).count(identity) == 1, "exactly one port should be granted")

      // roundRobin=0 → port 0 wins
      dut.io.reserve(0).ready.expect(true.B)
      dut.io.reserve(1).ready.expect(false.B)
      dut.io.reserve(2).ready.expect(false.B)
    }
  }
}
