package ATA8

import chisel3._
import chisel3.util._

/**
 * Top-level DUT combining ATA8 and MccWrapper for combined simulation.
 *
 * mcc dmem is routed through a TLXbar to two destinations, mirroring
 * mcc.MccTestHarness's own structure:
 *   [sharedSpmBase, sharedSpmBase + tier0Size - 1] → ATAN tier-0 shared scratchpad
 *   [semBase,       semBase       + 0x00FF]        → ATAN semaphore bank
 *
 * sharedSpmBase is 0 (see MccParams doc): the compiler emits buffer
 * pointers with no offset, assuming they route directly to the SPM, so no
 * address rebasing happens on that path (TLXbar itself never modifies
 * addresses — a request routed to output x keeps whatever address it had
 * on input). semBase (0x4000) is hardcoded in the compiler; the sem path
 * strips it by subtraction only — SemaphoreBank is byte-indexed, so the
 * rebased address goes through as-is (no additional shift).
 *
 * mcc imem is private (mcc.ImemTL, wired up inside MccWrapper) and never
 * touches ATAN's memory system.
 *
 * `atanConfig` must have `riscv.enabled = true`.
 */
class AtanMccDUT(
  atanConfig:  Configuration,
  memCfgBase:  MemSystemConfig     = MemSystemConfig.default(),
  mccInitData: Map[Int, BigInt]    = Map.empty,
) extends Module {
  require(atanConfig.riscv.enabled, "AtanMccDUT requires atanConfig.riscv.enabled = true")

  implicit val c: Configuration = atanConfig

  val ata8 = Module(new ATA8(atanConfig, memCfgBase))
  val mcc  = Module(new MccWrapper(mccInitData))

  val io = IO(new Bundle {
    val AXIST_out    = new AXIST_2(64, 2, 1, 1, 1)
    val AXIST_inData = Flipped(new AXIST_2(64, 2, 1, 1, 1))
    val AXIST_inInst = Flipped(new AXIST_2(128, 2, 1, 1, 1))
    val axi_s0       = Flipped(new CustomAXI4Lite(32, 32))
    val hostIn       = Flipped(new TilelinkPort(c.tlBus))
    val mccTohost    = Output(UInt(32.W))
    val mccSuccess   = Output(Bool())
    val mccInitDone  = Output(Bool())
  })

  ata8.io.AXIST_out    <> io.AXIST_out
  ata8.io.AXIST_inData <> io.AXIST_inData
  ata8.io.AXIST_inInst <> io.AXIST_inInst
  ata8.io.axi_s0       <> io.axi_s0
  ata8.io.hostIn       <> io.hostIn

  io.mccTohost   := mcc.io.tohost
  io.mccSuccess  := mcc.io.success
  io.mccInitDone := mcc.io.initDone

  // ── TLXbar: route mcc dmem to two slaves ────────────────────────────────

  val tier0Size = memCfgBase.tiers(0).bankDepth * atanConfig.dataBusSize

  // Must cover every address in semaphore address range
  val semMaxOffset: BigInt =
    ((((c.nSemaphores.toLong - 1) * 4 + 3) << c.semaphoreGenerationWidth) +
      ((1L << c.semaphoreGenerationWidth) - 1))
  val semMask: BigInt = (BigInt(1) << (semMaxOffset + 1).bitLength) - 1

  val xbar = Module(new TLXbar(TLXbarConfig(
    nMasters = 1,
    slaves = Seq(
      TLSlaveConfig(addressSet = Seq((BigInt(c.riscv.sharedSpmBase), BigInt(tier0Size - 1)))),
      TLSlaveConfig(addressSet = Seq((BigInt(c.riscv.semBase), semMask))),
    ),
    tl = c.riscv.tlBus,
  )))

  xbar.io.in(0) <> mcc.io.dmem

  // ── Slave 0: ATAN tier-0 shared scratchpad ───────────────────────────────
  // sharedSpmBase = 0, so no rebasing is needed on this path.

  val mccIn = ata8.io.mccIn.get
  val out0  = xbar.io.out(0)
  mccIn.a.valid        := out0.a.valid
  out0.a.ready         := mccIn.a.ready
  mccIn.a.bits.opcode  := out0.a.bits.opcode
  mccIn.a.bits.param   := out0.a.bits.param
  mccIn.a.bits.size    := out0.a.bits.size
  mccIn.a.bits.source  := out0.a.bits.source
  mccIn.a.bits.address := out0.a.bits.address - c.riscv.sharedSpmBase.U
  mccIn.a.bits.mask    := out0.a.bits.mask
  mccIn.a.bits.data    := out0.a.bits.data
  mccIn.a.bits.corrupt := out0.a.bits.corrupt
  out0.d <> mccIn.d

  // ── Slave 1: ATAN semaphore bank ────────────────────────────────────────
  // Strip semBase only — SemaphoreBank is byte-indexed, no further shift.

  val mccSemIn = ata8.io.mccSemIn.get
  val out1     = xbar.io.out(1)
  mccSemIn.a.valid        := out1.a.valid
  out1.a.ready            := mccSemIn.a.ready
  mccSemIn.a.bits.opcode  := out1.a.bits.opcode
  mccSemIn.a.bits.param   := out1.a.bits.param
  mccSemIn.a.bits.size    := out1.a.bits.size
  mccSemIn.a.bits.source  := out1.a.bits.source
  mccSemIn.a.bits.address := out1.a.bits.address - c.riscv.semBase.U
  mccSemIn.a.bits.mask    := out1.a.bits.mask
  mccSemIn.a.bits.data    := out1.a.bits.data
  mccSemIn.a.bits.corrupt := out1.a.bits.corrupt
  out1.d <> mccSemIn.d
}
