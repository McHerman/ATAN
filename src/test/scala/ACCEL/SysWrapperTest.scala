package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class SysWrapperTest extends AnyFreeSpec with Matchers with ChiselSim {

  val n = 8
  val maxCycles = 2000

  val matrix3: Array[Array[Int]] = Array(
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4),
    Array(1, 2, 3, 4, 1, 2, 3, 4)
  )

  def matrixDotProduct(A: Array[Array[Int]], B: Array[Array[Int]]): Array[Array[Int]] = {
    val n = A.length
    Array.tabulate(n, n) { (i, j) =>
      (0 until n).map(k => A(i)(k) * B(k)(j)).sum
    }
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  def waitFor(clock: chisel3.Clock)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      clock.step()
      cycles += 1
    }
  }

  /** Pack a row of byte values into a 64-bit BigInt (little-endian: element 0 → LSB). */
  def packRow(elements: Seq[Int]): BigInt = {
    elements.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (elem, i)) =>
      acc | (BigInt(elem & 0xFF) << (i * 8))
    }
  }

  /** Unpack a 64-bit value into 8 byte values (little-endian). */
  def unpackRow(value: BigInt): Seq[Int] = {
    (0 until 8).map(i => ((value >> (i * 8)) & 0xFF).toInt)
  }

  /** Poke a TileLink port to idle (test acts as TL device). */
  def pokeTLIdle(port: TilelinkPort): Unit = {
    port.a.ready.poke(false.B)
    port.d.valid.poke(false.B)
    port.d.bits.opcode.poke(0.U)
    port.d.bits.param.poke(0.U)
    port.d.bits.size.poke(0.U)
    port.d.bits.source.poke(0.U)
    port.d.bits.sink.poke(0.U)
    port.d.bits.denied.poke(0.U)
    port.d.bits.data.poke(0.U)
    port.d.bits.corrupt.poke(0.U)
  }

  /** Poke instruction with no semaphore dependencies. */
  def pokeInstruction(dut: SysWrapper, mode: Int, size: Int,
                      addr0: Int, addr1: Int, addrD: Int): Unit = {
    pokeInstructionWithSem(dut, mode, size, addr0, addr1, addrD)
  }

  /** Poke instruction with optional semaphore dependencies.
   *  Each sem option is (semAddr, stepSize). */
  def pokeInstructionWithSem(dut: SysWrapper, mode: Int, size: Int,
                             addr0: Int, addr1: Int, addrD: Int,
                             sem0: Option[(Int, Int)] = None,
                             sem1: Option[(Int, Int)] = None,
                             semD: Option[(Int, Int)] = None): Unit = {
    dut.io.in.response.valid.poke(true.B)
    dut.io.in.response.bits.readData.op.poke(0.U)
    dut.io.in.response.bits.readData.mode.poke(mode.U)
    dut.io.in.response.bits.readData.grainSize.poke(0.U)
    dut.io.in.response.bits.readData.size.poke(size.U)

    val ports = Seq(
      (dut.io.in.response.bits.readData.addrs(0), addr0, sem0),
      (dut.io.in.response.bits.readData.addrs(1), addr1, sem1),
      (dut.io.in.response.bits.readData.addrd(0), addrD, semD)
    )
    for ((port, addr, sem) <- ports) {
      port.addr.poke(addr.U)
      sem match {
        case Some((semAddr, stepSize)) =>
          port.sem.valid.poke(true.B)
          port.sem.bits.addr.poke(semAddr.U)
          port.sem.bits.stepSize.valid.poke(true.B)
          port.sem.bits.stepSize.bits.poke(stepSize.U)
        case None =>
          port.sem.valid.poke(false.B)
          port.sem.bits.addr.poke(0.U)
          port.sem.bits.stepSize.valid.poke(false.B)
          port.sem.bits.stepSize.bits.poke(0.U)
      }
    }
  }

  /** Handle a semaphore TL handshake: accept A-channel request, send D-channel response. */
  def handleSemaphoreOp(clock: chisel3.Clock, port: TilelinkPort,
                         expectedParam: Int, expectedAddr: Option[Int] = None): Unit = {
    // Phase 1: accept A request
    port.a.ready.poke(true.B)
    waitFor(clock)(port.a.valid.peek().litToBoolean, s"semaphore A valid (param=$expectedParam)")
    port.a.bits.opcode.expect(TilelinkOpcodes.ArithmeticData)
    port.a.bits.param.expect(expectedParam.U)
    expectedAddr.foreach(a => port.a.bits.address.expect(a.U))
    clock.step()
    port.a.ready.poke(false.B)

    // Phase 2: send D response
    port.d.valid.poke(true.B)
    port.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
    port.d.bits.param.poke(0.U)
    port.d.bits.size.poke(1.U)
    port.d.bits.source.poke(0.U)
    port.d.bits.sink.poke(0.U)
    port.d.bits.denied.poke(0.U)
    port.d.bits.data.poke(0.U)
    port.d.bits.corrupt.poke(0.U)
    waitFor(clock)(port.d.ready.peek().litToBoolean, s"semaphore D ready (param=$expectedParam)")
    clock.step()
    port.d.valid.poke(false.B)
  }

  /** Send AccessAckData beats on a TL D-channel. */
  def sendAccessAckData(clock: chisel3.Clock, port: TilelinkPort,
                        data: Seq[BigInt], size: Int): Unit = {
    for (beat <- data.indices) {
      port.d.valid.poke(true.B)
      port.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
      port.d.bits.param.poke(0.U)
      port.d.bits.size.poke(size.U)
      port.d.bits.source.poke(0.U)
      port.d.bits.sink.poke(0.U)
      port.d.bits.denied.poke(0.U)
      port.d.bits.data.poke(data(beat).U)
      port.d.bits.corrupt.poke(0.U)

      waitFor(clock)(port.d.ready.peek().litToBoolean, s"DMA ready for D-beat $beat")
      clock.step()
    }
    port.d.valid.poke(false.B)
  }

  /** Send AccessAck on channel D (write acknowledgement). */
  def sendAccessAck(clock: chisel3.Clock, port: TilelinkPort): Unit = {
    port.d.valid.poke(true.B)
    port.d.bits.opcode.poke(TilelinkOpcodes.AccessAck)
    port.d.bits.param.poke(0.U)
    port.d.bits.size.poke(0.U)
    port.d.bits.source.poke(0.U)
    port.d.bits.sink.poke(0.U)
    port.d.bits.denied.poke(0.U)
    port.d.bits.data.poke(0.U)
    port.d.bits.corrupt.poke(0.U)

    waitFor(clock)(port.d.ready.peek().litToBoolean, "DMA ready for AccessAck")
    clock.step()
    port.d.valid.poke(false.B)
  }

  // ── Tests ──────────────────────────────────────────────────────────────────

  "SysWrapper should have basic dataflow" in {
    simulate(new SysWrapper()(Configuration.test())) { dut =>
      // Initialize all TL ports to idle
      dut.io.scratchIn.flatten.foreach(pokeTLIdle)
      dut.io.scratchOut.foreach(pokeTLIdle)
      dut.io.readSemaphoreIF.flatten.foreach(pokeTLIdle)
      dut.io.writeSemaphoreIF.foreach(pokeTLIdle)

      val readPort0 = dut.io.scratchIn(0)(0)
      val readPort1 = dut.io.scratchIn(1)(0)
      val writePort = dut.io.scratchOut(0)

      // ── Issue instruction ──
      dut.io.in.request.ready.poke(true.B)
      dut.io.in.request.valid.expect(true.B)

      pokeInstruction(dut, mode = 0, size = n,
        addr0 = 0, addr1 = 8, addrD = 16)

      dut.clock.step()
      dut.io.in.response.valid.poke(false.B)
      dut.io.in.request.ready.poke(false.B)

      // ── Wait for read Gets ──
      readPort0.a.ready.poke(true.B)
      readPort1.a.ready.poke(true.B)

      waitFor(dut.clock)(
        readPort0.a.valid.peek().litToBoolean && readPort1.a.valid.peek().litToBoolean,
        "both read DMAs issue Gets"
      )

      readPort0.a.bits.opcode.expect(TilelinkOpcodes.Get)
      readPort0.a.bits.address.expect(0.U)
      readPort0.a.bits.size.expect(n.U)

      readPort1.a.bits.opcode.expect(TilelinkOpcodes.Get)
      readPort1.a.bits.address.expect(8.U)
      readPort1.a.bits.size.expect(n.U)

      dut.clock.step()
      readPort0.a.ready.poke(false.B)
      readPort1.a.ready.poke(false.B)

      // ── Feed read data (8 beats per port) ──
      // WS mode: both ports receive rows of the matrix
      val inputRows = (0 until n).map(i => packRow(matrix3(i).toSeq))

      sendAccessAckData(dut.clock, readPort0, inputRows, n)
      sendAccessAckData(dut.clock, readPort1, inputRows, n)

      // ── Wait for write PutFullData ──
      writePort.a.ready.poke(true.B)
      waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, "write DMA issues PutFullData")

      writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      writePort.a.bits.address.expect(16.U)

      // ── Collect and verify write data ──
      val expectedResult = matrixDotProduct(matrix3, matrix3)

  
      /* 
      for (row <- 0 until n) {
        for (col <- 0 until n) {
          print(expectedResult(row)(col) + ", ")
        }
        print("\n")
      }
      */

      for (row <- 0 until n) {
        waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, s"write beat $row")
        writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)

        val writtenData = writePort.a.bits.data.peek().litValue
        val writtenRow = unpackRow(writtenData)

        for (col <- 0 until n) {
          assert(writtenRow(col) == (expectedResult(row)(col) & 0xFF),
            s"Mismatch at ($row,$col): got ${writtenRow(col)}, expected ${expectedResult(row)(col) & 0xFF}")
        }

        dut.clock.step()
      }
      writePort.a.ready.poke(false.B)

      // ── Acknowledge write ──
      sendAccessAck(dut.clock, writePort)
    }
  }
  "SysWrapper should execute OS without semaphore" in {
    simulate(new SysWrapper()(Configuration.test())) { dut =>
      dut.io.scratchIn.flatten.foreach(pokeTLIdle)
      dut.io.scratchOut.foreach(pokeTLIdle)
      dut.io.readSemaphoreIF.flatten.foreach(pokeTLIdle)
      dut.io.writeSemaphoreIF.foreach(pokeTLIdle)

      val readPort0 = dut.io.scratchIn(0)(0)
      val readPort1 = dut.io.scratchIn(1)(0)
      val writePort = dut.io.scratchOut(0)

      // ── Issue instruction ──
      dut.io.in.request.ready.poke(true.B)
      dut.io.in.request.valid.expect(true.B)

      pokeInstruction(dut, mode = 1, size = n,
        addr0 = 0, addr1 = 8, addrD = 16)

      dut.clock.step()
      dut.io.in.response.valid.poke(false.B)
      dut.io.in.request.ready.poke(false.B)

      // ── Wait for read Gets ──
      readPort0.a.ready.poke(true.B)
      readPort1.a.ready.poke(true.B)

      waitFor(dut.clock)(
        readPort0.a.valid.peek().litToBoolean && readPort1.a.valid.peek().litToBoolean,
        "both read DMAs issue Gets"
      )

      readPort0.a.bits.opcode.expect(TilelinkOpcodes.Get)
      readPort0.a.bits.address.expect(0.U)

      readPort1.a.bits.opcode.expect(TilelinkOpcodes.Get)
      readPort1.a.bits.address.expect(8.U)

      dut.clock.step()
      readPort0.a.ready.poke(false.B)
      readPort1.a.ready.poke(false.B)

      // ── Feed read data (8 beats per port) ──
      // OS mode: port 0 gets transposed data (columns of A), port 1 gets rows of B
      val inputRows0 = (0 until n).map(i => packRow((0 until n).map(k => matrix3(k)(i)).toSeq))
      val inputRows1 = (0 until n).map(i => packRow(matrix3(i).toSeq))

      sendAccessAckData(dut.clock, readPort0, inputRows0, n)
      sendAccessAckData(dut.clock, readPort1, inputRows1, n)

      // ── Wait for write PutFullData ──
      writePort.a.ready.poke(true.B)
      waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, "write DMA issues PutFullData")

      writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      writePort.a.bits.address.expect(16.U)

      // ── Collect and verify write data ──
      val expectedResult = matrixDotProduct(matrix3, matrix3)

      for (row <- 0 until n) {
        waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, s"write beat $row")
        writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)

        val writtenData = writePort.a.bits.data.peek().litValue
        val writtenRow = unpackRow(writtenData)

        for (col <- 0 until n) {
          assert(writtenRow(col) == (expectedResult(row)(col) & 0xFF),
            s"Mismatch at ($row,$col): got ${writtenRow(col)}, expected ${expectedResult(row)(col) & 0xFF}")
        }

        dut.clock.step()
      }
      writePort.a.ready.poke(false.B)

      // ── Acknowledge write ──
      sendAccessAck(dut.clock, writePort)
    }
  }

  "SysWrapper should execute WS with read semaphores" in {
    simulate(new SysWrapper()(Configuration.test())) { dut =>
      dut.io.scratchIn.flatten.foreach(pokeTLIdle)
      dut.io.scratchOut.foreach(pokeTLIdle)
      dut.io.readSemaphoreIF.flatten.foreach(pokeTLIdle)
      dut.io.writeSemaphoreIF.foreach(pokeTLIdle)

      val readPort0 = dut.io.scratchIn(0)(0)
      val readPort1 = dut.io.scratchIn(1)(0)
      val writePort = dut.io.scratchOut(0)
      val readSem0  = dut.io.readSemaphoreIF(0)(0)
      val readSem1  = dut.io.readSemaphoreIF(1)(0)

      // ── Issue instruction with read semaphores (stepSize = size, single chunk) ──
      dut.io.in.request.ready.poke(true.B)
      dut.io.in.request.valid.expect(true.B)

      pokeInstructionWithSem(dut, mode = 0, size = n,
        addr0 = 0, addr1 = 8, addrD = 16,
        sem0 = Some((100, n)), sem1 = Some((200, n)))

      dut.clock.step()
      dut.io.in.response.valid.poke(false.B)
      dut.io.in.request.ready.poke(false.B)

      // ── Semaphore acquires (AQGREQ = 6) ──
      handleSemaphoreOp(dut.clock, readSem0, expectedParam = 6, expectedAddr = Some(100))
      handleSemaphoreOp(dut.clock, readSem1, expectedParam = 6, expectedAddr = Some(200))

      // ── Wait for read Gets ──
      readPort0.a.ready.poke(true.B)
      readPort1.a.ready.poke(true.B)

      waitFor(dut.clock)(
        readPort0.a.valid.peek().litToBoolean && readPort1.a.valid.peek().litToBoolean,
        "both read DMAs issue Gets"
      )

      readPort0.a.bits.opcode.expect(TilelinkOpcodes.Get)
      readPort0.a.bits.address.expect(0.U)
      readPort1.a.bits.opcode.expect(TilelinkOpcodes.Get)
      readPort1.a.bits.address.expect(8.U)

      dut.clock.step()
      readPort0.a.ready.poke(false.B)
      readPort1.a.ready.poke(false.B)

      // ── Feed read data ──
      val inputRows = (0 until n).map(i => packRow(matrix3(i).toSeq))
      sendAccessAckData(dut.clock, readPort0, inputRows, n)
      sendAccessAckData(dut.clock, readPort1, inputRows, n)

      // ── Semaphore releases (ADDU = 7) ──
      handleSemaphoreOp(dut.clock, readSem0, expectedParam = 7, expectedAddr = Some(100))
      handleSemaphoreOp(dut.clock, readSem1, expectedParam = 7, expectedAddr = Some(200))

      // ── Wait for write PutFullData and verify ──
      writePort.a.ready.poke(true.B)
      waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, "write DMA issues PutFullData")
      writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      writePort.a.bits.address.expect(16.U)

      val expectedResult = matrixDotProduct(matrix3, matrix3)
      for (row <- 0 until n) {
        waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, s"write beat $row")
        writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
        val writtenData = writePort.a.bits.data.peek().litValue
        val writtenRow = unpackRow(writtenData)
        for (col <- 0 until n) {
          assert(writtenRow(col) == (expectedResult(row)(col) & 0xFF),
            s"Mismatch at ($row,$col): got ${writtenRow(col)}, expected ${expectedResult(row)(col) & 0xFF}")
        }
        dut.clock.step()
      }
      writePort.a.ready.poke(false.B)

      sendAccessAck(dut.clock, writePort)
    }
  }

  "SysWrapper should execute WS with read and write semaphores" in {
    simulate(new SysWrapper()(Configuration.test())) { dut =>
      dut.io.scratchIn.flatten.foreach(pokeTLIdle)
      dut.io.scratchOut.foreach(pokeTLIdle)
      dut.io.readSemaphoreIF.flatten.foreach(pokeTLIdle)
      dut.io.writeSemaphoreIF.foreach(pokeTLIdle)

      val readPort0 = dut.io.scratchIn(0)(0)
      val readPort1 = dut.io.scratchIn(1)(0)
      val writePort = dut.io.scratchOut(0)
      val readSem0  = dut.io.readSemaphoreIF(0)(0)
      val readSem1  = dut.io.readSemaphoreIF(1)(0)
      val writeSem  = dut.io.writeSemaphoreIF(0)

      // ── Issue instruction with semaphores on all addresses ──
      dut.io.in.request.ready.poke(true.B)
      dut.io.in.request.valid.expect(true.B)

      pokeInstructionWithSem(dut, mode = 0, size = n,
        addr0 = 0, addr1 = 8, addrD = 16,
        sem0 = Some((100, n)), sem1 = Some((200, n)), semD = Some((300, n)))

      dut.clock.step()
      dut.io.in.response.valid.poke(false.B)
      dut.io.in.request.ready.poke(false.B)

      // ── Read semaphore acquires ──
      handleSemaphoreOp(dut.clock, readSem0, expectedParam = 6, expectedAddr = Some(100))
      handleSemaphoreOp(dut.clock, readSem1, expectedParam = 6, expectedAddr = Some(200))

      // ── Read Gets ──
      readPort0.a.ready.poke(true.B)
      readPort1.a.ready.poke(true.B)
      waitFor(dut.clock)(
        readPort0.a.valid.peek().litToBoolean && readPort1.a.valid.peek().litToBoolean,
        "both read DMAs issue Gets"
      )
      readPort0.a.bits.opcode.expect(TilelinkOpcodes.Get)
      readPort1.a.bits.opcode.expect(TilelinkOpcodes.Get)
      dut.clock.step()
      readPort0.a.ready.poke(false.B)
      readPort1.a.ready.poke(false.B)

      // ── Feed read data ──
      val inputRows = (0 until n).map(i => packRow(matrix3(i).toSeq))
      sendAccessAckData(dut.clock, readPort0, inputRows, n)
      sendAccessAckData(dut.clock, readPort1, inputRows, n)

      // ── Read semaphore releases ──
      handleSemaphoreOp(dut.clock, readSem0, expectedParam = 7, expectedAddr = Some(100))
      handleSemaphoreOp(dut.clock, readSem1, expectedParam = 7, expectedAddr = Some(200))

      // ── Write semaphore acquire ──
      handleSemaphoreOp(dut.clock, writeSem, expectedParam = 6, expectedAddr = Some(300))

      // ── Write PutFullData ──
      writePort.a.ready.poke(true.B)
      waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, "write DMA issues PutFullData")
      writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      writePort.a.bits.address.expect(16.U)

      val expectedResult = matrixDotProduct(matrix3, matrix3)
      for (row <- 0 until n) {
        waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, s"write beat $row")
        writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
        val writtenData = writePort.a.bits.data.peek().litValue
        val writtenRow = unpackRow(writtenData)
        for (col <- 0 until n) {
          assert(writtenRow(col) == (expectedResult(row)(col) & 0xFF),
            s"Mismatch at ($row,$col): got ${writtenRow(col)}, expected ${expectedResult(row)(col) & 0xFF}")
        }
        dut.clock.step()
      }
      writePort.a.ready.poke(false.B)

      // ── Write ack ──
      sendAccessAck(dut.clock, writePort)

      // ── Write semaphore release ──
      handleSemaphoreOp(dut.clock, writeSem, expectedParam = 7, expectedAddr = Some(300))
    }
  }

  "SysWrapper should execute WS with chunked semaphore reads" in {
    val stepSize = 4
    val numChunks = n / stepSize

    simulate(new SysWrapper()(Configuration.test())) { dut =>
      dut.io.scratchIn.flatten.foreach(pokeTLIdle)
      dut.io.scratchOut.foreach(pokeTLIdle)
      dut.io.readSemaphoreIF.flatten.foreach(pokeTLIdle)
      dut.io.writeSemaphoreIF.foreach(pokeTLIdle)

      val readPort0 = dut.io.scratchIn(0)(0)
      val readPort1 = dut.io.scratchIn(1)(0)
      val writePort = dut.io.scratchOut(0)
      val readSem0  = dut.io.readSemaphoreIF(0)(0)
      val readSem1  = dut.io.readSemaphoreIF(1)(0)

      // ── Issue instruction with chunked read semaphores ──
      dut.io.in.request.ready.poke(true.B)
      dut.io.in.request.valid.expect(true.B)

      pokeInstructionWithSem(dut, mode = 0, size = n,
        addr0 = 0, addr1 = 8, addrD = 16,
        sem0 = Some((100, stepSize)), sem1 = Some((200, stepSize)))

      dut.clock.step()
      dut.io.in.response.valid.poke(false.B)
      dut.io.in.request.ready.poke(false.B)

      val inputRows = (0 until n).map(i => packRow(matrix3(i).toSeq))

      // ── Process each chunk: acquire → read stepSize beats → release ──
      for (chunk <- 0 until numChunks) {
        val offset = chunk * stepSize

        // Semaphore acquires
        handleSemaphoreOp(dut.clock, readSem0, expectedParam = 6, expectedAddr = Some(100))
        handleSemaphoreOp(dut.clock, readSem1, expectedParam = 6, expectedAddr = Some(200))

        // Wait for Gets with correct addresses
        readPort0.a.ready.poke(true.B)
        readPort1.a.ready.poke(true.B)
        waitFor(dut.clock)(
          readPort0.a.valid.peek().litToBoolean && readPort1.a.valid.peek().litToBoolean,
          s"both read DMAs issue Gets for chunk $chunk"
        )

        readPort0.a.bits.opcode.expect(TilelinkOpcodes.Get)
        readPort0.a.bits.address.expect((0 + offset).U)
        readPort0.a.bits.size.expect(stepSize.U)

        readPort1.a.bits.opcode.expect(TilelinkOpcodes.Get)
        readPort1.a.bits.address.expect((8 + offset).U)
        readPort1.a.bits.size.expect(stepSize.U)

        dut.clock.step()
        readPort0.a.ready.poke(false.B)
        readPort1.a.ready.poke(false.B)

        // Feed chunk of read data (stepSize beats per port)
        val chunkRows = inputRows.slice(offset, offset + stepSize)
        sendAccessAckData(dut.clock, readPort0, chunkRows, stepSize)
        sendAccessAckData(dut.clock, readPort1, chunkRows, stepSize)

        // Semaphore releases
        handleSemaphoreOp(dut.clock, readSem0, expectedParam = 7, expectedAddr = Some(100))
        handleSemaphoreOp(dut.clock, readSem1, expectedParam = 7, expectedAddr = Some(200))
      }

      // ── Write phase (no semaphore on destination) ──
      writePort.a.ready.poke(true.B)
      waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, "write DMA issues PutFullData")
      writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      writePort.a.bits.address.expect(16.U)

      val expectedResult = matrixDotProduct(matrix3, matrix3)
      for (row <- 0 until n) {
        waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, s"write beat $row")
        writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
        val writtenData = writePort.a.bits.data.peek().litValue
        val writtenRow = unpackRow(writtenData)
        for (col <- 0 until n) {
          assert(writtenRow(col) == (expectedResult(row)(col) & 0xFF),
            s"Mismatch at ($row,$col): got ${writtenRow(col)}, expected ${expectedResult(row)(col) & 0xFF}")
        }
        dut.clock.step()
      }
      writePort.a.ready.poke(false.B)

      sendAccessAck(dut.clock, writePort)
    }
  }
}
