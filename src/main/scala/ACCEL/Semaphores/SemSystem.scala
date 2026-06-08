package ATA8

import chisel3._
import chisel3.util._

class SemSystem(noPorts: Int)(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val inPorts = Vec(noPorts, Flipped(new TilelinkPort))
    val instructionStream = Flipped(Decoupled(new SemProgInst))
  })

  val queue      = Module(new BufferFIFO(c.semaphoreQueueSize, new SemProgInst))
  val SemaphoreBank = Module(new SemaphoreBank(noPorts))

  // Slice encoding-side gen down to the hardware width (may be zero).
  def truncGeneration(g: UInt): UInt =
    if (c.semaphoreGenerationWidth == 0) 0.U
    else g(c.semaphoreGenerationWidth - 1, 0)

  queue.io.WriteData <> io.instructionStream
  
  queue.io.ReadData.request.valid := false.B
  queue.io.ReadData.request.bits  := DontCare

  io.inPorts <> SemaphoreBank.io.inPorts

  SemaphoreBank.io.progPort.valid := false.B
  SemaphoreBank.io.progPort.bits.addr := 0.U
  SemaphoreBank.io.progPort.bits.initValues(0) := 0.U
  SemaphoreBank.io.progPort.bits.initValues(1) := 0.U
  SemaphoreBank.io.progPort.bits.generation := 0.U

  // State machine for writing into semaphore system
  // ── States ───────────────────────────────────────────────────────────────
  val idle :: fetch :: writeDelay :: write :: Nil = Enum(4)

  val StateReg = RegInit(idle)

  val instReg = RegInit(0.U.asTypeOf(new SemProgInst))

  switch(StateReg) {
    is(idle) {

      when(queue.io.ReadData.request.ready){
        queue.io.ReadData.request.valid := true.B

        instReg := queue.io.ReadData.response.bits.readData

        StateReg := writeDelay
      }
    }
    is(writeDelay) {
      SemaphoreBank.io.progPort.bits.addr       := instReg.semAddr
      SemaphoreBank.io.progPort.bits.initValues := instReg.initValues
      SemaphoreBank.io.progPort.bits.generation := truncGeneration(instReg.generation)

      StateReg := write
    }
    is(write) {
      SemaphoreBank.io.progPort.bits.addr       := instReg.semAddr
      SemaphoreBank.io.progPort.bits.initValues := instReg.initValues
      SemaphoreBank.io.progPort.bits.generation := truncGeneration(instReg.generation)

      SemaphoreBank.io.progPort.valid := true.B

      when(SemaphoreBank.io.progPort.fire){
        StateReg := idle
      }
    }
  }
}
