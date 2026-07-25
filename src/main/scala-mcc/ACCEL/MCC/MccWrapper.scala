package ATA8

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.rocket.CoreInterrupts

/**
 * Wraps the mcc RISC-V core with an instruction memory and exposes a
 * narrow TileLink dmem port (16-bit local address) for connection to
 * the ATAN scratchpad.
 *
 * When `initData` is provided the imem is loaded one word per cycle
 * before the core starts fetching (`io.initDone` goes high after loading).
 * With an empty `initData` the core starts immediately with a zeroed imem.
 *
 * Address translation: mcc 32-bit CPU address → 16-bit ATAN local address.
 *   local_addr = (cpu_addr - c.riscv.baseAddr)(15, 0)
 */
class MccWrapper(initData: Map[Int, BigInt] = Map.empty)(implicit c: Configuration) extends Module {

  implicit val p: Parameters                  = Parameters.empty
  implicit val conf: mcc.common.MccCoreParams = mcc.common.MccCoreParams(xprlen = 32)
  implicit val coreBus: MemBusConfig          = Configuration(bus = c.riscv.coreBusConf)

  val io = IO(new Bundle {
    val dmem     = new TilelinkPort(c.riscv.tlBus)
    val success  = Output(Bool())
    val tohost   = Output(UInt(32.W))
    val initDone = Output(Bool())
  })

  val core = Module(new mcc.stage5.Core())

  // ── Instruction memory with optional ROM init ─────────────────────────────
  val imem = Mem(c.riscv.imemWords, Vec(4, UInt(8.W)))

  val isInitDone: Bool = if (initData.nonEmpty) {
    val entries  = initData.toSeq.sortBy(_._1)
    val romAddrs = VecInit(entries.map { case (a, _) => a.U(log2Ceil(c.riscv.imemWords).W) })
    val romData  = VecInit(entries.map { case (_, d) =>
      VecInit(Seq(
        ((d >>  0) & 0xFF).U(8.W),
        ((d >>  8) & 0xFF).U(8.W),
        ((d >> 16) & 0xFF).U(8.W),
        ((d >> 24) & 0xFF).U(8.W),
      ))
    })
    val idx  = RegInit(0.U(log2Ceil(entries.size + 1).W))
    val done = idx === entries.size.U
    when(!reset.asBool && !done) {
      imem.write(romAddrs(idx), romData(idx))
      idx := idx + 1.U
    }
    done
  } else true.B

  io.initDone := isInitDone

  core.io.imem.req.ready      := true.B
  val wordIdx                  = core.io.imem.req.bits.addr(c.riscv.addrBits - 1, 2)
  val imem_rdata               = imem.read(wordIdx)
  core.io.imem.resp.valid     := isInitDone && core.io.imem.req.valid
  core.io.imem.resp.bits.data := Cat(imem_rdata(3), imem_rdata(2), imem_rdata(1), imem_rdata(0))

  // ── Tie off unused core inputs ────────────────────────────────────────────
  core.io.ddpath       := DontCare
  core.io.dcpath       := DontCare
  core.io.interrupt    := 0.U.asTypeOf(new CoreInterrupts(false))
  core.io.hartid       := 0.U
  core.io.reset_vector := c.riscv.baseAddr.U

  // ── Data memory: address translation, gated on initDone ──────────────────
  val mccA = core.io.dmem.a

  io.dmem.a.valid        := mccA.valid && isInitDone
  mccA.ready             := io.dmem.a.ready && isInitDone
  io.dmem.a.bits.opcode  := mccA.bits.opcode
  io.dmem.a.bits.param   := mccA.bits.param
  io.dmem.a.bits.size    := mccA.bits.size
  io.dmem.a.bits.source  := mccA.bits.source
  io.dmem.a.bits.address := (mccA.bits.address - c.riscv.baseAddr.U)(15, 0)
  io.dmem.a.bits.mask    := mccA.bits.mask
  io.dmem.a.bits.data    := mccA.bits.data
  io.dmem.a.bits.corrupt := mccA.bits.corrupt

  core.io.dmem.d <> io.dmem.d

  // ── tohost detection ──────────────────────────────────────────────────────
  val isTohostWrite = mccA.fire && isInitDone &&
    (mccA.bits.opcode === TilelinkOpcodes.PutFullData ||
     mccA.bits.opcode === TilelinkOpcodes.PutPartialData) &&
    (mccA.bits.address - c.riscv.baseAddr.U) === c.riscv.tohostOffset.U

  val tohostReg = RegInit(0.U(32.W))
  when(isTohostWrite) { tohostReg := mccA.bits.data }

  io.tohost  := tohostReg
  io.success := tohostReg === 1.U
}
