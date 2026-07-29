package ATA8

import chisel3._
import chisel3.util._

// Deserializes one wide TileLink beat (c.dataBusSize bytes) into sub-rows for matrix inputs smaller than full bus
class BeatUnpacker(implicit c: Configuration) extends Module {
  require(c.dataBusSize % c.arrayDim == 0,
    s"BeatUnpacker requires dataBusSize (${c.dataBusSize}) to be a whole multiple of " +
    s"arrayDim (${c.arrayDim}) so a beat always holds a whole number of full-width rows.")

  val io = IO(new Bundle {
    val beatIn      = Flipped(Decoupled(Vec(c.dataBusSize, UInt(8.W))))
    val size        = Input(UInt(log2Ceil(c.arrayDim + 1).W))
    val subRow      = Output(Vec(c.arrayDim, UInt(8.W)))
    val subRowValid = Output(Bool())
    val subRowReady = Input(Bool())
  })

  val latched    = Reg(Vec(c.dataBusSize, UInt(8.W)))
  val byteOffset = RegInit(0.U(log2Ceil(c.dataBusSize + 1).W))
  val busy       = RegInit(false.B)

  val effSize = Mux(io.size === 0.U, c.arrayDim.U, io.size)

  io.beatIn.ready := !busy
  io.subRowValid  := busy

  for (i <- 0 until c.arrayDim) {
    val srcIdx = byteOffset +& i.U
    io.subRow(i) := Mux(i.U < effSize && srcIdx < c.dataBusSize.U, latched(srcIdx), 0.U)
  }

  when(io.beatIn.fire) {
    latched    := io.beatIn.bits
    byteOffset := 0.U
    busy       := true.B
  }.elsewhen(busy && io.subRowReady) {
    val next = byteOffset +& effSize
    when(next >= c.dataBusSize.U) {
      busy       := false.B
      byteOffset := 0.U
      assert(next == c.dataBusSize.U, "unpacker underflow")
    }.otherwise {
      byteOffset := next
    }
  }
}
