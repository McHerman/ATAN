package ATA8

import chisel3._
import chisel3.util._

/**
 * Single memory tier: a parameterized scratchpad with:
 *   - nWritePorts external TileLink write ports (Put only, via write arbiter)
 *   - nReadPorts  external TileLink read  ports (Get only)
 *   - nDMAPorts   combined TileLink R/W ports   (for MemDMA, both Get and Put)
 *
 * Scratchpad SRAM dimensions are taken from [[TierConfig.nBanks]] and
 * [[TierConfig.bankDepth]].
 */
class MemTier(tier: TierConfig, nDMAPorts: Int)(implicit mc: MemSystemConfig)
    extends Module {

  val io = IO(new Bundle {
    val writePorts = Vec(tier.nWritePorts, Flipped(new TilelinkPort))
    val readPorts  = Vec(tier.nReadPorts,  Flipped(new TilelinkPort))
    val dmaPorts   = Vec(nDMAPorts,        Flipped(new TilelinkPort))
  })

  // Total scratchpad ports
  val totalWritePorts = tier.nWritePorts + nDMAPorts
  val totalReadPorts  = tier.nReadPorts  + nDMAPorts

  val scratchpad = Module(new MemTierScratchpad(tier.nBanks, tier.bankDepth,
                                                totalWritePorts, totalReadPorts))

  // ── External write ports (Put only) via write arbiter ────────────────────
  val writeArb = Module(new ScratchWriteArbiter(tier.nWritePorts))
  writeArb.io.inPorts <> io.writePorts
  val writeHandler = Module(new TilelinkWriteHandler)
  writeHandler.io.tl <> writeArb.io.outPort
  scratchpad.io.Writeport(0) <> writeHandler.io.mem

  // ── External read ports (Get only) ───────────────────────────────────────
  val readHandlers = Seq.fill(tier.nReadPorts)(Module(new TilelinkReadHandler))
  readHandlers.zipWithIndex.foreach { case (h, i) =>
    h.io.tl                   <> io.readPorts(i)
    scratchpad.io.Readport(i) <> h.io.mem
  }

  // ── DMA ports (combined Get/Put) ─────────────────────────────────────────
  val rwHandlers = Seq.fill(nDMAPorts)(Module(new TilelinkRWHandler))
  rwHandlers.zipWithIndex.foreach { case (h, i) =>
    h.io.tl <> io.dmaPorts(i)
    // Write side occupies scratchpad write slot (1 + i)
    scratchpad.io.Writeport(1 + i)          <> h.io.wMem
    // Read side occupies scratchpad read slot (nReadPorts + i)
    scratchpad.io.Readport(tier.nReadPorts + i) <> h.io.rMem
  }
}

/**
 * Raw SRAM scratchpad with configurable bank count, bank depth, and port
 * counts.  Used exclusively by [[MemTier]].
 */
class MemTierScratchpad(
  nBanks:      Int,
  bankDepth:   Int,
  writeports:  Int,
  readports:   Int
)(implicit c: MemBusConfig) extends Module {

  val io = IO(new Bundle {
    val Writeport = Vec(writeports, Flipped(Decoupled(new Writeport(
      new Bundle {
        val writeData = Vec(c.dataBusSize, UInt(8.W))
        val strb      = Vec(c.dataBusSize, Bool())
      }, 16))))
    val Readport = Vec(readports, Flipped(new Readport(
      Vec(c.dataBusSize, UInt(c.arithDataWidth.W)), 16)))
  })

  val memBanks = Seq.fill(nBanks)(SyncReadMem(bankDepth, UInt((c.dataBusSize * 8).W)))

  // Write logic
  io.Writeport.foreach { port =>
    port.ready := true.B
    val bankIdx  = port.bits.addr(log2Ceil(nBanks) - 1, 0)
    val bankAddr = port.bits.addr >> log2Ceil(nBanks)
    memBanks.zipWithIndex.foreach { case (mem, i) =>
      when(port.fire && bankIdx === i.U) {
        mem.write(bankAddr, port.bits.data.writeData.asUInt)
      }
    }
  }

  // Read logic
  io.Readport.foreach { port =>
    port.request.ready := true.B
    val bankIdx  = port.request.bits.addr(log2Ceil(nBanks) - 1, 0)
    val bankAddr = port.request.bits.addr >> log2Ceil(nBanks)
    val readResults = VecInit(memBanks.map(_.read(bankAddr, port.request.fire)))
    val bankIdxReg  = RegNext(bankIdx)
    port.response.bits.readData :=
      readResults(bankIdxReg).asTypeOf(Vec(c.dataBusSize, UInt(c.arithDataWidth.W)))
    port.response.valid := RegNext(port.request.fire)
  }
}
