package eaac.assembler

import eaac.shared.InstructionSet.AddrPkg
import eaac_fb._

import java.nio.ByteBuffer
import scala.collection.mutable.ArrayBuffer

/** Hardware parameters the assembler needs to produce correct encodings. */
case class AssemblerConfig(
  /** Bytes per data bus beat (default 8 for 64-bit AXI). */
  dataBusBytes: Int = 8,
  /** Number of memory tiers in the hardware (used to map FB tier numbering to hardware tier indices). */
  nTiers: Int = 3,
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
    val buffers = (0 until fn.buffersLength()).map(fn.buffers(_))
    val instructions = new ArrayBuffer[BigInt]()
    val preloads = new ArrayBuffer[Preload]()
    for (i <- 0 until fn.opsLength()) {
      val lowered = lowerCommand(fn.ops(i), buffers, const)
      instructions ++= lowered.instructions
      preloads ++= lowered.preloads
    }
    AssembledFunction(fn.name(), instructions.toSeq, preloads.toSeq)
  }

  private def lowerCommand(op: Operation, buffers: Seq[BufferRef], const: Seq[eaac_fb.Constant]): Lowered = op.commandType() match {
    case Command.SemAlloc   => lowerSemAlloc(op)
    case Command.SemDealloc => lowerSemDealloc(op)
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

    val srcSem = findSemForBuffer(exec, isAcquire = false, srcId)
    val dstSem = findSemForBuffer(exec, isAcquire = true, dstId)
    val beats = bufferBeats(src)

    Lowered(Seq(Encoding.encodeDMA(
      func   = 0,
      size   = beats,
      addrs0 = encodeBufferAddr(src, srcSem, beats),
      addrd0 = encodeBufferAddr(dst, dstSem, beats),
    )), Seq.empty)
  }

  private def lowerLoadPayload(exec: Execute, buffers: Seq[BufferRef]): Lowered = {
    val load = exec.payload(new LoadOp()).asInstanceOf[LoadOp]
    val dstId = load.dst().toInt
    val dst = buffers(dstId)

    // Acquire means that the give command produces data
    val dstSem = findSemForBuffer(exec, isAcquire = true, dstId)
    val beats = bufferBeats(dst)

    Lowered(Seq(Encoding.encodeLoad(
      func   = 0,
      mode   = 0,
      size   = beats,
      addrd0 = encodeBufferAddr(dst, dstSem, beats),
    )), Seq.empty)
  }

  private def lowerStorePayload(exec: Execute, buffers: Seq[BufferRef]): Lowered = {
    val store = exec.payload(new StoreOp()).asInstanceOf[StoreOp]
    val srcId = store.src().toInt
    val src = buffers(srcId)

    println(f"store buffer, src_id: ${srcId}")

    val srcSem = findSemForBuffer(exec, isAcquire = false, srcId)

    //println(f"buffer ${src.id.toInt}")

    println(f"store semaphore, id: ${srcSem}")

    val beats = bufferBeats(src)

    Lowered(Seq(Encoding.encodeStore(
      func   = 0,
      size   = beats,
      addrs0 = encodeBufferAddr(src, srcSem, beats),
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

    val addrs0 = {
      val sem = findSemForBuffer(exec, isAcquire = false, src0Id)
      encodeBufferAddr(src0, sem, bufferBeats(src0))
    }

    val addrs1 = {
      val sem = findSemForBuffer(exec, isAcquire = false, src1Id)
      encodeBufferAddr(src1, sem, bufferBeats(src1))
    }

    val addrd0 = {
      val sem = findSemForBuffer(exec, isAcquire = true, dstId)
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

  /** Encode a BufferRef's offset as an addrPkg BigInt with optional semaphore. */
  private def encodeBufferAddr(buf: BufferRef, semAddr: Option[Int], stepSize: Int): BigInt = {
    val offset16 = (buf.offset() & 0xFFFF).toInt
    semAddr match {
      case Some(addr) =>
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

  /** Find a semaphore dependency that guards a buffer by index, searching
    * either acquires (producer-side) or requires (consumer-side).
    */
  private def findSemForBuffer(
    exec: Execute,
    isAcquire: Boolean,
    bufferId: Int,
  ): Option[Int] = {
    val count = if (isAcquire) exec.acquiresLength() else exec.requiresLength()
    val getDep = if (isAcquire) exec.acquires(_: Int) else exec.requires(_: Int)

    for (i <- 0 until count) {
      val dep = getDep(i)
      print(f"${dep.semAddress()}")

      // To ensure that the producer and consumer doesnt request the same memory port
      val offset = if (isAcquire) 0 else 2 

      if (dep.bufferId() == bufferId)
        return Some(dep.semAddress() + offset)
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
