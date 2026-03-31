package ATA8

import chisel3._
import chisel3.util._

object SemaphoreAccessModes {
  val Uninterupted   = 0.U(3.W)
  val RestartOnStep = 1.U(3.W)
}

class semaphoreAccess(implicit c: MemBusConfig) extends Bundle {
  val semEnable   = Bool()
  val mode        = UInt(3.W)
  val semAddr     = UInt(16.W)
  val semStepSize = UInt(16.W)
}

class dmaDescriptor(hasSemaphore: Boolean = true)(implicit c: MemBusConfig) extends Bundle {
  val addr    = UInt(c.addrWidth.W)
  val size    = UInt(24.W)
  val writeEn = Bool()

  val source  = UInt(c.sourceWidth.W)
  val sink    = UInt(c.sourceWidth.W)

  val semaphore = if (hasSemaphore) Some(new semaphoreAccess) else None
} 

class dmaResponse(implicit c: MemBusConfig) extends Bundle {
  val source  = UInt(c.sourceWidth.W)
  val sink    = UInt(c.sourceWidth.W)
  val denied  = UInt(1.W)
  val corrupt = UInt(1.W)
}

class dmaInterface(size: Int = 1, hasSemaphore: Boolean = true)(implicit c: MemBusConfig) extends Bundle {
  val descriptor = Decoupled(Vec(size, new dmaDescriptor(hasSemaphore)))
  val response   = Flipped(Decoupled(new dmaResponse))
}
