package eaac.shared

/** Pure-Scala instruction layout definitions shared between the Chisel
  * hardware decoder (ctrlDefs.scala) and the software assembler.
  *
  * Any change here is automatically picked up by both sides, keeping
  * hardware and software encodings in sync without manual duplication.
  */
object InstructionSet {

  /** AXI-S beat width feeding the front-end realigner. */
  val BeatBits = 128

  /** Bits reserved for the semaphore gen tag; hardware may use fewer. */
  val SemGenerationBits = 4

  val MaxSemDeps     = 3
  val SemDepAddrBits = 12

  /** Granularity of the variable-length instruction encoding. */
  val SlotBits = 64

  /** Maximum instruction width (Execute = 3 slots). */
  val MaxInstBits = 3 * SlotBits   // 192

  /** A single named bit-field inside an instruction word. */
  case class Field(name: String, startBit: Int, width: Int)

  /** Complete layout for one instruction type. */
  case class InstructionLayout(opcode: Int, slots: Int, fields: Seq[Field]) {
    /** Total bit width occupied by this instruction (1, 2 or 3 × SlotBits). */
    def bitWidth: Int = slots * SlotBits

    for (f <- fields) {
      require(f.startBit + f.width <= bitWidth,
        s"opcode=$opcode field '${f.name}' (bits ${f.startBit}..${f.startBit + f.width - 1}) " +
        s"overflows the $bitWidth-bit ($slots-slot) instruction word")
    }

    /** 2-bit length code stored in the inst's header (slots − 1). */
    def lengthCode: Int = slots - 1

    /** Pack a map of field-name -> value into a BigInt instruction word.
      * The returned BigInt is exactly `bitWidth` bits wide; callers slice
      * it into 64-bit slots before feeding the AXI-S beat stream.
      * `length` and `opcode` are forced from the layout so callers can't
      * accidentally drop them.
      */
    def encode(values: Map[String, BigInt]): BigInt = {
      val withHeader = values +
        ("length" -> BigInt(lengthCode)) +
        ("opcode" -> BigInt(opcode))
      var result = BigInt(0)
      for (field <- fields) {
        val v = withHeader.getOrElse(field.name, BigInt(0))
        val mask = (BigInt(1) << field.width) - 1
        result |= (v & mask) << field.startBit
      }
      result
    }
  }

  // ---------------------------------------------------------------------------
  // Common instruction header (8 bits, shared by every layout):
  //   [1:0]  length   2 bits  : 0 → 1 slot (64 b), 1 → 2 slots (128 b),
  //                              2 → 3 slots (192 b), 3 → reserved
  //   [7:2]  opcode   6 bits
  // ---------------------------------------------------------------------------
  val HeaderBits  = 8
  val LengthField = Field("length", 0, 2)
  val OpcodeField = Field("opcode", 2, 6)

  // ---------------------------------------------------------------------------
  // addrPkg sub-layout (49 bits)
  // Mirrors the Chisel `addrPkg` Bundle in ctrlDefs.scala (first-declared
  // field = MSB). semAddr is 12 bits -> up to 4096 semaphore-bank ports;
  // stepSize is 12 bits, trading the 4 bits it gave up straight to semAddr
  // so the total addrPkg width (and every downstream instruction offset
  // that's computed from AddrPkgWidth) is unchanged.
  // ---------------------------------------------------------------------------
  val AddrWidth    = 23
  val SemAddrBits  = 12
  val SemStepSizeBits = 12
  val AddrPkgWidth = 26 + AddrWidth
  val AddrMask     = (BigInt(1) << AddrWidth) - 1
  val SemAddrMask     = (BigInt(1) << SemAddrBits) - 1
  val SemStepSizeMask = (BigInt(1) << SemStepSizeBits) - 1
  object AddrPkg {
    val addr              = Field("addr",              26, AddrWidth)
    val semValid          = Field("sem.valid",         25,  1)
    val semAddr           = Field("sem.addr",          13,  SemAddrBits)
    val semStepSizeValid  = Field("sem.stepSize.valid", 12,  1)
    val semStepSizeBits   = Field("sem.stepSize.bits",   0,  SemStepSizeBits)

    val fields: Seq[Field] = Seq(addr, semValid, semAddr, semStepSizeValid, semStepSizeBits)

    /** Encode an addrPkg value (AddrPkgWidth-bit BigInt). The `semAddr` field
      * is a byte-level TL address into the semaphore xbar — the assembler is
      * responsible for shifting the semaphore index/port into position and
      * baking the generation tag into the LSBs (the xbar treats those bits
      * as routing don't-care; the semaphore module validates them).
      */
    def encode(
      addr: Int,
      semValid: Boolean = false,
      semAddr: Int = 0,
      stepSizeValid: Boolean = false,
      stepSize: Int = 0,
    ): BigInt = {
      var v = BigInt(0)
      v |= (BigInt(addr) & AddrMask) << 26
      if (semValid) {
        v |= BigInt(1) << 25
        v |= (BigInt(semAddr) & SemAddrMask) << 13
        if (stepSizeValid) {
          v |= BigInt(1) << 12
          v |= (BigInt(stepSize) & SemStepSizeMask)
        }
      }
      v
    }

