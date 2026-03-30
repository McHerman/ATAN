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

  val memSys  = Module(new MemSystem()(msCfg))
  val semBank = Module(new SemaphoreBank(nDMAs * 2)(bankCfg))

  // DMA[i].semaphoreA → inPort(2*i),  DMA[i].semaphoreB → inPort(2*i+1)
  for (i <- 0 until nDMAs) {
    semBank.io.inPorts(i * 2)     <> memSys.io.semaphoreA(i)
    semBank.io.inPorts(i * 2 + 1) <> memSys.io.semaphoreB(i)
  }

  val io = IO(new Bundle {
    val tier3Write  = Flipped(new TilelinkPort()(msCfg))
    val tier3Read   = Flipped(new TilelinkPort()(msCfg))
    val dma         = Vec(nDMAs, Flipped(new dmaInterface(2)(msCfg)))
    val semProgPort = Flipped(Decoupled(new SemaphoreProgPort))
  })

  io.tier3Write  <> memSys.io.tier3WritePorts(0)
  io.tier3Read   <> memSys.io.tier3ReadPorts(0)
  io.dma         <> memSys.io.dmaInterfaces
  io.semProgPort <> semBank.io.progPort
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
  val bankCfg: Configuration = Configuration.default().copy(sourceWidth = 8)

  // Semaphore TL addresses for each DMA pipeline (fullReg, bit-0=0):
  //   SEM_DMA0_A is sent by inPort 0 → slave 0  → semaphore 0, port 0
  //   SEM_DMA0_B is sent by inPort 1 → slave 2  → semaphore 1, port 0
  //   SEM_DMA1_A is sent by inPort 2 → slave 4  → semaphore 2, port 0
  //   SEM_DMA1_B is sent by inPort 3 → slave 6  → semaphore 3, port 0
  val SEM_DMA0_A = 0
  val SEM_DMA0_B = 4
  val SEM_DMA1_A = 8
  val SEM_DMA1_B = 12

  val N         = 4     // beats per transfer
  val maxCycles = 3000  // generous timeout

  // ── Helpers ────────────────────────────────────────────────────────────────

  def waitFor(dut: ThreeTierDUT)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout ($maxCycles cycles) waiting for: $msg")
      dut.clock.step()
      cycles += 1
    }
  }

  /** Program semaphore semIdx via the SemaphoreBank progPort. */
  def programSemaphore(dut: ThreeTierDUT, semIdx: Int, full: Int, empty: Int): Unit = {
    dut.io.semProgPort.valid.poke(true.B)
    dut.io.semProgPort.bits.addr.poke((semIdx * 2).U) // maskedAddr = addr >> 1 = semIdx
    dut.io.semProgPort.bits.initValues(0).poke(full.U)
    dut.io.semProgPort.bits.initValues(1).poke(empty.U)
    dut.clock.step()
    dut.io.semProgPort.valid.poke(false.B)
  }

  /** Drive all DUT inputs to a safe idle state. */
  def idlePorts(dut: ThreeTierDUT): Unit = {
    dut.io.semProgPort.valid.poke(false.B)
    dut.io.semProgPort.bits.addr.poke(0.U)
    dut.io.semProgPort.bits.initValues(0).poke(0.U)
    dut.io.semProgPort.bits.initValues(1).poke(0.U)

    for (i <- 0 until 2) {
      dut.io.dma(i).descriptor.valid.poke(false.B)
      dut.io.dma(i).response.ready.poke(false.B)
      for (j <- 0 until 2) {
        dut.io.dma(i).descriptor.bits(j).addr.poke(0.U)
        dut.io.dma(i).descriptor.bits(j).size.poke(0.U)
        dut.io.dma(i).descriptor.bits(j).writeEn.poke(false.B)
        dut.io.dma(i).descriptor.bits(j).source.poke(0.U)
        dut.io.dma(i).descriptor.bits(j).sink.poke(0.U)
        dut.io.dma(i).descriptor.bits(j).semaphore.get.semEnable.poke(false.B)
        dut.io.dma(i).descriptor.bits(j).semaphore.get.semAddr.poke(0.U)
        dut.io.dma(i).descriptor.bits(j).semaphore.get.semStepSize.poke(0.U)
        dut.io.dma(i).descriptor.bits(j).semaphore.get.mode.poke(0.U)
      }
    }

    dut.io.tier3Write.a.valid.poke(false.B)
    dut.io.tier3Write.a.bits.opcode.poke(0.U)
    dut.io.tier3Write.a.bits.param.poke(0.U)
    dut.io.tier3Write.a.bits.size.poke(0.U)
    dut.io.tier3Write.a.bits.source.poke(0.U)
    dut.io.tier3Write.a.bits.address.poke(0.U)
    dut.io.tier3Write.a.bits.mask.poke(0.U)
    dut.io.tier3Write.a.bits.data.poke(0.U)
    dut.io.tier3Write.a.bits.corrupt.poke(0.U)
    dut.io.tier3Write.d.ready.poke(false.B)

    dut.io.tier3Read.a.valid.poke(false.B)
    dut.io.tier3Read.a.bits.opcode.poke(0.U)
    dut.io.tier3Read.a.bits.param.poke(0.U)
    dut.io.tier3Read.a.bits.size.poke(0.U)
    dut.io.tier3Read.a.bits.source.poke(0.U)
    dut.io.tier3Read.a.bits.address.poke(0.U)
    dut.io.tier3Read.a.bits.mask.poke(0.U)
    dut.io.tier3Read.a.bits.data.poke(0.U)
    dut.io.tier3Read.a.bits.corrupt.poke(0.U)
    dut.io.tier3Read.d.ready.poke(false.B)
  }

  /**
   * Write `data.length` beats to tier[0] starting at address 0 via the
   * TilelinkWriteHandler (PutFullData burst, single AccessAck at the end).
   */
  def writeTier3(dut: ThreeTierDUT, data: Seq[BigInt]): Unit = {
    val port = dut.io.tier3Write
    port.d.ready.poke(true.B)

    for ((beat, i) <- data.zipWithIndex) {
      waitFor(dut)(port.a.ready.peek().litToBoolean, s"tier3Write.a.ready beat $i")
      port.a.valid.poke(true.B)
      port.a.bits.opcode.poke(TilelinkOpcodes.PutFullData)
      port.a.bits.param.poke(0.U)
      port.a.bits.size.poke(data.length.U)
      port.a.bits.source.poke(0.U)
      //port.a.bits.address.poke(i.U)  // TilelinkWriteHandler uses address of first beat
      port.a.bits.address.poke(0.U)  // TilelinkWriteHandler uses address of first beat
      port.a.bits.mask.poke(0xFF.U)
      port.a.bits.data.poke(beat.U)
      port.a.bits.corrupt.poke(0.U)
      dut.clock.step()
    }
    port.a.valid.poke(false.B)

    waitFor(dut)(port.d.valid.peek().litToBoolean, "tier3Write AccessAck")
    port.d.bits.opcode.expect(TilelinkOpcodes.AccessAck)
    dut.clock.step()
    port.d.ready.poke(false.B)
  }

  /**
   * Issue a single Get to tier[0] (address 0, size n) and collect all
   * AccessAckData beats.  Returns beats in order.
   */
  def readTier3(dut: ThreeTierDUT, n: Int, addr: Int = 0): Seq[BigInt] = {
    val port = dut.io.tier3Read

    waitFor(dut)(port.a.ready.peek().litToBoolean, "tier3Read.a.ready")
    port.a.valid.poke(true.B)
    port.a.bits.opcode.poke(TilelinkOpcodes.Get)
    port.a.bits.param.poke(0.U)
    port.a.bits.size.poke(n.U)
    port.a.bits.source.poke(0.U)
    port.a.bits.address.poke(addr.U)
    port.a.bits.mask.poke(0xFF.U)
    port.a.bits.data.poke(0.U)
    port.a.bits.corrupt.poke(0.U)
    dut.clock.step()
    port.a.valid.poke(false.B)

    port.d.ready.poke(true.B)
    val buf = collection.mutable.ArrayBuffer[BigInt]()
    while (buf.length < n) {
      waitFor(dut)(port.d.valid.peek().litToBoolean, s"tier3Read D beat ${buf.length}")
      buf += port.d.bits.data.peek().litValue
      dut.clock.step()
    }
    port.d.ready.poke(false.B)
    buf.toSeq
  }

  /**
   * Submit one descriptor pair to dmaInterfaces(dmaIdx) and block until
   * the DMA issues its response.
   *
   * Descriptor layout (MemDMA):
   *   bits(0) → pipeline A (portA = upper tier)
   *   bits(1) → pipeline B (portB = lower tier)
   *
   * For a downward transfer (upper→lower):  writeEn0=false (A reads), writeEn1=true  (B writes)
   * For an upward   transfer (lower→upper):  writeEn0=true  (A writes), writeEn1=false (B reads)
   */
  def runDMA(
    dut: ThreeTierDUT, dmaIdx: Int,
    addr0: Int, size0: Int, write0: Boolean,
    semEn0: Boolean, semAddr0: Int, semStep0: Int,
    addr1: Int, size1: Int, write1: Boolean,
    semEn1: Boolean, semAddr1: Int, semStep1: Int
  ): Unit = {
    val ifc = dut.io.dma(dmaIdx)

    waitFor(dut)(ifc.descriptor.ready.peek().litToBoolean, s"DMA[$dmaIdx] descriptor.ready")

    ifc.descriptor.valid.poke(true.B)

    ifc.descriptor.bits(0).addr.poke(addr0.U)
    ifc.descriptor.bits(0).size.poke(size0.U)
    ifc.descriptor.bits(0).writeEn.poke(write0.B)
    ifc.descriptor.bits(0).source.poke(0.U)
    ifc.descriptor.bits(0).sink.poke(0.U)
    ifc.descriptor.bits(0).semaphore.get.semEnable.poke(semEn0.B)
    ifc.descriptor.bits(0).semaphore.get.semAddr.poke(semAddr0.U)
    ifc.descriptor.bits(0).semaphore.get.semStepSize.poke(semStep0.U)
    ifc.descriptor.bits(0).semaphore.get.mode.poke(SemaphoreAccessModes.RestartOnStep)

    ifc.descriptor.bits(1).addr.poke(addr1.U)
    ifc.descriptor.bits(1).size.poke(size1.U)
    ifc.descriptor.bits(1).writeEn.poke(write1.B)
    ifc.descriptor.bits(1).source.poke(0.U)
    ifc.descriptor.bits(1).sink.poke(0.U)
    ifc.descriptor.bits(1).semaphore.get.semEnable.poke(semEn1.B)
    ifc.descriptor.bits(1).semaphore.get.semAddr.poke(semAddr1.U)
    ifc.descriptor.bits(1).semaphore.get.semStepSize.poke(semStep1.U)
    ifc.descriptor.bits(1).semaphore.get.mode.poke(SemaphoreAccessModes.RestartOnStep)

    dut.clock.step()
    ifc.descriptor.valid.poke(false.B)

    ifc.response.ready.poke(true.B)
    waitFor(dut)(ifc.response.valid.peek().litToBoolean, s"DMA[$dmaIdx] response")
    dut.clock.step()
    ifc.response.ready.poke(false.B)
  }

  // ── Test ───────────────────────────────────────────────────────────────────

  "Three-tier MemSystem: DMA chain with semaphore-synchronized transfers" in {
    simulate(new ThreeTierDUT(msCfg, bankCfg)) { dut =>

      idlePorts(dut)
      dut.clock.step(2)

      val testData = Seq[BigInt](
        BigInt("0102030405060708", 16),
        BigInt("090A0B0C0D0E0F10", 16),
        BigInt("1112131415161718", 16),
        BigInt("191A1B1C1D1E1F20", 16)
      )

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 1 – Write test data to tier 1 (tier[0]) via external write port
      // ═══════════════════════════════════════════════════════════════════════
      writeTier3(dut, testData)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 2 – Downward: t3→t2 (DMA[0]) and t2→t1 (DMA[1]) in parallel
      //
      // DMA[0] (producer): A reads t3, B writes t2 — no semaphore
      // DMA[1] (consumer): A reads t2 with semaphore (blocked), B writes t1 — no sem
      //
      // Consumer DMA[1].A blocks on semaphore 2 (inPort 2, TL addr 8) fullReg=0.
      // After producer DMA[0] completes, test programs sem to unblock consumer.
      // ═══════════════════════════════════════════════════════════════════════

      // Init semaphore 2 (DMA[1].semA) to fullReg=0 → consumer blocks
      programSemaphore(dut, semIdx = 2, full = 0, empty = 0)

      // Issue both DMAs in the same cycle
      dut.io.dma(0).descriptor.valid.poke(true.B)
      dut.io.dma(0).descriptor.bits(0).addr.poke(0.U)
      dut.io.dma(0).descriptor.bits(0).size.poke(N.U)
      dut.io.dma(0).descriptor.bits(0).writeEn.poke(false.B)  // A reads t3
      dut.io.dma(0).descriptor.bits(0).source.poke(0.U)
      dut.io.dma(0).descriptor.bits(0).sink.poke(0.U)
      dut.io.dma(0).descriptor.bits(0).semaphore.get.semEnable.poke(false.B)
      dut.io.dma(0).descriptor.bits(1).addr.poke(0.U)
      dut.io.dma(0).descriptor.bits(1).size.poke(N.U)
      dut.io.dma(0).descriptor.bits(1).writeEn.poke(true.B)   // B writes t2
      dut.io.dma(0).descriptor.bits(1).source.poke(0.U)
      dut.io.dma(0).descriptor.bits(1).sink.poke(0.U)
      dut.io.dma(0).descriptor.bits(1).semaphore.get.semEnable.poke(false.B)

      dut.io.dma(1).descriptor.valid.poke(true.B)
      dut.io.dma(1).descriptor.bits(0).addr.poke(0.U)
      dut.io.dma(1).descriptor.bits(0).size.poke(N.U)
      dut.io.dma(1).descriptor.bits(0).writeEn.poke(false.B)  // A reads t2
      dut.io.dma(1).descriptor.bits(0).source.poke(0.U)
      dut.io.dma(1).descriptor.bits(0).sink.poke(0.U)
      dut.io.dma(1).descriptor.bits(0).semaphore.get.semEnable.poke(true.B)
      dut.io.dma(1).descriptor.bits(0).semaphore.get.semAddr.poke(SEM_DMA1_A.U)
      dut.io.dma(1).descriptor.bits(0).semaphore.get.semStepSize.poke(N.U)
      dut.io.dma(1).descriptor.bits(0).semaphore.get.mode.poke(SemaphoreAccessModes.RestartOnStep)
      dut.io.dma(1).descriptor.bits(1).addr.poke(0.U)
      dut.io.dma(1).descriptor.bits(1).size.poke(N.U)
      dut.io.dma(1).descriptor.bits(1).writeEn.poke(true.B)   // B writes t1
      dut.io.dma(1).descriptor.bits(1).source.poke(0.U)
      dut.io.dma(1).descriptor.bits(1).sink.poke(0.U)
      dut.io.dma(1).descriptor.bits(1).semaphore.get.semEnable.poke(false.B)

      dut.clock.step()
      dut.io.dma(0).descriptor.valid.poke(false.B)
      dut.io.dma(1).descriptor.valid.poke(false.B)

      // Wait for producer DMA[0] to finish
      dut.io.dma(0).response.ready.poke(true.B)
      waitFor(dut)(dut.io.dma(0).response.valid.peek().litToBoolean, "DMA[0] down response")
      dut.clock.step()
      dut.io.dma(0).response.ready.poke(false.B)

      // Unblock consumer: program sem 2 fullReg = N
      programSemaphore(dut, semIdx = 2, full = N, empty = 0)

      // Wait for consumer DMA[1] to finish
      dut.io.dma(1).response.ready.poke(true.B)
      waitFor(dut)(dut.io.dma(1).response.valid.peek().litToBoolean, "DMA[1] down response")
      dut.clock.step()
      dut.io.dma(1).response.ready.poke(false.B)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 3 – Upward: t1→t2 (DMA[1]) and t2→t3 (DMA[0]) in parallel
      //
      // DMA[1] (producer): A writes t2, B reads t1 — no semaphore
      // DMA[0] (consumer): A writes t3, B reads t2 with semaphore (blocked)
      //
      // Consumer DMA[0].B blocks on semaphore 1 (inPort 1, TL addr 4) fullReg=0.
      // After producer DMA[1] completes, test programs sem to unblock consumer.
      // ═══════════════════════════════════════════════════════════════════════

      // Init semaphore 1 (DMA[0].semB) to fullReg=0 → consumer blocks
      programSemaphore(dut, semIdx = 1, full = 0, empty = 0)

      // Issue both DMAs in the same cycle
      dut.io.dma(1).descriptor.valid.poke(true.B)
      dut.io.dma(1).descriptor.bits(0).addr.poke(0.U)
      dut.io.dma(1).descriptor.bits(0).size.poke(N.U)
      dut.io.dma(1).descriptor.bits(0).writeEn.poke(true.B)   // A writes t2
      dut.io.dma(1).descriptor.bits(0).source.poke(0.U)
      dut.io.dma(1).descriptor.bits(0).sink.poke(0.U)
      dut.io.dma(1).descriptor.bits(0).semaphore.get.semEnable.poke(false.B)
      dut.io.dma(1).descriptor.bits(1).addr.poke(0.U)
      dut.io.dma(1).descriptor.bits(1).size.poke(N.U)
      dut.io.dma(1).descriptor.bits(1).writeEn.poke(false.B)  // B reads t1
      dut.io.dma(1).descriptor.bits(1).source.poke(0.U)
      dut.io.dma(1).descriptor.bits(1).sink.poke(0.U)
      dut.io.dma(1).descriptor.bits(1).semaphore.get.semEnable.poke(false.B)

      dut.io.dma(0).descriptor.valid.poke(true.B)
      dut.io.dma(0).descriptor.bits(0).addr.poke(128.U)
      dut.io.dma(0).descriptor.bits(0).size.poke(N.U)
      dut.io.dma(0).descriptor.bits(0).writeEn.poke(true.B)   // A writes t3
      dut.io.dma(0).descriptor.bits(0).source.poke(0.U)
      dut.io.dma(0).descriptor.bits(0).sink.poke(0.U)
      dut.io.dma(0).descriptor.bits(0).semaphore.get.semEnable.poke(false.B)
      dut.io.dma(0).descriptor.bits(1).addr.poke(0.U)
      dut.io.dma(0).descriptor.bits(1).size.poke(N.U)
      dut.io.dma(0).descriptor.bits(1).writeEn.poke(false.B)  // B reads t2
      dut.io.dma(0).descriptor.bits(1).source.poke(0.U)
      dut.io.dma(0).descriptor.bits(1).sink.poke(0.U)
      dut.io.dma(0).descriptor.bits(1).semaphore.get.semEnable.poke(true.B)
      dut.io.dma(0).descriptor.bits(1).semaphore.get.semAddr.poke(SEM_DMA0_B.U)
      dut.io.dma(0).descriptor.bits(1).semaphore.get.semStepSize.poke(N.U)
      dut.io.dma(0).descriptor.bits(1).semaphore.get.mode.poke(SemaphoreAccessModes.RestartOnStep)

      dut.clock.step()
      dut.io.dma(0).descriptor.valid.poke(false.B)
      dut.io.dma(1).descriptor.valid.poke(false.B)

      // Wait for producer DMA[1] to finish
      dut.io.dma(1).response.ready.poke(true.B)
      waitFor(dut)(dut.io.dma(1).response.valid.peek().litToBoolean, "DMA[1] up response")
      dut.clock.step()
      dut.io.dma(1).response.ready.poke(false.B)

      // Unblock consumer: program sem 1 fullReg = N
      programSemaphore(dut, semIdx = 1, full = N, empty = 0)

      // Wait for consumer DMA[0] to finish
      dut.io.dma(0).response.ready.poke(true.B)
      waitFor(dut)(dut.io.dma(0).response.valid.peek().litToBoolean, "DMA[0] up response")
      dut.clock.step()
      dut.io.dma(0).response.ready.poke(false.B)

      // ═══════════════════════════════════════════════════════════════════════
      // Phase 4 – Verify: read tier 3 at address 128 and check data integrity
      // ═══════════════════════════════════════════════════════════════════════
      val readback = readTier3(dut, N, addr = 128)

      assert(
        readback == testData,
        s"Verification after round-trip mismatch:\n" +
        s"  expected: ${testData.map(x => f"0x$x%016x")}\n" +
        s"  got:      ${readback.map(x => f"0x$x%016x")}"
      )

    }
  }
}
