package ATA8

import chisel3._
import chisel3.util._

/**
 * Inter-tier DMA engine.
 *
 * Coordinates two [[MemDMAPipeline]] instances (A and B) to transfer data
 * between two scratchpad tiers.  Each pipeline has its own independent
 * TileLink master port to a [[SemaphoreBank]] for producer/consumer
 * synchronisation; the semaphore addresses and step size are carried in
 * the per-pipeline [[dmaDescriptor]].
 *
 * [[io.semaphoreA]] and [[io.semaphoreB]] are pass-through ports — connect
 * them to a SemaphoreBank (or crossbar) externally.
 */
class MemDMA(implicit c: MemBusConfig) extends Module {
  val io = IO(new Bundle {
    val portA      = new TilelinkPort
    val portB      = new TilelinkPort
    val semaphoreA = new TilelinkPort   // pipeline A's semaphore master port
    val semaphoreB = new TilelinkPort   // pipeline B's semaphore master port
    val interface  = Flipped(new dmaInterface(size = 2))
  })

  io.interface.descriptor.ready := false.B
  io.interface.response.valid   := false.B
  io.interface.response.bits    := DontCare

  val A = Module(new MemDMAPipeline())
  val B = Module(new MemDMAPipeline())

  io.portA      <> A.io.tl
  io.portB      <> B.io.tl
  io.semaphoreA <> A.io.semaphoreIF
  io.semaphoreB <> B.io.semaphoreIF

  A.io.interface.descriptor.valid := false.B
  A.io.interface.descriptor.bits  := DontCare
  A.io.interface.response.ready   := false.B
  B.io.interface.descriptor.valid := false.B
  B.io.interface.descriptor.bits  := DontCare
  B.io.interface.response.ready   := false.B

  val AtoB = Module(new BufferFIFO(8, UInt((c.dataBusSize * 8).W)))
  val BtoA = Module(new BufferFIFO(8, UInt((c.dataBusSize * 8).W)))

  AtoB.io.WriteData <> A.io.dataOut
  BtoA.io.WriteData <> B.io.dataOut

  AtoB.io.ReadData.request.valid     := B.io.dataIn.ready
  AtoB.io.ReadData.request.bits.addr := DontCare
  BtoA.io.ReadData.request.valid     := A.io.dataIn.ready
  BtoA.io.ReadData.request.bits.addr := DontCare

  A.io.dataIn.valid := BtoA.io.ReadData.request.ready
  A.io.dataIn.bits  := BtoA.io.ReadData.response.bits.readData
  B.io.dataIn.valid := AtoB.io.ReadData.request.ready
  B.io.dataIn.bits  := AtoB.io.ReadData.response.bits.readData

  val aResponseReg = RegInit(false.B)
  val bResponseReg = RegInit(false.B)

  val StateReg = RegInit(0.U(3.W))
  val reg      = Reg(io.interface.descriptor.bits.cloneType)

  switch(StateReg) {
    is(0.U) {
      io.interface.descriptor.ready := true.B
      when(io.interface.descriptor.valid) {
        reg          := io.interface.descriptor.bits
        aResponseReg := false.B
        bResponseReg := false.B
        StateReg     := 1.U
      }
    }
    is(1.U) {
      A.io.interface.descriptor.valid     := true.B
      B.io.interface.descriptor.valid     := true.B
      A.io.interface.descriptor.bits(0)   := reg(0)
      B.io.interface.descriptor.bits(0)   := reg(1)

      when(A.io.interface.descriptor.fire && B.io.interface.descriptor.fire) {
        StateReg := 2.U
      }
    }
    is(2.U) {
      A.io.interface.response.ready := true.B
      B.io.interface.response.ready := true.B

      when(A.io.interface.response.valid) { aResponseReg := true.B }
      when(B.io.interface.response.valid) { bResponseReg := true.B }

      when(aResponseReg && bResponseReg) { StateReg := 3.U }
    }
    is(3.U) {
      io.interface.response.valid        := true.B
      io.interface.response.bits.denied  := 0.U
      io.interface.response.bits.corrupt := 0.U
      io.interface.response.bits.source  := 0.U
      io.interface.response.bits.sink    := 0.U
      when(io.interface.response.fire) { StateReg := 0.U }
    }
  }
}
