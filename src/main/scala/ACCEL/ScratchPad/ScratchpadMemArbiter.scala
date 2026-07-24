package ATA8

import chisel3._
import chisel3.util._

/**
 * Fixed-priority N-port arbiter for the scratchpad's single read/write port pair.
 *
 * Port 0 has highest priority on both dimensions when ports contend simultaneously.
 */
class ScratchpadMemArbiter(nPorts: Int)(implicit c: MemBusConfig) extends Module {

  val io = IO(new Bundle {
    val wMem = Decoupled(new Writeport(new Bundle {
      val writeData = Vec(c.dataBusSize, UInt(8.W))
      val strb      = Vec(c.dataBusSize, Bool())
    }, 16))
    val rMem = new Readport(Vec(c.dataBusSize, UInt(c.arithDataWidth.W)), Some(16))

    val wPorts = Vec(nPorts, Flipped(Decoupled(new Writeport(new Bundle {
      val writeData = Vec(c.dataBusSize, UInt(8.W))
      val strb      = Vec(c.dataBusSize, Bool())
    }, 16))))
    val rPorts = Vec(nPorts, Flipped(new Readport(Vec(c.dataBusSize, UInt(c.arithDataWidth.W)), Some(16))))
  })

  io.wMem.valid := false.B
  io.wMem.bits  := DontCare
  io.wPorts.foreach(_.ready := false.B)

  io.rMem.request.valid := false.B
  io.rMem.request.bits  := DontCare
  io.rPorts.foreach { p =>
    p.request.ready          := false.B
    p.response.valid         := false.B
    p.response.bits.readData := DontCare
  }

  // ── Write: fixed priority (0 highest) ────────────────────────────────────
  val wGrant = WireDefault(nPorts.U(log2Ceil(nPorts + 1).W))
  for (i <- (nPorts - 1) to 0 by -1) {
    when(io.wPorts(i).valid) { wGrant := i.U }
  }
  when(wGrant < nPorts.U) {
    io.wMem.valid := true.B
  }
  for (i <- 0 until nPorts) {
    when(wGrant === i.U) {
      io.wMem.bits       := io.wPorts(i).bits
      io.wPorts(i).ready := io.wMem.ready
    }
  }

  // ── Read: fixed priority (0 highest); grant register steers response ──────
  val readGrant = RegInit(0.U(log2Ceil(nPorts).W))

  val rGrant = WireDefault(nPorts.U(log2Ceil(nPorts + 1).W))
  for (i <- (nPorts - 1) to 0 by -1) {
    when(io.rPorts(i).request.valid) { rGrant := i.U }
  }
  when(rGrant < nPorts.U) {
    io.rMem.request.valid := true.B
  }
  for (i <- 0 until nPorts) {
    when(rGrant === i.U) {
      io.rMem.request.bits.addr.get := io.rPorts(i).request.bits.addr.get
      io.rPorts(i).request.ready    := io.rMem.request.ready
      when(io.rMem.request.fire) { readGrant := i.U }
    }
  }

  for (i <- 0 until nPorts) {
    when(readGrant === i.U) {
      io.rPorts(i).response.valid         := io.rMem.response.valid
      io.rPorts(i).response.bits.readData := io.rMem.response.bits.readData
    }
  }
}
