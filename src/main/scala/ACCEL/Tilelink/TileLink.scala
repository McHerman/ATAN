package ATA8

import chisel3._
import chisel3.util._

object TilelinkOpcodes {
  // A channel opcodes
  val PutFullData    = 0.U(3.W)
  val PutPartialData = 1.U(3.W)
  val ArithmeticData = 2.U(3.W)
  val LogicalData    = 3.U(3.W)
  val Get            = 4.U(3.W)
  // D channel opcodes
  val AccessAck      = 0.U(3.W)
  val AccessAckData  = 1.U(3.W)
}

object ArithmeticDataParam {
  val MIN     = 0.U(3.W)
  val MAX     = 1.U(3.W)
  val MINU    = 2.U(3.W)
  val MAXU    = 3.U(3.W)
  val ADD     = 4.U(3.W)
  val AQGREQ  = 6.U(3.W)
}

object LogicalDataParam {
  val XOR     = 0.U(3.W)
  val OR      = 1.U(3.W)
  val AND     = 2.U(3.W)
  val SWAP    = 3.U(3.W)
}

class TilelinkA(tl: TLBusConfig) extends Bundle {
  val opcode  = UInt(3.W)
  val param   = UInt(3.W)
  val size    = UInt(24.W)
  val source  = UInt(tl.sourceWidth.W)
  val address = UInt(tl.addrWidth.W)
  val mask    = UInt(tl.dataBusSize.W)
  val data    = UInt((tl.dataBusSize * 8).W)
  val corrupt = UInt(1.W)
}

class TilelinkD(tl: TLBusConfig) extends Bundle {
  val opcode  = UInt(3.W)
  val param   = UInt(2.W)
  val size    = UInt(24.W)
  val source  = UInt(tl.sourceWidth.W)
  val sink    = UInt(tl.sourceWidth.W)
  val denied  = UInt(1.W)
  val data    = UInt((tl.dataBusSize * 8).W)
  val corrupt = UInt(1.W)
}

class TilelinkPort(tl: TLBusConfig) extends Bundle {
  val a = Decoupled(new TilelinkA(tl))
  val d = Flipped(Decoupled(new TilelinkD(tl)))
}
