package ATA8

import chisel3._
import chisel3.util._

/**
 * Single-channel DMA pipeline.
 *
 * Per-step order when semEnable is set (RestartOnStep):
 *   1. Acquire  (state 8) – AQGREQ, stall until semaphore condition is met
 *   2. Transfer (states 1-3 or 5-6) – TileLink read or write of semStepSize beats
 *   3. Release  (state 9) – ADDU, then decrement remaining
 *   Repeat until remaining reaches zero, then respond.
 *
 * Without semEnable: single TileLink transaction of size beats, no semaphore.
 *
 * State machine:
 *   0  Idle
 *   8  SemAcquire  – AQGREQ
 *   1  WriteFirst  – issue first PutFullData A-beat
 *   2  WriteRest   – issue remaining A-beats
 *   3  WriteAck    – wait for D AccessAck
 *   9  SemRelease  – ADDU; decrement remaining; loop to 8 or finish
 *   4  WriteRespond
 *   5  ReadIssue   – issue Get
 *   6  ReadData    – receive D data beats
 *   7  ReadRespond
 */
class MemDMAPipeline(implicit c: MemBusConfig) extends Module {
  val io = IO(new Bundle {
    val tl          = new TilelinkPort
    val semaphoreIF = new TilelinkPort
    val interface   = Flipped(new dmaInterface(1))
    val dataOut     = Decoupled(UInt((c.dataBusSize * 8).W))
    val dataIn      = Flipped(Decoupled(UInt((c.dataBusSize * 8).W)))
  })

  // ── Defaults ──────────────────────────────────────────────────────────────
  io.interface.descriptor.ready := false.B
  io.interface.response.valid   := false.B
  io.interface.response.bits    := DontCare

  io.tl.a.valid := false.B
  io.tl.d.ready := false.B
  io.tl.a.bits  := DontCare

  io.semaphoreIF.a.valid := false.B
  io.semaphoreIF.a.bits  := DontCare
  io.semaphoreIF.d.ready := false.B

  io.dataIn.ready  := false.B
  io.dataOut.valid := false.B
  io.dataOut.bits  := DontCare

  // ── Registers ─────────────────────────────────────────────────────────────
  val StateReg      = RegInit(0.U(4.W))
  val reg           = Reg(new dmaDescriptor)
  val beatCnt       = RegInit(0.U(24.W))
  val semAFired     = RegInit(false.B)
  val isWrite       = RegInit(true.B)
  val remaining     = RegInit(0.U(24.W))
  val effectiveAddr = RegInit(0.U(c.addrWidth.W))
  val effectiveSize = RegInit(0.U(24.W))

  // ── Helper: drive one ArithmeticData beat on semaphoreIF ──────────────────
  def semOp(param: UInt): Unit = {
    io.semaphoreIF.a.valid        := true.B
    io.semaphoreIF.a.bits.opcode  := TilelinkOpcodes.ArithmeticData
    io.semaphoreIF.a.bits.param   := param
    io.semaphoreIF.a.bits.size    := 1.U
    io.semaphoreIF.a.bits.source  := 0.U
    io.semaphoreIF.a.bits.address := reg.semaphore.semAddr
    io.semaphoreIF.a.bits.data    := reg.semaphore.semStepSize
    io.semaphoreIF.a.bits.mask    := Fill(c.dataBusSize, 1.U(1.W))
    io.semaphoreIF.a.bits.corrupt := 0.U
  }

