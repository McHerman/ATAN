package ATA8

import chisel3._
import chisel3.util._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

/**
 * Test DUT: 3-tier MemSystem with SemaphoreBank wired to all DMA semaphore ports.
 *
 * Semaphore bank port assignment (4 ports total for 2 DMAs):
 *   inPort 0 ← DMA[0].semaphoreA  (pipeline A, portA side = tier[0])
 *   inPort 1 ← DMA[0].semaphoreB  (pipeline B, portB side = tier[1])
 *   inPort 2 ← DMA[1].semaphoreA  (pipeline A, portA side = tier[1])
 *   inPort 3 ← DMA[1].semaphoreB  (pipeline B, portB side = tier[2])
 *
 * TileLink address routing in the SemaphoreBank xbar (mask=0x1, bit-0 = reg select):
 *   address  0..1  → slave 0 → semaphore 0, port 0  (used by inPort 0)
 *   address  4..5  → slave 2 → semaphore 1, port 0  (used by inPort 1)
 *   address  8..9  → slave 4 → semaphore 2, port 0  (used by inPort 2)
 *   address 12..13 → slave 6 → semaphore 3, port 0  (used by inPort 3)
 */
class ThreeTierDUT(msCfg: MemSystemConfig, bankCfg: Configuration) extends Module {
  private val nDMAs = msCfg.tiers.length - 1 // 2

  val memSys  = Module(new MemSystem(bankCfg)(msCfg))
  val semBank = Module(new SemaphoreBank(nDMAs * 2)(bankCfg))

  // DMA[i].semaphoreA → inPort(2*i),  DMA[i].semaphoreB → inPort(2*i+1)
  for (i <- 0 until nDMAs) {
    semBank.io.inPorts(i * 2)     <> memSys.io.semaphoreA(i)
    semBank.io.inPorts(i * 2 + 1) <> memSys.io.semaphoreB(i)
  }

  val io = IO(new Bundle {
    val tier0Write          = Flipped(new TilelinkPort()(msCfg))
    val tier0Read           = Flipped(new TilelinkPort()(msCfg))
    val dmaInstructionStream = Flipped(Decoupled(new DMAInst()(bankCfg)))
    val semProgPort         = Flipped(Decoupled(new SemaphoreProgPort()(bankCfg)))
  })

  io.tier0Write          <> memSys.io.tier0WritePorts(0)
  io.tier0Read           <> memSys.io.tier0ReadPorts(0)
  io.dmaInstructionStream <> memSys.io.dmaInstructionStream
  io.semProgPort         <> semBank.io.progPort


  memSys.io.hostIn.a.valid := false.B
  memSys.io.hostIn.a.bits := DontCare 

  memSys.io.hostIn.d.ready := false.B 
}

class MemSystemDUT(msCfg: MemSystemConfig, bankCfg: Configuration) extends Module {
  private val nDMAs = msCfg.tiers.length - 1 // 2

  val memSys  = Module(new MemSystem(bankCfg)(msCfg))
  val semSys = Module(new SemSystem(nDMAs * 2)(bankCfg))

  // DMA[i].semaphoreA → inPort(2*i),  DMA[i].semaphoreB → inPort(2*i+1)
  for (i <- 0 until nDMAs) {
    semSys.io.inPorts(i * 2)     <> memSys.io.semaphoreA(i)
    semSys.io.inPorts(i * 2 + 1) <> memSys.io.semaphoreB(i)
  }

  val io = IO(new Bundle {
    val tier0Write          = Flipped(new TilelinkPort()(msCfg))
    val tier0Read           = Flipped(new TilelinkPort()(msCfg))
    val dmaInstructionStream = Flipped(Decoupled(new DMAInst()(bankCfg)))
    val semInstructionStream = Flipped(Decoupled(new SemProgInst()(bankCfg)))
  })

  io.tier0Write          <> memSys.io.tier0WritePorts(0)
  io.tier0Read           <> memSys.io.tier0ReadPorts(0)
  io.dmaInstructionStream <> memSys.io.dmaInstructionStream
  io.semInstructionStream <> semSys.io.instructionStream


  memSys.io.hostIn.a.valid := false.B
  memSys.io.hostIn.a.bits := DontCare 

  memSys.io.hostIn.d.ready := false.B 
}


class HostInDUT(msCfg: MemSystemConfig, bankCfg: Configuration) extends Module {
  private val nDMAs = msCfg.tiers.length - 1

  val memSys  = Module(new MemSystem(bankCfg)(msCfg))
  val semBank = Module(new SemaphoreBank(nDMAs * 2)(bankCfg))

  for (i <- 0 until nDMAs) {
    semBank.io.inPorts(i * 2)     <> memSys.io.semaphoreA(i)
    semBank.io.inPorts(i * 2 + 1) <> memSys.io.semaphoreB(i)
  }

  val io = IO(new Bundle {
    val hostIn               = Flipped(new TilelinkPort()(msCfg))
    val tier0Write           = Flipped(new TilelinkPort()(msCfg))
    val tier0Read            = Flipped(new TilelinkPort()(msCfg))
    val dmaInstructionStream = Flipped(Decoupled(new DMAInst()(bankCfg)))
    val semProgPort          = Flipped(Decoupled(new SemaphoreProgPort()(bankCfg)))
  })

