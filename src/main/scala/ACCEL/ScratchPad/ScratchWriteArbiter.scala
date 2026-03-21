package ATA8

import chisel3._
import chisel3.util._

class ScratchWriteArbiter(numPorts: Int)(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val inPorts = Vec(numPorts, Flipped(new TilelinkPort))
    val outPort = new TilelinkPort
  })

  io.inPorts.foreach { port =>
    port.a.ready := false.B
    port.d.valid := false.B
    port.d.bits := DontCare
  }
  io.outPort.a.valid := false.B
  io.outPort.a.bits := DontCare
  io.outPort.d.ready := false.B

  val activePort = RegInit(0.U(log2Ceil(numPorts).W))
  val isLocked = RegInit(false.B)
  val roundRobin = RegInit(0.U(log2Ceil(numPorts).W))

  when(!isLocked) {
    roundRobin := roundRobin + 1.U
  }

  io.inPorts.zipWithIndex.foreach { case (port, index) =>
    when((index.U === roundRobin) && !isLocked) {
      port <> io.outPort
    }
  }

  when(io.outPort.a.fire && !isLocked) {
    isLocked := true.B
    activePort := roundRobin
  }

  when(isLocked) {
    io.outPort <> io.inPorts(activePort)
  }

  // Unlock when D-channel AccessAck fires
  when(isLocked && io.outPort.d.fire) {
    isLocked := false.B
  }
}
