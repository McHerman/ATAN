package eaac.assembler

import eaac.shared.InstructionSet
import eaac.shared.InstructionSet.{AddrPkg, Execute, Load, Store, DMA, SemProg}

/** Encoding helpers that produce variable-length instruction words (as
  * BigInt) using the shared InstructionSet layouts.  Each helper returns
  * an [[Encoded]] carrying the bit pattern and its slot count (1, 2 or 3
  * × 64 b); the assembler packs these into a 128-bit beat stream before
  * writing the binary.  The layout's length and opcode fields are forced
  * by `InstructionLayout.encode`, so callers only fill in payload fields.
  */
object Encoding {

  /** A single encoded instruction together with its slot count. */
  case class Encoded(bits: BigInt, slots: Int) {
    /** Total bit width of this instruction (slots × SlotBits). */
    def bitWidth: Int = slots * InstructionSet.SlotBits
  }

  /** Encode an Execute instruction (3 slots / 192 b). */
  def encodeExecute(
    func: Int,
    mode: Int,
    size: Int,
    addrs0: BigInt,
    addrs1: BigInt = BigInt(0),
    addrd0: BigInt,
  ): Encoded = Encoded(
    Execute.encode(Map(
      "func"   -> BigInt(func),
      "mode"   -> BigInt(mode),
      "size"   -> BigInt(size),
      "addrs0" -> addrs0,
      "addrs1" -> addrs1,
      "addrd0" -> addrd0,
    )),
    Execute.slots,
  )

  /** Encode a Load instruction (2 slots / 128 b). */
  def encodeLoad(
    func: Int,
    mode: Int,
    size: Int,
    addrd0: BigInt,
  ): Encoded = Encoded(
    Load.encode(Map(
      "func"   -> BigInt(func),
      "mode"   -> BigInt(mode),
      "size"   -> BigInt(size),
      "addrd0" -> addrd0,
    )),
    Load.slots,
  )

  /** Encode a Store instruction (2 slots / 128 b). */
  def encodeStore(
    func: Int,
    size: Int,
    addrs0: BigInt,
  ): Encoded = Encoded(
    Store.encode(Map(
      "func"   -> BigInt(func),
      "size"   -> BigInt(size),
      "addrs0" -> addrs0,
    )),
    Store.slots,
  )

  /** Encode a DMA instruction (2 slots / 128 b). */
  def encodeDMA(
    func: Int,
    size: Int,
    addrs0: BigInt,
    addrd0: BigInt,
    dmaAddr: Int = 0,
  ): Encoded = Encoded(
    DMA.encode(Map(
      "func"    -> BigInt(func),
      "size"    -> BigInt(size),
      "addrs0"  -> addrs0,
      "addrd0"  -> addrd0,
      "DMAAddr" -> BigInt(dmaAddr),
    )),
    DMA.slots,
  )

  /** Encode a SemProg instruction (1 slot / 64 b).
    * Hardware convention: initValues(0) = fullReg, initValues(1) = emptyReg.
    */
  def encodeSemProg(
    semAddr: Int,
    initEmpty: Int,
    initFull: Int,
  ): Encoded = Encoded(
    SemProg.encode(Map(
      "semAddr"     -> BigInt(semAddr),
      "initValues0" -> BigInt(initFull),
      "initValues1" -> BigInt(initEmpty),
    )),
    SemProg.slots,
  )

  /** Pack a sequence of encoded instructions into a stream of 128-bit
    * beats.  Instructions are emitted in order at 64-bit-slot granularity;
    * a 3-slot Execute that doesn't begin on an even slot will straddle two
    * beats.  The trailing beat is zero-padded if there's an odd slot at the
    * end (the realigner ignores it because no inst's length code points at
    * the padding).
    */
  def packBeats(instructions: Seq[Encoded]): Seq[BigInt] = {
    val slotMask = (BigInt(1) << InstructionSet.SlotBits) - 1
    val slots = instructions.flatMap { e =>
      (0 until e.slots).map { i => (e.bits >> (i * InstructionSet.SlotBits)) & slotMask }
    }
    val padded = if (slots.size % 2 == 0) slots else slots :+ BigInt(0)
    padded.grouped(2).map { pair =>
      val low :: high :: Nil = pair.toList
      low | (high << InstructionSet.SlotBits)
    }.toSeq
  }

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
