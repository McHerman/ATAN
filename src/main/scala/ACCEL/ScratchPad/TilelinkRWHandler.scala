package ATA8

import chisel3._
import chisel3.util._

/**
 * Combined TileLink Get/Put handler.  Acts as a device-side TileLink slave
 * and translates incoming A-channel requests to separate scratchpad read and
 * write mem ports.  Used for the inter-tier DMA ports on each MemTier.
 *
 * Write path: mirrors TilelinkWriteHandler (multi-beat PutFullData burst).
 * Read  path: mirrors TilelinkReadHandler  (multi-beat Get with 1-cycle
 *             SyncReadMem latency).
 */
class TilelinkRWHandler(implicit c: MemBusConfig) extends Module {
  val io = IO(new Bundle {
    val tl   = Flipped(new TilelinkPort(c.tlBus))
    val wMem = Decoupled(new Writeport(
      new Bundle {
        val writeData = Vec(c.dataBusSize, UInt(8.W))
        val strb      = Vec(c.dataBusSize, Bool())
      }, 16))
    val rMem = new Readport(Vec(c.dataBusSize, UInt(c.arithDataWidth.W)), Some(16))
  })

  // ── Defaults ──────────────────────────────────────────────────────────────
  io.tl.a.ready         := false.B
  io.tl.d.valid         := false.B
  io.tl.d.bits          := DontCare
  io.wMem.valid         := false.B
  io.wMem.bits          := DontCare
  io.rMem.request.valid := false.B
  io.rMem.request.bits  := DontCare

  // ── State ─────────────────────────────────────────────────────────────────
  val sIdle :: sWriteLock :: sWriteAck :: sReadLock :: Nil = Enum(4)
  val state     = RegInit(sIdle)
  val sizeReg   = Reg(UInt(24.W))
  val addrReg   = Reg(UInt(16.W))
  val beatCnt   = Reg(UInt(24.W))
  val firstBeat = RegInit(false.B)   // write-path: triggers AccessAck

  // ── Write AccessAck (shared between single-beat and last-beat paths) ──────
  when(firstBeat) {
    io.tl.d.valid            := true.B
    io.tl.d.bits.opcode      := TilelinkOpcodes.AccessAck
    io.tl.d.bits.param       := 0.U
    io.tl.d.bits.size        := sizeReg
    io.tl.d.bits.source      := 0.U
    io.tl.d.bits.sink        := 0.U
    io.tl.d.bits.denied      := 0.U
    io.tl.d.bits.data        := 0.U
    io.tl.d.bits.corrupt     := 0.U
    when(io.tl.d.fire) { firstBeat := false.B }
  }

  switch(state) {

    // ── Idle: accept a new A-channel request ────────────────────────────────
    is(sIdle) {
      when(io.tl.a.valid) {
        when(io.tl.a.bits.opcode === TilelinkOpcodes.Get) {
          // Read: accept immediately, lock, start issuing read requests
          io.tl.a.ready := true.B
          when(io.tl.a.fire) {
            sizeReg := io.tl.a.bits.size
            addrReg := io.tl.a.bits.address
            beatCnt := io.tl.a.bits.size - c.dataBusSize.U
            state   := sReadLock
          }
        }.otherwise {
          // Write: pass valid/ready through independently to avoid combinational cycle
          io.tl.a.ready := io.wMem.ready
          io.wMem.valid                  := io.tl.a.valid
          io.wMem.bits.addr              := io.tl.a.bits.address
          io.wMem.bits.data.writeData    :=
            io.tl.a.bits.data.asTypeOf(Vec(c.dataBusSize, UInt(8.W)))
          io.wMem.bits.data.strb         := VecInit(io.tl.a.bits.mask.asBools)
          when(io.tl.a.fire) {
            sizeReg := io.tl.a.bits.size
            when(io.tl.a.bits.size > c.dataBusSize.U) {
              addrReg := io.tl.a.bits.address + 1.U
              beatCnt := io.tl.a.bits.size - (2 * c.dataBusSize).U
              state   := sWriteLock
            }.otherwise {
              firstBeat := true.B   // single beat: go straight to ack
            }
          }
        }
      }
    }

    // ── Write burst: consume remaining A beats ───────────────────────────────
    is(sWriteLock) {
      io.tl.a.ready              := io.wMem.ready
      io.wMem.valid              := io.tl.a.valid
      io.wMem.bits.addr          := addrReg
      io.wMem.bits.data.writeData :=
        io.tl.a.bits.data.asTypeOf(Vec(c.dataBusSize, UInt(8.W)))
      io.wMem.bits.data.strb     := VecInit(io.tl.a.bits.mask.asBools)
      when(io.tl.a.fire) {
        addrReg := addrReg + 1.U
        when(beatCnt === 0.U) {
          firstBeat := true.B
          state     := sIdle
        }.otherwise {
          beatCnt := beatCnt - c.dataBusSize.U
        }
      }
    }

    // ── Read burst: issue requests and stream back AccessAckData ─────────────
    is(sReadLock) {
      io.rMem.request.valid          := true.B
      io.rMem.request.bits.addr.get  := addrReg

      // Forward response on D channel
      io.tl.d.valid              := io.rMem.response.valid
      io.tl.d.bits.opcode        := TilelinkOpcodes.AccessAckData
      io.tl.d.bits.param         := 0.U
      io.tl.d.bits.size          := sizeReg
      io.tl.d.bits.source        := 0.U
      io.tl.d.bits.sink          := 0.U
      io.tl.d.bits.denied        := 0.U
      io.tl.d.bits.data          := io.rMem.response.bits.readData.asUInt
      io.tl.d.bits.corrupt       := 0.U

      when(io.rMem.request.fire) {
        addrReg := addrReg + 1.U
      }
      when(io.tl.d.fire) {
        beatCnt := beatCnt - c.dataBusSize.U
        when(beatCnt === 0.U) { state := sIdle }
      }
    }
  }
}
