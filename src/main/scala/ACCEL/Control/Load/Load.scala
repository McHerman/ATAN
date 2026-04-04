package ATA8

import chisel3._
import chisel3.experimental._
import chisel3.util._

class Load(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val instructionStream = Flipped(Decoupled(new LoadInstIssue))

    val AXIST      = Flipped(new AXIST_2(64, 2, 1, 1, 1))
    val scratchOut = new TilelinkPort

    val semaphoreIF = new TilelinkPort

    val debug = new LoadDebug
  })

  val queue          = Module(new BufferFIFO(32, new LoadInstIssue))
  val LoadController = Module(new LoadController)

  queue.io.WriteData <> io.instructionStream


  LoadController.io.instructionStream <> queue.io.ReadData
  LoadController.io.AXIST             <> io.AXIST

  io.scratchOut    <> LoadController.io.writeport
  io.semaphoreIF   <> LoadController.io.semaphoreIF

  /// DEBUG ///

  LoadController.io.debug <> io.debug
}