  io.hostIn              <> memSys.io.hostIn
  io.tier0Write          <> memSys.io.tier0WritePorts(0)
  io.tier0Read           <> memSys.io.tier0ReadPorts(0)
  io.dmaInstructionStream <> memSys.io.dmaInstructionStream
  io.semProgPort         <> semBank.io.progPort
}

class MemSystemThreeTierTest extends AnyFreeSpec with Matchers with ChiselSim {

  // ── Configuration ──────────────────────────────────────────────────────────
  // sourceWidth=8: TLXbar assigns up to 10 source IDs per master; 4 masters
  // need at least log2(40)=6 bits, so 8 is safe.
  val msCfg: MemSystemConfig = MemSystemConfig(
    tiers = Seq(
      TierConfig(nWritePorts = 1, nReadPorts = 1, nBanks = 4, bankDepth = 256),
      TierConfig(nWritePorts = 1, nReadPorts = 1, nBanks = 4, bankDepth = 256),
      TierConfig(nWritePorts = 1, nReadPorts = 1, nBanks = 4, bankDepth = 256)
    ),
    dataBusSize    = 8,
    arithDataWidth = 8,
    addrWidth      = 16,
    sourceWidth    = 8
  )

  // Must share bus widths with msCfg so TilelinkPort bundles are compatible.
  val bankCfg: Configuration = Configuration.default().withBus(_.copy(sourceWidth = 8))

  // Semaphore TL addresses for each DMA pipeline (fullReg, bit-0=0):
  //   SEM_DMA0_A is sent by inPort 0 → slave 0  → semaphore 0, port 0
  //   SEM_DMA0_B is sent by inPort 1 → slave 2  → semaphore 1, port 0
  //   SEM_DMA1_A is sent by inPort 2 → slave 4  → semaphore 2, port 0
  //   SEM_DMA1_B is sent by inPort 3 → slave 6  → semaphore 3, port 0
  val SEM_0_P = 0
  val SEM_0_C = 2
  val SEM_1_P = 4 
  val SEM_1_C = 6 

  val N         = 4     // beats per transfer
  val maxCycles = 3000  // generous timeout

  // ── Helpers ────────────────────────────────────────────────────────────────

