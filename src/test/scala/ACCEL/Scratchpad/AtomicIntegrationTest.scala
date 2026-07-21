package ATA8

import chisel3._
import chisel3.util._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

// port 0 = external actor, port 1 = handler's AMO reservation
class AtomicIntegrationDUT(implicit c: Configuration) extends Module {
  val handler = Module(new TLScratchpadHandler(
    TLScratchConfig(read = true, write = true, atomic = true, atomicIn = 1, tlConfig = c.tlBus)
  ))
  val bank = Module(new AtomicReservationBank(2))

  val io = IO(new Bundle {
    val tl      = Flipped(new TilelinkPort(c.tlBus))
    val wMem    = Decoupled(new Writeport(new Bundle {
      val writeData = Vec(c.dataBusSize, UInt(8.W))
      val strb      = Vec(c.dataBusSize, Bool())
    }, 16))
    val rMem    = new Readport(Vec(c.dataBusSize, UInt(c.arithDataWidth.W)), Some(16))
    val bankAcq = Flipped(Decoupled(new AtomicReservation()))
  })

  io.tl              <> handler.io.tl
  io.wMem            <> handler.io.wMem.get
  io.rMem            <> handler.io.rMem.get

  bank.io.reserve(0) <> io.bankAcq
  bank.io.reserve(1) <> handler.io.amoReserve.get

  // External reservation (port 0) feeds handler so it knows not to AMO on a contested address
  handler.io.reserveIn.get(0) := bank.io.reservations(0)
}

class AtomicIntegrationTest extends AnyFreeSpec with Matchers with ChiselSim {

  implicit val c: Configuration = Configuration.default()

  "bank reservation blocks handler AMO until released" in {
    simulate(new AtomicIntegrationDUT) { dut =>
      // External acquires 0x100 (roundRobin=0 → port 0 wins immediately)
      dut.io.bankAcq.valid.poke(true.B)
      dut.io.bankAcq.bits.address.poke(0x100.U)
      dut.io.bankAcq.bits.opcode.poke(amoReservationOp.acquire)
      dut.io.bankAcq.ready.expect(true.B)
      dut.clock.step()  // reservationReg(0) = {true, 0x100}, roundRobin → 1

      // Send AMO on same address
      dut.io.bankAcq.valid.poke(false.B)
      dut.io.tl.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.tl.a.bits.param.poke(ArithmeticDataParam.ADD)
      dut.io.tl.a.bits.size.poke(8.U)
      dut.io.tl.a.bits.source.poke(0.U)
      dut.io.tl.a.bits.address.poke(0x100.U)
      dut.io.tl.a.bits.mask.poke(0xFF.U)
      dut.io.tl.a.bits.data.poke(10.U)
      dut.io.tl.a.bits.corrupt.poke(0.U)
      dut.io.tl.a.valid.poke(true.B)
      dut.io.tl.a.ready.expect(true.B)
      dut.clock.step()  // sIdle → amoLock, roundRobin → 0

      dut.io.tl.a.valid.poke(false.B)

      // amoLock: roundRobin=0 → port 1 not the winning slot → stalls
      dut.io.rMem.request.valid.expect(false.B)
      dut.clock.step()  // roundRobin → 1

      // amoLock: roundRobin=1 → port 1 is candidate, but internal conflict → still stalls
      dut.io.rMem.request.valid.expect(false.B)
      dut.clock.step()  // roundRobin → 0

      // Release external reservation (release bypasses arbitration)
      dut.io.bankAcq.valid.poke(true.B)
      dut.io.bankAcq.bits.address.poke(0x100.U)
      dut.io.bankAcq.bits.opcode.poke(amoReservationOp.release)
      dut.io.rMem.request.valid.expect(false.B)
      dut.clock.step()  // reservationReg(0) cleared, roundRobin → 1

      // amoLock: roundRobin=1, no conflict → bank grants handler
      dut.io.bankAcq.valid.poke(false.B)
      dut.clock.step()  // amoLock → amoRead

      // Handler proceeds through full AMO cycle
      dut.io.rMem.request.valid.expect(true.B)
      dut.io.rMem.request.bits.addr.get.expect(0x100.U)
      dut.io.rMem.request.ready.poke(true.B)
      dut.clock.step()  // amoRead → amoOp

      dut.io.rMem.request.ready.poke(false.B)
      dut.io.rMem.response.bits.readData(0).poke(50.U)
      for (i <- 1 until c.dataBusSize) { dut.io.rMem.response.bits.readData(i).poke(0.U) }
      dut.io.rMem.response.valid.poke(true.B)
      dut.clock.step()  // amoOp → amoWrite  (50 + 10 = 60)

      dut.io.rMem.response.valid.poke(false.B)
      dut.io.wMem.valid.expect(true.B)
      dut.io.wMem.bits.addr.expect(0x100.U)
      dut.io.wMem.bits.data.writeData(0).expect(60.U)
      dut.io.wMem.ready.poke(true.B)
      dut.clock.step()  // amoWrite → amoReturn

      dut.io.wMem.ready.poke(false.B)
      dut.io.tl.d.ready.poke(true.B)
      dut.io.tl.d.valid.expect(true.B)
      dut.io.tl.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.tl.d.bits.data.expect(50.U)
      dut.clock.step()

      dut.io.tl.d.valid.expect(false.B)
    }
  }

