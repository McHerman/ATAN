package ATA8

import chisel3._
import chisel3.simulator.PeekPokeAPI._
import eaac.assembler.Assembler

/** Pure (hardware-independent) helpers for encoding/decoding test data. */
object TestUtil {

  /** Pack row elements into a little-endian BigInt, one byte per element. */
  def packRow(elements: Seq[Int]): BigInt =
    elements.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (elem, i)) =>
      acc | (BigInt(elem & 0xFF) << (i * 8))
    }

  def elemBitsOf(elementType: String): Int = elementType.toLowerCase match {
    case "i8"  => 8
    case "i16" => 16
    case "i32" => 32
    case other => throw new IllegalArgumentException(s"unsupported element_type: $other")
  }

  /** Unpack a single AXI-Stream beat into `axiStreamWidth / elemBits`
    * signed elements (elements wider than 8 bits are sign-extended). */
  def unpackBeat(value: BigInt, elemBits: Int, axiStreamWidth: Int): Seq[Int] = {
    val elemsPerBeat = axiStreamWidth / elemBits
    val mask = (BigInt(1) << elemBits) - 1
    val sign = BigInt(1) << (elemBits - 1)
    (0 until elemsPerBeat).map { i =>
      val v = (value >> (i * elemBits)) & mask
      val signed = elemBits > 8 && v >= sign
      (if (signed) v - (mask + 1) else v).toInt
    }
  }

  /** Unpack one output row (arrayDim elements, accDataWidth bits each). */
  def unpackRow(value: BigInt)(implicit c: Configuration): Seq[Int] = {
    val mask = (BigInt(1) << c.accDataWidth) - 1
    (0 until c.arrayDim).map(i => ((value >> (i * c.accDataWidth)) & mask).toInt)
  }
}

/** Shared hardware-driving tooling. Holds only the DUT clock and a running
  * cycle count; every method takes the specific port bundle it drives
  * (`io.AXIST_inInst`, `io.hostIn`, ...). Since those ports are the same
  * named bundle types (`AXIST_2`, `TilelinkPort`) on every DUT, the same ops
  * work for [[ATA8]], [[AtanMccDUT]] and [[AtanMcc16x16DUT]] with no shared
  * DUT supertype. Create one per `simulate { dut => ... }` block so
  * [[totalCycles]] scopes to a single run.
  */
