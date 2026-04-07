package ATA8

import chisel3._
import chisel3.util._

class InstructionPackage extends Bundle {
  val instruction = UInt(128.W)
}

trait Decodable { self: Bundle =>
  def layout: Seq[(Data, Int)] // (field, startBit)

  def decodeFrom(raw: UInt): Unit = {
    layout.foreach { case (field, startBit) =>
      field := raw(startBit + field.getWidth - 1, startBit).asTypeOf(field)
    }
  }
}

class addrPkg(implicit c: Configuration) extends Bundle {
  val addr = UInt(16.W)
  val sem = Valid(new Bundle { val addr = UInt(4.W); val stepSize = Valid(UInt(14.W)) })
}

abstract class InstBase(implicit c: Configuration) extends Bundle {
  val opcode = UInt(6.W)
}

abstract class InstBaseExtended(addrsSize: Int, addrdSize: Int)(implicit c: Configuration) extends InstBase {
  val func = UInt(1.W)
  val size = UInt(8.W)
  val addrs = Vec(addrsSize, new addrPkg())
  val addrd = Vec(addrdSize, new addrPkg())
}

class ExecuteInst(implicit c: Configuration) extends InstBaseExtended(2, 1) with Decodable {
  val mode      = UInt(1.W)
  val grainSize = UInt(4.W)

  def layout = Seq(
    opcode    -> 0,
    func      -> 6,
    mode      -> 7,
    size      -> 8,
    addrs(0)  -> 16,
    addrs(1)  -> 52,
    addrd(0)  -> 88,
    grainSize -> 124,
  )
}

class LoadInst(implicit c: Configuration) extends InstBaseExtended(0, 1) with Decodable {
  val mode = UInt(1.W)

  def layout = Seq(
    opcode   -> 0,
    func     -> 6,
    mode     -> 7,
    size     -> 8,
    addrd(0) -> 16,
  )
}

class StoreInst(implicit c: Configuration) extends InstBaseExtended(1, 0) with Decodable {
  val mode = UInt(1.W) //Technically not needed, whole decoder is a big mess anyways 

  def layout = Seq(
    opcode   -> 0,
    func     -> 6,
    size     -> 8,
    addrs(0) -> 16,
  )
}

class DMAInst(implicit c: Configuration) extends InstBaseExtended(1, 1) with Decodable {
  val DMAAddr = UInt(4.W)

  def layout = Seq(
    opcode  -> 0,
    func    -> 6,
    size    -> 8,
    addrs(0) -> 16,
    addrd(0) -> 52,
    DMAAddr -> 88,
  )
}

class SemProgInst(implicit c: Configuration) extends InstBase with Decodable {
  val semAddr = UInt(8.W)
  val initValues = Vec(2, UInt(16.W))

  def layout = Seq(
    opcode  -> 0,
    semAddr -> 6,
    initValues -> 14,
  )
}

class SysOP(implicit c: Configuration) extends Bundle {
  val mode = UInt(1.W)
  val size = UInt(8.W)
  val sizes = Vec(c.grainDim, UInt(log2Ceil(c.dataBusSize + 1).W))
}

class DMARead(implicit c: Configuration) extends Bundle {
  val request = Decoupled(new Bundle {
    val addr = UInt(16.W)
    val burstCnt = UInt(8.W)
    val burstSize = UInt(log2Ceil(c.dataBusSize + 1).W)
  })
  val response = Flipped(Decoupled(new Bundle {
    val completed = Bool()
  }))
}

class DMAWrite(implicit c: Configuration) extends Bundle {
  val request = Decoupled(new Bundle {
    val addr = UInt(16.W)
    val burstCnt = UInt(8.W)
    val burstSize = UInt(log2Ceil(c.dataBusSize + 1).W)
  })
  val response = Flipped(Decoupled(new Bundle {
    val completed = Bool()
  }))
}
