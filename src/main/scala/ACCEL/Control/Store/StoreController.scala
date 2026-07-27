package ATA8

import chisel3._
import chisel3.experimental._
import chisel3.util._

class StoreController(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val instructionStream = new Readport(new StoreInst)
    val AXIST = new AXIST_2(c.axiStreamWidth, 2, 1, 1, 1)
    val readport = new TilelinkPort(c.tlBus)
    val semaphoreIF = new TilelinkPort(c.tlSemBus)
    val debug = new StoreDebug
  })

  // Number of AXI-Stream words a single TileLink beat must be split into.
  val wordsPerBeat = (c.dataBusSize * 8) / c.axiStreamWidth

  val dmaConf = TLDMAConfig(read = true, write = false, semaphore = true)
  val StoreDMA = Module(new TLDMA(dmaConf, sourceId = 2))

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

  // Splits one dataBusSize*8-bit TileLink beat into wordsPerBeat sequential
  // axiStreamWidth-bit AXI-Stream words.
  val beatWords = Reg(Vec(wordsPerBeat, UInt(c.axiStreamWidth.W)))
  val wordIdx   = RegInit(0.U(log2Ceil(wordsPerBeat + 1).W))
  val haveBeat  = RegInit(false.B)

  val reg = Reg(new StoreInst)
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
    is(2.U) { // Split DMA beat into AXI-Stream words, stream out, wait for completion
      StoreDMA.io.interface.response.ready := true.B

      StoreDMA.io.dataOut.get.ready := !haveBeat

      when(StoreDMA.io.dataOut.get.fire) {
        beatWords := VecInit(Seq.tabulate(wordsPerBeat)(i =>
          StoreDMA.io.dataOut.get.bits((i + 1) * c.axiStreamWidth - 1, i * c.axiStreamWidth)))
        wordIdx  := 0.U
        haveBeat := true.B
      }

      when(haveBeat) {
        io.AXIST.tvalid := true.B
        io.AXIST.tdata  := beatWords(wordIdx)
        io.AXIST.tkeep  := Fill(c.axiStreamWidth / 8, 1.U(1.W))
        io.AXIST.tstrb  := Fill(c.axiStreamWidth / 8, 1.U(1.W))

        when(io.AXIST.tready) {
          when(wordIdx === (wordsPerBeat - 1).U) {
            wordIdx  := 0.U
            haveBeat := false.B
          }.otherwise {
            wordIdx := wordIdx + 1.U
          }
        }
      }

      when(StoreDMA.io.interface.response.fire) {
        io.AXIST.tlast := true.B
        StateReg := 0.U
      }
    }
  }
}
