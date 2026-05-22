package ATA8

import chisel3._
import chisel3.util._
import eaac.shared.InstructionSet

/** Variable-length instruction realigner.
  *
  * Accepts 128-bit AXI-S beats from the upstream receiver and emits
  * fully-realigned instruction words to the decoder.  The encoding is
  * 64-bit-slot granular: the two bottom bits of each instruction's first
  * slot tell the realigner how many slots to consume (1, 2 or 3 → 64/128/192
  * bits).  Code 3 is illegal and raises `io.illegal`.
  *
  * Internals: a 4-slot circular buffer holds at most two beats in flight.
  * Each cycle the realigner peeks the length code at `head`, decides whether
  * enough slots are buffered to pop a complete instruction, and presents up
  * to three consecutive slots on the output as `Cat(slot[head+2], slot[head+1],
  * slot[head])`.  Short instructions leave the upper slots as don't-care; the
  * decoder slices to the right width using the length code.
  */

class InstRealigner extends Module {
  import InstructionSet.{SlotBits, MaxInstBits, BeatBits}

  val io = IO(new Bundle {
    val beatIn  = Flipped(Decoupled(new InstBeat))
    val instOut = Decoupled(new InstructionPackage)
    /** Length code observed at the head of the buffer (drives instOut). */
    val instLen = Output(UInt(2.W))
    /** Pulses when the head slot reports the reserved length code (11). */
    val illegal = Output(Bool())
  })

  // 4-slot ring buffer, indexed modulo-4 by `head`.
  val buf  = Reg(Vec(4, UInt(SlotBits.W)))
  val head = RegInit(0.U(2.W))
  val fill = RegInit(0.U(3.W))   // 0..4 valid slots

  // Peek length code from bits [1:0] of the slot at `head`.
  val lenCode = buf(head)(1, 0)
  val need    = lenCode +& 1.U   // # slots required: 1, 2, 3, or 4(reserved)
  val illegal = lenCode === 3.U

  val canPop  = (fill >= need) && !illegal
  val canPush = fill <= 2.U      // accept a beat only when ≥ 2 slots free

  // 4:1 barrel-shift to extract 3 consecutive slots starting at `head`.
  io.instOut.bits.instruction := Cat(
    buf(head + 2.U),
    buf(head + 1.U),
    buf(head),
  )
  io.instOut.valid := canPop
  io.instLen       := lenCode
  io.illegal       := illegal && fill =/= 0.U
  io.beatIn.ready  := canPush

  val pushed = io.beatIn.fire
  val popped = io.instOut.fire

  // On push: the new beat's two slots land at (head+fill) and (head+fill+1).
  // The host streams the lower-addressed slot first (LSB), so beatIn.bits.data
  // is laid out as Cat(highSlot, lowSlot) over its 128 bits.
  // Vec is 4 deep; truncate the (head + fill + k) index to 2 bits explicitly
  // so Chisel doesn't complain about the wider arithmetic width.
  val pushIdx0 = (head +& fill)(1, 0)
  val pushIdx1 = (head +& fill +& 1.U)(1, 0)
  when(pushed) {
    buf(pushIdx0) := io.beatIn.bits.data(SlotBits - 1, 0)
    buf(pushIdx1) := io.beatIn.bits.data(BeatBits - 1, SlotBits)
  }

  fill := fill + Mux(pushed, 2.U, 0.U) - Mux(popped, need, 0.U)
  head := head + Mux(popped, need, 0.U)

  // Reasonable defaults for the 192-bit window; without these, an output
  // sampled while fill < 3 would read uninitialised buf entries downstream.
  when(reset.asBool) {
    head := 0.U
    fill := 0.U
  }
}
