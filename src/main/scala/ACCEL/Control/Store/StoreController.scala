package ATA8

import chisel3._
import chisel3.experimental._
import chisel3.util._

class StoreController(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val instructionStream = new Readport(new StoreInstIssue)
    val AXIST = new AXIST_2(64, 2, 1, 1, 1)
    val readport = new TilelinkPort
    val semaphoreIF = new TilelinkPort
    val debug = new StoreDebug
  })

  val dmaConf = TLDMAConfig(read = true, write = false, semaphore = true)
  val StoreDMA = Module(new TLDMA(dmaConf))

  io.instructionStream.request.valid := false.B
  io.instructionStream.request.bits := DontCare

  StoreDMA.io.tl <> io.readport
  StoreDMA.io.semaphoreIF.get <> io.semaphoreIF

  io.AXIST := DontCare
  io.AXIST.tvalid := false.B
  io.AXIST.tlast := false.B

  StoreDMA.io.interface.descriptor.valid := false.B
  StoreDMA.io.interface.descriptor.bits := DontCare
  StoreDMA.io.interface.response.ready := false.B

  StoreDMA.io.dataOut.get.ready := false.B

  val reg = Reg(new StoreInstIssue)
  val StateReg = RegInit(0.U(4.W))

  /// DEBUG ///
  io.debug.state := StateReg
  io.debug.axiReady := io.AXIST.tready
  io.debug.readPortValid := io.readport.d.valid

  switch(StateReg) {
    is(0.U) { // Fetch instruction
      when(io.instructionStream.request.ready) {
        io.instructionStream.request.valid := true.B

        when(io.instructionStream.response.valid) {
          reg := io.instructionStream.response.bits.readData
          StateReg := 1.U
        }
      }
    }
    is(1.U) { // Submit DMA descriptor
      when(StoreDMA.io.interface.descriptor.ready) {
        StoreDMA.io.interface.descriptor.valid := true.B

        StoreDMA.io.interface.descriptor.bits(0).addr := reg.addrs(0).addr
        StoreDMA.io.interface.descriptor.bits(0).size := reg.size
        StoreDMA.io.interface.descriptor.bits(0).writeEn := false.B
        StoreDMA.io.interface.descriptor.bits(0).source := 0.U
        StoreDMA.io.interface.descriptor.bits(0).sink := 0.U

        StoreDMA.io.interface.descriptor.bits(0).semaphore.get.semEnable := reg.addrs(0).sem.valid
        StoreDMA.io.interface.descriptor.bits(0).semaphore.get.mode := SemaphoreAccessModes.RestartOnStep

        StoreDMA.io.interface.descriptor.bits(0).semaphore.get.semAddr := reg.addrs(0).sem.bits.addr
        StoreDMA.io.interface.descriptor.bits(0).semaphore.get.semStepSize := reg.addrs(0).sem.bits.stepSize.bits

        StateReg := 2.U
      }
    }
    is(2.U) { // Stream DMA data out over AXIST, wait for completion
      StoreDMA.io.interface.response.ready := true.B

      StoreDMA.io.dataOut.get.ready := io.AXIST.tready

      when(StoreDMA.io.dataOut.get.valid) {
        io.AXIST.tvalid := true.B
        io.AXIST.tdata := StoreDMA.io.dataOut.get.bits
        io.AXIST.tkeep := "hff".U
        io.AXIST.tstrb := "hff".U
      }

      when(StoreDMA.io.interface.response.fire) {
        io.AXIST.tlast := true.B
        StateReg := 0.U
      }
    }
  }
}
