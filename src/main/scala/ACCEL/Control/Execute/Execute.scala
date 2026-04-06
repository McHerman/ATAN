package ATA8

import chisel3._
import chisel3.experimental._
import chisel3.util._

class Execute(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val instructionStream = Flipped(Decoupled(new ExecuteInst))

    val scratchOut = Vec(c.grainDim, new TilelinkPort)
    val scratchIn  = Vec(2, new TilelinkPort)

    val semaphoreIF = Flipped(new TilelinkPort)

    val debug = new ExeDebug
  })

  val queue      = Module(new BufferFIFO(32, new ExecuteInst))
  val SysWrapper = Module(new SysWrapper)

  queue.io.WriteData <> io.instructionStream
  SysWrapper.io.in  <> queue.io.ReadData

  /// SCRATCHPAD CONNECTIONS ///

  if (c.grainDim != 1) {
    val ReadArbiter = Seq.fill(2)(Module(new ScratchReadArbiter(c.grainDim)))

    io.scratchOut <> SysWrapper.io.scratchOut

    ReadArbiter(0).io.inPorts <> SysWrapper.io.scratchIn(0)
    ReadArbiter(1).io.inPorts <> SysWrapper.io.scratchIn(1)

    io.scratchIn(0) <> ReadArbiter(0).io.outPort
    io.scratchIn(1) <> ReadArbiter(1).io.outPort
  } else {
    SysWrapper.io.scratchOut <> io.scratchOut

    SysWrapper.io.scratchIn(0)(0) <> io.scratchIn(0)
    SysWrapper.io.scratchIn(1)(0) <> io.scratchIn(1)
  }

  /// DEBUG ///

  SysWrapper.io.debug <> io.debug
}
