package eaac.assembler

import eaac.shared.InstructionSet.AddrPkg
import eaac_fb._

import java.nio.ByteBuffer
import scala.collection.mutable.ArrayBuffer

/** Hardware parameters the assembler needs to produce correct encodings. */
case class AssemblerConfig(
  /** Bytes per data bus beat (default 8 for 64-bit AXI). */
  dataBusBytes: Int = 8,
)

object AssemblerConfig {
  val default: AssemblerConfig = AssemblerConfig()
}

/** Assembles an EAAC FlatBuffer program into a sequence of 128-bit
  * instruction words that can be streamed to the accelerator.
  */
class Assembler(config: AssemblerConfig = AssemblerConfig.default) {

  /** Result of assembling a single function. */

  case class Preload(
    shape: Seq[Int],
    offsetAddress: BigInt,
    tier: Int,
    elementType: Byte,
    data: Seq[Byte]
  )

  case class AssembledFunction(
    name: String,
    instructions: Seq[BigInt],
    preloads: Seq[Preload]
  )


  case class Lowered(
    instructions: Seq[BigInt],
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
    AssembledProgram(funcs)
  }

  private def assembleFunction(fn: eaac_fb.Function, const: Seq[eaac_fb.Constant]): AssembledFunction = {
    val instructions = new ArrayBuffer[BigInt]()
    val preloads = new ArrayBuffer[Preload]()
    for (i <- 0 until fn.opsLength()) {
      val lowered = lowerCommand(fn.ops(i), const)
      instructions ++= lowered.instructions
      preloads ++= lowered.preloads
    }
    AssembledFunction(fn.name(), instructions.toSeq, preloads.toSeq)
  }

  private def lowerCommand(op: Operation, const: Seq[eaac_fb.Constant]): Lowered = op.commandType() match {
    case Command.SemAlloc   => lowerSemAlloc(op)
    case Command.SemDealloc => lowerSemDealloc(op)
    case Command.Execute    => lowerExecute(op)
    //case Command.MemAlloc   => Seq.empty
    case Command.MemAlloc   => lowerMemoryAlloc(op,const) 
    case Command.MemDealloc => Lowered(Seq.empty, Seq.empty)
    case other => throw new IllegalArgumentException(s"Unknown command type: $other")
  }

  // ── SemAlloc / SemDealloc ─────────────────────────────────────────────

  // Doesnt get lowered into a instruction, but rather a host preload
  private def lowerMemoryAlloc(op: Operation, const: Seq[eaac_fb.Constant]): Lowered = {

    val alloc = op.command(new MemAlloc()).asInstanceOf[MemAlloc]
    val buf = alloc.buffer()
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
    val sem = op.command(new SemAlloc()).asInstanceOf[SemAlloc]
    Lowered(Seq(Encoding.encodeSemProg(
      semAddr   = sem.address(),
      initEmpty = sem.emptyCount().toInt,
      initFull  = sem.fullCount().toInt,
    )), Seq.empty)
  }

  private def lowerSemDealloc(op: Operation): Lowered = {
    val sem = op.command(new SemDealloc()).asInstanceOf[SemDealloc]
    Lowered(Seq(Encoding.encodeSemProg(
      semAddr   = sem.address(),
      initEmpty = 0,
      initFull  = 0,
    )), Seq.empty)
  }

  // ── Execute (DMA or Compute) ──────────────────────────────────────────

  private def lowerExecute(op: Operation): Lowered = {
    val exec = op.command(new Execute()).asInstanceOf[Execute]
    exec.payloadType() match {
      case ExecutePayload.DmaStart => lowerDmaPayload(exec)
      case ExecutePayload.LoadOp  => lowerLoadPayload(exec)
      case ExecutePayload.StoreOp => lowerStorePayload(exec)
      case ExecutePayload.Matmul   => lowerMatmulPayload(exec)
      case other => throw new IllegalArgumentException(s"Unknown execute payload type: $other")
    }
  }

