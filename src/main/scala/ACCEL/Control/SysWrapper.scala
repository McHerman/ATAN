package ATA8

import chisel3._
import chisel3.util._

class SysWrapper(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val in         = new Readport(new ExecuteInst)
    val scratchOut = Vec(c.grainDim, new TilelinkPort(c.tlBus))
    val scratchIn  = Vec(2, Vec(c.grainDim, new TilelinkPort(c.tlBus)))

    val writeSemaphoreIF = Vec(c.grainDim, new TilelinkPort(c.tlSemBus))
    val readSemaphoreIF = Vec(2, Vec(c.grainDim, new TilelinkPort(c.tlSemBus)))

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
  SysGrain.io.loadSizes := SysController.io.loadSizes

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
  // The beatpacker is inserted to pack sub-bus sized output into a single bus write beat
  val BeatPackers = Seq.fill(c.grainDim)(Module(new BeatPacker()))
  (BeatPackers zip SysGrain.io.readPort).foreach { case (packer, port) =>
    packer.io.rowPort <> port
  }
  (BeatPackers zip SysGrain.io.sizes).foreach { case (packer, size) =>
    packer.io.size := size
  }
  (BeatPackers zip WriteDMA).foreach { case (packer, dma) =>
    packer.io.beatPort <> dma.io.dataIn.get
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
