import chisel3._
import chisel3.util.log2Ceil

package object ATA8 {

  // Minimal bus-level parameters shared between Configuration and MemSystemConfig.
  // Modules that only need TileLink/data-bus dimensions should take this as their implicit.
  trait MemBusConfig {
    def dataBusSize: Int
    def arithDataWidth: Int
    def addrWidth: Int
    def sourceWidth: Int
  }

  // ── Logical parameter groups ───────────────────────────────────────────
  // Each group has its own defaults so a `Configuration` can be built by
  // overriding only the slice that matters (e.g. `Configuration(bus =
  // BusParams(sourceWidth = 16))`).  Flat accessors on `Configuration`
  // (`c.dataBusSize`, `c.grainDim`, ...) are preserved for back-compat.

  /** Interconnect / TileLink-ish bus dimensions. */
  case class BusParams(
    dataBusSize: Int = 8,
    addrWidth:   Int = 16,
    sourceWidth: Int = 8,   // covers TLXbar ID ranges: 10 ids/master rounded to 16
  )

  /** Arithmetic / accumulator datapath widths. */
  case class DatapathParams(
    arithDataWidth: Int = 8,
    accDataWidth:   Int = 8,
    modeWidth:      Int = 1,
  )

  /** Systolic array geometry and per-grain FIFO/accumulator depths. */
  case class SystolicParams(
    grainDim:      Int = 1,
    sysDim:        Int = 1,
    grainFIFOSize: Int = 64,
    grainACCUSize: Int = 64,
  )

  /** Scratchpad / buffer parameters. */
  case class MemoryParams(
    scratchpadSize:   Int = 4096,
    bufferReadPorts:  Int = 3,
    bufferWritePorts: Int = 2,
  )

  /** Front-end / control parameters. */
  case class ControlParams(
    tagCount: Int = 8,
  )

  /** Semaphore-system parameters. */
  case class SemaphoreParams(
    nSemaphores: Int = 8,
    queueSize:   Int = 2,
  )

  case class Configuration(
    bus:       BusParams       = BusParams(),
    data:      DatapathParams  = DatapathParams(),
    systolic:  SystolicParams  = SystolicParams(),
    memory:    MemoryParams    = MemoryParams(),
    control:   ControlParams   = ControlParams(),
    semaphore: SemaphoreParams = SemaphoreParams(),
  ) extends MemBusConfig {

    // ── Flat accessors (delegate into the grouped params) ────────────────
    def scratchpadSize   = memory.scratchpadSize
    def bufferReadPorts  = memory.bufferReadPorts
    def bufferWritePorts = memory.bufferWritePorts
    def grainDim         = systolic.grainDim
    def sysDim           = systolic.sysDim
    def grainFIFOSize    = systolic.grainFIFOSize
    def grainACCUSize    = systolic.grainACCUSize
    def arithDataWidth   = data.arithDataWidth
    def accDataWidth     = data.accDataWidth
    def modeWidth        = data.modeWidth
    def tagCount            = control.tagCount
    def nSemaphores         = semaphore.nSemaphores
    def semaphoreQueueSize  = semaphore.queueSize
    def addrWidth           = bus.addrWidth
    def dataBusSize         = bus.dataBusSize
    def sourceWidth         = bus.sourceWidth

    // ── Group-level overriders (less verbose than nested .copy chains) ───
    def withBus(f: BusParams => BusParams):                  Configuration = copy(bus       = f(bus))
    def withData(f: DatapathParams => DatapathParams):       Configuration = copy(data      = f(data))
    def withSystolic(f: SystolicParams => SystolicParams):   Configuration = copy(systolic  = f(systolic))
    def withMemory(f: MemoryParams => MemoryParams):         Configuration = copy(memory    = f(memory))
    def withControl(f: ControlParams => ControlParams):      Configuration = copy(control   = f(control))
    def withSemaphore(f: SemaphoreParams => SemaphoreParams): Configuration = copy(semaphore = f(semaphore))

    // ── Derived widths / sanity checks ───────────────────────────────────
    val tagWidth       = log2Ceil(tagCount)
    val grainSizeWidth = log2Ceil(grainDim)
    val accDataBytes   = accDataWidth / 8

    require(accDataWidth >= arithDataWidth, "accDataWidth must be >= arithDataWidth")
    require(accDataWidth % 8 == 0, "accDataWidth must be a multiple of 8")
    require(nSemaphores > 0, "nSemaphores must be positive")
  }

  object Configuration {
    def default(): Configuration = Configuration()
    def test():    Configuration = default()
  }
}
