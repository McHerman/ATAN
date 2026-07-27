package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class ExecuteTest extends AnyFreeSpec with Matchers with ChiselSim {

  val n = 8
  //val n_in_bytes = 64 
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

  def packRow(elements: Seq[Int]): BigInt = {
    elements.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (elem, i)) =>
      acc | (BigInt(elem & 0xFF) << (i * 8))
    }
  }

  def unpackRow(value: BigInt): Seq[Int] = {
    (0 until 8).map(i => ((value >> (i * 8)) & 0xFF).toInt)
  }

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

  def pokeSemIdle(dut: Execute): Unit = {
    dut.io.writeSemaphoreIF.foreach(pokeTLIdle)
    dut.io.readSemaphoreIF.flatten.foreach(pokeTLIdle)
  }

  def pokeInstruction(dut: Execute, mode: Int, size: Int,
                      addr0: Int, addr1: Int, addrD: Int): Unit = {
    dut.io.instructionStream.valid.poke(true.B)
    dut.io.instructionStream.bits.opcode.poke(1.U)
    dut.io.instructionStream.bits.func.poke(0.U)
    dut.io.instructionStream.bits.mode.poke(mode.U)
    dut.io.instructionStream.bits.size.poke(size.U)

    val ports = Seq(
      (dut.io.instructionStream.bits.addrs(0), addr0),
      (dut.io.instructionStream.bits.addrs(1), addr1),
      (dut.io.instructionStream.bits.addrd(0), addrD)
    )
    for ((port, addr) <- ports) {
      port.addr.poke(addr.U)
      port.sem.valid.poke(false.B)
      port.sem.bits.addr.poke(0.U)
      port.sem.bits.stepSize.valid.poke(false.B)
      port.sem.bits.stepSize.bits.poke(0.U)
    }
  }

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

  "Execute should compute WS matrix multiply without semaphores" in {
    implicit val c = Configuration.default()

    simulate(new Execute()) { dut =>

      val readPort0 = dut.io.scratchIn(0)
      val readPort1 = dut.io.scratchIn(1)
      val writePort = dut.io.scratchOut(0)

      // ── Issue instruction (WS mode = 0) ──
      pokeInstruction(dut, mode = 0, size = (n * c.dataBusSize),
        addr0 = 0, addr1 = n*n, addrD = 2*n*n)

      waitFor(dut.clock)(dut.io.instructionStream.ready.peek().litToBoolean, "instructionStream.ready")
      dut.clock.step()
      dut.io.instructionStream.valid.poke(false.B)

      // ── Wait for read Gets ──
      readPort0.a.ready.poke(true.B)
      readPort1.a.ready.poke(true.B)

      waitFor(dut.clock)(
        readPort0.a.valid.peek().litToBoolean && readPort1.a.valid.peek().litToBoolean,
        "both read DMAs issue Gets"
      )

      readPort0.a.bits.opcode.expect(TilelinkOpcodes.Get)
      readPort0.a.bits.address.expect(0.U)
      readPort0.a.bits.size.expect((n * c.dataBusSize).U)

      readPort1.a.bits.opcode.expect(TilelinkOpcodes.Get)
      readPort1.a.bits.address.expect((n*n).U)
      readPort1.a.bits.size.expect((n * c.dataBusSize).U)

      dut.clock.step()
      readPort0.a.ready.poke(false.B)
      readPort1.a.ready.poke(false.B)

      // ── Feed read data (8 beats per port) ──
      val inputRows = (0 until n).map(i => packRow(matrix3(i).toSeq))

      sendAccessAckData(dut.clock, readPort0, inputRows, (n * c.dataBusSize))
      sendAccessAckData(dut.clock, readPort1, inputRows, (n * c.dataBusSize))

      // ── Wait for write PutFullData ──
      writePort.a.ready.poke(true.B)
      waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, "write DMA issues PutFullData")

      writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      writePort.a.bits.address.expect((2*n*n).U)

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

  "Execute should compute OS matrix multiply without semaphores" in {
    implicit val c = Configuration.test()

    simulate(new Execute()) { dut =>

      val readPort0 = dut.io.scratchIn(0)
      val readPort1 = dut.io.scratchIn(1)
      val writePort = dut.io.scratchOut(0)

      // ── Issue instruction (OS mode = 1) ──
      pokeInstruction(dut, mode = 1, size = (n * c.dataBusSize),
        addr0 = 0, addr1 = n*n, addrD = 2*n*n)

      waitFor(dut.clock)(dut.io.instructionStream.ready.peek().litToBoolean, "instructionStream.ready")
      dut.clock.step()
      dut.io.instructionStream.valid.poke(false.B)

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
      readPort1.a.bits.address.expect((n*n).U)

      dut.clock.step()
      readPort0.a.ready.poke(false.B)
      readPort1.a.ready.poke(false.B)

      // ── Feed read data ──
      // OS mode: port 0 gets transposed data (columns of A), port 1 gets rows of B
      val inputRows0 = (0 until n).map(i => packRow((0 until n).map(k => matrix3(k)(i)).toSeq))
      val inputRows1 = (0 until n).map(i => packRow(matrix3(i).toSeq))

      sendAccessAckData(dut.clock, readPort0, inputRows0, (n * c.dataBusSize))
      sendAccessAckData(dut.clock, readPort1, inputRows1, (n * c.dataBusSize))

      // ── Wait for write PutFullData ──
      writePort.a.ready.poke(true.B)
      waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, "write DMA issues PutFullData")

      writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      writePort.a.bits.address.expect((2*n*n).U)

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