class AtanTestOps(
  clock:     Clock,
  maxCycles: Int = 200000,
)(implicit c: Configuration) {

  var totalCycles: Long = 0L

  // ── Clock stepping ───────────────────────────────────────────────────

  def step(k: Int = 1): Unit = {
    clock.step(k)
    totalCycles += k
  }

  def waitFor(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      step()
      cycles += 1
    }
  }

  // ── Instruction stream ───────────────────────────────────────────────

  def sendInst(inst: AXIST_2, raw: BigInt): Unit = {
    inst.tdata.poke(raw.U(128.W))
    inst.tvalid.poke(true.B)
    inst.tkeep.poke("hffff".U)
    inst.tstrb.poke("hffff".U)
    waitFor(inst.tready.peek().litToBoolean, "AXIST_inInst.tready")
    step()
    inst.tvalid.poke(false.B)
  }

  // ── Input data stream ────────────────────────────────────────────────

  /** Feed input rows via AXIST_inData. Assumes axiStreamWidth carries one
    * row per beat (true for every Configuration used in these tests). */
  def feedLoadData(inData: AXIST_2, rows: Seq[BigInt]): Unit = {
    val allOnes = (BigInt(1) << (c.axiStreamWidth / 8)) - 1
    for ((row, i) <- rows.zipWithIndex) {
      inData.tdata.poke(row.U(c.axiStreamWidth.W))
      inData.tstrb.poke(allOnes.U)
      inData.tkeep.poke(allOnes.U)
      inData.tvalid.poke(true.B)
      inData.tlast.poke((i == rows.length - 1).B)
      var cyc = 0
      do {
        require(cyc < maxCycles, s"Timeout waiting for AXIST_inData.tready row $i")
        step()
        cyc += 1
      } while (!inData.tready.peek().litToBoolean)
    }
    inData.tvalid.poke(false.B)
    inData.tlast.poke(false.B)
  }

  // ── Output data stream ───────────────────────────────────────────────

  /** Collect `nBeats` raw AXIST_out beats (one BigInt per beat). Callers
    * that work at beat granularity (self-unpacking) use this directly. */
  def collectStoreBeats(out: AXIST_2, nBeats: Int): Seq[BigInt] = {
    out.tready.poke(true.B)
    val collected = scala.collection.mutable.ArrayBuffer[BigInt]()
    while (collected.length < nBeats) {
      waitFor(out.tvalid.peek().litToBoolean, s"AXIST_out.tvalid beat ${collected.length}")
      collected += out.tdata.peek().litValue
      step()
    }
    out.tready.poke(false.B)
    collected.toSeq
  }

  /** Collect `nRows` output rows, reassembling each row from its
    * `wordsPerRow` sequential AXI-Stream words (LSB word first, matching
    * StoreController's split order). */
  def collectStoreRows(out: AXIST_2, nRows: Int): Seq[BigInt] = {
    require((c.arrayDim * c.accDataWidth) % c.axiStreamWidth == 0,
      "collectStoreRows assumes a whole number of AXI words per output row")
    val wordsPerRow = (c.arrayDim * c.accDataWidth) / c.axiStreamWidth
    val words = collectStoreBeats(out, nRows * wordsPerRow)
    words.grouped(wordsPerRow).map { ws =>
      ws.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (w, i)) => acc | (w << (i * c.axiStreamWidth)) }
    }.toSeq
  }

  // ── Host memory preload ──────────────────────────────────────────────

  /** Preload one assembler-emitted region into ATAN memory via hostIn.
    *
    * @param reverseBeats   send beats in reverse order (E2E path)
    * @param sizeAsBeatSpan set the TL `size` field to `nBeats * dataBusSize`
    *                       (E2E path); otherwise it is `preload.data.size`
    *                       (scaling path).
    */
  def preloadMem(
    hostIn:         TilelinkPort,
    preload:        Assembler#Preload,
    msCfg:          MemSystemConfig,
    reverseBeats:   Boolean = false,
    sizeAsBeatSpan: Boolean = false,
  ): Unit = {
    val addr = preload.offsetAddress + msCfg.tierBases(preload.tier).toInt

    val beats: Seq[BigInt] = preload.data
      .grouped(msCfg.dataBusSize)
      .map { bytes =>
        bytes.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (b, i)) =>
          acc | (BigInt(b & 0xFF) << (i * 8))
        }
      }
      .toSeq

    val orderedBeats = if (reverseBeats) beats.reverse else beats
    val size         = if (sizeAsBeatSpan) beats.length * msCfg.dataBusSize else preload.data.size

    hostIn.d.ready.poke(true.B)

    for ((beat, i) <- orderedBeats.zipWithIndex) {
      hostIn.a.bits.opcode.poke(0.U) // PutFullData
      hostIn.a.bits.param.poke(0.U)
      hostIn.a.bits.address.poke(addr.U)
      hostIn.a.bits.size.poke(size.U)
      hostIn.a.bits.source.poke(0.U)
      hostIn.a.bits.data.poke(beat.U)
      hostIn.a.bits.mask.poke(((BigInt(1) << msCfg.dataBusSize) - 1).U)
      hostIn.a.bits.corrupt.poke(0.U)
      hostIn.a.valid.poke(true.B)
      waitFor(hostIn.a.ready.peek().litToBoolean, s"hostIn.a.ready preload beat $i")
      step()
    }

    hostIn.a.valid.poke(false.B)
    waitFor(hostIn.d.valid.peek().litToBoolean, "hostIn.d.valid")
    hostIn.d.bits.opcode.expect(0.U) // AccessAck
    step()
  }

  // ── mcc imem preload (mcc DUTs only) ─────────────────────────────────

  def loadImem(riscv: TilelinkPort, initData: Map[Int, BigInt]): Unit = {
    val InitDoneAddr = 0xF000L

    riscv.d.ready.poke(true.B)
    riscv.a.bits.opcode.poke(TilelinkOpcodes.PutFullData)
    riscv.a.bits.param.poke(0.U)
    riscv.a.bits.size.poke(4.U)
    riscv.a.bits.source.poke(0.U)
    riscv.a.bits.mask.poke("b1111".U)
    riscv.a.bits.corrupt.poke(0.U)

    def put(addr: Long, data: BigInt): Unit = {
      riscv.a.bits.address.poke(addr.U)
      riscv.a.bits.data.poke(data.U(32.W))
      riscv.a.valid.poke(true.B)
      waitFor(riscv.a.ready.peek().litToBoolean, s"hostInRiscV.a.ready (addr=$addr)")
      step()
      riscv.a.valid.poke(false.B)
      waitFor(riscv.d.valid.peek().litToBoolean, s"hostInRiscV.d.valid (addr=$addr)")
      step()
    }

    for ((wordIdx, word) <- initData.toSeq.sortBy(_._1)) put(wordIdx.toLong * 4, word)
    put(InitDoneAddr, BigInt(1))
  }
}
