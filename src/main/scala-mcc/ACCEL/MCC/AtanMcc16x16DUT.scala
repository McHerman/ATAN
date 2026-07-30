package ATA8

import chisel3._
import chisel3.util._

/**
 * Top-level DUT combining ATA8 and MccWrapper for the larger
 * (Configuration.large16x16) systolic array + wide-accumulator
 * configuration. Structurally identical to [[AtanMccDUT]] -- same two-slave
 * mcc dmem routing (shared tier-0 scratchpad + semaphore bank), same
 * no-rebase-on-tier0 / strip-semBase-only addressing -- the only difference
 * is that the host-facing AXI-Stream ports are sized from
 * `atanConfig.axiStreamWidth` instead of a hardcoded 64 bits, since the
 * large config's dataBusSize/accDataWidth no longer match the small
 * config's fixed 64-bit stream width.
 *
 * `atanConfig` must have `riscv.enabled = true`.
 */
class AtanMcc16x16DUT(
  atanConfig:  Configuration,
  memCfgBase:  MemSystemConfig     = MemSystemConfig.default(),
  mccInitData: Map[Int, BigInt]    = Map.empty,
) extends Module {
  require(atanConfig.riscv.enabled, "AtanMcc16x16DUT requires atanConfig.riscv.enabled = true")

  implicit val c: Configuration = atanConfig

  val ata8 = Module(new ATA8(atanConfig, memCfgBase))
  val mcc  = Module(new MccWrapper(mccInitData))

  val io = IO(new Bundle {
    val AXIST_out    = new AXIST_2(c.axiStreamWidth, 2, 1, 1, 1)
    val AXIST_inData = Flipped(new AXIST_2(c.axiStreamWidth, 2, 1, 1, 1))
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
  //
  // The shared-SPM address-set mask must not exceed what mcc's own dmem
  // bus (c.riscv.tlBus, a fixed 16-bit local address window regardless of
  // the surrounding tier's real size -- MccWrapper always truncates to the
  // low 16 bits on rebase) can express. Tier0 itself can be far larger
  // (MemSystemConfig.large()'s tier0 alone is 1MB) than mcc's addressable
  // window, but mcc only ever touches a handful of buffers near the start
  // of it, so capping the mask at mcc's own bus width is correct, not just
  // convenient -- anything mcc could actually emit already falls in range.
  val mccAddrSpace  = BigInt(1) << c.riscv.tlBus.addrWidth
  val tier0Size     = BigInt(memCfgBase.tiers(0).bankDepth) * atanConfig.dataBusSize
  val sharedSpmMask = tier0Size.min(mccAddrSpace) - 1

  // Must cover every address in semaphore address range
  val semMaxOffset: BigInt =
    ((((c.nSemaphores.toLong - 1) * 4 + 3) << c.semaphoreGenerationWidth) +
      ((1L << c.semaphoreGenerationWidth) - 1))
  val semMask: BigInt = (BigInt(1) << (semMaxOffset + 1).bitLength) - 1

  val xbar = Module(new TLXbar(TLXbarConfig(
    nMasters = 1,
    slaves = Seq(
      TLSlaveConfig(addressSet = Seq((BigInt(c.riscv.sharedSpmBase), sharedSpmMask))),
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
