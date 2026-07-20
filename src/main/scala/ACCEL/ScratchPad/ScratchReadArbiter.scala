package ATA8

import chisel3._
import chisel3.util._

class ScratchReadArbiter(numPorts: Int)(implicit c: MemBusConfig) extends Module {
  val io = IO(new Bundle {
    val inPorts = Vec(numPorts, Flipped(new TilelinkPort(c.tlBus)))
    val outPort = new TilelinkPort(c.tlBus)
  })

  // Initialize all ports
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
  val beatCnt = Reg(UInt(24.W))
  val totalBeats = Reg(UInt(24.W))

  // Round-robin increment
  when(!isLocked) {
    roundRobin := roundRobin + 1.U
  }

  // Arbitration logic - connect selected port when unlocked
  io.inPorts.zipWithIndex.foreach { case (port, index) =>
    when((index.U === roundRobin) && !isLocked) {
      port <> io.outPort
    }
  }

  when(io.outPort.a.fire && !isLocked) {
    isLocked := true.B
    activePort := roundRobin
    beatCnt := 0.U
    totalBeats := io.outPort.a.bits.size
  }

  // Data transfer when locked
  when(isLocked) {
    io.inPorts(activePort) <> io.outPort

    // Count D bytes to unlock
    when(io.outPort.d.fire) {
      beatCnt := beatCnt + c.dataBusSize.U
      when(beatCnt === (totalBeats - c.dataBusSize.U)) {
        isLocked := false.B
      }
    }
  }
}
