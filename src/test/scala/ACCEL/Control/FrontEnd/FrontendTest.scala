package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import eaac.assembler.Encoding
import eaac.assembler.Encoding.Encoded
import eaac.shared.InstructionSet
import eaac.shared.InstructionSet.{AddrPkg, Execute, Load, Store, DMA, SemProg}

/** Front-end tests.
  *
  * Instructions are variable length (1–3 × 64-bit slots) and the AXI-S input
  * carries them in 128-bit beat-packed form.  These tests build each
  * instruction using the shared `InstructionSet` layouts, hand them to
  * `Encoding.packBeats` to pack into beats, and stream the beats one at a
  * time — exactly what the assembler does in production.
  */
class FrontendTest extends AnyFreeSpec with Matchers with ChiselSim {

  val maxCycles = 500

  // ── Instruction encoders ──────────────────────────────────────────────────

  private def simpleAddr(addr: Int): BigInt = AddrPkg.simple(addr)

  def assembleExe(mode: Int, size: Int, addr0: Int): Encoded =
    Encoding.encodeExecute(
      func   = 0,
      mode   = mode,
      size   = size,
      addrs0 = simpleAddr(addr0),
      addrd0 = BigInt(0),
    )

  def assembleLd(func: Int, mode: Int, size: Int, addrD: Int): Encoded =
    Encoding.encodeLoad(
      func   = func,
      mode   = mode,
      size   = size,
      addrd0 = simpleAddr(addrD),
    )

  def assembleSt(func: Int, size: Int, addrS: Int): Encoded =
    Encoding.encodeStore(
      func   = func,
      size   = size,
      addrs0 = simpleAddr(addrS),
    )

  def assembleDma(func: Int, size: Int, addrS: Int): Encoded =
    Encoding.encodeDMA(
      func    = func,
      size    = size,
      addrs0  = simpleAddr(addrS),
      addrd0  = BigInt(0),
      dmaAddr = 0,
    )

  def assembleSemProg(semAddr: Int, init0: Int, init1: Int): Encoded =
    // encodeSemProg parameter naming: init0 here = initValues(0) = fullReg
    Encoding.encodeSemProg(semAddr = semAddr, initEmpty = init1, initFull = init0)

  // ── AXI-S streaming ───────────────────────────────────────────────────────

