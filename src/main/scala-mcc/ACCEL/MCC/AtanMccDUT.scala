package ATA8

import chisel3._
import chisel3.util._

/**
 * Top-level DUT combining ATA8 and MccWrapper for combined simulation.
 *
 * mcc dmem is routed through a TLXbar to three destinations:
 *   [dataSpmBase,  dataSpmBase  + dataSpmWords*4 - 1] → private mcc-only data SPM
 *   [sharedSpmBase, sharedSpmBase + tier0Size - 1]    → ATAN tier-0 shared scratchpad
 *   [semBase,      semBase      + 0x0FFF]             → ATAN semaphore bank
 *
 * Address transformations applied before forwarding:
 *   tier-0 path : subtract sharedSpmBase so tier-0 sees offsets from 0
 *   sem path    : (addr - semBase) >> 2  converts byte addr to sem-address
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

  // ── TLXbar: route mcc dmem to three slaves ──────────────────────────────

  val tier0Size = memCfgBase.tiers(0).bankDepth * atanConfig.dataBusSize

  val xbar = Module(new TLXbar(TLXbarConfig(
    nMasters = 1,
    slaves = Seq(
      TLSlaveConfig(addressSet = Seq((BigInt(c.riscv.dataSpmBase), BigInt(c.riscv.dataSpmWords * 4 - 1)))),
      TLSlaveConfig(addressSet = Seq((BigInt(c.riscv.sharedSpmBase), BigInt(tier0Size - 1)))),
      TLSlaveConfig(addressSet = Seq((BigInt(c.riscv.semBase), BigInt(0x0FFF)))),
    ),
    tl = c.riscv.tlBus,
  )))

  xbar.io.in(0) <> mcc.io.dmem

  // ── Slave 0: private mcc data SPM (4KB by default) ──────────────────────

  val spmBus: MemBusConfig = Configuration(bus = BusParams(dataBusSize = 4, addrWidth = 16, sourceWidth = 1))
  val privateSpm = Module(new MemTierScratchpad(
    SPMConfig(bankDepth = c.riscv.dataSpmWords, writeports = 1, readports = 1)(spmBus)
  )(spmBus))
  val spmHandler = Module(new TLScratchpadHandler(
    TLScratchConfig(read = true, write = true, atomic = false, tlConfig = c.riscv.tlBus)
  )(spmBus))
  spmHandler.io.tl <> xbar.io.out(0)
  privateSpm.io.Writeport(0) <> spmHandler.io.wMem.get
  privateSpm.io.Readport(0)  <> spmHandler.io.rMem.get

  // ── Slave 1: ATAN tier-0 shared scratchpad ───────────────────────────────
  // Subtract sharedSpmBase so tier-0 sees local addresses from 0.

  val mccIn = ata8.io.mccIn.get
  val out1  = xbar.io.out(1)
  mccIn.a.valid        := out1.a.valid
  out1.a.ready         := mccIn.a.ready
  mccIn.a.bits.opcode  := out1.a.bits.opcode
  mccIn.a.bits.param   := out1.a.bits.param
  mccIn.a.bits.size    := out1.a.bits.size
  mccIn.a.bits.source  := out1.a.bits.source
  mccIn.a.bits.address := out1.a.bits.address - c.riscv.sharedSpmBase.U
  mccIn.a.bits.mask    := out1.a.bits.mask
  mccIn.a.bits.data    := out1.a.bits.data
  mccIn.a.bits.corrupt := out1.a.bits.corrupt
  out1.d <> mccIn.d

  // ── Slave 2: ATAN semaphore bank ────────────────────────────────────────
  // Convert byte offset to sem-address: (addr - semBase) >> 2.

  val mccSemIn = ata8.io.mccSemIn.get
  val out2     = xbar.io.out(2)
  mccSemIn.a.valid        := out2.a.valid
  out2.a.ready            := mccSemIn.a.ready
  mccSemIn.a.bits.opcode  := out2.a.bits.opcode
  mccSemIn.a.bits.param   := out2.a.bits.param
  mccSemIn.a.bits.size    := out2.a.bits.size
  mccSemIn.a.bits.source  := out2.a.bits.source
  mccSemIn.a.bits.address := (out2.a.bits.address - c.riscv.semBase.U) >> 2
  mccSemIn.a.bits.mask    := out2.a.bits.mask
  mccSemIn.a.bits.data    := out2.a.bits.data
  mccSemIn.a.bits.corrupt := out2.a.bits.corrupt
  out2.d <> mccSemIn.d
}
