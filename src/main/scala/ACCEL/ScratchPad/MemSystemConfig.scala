package ATA8

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
}

object MemSystemConfig {
  /** Sensible two-tier default: small L1 + larger L2, single-lane external access. */
  def default(): MemSystemConfig = MemSystemConfig(
    tiers = Seq(
      TierConfig(nWritePorts = 2, nReadPorts = 3, nBanks = 4,  bankDepth = 256),
      TierConfig(nWritePorts = 1, nReadPorts = 1, nBanks = 16, bankDepth = 1024)
    )
  )
}
