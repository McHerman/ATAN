package ATA8

import chisel3._
import chisel3.util._

class SemaphoreEventArbiter(n: Int)(implicit c: Configuration) extends Module {
  require(n >= 1, "SemaphoreEventArbiter needs at least one input")

  val io = IO(new Bundle {
    val in  = Vec(n, Flipped(Decoupled(new SemaphoreEvent)))
    val out = Decoupled(new SemaphoreEvent)
  })

  val idxWidth  = log2Ceil(n max 2)
  val lastGrant = RegInit((n - 1).U(idxWidth.W))

  val highPriorityMask  = VecInit((0 until n).map(i => i.U > lastGrant))
  val highPriorityValid = VecInit((0 until n).map(i => io.in(i).valid && highPriorityMask(i)))
  val anyHighPriority   = highPriorityValid.reduce(_ || _)

  val anyValid     = VecInit(io.in.map(_.valid))
  val anyAnyValid  = anyValid.reduce(_ || _)
  val hpWinner     = PriorityEncoder(highPriorityValid)
  val fallbackWinner = PriorityEncoder(anyValid)

  val granted = Wire(UInt(idxWidth.W))
  when(anyHighPriority) {
    granted := hpWinner
  }.otherwise {
    granted := fallbackWinner
  }

  io.out.valid := anyAnyValid
  io.out.bits  := io.in(granted).bits

  for (i <- 0 until n) {
    io.in(i).ready := io.out.ready && (granted === i.U) && anyAnyValid
  }

  when(io.out.fire) {
    lastGrant := granted
  }
}
