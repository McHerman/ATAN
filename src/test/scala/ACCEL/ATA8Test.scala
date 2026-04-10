package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class ATA8Test extends AnyFreeSpec with Matchers with ChiselSim {

  val n = 8
  val maxCycles = 5000

  // ── Test matrix ─────────────────────────────────────────────────────────

  val matrix: Array[Array[Int]] = Array.fill(n, n)(0).zipWithIndex.map { case (_, i) =>
    Array(1, 2, 3, 4, 1, 2, 3, 4)
  }

  def matrixDotProduct(A: Array[Array[Int]], B: Array[Array[Int]]): Array[Array[Int]] = {
    val m = A.length
    Array.tabulate(m, m) { (i, j) =>
      (0 until m).map(k => A(i)(k) * B(k)(j)).sum
    }
  }

  def packRow(elements: Seq[Int]): BigInt =
    elements.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (elem, i)) =>
      acc | (BigInt(elem & 0xFF) << (i * 8))
    }

  def unpackRow(value: BigInt): Seq[Int] =
    (0 until 8).map(i => ((value >> (i * 8)) & 0xFF).toInt)

  // ── Helpers ─────────────────────────────────────────────────────────────

  def waitFor(clock: chisel3.Clock)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      clock.step()
      cycles += 1
    }
  }

  // ── Instruction assembly ────────────────────────────────────────────────
  //
  // 128-bit raw instructions matching the Decodable layouts in ctrlDefs.scala.
  //
  // addrPkg (36 bits, first-declared = MSB):
  //   [35:20] addr               (16 bits)
  //   [19]    sem.valid          (1 bit)
  //   [18:15] sem.bits.addr      (4 bits) — byte addr into semaphore bank xbar
  //   [14]    sem.bits.stepSize.valid (1 bit)
  //   [13:0]  sem.bits.stepSize.bits  (14 bits)

  def addrPkgBits(addr: Int, semEnable: Boolean, semAddr: Int, stepSize: Int): BigInt = {
    var v = BigInt(stepSize & 0x3FFF)                       // stepSize.bits
    v |= BigInt(1) << 14                                    // stepSize.valid
    v |= BigInt(semAddr & 0xF) << 15                        // sem.bits.addr
    if (semEnable) v |= BigInt(1) << 19                     // sem.valid
    v |= BigInt(addr & 0xFFFF) << 20                        // addr
    v
  }

  /** ExecuteInst:
   *  opcode[5:0]=1, func[6], mode[7], size[15:8],
   *  addrs(0)[51:16], addrs(1)[87:52], addrd(0)[123:88], grainSize[127:124] */
  def assembleExe(mode: Int, size: Int,
                  addrsPkg0: BigInt, addrsPkg1: BigInt, addrdPkg0: BigInt): BigInt = {
    var inst = BigInt(1)                                    // opcode = 1
    inst |= BigInt(mode & 0x1) << 7                         // mode
    inst |= BigInt(size & 0xFF) << 8                        // size
    inst |= (addrsPkg0 & ((BigInt(1) << 36) - 1)) << 16     // addrs(0)
    inst |= (addrsPkg1 & ((BigInt(1) << 36) - 1)) << 52     // addrs(1)
    inst |= (addrdPkg0 & ((BigInt(1) << 36) - 1)) << 88     // addrd(0)
    inst
  }

  /** LoadInst: opcode[5:0]=2, size[15:8], addrd(0)[51:16] */
  def assembleLd(size: Int, addrdPkg0: BigInt): BigInt = {
    var inst = BigInt(2)                                    // opcode = 2
    inst |= BigInt(size & 0xFF) << 8                        // size
    inst |= (addrdPkg0 & ((BigInt(1) << 36) - 1)) << 16     // addrd(0)
    inst
  }

  /** StoreInst: opcode[5:0]=3, size[15:8], addrs(0)[51:16] */
  def assembleSt(size: Int, addrsPkg0: BigInt): BigInt = {
    var inst = BigInt(3)                                    // opcode = 3
    inst |= BigInt(size & 0xFF) << 8                        // size
    inst |= (addrsPkg0 & ((BigInt(1) << 36) - 1)) << 16     // addrs(0)
    inst
  }

  /** SemProgInst: opcode[5:0]=5, semAddr[13:6], initValues[45:14] */
  def assembleSemProg(semAddr: Int, full: Int, empty: Int): BigInt = {
    var inst = BigInt(5)                                    // opcode = 5
    inst |= BigInt(semAddr & 0xFF) << 6                     // semAddr
    inst |= BigInt(full & 0xFFFF) << 14                     // initValues(0) = fullReg
    inst |= BigInt(empty & 0xFFFF) << 30                    // initValues(1) = emptyReg
    inst
  }

  // ── Instruction streaming ───────────────────────────────────────────────

  def sendInst(dut: ATA8, raw: BigInt): Unit = {
    dut.io.AXIST_inInst.tdata.poke(raw.U(128.W))
    dut.io.AXIST_inInst.tvalid.poke(true.B)
    dut.io.AXIST_inInst.tkeep.poke("hffff".U)
    dut.io.AXIST_inInst.tstrb.poke("hffff".U)
    waitFor(dut.clock)(dut.io.AXIST_inInst.tready.peek().litToBoolean, "AXIST_inInst.tready")
    dut.clock.step()
    dut.io.AXIST_inInst.tvalid.poke(false.B)
  }

  def feedLoadData(dut: ATA8, rows: Seq[BigInt]): Unit = {
    for ((row, i) <- rows.zipWithIndex) {
      //dut.io.AXIST_inData.tready.expect(true.B)

      dut.io.AXIST_inData.tdata.poke(row.U(64.W))
      dut.io.AXIST_inData.tstrb.poke("hff".U)
      dut.io.AXIST_inData.tkeep.poke("hff".U)
      dut.io.AXIST_inData.tvalid.poke(true.B)

      if(i == rows.length - 1) {
        dut.io.AXIST_inData.tlast.poke(true.B)
        //dut.clock.step()
      } else {
        //waitFor(dut.clock)(dut.io.AXIST_inData.tready.peek().litToBoolean, s"AXIST_inData.tready beat $i")
        dut.io.AXIST_inData.tready.expect(true.B)
        //dut.clock.step()
      }

      print("Sent index " + i + " Out of " + rows.length)

      dut.clock.step()
    }
    dut.io.AXIST_inData.tvalid.poke(false.B)
    dut.io.AXIST_inData.tlast.poke(false.B)
  }

  def collectStoreData(dut: ATA8, nRows: Int): Seq[BigInt] = {
    dut.io.AXIST_out.tready.poke(true.B)
    val collected = scala.collection.mutable.ArrayBuffer[BigInt]()
    while (collected.length < nRows) {
      waitFor(dut.clock)(dut.io.AXIST_out.tvalid.peek().litToBoolean, s"AXIST_out.tvalid beat ${collected.length}")
      collected += dut.io.AXIST_out.tdata.peek().litValue
      dut.clock.step()
    }
    dut.io.AXIST_out.tready.poke(false.B)
    collected.toSeq
  }

  // ── Tests ───────────────────────────────────────────────────────────────

  "ATA8 should load, matmul, and store two 8x8 matrices through blocking semaphores" in {
    // sourceWidth=8 is required by TLXbar's ID-range scheme (10 IDs per master).
    val testConfig = Configuration.default().copy(sourceWidth = 8)
    simulate(new ATA8(testConfig)) { dut =>
      //defaultPokes(dut)
      //dut.clock.step(2)

      // ── Scratchpad addresses (beat-indexed) ──
      val addrA = 0
      val addrB = n * n
      val addrD = 2 * n * n

      // ── Semaphore layout ──
      // Sem bank entries (SemProgInst.semAddr indexes semaphores 0..7 directly):
      //   sem 0 → matrix-A handshake (Load producer ↔ Execute consumer)
      //   sem 1 → matrix-B handshake (Load producer ↔ Execute consumer)
      //   sem 2 → result handshake   (Execute producer ↔ Store consumer)
      //
      // Per-DMA semAddr is a byte address into the semaphore xbar: 4 bytes per
      // semaphore (2 ports × 2 regs). Use the base of port 0:  
      //val semByteAddrA = 0   // sem 0, port 0
      val sem0A = 0   // sem 0, port a
      val sem0B = 2   // sem 0, port b 
      val sem1A = 4   // sem 0, port a 
      val sem1B = 6   // sem 0, port b  
      val sem2A = 8   // sem 0, port a
      val sem2B = 10   // sem 0, port b

      // ── Program semaphores: producer acquires empty (= size), consumer acquires full (= 0) ──
      sendInst(dut, assembleSemProg(semAddr = 0, full = 0, empty = n))
      sendInst(dut, assembleSemProg(semAddr = 1, full = 0, empty = n))
      sendInst(dut, assembleSemProg(semAddr = 2, full = 0, empty = n))

      // ── Load A (producer on sem 0) ──
      sendInst(dut, assembleLd(
        size = n,
        addrdPkg0 = addrPkgBits(addrA, semEnable = true, semAddr = sem0A, stepSize = n)
      ))

      // ── Load B (producer on sem 1) ──
      sendInst(dut, assembleLd(
        size = n,
        addrdPkg0 = addrPkgBits(addrB, semEnable = true, semAddr = sem1A, stepSize = n)
      ))

      // ── Execute matmul (consumers on sem 0 + sem 1, producer on sem 2) ──
      sendInst(dut, assembleExe(
        mode = 0, size = n,
        addrsPkg0 = addrPkgBits(addrA, semEnable = true, semAddr = sem0B, stepSize = n),
        addrsPkg1 = addrPkgBits(addrB, semEnable = true, semAddr = sem1B, stepSize = n),
        addrdPkg0 = addrPkgBits(addrD, semEnable = true, semAddr = sem2A, stepSize = n)
      ))

      // ── Store (consumer on sem 2) ──
      sendInst(dut, assembleSt(
        size = n,
        addrsPkg0 = addrPkgBits(addrD, semEnable = true, semAddr = sem2B, stepSize = n)
      ))

      // ── Feed load data for A then B ──
      val matrixRows = (0 until n).map(i => packRow(matrix(i).toSeq))

      // Step a few cycles so the pipeline can start processing the sem-prog
      // and load instructions before we start pushing data.
      dut.clock.step(50)

      //def dumpDbg(tag: String): Unit = {
      //  println(s"[debug $tag]")
      //  println(s"  AXIST_inInst.tready = ${dut.io.AXIST_inInst.tready.peek().litToBoolean}")
      //  println(s"  AXIST_inData.tready = ${dut.io.AXIST_inData.tready.peek().litToBoolean}")
      //  println(s"  AXIST_out.tvalid    = ${dut.io.AXIST_out.tvalid.peek().litToBoolean}")
      //}
      //dumpDbg("after 50 cycles")
//
//
      //println(s"  Load.state     = ${dut.Load.io.debug.state.peek().litValue}")
      //println(s"  Execute.state  = ${dut.Execute.io.debug.state.peek().litValue}")
      //println(s"  Store.state    = ${dut.Store.io.debug.state.peek().litValue}")

      feedLoadData(dut, matrixRows) // matrix A

      dut.clock.step(50)    
  
      feedLoadData(dut, matrixRows) // matrix B

      // ── Collect store output ──
      val outputRows = collectStoreData(dut, n)

      // ── Verify ──
      val expected = matrixDotProduct(matrix, matrix)
      for (row <- 0 until n) {
        val got = unpackRow(outputRows(row))
        for (col <- 0 until n) {
          assert(got(col) == (expected(row)(col) & 0xFF),
            s"Mismatch at ($row,$col): got ${got(col)}, expected ${expected(row)(col) & 0xFF}")
        }
      }
    }
  }
}