  private def lowerDmaPayload(exec: Execute): Lowered = {
    val dma = exec.payload(new DmaStart()).asInstanceOf[DmaStart]
    val src = dma.src()
    val dst = dma.dst()

    val srcSem = findSemForBuffer(exec, isAcquire = true, src)
    val dstSem = findSemForBuffer(exec, isAcquire = false, dst)
    val beats = bufferBeats(src)

    Lowered(Seq(Encoding.encodeDMA(
      func   = 0,
      size   = beats,
      addrs0 = encodeBufferAddr(src, srcSem, beats),
      addrd0 = encodeBufferAddr(dst, dstSem, beats),
    )), Seq.empty)
  }

  private def lowerLoadPayload(exec: Execute): Lowered = {
    val load = exec.payload(new LoadOp()).asInstanceOf[LoadOp]
    val dst = load.dst()

    val dstSem = findSemForBuffer(exec, isAcquire = false, dst)
    val beats = bufferBeats(dst)

    Lowered(Seq(Encoding.encodeLoad(
      func   = 0,
      mode   = 0,
      size   = beats,
      addrd0 = encodeBufferAddr(dst, dstSem, beats),
    )), Seq.empty)
  }

  private def lowerStorePayload(exec: Execute): Lowered = {
    val store = exec.payload(new StoreOp()).asInstanceOf[StoreOp]
    val src = store.src()

    val srcSem = findSemForBuffer(exec, isAcquire = true, src)
    val beats = bufferBeats(src)

    Lowered(Seq(Encoding.encodeStore(
      func   = 0,
      size   = beats,
      addrs0 = encodeBufferAddr(src, srcSem, beats),
    )), Seq.empty)
  }

  private def lowerMatmulPayload(exec: Execute): Lowered = {
    val matmul = exec.payload(new Matmul()).asInstanceOf[Matmul]
    val src0 = matmul.src0()
    val src1 = matmul.src1()
    val dst  = matmul.dst()

    val beats = bufferBeats(dst)

    val addrs0 = {
      val sem = findSemForBuffer(exec, isAcquire = false, src0)
      encodeBufferAddr(src0, sem, bufferBeats(src0))
    }

    val addrs1 = {
      val sem = findSemForBuffer(exec, isAcquire = false, src1)
      encodeBufferAddr(src1, sem, bufferBeats(src1))
    }

    val addrd0 = {
      val sem = findSemForBuffer(exec, isAcquire = true, dst)
      encodeBufferAddr(dst, sem, beats)
    }

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

  /** Encode a BufferRef's offset as an addrPkg BigInt with optional semaphore.
    * @param semDep Some((semByteAddr, bankIdx)) or None
    * @param stepSize number of beats for semaphore step
    */
  private def encodeBufferAddr(buf: BufferRef, semDep: Option[(Int, Int)], stepSize: Int): BigInt = {
    val offset16 = (buf.offset() & 0xFFFF).toInt
    semDep match {
      case Some((semByteAddr, _)) =>
        AddrPkg.encode(
          addr          = offset16,
          semValid      = true,
          semAddr       = semByteAddr,
          stepSizeValid = true,
          stepSize      = stepSize,
        )
      case None =>
        AddrPkg.simple(offset16)
    }
  }

  /** Find a semaphore dependency that guards a buffer, searching either
    * acquires (producer-side) or requires (consumer-side).
    * Returns Some((bankIndex, 0)) or None.
    */
  private def findSemForBuffer(
    exec: Execute,
    isAcquire: Boolean,
    buf: BufferRef,
  ): Option[(Int, Int)] = {
    if (buf == null) return None
    val count = if (isAcquire) exec.acquiresLength() else exec.requiresLength()
    val getDep = if (isAcquire) exec.acquires(_: Int) else exec.requires(_: Int)

    for (i <- 0 until count) {
      val dep = getDep(i)
      val depBuf = dep.buffer()
      if (depBuf != null && depBuf.offset() == buf.offset() && depBuf.tier() == buf.tier())
        return Some((dep.semAddress(), 0))
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
}

/** Companion with a convenience method using default config. */
object Assembler {
  def assemble(buf: ByteBuffer): Assembler#AssembledProgram =
    new Assembler().assemble(buf)
}
