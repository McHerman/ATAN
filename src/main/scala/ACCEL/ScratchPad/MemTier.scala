package ATA8

import chisel3._
import chisel3.util._

/**
 * Single memory tier with:
 *   - nWritePorts external TileLink write ports (Put only)
 *   - nReadPorts  external TileLink read  ports (Get only)
 *   - nDMAPorts combined TileLink R/W ports (for DMA)
 *   - 1 hostIn combined TileLink R/W port
 *
 * Each RW port is split by opcode into separate read and write TL streams
 * via a [[TLSplitter]].  All write-side streams are arbitrated into a
 * single [[TilelinkWriteHandler]], and all read-side streams into a single
 * [[TilelinkReadHandler]].  The scratchpad sees exactly 1 write port and
 * 1 read port with no additional arbitration.
 */
class MemTier(tier: TierConfig, nDMAPorts: Int)(implicit mc: MemSystemConfig)
    extends Module {

  val io = IO(new Bundle {
    val writePorts = Vec(tier.nWritePorts, Flipped(new TilelinkPort))
    val readPorts  = Vec(tier.nReadPorts,  Flipped(new TilelinkPort))
    val dmaPorts   = Vec(nDMAPorts,        Flipped(new TilelinkPort))
    val hostIn     = Flipped(new TilelinkPort)
  })

  // ── Scratchpad: 1 write port, 1 read port ────────────────────────────────
  val scratchpad = Module(new MemTierScratchpad(tier.nBanks, tier.bankDepth, 1, 1))

  // ── Split each RW port by opcode ─────────────────────────────────────────
  val nRW = nDMAPorts + 1
  val splitters = Seq.fill(nRW)(Module(new TLSplitter))

  io.dmaPorts.zipWithIndex.foreach { case (p, i) => splitters(i).io.in <> p }
  splitters(nDMAPorts).io.in <> io.hostIn

  // ── Write path: all write sources → arbiter → handler → scratchpad ───────
  val nWriteSources = tier.nWritePorts + nRW
  val writeArb = Module(new ScratchWriteArbiter(nWriteSources))

  (io.writePorts ++ splitters.map(_.io.write)).zip(writeArb.io.inPorts).foreach {
    case (src, dst) => dst <> src
  }

  //val writeHandler = Module(new TilelinkWriteHandler)
  val writeHandler = Module(new TLScratchpadHandler(TLScratchConfig(read = false, write = true, atomic = false)))
  writeHandler.io.tl <> writeArb.io.outPort
  scratchpad.io.Writeport(0) <> writeHandler.io.wMem.get

  // ── Read path: all read sources → arbiter → handler → scratchpad ─────────
  val nReadSources = tier.nReadPorts + nRW
  val readArb = Module(new ScratchReadArbiter(nReadSources))

  (io.readPorts ++ splitters.map(_.io.read)).zip(readArb.io.inPorts).foreach {
    case (src, dst) => dst <> src
  }

  //val readHandler = Module(new TilelinkReadHandler)
  val readHandler = Module(new TLScratchpadHandler(TLScratchConfig(read = true, write = false, atomic = false)))
  readHandler.io.tl <> readArb.io.outPort
  scratchpad.io.Readport(0) <> readHandler.io.rMem.get
}

// ═══════════════════════════════════════════════════════════════════════════
// TLSplitter — routes a single RW TileLink port to separate R and W ports
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Inspects the A-channel opcode and routes:
 *   - Get           → [[io.read]]  (A channel forwarded, D channel returned)
 *   - PutFullData   → [[io.write]] (A channel forwarded, D channel returned)
 *
 * Only one path is active per transaction, so the D channel is simply
 * muxed back based on which path was selected.  The splitter locks for
 * the duration of a transaction (multi-beat writes, multi-beat read
 * responses) to prevent interleaving.
 */
