package ATA8

import chisel3._
import chisel3.util._

class Config(implicit c: Configuration) extends Module {
  val registersCount = 38

  val io = IO(new Bundle {
    val axi_s0      = Flipped(new CustomAXI4Lite(32, 32))
    val loadDebug   = Flipped(new LoadDebug)
    val exeDebug    = Flipped(new ExeDebug)
    val storeDebug  = Flipped(new StoreDebug)
    val receiverDebug = Flipped(Valid(UInt(64.W)))
    val decodeDebug   = Flipped(Valid(new LoadInst))
    val decodeOutLoad = Flipped(Valid(new LoadInst))
    val frontEndDebug = Input(new Bundle {
      val decodeReady   = Bool()
      val exeOutReady   = Bool()
      val loadOutReady  = Bool()
      val storeOutReady = Bool()
    })
    val AXIDebug = Input(new Bundle {
      val data_ready = Bool(); val data_valid = Bool()
      val inst_ready = Bool(); val inst_valid = Bool()
      val out_ready  = Bool(); val out_valid  = Bool()
    })
  })

  val regs = RegInit(VecInit(Seq.fill(registersCount)(0.U(32.W))))
  val slaveInterface = Module(new AXI4LiteCSR(32, 32))

  slaveInterface.io.ctl <> io.axi_s0

  when(slaveInterface.io.bus.write) {
    regs(slaveInterface.io.bus.addr) := slaveInterface.io.bus.dataOut
  }

  regs(0) := io.loadDebug.state
  regs(1) := io.exeDebug.state
  regs(2) := io.storeDebug.asUInt
  regs(3) := io.AXIDebug.asUInt

  when(io.receiverDebug.valid) {
    regs(4) := io.receiverDebug.bits
    regs(5) := regs(4)
    regs(6) := regs(5)
    regs(7) := regs(6)
  }

  when(io.decodeDebug.valid) {
    regs(8)  := io.decodeDebug.bits.asUInt
    regs(9)  := regs(8)
    regs(10) := regs(9)
    regs(11) := regs(10)
  }

  when(io.decodeOutLoad.valid) {
    regs(12) := io.decodeOutLoad.bits.asUInt
    regs(13) := regs(12)
    regs(14) := regs(13)
    regs(15) := regs(14)
  }

  regs(36) := io.frontEndDebug.asUInt

  slaveInterface.io.bus.dataIn := regs(slaveInterface.io.bus.addr)
}
