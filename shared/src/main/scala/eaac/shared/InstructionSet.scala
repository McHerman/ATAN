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
  // addrPkg sub-layout (42 bits)
  // Mirrors the Chisel `addrPkg` Bundle in ctrlDefs.scala (first-declared
  // field = MSB).  semAddr is 8 bits → up to 256 semaphore-bank ports.
  // ---------------------------------------------------------------------------
  val AddrPkgWidth = 42
  object AddrPkg {
    val addr              = Field("addr",              26, 16)
    val semValid          = Field("sem.valid",         25,  1)
    val semAddr           = Field("sem.addr",          17,  8)
    val semStepSizeValid  = Field("sem.stepSize.valid", 16,  1)
    val semStepSizeBits   = Field("sem.stepSize.bits",   0, 16)

    val fields: Seq[Field] = Seq(addr, semValid, semAddr, semStepSizeValid, semStepSizeBits)

    /** Encode an addrPkg value (42-bit BigInt). The `semAddr` field is a
      * byte-level TL address into the semaphore xbar — the assembler is
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
      v |= BigInt(addr & 0xFFFF) << 26
      if (semValid) {
        v |= BigInt(1) << 25
        v |= BigInt(semAddr & 0xFF) << 17
        if (stepSizeValid) {
          v |= BigInt(1) << 16
          v |= BigInt(stepSize & 0xFFFF)
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
  //   [...]   addrPkg(s)  (42 bits each, packed contiguously after the header)
  //
  // Sizes:
  //   SemProg : 48 bits used → 1 slot   (length code 0)
  //   Store   : 67 bits used → 2 slots  (length code 1)
  //   Load    : 68 bits used → 2 slots  (length code 1)
  //   DMA     : 113 bits used → 2 slots (length code 1)
  //   Execute : 152 bits used → 3 slots (length code 2)
  // ---------------------------------------------------------------------------

  val Execute = InstructionLayout(1, slots = 3, Seq(
    LengthField,
    OpcodeField,
    Field("func",     8,  1),
    Field("mode",     9,  1),
    Field("size",    10, 16),
    Field("addrs0",  26, AddrPkgWidth),  // bits 26..67
    Field("addrs1",  68, AddrPkgWidth),  // bits 68..109
    Field("addrd0", 110, AddrPkgWidth),  // bits 110..151
  ))

  val Load = InstructionLayout(2, slots = 2, Seq(
    LengthField,
    OpcodeField,
    Field("func",    8,  1),
    Field("mode",    9,  1),
    Field("size",   10, 16),
    Field("addrd0", 26, AddrPkgWidth),   // bits 26..67
  ))

  val Store = InstructionLayout(3, slots = 2, Seq(
    LengthField,
    OpcodeField,
    Field("func",    8,  1),
    Field("size",   10, 16),
    Field("addrs0", 26, AddrPkgWidth),   // bits 26..67
  ))

  val DMA = InstructionLayout(4, slots = 2, Seq(
    LengthField,
    OpcodeField,
    Field("func",     8,  1),
    Field("size",    10, 16),
    Field("addrs0",  26, AddrPkgWidth),  // bits 26..67
    Field("addrd0",  68, AddrPkgWidth),  // bits 68..109
    Field("DMAAddr", 110, 4),
  ))

  val SemProg = InstructionLayout(5, slots = 1, Seq(
    LengthField,
    OpcodeField,
    Field("semAddr",      8,  8),
    Field("initValues0", 16, 16),
    Field("initValues1", 32, 16),
    Field("generation",  48, SemGenerationBits),
  ))

  /** All instruction layouts indexed by opcode. */
  val All: Map[Int, InstructionLayout] = Seq(Execute, Load, Store, DMA, SemProg)
    .map(l => l.opcode -> l).toMap

  /** Map a 2-bit length code back to its slot count. */
  def slotsForLength(code: Int): Int = code + 1
}
