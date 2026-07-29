package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

// Exercises a matmul genuinely smaller than the systolic array's native
// width (n=4 on an arrayDim=8 array), mirroring SysWrapperTest's "basic
// dataflow" test structure exactly but with rows packed tightly at n bytes
// each (no arrayDim padding) -- matching the assembler's real (unpadded)
// buffer layout -- to verify BeatUnpacker/XFile/YFile/ACCUFile/BeatPacker
// correctly crop to `size` instead of assuming size==arrayDim.
class SysWrapperSmallMatmulTest extends AnyFreeSpec with Matchers with ChiselSim {

  val n = 4 // matmul size, smaller than Configuration.test()'s arrayDim=8
  val totalBytes = n * n // 1 byte/element (accDataWidth=8 in Configuration.test())
  val rowsPerBeat = Configuration.test().dataBusSize / n
  val maxCycles = 2000

  val matrix: Array[Array[Int]] = Array(
    Array(1, 2, 3, 4),
    Array(1, 2, 3, 4),
    Array(1, 2, 3, 4),
    Array(1, 2, 3, 4),
  )

  def matrixDotProduct(A: Array[Array[Int]], B: Array[Array[Int]]): Array[Array[Int]] = {
    val n = A.length
    Array.tabulate(n, n) { (i, j) =>
      (0 until n).map(k => A(i)(k) * B(k)(j)).sum
    }
  }

  def waitFor(clock: chisel3.Clock)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      clock.step()
      cycles += 1
    }
  }

  /** Pack `rowsPerBeat` consecutive n-byte rows tightly into one dataBusSize-byte beat. */
  def packBeat(rows: Seq[Seq[Int]]): BigInt =
    rows.flatten.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (elem, i)) =>
      acc | (BigInt(elem & 0xFF) << (i * 8))
    }

  def packRowsIntoBeats(m: Array[Array[Int]]): Seq[BigInt] =
    m.toSeq.map(_.toSeq).grouped(rowsPerBeat).map(packBeat).toSeq

  /** Unpack one dataBusSize-byte beat into rowsPerBeat n-byte rows. */
  def unpackBeat(value: BigInt): Seq[Seq[Int]] =
    (0 until rowsPerBeat).map { r =>
      (0 until n).map(c => ((value >> ((r * n + c) * 8)) & 0xFF).toInt)
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

  def pokeInstruction(dut: SysWrapper, mode: Int, size: Int,
                       addr0: Int, addr1: Int, addrD: Int): Unit = {
    dut.io.in.response.valid.poke(true.B)
    dut.io.in.response.bits.readData.func.poke(0.U)
    dut.io.in.response.bits.readData.mode.poke(mode.U)
    dut.io.in.response.bits.readData.size.poke(size.U)
    // Square matmul in this test: rows (M) == size (K/N).
    dut.io.in.response.bits.readData.rows.poke(size.U)

    val ports = Seq(
      (dut.io.in.response.bits.readData.addrs(0), addr0),
      (dut.io.in.response.bits.readData.addrs(1), addr1),
      (dut.io.in.response.bits.readData.addrd(0), addrD)
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

  "SysWrapper executes a matmul smaller than the array (n=4, arrayDim=8)" in {
    simulate(new SysWrapper()(Configuration.test())) { dut =>
      dut.io.scratchIn.flatten.foreach(pokeTLIdle)
      dut.io.scratchOut.foreach(pokeTLIdle)
      dut.io.readSemaphoreIF.flatten.foreach(pokeTLIdle)
      dut.io.writeSemaphoreIF.foreach(pokeTLIdle)

      val readPort0 = dut.io.scratchIn(0)(0)
      val readPort1 = dut.io.scratchIn(1)(0)
      val writePort = dut.io.scratchOut(0)

      dut.io.in.request.ready.poke(true.B)
      dut.io.in.request.valid.expect(true.B)

      // size = n (raw matmul row/col trip count), not a byte-scaled value --
      // matches the assembler's Execute.size = dst.shape(0) convention.
      pokeInstruction(dut, mode = 0, size = n, addr0 = 0, addr1 = totalBytes, addrD = 2 * totalBytes)

      dut.clock.step()
      dut.io.in.response.valid.poke(false.B)
      dut.io.in.request.ready.poke(false.B)

      readPort0.a.ready.poke(true.B)
      readPort1.a.ready.poke(true.B)

      waitFor(dut.clock)(
        readPort0.a.valid.peek().litToBoolean && readPort1.a.valid.peek().litToBoolean,
        "both read DMAs issue Gets"
      )

      readPort0.a.bits.opcode.expect(TilelinkOpcodes.Get)
      readPort0.a.bits.address.expect(0.U)
      readPort0.a.bits.size.expect(totalBytes.U)

      readPort1.a.bits.opcode.expect(TilelinkOpcodes.Get)
      readPort1.a.bits.address.expect(totalBytes.U)
      readPort1.a.bits.size.expect(totalBytes.U)

      dut.clock.step()
      readPort0.a.ready.poke(false.B)
      readPort1.a.ready.poke(false.B)

      val inputBeats = packRowsIntoBeats(matrix)
      sendAccessAckData(dut.clock, readPort0, inputBeats, totalBytes)
      sendAccessAckData(dut.clock, readPort1, inputBeats, totalBytes)

      writePort.a.ready.poke(true.B)
      waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, "write DMA issues PutFullData")

      writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      writePort.a.bits.address.expect((2 * totalBytes).U)

      val expectedResult = matrixDotProduct(matrix, matrix)
      val gotRows = scala.collection.mutable.ArrayBuffer[Seq[Int]]()

      for (beatIdx <- 0 until (totalBytes / Configuration.test().dataBusSize)) {
        waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, s"write beat $beatIdx")
        writePort.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
        val raw = writePort.a.bits.data.peek().litValue
        gotRows ++= unpackBeat(raw)
        dut.clock.step()
      }
      writePort.a.ready.poke(false.B)

      for (row <- 0 until n) {
        for (col <- 0 until n) {
          assert(gotRows(row)(col) == (expectedResult(row)(col) & 0xFF),
            s"Mismatch at ($row,$col): got ${gotRows(row)(col)}, expected ${expectedResult(row)(col) & 0xFF}")
        }
      }

      sendAccessAck(dut.clock, writePort)
    }
  }
}
