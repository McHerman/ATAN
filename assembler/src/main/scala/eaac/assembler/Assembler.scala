package eaac.assembler

import eaac.shared.InstructionSet
import eaac.shared.InstructionSet.{AddrPkg, Field}
import eaac_fb._

import java.nio.ByteBuffer
import scala.collection.mutable.ArrayBuffer

/** Hardware parameters the assembler needs to produce correct encodings. */
case class AssemblerConfig(
  /** Bytes per data bus beat (default 8 for 64-bit AXI). */
  dataBusBytes: Int = 8,
  /** Number of memory tiers in the hardware (used to map FB tier numbering to hardware tier indices). */
  nTiers: Int = 3,
  /** Width of the generation tag (in bits) baked into the LSBs of every
    * semaphore TL address. Must match `SemaphoreParams.generationWidth`
    * on the hardware side. With the default 0 the address layout reduces
    * to the legacy `semAddr * 4 + 0/2` form.
    */
  semaphoreGenerationWidth: Int = 0,
  /** When true, the assembler prints a decoded trace of each lowered instruction. */
  verbose: Boolean = false,
)

object AssemblerConfig {
  val default: AssemblerConfig = AssemblerConfig()
}

/** Assembles an EAAC FlatBuffer program into a stream of 128-bit beats.
  *
  * Each lowered instruction is variable length (1, 2 or 3 × 64 b) per the
  * shared `InstructionSet` layouts; the assembler packs them slot-by-slot
  * into the AXI-S beat stream that the front-end realigner consumes.
  * `AssembledFunction.instructions` now holds the packed 128-bit beats —
  * downstream callers iterate beats and stream them as-is.
  */
class Assembler(config: AssemblerConfig = AssemblerConfig.default) {

  /** Result of assembling a single function. */

  /** Per-semaphore-index fused address of the most recent SemAlloc seen so
    * far. The next SemAlloc on the same physical address auto-deps on this
    * predecessor so the trigger waits for the previous tenant's Complete
    * event before re-programming.
    */
  private val semAllocPrevFused = scala.collection.mutable.Map.empty[Int, Int]

  /** (semAddr, gen) → union of chain_addresses contributed by every Execute
    * SemDep that targets this slot. Populated by `collectSemDepChainAddrs`
    * before lowering, consumed in `lowerSemAlloc` so each SemAlloc waits for
    * its consumers' upstream chains too.
    */
  private val semDepChainAddrs =
    scala.collection.mutable.Map.empty[(Int, Int), Set[Int]]

  case class Preload(
    shape: Seq[Int],
    offsetAddress: BigInt,
    tier: Int,
    elementType: Byte,
    data: Seq[Byte]
  )

  case class AssembledFunction(
    name: String,
    /** Packed 128-bit beats ready to stream to the accelerator's AXI-S port. */
    instructions: Seq[BigInt],
    preloads: Seq[Preload]
  )


  case class Lowered(
    /** Per-source-op encoded instructions, kept around for verbose tracing. */
    instructions: Seq[Encoding.Encoded],
    preloads: Seq[Preload]
  )

  /** Result of assembling a full program. */
  case class AssembledProgram(
    functions: Seq[AssembledFunction],
  ) {
    def allInstructions: Seq[BigInt] = functions.flatMap(_.instructions)
    def allPreloads: Seq[Preload] = functions.flatMap(_.preloads)
  }

  /** Assemble a FlatBuffer binary into instruction words. */
  def assemble(buf: ByteBuffer): AssembledProgram = {
    val program = Program.getRootAsProgram(buf)
    val consts = (0 until program.constantsLength()).map(program.constants(_))
    val funcs = (0 until program.functionsLength()).map { i =>
      assembleFunction(program.functions(i), consts)
    }
    val assembled = AssembledProgram(funcs)
    if (config.verbose) println(s"Total: ${assembled.allInstructions.length} packed beats")
    assembled
  }

  private def assembleFunction(fn: eaac_fb.Function, const: Seq[eaac_fb.Constant]): AssembledFunction = {
    val buffers = (0 until fn.buffersLength()).map(fn.buffers(_))
    val encoded = new ArrayBuffer[Encoding.Encoded]()
    val preloads = new ArrayBuffer[Preload]()
    if (config.verbose) println(s"Function '${fn.name()}' (${fn.opsLength()} ops):")
    for (i <- 0 until fn.opsLength()) {
      val op = fn.ops(i)
      val lowered = lowerCommand(op, buffers, const)
      if (config.verbose) traceLowered(op, lowered, encoded.length)
      encoded ++= lowered.instructions
      preloads ++= lowered.preloads
    }
    val beats = Encoding.packBeats(encoded.toSeq)
    AssembledFunction(fn.name(), beats, preloads.toSeq)
  }

