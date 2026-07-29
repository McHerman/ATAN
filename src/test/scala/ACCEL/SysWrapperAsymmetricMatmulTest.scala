package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import scala.io.Source
import io.circe.generic.auto._
import io.circe.parser._

// Isolates the M != K/N (rows != size) case at the hardware level, with no
// mcc/RISC-V involved, using the exact same X/Y data as
// Riscv1x16CoexecuteE2ETest (1x16 @ 16x16 -> raw i32 accumulator). Confirmed
// bit-exact against the hand-computed expected dot products -- the
// accelerator hardware is correct for M != K/N; Riscv1x16CoexecuteE2ETest's
// remaining mismatch is downstream of this (mcc kernel / riscv memory path),
// not in the matmul itself.
class SysWrapperAsymmetricMatmulTest extends AnyFreeSpec with Matchers with ChiselSim {

  val k = 16 // contraction width / output column count (K == N == arrayDim)
  val mRows = 1
  val xBytes = mRows * k
  val yBytes = k * k
  val maxCycles = 3000

  case class Tensor(shape: Seq[Int], element_type: String, data: Seq[Int])
  case class ModelIO(inputs: Seq[Tensor], outputs: Seq[Tensor])

  val jsonStr = Source.fromFile("/home/karlhk/dtu/Thesis/hardware/ATAN/test/riscv-1x16-coexecute.reference.json").mkString
  val parsed = for {
    json  <- parse(jsonStr)
    model <- json.as[ModelIO]
  } yield model
  val inputArrays: Seq[Seq[Int]] = parsed.toOption.get.inputs.map(_.data)

  val xRow: Seq[Int] = inputArrays(0)
  val yMatrix: Seq[Seq[Int]] = inputArrays(1).grouped(k).toSeq

  def s8(v: Int): Int = if (v > 127) v - 256 else v

  val expected: Seq[Int] = (0 until k).map { col =>
    (0 until k).map(row => s8(xRow(row)) * s8(yMatrix(row)(col))).sum
  }

  def waitFor(clock: chisel3.Clock)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      clock.step()
      cycles += 1
    }
  }

  def packRow8(elements: Seq[Int]): BigInt =
    elements.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (elem, i)) => acc | (BigInt(elem & 0xFF) << (i * 8)) }

  // dataBusSize(64)/k(16) = 4 rows pack into one beat.
  def packRowsIntoBeats(rows: Seq[Seq[Int]], dataBusSize: Int): Seq[BigInt] =
    rows.grouped(dataBusSize / k).map(chunk => packRow8(chunk.flatten)).toSeq

  def unpackRow32(value: BigInt, n: Int): Seq[Int] =
    (0 until n).map { i =>
      val bits = (value >> (i * 32)) & BigInt("FFFFFFFF", 16)
      (if (bits.testBit(31)) bits - (BigInt(1) << 32) else bits).toInt
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

  def pokeInstruction(dut: SysWrapper, size: Int, rows: Int,
                       addr0: Int, addr1: Int, addrD: Int): Unit = {
    dut.io.in.response.valid.poke(true.B)
    dut.io.in.response.bits.readData.func.poke(0.U)
    dut.io.in.response.bits.readData.mode.poke(0.U)
    dut.io.in.response.bits.readData.size.poke(size.U)
    dut.io.in.response.bits.readData.rows.poke(rows.U)

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

  def sendAccessAckData(clock: chisel3.Clock, port: TilelinkPort, data: Seq[BigInt], size: Int): Unit = {
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

  "SysWrapper executes the exact 1x16 @ 16x16 riscv-coexecute matmul, raw accumulator should match hand-computed expected" in {
    implicit val c: Configuration = Configuration.large16x16()
    simulate(new SysWrapper()) { dut =>
      dut.io.scratchIn.flatten.foreach(pokeTLIdle)
      dut.io.scratchOut.foreach(pokeTLIdle)
      dut.io.readSemaphoreIF.flatten.foreach(pokeTLIdle)
      dut.io.writeSemaphoreIF.foreach(pokeTLIdle)

      val readPort0 = dut.io.scratchIn(0)(0) // X
      val readPort1 = dut.io.scratchIn(1)(0) // Y
      val writePort = dut.io.scratchOut(0)

      dut.io.in.request.ready.poke(true.B)

      pokeInstruction(dut, size = k, rows = mRows, addr0 = 0, addr1 = xBytes, addrD = xBytes + yBytes)

      dut.clock.step()
      dut.io.in.response.valid.poke(false.B)
      dut.io.in.request.ready.poke(false.B)

      readPort0.a.ready.poke(true.B)
      readPort1.a.ready.poke(true.B)

      waitFor(dut.clock)(
        readPort0.a.valid.peek().litToBoolean && readPort1.a.valid.peek().litToBoolean,
        "both read DMAs issue Gets"
      )

      readPort0.a.bits.size.expect(xBytes.U)
      readPort1.a.bits.size.expect(yBytes.U)

      dut.clock.step()
      readPort0.a.ready.poke(false.B)
      readPort1.a.ready.poke(false.B)

      sendAccessAckData(dut.clock, readPort0, Seq(packRow8(xRow)), xBytes)
      // Y fed row-reversed (established convention), packed dataBusSize/k
      // rows per beat.
      sendAccessAckData(dut.clock, readPort1, packRowsIntoBeats(yMatrix.reverse, c.dataBusSize), yBytes)

      writePort.a.ready.poke(true.B)
      waitFor(dut.clock)(writePort.a.valid.peek().litToBoolean, "write DMA issues PutFullData")

      val raw = writePort.a.bits.data.peek().litValue
      val got = unpackRow32(raw, k)

      for (col <- 0 until k) {
        assert(got(col) == expected(col), s"Mismatch at col $col: got ${got(col)}, expected ${expected(col)}")
      }
    }
  }
}
