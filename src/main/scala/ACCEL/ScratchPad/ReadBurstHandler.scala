package ATA8

import chisel3._
import chisel3.util._

class TilelinkReadHandler(implicit c: MemBusConfig) extends Module {
  val io = IO(new Bundle {
    val tl = Flipped(new TilelinkPort)
    val mem = new Readport(Vec(c.dataBusSize, UInt(c.arithDataWidth.W)), Some(16))
  })

  // Defaults
  io.tl.a.ready := false.B
  io.tl.d.valid := false.B
  io.tl.d.bits := DontCare

  io.mem.request.valid := false.B
  io.mem.request.bits := DontCare

  val isLocked = RegInit(false.B)
  val addrReg = Reg(UInt(c.addrWidth.W))
  val sizeReg = Reg(UInt(24.W))
  val beatCnt = Reg(UInt(24.W))

  io.tl.a.ready := !isLocked

  when(io.tl.a.fire) {
    isLocked := true.B
    addrReg := io.tl.a.bits.address
    sizeReg := io.tl.a.bits.size
    beatCnt := io.tl.a.bits.size - c.dataBusSize.U
  }

  when(isLocked) {
    io.mem.request.valid := true.B
    io.mem.request.bits.addr.get := addrReg

    io.tl.d.valid := io.mem.response.valid
    io.tl.d.bits.opcode := TilelinkOpcodes.AccessAckData
    io.tl.d.bits.param := 0.U
    io.tl.d.bits.size := sizeReg
    io.tl.d.bits.source := 0.U
    io.tl.d.bits.sink := 0.U
    io.tl.d.bits.denied := 0.U
    io.tl.d.bits.data := io.mem.response.bits.readData.asUInt
    io.tl.d.bits.corrupt := 0.U

    when(io.mem.request.fire) {
      addrReg := addrReg + c.dataBusSize.U
    }
    when(io.tl.d.fire) {
      beatCnt := beatCnt - c.dataBusSize.U

      when(beatCnt === 0.U) {
        isLocked := false.B
      }
    }
  }
}
