package ATA8

import chisel3._
import chisel3.util._

class DMAQueue(nDMAs: Int, queueDepth: Int = 2)(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val instructionStream = Flipped(Decoupled(new DMAInst))
    val dmaInterfaces     = Vec(nDMAs, new dmaInterface(2))
  })

  val queue = Module(new BufferFIFO(queueDepth, new DMAInst))


  // Defaults
  queue.io.WriteData <> io.instructionStream

  queue.io.ReadData.request.valid := false.B
  queue.io.ReadData.request.bits  := DontCare

  io.dmaInterfaces.foreach { iface =>
    iface.descriptor.valid := false.B
    iface.descriptor.bits  := DontCare
    iface.response.ready   := true.B
  }

  // ── State machine ─────────────────────────────────────────────────────────
  val idle :: issue :: Nil = Enum(2)
  val stateReg = RegInit(idle)
  val instReg  = Reg(new DMAInst)

  // ── Convert DMAInst → descriptor pair ──────────────────────────────────────
  val descPair = Wire(Vec(2, new dmaDescriptor()))

  // TODO, create a new bundle to more ellegantly map between these two

  descPair(0).addr    := instReg.addrs(0).addr
  descPair(0).size    := instReg.size
  //descPair(0).writeEn := false.B
  descPair(0).writeEn := instReg.func 
  descPair(0).source  := 0.U
  descPair(0).sink    := 0.U
  descPair(0).semaphore.get.semEnable   := instReg.addrs(0).sem.valid
  descPair(0).semaphore.get.semAddr     := instReg.addrs(0).sem.bits.addr
  descPair(0).semaphore.get.semStepSize := instReg.addrs(0).sem.bits.stepSize.bits
  descPair(0).semaphore.get.mode        := 0.U

  descPair(1).addr    := instReg.addrd(0).addr
  descPair(1).size    := instReg.size
  //descPair(1).writeEn := true.B
  descPair(1).writeEn := !instReg.func 
  descPair(1).source  := 0.U
  descPair(1).sink    := 0.U
  descPair(1).semaphore.get.semEnable   := instReg.addrd(0).sem.valid
  descPair(1).semaphore.get.semAddr     := instReg.addrd(0).sem.bits.addr
  descPair(1).semaphore.get.semStepSize := instReg.addrd(0).sem.bits.stepSize.bits
  descPair(1).semaphore.get.mode        := 0.U

  // ── Route to selected DMA ─────────────────────────────────────────────────
  /*
  val dmaFire = Wire(Bool())
  dmaFire := false.B

  for (i <- 0 until nDMAs) {
    when(instReg.DMAAddr === i.U) {
      io.dmaInterfaces(i).descriptor.valid := stateReg === issue
      io.dmaInterfaces(i).descriptor.bits  := descPair
      dmaFire := io.dmaInterfaces(i).descriptor.fire
    }
  }
  */

  switch(stateReg) {
    is(idle) {
      when(queue.io.ReadData.request.ready) {
        queue.io.ReadData.request.valid := true.B
        instReg  := queue.io.ReadData.response.bits.readData
        stateReg := issue
      }
    }
    is(issue) {
      io.dmaInterfaces(instReg.DMAAddr).descriptor.valid := true.B
      io.dmaInterfaces(instReg.DMAAddr).descriptor.bits  := descPair

      when(io.dmaInterfaces(instReg.DMAAddr).descriptor.fire) {
        stateReg := idle
      }
    }
  }
}
