package ATA8

import chisel3._
import chisel3.util._

class AtomicReservationBank(numPorts: Int)(implicit c: MemBusConfig) extends Module {
  val io = IO(new Bundle {
    val reserve = Vec(numPorts, Flipped(Decoupled(new AtomicReservation())))
    val reservations = Vec(numPorts, Output(Valid(UInt(32.W))))
  })

  private val idxWidth = log2Ceil(numPorts)

  val reservationRegs = RegInit(VecInit(Seq.fill(numPorts)(
    0.U.asTypeOf(Valid(UInt(32.W)))
  )))

  val roundRobin = RegInit(0.U(idxWidth.W))

  io.reservations    := reservationRegs
  io.reserve.foreach(_.ready := false.B)

  io.reserve.zipWithIndex.foreach{case (port, i) => 
    when(port.valid && port.bits.opcode === amoReservationOp.release) { reservationRegs(i).valid := false.B }
  }


  /*
  for (i <- 0 until numPorts) {
    when(io.release(i).valid) { reservationRegs(i).valid := false.B }
  }
  */

  /*
  val candidates = VecInit((0 until numPorts).map(i =>
    io.reserve(i).valid && !reservationRegs(i).valid
  ))
  */

  val candidates = VecInit((0 until numPorts).map(i =>
    io.reserve(i).valid && io.reserve(i).bits.opcode === amoReservationOp.acquire && !reservationRegs(i).valid
  ))

  val winner      = Wire(UInt(idxWidth.W))
  val winnerValid = Wire(Bool())
  winner      := 0.U
  winnerValid := false.B

  when(roundRobin < (numPorts - 1).U) {
    roundRobin := roundRobin + 1.U
  }.otherwise{
    roundRobin := 0.U 
  }

  /*
  for (offset <- (numPorts - 1) to 0 by -1) {
    val idx = ((roundRobin +& offset.U) % numPorts.U).asUInt
    when(candidates(idx)) { winner := idx; winnerValid := true.B }
  }
  */

  when(candidates(roundRobin)) { winner := roundRobin; winnerValid := true.B }

  val internalConflict = HelperFunctions.checkAtomic(
    io.reserve(winner).bits.address, reservationRegs)

  when(winnerValid && !internalConflict) {

    io.reserve(winner).ready := true.B
    when(io.reserve(winner).fire) {
      reservationRegs(winner).valid        := true.B
      reservationRegs(winner).bits := io.reserve(winner).bits.address
      //roundRobin                           := (winner + 1.U) % numPorts.U
    }
  }
}