  def waitFor(clock: chisel3.Clock)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      clock.step()
      cycles += 1
    }
  }

  /** Pack `insts` into 128-bit beats and stream them via AXIST.  Multiple short
    * instructions can land in a single beat; an Execute straddles two beats.
    */
  def sendInstructions(dut: FrontEnd, insts: Encoded*): Unit = {
    for (beat <- Encoding.packBeats(insts)) {
      dut.io.AXIST.tdata.poke(beat.U(128.W))
      dut.io.AXIST.tvalid.poke(true.B)
      dut.io.AXIST.tkeep.poke("hffff".U)
      // Wait for tready so we don't drop a beat the receiver isn't accepting yet.
      waitFor(dut.clock)(dut.io.AXIST.tready.peek().litToBoolean, "AXIST.tready")
      dut.clock.step()
    }
    dut.io.AXIST.tvalid.poke(false.B)
  }

  def idleAXI(dut: FrontEnd): Unit = {
    dut.io.AXIST.tvalid.poke(false.B)
  }

  def defaultPokes(dut: FrontEnd): Unit = {
    dut.io.AXIST.tvalid.poke(false.B)
    dut.io.AXIST.tdata.poke(0.U)
    dut.io.AXIST.tkeep.poke(0.U)
    dut.io.exeStream.ready.poke(false.B)
    dut.io.loadStream.ready.poke(false.B)
    dut.io.storeStream.ready.poke(false.B)
    dut.io.dmaStream.ready.poke(false.B)
    dut.io.semProgStream.ready.poke(false.B)
  }

  // ── Tests ─────────────────────────────────────────────────────────────────

  "FrontEnd should decode and dispatch an execute instruction" in {
    implicit val c = Configuration.default()

    simulate(new FrontEnd()) { dut =>
      defaultPokes(dut)

      sendInstructions(dut, assembleExe(mode = 0, size = 8, addr0 = 42))
      idleAXI(dut)

      dut.io.exeStream.ready.poke(true.B)
      waitFor(dut.clock)(dut.io.exeStream.valid.peek().litToBoolean, "exeStream.valid")

      dut.io.exeStream.bits.opcode.expect(1.U)
      dut.io.exeStream.bits.mode.expect(0.U)
      dut.io.exeStream.bits.size.expect(8.U)
      dut.io.exeStream.bits.addrs(0).addr.expect(42.U)

      dut.io.loadStream.valid.expect(false.B)
      dut.io.storeStream.valid.expect(false.B)
      dut.io.dmaStream.valid.expect(false.B)
      dut.io.semProgStream.valid.expect(false.B)

      dut.clock.step()
    }
  }

  "FrontEnd should decode and dispatch a load instruction" in {
    implicit val c = Configuration.default()

    simulate(new FrontEnd()) { dut =>
      defaultPokes(dut)

      sendInstructions(dut, assembleLd(func = 1, mode = 0, size = 16, addrD = 100))
      idleAXI(dut)

      dut.io.loadStream.ready.poke(true.B)
      waitFor(dut.clock)(dut.io.loadStream.valid.peek().litToBoolean, "loadStream.valid")

      dut.io.loadStream.bits.opcode.expect(2.U)
      dut.io.loadStream.bits.func.expect(1.U)
      dut.io.loadStream.bits.size.expect(16.U)
      dut.io.loadStream.bits.addrd(0).addr.expect(100.U)

      dut.io.exeStream.valid.expect(false.B)
      dut.io.storeStream.valid.expect(false.B)

      dut.clock.step()
    }
  }

  "FrontEnd should decode and dispatch a store instruction" in {
    implicit val c = Configuration.default()

    simulate(new FrontEnd()) { dut =>
      defaultPokes(dut)

      sendInstructions(dut, assembleSt(func = 0, size = 32, addrS = 200))
      idleAXI(dut)

      dut.io.storeStream.ready.poke(true.B)
      waitFor(dut.clock)(dut.io.storeStream.valid.peek().litToBoolean, "storeStream.valid")

      dut.io.storeStream.bits.opcode.expect(3.U)
      dut.io.storeStream.bits.func.expect(0.U)
      dut.io.storeStream.bits.size.expect(32.U)
      dut.io.storeStream.bits.addrs(0).addr.expect(200.U)

      dut.io.exeStream.valid.expect(false.B)
      dut.io.loadStream.valid.expect(false.B)

      dut.clock.step()
    }
  }

  "FrontEnd should decode and dispatch a DMA instruction" in {
    implicit val c = Configuration.default()

    simulate(new FrontEnd()) { dut =>
      defaultPokes(dut)

      sendInstructions(dut, assembleDma(func = 1, size = 64, addrS = 512))
      idleAXI(dut)

      dut.io.dmaStream.ready.poke(true.B)
      waitFor(dut.clock)(dut.io.dmaStream.valid.peek().litToBoolean, "dmaStream.valid")

      dut.io.dmaStream.bits.opcode.expect(4.U)
      dut.io.dmaStream.bits.func.expect(1.U)
      dut.io.dmaStream.bits.size.expect(64.U)
      dut.io.dmaStream.bits.addrs(0).addr.expect(512.U)

      dut.io.exeStream.valid.expect(false.B)
      dut.io.loadStream.valid.expect(false.B)
      dut.io.storeStream.valid.expect(false.B)
      dut.io.semProgStream.valid.expect(false.B)

      dut.clock.step()
    }
  }

  "FrontEnd should decode and dispatch a semaphore program instruction" in {
    implicit val c = Configuration.default()

    simulate(new FrontEnd()) { dut =>
      defaultPokes(dut)

      sendInstructions(dut, assembleSemProg(semAddr = 3, init0 = 10, init1 = 20))
      idleAXI(dut)

      dut.io.semProgStream.ready.poke(true.B)
      waitFor(dut.clock)(dut.io.semProgStream.valid.peek().litToBoolean, "semProgStream.valid")

      dut.io.semProgStream.bits.opcode.expect(5.U)
      dut.io.semProgStream.bits.payload.semAddr.expect(3.U)
      dut.io.semProgStream.bits.payload.initValues(0).expect(10.U)
      dut.io.semProgStream.bits.payload.initValues(1).expect(20.U)

      dut.io.exeStream.valid.expect(false.B)
      dut.io.loadStream.valid.expect(false.B)
      dut.io.storeStream.valid.expect(false.B)
      dut.io.dmaStream.valid.expect(false.B)

      dut.clock.step()
    }
  }

  "FrontEnd should dispatch mixed instruction types in order" in {
    implicit val c = Configuration.default()

    simulate(new FrontEnd()) { dut =>
      defaultPokes(dut)

      sendInstructions(
        dut,
        assembleExe(mode = 1, size = 4, addr0 = 10),
        assembleLd(func = 0, mode = 1, size = 8, addrD = 20),
        assembleSt(func = 1, size = 16, addrS = 30),
        assembleDma(func = 0, size = 32, addrS = 40),
        assembleSemProg(semAddr = 1, init0 = 5, init1 = 15),
      )
      idleAXI(dut)

      // 1. Execute
      dut.io.exeStream.ready.poke(true.B)
      waitFor(dut.clock)(dut.io.exeStream.valid.peek().litToBoolean, "exeStream.valid")
      dut.io.exeStream.bits.mode.expect(1.U)
      dut.io.exeStream.bits.size.expect(4.U)
      dut.io.exeStream.bits.addrs(0).addr.expect(10.U)
      dut.clock.step()
      dut.io.exeStream.ready.poke(false.B)

      // 2. Load
      dut.io.loadStream.ready.poke(true.B)
      waitFor(dut.clock)(dut.io.loadStream.valid.peek().litToBoolean, "loadStream.valid")
      dut.io.loadStream.bits.mode.expect(1.U)
      dut.io.loadStream.bits.size.expect(8.U)
      dut.io.loadStream.bits.addrd(0).addr.expect(20.U)
      dut.clock.step()
      dut.io.loadStream.ready.poke(false.B)

      // 3. Store
      dut.io.storeStream.ready.poke(true.B)
      waitFor(dut.clock)(dut.io.storeStream.valid.peek().litToBoolean, "storeStream.valid")
      dut.io.storeStream.bits.size.expect(16.U)
      dut.io.storeStream.bits.addrs(0).addr.expect(30.U)
      dut.clock.step()
      dut.io.storeStream.ready.poke(false.B)

      // 4. DMA
      dut.io.dmaStream.ready.poke(true.B)
      waitFor(dut.clock)(dut.io.dmaStream.valid.peek().litToBoolean, "dmaStream.valid")
      dut.io.dmaStream.bits.size.expect(32.U)
      dut.io.dmaStream.bits.addrs(0).addr.expect(40.U)
      dut.clock.step()
      dut.io.dmaStream.ready.poke(false.B)

      // 5. SemProg
      dut.io.semProgStream.ready.poke(true.B)
      waitFor(dut.clock)(dut.io.semProgStream.valid.peek().litToBoolean, "semProgStream.valid")
      dut.io.semProgStream.bits.payload.semAddr.expect(1.U)
      dut.io.semProgStream.bits.payload.initValues(0).expect(5.U)
      dut.io.semProgStream.bits.payload.initValues(1).expect(15.U)
      dut.clock.step()
      dut.io.semProgStream.ready.poke(false.B)
    }
  }

  "FrontEnd should handle backpressure" in {
    implicit val c = Configuration.default()

    simulate(new FrontEnd()) { dut =>
      defaultPokes(dut)

      sendInstructions(
        dut,
        assembleExe(mode = 0, size = 1, addr0 = 100),
        assembleExe(mode = 0, size = 2, addr0 = 200),
      )
      idleAXI(dut)

      // Let the pipeline fill while exeStream stays not-ready.
      dut.clock.step(5)

      dut.io.exeStream.ready.poke(true.B)

      waitFor(dut.clock)(dut.io.exeStream.valid.peek().litToBoolean, "first exe valid")
      dut.io.exeStream.bits.size.expect(1.U)
      dut.io.exeStream.bits.addrs(0).addr.expect(100.U)
      dut.clock.step()

      waitFor(dut.clock)(dut.io.exeStream.valid.peek().litToBoolean, "second exe valid")
      dut.io.exeStream.bits.size.expect(2.U)
      dut.io.exeStream.bits.addrs(0).addr.expect(200.U)
      dut.clock.step()
    }
  }
}
