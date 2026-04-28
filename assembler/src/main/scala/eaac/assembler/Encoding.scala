package eaac.assembler

import eaac.shared.InstructionSet
import eaac.shared.InstructionSet.{AddrPkg, Execute, Load, Store, DMA, SemProg}

/** Encoding helpers that produce 128-bit instruction words (as BigInt)
  * using the shared InstructionSet layouts.
  */
object Encoding {

  /** Encode an Execute instruction. */
  def encodeExecute(
    func: Int,
    mode: Int,
    size: Int,
    addrs0: BigInt,
    addrs1: BigInt = BigInt(0),
    addrd0: BigInt,
    grainSize: Int = 0,
  ): BigInt = Execute.encode(Map(
    "opcode"    -> BigInt(Execute.opcode),
    "func"      -> BigInt(func),
    "mode"      -> BigInt(mode),
    "size"      -> BigInt(size),
    "addrs0"    -> addrs0,
    "addrs1"    -> addrs1,
    "addrd0"    -> addrd0,
    "grainSize" -> BigInt(grainSize),
  ))

  /** Encode a Load instruction. */
  def encodeLoad(
    func: Int,
    mode: Int,
    size: Int,
    addrd0: BigInt,
  ): BigInt = Load.encode(Map(
    "opcode" -> BigInt(Load.opcode),
    "func"   -> BigInt(func),
    "mode"   -> BigInt(mode),
    "size"   -> BigInt(size),
    "addrd0" -> addrd0,
  ))

  /** Encode a Store instruction. */
  def encodeStore(
    func: Int,
    size: Int,
    addrs0: BigInt,
  ): BigInt = Store.encode(Map(
    "opcode" -> BigInt(Store.opcode),
    "func"   -> BigInt(func),
    "size"   -> BigInt(size),
    "addrs0" -> addrs0,
  ))

  /** Encode a DMA instruction. */
  def encodeDMA(
    func: Int,
    size: Int,
    addrs0: BigInt,
    addrd0: BigInt,
    dmaAddr: Int = 0,
  ): BigInt = DMA.encode(Map(
    "opcode"  -> BigInt(DMA.opcode),
    "func"    -> BigInt(func),
    "size"    -> BigInt(size),
    "addrs0"  -> addrs0,
    "addrd0"  -> addrd0,
    "DMAAddr" -> BigInt(dmaAddr),
  ))

  /** Encode a SemProg instruction.
    * Hardware convention: initValues(0) = fullReg, initValues(1) = emptyReg.
    */
  def encodeSemProg(
    semAddr: Int,
    initEmpty: Int,
    initFull: Int,
  ): BigInt = SemProg.encode(Map(
    "opcode"      -> BigInt(SemProg.opcode),
    "semAddr"     -> BigInt(semAddr),
    "initValues0" -> BigInt(initFull),
    "initValues1" -> BigInt(initEmpty),
  ))

  /** Compute total byte count for a buffer shape + element type. */
  def bufferByteSize(shape: Seq[Long], elementBytes: Int): Long =
    shape.product * elementBytes

  /** Element type byte width (matches eaac_fb.ElementType). */
  def elementBytes(elementType: Byte): Int = elementType match {
    case 0 => 1  // I8
    case 1 => 2  // I16
    case 2 => 4  // I32
    case 3 => 8  // I64
    case 4 => 2  // F16
    case 5 => 4  // F32
    case 6 => 8  // F64
    case _ => throw new IllegalArgumentException(s"Unknown element type: $elementType")
  }
}