class TLSplitter(implicit c: MemBusConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(new TilelinkPort)
    val read  = new TilelinkPort
    val write = new TilelinkPort
  })

  // Defaults — nothing connected
  io.in.a.ready    := false.B
  io.in.d.valid    := false.B
  io.in.d.bits     := DontCare

  io.read.a.valid  := false.B
  io.read.a.bits   := DontCare
  io.read.d.ready  := false.B

  io.write.a.valid := false.B
  io.write.a.bits  := DontCare
  io.write.d.ready := false.B

  val sIdle :: sRead :: sWrite :: Nil = Enum(3)
  val state   = RegInit(sIdle)
  val beatCnt = Reg(UInt(24.W))

  switch(state) {

    is(sIdle) {
      when(io.in.a.valid) {
        switch(io.in.a.bits.opcode) {
          is(TilelinkOpcodes.Get) {
            // Route to read path
            io.read.a.valid := io.in.a.valid
            io.read.a.bits  := io.in.a.bits
            io.in.a.ready   := io.read.a.ready
            when(io.in.a.fire) {
              beatCnt := io.in.a.bits.size - c.dataBusSize.U
              state   := sRead
            }
          }
          is(TilelinkOpcodes.PutFullData) {
            // Route to write path
            io.write.a.valid := io.in.a.valid
            io.write.a.bits  := io.in.a.bits
            io.in.a.ready    := io.write.a.ready
            when(io.in.a.fire) {
              when(io.in.a.bits.size > c.dataBusSize.U) {
                beatCnt := io.in.a.bits.size - (2 * c.dataBusSize).U
                state   := sWrite
              }
            }
          }
          is(TilelinkOpcodes.PutPartialData) {
            assert(false.B, "TLSplitter: PutPartialData not supported")
          }
          is(TilelinkOpcodes.ArithmeticData) {
            assert(false.B, "TLSplitter: ArithmeticData not supported")
          }
        }
      }
      // In idle, also forward any pending D responses from either path
      when(io.write.d.valid) {
        io.in.d <> io.write.d
      }.elsewhen(io.read.d.valid) {
        io.in.d <> io.read.d
      }
    }

    is(sRead) {
      // Forward D beats back to the input
      io.in.d.valid    := io.read.d.valid
      io.in.d.bits     := io.read.d.bits
      io.read.d.ready  := io.in.d.ready
      when(io.in.d.fire) {
        beatCnt := beatCnt - c.dataBusSize.U
        when(beatCnt === 0.U) {
          state := sIdle
        }
      }
    }

    is(sWrite) {
      // Forward remaining A beats to write path
      io.write.a.valid := io.in.a.valid
      io.write.a.bits  := io.in.a.bits
      io.in.a.ready    := io.write.a.ready
      when(io.in.a.fire) {
        beatCnt := beatCnt - c.dataBusSize.U
        when(beatCnt === 0.U) {
          state := sIdle
        }
      }
      // Forward D ack back
      when(io.write.d.valid) {
        io.in.d.valid    := io.write.d.valid
        io.in.d.bits     := io.write.d.bits
        io.write.d.ready := io.in.d.ready
      }
    }
  }
}

// ═══════════════════════════════════════════════════════════════════════════
// Scratchpad
// ═══════════════════════════════════════════════════════════════════════════

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
      Vec(c.dataBusSize, UInt(c.arithDataWidth.W)), Some(16))))
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
    val bankIdx  = port.request.bits.addr.get(log2Ceil(nBanks) - 1, 0)
    val bankAddr = port.request.bits.addr.get >> log2Ceil(nBanks)
    val readResults = VecInit(memBanks.map(_.read(bankAddr, port.request.fire)))
    val bankIdxReg  = RegNext(bankIdx)
    port.response.bits.readData :=
      readResults(bankIdxReg).asTypeOf(Vec(c.dataBusSize, UInt(c.arithDataWidth.W)))
    port.response.valid := RegNext(port.request.fire)
  }
}