    /** Encode a simple address-only addrPkg. */
    def simple(addr: Int): BigInt = encode(addr)
  }

  // ---------------------------------------------------------------------------
  // Instruction layouts
  // Opcodes: 1=Execute, 2=Load, 3=Store, 4=DMA, 5=SemProg
  //
  // Bit layout convention:
  //   [1:0]   length
  //   [7:2]   opcode
  //   [8]     func        (where applicable)
  //   [9]     mode        (Execute / Load only; reserved otherwise)
  //   [25:10] size        (16 bits, where applicable)
  //   [...]   addrPkg(s)  (AddrPkgWidth bits each, packed contiguously after
  //           the header -- offsets below are computed from AddrPkgWidth so
  //           widening AddrWidth doesn't require re-deriving every literal.
  //
  // Sizes (at AddrWidth=23, AddrPkgWidth=49, SemAddrBits=12, SemDepAddrBits=12):
  //   SemProg : 96 bits used  → 2 slots (length code 1)
  //   Store   : 75 bits used  → 2 slots (length code 1)
  //   Load    : 75 bits used  → 2 slots (length code 1)
  //   DMA     : 128 bits used → 2 slots (length code 1, zero slack left)
  //   Execute : 189 bits used → 3 slots (length code 2)
  // ---------------------------------------------------------------------------

  val Execute = InstructionLayout(1, slots = 3, Seq(
    LengthField,
    OpcodeField,
    Field("func",     8,  1),
    Field("mode",     9,  1),
    // Decoupled size and row count
    Field("size",    10, 16),
    Field("addrs0",  26,                     AddrPkgWidth),
    Field("addrs1",  26 +     AddrPkgWidth,   AddrPkgWidth),
    Field("addrd0",  26 + 2 * AddrPkgWidth,   AddrPkgWidth),
    Field("rows",    26 + 3 * AddrPkgWidth,   16),
  ))

  val Load = InstructionLayout(2, slots = 2, Seq(
    LengthField,
    OpcodeField,
    Field("func",    8,  1),
    Field("mode",    9,  1),
    Field("size",   10, 16),
    Field("addrd0", 26, AddrPkgWidth),
  ))

  val Store = InstructionLayout(3, slots = 2, Seq(
    LengthField,
    OpcodeField,
    Field("func",    8,  1),
    Field("size",   10, 16),
    Field("addrs0", 26, AddrPkgWidth),
  ))

  val DMA = InstructionLayout(4, slots = 2, Seq(
    LengthField,
    OpcodeField,
    Field("func",     8,  1),
    Field("size",    10, 16),
    Field("addrs0",  26,                   AddrPkgWidth),
    Field("addrd0",  26 +     AddrPkgWidth, AddrPkgWidth),
    Field("DMAAddr", 26 + 2 * AddrPkgWidth, 4),
  ))

  val EventModeBits = 2

  // Offsets derived from SemAddrBits/SemGenerationBits/EventModeBits so
  // widening any one of them (as happened when semAddr grew from 8 to 12
  // bits) can't silently leave a later field's start-bit stale.
  private val SemProgInitFullBit  = 8 + SemAddrBits
  private val SemProgInitEmptyBit = SemProgInitFullBit + 16
  private val SemProgGenBit       = SemProgInitEmptyBit + 16
  private val SemProgEventModeBit = SemProgGenBit + SemGenerationBits
  private val SemProgDepCountBit  = SemProgEventModeBit + EventModeBits
  private val SemProgDepsBit      = SemProgDepCountBit + 2

  val SemProg = InstructionLayout(5, slots = 2, Seq(
    LengthField,
    OpcodeField,
    Field("semAddr",     8,                  SemAddrBits),
    Field("initFull",    SemProgInitFullBit,  16),
    Field("initEmpty",   SemProgInitEmptyBit, 16),
    Field("generation",  SemProgGenBit,       SemGenerationBits),
    Field("eventMode",   SemProgEventModeBit, EventModeBits),
    Field("depCount",    SemProgDepCountBit,  2),
    Field("dep0",        SemProgDepsBit + 0 * SemDepAddrBits, SemDepAddrBits),
    Field("dep1",        SemProgDepsBit + 1 * SemDepAddrBits, SemDepAddrBits),
    Field("dep2",        SemProgDepsBit + 2 * SemDepAddrBits, SemDepAddrBits),
  ))

  /** All instruction layouts indexed by opcode. */
  val All: Map[Int, InstructionLayout] = Seq(Execute, Load, Store, DMA, SemProg)
    .map(l => l.opcode -> l).toMap

  /** Map a 2-bit length code back to its slot count. */
  def slotsForLength(code: Int): Int = code + 1
}
