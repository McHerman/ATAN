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
class MemTier(tier: TierConfig, nRwPorts: Int)(implicit mc: MemSystemConfig)
    extends Module {

  val io = IO(new Bundle {
    val writePorts = Vec(tier.nWritePorts, Flipped(new TilelinkPort(mc.tlBus)))
    val readPorts  = Vec(tier.nReadPorts,  Flipped(new TilelinkPort(mc.tlBus)))
    val rwPorts    = Vec(nRwPorts,        Flipped(new TilelinkPort(mc.tlBus)))
    val hostIn     = Flipped(new TilelinkPort(mc.tlBus))
  })

  // ── Scratchpad: 1 write port, 1 read port ────────────────────────────────


  val spmConfig = SPMConfig(bankDepth = tier.bankDepth,
                            writeports = 1,
                            readports = 1)

  val scratchpad = Module(new MemTierScratchpad(spmConfig))


  // ── Split each RW port by opcode ─────────────────────────────────────────
  val rwSplitters  = Seq.fill(nRwPorts)(Module(new TLSplitter))
  val hostSplitter = Module(new TLSplitter(atomic = tier.atomic))
  io.rwPorts.zip(rwSplitters).foreach { case (p, s) => s.io.in <> p }
  hostSplitter.io.in <> io.hostIn
  val allSplitters = rwSplitters :+ hostSplitter

  // ── Write path: all write sources → arbiter → handler → scratchpad ───────
  val nWriteSources = tier.nWritePorts + allSplitters.size
  val writeArb = Module(new ScratchWriteArbiter(nWriteSources))
  (io.writePorts ++ allSplitters.map(_.io.write)).zip(writeArb.io.inPorts).foreach {
    case (src, dst) => dst <> src
  }
  val writeHandler = Module(new TLScratchpadHandler(TLScratchConfig(read = false, write = true, atomic = false)))
  writeHandler.io.tl <> writeArb.io.outPort

  // ── Read path: all read sources → arbiter → handler → scratchpad ─────────
  val nReadSources = tier.nReadPorts + allSplitters.size
  val readArb = Module(new ScratchReadArbiter(nReadSources))
  (io.readPorts ++ allSplitters.map(_.io.read)).zip(readArb.io.inPorts).foreach {
    case (src, dst) => dst <> src
  }
  val readHandler = Module(new TLScratchpadHandler(TLScratchConfig(read = true, write = false, atomic = false)))
  readHandler.io.tl <> readArb.io.outPort

  // ── Scratchpad connections (with or without atomic handler) ───────────────
  if (tier.atomic) {
    val atomicHandler = Module(new TLScratchpadHandler(
      TLScratchConfig(read = true, write = true, atomic = true)
    ))
    atomicHandler.io.tl              <> hostSplitter.io.amo.get
    atomicHandler.io.amoReserve.get.ready   := true.B
    atomicHandler.io.reserveIn.get(0).valid := false.B
    atomicHandler.io.reserveIn.get(0).bits  := DontCare

    val memArb = Module(new ScratchpadMemArbiter)
    memArb.io.wPorts(0) <> writeHandler.io.wMem.get
    memArb.io.wPorts(1) <> atomicHandler.io.wMem.get
    memArb.io.rPorts(0) <> readHandler.io.rMem.get
    memArb.io.rPorts(1) <> atomicHandler.io.rMem.get
    scratchpad.io.Writeport(0) <> memArb.io.wMem
    scratchpad.io.Readport(0)  <> memArb.io.rMem
  } else {
    scratchpad.io.Writeport(0) <> writeHandler.io.wMem.get
    scratchpad.io.Readport(0)  <> readHandler.io.rMem.get
  }
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
class TLSplitter(atomic: Boolean = false)(implicit c: MemBusConfig) extends Module {
  val io = IO(new Bundle {
    val in    = Flipped(new TilelinkPort(c.tlBus))
    val read  = new TilelinkPort(c.tlBus)
    val write = new TilelinkPort(c.tlBus)
    val amo   = if (atomic) Some(new TilelinkPort(c.tlBus)) else None
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
  if (atomic) {
    io.amo.get.a.valid := false.B
    io.amo.get.a.bits  := DontCare
    io.amo.get.d.ready := false.B
  }

  val sIdle :: sRead :: sWrite :: sAmo :: Nil = Enum(4)
  val state   = RegInit(sIdle)
  val beatCnt = Reg(UInt(24.W))

  switch(state) {

    is(sIdle) {
      when(io.in.a.valid) {
        switch(io.in.a.bits.opcode) {
          is(TilelinkOpcodes.Get) {
            io.read.a.valid := io.in.a.valid
            io.read.a.bits  := io.in.a.bits
            io.in.a.ready   := io.read.a.ready
            when(io.in.a.fire) {
              beatCnt := io.in.a.bits.size - c.dataBusSize.U
              state   := sRead
            }
          }
          is(TilelinkOpcodes.PutFullData) {
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
          is(TilelinkOpcodes.ArithmeticData, TilelinkOpcodes.LogicalData) {
            if (atomic) {
              io.amo.get.a.valid := io.in.a.valid
              io.amo.get.a.bits  := io.in.a.bits
              io.in.a.ready      := io.amo.get.a.ready
              when(io.in.a.fire) {
                beatCnt := io.in.a.bits.size - c.dataBusSize.U
                state   := sAmo
              }
            } else {
              assert(false.B, "TLSplitter: ArithmeticData not supported")
            }
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
      io.in.d.valid   := io.read.d.valid
      io.in.d.bits    := io.read.d.bits
      io.read.d.ready := io.in.d.ready
      when(io.in.d.fire) {
        beatCnt := beatCnt - c.dataBusSize.U
        when(beatCnt === 0.U) { state := sIdle }
      }
    }

    is(sWrite) {
      io.write.a.valid := io.in.a.valid
      io.write.a.bits  := io.in.a.bits
      io.in.a.ready    := io.write.a.ready
      when(io.in.a.fire) {
        beatCnt := beatCnt - c.dataBusSize.U
        when(beatCnt === 0.U) { state := sIdle }
      }
      when(io.write.d.valid) {
        io.in.d.valid    := io.write.d.valid
        io.in.d.bits     := io.write.d.bits
        io.write.d.ready := io.in.d.ready
      }
    }

    is(sAmo) {
      if (atomic) {
        io.in.d.valid      := io.amo.get.d.valid
        io.in.d.bits       := io.amo.get.d.bits
        io.amo.get.d.ready := io.in.d.ready
        when(io.in.d.fire) {
          beatCnt := beatCnt - c.dataBusSize.U
          when(beatCnt === 0.U) { state := sIdle }
        }
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
 *
 * `wideBanks`, when set, exposes a `wideBanks`-element 32-bit-wide port
 * matching [[TLScratchpadHandler]]'s wide mode; `bankDepth` is the row
 * count.  Left at its default (`None`), this is a narrow byte-bus scratchpad.
 */


case class SPMConfig(
  bankDepth:   Int,
  writeports:  Int,
  readports:   Int,
  wideBanks:   Option[Int] = None,
)(implicit val c: MemBusConfig)

class MemTierScratchpad(config: SPMConfig)(implicit c: MemBusConfig) extends Module {

  private val portElems = config.wideBanks.getOrElse(c.dataBusSize)
  private val portWidth = config.wideBanks.map(_ => 32).getOrElse(8)

  val io = IO(new Bundle {
    val Writeport = Vec(config.writeports, Flipped(Decoupled(new Writeport(
      new Bundle {
        val writeData = Vec(portElems, UInt(portWidth.W))
        val strb      = Vec(portElems, Bool())
      }, 16))))
    val Readport = Vec(config.readports, Flipped(new Readport(
      Vec(portElems, UInt(config.wideBanks.map(_ => 32).getOrElse(c.arithDataWidth).W)), Some(16))))
  })

  config.wideBanks match {
    case None =>
      val mem = SyncReadMem(config.bankDepth, UInt((c.dataBusSize * 8).W))

      io.Writeport.foreach { port =>
        port.ready := true.B
        val wordAddr = port.bits.addr >> log2Ceil(c.dataBusSize)
        when(port.fire) {
          mem.write(wordAddr, port.bits.data.writeData.asUInt)
        }
      }

      io.Readport.foreach { port =>
        port.request.ready := true.B
        val wordAddr = port.request.bits.addr.get >> log2Ceil(c.dataBusSize)
        val readResult = mem.read(wordAddr, port.request.fire)
        port.response.bits.readData := readResult.asTypeOf(Vec(c.dataBusSize, UInt(c.arithDataWidth.W)))
        port.response.valid := RegNext(port.request.fire)
      }

    case Some(n) =>
      val mem = SyncReadMem(config.bankDepth, Vec(n, UInt(32.W)))

      io.Writeport.foreach { port =>
        port.ready := true.B
        when(port.fire) {
          mem.write(port.bits.addr, port.bits.data.writeData, port.bits.data.strb)
        }
      }

      io.Readport.foreach { port =>
        port.request.ready := true.B
        val result = mem.read(port.request.bits.addr.get, port.request.fire)
        port.response.bits.readData := result
        port.response.valid         := RegNext(port.request.fire)
      }
  }
}
