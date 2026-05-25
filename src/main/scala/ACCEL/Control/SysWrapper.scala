package ATA8

import chisel3._
import chisel3.util._

class SysWrapper(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val in         = new Readport(new ExecuteInst)
    val scratchOut = Vec(c.grainDim, new TilelinkPort)
    val scratchIn  = Vec(2, Vec(c.grainDim, new TilelinkPort))

    val writeSemaphoreIF = Vec(c.grainDim, new TilelinkPort)
    val readSemaphoreIF = Vec(2, Vec(c.grainDim, new TilelinkPort))

    val debug      = new ExeDebug
  })

  val SysController = Module(new SysController())
  val SysGrain      = Module(new Grain())

  //val DMA      = Seq.fill(2, c.grainDim)(Module(new SysDMA()))
  //val WriteDMA = Seq.fill(c.grainDim)(Module(new SysWriteDMA()))

  val dmaReadConf = TLDMAConfig(read = true, write = false, semaphore = true)
  val ReadDMA = Seq.tabulate(2, c.grainDim) { (i, j) =>
    Module(new TLDMA(dmaReadConf, sourceId = 3 + i * c.grainDim + j))
  }

  val dmaWriteConf = TLDMAConfig(read = false, write = true, semaphore = true)
  val WriteDMA = Seq.tabulate(c.grainDim) { j =>
    Module(new TLDMA(dmaWriteConf, sourceId = 3 + 2 * c.grainDim + j))
  }


  SysController.io.in  <> io.in
  SysController.io.out <> SysGrain.io.in

  // ReadDMA dataOut -> Grain writePort (Vec[UInt] <-> flat UInt conversion)
  (SysGrain.io.writePort zip ReadDMA).foreach { case (portVec, dmaVec) =>
    (portVec zip dmaVec).foreach { case (port, dma) =>
      val dataOut = dma.io.dataOut.get
      port.valid     := dataOut.valid
      dataOut.ready  := port.ready
      port.bits      := dataOut.bits.asTypeOf(port.bits)
    }
  }

  // ReadDMA TileLink ports
  (io.scratchIn.flatten zip ReadDMA.flatten).foreach { case (port, dma) =>
    dma.io.tl <> port
  }

  (SysController.io.dmaRead.flatten zip ReadDMA.flatten).foreach { case (command, dma) =>
    command <> dma.io.interface
  }

  // WriteDMA TileLink ports
  (io.scratchOut zip WriteDMA).foreach { case (port, dma) =>
    dma.io.tl <> port
  }

  (SysController.io.dmaWrite zip WriteDMA).foreach { case (command, dma) =>
    dma.io.interface <> command
  }

  // Grain readPort -> WriteDMA dataIn (Vec[UInt] <-> flat UInt conversion)
  (SysGrain.io.readPort zip WriteDMA).foreach { case (port, dma) =>
    val dataIn = dma.io.dataIn.get
    port.request.valid          := dataIn.request.valid
    dataIn.request.ready        := port.request.ready
    dataIn.response.valid       := port.response.valid
    dataIn.response.bits.readData := port.response.bits.readData.asTypeOf(dataIn.response.bits.readData)
  }

  // Semaphore interfaces
  (io.writeSemaphoreIF zip WriteDMA).foreach { case (port, dma) =>
    dma.io.semaphoreIF.get <> port
  }

  (io.readSemaphoreIF.flatten zip ReadDMA.flatten).foreach { case (port, dma) =>
    dma.io.semaphoreIF.get <> port
  }

  SysController.io.sysCompleted := SysGrain.io.completed

  /// DEBUG ///

  SysController.io.debug <> io.debug
}
