package ATA8

import chisel3._
import chisel3.experimental._
import chisel3.util._

class Store(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val instructionStream = Flipped(Decoupled(new StoreInstIssue))

    val AXIST    = new AXIST_2(64, 2, 1, 1, 1)
    val readPort = new TilelinkPort
    val semaphoreIF = new TilelinkPort

    val debug = new StoreDebug
  })

  val queue           = Module(new BufferFIFO(32, new StoreInstIssue))
  val StoreController = Module(new StoreController)

  queue.io.WriteData <> io.instructionStream

  StoreController.io.instructionStream <> queue.io.ReadData
  StoreController.io.AXIST             <> io.AXIST

  io.readPort     <> StoreController.io.readport
  io.semaphoreIF  <> StoreController.io.semaphoreIF

  /// DEBUG ///

  StoreController.io.debug <> io.debug
}
