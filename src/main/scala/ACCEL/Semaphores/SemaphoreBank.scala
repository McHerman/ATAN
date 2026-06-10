package ATA8

import chisel3._
import chisel3.util._

class SemaphoreProgPort()(implicit c: Configuration) extends Bundle {
  val initValues = Vec(2, UInt(16.W))
  val addr       = UInt(16.W)
  val generation = UInt(c.semaphoreGenerationWidth.W)
}

class SemaphoreBank(noPorts: Int)(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val inPorts = Vec(noPorts, Flipped(new TilelinkPort))
    val progPort = Flipped(Decoupled(new SemaphoreProgPort))
    val eventPort = Decoupled(new SemaphoreEvent)
  })

  val noSemaphores = c.nSemaphores
  val genWidth     = c.semaphoreGenerationWidth

  val semaphores = VecInit(Seq.tabulate(noSemaphores)(i => Module(new Semaphore(i)).io))

  semaphores.zipWithIndex.foreach { case (sem, i) =>
    sem.progPort.valid             := io.progPort.valid && (io.progPort.bits.addr === i.U)
    sem.progPort.bits.initValues   := io.progPort.bits.initValues
    sem.progPort.bits.generation   := io.progPort.bits.generation
  }

  io.progPort.ready := semaphores(io.progPort.bits.addr).progPort.ready

  val eventArb = Module(new SemaphoreEventArbiter(noSemaphores))
  eventArb.io.in.zip(semaphores).foreach { case (arbIn, sem) => arbIn <> sem.eventPort }
  io.eventPort <> eventArb.io.out


  // Address LSBs: [gen | regSel | portSel | semIdx]. xbar treats gen + regSel as routing don't-care.
  val perSlaveStride = 2 << genWidth
  val perSlaveMask   = perSlaveStride - 1
  val xbarConfig = TLXbarConfig(
    nMasters = noPorts,
    slaves = Seq.tabulate(noSemaphores * 2)(i =>
      TLSlaveConfig(Seq((BigInt(i) * BigInt(perSlaveStride), BigInt(perSlaveMask))))
    ),
    //arbiterPolicy = "lock"
    arbiterPolicy = "roundRobin"
  )(c)

  val xbar = Module(new TLXbar(xbarConfig))

  // Connect external master ports to xbar inputs
  xbar.io.in.zip(io.inPorts).foreach { case (xbarIn, extPort) => xbarIn <> extPort }

  // xbar slave 2*i   -> semaphore i, port 0
  // xbar slave 2*i+1 -> semaphore i, port 1
  semaphores.zipWithIndex.foreach { case (sem, i) =>
    sem.inPorts(0) <> xbar.io.out(2 * i)
    sem.inPorts(1) <> xbar.io.out(2 * i + 1)
  }




}
