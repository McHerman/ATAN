package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class FrontendTest extends AnyFreeSpec with Matchers with ChiselSim {

  val maxCycles = 500

  // ── Instruction assembly helpers ──────────────────────────────────────────
  // These build 64-bit raw instruction values matching the Decodable layouts.
  //
  // addrPkg (36 bits) Chisel bit ordering (first-declared = MSB):
  //   [35:20] addr
  //   [19:0]  sem  (Valid bundle, all zeros when sem disabled)
  //
  // With a 64-bit AXI bus, only instruction bits [63:0] are populated.

  def addrPkgBits(addr: Int): BigInt = BigInt(addr & 0xFFFF) << 20

  /** ExecuteInst: opcode[5:0]=1, func[6], mode[7], size[15:8], addrs(0)[51:16] */
  def assembleExe(mode: Int, size: Int, addr0: Int): BigInt = {
    var inst = BigInt(1)                        // opcode = 1
    inst |= BigInt(mode & 0x1) << 7             // mode
    inst |= BigInt(size & 0xFF) << 8            // size
    inst |= addrPkgBits(addr0) << 16            // addrs(0)
    inst
  }

  /** LoadInst: opcode[5:0]=2, func[6], mode[7], size[15:8], addrd(0)[51:16] */
  def assembleLd(func: Int, mode: Int, size: Int, addrD: Int): BigInt = {
    var inst = BigInt(2)                        // opcode = 2
    inst |= BigInt(func & 0x1) << 6             // func
    inst |= BigInt(mode & 0x1) << 7             // mode
    inst |= BigInt(size & 0xFF) << 8            // size
    inst |= addrPkgBits(addrD) << 16            // addrd(0)
    inst
  }

  /** StoreInst: opcode[5:0]=3, func[6], size[15:8], addrs(0)[51:16] */
  def assembleSt(func: Int, size: Int, addrS: Int): BigInt = {
    var inst = BigInt(3)                        // opcode = 3
    inst |= BigInt(func & 0x1) << 6             // func
    inst |= BigInt(size & 0xFF) << 8            // size
    inst |= addrPkgBits(addrS) << 16            // addrs(0)
    inst
  }

  /** DMAInst: opcode[5:0]=4, func[6], size[15:8], addrs(0)[51:16]
   *  (addrd(0) and DMAAddr are above bit 63, so zero with 64-bit AXI) */
  def assembleDma(func: Int, size: Int, addrS: Int): BigInt = {
    var inst = BigInt(4)                        // opcode = 4
    inst |= BigInt(func & 0x1) << 6             // func
    inst |= BigInt(size & 0xFF) << 8            // size
    inst |= addrPkgBits(addrS) << 16            // addrs(0)
    inst
  }

  /** SemProgInst: opcode[5:0]=5, semAddr[13:6], initValues[45:14]
   *  Vec(2, UInt(16.W)): element 0 at lower bits [29:14], element 1 at [45:30] */
  def assembleSemProg(semAddr: Int, init0: Int, init1: Int): BigInt = {
    var inst = BigInt(5)                        // opcode = 5
    inst |= BigInt(semAddr & 0xFF) << 6         // semAddr
    inst |= BigInt(init0 & 0xFFFF) << 14        // initValues(0)
    inst |= BigInt(init1 & 0xFFFF) << 30        // initValues(1)
    inst
  }

  // ── Test helpers ──────────────────────────────────────────────────────────

  def waitFor(clock: chisel3.Clock)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      clock.step()
      cycles += 1
    }
  }

  def sendInstruction(dut: FrontEnd, raw: BigInt): Unit = {
    dut.io.AXIST.tdata.poke(raw.U(64.W))
    dut.io.AXIST.tvalid.poke(true.B)
    dut.io.AXIST.tkeep.poke("hff".U)
    dut.clock.step()
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

      // Send one execute instruction
      sendInstruction(dut, assembleExe(mode = 0, size = 8, addr0 = 42))
      idleAXI(dut)

      // It should appear on exeStream
      dut.io.exeStream.ready.poke(true.B)
      waitFor(dut.clock)(dut.io.exeStream.valid.peek().litToBoolean, "exeStream.valid")

      dut.io.exeStream.bits.opcode.expect(1.U)
      dut.io.exeStream.bits.mode.expect(0.U)
      dut.io.exeStream.bits.size.expect(8.U)
      dut.io.exeStream.bits.addrs(0).addr.expect(42.U)

      // Other streams should be idle
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

      sendInstruction(dut, assembleLd(func = 1, mode = 0, size = 16, addrD = 100))
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

      sendInstruction(dut, assembleSt(func = 0, size = 32, addrS = 200))
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

      sendInstruction(dut, assembleDma(func = 1, size = 64, addrS = 512))
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

      sendInstruction(dut, assembleSemProg(semAddr = 3, init0 = 10, init1 = 20))
      idleAXI(dut)

      dut.io.semProgStream.ready.poke(true.B)
      waitFor(dut.clock)(dut.io.semProgStream.valid.peek().litToBoolean, "semProgStream.valid")

      dut.io.semProgStream.bits.opcode.expect(5.U)
      dut.io.semProgStream.bits.semAddr.expect(3.U)
      dut.io.semProgStream.bits.initValues(0).expect(10.U)
      dut.io.semProgStream.bits.initValues(1).expect(20.U)

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

      // Send one of each type
      sendInstruction(dut, assembleExe(mode = 1, size = 4, addr0 = 10))
      sendInstruction(dut, assembleLd(func = 0, mode = 1, size = 8, addrD = 20))
      sendInstruction(dut, assembleSt(func = 1, size = 16, addrS = 30))
      sendInstruction(dut, assembleDma(func = 0, size = 32, addrS = 40))
      sendInstruction(dut, assembleSemProg(semAddr = 1, init0 = 5, init1 = 15))
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
      dut.io.semProgStream.bits.semAddr.expect(1.U)
      dut.io.semProgStream.bits.initValues(0).expect(5.U)
      dut.io.semProgStream.bits.initValues(1).expect(15.U)
      dut.clock.step()
      dut.io.semProgStream.ready.poke(false.B)
    }
  }

  "FrontEnd should handle backpressure" in {
    implicit val c = Configuration.default()

    simulate(new FrontEnd()) { dut =>
      defaultPokes(dut)

      // Send two execute instructions while exeStream is not ready
      sendInstruction(dut, assembleExe(mode = 0, size = 1, addr0 = 100))
      sendInstruction(dut, assembleExe(mode = 0, size = 2, addr0 = 200))
      idleAXI(dut)

      // Neither should be lost — first instruction should stall in the pipeline
      dut.clock.step(5)

      // Now accept them one by one
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
