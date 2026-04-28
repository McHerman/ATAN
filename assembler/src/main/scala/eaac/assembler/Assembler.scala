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
  case class AssembledFunction(
    name: String,
    instructions: Seq[BigInt],
  )

  /** Result of assembling a full program. */
  case class AssembledProgram(
    functions: Seq[AssembledFunction],
  ) {
    def allInstructions: Seq[BigInt] = functions.flatMap(_.instructions)
  }

  /** Assemble a FlatBuffer binary into instruction words. */
  def assemble(buf: ByteBuffer): AssembledProgram = {
    val program = Program.getRootAsProgram(buf)
    val funcs = (0 until program.functionsLength()).map { i =>
      assembleFunction(program.functions(i))
    }
    AssembledProgram(funcs)
  }

  private def assembleFunction(fn: eaac_fb.Function): AssembledFunction = {
    val instructions = new ArrayBuffer[BigInt]()
    for (i <- 0 until fn.opsLength())
      instructions ++= lowerCommand(fn.ops(i))
    AssembledFunction(fn.name(), instructions.toSeq)
  }

  private def lowerCommand(op: Operation): Seq[BigInt] = op.commandType() match {
    case Command.SemAlloc   => lowerSemAlloc(op)
    case Command.SemDealloc => lowerSemDealloc(op)
    case Command.Execute    => lowerExecute(op)
    case Command.MemAlloc   => Seq.empty
    case Command.MemDealloc => Seq.empty
    case other => throw new IllegalArgumentException(s"Unknown command type: $other")
  }

  // ── SemAlloc / SemDealloc ─────────────────────────────────────────────

  private def lowerSemAlloc(op: Operation): Seq[BigInt] = {
    val sem = op.command(new SemAlloc()).asInstanceOf[SemAlloc]
    Seq(Encoding.encodeSemProg(
      semAddr   = sem.address(),
      initEmpty = sem.emptyCount().toInt,
      initFull  = sem.fullCount().toInt,
    ))
  }

  private def lowerSemDealloc(op: Operation): Seq[BigInt] = {
    val sem = op.command(new SemDealloc()).asInstanceOf[SemDealloc]
    Seq(Encoding.encodeSemProg(
      semAddr   = sem.address(),
      initEmpty = 0,
      initFull  = 0,
    ))
  }

  // ── Execute (DMA or Compute) ──────────────────────────────────────────

  private def lowerExecute(op: Operation): Seq[BigInt] = {
    val exec = op.command(new Execute()).asInstanceOf[Execute]
    exec.payloadType() match {
      case ExecutePayload.DmaStart => lowerDmaPayload(exec)
      case ExecutePayload.LoadOp  => lowerLoadPayload(exec)
      case ExecutePayload.StoreOp => lowerStorePayload(exec)
      case ExecutePayload.Matmul   => lowerMatmulPayload(exec)
      case other => throw new IllegalArgumentException(s"Unknown execute payload type: $other")
    }
  }

  private def lowerDmaPayload(exec: Execute): Seq[BigInt] = {
    val dma = exec.payload(new DmaStart()).asInstanceOf[DmaStart]
    val src = dma.src()
    val dst = dma.dst()

    val srcSem = findSemForBuffer(exec, isAcquire = true, src)
    val dstSem = findSemForBuffer(exec, isAcquire = false, dst)
    val beats = bufferBeats(src)

    Seq(Encoding.encodeDMA(
      func   = 0,
      size   = beats,
      addrs0 = encodeBufferAddr(src, srcSem, beats),
      addrd0 = encodeBufferAddr(dst, dstSem, beats),
    ))
  }

  private def lowerLoadPayload(exec: Execute): Seq[BigInt] = {
    val load = exec.payload(new LoadOp()).asInstanceOf[LoadOp]
    val dst = load.dst()

    val dstSem = findSemForBuffer(exec, isAcquire = false, dst)
    val beats = bufferBeats(dst)

    Seq(Encoding.encodeLoad(
      func   = 0,
      mode   = 0,
      size   = beats,
      addrd0 = encodeBufferAddr(dst, dstSem, beats),
    ))
  }

  private def lowerStorePayload(exec: Execute): Seq[BigInt] = {
    val store = exec.payload(new StoreOp()).asInstanceOf[StoreOp]
    val src = store.src()

    val srcSem = findSemForBuffer(exec, isAcquire = true, src)
    val beats = bufferBeats(src)

    Seq(Encoding.encodeStore(
      func   = 0,
      size   = beats,
      addrs0 = encodeBufferAddr(src, srcSem, beats),
    ))
  }

  /** Lower a Matmul into a single Execute instruction.
    *
    * Semaphore mapping:
    *   - `requires` dependencies guard src0/src1 (consumer waits for data)
    *   - `acquires` dependencies guard dst (producer signals result ready)
    */
  private def lowerMatmulPayload(exec: Execute): Seq[BigInt] = {
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

    Seq(Encoding.encodeExecute(
      func   = 0,
      mode   = 0,
      size   = beats,
      addrs0 = addrs0,
      addrs1 = addrs1,
      addrd0 = addrd0,
    ))
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
