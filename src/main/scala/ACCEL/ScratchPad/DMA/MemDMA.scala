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
class MemDMA(sourceIdA: Int = 0, sourceIdB: Int = 0)(implicit c: MemBusConfig) extends Module {
  val io = IO(new Bundle {
    val portA      = new TilelinkPort(c.tlBus)
    val portB      = new TilelinkPort(c.tlBus)
    val semaphoreA = new TilelinkPort(c.tlSemBus)
    val semaphoreB = new TilelinkPort(c.tlSemBus)
    val interface  = Flipped(new dmaInterface(size = 2))
  })

  io.interface.descriptor.ready := false.B
  io.interface.response.valid   := false.B
  io.interface.response.bits    := DontCare

  val tlDMAConfig = TLDMAConfig(read = true, write = true, semaphore = true)
  val A = Module(new TLDMA(tlDMAConfig, sourceId = sourceIdA))
  val B = Module(new TLDMA(tlDMAConfig, sourceId = sourceIdB))

  io.portA      <> A.io.tl
  io.portB      <> B.io.tl
  io.semaphoreA <> A.io.semaphoreIF.get
  io.semaphoreB <> B.io.semaphoreIF.get

  A.io.interface.descriptor.valid := false.B
  A.io.interface.descriptor.bits  := DontCare
  A.io.interface.response.ready   := false.B
  B.io.interface.descriptor.valid := false.B
  B.io.interface.descriptor.bits  := DontCare
  B.io.interface.response.ready   := false.B

  val AtoB = Module(new BufferFIFO(8, UInt((c.dataBusSize * 8).W)))
  val BtoA = Module(new BufferFIFO(8, UInt((c.dataBusSize * 8).W)))

  AtoB.io.WriteData <> A.io.dataOut.get
  BtoA.io.WriteData <> B.io.dataOut.get

  AtoB.io.ReadData <> B.io.dataIn.get
  BtoA.io.ReadData <> A.io.dataIn.get


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

      //FIXME, should work, but is a bad idea
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
