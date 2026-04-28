package eaac.assembler

import eaac.shared.InstructionSet
import eaac.shared.InstructionSet.{AddrPkg, Field, InstructionLayout}

object PrettyPrinter {

  private val opcodeNames: Map[Int, String] = Map(
    1 -> "Execute",
    2 -> "Load",
    3 -> "Store",
    4 -> "DMA",
    5 -> "SemProg",
  )

  private val addrPkgFields: Seq[Field] = AddrPkg.fields

  def extractField(inst: BigInt, field: Field): BigInt =
    (inst >> field.startBit) & ((BigInt(1) << field.width) - 1)

  def formatAddrPkg(value: BigInt): String = {
    val addr     = extractField(value, AddrPkg.addr)
    val semV     = extractField(value, AddrPkg.semValid)
    val semA     = extractField(value, AddrPkg.semAddr)
    val stepV    = extractField(value, AddrPkg.semStepSizeValid)
    val stepSize = extractField(value, AddrPkg.semStepSizeBits)

    val sb = new StringBuilder
    sb ++= f"addr=0x${addr}%04x"
    if (semV != 0) {
      sb ++= f", sem=$semA%d"
      if (stepV != 0) sb ++= f", step=$stepSize%d"
    }
    sb.result()
  }

  def formatInstruction(inst: BigInt): String = {
    val opcode = (inst & 0x3F).toInt
    val layout = InstructionSet.All.getOrElse(opcode,
      return f"0x${inst}%032x  UNKNOWN opcode=$opcode"
    )
    val name = opcodeNames.getOrElse(opcode, s"Op$opcode")

    val parts = layout.fields.collect {
      case f if f.name == "opcode" => None
      case f if isAddrPkgField(f) =>
        val raw = extractField(inst, f)
        Some(s"${f.name}={${formatAddrPkg(raw)}}")
      case f =>
        val v = extractField(inst, f)
        Some(f"${f.name}=${v}%d")
    }.flatten

    f"$name%-8s ${parts.mkString(", ")}"
  }

  private def isAddrPkgField(f: Field): Boolean =
    f.name.startsWith("addr") && f.width == InstructionSet.AddrPkgWidth

  def prettyPrint(program: Assembler#AssembledProgram): String = {
    val sb = new StringBuilder
    for (fn <- program.functions) {
      sb ++= s"Function '${fn.name}' (${fn.instructions.length} instructions):\n"
      for ((inst, i) <- fn.instructions.zipWithIndex) {
        sb ++= f"  [$i%3d] ${formatInstruction(inst)}%s\n"
        sb ++= f"         raw: 0x${inst}%032x\n"
      }
    }
    sb ++= s"Total: ${program.allInstructions.length} instructions\n"
    sb.result()
  }
}
