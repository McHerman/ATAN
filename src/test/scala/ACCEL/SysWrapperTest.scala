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
    dut.io.in.response.valid.poke(true.B)
    dut.io.in.response.bits.readData.op.poke(0.U)
    dut.io.in.response.bits.readData.mode.poke(mode.U)
    dut.io.in.response.bits.readData.grainSize.poke(0.U)
    dut.io.in.response.bits.readData.size.poke(size.U)

    for (a <- Seq(dut.io.in.response.bits.readData.addrs(0),
                  dut.io.in.response.bits.readData.addrs(1),
                  dut.io.in.response.bits.readData.addrd(0))) {
      a.sem.valid.poke(false.B)
      a.sem.bits.addr.poke(0.U)
      a.sem.bits.stepSize.valid.poke(false.B)
      a.sem.bits.stepSize.bits.poke(0.U)
    }

    dut.io.in.response.bits.readData.addrs(0).addr.poke(addr0.U)
    dut.io.in.response.bits.readData.addrs(1).addr.poke(addr1.U)
    dut.io.in.response.bits.readData.addrd(0).addr.poke(addrD.U)
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
}
