package ATA8

import chisel3._
import chisel3.util._
import chisel3.util.MixedVec._

class Dispatch(implicit c: Configuration) extends Module {

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Bundle{val op = UInt(6.W); val data = MixedVec(new ExecuteInst, new LoadInst, new StoreInst)}))

    val exeStream   = Decoupled(new ExecuteInst)
    val loadStream  = Decoupled(new LoadInst)
    val storeStream = Decoupled(new StoreInst)
  })

  val inReg = RegInit(0.U.asTypeOf(io.in.bits.cloneType))
  val valid = RegInit(false.B)

  io.in.ready := !valid

  io.exeStream.valid   := false.B
  io.exeStream.bits    := DontCare
  io.loadStream.valid  := false.B
  io.loadStream.bits   := DontCare
  io.storeStream.valid := false.B
  io.storeStream.bits  := DontCare

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
        when(io.exeStream.ready) { valid := false.B }
      }
      is(2.U) {
        io.loadStream.valid := true.B
        io.loadStream.bits  := inReg.data(1).asInstanceOf[LoadInst]
        when(io.loadStream.ready) { valid := false.B }
      }
      is(3.U) {
        io.storeStream.valid := true.B
        io.storeStream.bits  := inReg.data(2).asInstanceOf[StoreInst]
        when(io.storeStream.ready) { valid := false.B }
      }
    }
  }
}