  private def lowerCommand(op: Operation, buffers: Seq[BufferRef], const: Seq[eaac_fb.Constant]): Lowered = op.commandType() match {
    case Command.SemAlloc   => lowerSemAlloc(op)
    case Command.SemDealloc => Lowered(Seq.empty, Seq.empty)
    case Command.Execute    => lowerExecute(op, buffers)
    case Command.MemAlloc   => lowerMemoryAlloc(op, buffers, const)
    case Command.MemDealloc => Lowered(Seq.empty, Seq.empty)
    case other => throw new IllegalArgumentException(s"Unknown command type: $other")
  }

  // ── SemAlloc / SemDealloc ─────────────────────────────────────────────

  // Doesnt get lowered into a instruction, but rather a host preload
  private def lowerMemoryAlloc(op: Operation, buffers: Seq[BufferRef], const: Seq[eaac_fb.Constant]): Lowered = {

    val alloc = op.command(new MemAlloc()).asInstanceOf[MemAlloc]
    val buf = buffers(alloc.bufferId().toInt)
    val constantName = buf.constantName()

    if (constantName == null) return Lowered(Seq.empty, Seq.empty)

    val constant = const.find(_.name() == constantName).getOrElse(
      throw new IllegalArgumentException(s"Constant '$constantName' not found")
    )

    val shape = (0 until buf.shapeLength()).map(buf.shape(_).toInt)
    val data = (0 until constant.dataLength()).map(constant.data(_).toByte)
    val preload = Preload(shape, buf.offset(), buf.tier(), buf.elementType(), data)
    Lowered(Seq.empty, Seq(preload))
  }

  private def lowerSemAlloc(op: Operation): Lowered = {
    val sem      = op.command(new SemAlloc()).asInstanceOf[SemAlloc]
    val fused    = sem.fusedAddress()
    val (semIdx, gen) = decomposeFused(fused)
    val eventMode = sem.eventMode()
    val prevDep      = semAllocPrevFused.get(semIdx).toSeq
    val explicitDeps = (0 until sem.chainsLength()).map(sem.chains(_).toInt)
    val deps         = (prevDep ++ explicitDeps).distinct

    semAllocPrevFused(semIdx) = fused

    Lowered(Seq(Encoding.encodeSemProg(
      semAddr    = semIdx,
      initEmpty  = sem.emptyCount().toInt,
      initFull   = sem.fullCount().toInt,
      generation = gen,
      eventMode  = eventMode,
      deps       = deps,
    )), Seq.empty)
  }

  /** Split a fused trigger-state index back into `(semIdx, generation)` for
    * the hardware-side SemProg slots that still keep them apart.
    */
  private def decomposeFused(fused: Int): (Int, Int) = {
    val gw   = config.semaphoreGenerationWidth
    val mask = (1 << gw) - 1
    (fused >>> gw, fused & mask)
  }

  /*
  private def lowerSemDealloc(op: Operation): Lowered = {
    val sem = op.command(new SemDealloc()).asInstanceOf[SemDealloc]
    Lowered(Seq(Encoding.encodeSemProg(
      semAddr   = sem.address(),
      initEmpty = 0,
      initFull  = 0,
    )), Seq.empty)
  }
  */

  // ── Execute (DMA or Compute) ──────────────────────────────────────────

  private def lowerExecute(op: Operation, buffers: Seq[BufferRef]): Lowered = {
    val exec = op.command(new Execute()).asInstanceOf[Execute]
    exec.payloadType() match {
      case ExecutePayload.DmaStart => lowerDmaPayload(exec, buffers)
      case ExecutePayload.LoadOp  => lowerLoadPayload(exec, buffers)
      case ExecutePayload.StoreOp => lowerStorePayload(exec, buffers)
      case ExecutePayload.Matmul   => lowerMatmulPayload(exec, buffers)
      case other => throw new IllegalArgumentException(s"Unknown execute payload type: $other")
    }
  }