  "handler AMO reservation blocks bank acquisition on same address" in {
    simulate(new AtomicIntegrationDUT) { dut =>
      dut.io.bankAcq.valid.poke(false.B)

      // Send AMO on 0x200 with no competing reservations
      dut.io.tl.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.tl.a.bits.param.poke(ArithmeticDataParam.ADD)
      dut.io.tl.a.bits.size.poke(8.U)
      dut.io.tl.a.bits.source.poke(0.U)
      dut.io.tl.a.bits.address.poke(0x200.U)
      dut.io.tl.a.bits.mask.poke(0xFF.U)
      dut.io.tl.a.bits.data.poke(5.U)
      dut.io.tl.a.bits.corrupt.poke(0.U)
      dut.io.tl.a.valid.poke(true.B)
      dut.io.tl.a.ready.expect(true.B)
      dut.clock.step()  // sIdle → amoLock, roundRobin → 1

      dut.io.tl.a.valid.poke(false.B)
      // amoLock: roundRobin=1 → bank grants port 1 (handler); amoReserve.fire → amoRead
      dut.clock.step()  // amoLock → amoRead, reservationReg(1) = {true, 0x200}, roundRobin → 0

      // Handler holds reservation on 0x200 — bank must not grant the same address
      dut.io.bankAcq.valid.poke(true.B)
      dut.io.bankAcq.bits.address.poke(0x200.U)
      dut.io.bankAcq.bits.opcode.poke(amoReservationOp.acquire)
      dut.io.bankAcq.ready.expect(false.B)

      // Drive the handler through the rest of the AMO
      dut.io.rMem.request.valid.expect(true.B)
      dut.io.rMem.request.bits.addr.get.expect(0x200.U)
      dut.io.rMem.request.ready.poke(true.B)
      dut.clock.step()  // amoRead → amoOp

      dut.io.rMem.request.ready.poke(false.B)
      dut.io.rMem.response.bits.readData(0).poke(20.U)
      for (i <- 1 until c.dataBusSize) { dut.io.rMem.response.bits.readData(i).poke(0.U) }
      dut.io.rMem.response.valid.poke(true.B)
      dut.clock.step()  // amoOp → amoWrite

      dut.io.rMem.response.valid.poke(false.B)
      dut.io.wMem.valid.expect(true.B)
      dut.io.wMem.ready.poke(true.B)
      dut.clock.step()  // amoWrite → amoReturn

      dut.io.wMem.ready.poke(false.B)
      dut.io.tl.d.ready.poke(true.B)
      dut.io.tl.d.valid.expect(true.B)
      // reservation on port 1 persists (no release issued), external still blocked
      dut.io.bankAcq.ready.expect(false.B)
    }
  }
}