  // ── State machine ─────────────────────────────────────────────────────────
  switch(StateReg) {

    // 0 – Idle ────────────────────────────────────────────────────────────────
    is(0.U) {
      io.interface.descriptor.ready := true.B

      when(io.interface.descriptor.valid) {
        val desc = io.interface.descriptor.bits(0)
        reg           := desc
        semAFired     := false.B
        remaining     := desc.size
        isWrite       := desc.writeEn
        effectiveAddr := desc.addr
        effectiveSize := Mux(desc.semaphore.semEnable, desc.semaphore.semStepSize, desc.size)
        StateReg      := Mux(desc.semaphore.semEnable, 8.U, Mux(desc.writeEn, 1.U, 5.U))
      }
    }

    // 8 – Acquire: AQGREQ, stall until semaphore condition is met ─────────────
    is(8.U) {
      when(!semAFired) {
        semOp(ArithmeticDataParam.AQGREQ)
        when(io.semaphoreIF.a.fire) { semAFired := true.B }
      }
      io.semaphoreIF.d.ready := semAFired
      when(io.semaphoreIF.d.fire) {
        semAFired := false.B
        StateReg  := Mux(isWrite, 1.U, 5.U)
      }
    }

    // 1 – Write: issue first A-beat ───────────────────────────────────────────
    is(1.U) {
      assert(effectiveSize =/= 0.U)

      io.tl.a.valid        := io.dataIn.valid
      io.tl.a.bits.opcode  := TilelinkOpcodes.PutFullData
      io.tl.a.bits.param   := 0.U
      io.tl.a.bits.address := effectiveAddr
      io.tl.a.bits.size    := effectiveSize
      io.tl.a.bits.source  := 0.U
      io.tl.a.bits.data    := io.dataIn.bits
      io.tl.a.bits.mask    := HelperFunctions.uintToBoolVec(effectiveSize, c.dataBusSize).asUInt

      when(io.tl.a.fire) {
        io.dataIn.ready := true.B
        beatCnt         := 1.U

        when(effectiveSize > 1.U) {
          StateReg := 2.U
        }.otherwise {
          beatCnt  := 0.U
          StateReg := 3.U
        }
      }
    }

    // 2 – Write: remaining A-beats ────────────────────────────────────────────
    is(2.U) {
      when(io.dataIn.valid) { io.tl.a.valid := true.B }

      io.tl.a.bits.opcode  := TilelinkOpcodes.PutFullData
      io.tl.a.bits.param   := 0.U
      io.tl.a.bits.address := effectiveAddr
      io.tl.a.bits.size    := effectiveSize
      io.tl.a.bits.source  := 0.U
      io.tl.a.bits.data    := io.dataIn.bits
      io.tl.a.bits.corrupt := 0.U

      when(io.tl.a.fire) {
        io.dataIn.ready := true.B

        when(beatCnt < (effectiveSize - 1.U)) {
          beatCnt := beatCnt + 1.U
        }.otherwise {
          beatCnt  := 0.U
          StateReg := 3.U
        }
      }
    }

    // 3 – Write: wait for D AccessAck ─────────────────────────────────────────
    is(3.U) {
      io.tl.d.ready := true.B

      when(effectiveSize === 0.U || io.tl.d.valid) {
        StateReg := Mux(reg.semaphore.semEnable, 9.U, 4.U)
      }
    }

    // 4 – Write: send response ────────────────────────────────────────────────
    is(4.U) {
      io.interface.response.valid := true.B

      when(io.interface.response.fire) {
        io.interface.response.bits.denied  := false.B
        io.interface.response.bits.corrupt := false.B
        StateReg := 0.U
      }
    }

    // 5 – Read: issue Get ─────────────────────────────────────────────────────
    is(5.U) {
      assert(effectiveSize =/= 0.U)

      io.tl.a.valid        := true.B
      io.tl.a.bits.opcode  := TilelinkOpcodes.Get
      io.tl.a.bits.param   := 0.U
      io.tl.a.bits.address := effectiveAddr
      io.tl.a.bits.size    := effectiveSize
      io.tl.a.bits.source  := 0.U
      io.tl.a.bits.corrupt := 0.U

      when(io.tl.a.fire) {
        beatCnt  := 0.U
        StateReg := 6.U
      }
    }

    // 6 – Read: receive D data beats ──────────────────────────────────────────
    is(6.U) {
      when(io.dataOut.ready) { io.tl.d.ready := true.B }

      when(io.tl.d.fire) {
        io.dataOut.valid := true.B
        io.dataOut.bits  := io.tl.d.bits.data

        when(beatCnt < (effectiveSize - 1.U)) {
          beatCnt := beatCnt + 1.U
        }.otherwise {
          beatCnt  := 0.U
          StateReg := Mux(reg.semaphore.semEnable, 9.U, 7.U)
        }
      }
    }

    // 9 – Release: ADDU, decrement remaining, loop back to acquire or finish ───
    is(9.U) {
      when(!semAFired) {
        semOp(ArithmeticDataParam.ADDU)
        when(io.semaphoreIF.a.fire) { semAFired := true.B }
      }
      io.semaphoreIF.d.ready := semAFired
      when(io.semaphoreIF.d.fire) {
        semAFired := false.B
        val next = remaining - reg.semaphore.semStepSize
        remaining := next
        when(next > 0.U) {
          effectiveAddr := effectiveAddr + reg.semaphore.semStepSize
          StateReg      := 8.U
        }.otherwise {
          StateReg := Mux(isWrite, 4.U, 7.U)
        }
      }
    }

    // 7 – Read: send response ─────────────────────────────────────────────────
    is(7.U) {
      io.interface.response.valid := true.B

      when(io.interface.response.fire) {
        io.interface.response.bits.denied  := false.B
        io.interface.response.bits.corrupt := false.B
        StateReg := 0.U
      }
    }
  }
}