  private def lowerDmaPayload(exec: Execute, buffers: Seq[BufferRef]): Lowered = {
    val dma = exec.payload(new DmaStart()).asInstanceOf[DmaStart]
    val srcId = dma.src().toInt
    val dstId = dma.dst().toInt
    val src = buffers(srcId)
    val dst = buffers(dstId)

    val src_tier = src.tier
    val dst_tier = dst.tier

    require(
      math.abs(src_tier - dst_tier) == 1,
      s"DMA src tier ($src_tier) and dst tier ($dst_tier) must differ by exactly 1",
    )

    // DMA[i] bridges tier i and tier i+1 via port_A (addrs0, lower tier) and
    // port_B (addrd0, higher tier).
    val dmaAddr = math.min(src_tier, dst_tier)

    // func = 0: addrs0 reads (lower tier), addrd0 writes (higher tier)  → downward
    // func = 1: addrs0 writes (lower tier), addrd0 reads (higher tier)  → upward
    val dma_func = if (src_tier > dst_tier) 1 else 0

    val srcSem = findSemForBuffer(exec, isAcquire = false, srcId)
    val dstSem = findSemForBuffer(exec, isAcquire = true, dstId)
    val totalBytes = bufferTotalBytes(src).toInt

    // Buffers/semaphores are routed by tier, not by src/dst role: the buffer
    // sitting on the lower tier always goes on addrs0, the one on the higher
    // tier always goes on addrd0. When the direction flips (upward), this
    // swaps which port carries the producer vs. consumer semaphore.
    val (lowBuf, lowSem, highBuf, highSem) =
      if (src_tier < dst_tier) (src, srcSem, dst, dstSem)
      else                     (dst, dstSem, src, srcSem)

    Lowered(Seq(Encoding.encodeDMA(
      func    = dma_func,
      size    = totalBytes,
      addrs0  = encodeBufferAddr(lowBuf,  lowSem),
      addrd0  = encodeBufferAddr(highBuf, highSem),
      dmaAddr = dmaAddr,
    )), Seq.empty)
  }

  private def lowerLoadPayload(exec: Execute, buffers: Seq[BufferRef]): Lowered = {
    val load = exec.payload(new LoadOp()).asInstanceOf[LoadOp]
    val dstId = load.dst().toInt
    val dst = buffers(dstId)

    // Acquire means that the give command produces data
    val dstSem = findSemForBuffer(exec, isAcquire = true, dstId)
    val totalBytes = bufferTotalBytes(dst).toInt

    Lowered(Seq(Encoding.encodeLoad(
      func   = 0,
      mode   = 0,
      size   = totalBytes,
      addrd0 = encodeBufferAddr(dst, dstSem),
    )), Seq.empty)
  }

  private def lowerStorePayload(exec: Execute, buffers: Seq[BufferRef]): Lowered = {
    val store = exec.payload(new StoreOp()).asInstanceOf[StoreOp]
    val srcId = store.src().toInt
    val src = buffers(srcId)

    val srcSem = findSemForBuffer(exec, isAcquire = false, srcId)
    val totalBytes = bufferTotalBytes(src).toInt

    Lowered(Seq(Encoding.encodeStore(
      func   = 0,
      size   = totalBytes,
      addrs0 = encodeBufferAddr(src, srcSem),
    )), Seq.empty)
  }

  private def lowerMatmulPayload(exec: Execute, buffers: Seq[BufferRef]): Lowered = {
    val matmul = exec.payload(new Matmul()).asInstanceOf[Matmul]
    val src0Id = matmul.src0().toInt
    val src1Id = matmul.src1().toInt
    val dstId  = matmul.dst().toInt
    val src0 = buffers(src0Id)
    val src1 = buffers(src1Id)
    val dst  = buffers(dstId)

    val beats = bufferBeats(dst)

    val addrs0 = encodeBufferAddr(src0, findSemForBuffer(exec, isAcquire = false, src0Id))
    val addrs1 = encodeBufferAddr(src1, findSemForBuffer(exec, isAcquire = false, src1Id))
    val addrd0 = encodeBufferAddr(dst,  findSemForBuffer(exec, isAcquire = true,  dstId))

    Lowered(Seq(Encoding.encodeExecute(
      func   = 0,
      mode   = 0,
      size   = beats,
      addrs0 = addrs0,
      addrs1 = addrs1,
      addrd0 = addrd0,
    )), Seq.empty)
  }

