package ATA8

import chisel3._
import chisel3.util._

/**
 * Multi-tier memory system.
 *
 * Topology (example, 3 tiers):
 *
 *   External access
 *        ↕  (tier0.writePorts / tier0.readPorts)
 *   [ Tier 0 – small/fast L1 ]
 *        ↕  portA          portB ↕
 *              [ MemDMA 0 ]
 *        ↕  portB          portA ↕
 *   [ Tier 1 – medium L2 ]
 *        ↕  portA          portB ↕
 *              [ MemDMA 1 ]
 *        ↕  portB          portA ↕
 *   [ Tier 2 – large/slow L3 ]
 *
 * Only Tier 0's external write/read ports are exposed in [[io]].
 * DMA interfaces for software-initiated transfers are exposed as
 * [[io.dmaInterfaces]].
 *
 * Integration with the wider accelerator is deferred; this module is
 * self-contained and configured entirely via [[MemSystemConfig]].
 */
class MemSystem(implicit mc: MemSystemConfig) extends Module {

  private val nTiers = mc.tiers.length
  private val nDMAs  = nTiers - 1

  // Number of DMA ports each tier needs:
  //   tier 0        → 1 (portA side of DMA 0)
  //   tier 1..N-2   → 2 (portB of DMA i-1, portA of DMA i)
  //   tier N-1      → 1 (portB side of DMA N-2)
  //   single tier   → 0
  private def tierDMACnt(i: Int): Int =
    if (nTiers == 1) 0
    else if (i == 0 || i == nTiers - 1) 1
    else 2

  val io = IO(new Bundle {
    // External access always goes through the hottest (tier 0) scratchpad
    val tier0WritePorts = Vec(mc.tiers(0).nWritePorts, Flipped(new TilelinkPort))
    val tier0ReadPorts  = Vec(mc.tiers(0).nReadPorts,  Flipped(new TilelinkPort))
    // Software-initiated DMA descriptors (one per inter-tier link)
    val dmaInterfaces = Vec(nDMAs, Flipped(new dmaInterface(2)))
    // Per-pipeline semaphore ports — connect to a SemaphoreBank externally.
    // semaphoreA(i) belongs to pipeline A of DMA i (portA side / upper tier).
    // semaphoreB(i) belongs to pipeline B of DMA i (portB side / lower tier).
    val semaphoreA = Vec(nDMAs, new TilelinkPort)
    val semaphoreB = Vec(nDMAs, new TilelinkPort)
  })

  // ── Instantiate tiers ─────────────────────────────────────────────────────
  val tiers = mc.tiers.zipWithIndex.map { case (t, i) =>
    Module(new MemTier(t, tierDMACnt(i)))
  }

  // ── Instantiate DMAs (one per adjacent tier pair) ─────────────────────────
  val dmas = Seq.fill(nDMAs)(Module(new MemDMA))

  // ── Wire tier-0 external ports ────────────────────────────────────────────
  tiers(0).io.writePorts <> io.tier0WritePorts
  tiers(0).io.readPorts  <> io.tier0ReadPorts

  // ── Wire DMA interfaces and inter-tier connections ────────────────────────
  // DMA[i] connects:
  //   portA  →  tier[i].dmaPorts(0)          for i == 0
  //             tier[i].dmaPorts(1)          for i > 0  (slot 0 is taken by the previous DMA's portB)
  //   portB  →  tier[i+1].dmaPorts(0)        always (first DMA slot of the lower tier)
  for (i <- 0 until nDMAs) {
    dmas(i).io.interface  <> io.dmaInterfaces(i)
    dmas(i).io.semaphoreA <> io.semaphoreA(i)
    dmas(i).io.semaphoreB <> io.semaphoreB(i)

    val portASlot = if (i == 0) 0 else 1
    dmas(i).io.portA <> tiers(i).io.dmaPorts(portASlot)
    dmas(i).io.portB <> tiers(i + 1).io.dmaPorts(0)
  }
}

object MemSystem extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new MemSystem()(MemSystemConfig.default()),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
