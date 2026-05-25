package ATA8

import chisel3._
import chisel3.experimental._
import chisel3.util._

class LoadController(implicit c: Configuration) extends Module {
  def splitInt(input: UInt, totalWidth: Int, subWidth: Int): Seq[UInt] = {
    require(totalWidth % subWidth == 0, "Total width must be a multiple of sub width")
    val numSplits = totalWidth / subWidth
    (0 until numSplits).map(i => input(subWidth * (i + 1) - 1, subWidth * i))
  }

  val io = IO(new Bundle {
    val instructionStream = new Readport(new LoadInst)
    val AXIST             = Flipped(new AXIST_2(64, 2, 1, 1, 1))
    val writeport         = new TilelinkPort
    val semaphoreIF       = new TilelinkPort
    val debug             = new LoadDebug
  })


  val dmaConf = TLDMAConfig(read = false, write = true, semaphore = true)
  val ReadDMA = Module(new TLDMA(dmaConf, sourceId = 1))


  io.instructionStream.request.valid := false.B
  io.instructionStream.request.bits  := DontCare

  ReadDMA.io.tl <> io.writeport
  ReadDMA.io.semaphoreIF.get <> io.semaphoreIF

  // FIFO between AXIST and DMA dataIn so the AXIST handshake doesn't see
  // tl.a.ready combinationally through the TileLink xbar.
  class FifoBeat extends Bundle {
    val data = UInt(64.W)
    val last = Bool()
  }
  val fifo = Module(new Queue(new FifoBeat, entries = 4, pipe = false, flow = false))

  fifo.io.enq.valid     := io.AXIST.tvalid
  fifo.io.enq.bits.data := io.AXIST.tdata
  fifo.io.enq.bits.last := io.AXIST.tlast
  io.AXIST.tready       := fifo.io.enq.ready

  fifo.io.deq.ready := false.B
 
  ReadDMA.io.interface.descriptor.valid := false.B
  ReadDMA.io.interface.descriptor.bits := DontCare
  ReadDMA.io.interface.response.ready := false.B

  ReadDMA.io.dataIn.get.request.ready := false.B
  ReadDMA.io.dataIn.get.response.valid := false.B
  ReadDMA.io.dataIn.get.response.bits.readData := DontCare

  val reg           = Reg(new LoadInst)
  val StateReg      = RegInit(0.U(4.W))

  /// DEBUG ///
  io.debug.state := StateReg

  switch(StateReg) {
    is(0.U) { // Fetch instruction

     io.instructionStream.request.valid := true.B


      when(io.instructionStream.request.fire) {
        when(io.instructionStream.response.valid) {
          reg      := io.instructionStream.response.bits.readData
          StateReg := 1.U
        }
      }
    }
    is(1.U) { // Submit DMA descriptor
      when(ReadDMA.io.interface.descriptor.ready) {
        ReadDMA.io.interface.descriptor.valid := true.B

        ReadDMA.io.interface.descriptor.bits(0).addr := reg.addrd(0).addr
        ReadDMA.io.interface.descriptor.bits(0).size := reg.size
        ReadDMA.io.interface.descriptor.bits(0).writeEn := true.B
        ReadDMA.io.interface.descriptor.bits(0).source := 0.U
        ReadDMA.io.interface.descriptor.bits(0).sink := 0.U

        ReadDMA.io.interface.descriptor.bits(0).semaphore.get.semEnable := reg.addrd(0).sem.valid
        ReadDMA.io.interface.descriptor.bits(0).semaphore.get.mode := SemaphoreAccessModes.RestartOnStep

        ReadDMA.io.interface.descriptor.bits(0).semaphore.get.semAddr := reg.addrd(0).sem.bits.addr
        ReadDMA.io.interface.descriptor.bits(0).semaphore.get.semStepSize := reg.addrd(0).sem.bits.stepSize.bits

        StateReg := 2.U
      }
    }
    is(2.U) { // Drain FIFO into DMA, wait for completion
      ReadDMA.io.dataIn.get.request.ready          := fifo.io.deq.valid
      fifo.io.deq.ready                            := ReadDMA.io.dataIn.get.request.valid
      ReadDMA.io.dataIn.get.response.valid         := fifo.io.deq.valid
      ReadDMA.io.dataIn.get.response.bits.readData := fifo.io.deq.bits.data

      ReadDMA.io.interface.response.ready := true.B

      when(ReadDMA.io.interface.response.fire) {
        StateReg := 0.U
      }
    }
  }
}
