package ATA8

import chisel3.util.log2Ceil

/** Per-tier scratchpad sizing. */
case class TierConfig(
  nWritePorts: Int,   // external write ports (from compute units / load)
  nReadPorts:  Int,   // external read ports (to compute units / store)
  nBanks:      Int,   // number of SRAM banks inside this tier
  bankDepth:   Int    // words per bank
)

/**
 * Self-contained configuration for the multi-tier memory system.
 * Decoupled from the main [[Configuration]] so the MemSystem can be
 * instantiated / tested independently.
 *
 * Extends [[MemBusConfig]] so all TileLink bundles and bus-level modules
 * that take an implicit [[MemBusConfig]] work with this config directly.
 */
case class MemSystemConfig(
  tiers:         Seq[TierConfig],
  dataBusSize:   Int = 8,   // bytes per TileLink beat
  arithDataWidth: Int = 8,  // bits per arithmetic element
  addrWidth:     Int = 16,  // address bits
  sourceWidth:   Int = 1    // TileLink source-ID width
) extends MemBusConfig {
  require(tiers.nonEmpty, "MemSystemConfig must have at least one tier")

  /** Size of each tier in words (rounded up to power of 2). */
  val tierSizes: Seq[BigInt] = tiers.map { t =>
    BigInt(1) << log2Ceil(t.nBanks * t.bankDepth)
  }

  /** Base address of each tier in the host address space.
   *  Each base is aligned to the tier's (power-of-2) size. */
  val tierBases: Seq[BigInt] = {
    val bases = scala.collection.mutable.ArrayBuffer[BigInt]()
    var next = BigInt(0)
    for (sz <- tierSizes) {
      // align `next` up to `sz`
      val base = ((next + sz - 1) / sz) * sz
      bases += base
      next = base + sz
    }
    bases.toSeq
  }
}

object MemSystemConfig {
  /** Sensible three-tier default: small L1 + medium L2 + larger L3, single-lane external access. */
  def default(): MemSystemConfig = MemSystemConfig(
    tiers = Seq(
      TierConfig(nWritePorts = 2, nReadPorts = 3, nBanks = 4,  bankDepth = 256),
      TierConfig(nWritePorts = 1, nReadPorts = 1, nBanks = 16, bankDepth = 1024),
      TierConfig(nWritePorts = 1, nReadPorts = 1, nBanks = 8,  bankDepth = 2048)
    )
  )
}
