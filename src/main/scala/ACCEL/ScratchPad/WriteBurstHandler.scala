package ATA8

import chisel3._
import chisel3.util._

class TilelinkWriteHandler(implicit c: MemBusConfig) extends Module {
  val io = IO(new Bundle {
    val tl = Flipped(new TilelinkPort)
    val mem = Decoupled(new Writeport(new Bundle { val writeData = Vec(c.dataBusSize, UInt(8.W)); val strb = Vec(c.dataBusSize, Bool()) }, 16))
  })

  // Defaults
  io.tl.a.ready := false.B
  io.tl.d.valid := false.B
  io.tl.d.bits := DontCare
  io.mem.valid := false.B
  io.mem.bits := DontCare

  val isLocked = RegInit(false.B)
  val addrReg = Reg(UInt(c.addrWidth.W))
  val sizeReg = Reg(UInt(24.W))
  val beatCnt = Reg(UInt(24.W))
  val firstBeat = RegInit(false.B)

  when(!isLocked) {
    // Accept first A beat and write it — pass valid/ready through independently
    io.tl.a.ready := io.mem.ready
    io.mem.valid := io.tl.a.valid
    io.mem.bits.addr := io.tl.a.bits.address
    io.mem.bits.data.writeData := io.tl.a.bits.data.asTypeOf(Vec(c.dataBusSize, UInt(8.W)))
    io.mem.bits.data.strb := VecInit(io.tl.a.bits.mask.asBools)

    when(io.tl.a.fire) {
      when(io.tl.a.bits.size > c.dataBusSize.U) {
        isLocked := true.B
        addrReg := io.tl.a.bits.address + 1.U
        sizeReg := io.tl.a.bits.size
        beatCnt := io.tl.a.bits.size - (2 * c.dataBusSize).U
      }.otherwise {
        // Single beat - send AccessAck immediately
        firstBeat := true.B
      }
    }
  }.otherwise {
    // Accept remaining A beats — pass valid/ready through independently
    io.tl.a.ready := io.mem.ready
    io.mem.valid := io.tl.a.valid
    io.mem.bits.addr := addrReg
    io.mem.bits.data.writeData := io.tl.a.bits.data.asTypeOf(Vec(c.dataBusSize, UInt(8.W)))
    io.mem.bits.data.strb := VecInit(io.tl.a.bits.mask.asBools)

    when(io.tl.a.fire) {
      addrReg := addrReg + 1.U
      beatCnt := beatCnt - c.dataBusSize.U

      when(beatCnt === 0.U) {
        isLocked := false.B
        firstBeat := true.B // trigger AccessAck next cycle
      }
    }
  }

  // Send AccessAck on D channel after all beats written
  when(firstBeat) {
    io.tl.d.valid := true.B
    io.tl.d.bits.opcode := TilelinkOpcodes.AccessAck
    io.tl.d.bits.param := 0.U
    io.tl.d.bits.size := sizeReg
    io.tl.d.bits.source := 0.U
    io.tl.d.bits.sink := 0.U
    io.tl.d.bits.denied := 0.U
    io.tl.d.bits.data := 0.U
    io.tl.d.bits.corrupt := 0.U
    when(io.tl.d.fire) {
      firstBeat := false.B
    }
  }
}
