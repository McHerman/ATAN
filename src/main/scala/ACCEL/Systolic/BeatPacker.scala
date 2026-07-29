package ATA8

import chisel3._
import chisel3.util._

// Iteratively builds full bus size transfers out of smaller systolic array output.
class BeatPacker(implicit c: Configuration) extends Module {

  require(c.dataBusSize == c.arrayDim * c.accDataBytes,
    s"BeatPacker requires dataBusSize (${c.dataBusSize}) to exactly equal " +
    s"arrayDim*accDataBytes (${c.arrayDim * c.accDataBytes}) so one full-width row is one beat.")

  val io = IO(new Bundle {
    val size     = Input(UInt(log2Ceil(c.arrayDim + 1).W))
    val rowPort  = new Readport(Vec(c.arrayDim, UInt(c.accDataWidth.W))) // Input from systolic array accumulation buffers 
    val beatPort = Flipped(new Readport(UInt((c.dataBusSize * 8).W))) //Output to DMA
  })

  val buffer     = Reg(Vec(c.dataBusSize, UInt(8.W)))
  val byteOffset = RegInit(0.U(log2Ceil(c.dataBusSize + 1).W))
  val beatReady  = RegInit(false.B)

  val started = RegInit(false.B)
  when(io.beatPort.request.valid) { started := true.B }

  io.rowPort.request.valid := started && !beatReady
  io.rowPort.request.bits  := DontCare

  io.beatPort.request.ready          := beatReady
  io.beatPort.response.valid         := beatReady
  io.beatPort.response.bits.readData := buffer.asUInt

  val realBytes  = io.size * c.accDataBytes.U
  val rowAsBytes = io.rowPort.response.bits.readData.asTypeOf(Vec(c.arrayDim * c.accDataBytes, UInt(8.W)))

  when(io.rowPort.response.valid && !beatReady) {
    for (j <- 0 until c.arrayDim * c.accDataBytes) {
      val dstIdx = byteOffset +& j.U
      when(j.U < realBytes && dstIdx < c.dataBusSize.U) {
        buffer(dstIdx) := rowAsBytes(j)
      }
    }

    val next = byteOffset +& realBytes
    when(next >= c.dataBusSize.U) {
      beatReady := true.B
      byteOffset := 0.U

      assert(next == c.dataBusSize.U, "beatpacker overflow")
    }.otherwise {
      byteOffset := next
    }
  }

  when(io.beatPort.request.fire) {
    beatReady := false.B
  }
}
