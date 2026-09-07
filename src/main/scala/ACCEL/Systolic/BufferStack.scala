package ATA8

import chisel3._
import chisel3.util._

// Stack module, must be filled completely before draining.

class BufferStack[T <: Data](val size: Int, val dataType: T) extends Module {

  val pointerwidth = log2Ceil(size)

  val io = IO(new Bundle {
    val WriteData = Flipped(Decoupled(dataType))  // updated line
    val ReadData = Flipped(new Readport(dataType))  // updated line
    val size = Input(UInt(pointerwidth.W))
  })

  assert(io.size <= size.U)

  val Head = RegInit(0.U(pointerwidth.W))
  // Top-of-stack read pointer. Kept stable (not re-derived from Head) so
  // that DualPortRAM's one-cycle synchronous read latency has already
  // caught up by the time the first read fires, however many idle cycles
  // pass between the stack becoming full and the first pop.
  val Tail = RegInit(0.U(pointerwidth.W))

  val fullReg = RegInit(false.B)
  val emptyReg = RegInit(true.B)

  val Mem = Module(new DualPortRAM(size, dataType))  // updated line

  io.ReadData.response.bits.readData := Mem.io.Read.data
  io.ReadData.response.valid := false.B

  Mem.io.Write.valid := false.B
  Mem.io.Write.bits := DontCare

  Mem.io.Read.addr := Tail

  when(io.WriteData.valid && !fullReg){
    Mem.io.Write.valid := true.B
    Mem.io.Write.bits.addr := Head
    Mem.io.Write.bits.data := io.WriteData.bits  // updated line

    when(Head === (io.size - 1.U)){
      fullReg  := true.B
      emptyReg := false.B
      Tail := Head
    }

    Head := Head + 1.U
  }

  // DualPortRAM's read is synchronous: Mem.io.Read.addr only reaches
  // Mem.io.Read.data one cycle later. Tail only becomes a valid "top of
  // stack" address the cycle after fullReg is asserted, so reads must wait
  // one further cycle for that address to have actually propagated through
  // the RAM before the first pop is allowed to fire.
  val readPrimed = RegNext(fullReg, false.B)

  io.ReadData.request.ready := readPrimed && !emptyReg

  when(io.ReadData.request.valid && readPrimed && !emptyReg){

    when(Tail === 0.U){
      Head     := 0.U
      Tail     := 0.U
      fullReg  := false.B
      emptyReg := true.B
    }.otherwise{
      Tail := Tail - 1.U
      Mem.io.Read.addr := Tail - 1.U // prefetch next pop's address a cycle early, matching DualPortRAM's read latency
    }

    io.ReadData.response.valid := true.B
  }

  io.WriteData.ready := !fullReg
}
