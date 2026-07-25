package ATA8

import chisel3._
import chisel3.util._

// Deserializes one wide TileLink beat (c.dataBusSize bytes) into
// c.dataBusSize/c.arrayDim sub-rows of c.arrayDim bytes each, presenting one
// sub-row per cycle to a per-lane FIFO consumer (XFile/YFile).
class BeatUnpacker(implicit c: Configuration) extends Module {
  val rowsPerBeat = c.dataBusSize / c.arrayDim

  val io = IO(new Bundle {
    val beatIn      = Flipped(Decoupled(Vec(c.dataBusSize, UInt(8.W))))
    val subRow      = Output(Vec(c.arrayDim, UInt(8.W)))
    val subRowValid = Output(Bool())
    val subRowReady = Input(Bool())
  })

  val latched = Reg(Vec(rowsPerBeat, Vec(c.arrayDim, UInt(8.W))))
  val subIdx  = RegInit(0.U(log2Ceil(rowsPerBeat).W))
  val busy    = RegInit(false.B)

  io.beatIn.ready := !busy
  io.subRowValid  := busy
  io.subRow       := latched(subIdx)

  when(io.beatIn.fire) {
    latched := VecInit(io.beatIn.bits.grouped(c.arrayDim).toSeq.map(VecInit(_)))
    subIdx  := 0.U
    busy    := true.B
  }.elsewhen(busy && io.subRowReady) {
    when(subIdx === (rowsPerBeat - 1).U) {
      busy   := false.B
      subIdx := 0.U
    }.otherwise {
      subIdx := subIdx + 1.U
    }
  }
}
