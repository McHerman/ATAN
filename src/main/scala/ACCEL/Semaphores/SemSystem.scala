package ATA8

import chisel3._
import chisel3.util._

class SemSystem(noPorts: Int)(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val inPorts = Vec(noPorts, Flipped(new TilelinkPort))
    val instructionStream = Flipped(Decoupled(new SemProgInst))
  })

  val queue   = Module(new BufferFIFO(c.semaphoreQueueSize, new SemProgInst))
  val bank    = Module(new SemaphoreBank(noPorts))
  val trigger = Module(new TriggerSystem)

  def truncGeneration(g: UInt): UInt =
    if (c.semaphoreGenerationWidth == 0) 0.U
    else g(c.semaphoreGenerationWidth - 1, 0)

  queue.io.WriteData <> io.instructionStream
  io.inPorts <> bank.io.inPorts

  queue.io.ReadData.request.valid := trigger.io.load.ready
  queue.io.ReadData.request.bits  := DontCare
  trigger.io.load.valid := queue.io.ReadData.request.ready
  trigger.io.load.bits  := queue.io.ReadData.response.bits.readData

  bank.io.progPort.valid           := trigger.io.fire.valid
  bank.io.progPort.bits.addr       := trigger.io.fire.bits.payload.semAddr
  bank.io.progPort.bits.initValues := trigger.io.fire.bits.payload.initValues
  bank.io.progPort.bits.generation := truncGeneration(trigger.io.fire.bits.payload.generation)
  trigger.io.fire.ready := bank.io.progPort.ready

  trigger.io.inputEvent.valid          := bank.io.eventPort.valid
  trigger.io.inputEvent.bits.addr      := bank.io.eventPort.bits.addr
  trigger.io.inputEvent.bits.eventCode := bank.io.eventPort.bits.eventCode
  bank.io.eventPort.ready := true.B
}
