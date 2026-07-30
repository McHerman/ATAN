package ATA8

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.rocket.CoreInterrupts

/**
 * Wraps the mcc RISC-V core together with its private instruction memory,
 * exposing a single dmem TileLink port (rebased to `atanConfig.riscv`'s
 * 16-bit local address window) plus tohost/success/initDone.
 *
 * imem is synchronous and TileLink-backed (mcc.ImemTL): a private
 * MemTierScratchpad bank, loaded at boot via a write-only
 * TLScratchpadHandler and served on the fetch-critical path by a pipelined
 * 2-outstanding Get-only responder. `mcc.stage5.Core` issues imem/dmem
 * requests against the CPU's 32-bit address space (`coreBusConf`); both
 * are rebased here by subtracting `atanConfig.riscv.baseAddr` before
 * leaving this module.
 */
// Minimal standalone MemBusConfig for mcc's own CPU-internal buses (imem/
// dmem), which have nothing to do with the systolic array. Configuration
// carries array-sizing invariants (dataBusSize == arrayDim*accDataBytes,
// etc.) that don't apply to this narrow, fixed 32-bit CPU bus, so it can't
// be reused here the way it is for the shared-scratchpad-facing side.
case class CoreMemBusConfig(
  dataBusSize:    Int,
  addrWidth:      Int,
  sourceWidth:    Int,
  arithDataWidth: Int = 8,
) extends MemBusConfig

class MccWrapper(initData: Map[Int, BigInt] = Map.empty)(implicit atanConfig: Configuration) extends Module {
  private val rp = atanConfig.riscv
  require(rp.enabled, "MccWrapper requires atanConfig.riscv.enabled = true")

  implicit val p: Parameters          = Parameters.empty
  implicit val coreConf: mcc.common.MccCoreParams = mcc.common.MccCoreParams(
    xprlen = 32,
    // The semaphore bank is byte-addressed (each byte in this 256-byte
    // window selects a distinct full/empty/generation register), not
    // word-aligned -- see memory.scala's mem_addr_exempt.
    mmioNoAlignCheckBase = Some(rp.semBase.toLong),
    mmioNoAlignCheckSize = 0x100,
  )
  val coreBusConf: MemBusConfig       = CoreMemBusConfig(
    dataBusSize = rp.coreBusConf.dataBusSize,
    addrWidth   = rp.coreBusConf.addrWidth,
    sourceWidth = rp.coreBusConf.sourceWidth,
  )

  val io = IO(new Bundle {
    val dmem     = new TilelinkPort(rp.tlBus)
    val tohost   = Output(UInt(32.W))
    val success  = Output(Bool())
    val initDone = Output(Bool())
  })

  val core = Module(new mcc.stage5.Core()(p, coreConf, coreBusConf))

  core.io.ddpath       := DontCare
  core.io.dcpath       := DontCare
  core.io.interrupt    := 0.U.asTypeOf(new CoreInterrupts(false))
  core.io.hartid       := 0.U
  core.io.reset_vector := rp.baseAddr.U

  // ── Instruction memory: rebase the core's 32-bit PC into imem's own
  // 16-bit local word space, mirroring mcc.MccTestHarness's wiring ─────────
  val imemMod = Module(new mcc.ImemTL(rp.imemWords, initData)(coreBusConf))

  val mccImemA = core.io.imem.a

  imemMod.io.tl.a.valid        := mccImemA.valid
  imemMod.io.tl.a.bits.opcode  := mccImemA.bits.opcode
  imemMod.io.tl.a.bits.param   := mccImemA.bits.param
  imemMod.io.tl.a.bits.size    := mccImemA.bits.size
  imemMod.io.tl.a.bits.source  := mccImemA.bits.source
  imemMod.io.tl.a.bits.address := (mccImemA.bits.address - rp.baseAddr.U)(15, 0)
  imemMod.io.tl.a.bits.mask    := mccImemA.bits.mask
  imemMod.io.tl.a.bits.data    := mccImemA.bits.data
  imemMod.io.tl.a.bits.corrupt := mccImemA.bits.corrupt
  mccImemA.ready               := imemMod.io.tl.a.ready

  core.io.imem.d.valid        := imemMod.io.tl.d.valid
  core.io.imem.d.bits.opcode  := imemMod.io.tl.d.bits.opcode
  core.io.imem.d.bits.param   := imemMod.io.tl.d.bits.param
  core.io.imem.d.bits.size    := imemMod.io.tl.d.bits.size
  core.io.imem.d.bits.source  := imemMod.io.tl.d.bits.source
  core.io.imem.d.bits.sink    := imemMod.io.tl.d.bits.sink
  core.io.imem.d.bits.denied  := imemMod.io.tl.d.bits.denied
  core.io.imem.d.bits.data    := imemMod.io.tl.d.bits.data
  core.io.imem.d.bits.corrupt := imemMod.io.tl.d.bits.corrupt
  imemMod.io.tl.d.ready       := core.io.imem.d.ready

  val initDone: Bool = imemMod.io.initDone
  io.initDone := initDone

  // ── Data memory: rebase to the 16-bit ATAN-local window; gate the A
  // channel on ROM-load completion so mcc can't touch dmem before its
  // program image is in place ───────────────────────────────────────────
  val mccA = core.io.dmem.a

  core.io.dmem <> io.dmem

  io.dmem.a.valid        := mccA.valid && initDone
  io.dmem.a.bits.opcode  := mccA.bits.opcode
  io.dmem.a.bits.param   := mccA.bits.param
  io.dmem.a.bits.size    := mccA.bits.size
  io.dmem.a.bits.source  := mccA.bits.source
  io.dmem.a.bits.address := (mccA.bits.address - rp.baseAddr.U)(15, 0)
  io.dmem.a.bits.mask    := mccA.bits.mask
  io.dmem.a.bits.data    := mccA.bits.data
  io.dmem.a.bits.corrupt := mccA.bits.corrupt
  mccA.ready             := io.dmem.a.ready && initDone

  // ── tohost detection ─────────────────────────────────────────────────
  val tohostOff = rp.tohostOffset
  val isTohostWrite = mccA.fire &&
    (mccA.bits.opcode === TilelinkOpcodes.PutFullData ||
     mccA.bits.opcode === TilelinkOpcodes.PutPartialData) &&
    (mccA.bits.address - rp.baseAddr.U) === tohostOff.U

  val tohostReg = RegInit(0.U(32.W))
  when(isTohostWrite) { tohostReg := mccA.bits.data }

  io.tohost  := tohostReg
  io.success := tohostReg === 1.U
}