  def waitFor(dut: Module)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout ($maxCycles cycles) waiting for: $msg")
      dut.clock.step()
      cycles += 1
    }
  }

  /** Program semaphore semIdx via the SemaphoreBank progPort. */
  def programSemaphore(dut: ThreeTierDUT, semIdx: Int, full: Int, empty: Int): Unit = {
    dut.io.semProgPort.bits.addr.poke(semIdx.U)
    dut.io.semProgPort.bits.initValues(0).poke(full.U)
    dut.io.semProgPort.bits.initValues(1).poke(empty.U)
    dut.clock.step()
    dut.io.semProgPort.ready.expect(true.B)
    dut.io.semProgPort.valid.poke(true.B)
    dut.clock.step()
    dut.io.semProgPort.valid.poke(false.B)
  }


  // ── Test ───────────────────────────────────────────────────────────────────

  "Three-tier MemSystem: DMA chain with semaphore-synchronized transfers" in {
    simulate(new ThreeTierDUT(msCfg, bankCfg)) { dut =>

      //idlePorts(dut)
      dut.clock.step(2)

      val testData = Seq[BigInt](
        BigInt("0102030405060708", 16),
        BigInt("090A0B0C0D0E0F10", 16),
        BigInt("1112131415161718", 16),
        BigInt("191A1B1C1D1E1F20", 16)
      )

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 1 – Write test data to tier 0 (tier[0]) via external write port
      // ═══════════════════════════════════════════════════════════════════════

      val port = dut.io.tier0Write
      port.d.ready.poke(true.B)

      for ((beat, i) <- testData.zipWithIndex) {
        waitFor(dut)(port.a.ready.peek().litToBoolean, s"tier0Write.a.ready beat $i")
        port.a.valid.poke(true.B)
        port.a.bits.opcode.poke(TilelinkOpcodes.PutFullData)
        port.a.bits.param.poke(0.U)
        port.a.bits.size.poke(testData.length.U)
        port.a.bits.source.poke(0.U)
        //port.a.bits.address.poke(i.U)  // TilelinkWriteHandler uses address of first beat
        port.a.bits.address.poke(0.U)  // TilelinkWriteHandler uses address of first beat
        port.a.bits.mask.poke(0xFF.U)
        port.a.bits.data.poke(beat.U)
        port.a.bits.corrupt.poke(0.U)
        dut.clock.step()
      }
      port.a.valid.poke(false.B)

      waitFor(dut)(port.d.valid.peek().litToBoolean, "tier0Write AccessAck")
      port.d.bits.opcode.expect(TilelinkOpcodes.AccessAck)
      dut.clock.step()
      port.d.ready.poke(false.B)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 2 – Downward: t3→t2 (DMA[0]) and t2→t1 (DMA[1]) in sequence
      //
      // DMA[0] (producer): A reads t0 - no semaphore, B writes t1 - producer semaphore 
      // DMA[1] (consumer): A reads t1 - consumer semaphore (blocked), B writes t2
      //
      // Consumer DMA[1].A blocks on semaphore 0 (inPort 0, TL addr 4) fullReg=0.
      // After producer DMA[0] completes, test programs sem to unblock prod and consumer.
      // ═══════════════════════════════════════════════════════════════════════

      // Init semaphore 0: fullReg=0 (consumer blocks), emptyReg=N (producer can start)
      programSemaphore(dut, semIdx = 0, full = 0, empty = N)

      // Enqueue both DMA instructions through the command queue
      // DMA[0]: read from t0 addr 0, write to t1 addr 0, prod semaphore 
      //enqueueDMAInst(dut, dmaIdx = 0, srcAddr = 0, dstAddr = 0, size = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(0.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(0.U)

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(0.U)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(SEM_0_P.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      var cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[0]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)


      // DMA[1]: read from t1 addr 0 (with sem), write to t2 addr 0
      //enqueueDMAInst(dut, dmaIdx = 1, srcAddr = 0, dstAddr = 0, size = N,
      //  srcSemEn = true, srcSemAddr = SEM_DMA1_A, srcSemStep = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(1.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(0.U)

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(SEM_0_C.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(4.U)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[1]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)

      // Wait for DMA[0] and DMA[1] to complete
      dut.clock.step(200)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 3 – Upward: t2→t1 (DMA[1]) and t1→t0(DMA[0]) in sequence
      //
      // DMA[1] (producer): A writes t1, B reads t2 — prod semaphore
      // DMA[0] (consumer): A writes t0, B reads t1 — cons semaphore
      //
      // After Phase 2: fullReg=0, emptyReg=N — naturally the right initial
      // state (producer acquires emptyReg, consumer blocks on fullReg).
      // ═══════════════════════════════════════════════════════════════════════

      programSemaphore(dut, semIdx = 1, full = 0, empty = N)


      // DMA[1]: read from t2 addr 0, write to t1 addr N 
      //enqueueDMAInst(dut, dmaIdx = 1, srcAddr = 0, dstAddr = 0, size = N,
      //  srcSemEn = true, srcSemAddr = SEM_DMA1_A, srcSemStep = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(1.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(1.U) 
      // We reverse the directionality of the write, data flows from rd to rs
      // B to A  

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(N.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(SEM_1_P.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(4.U)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[1]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)



      // Enqueue both DMA instructions through the command queue
      // DMA[0]: read from t3 addr 0, write to t2 addr 0, no semaphores
      //enqueueDMAInst(dut, dmaIdx = 0, srcAddr = 0, dstAddr = 0, size = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(0.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(1.U)

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(N.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(0.U)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(N.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(SEM_1_C.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[0]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)

      // Wait for DMA[0] and DMA[1] to complete
      dut.clock.step(200)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 4 – Verify: read tier 3 at address 0 to check data survived
      // the downward trip (t3 was not overwritten, data should still be there)
      // ═══════════════════════════════════════════════════════════════════════
      //val readback = readTier3(dut, N, addr = 0)

      val readport = dut.io.tier0Read

      waitFor(dut)(readport.a.ready.peek().litToBoolean, "tier0Read.a.ready")
      readport.a.valid.poke(true.B)
      readport.a.bits.opcode.poke(TilelinkOpcodes.Get)
      readport.a.bits.param.poke(0.U)
      readport.a.bits.size.poke(N.U)
      readport.a.bits.source.poke(0.U)
      readport.a.bits.address.poke(N.U)
      readport.a.bits.mask.poke(0xFF.U)
      readport.a.bits.data.poke(0.U)
      readport.a.bits.corrupt.poke(0.U)
      dut.clock.step()
      readport.a.valid.poke(false.B)

      readport.d.ready.poke(true.B)
      val buf = collection.mutable.ArrayBuffer[BigInt]()
      while (buf.length < N) {
        waitFor(dut)(readport.d.valid.peek().litToBoolean, s"tier0Read D beat ${buf.length}")
        buf += readport.d.bits.data.peek().litValue
        dut.clock.step()
      }
      readport.d.ready.poke(false.B)
      val readback = buf.toSeq



      assert(
        readback == testData,
        s"Verification mismatch:\n" +
        s"  expected: ${testData.map(x => f"0x$x%016x")}\n" +
        s"  got:      ${readback.map(x => f"0x$x%016x")}"
      )
    }
  }

  "Three-tier MemSystem: DMA chain with semaphore-synchronized transfers and different order" in {
    simulate(new ThreeTierDUT(msCfg, bankCfg)) { dut =>

      //idlePorts(dut)
      dut.clock.step(2)

      val testData = Seq[BigInt](
        BigInt("0102030405060708", 16),
        BigInt("090A0B0C0D0E0F10", 16),
        BigInt("1112131415161718", 16),
        BigInt("191A1B1C1D1E1F20", 16)
      )

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 1 – Write test data to tier 0 (tier[0]) via external write port
      // ═══════════════════════════════════════════════════════════════════════

      val port = dut.io.tier0Write
      port.d.ready.poke(true.B)

      for ((beat, i) <- testData.zipWithIndex) {
        waitFor(dut)(port.a.ready.peek().litToBoolean, s"tier0Write.a.ready beat $i")
        port.a.valid.poke(true.B)
        port.a.bits.opcode.poke(TilelinkOpcodes.PutFullData)
        port.a.bits.param.poke(0.U)
        port.a.bits.size.poke(testData.length.U)
        port.a.bits.source.poke(0.U)
        //port.a.bits.address.poke(i.U)  // TilelinkWriteHandler uses address of first beat
        port.a.bits.address.poke(0.U)  // TilelinkWriteHandler uses address of first beat
        port.a.bits.mask.poke(0xFF.U)
        port.a.bits.data.poke(beat.U)
        port.a.bits.corrupt.poke(0.U)
        dut.clock.step()
      }
      port.a.valid.poke(false.B)

      waitFor(dut)(port.d.valid.peek().litToBoolean, "tier0Write AccessAck")
      port.d.bits.opcode.expect(TilelinkOpcodes.AccessAck)
      dut.clock.step()
      port.d.ready.poke(false.B)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 2 – Downward: t3→t2 (DMA[0]) and t2→t1 (DMA[1]) in sequence
      //
      // DMA[0] (producer): A reads t0 - no semaphore, B writes t1 - producer semaphore 
      // DMA[1] (consumer): A reads t1 - consumer semaphore (blocked), B writes t2
      //
      // Consumer DMA[1].A blocks on semaphore 0 (inPort 0, TL addr 4) fullReg=0.
      // After producer DMA[0] completes, test programs sem to unblock prod and consumer.
      // ═══════════════════════════════════════════════════════════════════════

      // Init semaphore 0: fullReg=0 (consumer blocks), emptyReg=N (producer can start)
      programSemaphore(dut, semIdx = 0, full = 0, empty = N)


      // DMA[1]: read from t1 addr 0 (with sem), write to t2 addr 0
      //enqueueDMAInst(dut, dmaIdx = 1, srcAddr = 0, dstAddr = 0, size = N,
      //  srcSemEn = true, srcSemAddr = SEM_DMA1_A, srcSemStep = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(1.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(0.U)

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(SEM_0_C.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(4.U)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      var cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[1]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)

      // Enqueue both DMA instructions through the command queue
      // DMA[0]: read from t0 addr 0, write to t1 addr 0, prod semaphore 
      //enqueueDMAInst(dut, dmaIdx = 0, srcAddr = 0, dstAddr = 0, size = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(0.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(0.U)

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(0.U)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(SEM_0_P.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[0]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)

      // Wait for DMA[0] and DMA[1] to complete
      dut.clock.step(200)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 3 – Upward: t2→t1 (DMA[1]) and t1→t0(DMA[0]) in sequence
      //
      // DMA[1] (producer): A writes t1, B reads t2 — prod semaphore
      // DMA[0] (consumer): A writes t0, B reads t1 — cons semaphore
      //
      // After Phase 2: fullReg=0, emptyReg=N — naturally the right initial
      // state (producer acquires emptyReg, consumer blocks on fullReg).
      // ═══════════════════════════════════════════════════════════════════════

      // Enqueue both DMA instructions through the command queue
      // DMA[0]: read from t3 addr 0, write to t2 addr 0, no semaphores
      //enqueueDMAInst(dut, dmaIdx = 0, srcAddr = 0, dstAddr = 0, size = N)


      programSemaphore(dut, semIdx = 1, full = 0, empty = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(0.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(1.U)

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(N.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(0.U)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(N.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(SEM_1_C.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[0]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)

      // DMA[1]: read from t2 addr 0, write to t1 addr N 
      //enqueueDMAInst(dut, dmaIdx = 1, srcAddr = 0, dstAddr = 0, size = N,
      //  srcSemEn = true, srcSemAddr = SEM_DMA1_A, srcSemStep = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(1.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(1.U) 
      // We reverse the directionality of the write, data flows from rd to rs
      // B to A  

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(N.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(SEM_1_P.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(4.U)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[1]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)

      // Wait for DMA[0] and DMA[1] to complete
      dut.clock.step(200)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 4 – Verify: read tier 3 at address 0 to check data survived
      // the downward trip (t3 was not overwritten, data should still be there)
      // ═══════════════════════════════════════════════════════════════════════
      //val readback = readTier3(dut, N, addr = 0)

      val readport = dut.io.tier0Read

      waitFor(dut)(readport.a.ready.peek().litToBoolean, "tier0Read.a.ready")
      readport.a.valid.poke(true.B)
      readport.a.bits.opcode.poke(TilelinkOpcodes.Get)
      readport.a.bits.param.poke(0.U)
      readport.a.bits.size.poke(N.U)
      readport.a.bits.source.poke(0.U)
      readport.a.bits.address.poke(N.U)
      readport.a.bits.mask.poke(0xFF.U)
      readport.a.bits.data.poke(0.U)
      readport.a.bits.corrupt.poke(0.U)
      dut.clock.step()
      readport.a.valid.poke(false.B)

      readport.d.ready.poke(true.B)
      val buf = collection.mutable.ArrayBuffer[BigInt]()
      while (buf.length < N) {
        waitFor(dut)(readport.d.valid.peek().litToBoolean, s"tier0Read D beat ${buf.length}")
        buf += readport.d.bits.data.peek().litValue
        dut.clock.step()
      }
      readport.d.ready.poke(false.B)
      val readback = buf.toSeq



      assert(
        readback == testData,
        s"Verification mismatch:\n" +
        s"  expected: ${testData.map(x => f"0x$x%016x")}\n" +
        s"  got:      ${readback.map(x => f"0x$x%016x")}"
      )
    }
  }

  "Three-tier MemSystem: DMA chain with semsystem and single sem" in {
    simulate(new MemSystemDUT(msCfg, bankCfg)) { dut =>

      //idlePorts(dut)
      //dut.clock.step(2)

      val testData = Seq[BigInt](
        BigInt("0102030405060708", 16),
        BigInt("090A0B0C0D0E0F10", 16),
        BigInt("1112131415161718", 16),
        BigInt("191A1B1C1D1E1F20", 16)
      )

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 1 – Write test data to tier 0 (tier[0]) via external write port
      // ═══════════════════════════════════════════════════════════════════════

      val port = dut.io.tier0Write
      port.d.ready.poke(true.B)

      dut.io.semInstructionStream.ready.expect(true.B)
      dut.io.semInstructionStream.valid.poke(true.B)
      dut.io.semInstructionStream.bits.payload.semAddr.poke(0.U)
      dut.io.semInstructionStream.bits.payload.initValues(0).poke(0.U)
      dut.io.semInstructionStream.bits.payload.initValues(1).poke(N.U)

      dut.clock.step()

      dut.io.semInstructionStream.ready.expect(true.B)
      dut.io.semInstructionStream.valid.poke(true.B)
      dut.io.semInstructionStream.bits.payload.semAddr.poke(0.U)
      dut.io.semInstructionStream.bits.payload.initValues(0).poke(0.U)
      dut.io.semInstructionStream.bits.payload.initValues(1).poke(N.U)

      dut.clock.step()

      dut.io.semInstructionStream.valid.poke(false.B)


      for ((beat, i) <- testData.zipWithIndex) {
        waitFor(dut)(port.a.ready.peek().litToBoolean, s"tier0Write.a.ready beat $i")
        port.a.valid.poke(true.B)
        port.a.bits.opcode.poke(TilelinkOpcodes.PutFullData)
        port.a.bits.param.poke(0.U)
        port.a.bits.size.poke(testData.length.U)
        port.a.bits.source.poke(0.U)
        //port.a.bits.address.poke(i.U)  // TilelinkWriteHandler uses address of first beat
        port.a.bits.address.poke(0.U)  // TilelinkWriteHandler uses address of first beat
        port.a.bits.mask.poke(0xFF.U)
        port.a.bits.data.poke(beat.U)
        port.a.bits.corrupt.poke(0.U)
        dut.clock.step()
      }
      port.a.valid.poke(false.B)

      waitFor(dut)(port.d.valid.peek().litToBoolean, "tier0Write AccessAck")
      port.d.bits.opcode.expect(TilelinkOpcodes.AccessAck)
      dut.clock.step()
      port.d.ready.poke(false.B)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 2 – Downward: t3→t2 (DMA[0]) and t2→t1 (DMA[1]) in sequence
      //
      // DMA[0] (producer): A reads t0 - no semaphore, B writes t1 - producer semaphore 
      // DMA[1] (consumer): A reads t1 - consumer semaphore (blocked), B writes t2
      //
      // Consumer DMA[1].A blocks on semaphore 0 (inPort 0, TL addr 4) fullReg=0.
      // After producer DMA[0] completes, test programs sem to unblock prod and consumer.
      // ═══════════════════════════════════════════════════════════════════════

      // Enqueue both DMA instructions through the command queue
      // DMA[0]: read from t0 addr 0, write to t1 addr 0, prod semaphore 
      //enqueueDMAInst(dut, dmaIdx = 0, srcAddr = 0, dstAddr = 0, size = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(0.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(0.U)

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(0.U)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(SEM_0_P.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      var cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[0]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)


      // DMA[1]: read from t1 addr 0 (with sem), write to t2 addr 0
      //enqueueDMAInst(dut, dmaIdx = 1, srcAddr = 0, dstAddr = 0, size = N,
      //  srcSemEn = true, srcSemAddr = SEM_DMA1_A, srcSemStep = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(1.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(0.U)

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(SEM_0_C.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(4.U)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[1]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)

      // Wait for DMA[0] and DMA[1] to complete
      dut.clock.step(200)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 3 – Upward: t2→t1 (DMA[1]) and t1→t0(DMA[0]) in sequence
      //
      // DMA[1] (producer): A writes t1, B reads t2 — prod semaphore
      // DMA[0] (consumer): A writes t0, B reads t1 — cons semaphore
      //
      // After Phase 2: fullReg=0, emptyReg=N — naturally the right initial
      // state (producer acquires emptyReg, consumer blocks on fullReg).
      // ═══════════════════════════════════════════════════════════════════════


      // DMA[1]: read from t2 addr 0, write to t1 addr N 
      //enqueueDMAInst(dut, dmaIdx = 1, srcAddr = 0, dstAddr = 0, size = N,
      //  srcSemEn = true, srcSemAddr = SEM_DMA1_A, srcSemStep = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(1.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(1.U) 
      // We reverse the directionality of the write, data flows from rd to rs
      // B to A  

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(N.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(SEM_0_P.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(4.U)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[1]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)



      // Enqueue both DMA instructions through the command queue
      // DMA[0]: read from t3 addr 0, write to t2 addr 0, no semaphores
      //enqueueDMAInst(dut, dmaIdx = 0, srcAddr = 0, dstAddr = 0, size = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(0.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(1.U)

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(N.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(0.U)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(N.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(SEM_0_C.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[0]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)

      // Wait for DMA[0] and DMA[1] to complete
      dut.clock.step(200)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 4 – Verify: read tier 3 at address 0 to check data survived
      // the downward trip (t3 was not overwritten, data should still be there)
      // ═══════════════════════════════════════════════════════════════════════
      //val readback = readTier3(dut, N, addr = 0)

      val readport = dut.io.tier0Read

      waitFor(dut)(readport.a.ready.peek().litToBoolean, "tier0Read.a.ready")
      readport.a.valid.poke(true.B)
      readport.a.bits.opcode.poke(TilelinkOpcodes.Get)
      readport.a.bits.param.poke(0.U)
      readport.a.bits.size.poke(N.U)
      readport.a.bits.source.poke(0.U)
      readport.a.bits.address.poke(N.U)
      readport.a.bits.mask.poke(0xFF.U)
      readport.a.bits.data.poke(0.U)
      readport.a.bits.corrupt.poke(0.U)
      dut.clock.step()
      readport.a.valid.poke(false.B)

      readport.d.ready.poke(true.B)
      val buf = collection.mutable.ArrayBuffer[BigInt]()
      while (buf.length < N) {
        waitFor(dut)(readport.d.valid.peek().litToBoolean, s"tier0Read D beat ${buf.length}")
        buf += readport.d.bits.data.peek().litValue
        dut.clock.step()
      }
      readport.d.ready.poke(false.B)
      val readback = buf.toSeq



      assert(
        readback == testData,
        s"Verification mismatch:\n" +
        s"  expected: ${testData.map(x => f"0x$x%016x")}\n" +
        s"  got:      ${readback.map(x => f"0x$x%016x")}"
      )
    }
  }

  "Three-tier MemSystem: DMA chain with semsystem and dual sem" in {
    simulate(new MemSystemDUT(msCfg, bankCfg)) { dut =>

      //idlePorts(dut)
      //dut.clock.step(2)

      val testData = Seq[BigInt](
        BigInt("0102030405060708", 16),
        BigInt("090A0B0C0D0E0F10", 16),
        BigInt("1112131415161718", 16),
        BigInt("191A1B1C1D1E1F20", 16)
      )

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 1 – Write test data to tier 0 (tier[0]) via external write port
      // ═══════════════════════════════════════════════════════════════════════

      val port = dut.io.tier0Write
      port.d.ready.poke(true.B)

      dut.io.semInstructionStream.ready.expect(true.B)
      dut.io.semInstructionStream.valid.poke(true.B)
      dut.io.semInstructionStream.bits.payload.semAddr.poke(0.U)
      dut.io.semInstructionStream.bits.payload.initValues(0).poke(0.U)
      dut.io.semInstructionStream.bits.payload.initValues(1).poke(N.U)

      dut.clock.step()

      dut.io.semInstructionStream.ready.expect(true.B)
      dut.io.semInstructionStream.valid.poke(true.B)
      dut.io.semInstructionStream.bits.payload.semAddr.poke(1.U)
      dut.io.semInstructionStream.bits.payload.initValues(0).poke(0.U)
      dut.io.semInstructionStream.bits.payload.initValues(1).poke(N.U)

      dut.clock.step()

      dut.io.semInstructionStream.valid.poke(false.B)


      for ((beat, i) <- testData.zipWithIndex) {
        waitFor(dut)(port.a.ready.peek().litToBoolean, s"tier0Write.a.ready beat $i")
        port.a.valid.poke(true.B)
        port.a.bits.opcode.poke(TilelinkOpcodes.PutFullData)
        port.a.bits.param.poke(0.U)
        port.a.bits.size.poke(testData.length.U)
        port.a.bits.source.poke(0.U)
        //port.a.bits.address.poke(i.U)  // TilelinkWriteHandler uses address of first beat
        port.a.bits.address.poke(0.U)  // TilelinkWriteHandler uses address of first beat
        port.a.bits.mask.poke(0xFF.U)
        port.a.bits.data.poke(beat.U)
        port.a.bits.corrupt.poke(0.U)
        dut.clock.step()
      }
      port.a.valid.poke(false.B)

      waitFor(dut)(port.d.valid.peek().litToBoolean, "tier0Write AccessAck")
      port.d.bits.opcode.expect(TilelinkOpcodes.AccessAck)
      dut.clock.step()
      port.d.ready.poke(false.B)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 2 – Downward: t3→t2 (DMA[0]) and t2→t1 (DMA[1]) in sequence
      //
      // DMA[0] (producer): A reads t0 - no semaphore, B writes t1 - producer semaphore 
      // DMA[1] (consumer): A reads t1 - consumer semaphore (blocked), B writes t2
      //
      // Consumer DMA[1].A blocks on semaphore 0 (inPort 0, TL addr 4) fullReg=0.
      // After producer DMA[0] completes, test programs sem to unblock prod and consumer.
      // ═══════════════════════════════════════════════════════════════════════

      // Enqueue both DMA instructions through the command queue
      // DMA[0]: read from t0 addr 0, write to t1 addr 0, prod semaphore 
      //enqueueDMAInst(dut, dmaIdx = 0, srcAddr = 0, dstAddr = 0, size = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(0.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(0.U)

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(0.U)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(SEM_0_P.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      var cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[0]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)


      // DMA[1]: read from t1 addr 0 (with sem), write to t2 addr 0
      //enqueueDMAInst(dut, dmaIdx = 1, srcAddr = 0, dstAddr = 0, size = N,
      //  srcSemEn = true, srcSemAddr = SEM_DMA1_A, srcSemStep = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(1.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(0.U)

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(SEM_0_C.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(4.U)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[1]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)

      // Wait for DMA[0] and DMA[1] to complete
      dut.clock.step(200)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 3 – Upward: t2→t1 (DMA[1]) and t1→t0(DMA[0]) in sequence
      //
      // DMA[1] (producer): A writes t1, B reads t2 — prod semaphore
      // DMA[0] (consumer): A writes t0, B reads t1 — cons semaphore
      //
      // After Phase 2: fullReg=0, emptyReg=N — naturally the right initial
      // state (producer acquires emptyReg, consumer blocks on fullReg).
      // ═══════════════════════════════════════════════════════════════════════


      // DMA[1]: read from t2 addr 0, write to t1 addr N 
      //enqueueDMAInst(dut, dmaIdx = 1, srcAddr = 0, dstAddr = 0, size = N,
      //  srcSemEn = true, srcSemAddr = SEM_DMA1_A, srcSemStep = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(1.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(1.U) 
      // We reverse the directionality of the write, data flows from rd to rs
      // B to A  

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(N.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(SEM_1_P.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(0.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(4.U)
      //dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[1]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)



      // Enqueue both DMA instructions through the command queue
      // DMA[0]: read from t3 addr 0, write to t2 addr 0, no semaphores
      //enqueueDMAInst(dut, dmaIdx = 0, srcAddr = 0, dstAddr = 0, size = N)

      dut.io.dmaInstructionStream.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.DMAAddr.poke(0.U)
      dut.io.dmaInstructionStream.bits.size.poke(N.U)
      dut.io.dmaInstructionStream.bits.func.poke(1.U)

      dut.io.dmaInstructionStream.bits.addrs(0).addr.poke(N.U)
      dut.io.dmaInstructionStream.bits.addrs(0).sem.valid.poke(false.B)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.addr.poke(0.U)
      //dut.io.dmaInstructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(N.U)

      dut.io.dmaInstructionStream.bits.addrd(0).addr.poke(N.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.valid.poke(true.B)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.addr.poke(SEM_1_C.U)
      dut.io.dmaInstructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(N.U)

      cycles = 0
      while (!dut.io.dmaInstructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < maxCycles, s"Timeout enqueuing DMA instruction for DMA[0]")
      }
      dut.clock.step()
      dut.io.dmaInstructionStream.valid.poke(false.B)

      // Wait for DMA[0] and DMA[1] to complete
      dut.clock.step(200)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 4 – Verify: read tier 3 at address 0 to check data survived
      // the downward trip (t3 was not overwritten, data should still be there)
      // ═══════════════════════════════════════════════════════════════════════
      //val readback = readTier3(dut, N, addr = 0)

      val readport = dut.io.tier0Read

      waitFor(dut)(readport.a.ready.peek().litToBoolean, "tier0Read.a.ready")
      readport.a.valid.poke(true.B)
      readport.a.bits.opcode.poke(TilelinkOpcodes.Get)
      readport.a.bits.param.poke(0.U)
      readport.a.bits.size.poke(N.U)
      readport.a.bits.source.poke(0.U)
      readport.a.bits.address.poke(N.U)
      readport.a.bits.mask.poke(0xFF.U)
      readport.a.bits.data.poke(0.U)
      readport.a.bits.corrupt.poke(0.U)
      dut.clock.step()
      readport.a.valid.poke(false.B)

      readport.d.ready.poke(true.B)
      val buf = collection.mutable.ArrayBuffer[BigInt]()
      while (buf.length < N) {
        waitFor(dut)(readport.d.valid.peek().litToBoolean, s"tier0Read D beat ${buf.length}")
        buf += readport.d.bits.data.peek().litValue
        dut.clock.step()
      }
      readport.d.ready.poke(false.B)
      val readback = buf.toSeq



      assert(
        readback == testData,
        s"Verification mismatch:\n" +
        s"  expected: ${testData.map(x => f"0x$x%016x")}\n" +
        s"  got:      ${readback.map(x => f"0x$x%016x")}"
      )
    }
  }

  "HostIn demux: write and read back distinct data on each tier" in {
    simulate(new HostInDUT(msCfg, bankCfg)) { dut =>

      dut.clock.step(2)

      // Distinct 4-beat payloads per tier
      val tier0Data = Seq(BigInt("AA", 16), BigInt("BB", 16), BigInt("CC", 16), BigInt("DD", 16))
      val tier1Data = Seq(BigInt("11", 16), BigInt("22", 16), BigInt("33", 16), BigInt("44", 16))
      val tier2Data = Seq(BigInt("55", 16), BigInt("66", 16), BigInt("77", 16), BigInt("88", 16))

      // Tier bases for this config: each tier is 1024 words (4 banks * 256)
      val base0 = msCfg.tierBases(0).toInt  // 0x000
      val base1 = msCfg.tierBases(1).toInt  // 0x400
      val base2 = msCfg.tierBases(2).toInt  // 0x800

      val hostIn = dut.io.hostIn
      hostIn.d.ready.poke(true.B)

      // ── Helper: burst-write N beats via hostIn at a global address ────────
      // Drive valid+bits first, then wait for ready (TLSplitter only asserts
      // ready once it sees a valid request with a known opcode).
      def hostWrite(baseAddr: Int, data: Seq[BigInt]): Unit = {
        for ((beat, i) <- data.zipWithIndex) {
          hostIn.a.valid.poke(true.B)
          hostIn.a.bits.opcode.poke(TilelinkOpcodes.PutFullData)
          hostIn.a.bits.param.poke(0.U)
          hostIn.a.bits.size.poke(data.length.U)
          hostIn.a.bits.source.poke(0.U)
          hostIn.a.bits.address.poke(baseAddr.U)
          hostIn.a.bits.mask.poke(0xFF.U)
          hostIn.a.bits.data.poke(beat.U)
          hostIn.a.bits.corrupt.poke(0.U)
          waitFor(dut)(hostIn.a.ready.peek().litToBoolean, s"hostIn.a.ready write beat $i @ 0x${baseAddr.toHexString}")
          dut.clock.step()
        }
        hostIn.a.valid.poke(false.B)

        waitFor(dut)(hostIn.d.valid.peek().litToBoolean, s"hostIn AccessAck @ 0x${baseAddr.toHexString}")
        hostIn.d.bits.opcode.expect(TilelinkOpcodes.AccessAck)
        dut.clock.step()
      }

      // ── Helper: burst-read N beats via hostIn from a global address ───────
      def hostRead(baseAddr: Int, n: Int): Seq[BigInt] = {
        hostIn.a.valid.poke(true.B)
        hostIn.a.bits.opcode.poke(TilelinkOpcodes.Get)
        hostIn.a.bits.param.poke(0.U)
        hostIn.a.bits.size.poke(n.U)
        hostIn.a.bits.source.poke(0.U)
        hostIn.a.bits.address.poke(baseAddr.U)
        hostIn.a.bits.mask.poke(0xFF.U)
        hostIn.a.bits.data.poke(0.U)
        hostIn.a.bits.corrupt.poke(0.U)
        waitFor(dut)(hostIn.a.ready.peek().litToBoolean, s"hostIn.a.ready read @ 0x${baseAddr.toHexString}")
        dut.clock.step()
        hostIn.a.valid.poke(false.B)

        val buf = collection.mutable.ArrayBuffer[BigInt]()
        while (buf.length < n) {
          waitFor(dut)(hostIn.d.valid.peek().litToBoolean, s"hostIn D beat ${buf.length} @ 0x${baseAddr.toHexString}")
          buf += hostIn.d.bits.data.peek().litValue
          dut.clock.step()
        }
        buf.toSeq
      }

      // ═════════════════════════════════════════════════════════════════════
      // Phase 1 – Write distinct data to each tier via hostIn
      // ═════════════════════════════════════════════════════════════════════
      hostWrite(base0, tier0Data)
      hostWrite(base1, tier1Data)
      hostWrite(base2, tier2Data)

      // ═════════════════════════════════════════════════════════════════════
      // Phase 2 – Read back from each tier via hostIn and verify
      // ═════════════════════════════════════════════════════════════════════
      val read0 = hostRead(base0, N)
      val read1 = hostRead(base1, N)
      val read2 = hostRead(base2, N)

      assert(read0 == tier0Data,
        s"Tier 0 mismatch: expected ${tier0Data.map(x => f"0x$x%x")} got ${read0.map(x => f"0x$x%x")}")
      assert(read1 == tier1Data,
        s"Tier 1 mismatch: expected ${tier1Data.map(x => f"0x$x%x")} got ${read1.map(x => f"0x$x%x")}")
      assert(read2 == tier2Data,
        s"Tier 2 mismatch: expected ${tier2Data.map(x => f"0x$x%x")} got ${read2.map(x => f"0x$x%x")}")
    }
  }
}
