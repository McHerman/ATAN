package ATA8

import chisel3._
import chisel3.util._

class dmaDescriptor(implicit c: Configuration) extends Bundle {
  val addr = UInt(c.addrWidth.W)
  val size = UInt(24.W)
  val writeEn = Bool()

  val source  = UInt(c.sourceWidth.W)
  val sink    = UInt(c.sourceWidth.W)
} 

class dmaResponse(implicit c: Configuration) extends Bundle {
  val source  = UInt(c.sourceWidth.W)
  val sink    = UInt(c.sourceWidth.W)
  val denied  = UInt(1.W)
  val corrupt = UInt(1.W)
}

class dmaInterface(size: Int)(implicit c: Configuration) extends Bundle {
  val descriptor = Decoupled(Vec(size,new dmaDescriptor))
  val response = Flipped(Decoupled(new dmaResponse))
}