  // ── Helpers ───────────────────────────────────────────────────────────

  /** Encode a BufferRef's offset as an addrPkg BigInt with optional semaphore. */
  private def encodeBufferAddr(buf: BufferRef, sem: Option[(Int, Int)]): BigInt = {
    val offset16 = (buf.offset() & 0xFFFF).toInt
    sem match {
      case Some((addr, stepSize)) =>
        AddrPkg.encode(
          addr          = offset16,
          semValid      = true,
          semAddr       = addr,
          stepSizeValid = true,
          stepSize      = stepSize,
        )
      case None =>
        AddrPkg.simple(offset16)
    }
  }

  /** Insert the (regSel, portSel) two-bit field into a fused trigger-state
    * index to produce the addrPkg.semAddr value the hardware expects.
    *
    * Layout (LSB → MSB):
    *   [genWidth-1 : 0]   generation tag (gen bits, untouched from `fused`)
    *   [genWidth]         register select (0 = fullReg, 1 = emptyReg) —
    *                      filled by TLDMA at runtime; the assembler leaves
    *                      this bit as 0
    *   [genWidth+1]       port select within a semaphore (acquire = 0,
    *                      require = 1)
    *   [genWidth+2 : ...] semaphore index (semIdx bits shifted up by 2)
    */
  private def packTLSemAddr(fused: Int, isAcquire: Boolean): Int = {
    val gw      = config.semaphoreGenerationWidth
    val genMask = (1 << gw) - 1
    val portBit = if (isAcquire) 0 else 1
    val genBits = fused & genMask
    val semIdx  = fused >>> gw
    (semIdx << (gw + 2)) | (portBit << (gw + 1)) | genBits
  }

  /** Find a semaphore dependency that guards a buffer by index, searching
    * either acquires (producer-side) or requires (consumer-side).
    * Returns (semAddr, stepSize).
    */
  private def findSemForBuffer(
    exec: Execute,
    isAcquire: Boolean,
    bufferId: Int,
  ): Option[(Int, Int)] = {
    val count = if (isAcquire) exec.acquiresLength() else exec.requiresLength()
    val getDep = if (isAcquire) exec.acquires(_: Int) else exec.requires(_: Int)

    for (i <- 0 until count) {
      val dep = getDep(i)
      if (dep.bufferId() == bufferId)
        return Some((packTLSemAddr(dep.fusedAddress(), isAcquire), dep.stepSize().toInt))
    }
    None
  }

  /** Number of data bus beats for a buffer. */
  private def bufferBeats(buf: BufferRef): Int = {
    val totalBytes = bufferTotalBytes(buf)
    ((totalBytes + config.dataBusBytes - 1) / config.dataBusBytes).toInt
  }

  private def bufferTotalBytes(buf: BufferRef): Long = {
    val shape = (0 until buf.shapeLength()).map(buf.shape(_))
    val elemBytes = Encoding.elementBytes(buf.elementType())
    Encoding.bufferByteSize(shape, elemBytes)
  }

  // ── Verbose tracing ───────────────────────────────────────────────────

  /** Print the result of lowering a single source op: each emitted instruction
    * is shown decoded plus its raw hex; preloads are summarised on one line.
    * `firstIdx` is the program-relative index of the first emitted instruction
    * in this op's lowering.  Hex width tracks the instruction's slot count
    * (16, 32, or 48 nibbles for 1/2/3-slot insts).
    */
  private def traceLowered(op: Operation, lowered: Lowered, firstIdx: Int): Unit = {
    val srcLabel = commandLabel(op)
    if (lowered.instructions.isEmpty && lowered.preloads.isEmpty) {
      println(f"  [---] $srcLabel%-18s (no emit)")
    }
    for ((inst, j) <- lowered.instructions.zipWithIndex) {
      val idx = firstIdx + j
      val nibbles = inst.bitWidth / 4
      println(f"  [$idx%3d] $srcLabel%-18s ${formatInstruction(inst.bits)}")
      println(s"         raw (${inst.slots}×64b): 0x" + String.format(s"%0${nibbles}x", inst.bits.bigInteger))
    }
    for (preload <- lowered.preloads) {
      println(
        f"  [pre] $srcLabel%-18s preload tier=${preload.tier} " +
        f"offset=0x${preload.offsetAddress}%x shape=${preload.shape.mkString("x")} " +
        f"bytes=${preload.data.length}"
      )
    }
  }

