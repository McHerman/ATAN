package ATA8

import chisel3._
import chisel3.util._

class MemSystem(ctrlCfg: Configuration)(implicit mc: MemSystemConfig) extends Module {

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
    val tier0WritePorts = Vec(mc.tiers(0).nWritePorts, Flipped(new TilelinkPort))
    val tier0ReadPorts  = Vec(mc.tiers(0).nReadPorts,  Flipped(new TilelinkPort))

    val dmaInstructionStream = Flipped(Decoupled(new DMAInst()(ctrlCfg)))

    val semaphoreA = Vec(nDMAs, new TilelinkPort)
    val semaphoreB = Vec(nDMAs, new TilelinkPort)

    val hostIn = Flipped(new TilelinkPort)
  })

  // ── Instantiate tiers ─────────────────────────────────────────────────────
  val tiers = mc.tiers.zipWithIndex.map { case (t, i) =>
    Module(new MemTier(t, tierDMACnt(i)))
  }

  // ── Instantiate DMAs (one per adjacent tier pair) ─────────────────────────
  val dmas     = Seq.fill(nDMAs)(Module(new MemDMA))
  val dmaQueue = Module(new DMAQueue(nDMAs)(ctrlCfg))

  dmaQueue.io.instructionStream <> io.dmaInstructionStream

  // ── Wire tier-0 external ports ────────────────────────────────────────────
  tiers(0).io.writePorts <> io.tier0WritePorts
  tiers(0).io.readPorts  <> io.tier0ReadPorts
  tiers(0).io.hostIn     <> io.hostIn

  // ── Terminate external ports on non-tier-0 tiers ─────────────────────────
  // Only tier 0 has external access; tiers 1..N-1 have no software-visible
  // write/read ports, so we drive their master-side signals to safe defaults.

  for (i <- 1 until nTiers) {
    tiers(i).io.writePorts.foreach { port =>
      port.a.valid := false.B
      port.a.bits  := DontCare
      port.d.ready := false.B
    }
    tiers(i).io.readPorts.foreach { port =>
      port.a.valid := false.B
      port.a.bits  := DontCare
      port.d.ready := false.B
    }
    tiers(i).io.hostIn.a.valid := false.B
    tiers(i).io.hostIn.a.bits  := DontCare
    tiers(i).io.hostIn.d.ready := false.B
  }

  // ── Wire DMA interfaces and inter-tier connections ────────────────────────
  // DMA[i] connects:
  //   portA  →  tier[i].dmaPorts(0)          for i == 0
  //             tier[i].dmaPorts(1)          for i > 0  (slot 0 is taken by the previous DMA's portB)
  //   portB  →  tier[i+1].dmaPorts(0)        always (first DMA slot of the lower tier)
  for (i <- 0 until nDMAs) {
    dmaQueue.io.dmaInterfaces(i) <> dmas(i).io.interface
    dmas(i).io.semaphoreA <> io.semaphoreA(i)
    dmas(i).io.semaphoreB <> io.semaphoreB(i)

    val portASlot = if (i == 0) 0 else 1
    dmas(i).io.portA <> tiers(i).io.dmaPorts(portASlot)
    dmas(i).io.portB <> tiers(i + 1).io.dmaPorts(0)
  }
}
