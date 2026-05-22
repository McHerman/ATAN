package ATA8

import chisel3._
import chisel3.util._
import eaac.shared.InstructionSet

/** Raw 128-bit AXI-S beat as it lands in the front-end, before realignment. */
class InstBeat extends Bundle {
  val data = UInt(InstructionSet.BeatBits.W)
}

/** Realigned, slot-aligned instruction word handed to the decoder.
  * Width = MaxInstBits (192); shorter forms (64/128 b) leave the upper
  * slots zero — the `length` field tells the decoder how many slots to use.
  */
class InstructionPackage extends Bundle {
  val instruction = UInt(InstructionSet.MaxInstBits.W)
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
  val sem = Valid(new Bundle { val addr = UInt(8.W); val stepSize = Valid(UInt(16.W)) })
}

abstract class InstBase(implicit c: Configuration) extends Bundle {
  val length = UInt(2.W)
  val opcode = UInt(6.W)
}

abstract class InstBaseExtended(addrsSize: Int, addrdSize: Int)(implicit c: Configuration) extends InstBase {
  val func = UInt(1.W)
  val size = UInt(16.W)
  val addrs = Vec(addrsSize, new addrPkg())
  val addrd = Vec(addrdSize, new addrPkg())
}

class ExecuteInst(implicit c: Configuration) extends InstBaseExtended(2, 1) with Decodable {
  val mode = UInt(1.W)

  private val fieldMap: Map[String, Data] = Map(
    "length" -> length, "opcode" -> opcode, "func" -> func, "mode" -> mode,
    "size" -> size, "addrs0" -> addrs(0), "addrs1" -> addrs(1),
    "addrd0" -> addrd(0),
  )

  def layout = InstructionSet.Execute.fields.map { f =>
    fieldMap(f.name) -> f.startBit
  }
}

class LoadInst(implicit c: Configuration) extends InstBaseExtended(0, 1) with Decodable {
  val mode = UInt(1.W)

  private val fieldMap: Map[String, Data] = Map(
    "length" -> length, "opcode" -> opcode, "func" -> func, "mode" -> mode,
    "size" -> size, "addrd0" -> addrd(0),
  )

  def layout = InstructionSet.Load.fields.map { f =>
    fieldMap(f.name) -> f.startBit
  }
}

class StoreInst(implicit c: Configuration) extends InstBaseExtended(1, 0) with Decodable {
  val mode = UInt(1.W)

  private val fieldMap: Map[String, Data] = Map(
    "length" -> length, "opcode" -> opcode, "func" -> func,
    "size" -> size, "addrs0" -> addrs(0),
  )

  def layout = InstructionSet.Store.fields.map { f =>
    fieldMap(f.name) -> f.startBit
  }
}

class DMAInst(implicit c: Configuration) extends InstBaseExtended(1, 1) with Decodable {
  val DMAAddr = UInt(4.W)

  private val fieldMap: Map[String, Data] = Map(
    "length" -> length, "opcode" -> opcode, "func" -> func,
    "size" -> size, "addrs0" -> addrs(0),
    "addrd0" -> addrd(0), "DMAAddr" -> DMAAddr,
  )

  def layout = InstructionSet.DMA.fields.map { f =>
    fieldMap(f.name) -> f.startBit
  }
}

class SemProgInst(implicit c: Configuration) extends InstBase with Decodable {
  val semAddr = UInt(8.W)
  val initValues = Vec(2, UInt(16.W))

  private val fieldMap: Map[String, Data] = Map(
    "length" -> length, "opcode" -> opcode, "semAddr" -> semAddr,
    "initValues0" -> initValues(0), "initValues1" -> initValues(1),
  )

  def layout = InstructionSet.SemProg.fields.map { f =>
    fieldMap(f.name) -> f.startBit
  }
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