  private def commandLabel(op: Operation): String = op.commandType() match {
    case Command.SemAlloc   => "SemAlloc"
    case Command.SemDealloc => "SemDealloc"
    case Command.Execute    =>
      val exec = op.command(new Execute()).asInstanceOf[Execute]
      s"Execute(${ExecutePayload.name(exec.payloadType().toInt)})"
    case Command.MemAlloc   => "MemAlloc"
    case Command.MemDealloc => "MemDealloc"
    case other              => s"Cmd($other)"
  }

  private val opcodeNames: Map[Int, String] = Map(
    1 -> "Execute",
    2 -> "Load",
    3 -> "Store",
    4 -> "DMA",
    5 -> "SemProg",
  )

  /** Whether an addrPkg field is a source (acquire/wait) or destination (release/signal). */
  private val addrPkgRole: Map[String, String] = Map(
    "addrs0" -> "acquire",
    "addrs1" -> "acquire",
    "addrd0" -> "release",
  )

  private def extractField(inst: BigInt, field: Field): BigInt =
    (inst >> field.startBit) & ((BigInt(1) << field.width) - 1)

  private def isAddrPkgField(f: Field): Boolean =
    f.name.startsWith("addr") && f.width == InstructionSet.AddrPkgWidth

  private def formatAddrPkg(value: BigInt, fieldName: String): String = {
    val addr     = extractField(value, AddrPkg.addr)
    val semV     = extractField(value, AddrPkg.semValid)
    val semA     = extractField(value, AddrPkg.semAddr)
    val stepV    = extractField(value, AddrPkg.semStepSizeValid)
    val stepSize = extractField(value, AddrPkg.semStepSizeBits)

    val sb = new StringBuilder
    sb ++= f"addr=0x${addr}%04x"
    if (semV != 0) {
      val role     = addrPkgRole.getOrElse(fieldName, "")
      val genWidth = config.semaphoreGenerationWidth
      val genMask  = (BigInt(1) << genWidth) - 1
      val semIndex = semA >> (genWidth + 2)
      val generation = semA & genMask
      sb ++= f", sem=$semIndex%d"
      if (role.nonEmpty) sb ++= s" ($role)"
      if (genWidth > 0) sb ++= f", gen=$generation%d"
      if (stepV != 0) sb ++= f", step=$stepSize%d"
    }
    sb.result()
  }

  /** Pretty-print a fused dep address as (semIdx, gen). */
  private def formatFusedDep(value: BigInt): String = {
    val gw   = config.semaphoreGenerationWidth
    val mask = (BigInt(1) << gw) - 1
    val sem  = value >> gw
    val gen  = value & mask
    if (gw > 0) f"sem=$sem%d, gen=$gen%d" else f"sem=$sem%d"
  }

  private def isDepField(f: Field): Boolean =
    f.name.matches("dep[0-9]+")

  private def depIndex(f: Field): Int = f.name.substring(3).toInt

  private def formatInstruction(inst: BigInt): String = {
    val opcode = ((inst >> InstructionSet.OpcodeField.startBit) &
      ((BigInt(1) << InstructionSet.OpcodeField.width) - 1)).toInt
    InstructionSet.All.get(opcode) match {
      case None => f"UNKNOWN opcode=$opcode"
      case Some(layout) =>
        val name = opcodeNames.getOrElse(opcode, s"Op$opcode")
        val depCountField = layout.fields.find(_.name == "depCount")
        val depCount = depCountField.map(extractField(inst, _)).getOrElse(BigInt(0))
        val parts = layout.fields.flatMap {
          case f if f.name == "opcode" || f.name == "length" => None
          case f if isAddrPkgField(f) =>
            val raw = extractField(inst, f)
            Some(s"${f.name}={${formatAddrPkg(raw, f.name)}}")
          case f if isDepField(f) =>
            if (BigInt(depIndex(f)) < depCount)
              Some(s"${f.name}={${formatFusedDep(extractField(inst, f))}}")
            else
              None
          case f =>
            Some(f"${f.name}=${extractField(inst, f)}%d")
        }
        f"$name%-8s ${parts.mkString(", ")}"
    }
  }
}

/** Companion with a convenience method using default config. */
object Assembler {
  def assemble(buf: ByteBuffer): Assembler#AssembledProgram =
    new Assembler().assemble(buf)
}
