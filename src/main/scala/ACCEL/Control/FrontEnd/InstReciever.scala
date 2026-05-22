package ATA8

import chisel3._
import chisel3.experimental._
import chisel3.util._

class InstReciever(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val AXIST = Flipped(new AXIST_2(128, 2, 1, 1, 1))
    val beatStream = Decoupled(new InstBeat)
  })

  io.beatStream.valid    := false.B
  io.beatStream.bits     := DontCare

  io.AXIST.tready := false.B

  when(io.beatStream.ready) {
    io.AXIST.tready := true.B
    when(io.AXIST.tvalid) {
      io.beatStream.valid     := true.B
      io.beatStream.bits.data := io.AXIST.tdata
    }
  }
}
