package ATA8

import chisel3._
import chisel3.util._

class SemaphoreProgPort() extends Bundle {
  val initValues = Vec(2, UInt(16.W))
  val addr = UInt(16.W)
}

class SemaphoreBank(noPorts: Int)(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val inPorts = Vec(noPorts, Flipped(new TilelinkPort))
    val progPort = Flipped(Decoupled(new SemaphoreProgPort))
  })

  val noSemaphores = 8

  val semaphores = VecInit(Seq.fill(noSemaphores)(Module(new Semaphore()).io))

  val maskedAddr = io.progPort.bits.addr >> 1

  semaphores.zipWithIndex.foreach { case (sem, i) =>
    sem.progPort.valid := io.progPort.valid && (maskedAddr === i.U)
    sem.progPort.bits  := io.progPort.bits.initValues
  }

  io.progPort.ready := semaphores(maskedAddr).progPort.ready


  // Each semaphore has 2 independently addressed ports that alias the same physical registers.
  // Port j of semaphore i occupies addresses (i*4 + j*2) and (i*4 + j*2 + 1).
  // Bit 0 of the address selects the register (full/empty); the semaphore handles arbitration.
  val xbarConfig = TLXbarConfig(
    nMasters = noPorts,
    slaves = Seq.tabulate(noSemaphores * 2)(i => TLSlaveConfig(Seq((BigInt(i * 2), BigInt(0x1))))),
    arbiterPolicy = "lock"
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
