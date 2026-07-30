package ATA8

import chisel3._
import chisel3.experimental._
import chisel3.util._

class Store(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val instructionStream = Flipped(Decoupled(new StoreInst))

    val AXIST    = new AXIST_2(c.axiStreamWidth, 2, 1, 1, 1)
    val readPort = new TilelinkPort(c.tlBus)
    val semaphoreIF = new TilelinkPort(c.tlSemBus)

    val debug = new StoreDebug
  })

  val queue           = Module(new BufferFIFO(32, new StoreInst))
  val StoreController = Module(new StoreController)

  queue.io.WriteData <> io.instructionStream

  StoreController.io.instructionStream <> queue.io.ReadData
  StoreController.io.AXIST             <> io.AXIST

  io.readPort     <> StoreController.io.readport
  io.semaphoreIF  <> StoreController.io.semaphoreIF

  /// DEBUG ///

  StoreController.io.debug <> io.debug
}
