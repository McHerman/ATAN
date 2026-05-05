package eaac.shared

/** Pure-Scala instruction layout definitions shared between the Chisel
  * hardware decoder (ctrlDefs.scala) and the software assembler.
  *
  * Any change here is automatically picked up by both sides, keeping
  * hardware and software encodings in sync without manual duplication.
  */
object InstructionSet {
  val INST_WIDTH = 128

  /** A single named bit-field inside an instruction word. */
  case class Field(name: String, startBit: Int, width: Int)

  /** Complete layout for one instruction type. */
  case class InstructionLayout(opcode: Int, fields: Seq[Field]) {
    /** Pack a map of field-name -> value into a BigInt instruction word. */
    def encode(values: Map[String, BigInt]): BigInt = {
      var result = BigInt(0)
      for (field <- fields) {
        val v = values.getOrElse(field.name, BigInt(0))
        val mask = (BigInt(1) << field.width) - 1
        result |= (v & mask) << field.startBit
      }
      result
    }
  }

  // ---------------------------------------------------------------------------
  // addrPkg sub-layout (37 bits)
  // Mirrors the Chisel `addrPkg` Bundle in ctrlDefs.scala.
  // semAddr is 5 bits to address up to 32 semaphore-bank ports
  // (8 semaphores × 4 ports/sem in SemaphoreBank.scala).
  // ---------------------------------------------------------------------------
  val AddrPkgWidth = 37
  object AddrPkg {
    val addr              = Field("addr",              21, 16)
    val semValid          = Field("sem.valid",         20,  1)
    val semAddr           = Field("sem.addr",          15,  5)
    val semStepSizeValid  = Field("sem.stepSize.valid", 14,  1)
    val semStepSizeBits   = Field("sem.stepSize.bits",   0, 14)

    val fields: Seq[Field] = Seq(addr, semValid, semAddr, semStepSizeValid, semStepSizeBits)

    /** Encode an addrPkg value (37-bit BigInt). */
    def encode(
      addr: Int,
      semValid: Boolean = false,
      semAddr: Int = 0,
      stepSizeValid: Boolean = false,
      stepSize: Int = 0,
    ): BigInt = {
      var v = BigInt(0)
      v |= BigInt(addr & 0xFFFF) << 21
      if (semValid) {
        v |= BigInt(1) << 20
        v |= BigInt(semAddr & 0x1F) << 15
        if (stepSizeValid) {
          v |= BigInt(1) << 14
          v |= BigInt(stepSize & 0x3FFF)
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
  // ---------------------------------------------------------------------------

  val Execute = InstructionLayout(1, Seq(
    Field("opcode",  0, 6),
    Field("func",    6, 1),
    Field("mode",    7, 1),
    Field("size",    8, 8),
    Field("addrs0", 16, AddrPkgWidth),
    Field("addrs1", 53, AddrPkgWidth),
    Field("addrd0", 90, AddrPkgWidth),
  ))

  val Load = InstructionLayout(2, Seq(
    Field("opcode",  0, 6),
    Field("func",    6, 1),
    Field("mode",    7, 1),
    Field("size",    8, 8),
    Field("addrd0", 16, AddrPkgWidth),
  ))

  val Store = InstructionLayout(3, Seq(
    Field("opcode", 0, 6),
    Field("func",   6, 1),
    Field("size",   8, 8),
    Field("addrs0", 16, AddrPkgWidth),
  ))

  val DMA = InstructionLayout(4, Seq(
    Field("opcode",  0, 6),
    Field("func",    6, 1),
    Field("size",    8, 8),
    Field("addrs0", 16, AddrPkgWidth),
    Field("addrd0", 53, AddrPkgWidth),
    Field("DMAAddr", 90, 4),
  ))

  val SemProg = InstructionLayout(5, Seq(
    Field("opcode",      0, 6),
    Field("semAddr",     6, 8),
    Field("initValues0", 14, 16),
    Field("initValues1", 30, 16),
  ))

  /** All instruction layouts indexed by opcode. */
  val All: Map[Int, InstructionLayout] = Seq(Execute, Load, Store, DMA, SemProg)
    .map(l => l.opcode -> l).toMap
}
