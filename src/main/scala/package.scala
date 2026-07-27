import chisel3._
import chisel3.util.log2Ceil

package object ATA8 {

  /** Explicit TileLink port sizing — passed as a constructor arg, not an implicit. */
  case class TLBusConfig(dataBusSize: Int, addrWidth: Int, sourceWidth: Int)

  // Minimal bus-level parameters shared between Configuration and MemSystemConfig.
  // Modules that only need TileLink/data-bus dimensions should take this as their implicit.
  trait MemBusConfig {
    def dataBusSize: Int
    def arithDataWidth: Int
    def addrWidth: Int
    def sourceWidth: Int
    // Width of the gen tag in the LSBs of every semaphore TL address.
    def semaphoreGenerationWidth: Int = 0

    def tlBus:    TLBusConfig = TLBusConfig(dataBusSize, addrWidth, sourceWidth)
    def tlSemBus: TLBusConfig = TLBusConfig(4,           addrWidth, sourceWidth)
  }

  // ── Logical parameter groups ───────────────────────────────────────────
  // Each group has its own defaults so a `Configuration` can be built by
  // overriding only the slice that matters (e.g. `Configuration(bus =
  // BusParams(sourceWidth = 16))`).  Flat accessors on `Configuration`
  // (`c.dataBusSize`, `c.grainDim`, ...) are preserved for back-compat.

  /** Interconnect / TileLink-ish bus dimensions. */
  case class BusParams(
    dataBusSize:    Int = 8,    // bytes per TileLink beat
    addrWidth:      Int = 24,
    sourceWidth:    Int = 8,    // covers TLXbar ID ranges: 10 ids/master rounded to 16
    axiStreamWidth: Int = 64,   // bits per host-facing AXI-Stream tdata word
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
    arrayDim:      Int = 8,    // PE lanes per side of a PEArray tile
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
    nSemaphores:     Int = 8,
    queueSize:       Int = 2,
    // 0 disables the gen check and preserves the legacy 2-address-per-port layout.
    generationWidth: Int = 0,
  )

  /** Embedded RISC-V core (mcc) integration parameters.
    *
    * sharedSpmBase = 0: the compiler emits buffer pointers with no offset
    * whatsoever, assuming they route directly to the shared SPM (ATAN's
    * tier-0 scratchpad) — see mcc/test/programs_rv32/riscv-extrasmall.ll.
    * semBase = 0x4000 is likewise hardcoded in the compiler; changing it
    * here would desync from already-compiled RISC-V binaries.
    */
  case class MccParams(
    enabled:      Boolean = false,
    baseAddr:     Long    = 0x80000000L,
    tohostOffset: Int     = 0x1000,
    imemWords:    Int     = 4096,
    dataSpmWords: Int     = 1024,
    dataSpmBase:  Int     = 0x0000,
    sharedSpmBase: Int    = 0x0000,
    semBase:      Int     = 0x4000,
  ) {
    val addrBits: Int      = log2Ceil(imemWords * 4)
    val coreBusConf: BusParams = BusParams(dataBusSize = 4, addrWidth = 32, sourceWidth = 1)
    val tlBus: TLBusConfig = TLBusConfig(dataBusSize = 4, addrWidth = 16, sourceWidth = 1)
  }

  /** Trigger-system (predicate-table dispatcher) parameters. */
  case class TriggerParams(
    // Number of resident rows in the trigger table (parallel-evaluated each
    // cycle, stored as flip-flops).
    rows:         Int = 16,
    // Max dependency entries in a single row's guard list.
    maxGuards:    Int = 6,
    // Max acquire entries (sem `done` bits flipped on completion) per op.
    maxAcquires:  Int = 4,
    // Depth of the op memory (SRAM-backed payload store, indexed by row.opId).
    opMemDepth:   Int = 128,
    // Width of the op-type tag in the payload.
    opTypeWidth:  Int = 4,
    // Raw-operand width carried opaquely to the action units.
    operandsBits: Int = 32,
  ) {
    require(rows > 0,        "trigger.rows must be positive")
    require(maxGuards > 0,   "trigger.maxGuards must be positive")
    require(maxAcquires > 0, "trigger.maxAcquires must be positive")
    require(opMemDepth > 0,  "trigger.opMemDepth must be positive")
  }

  case class Configuration(
    bus:       BusParams       = BusParams(),
    data:      DatapathParams  = DatapathParams(),
    systolic:  SystolicParams  = SystolicParams(),
    memory:    MemoryParams    = MemoryParams(),
    control:   ControlParams   = ControlParams(),
    semaphore: SemaphoreParams = SemaphoreParams(),
    trigger:   TriggerParams   = TriggerParams(),
    riscv:     MccParams       = MccParams(),
  ) extends MemBusConfig {

    // ── Flat accessors (delegate into the grouped params) ────────────────
    def scratchpadSize   = memory.scratchpadSize
    def bufferReadPorts  = memory.bufferReadPorts
    def bufferWritePorts = memory.bufferWritePorts
    def grainDim         = systolic.grainDim
    def sysDim           = systolic.sysDim
    def arrayDim         = systolic.arrayDim
    def grainFIFOSize    = systolic.grainFIFOSize
    def grainACCUSize    = systolic.grainACCUSize
    def arithDataWidth   = data.arithDataWidth
    def accDataWidth     = data.accDataWidth
    def modeWidth        = data.modeWidth
    def tagCount            = control.tagCount
    def nSemaphores             = semaphore.nSemaphores
    def semaphoreQueueSize      = semaphore.queueSize
    override def semaphoreGenerationWidth = semaphore.generationWidth
    def triggerRows             = trigger.rows
    def triggerMaxGuards        = trigger.maxGuards
    def triggerMaxAcquires      = trigger.maxAcquires
    def triggerOpMemDepth       = trigger.opMemDepth
    def triggerOpTypeWidth      = trigger.opTypeWidth
    def triggerOperandsBits     = trigger.operandsBits
    def fusedSemStateSize       = nSemaphores * (1 << semaphoreGenerationWidth)
    def fusedSemAddrWidth       = log2Ceil(fusedSemStateSize max 2)
    def addrWidth           = bus.addrWidth
    def dataBusSize         = bus.dataBusSize
    def sourceWidth         = bus.sourceWidth
    def axiStreamWidth      = bus.axiStreamWidth

    // ── Group-level overriders (less verbose than nested .copy chains) ───
    def withBus(f: BusParams => BusParams):                  Configuration = copy(bus       = f(bus))
    def withData(f: DatapathParams => DatapathParams):       Configuration = copy(data      = f(data))
    def withSystolic(f: SystolicParams => SystolicParams):   Configuration = copy(systolic  = f(systolic))
    def withMemory(f: MemoryParams => MemoryParams):         Configuration = copy(memory    = f(memory))
    def withControl(f: ControlParams => ControlParams):      Configuration = copy(control   = f(control))
    def withSemaphore(f: SemaphoreParams => SemaphoreParams): Configuration = copy(semaphore = f(semaphore))
    def withTrigger(f: TriggerParams => TriggerParams):       Configuration = copy(trigger   = f(trigger))

    // ── Derived widths / sanity checks ───────────────────────────────────
    val tagWidth       = log2Ceil(tagCount)
    val grainSizeWidth = log2Ceil(grainDim)
    val accDataBytes   = accDataWidth / 8

    require(accDataWidth >= arithDataWidth, "accDataWidth must be >= arithDataWidth")
    require(accDataWidth % 8 == 0, "accDataWidth must be a multiple of 8")
    require(dataBusSize % (arrayDim * arithDataWidth / 8) == 0,
      "dataBusSize must divide evenly into arrayDim*arithDataWidth/8-byte sub-rows (X/Y beat deserialization)")
    require(dataBusSize == arrayDim * accDataBytes,
      "dataBusSize must exactly equal arrayDim*accDataBytes so one drained output row is exactly one TL beat")
    require((dataBusSize * 8) % axiStreamWidth == 0,
      "dataBusSize*8 must divide evenly by axiStreamWidth (AXI-Stream beat assembly)")
    require(nSemaphores > 0, "nSemaphores must be positive")
    require(semaphoreGenerationWidth >= 0, "semaphoreGenerationWidth must be non-negative")
  }

  object Configuration {
    // The pre-refactor 8x8 configuration: dataBusSize tightly coupled to
    // arrayDim (one PE lane's byte per bus lane) and accDataWidth == 8 (no
    // separate accumulator width, i.e. accumulation quantized to 8 bits
    // internally). Most of the test suite is still implicitly written
    // against this shape, so it's the default.
    def default(): Configuration = Configuration()
    def test():    Configuration = default()

    // The larger, decoupled-bus-vs-array-size configuration (16x16 array,
    // 512-bit bus, 32-bit accumulator, 128-bit AXI-Stream) -- opt in to this
    // explicitly in the handful of tests (and the real synthesis target)
    // that actually exercise it.
    def large16x16(): Configuration = Configuration(
      bus      = BusParams(dataBusSize = 64, axiStreamWidth = 128),
      data     = DatapathParams(accDataWidth = 32),
      systolic = SystolicParams(arrayDim = 16),
    )
  }
}
