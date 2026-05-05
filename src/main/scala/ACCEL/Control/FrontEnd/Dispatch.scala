package ATA8

import chisel3._
import chisel3.util._
import chisel3.util.MixedVec._

class Dispatch(implicit c: Configuration) extends Module {

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Bundle{val op = UInt(6.W); val data = MixedVec(new ExecuteInst, new LoadInst, new StoreInst, new DMAInst, new SemProgInst)}))

    val exeStream     = Decoupled(new ExecuteInst)
    val loadStream    = Decoupled(new LoadInst)
    val storeStream   = Decoupled(new StoreInst)
    val dmaStream     = Decoupled(new DMAInst)
    val semProgStream = Decoupled(new SemProgInst)
  })

  val inReg = RegInit(0.U.asTypeOf(io.in.bits.cloneType))
  val valid = RegInit(false.B)

  io.in.ready := !valid

  io.exeStream.valid     := false.B
  io.exeStream.bits      := DontCare
  io.loadStream.valid    := false.B
  io.loadStream.bits     := DontCare
  io.storeStream.valid   := false.B
  io.storeStream.bits    := DontCare
  io.dmaStream.valid     := false.B
  io.dmaStream.bits      := DontCare
  io.semProgStream.valid := false.B
  io.semProgStream.bits  := DontCare

  // Latch incoming decoded instruction
  when(io.in.fire) {
    inReg := io.in.bits
    valid := true.B
  }

  // Dispatch to the appropriate stream based on opcode
  when(valid) {
    switch(inReg.op) {
      is(1.U) {
        io.exeStream.valid := true.B
        io.exeStream.bits  := inReg.data(0).asInstanceOf[ExecuteInst]
        when(io.exeStream.fire) { valid := false.B }
      }
      is(2.U) {
        io.loadStream.valid := true.B
        io.loadStream.bits  := inReg.data(1).asInstanceOf[LoadInst]
        when(io.loadStream.fire) { valid := false.B }
      }
      is(3.U) {
        io.storeStream.valid := true.B
        io.storeStream.bits  := inReg.data(2).asInstanceOf[StoreInst]
        when(io.storeStream.fire) { valid := false.B }
      }
      is(4.U) {
        io.dmaStream.valid := true.B
        io.dmaStream.bits  := inReg.data(3).asInstanceOf[DMAInst]
        when(io.dmaStream.fire) { valid := false.B }
      }
      is(5.U) {
        io.semProgStream.valid := true.B
        io.semProgStream.bits  := inReg.data(4).asInstanceOf[SemProgInst]
        when(io.semProgStream.fire) { valid := false.B }
      }
    }
  }
}
