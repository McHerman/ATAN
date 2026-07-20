package ATA8

import chisel3._
import chisel3.util._

class ScratchpadWrapper(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val WritePorts = Vec(1 + c.grainDim, Flipped(new TilelinkPort(c.tlBus)))
    val ReadPorts = Vec(c.bufferReadPorts, Flipped(new TilelinkPort(c.tlBus)))
  })

  val WriteArbiter = Module(new ScratchWriteArbiter(1 + c.grainDim))
  val Scratchpad = Module(new Scratchpad(1, c.bufferReadPorts))

  val ReadBurstHandlerVec = Seq.fill(c.bufferReadPorts)(Module(new TilelinkReadHandler))
  val WriteBurstHandlerVec = Seq.fill(1)(Module(new TilelinkWriteHandler))

  WriteArbiter.io.inPorts <> io.WritePorts

  WriteArbiter.io.outPort <> WriteBurstHandlerVec(0).io.tl
  Scratchpad.io.Writeport(0) <> WriteBurstHandlerVec(0).io.mem

  io.ReadPorts.zipWithIndex.foreach { case (port, i) =>
    port <> ReadBurstHandlerVec(i).io.tl
    Scratchpad.io.Readport(i) <> ReadBurstHandlerVec(i).io.mem
  }

  // Simulation-only protocol checkers (device-side)
  io.WritePorts.zipWithIndex.foreach { case (port, i) =>
    TilelinkProtocolChecker(port, "Device", s"W$i")
  }
  io.ReadPorts.zipWithIndex.foreach { case (port, i) =>
    TilelinkProtocolChecker(port, "Device", s"R$i")
  }
}
