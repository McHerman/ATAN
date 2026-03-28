package ATA8

import chisel3._
import chisel3.util._

class TilelinkProtocolChecker(endpointType: String = "Host", name: String = "", timeout: Int = 1000)(implicit c: MemBusConfig) extends Module {
  require(endpointType == "Host" || endpointType == "Device",
    s"endpointType must be 'Host' or 'Device', got '$endpointType'")

  val io = IO(new Bundle {
    val a_valid = Input(Bool())
    val a_ready = Input(Bool())
    val a_bits  = Input(new TilelinkA)
    val d_valid = Input(Bool())
    val d_ready = Input(Bool())
    val d_bits  = Input(new TilelinkD)
  })

  val prefix = s"TL${if (name.nonEmpty) s"[$name]" else ""}"

  val a_fire = io.a_valid && io.a_ready
  val d_fire = io.d_valid && io.d_ready

  // Directional check helpers (OpenTitan convention)
  // Host endpoint: assert A-channel (host is DUT), assume D-channel
  // Device endpoint: assume A-channel, assert D-channel (device is DUT)
  def checkA(cond: Bool, msg: String): Unit =
    if (endpointType == "Host") chisel3.assert(cond, s"$prefix A: $msg")
    else chisel3.assume(cond, s"$prefix A: $msg")

  def checkD(cond: Bool, msg: String): Unit =
    if (endpointType == "Device") chisel3.assert(cond, s"$prefix D: $msg")
    else chisel3.assume(cond, s"$prefix D: $msg")

  // Transaction-level checks always assert
  def checkTxn(cond: Bool, msg: String): Unit =
    chisel3.assert(cond, s"$prefix: $msg")

  // ── A-channel per-beat checks ──

  when(io.a_valid) {
    // 1. Legal opcode
    val legalA = io.a_bits.opcode === TilelinkOpcodes.PutFullData ||
                 io.a_bits.opcode === TilelinkOpcodes.PutPartialData ||
                 io.a_bits.opcode === TilelinkOpcodes.Get
    checkA(legalA, "illegal opcode")
    // 2. Size > 0
    checkA(io.a_bits.size =/= 0.U, "size must be > 0")
    // 3. param must be 0 for TL-UH Get/Put
    checkA(io.a_bits.param === 0.U, "param must be 0 for Get/Put")
  }

  // 3. A-channel stability: bits must not change while valid && !ready
  val a_valid_prev = RegNext(io.a_valid, false.B)
  val a_ready_prev = RegNext(io.a_ready, false.B)
  val a_bits_prev  = RegNext(io.a_bits)

  when(a_valid_prev && !a_ready_prev && io.a_valid) {
    checkA(io.a_bits.asUInt === a_bits_prev.asUInt, "bits changed while valid && !ready")
  }

  // ── D-channel per-beat checks ──

  when(io.d_valid) {
    // 4. Legal opcode
    val legalD = io.d_bits.opcode === TilelinkOpcodes.AccessAck ||
                 io.d_bits.opcode === TilelinkOpcodes.AccessAckData
    checkD(legalD, "illegal opcode")
  }

  // 5. D-channel stability
  val d_valid_prev = RegNext(io.d_valid, false.B)
  val d_ready_prev = RegNext(io.d_ready, false.B)
  val d_bits_prev  = RegNext(io.d_bits)

  when(d_valid_prev && !d_ready_prev && io.d_valid) {
    checkD(io.d_bits.asUInt === d_bits_prev.asUInt, "bits changed while valid && !ready")
  }

  // ── Transaction state machine ──

  val sIdle :: sWriteData :: sWriteResp :: sReadResp :: Nil = Enum(4)
  val state = RegInit(sIdle)

  val beatCounter     = RegInit(0.U(24.W))
  val expectedBeats   = RegInit(0.U(24.W))
  val expectedDOpcode = RegInit(0.U(3.W))
  val timeoutCounter  = RegInit(0.U(log2Ceil(timeout + 1).W))

  switch(state) {
    is(sIdle) {
      when(a_fire) {
        expectedBeats  := io.a_bits.size
        timeoutCounter := 0.U
        when(io.a_bits.opcode === TilelinkOpcodes.Get) {
          state           := sReadResp
          expectedDOpcode := TilelinkOpcodes.AccessAckData
          beatCounter     := 0.U
        }.otherwise { // PutFullData or PutPartialData
          expectedDOpcode := TilelinkOpcodes.AccessAck
          beatCounter     := 1.U
          when(io.a_bits.size === 1.U) {
            state := sWriteResp
          }.otherwise {
            state := sWriteData
          }
        }
      }
    }

    is(sWriteData) {
      when(a_fire) {
        beatCounter := beatCounter + 1.U
        when(beatCounter + 1.U === expectedBeats) {
          state := sWriteResp
        }
      }
    }

    is(sWriteResp) {
      when(d_fire) {
        state := sIdle
      }
    }

    is(sReadResp) {
      when(d_fire) {
        beatCounter := beatCounter + 1.U
        when(beatCounter + 1.U === expectedBeats) {
          state := sIdle
        }
      }
    }
  }

  // ── Transaction-level checks ──

  // 6. Response opcode matches request
  when(d_fire && (state === sWriteResp || state === sReadResp)) {
    checkTxn(io.d_bits.opcode === expectedDOpcode, "D opcode doesn't match expected response type")
  }

  // 9. No unsolicited D beats
  when(io.d_valid) {
    checkTxn(state === sWriteResp || state === sReadResp, "unsolicited D-channel response")
  }

  // 10. No new A-channel transaction while waiting for response
  when(a_fire && (state === sWriteResp || state === sReadResp)) {
    checkTxn(false.B, "A-channel beat while waiting for response")
  }

  // In WriteData state, only Put opcodes are legal (not Get)
  when(a_fire && state === sWriteData) {
    checkTxn(
      io.a_bits.opcode === TilelinkOpcodes.PutFullData ||
      io.a_bits.opcode === TilelinkOpcodes.PutPartialData,
      "unexpected opcode during write burst"
    )
  }

  // 11. Timeout: transaction must complete within `timeout` cycles
  when(state =/= sIdle) {
    timeoutCounter := timeoutCounter + 1.U
    checkTxn(timeoutCounter < timeout.U, "transaction timeout exceeded")
  }
}

object TilelinkProtocolChecker {
  def apply(tl: TilelinkPort, endpointType: String, name: String = "")(implicit c: MemBusConfig): Unit = {
    val checker = Module(new TilelinkProtocolChecker(endpointType, name))
    checker.io.a_valid := tl.a.valid
    checker.io.a_ready := tl.a.ready
    checker.io.a_bits  := tl.a.bits
    checker.io.d_valid := tl.d.valid
    checker.io.d_ready := tl.d.ready
    checker.io.d_bits  := tl.d.bits
  }
}
