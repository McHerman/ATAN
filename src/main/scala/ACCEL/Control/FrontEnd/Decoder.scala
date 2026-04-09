package ATA8

import chisel3._
import chisel3.util._
import chisel3.util.MixedVec._

class Decoder(implicit c: Configuration) extends Module {

  val io = IO(new Bundle {
    val instructionStream = Flipped(Decoupled(new InstructionPackage))
    val issueStream = Decoupled(new Bundle{val op = UInt(6.W); val data = MixedVec(new ExecuteInst, new LoadInst, new StoreInst, new DMAInst, new SemProgInst)})
  })

  io.instructionStream.ready := false.B
  io.issueStream.valid := false.B
  io.issueStream.bits := DontCare

  val inReg = RegInit(0.U.asTypeOf(new InstructionPackage))
  val stall = WireDefault(false.B)

  when(!stall){
    io.instructionStream.ready := true.B
    when(io.instructionStream.valid){
      inReg := io.instructionStream.bits
    }.otherwise{
      inReg.instruction := 0.U
    }
  }

  val inst = inReg.instruction

  // Opcode is shared across all instruction types at bits [5:0]
  io.issueStream.bits.op := inst(5, 0)

  // Decode all instruction types from raw bits using their layout annotations
  val exe = Wire(new ExecuteInst);    exe := DontCare; exe.decodeFrom(inst)
  val ld  = Wire(new LoadInst);       ld  := DontCare; ld.decodeFrom(inst)
  val st  = Wire(new StoreInst);      st  := DontCare; st.decodeFrom(inst)
  val dma = Wire(new DMAInst);        dma := DontCare; dma.decodeFrom(inst)
  val sem = Wire(new SemProgInst);    sem := DontCare; sem.decodeFrom(inst)

  io.issueStream.bits.data(0).asInstanceOf[ExecuteInst]  := exe
  io.issueStream.bits.data(1).asInstanceOf[LoadInst]     := ld
  io.issueStream.bits.data(2).asInstanceOf[StoreInst]    := st
  io.issueStream.bits.data(3).asInstanceOf[DMAInst]      := dma
  io.issueStream.bits.data(4).asInstanceOf[SemProgInst]  := sem

  when(inst(5, 0) =/= 0.U){
    when(io.issueStream.ready){
      io.issueStream.valid := true.B
    }.otherwise{
      stall := true.B
    }
  }
}
