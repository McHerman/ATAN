package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

// Proves the systolic array computes a correct 16x16 matrix multiply at the
// real target config (arrayDim=16, dataBusSize=64, accDataWidth=32), fed via
// full 64-byte beats through writePort so BeatUnpacker actually deserializes
// 4 sub-rows per beat during a live matmul -- a combination neither
// SysArrayTest (small, non-serializing config, row-invariant test data) nor
// BeatUnpackerTest (isolated unpacker, no live array) exercises together.
class GrainWideBeatTest extends AnyFreeSpec with Matchers with ChiselSim {

  implicit val c: Configuration = Configuration.default()
  val n = c.arrayDim
  val rowsPerBeat = c.dataBusSize / c.arrayDim

  def waitFor(clock: chisel3.Clock)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < 2000, s"Timeout waiting for: $msg")
      clock.step()
      cycles += 1
    }
  }

  // Feeds one arrayDim x arrayDim matrix into a writePort, one dataBusSize
  // beat (rowsPerBeat rows) at a time, exercising BeatUnpacker's real
  // deserialization path rather than poking individual lanes directly.
  def feedMatrix(dut: Grain, portVec: Int, mat: Array[Array[Int]]): Unit = {
    for (beatIdx <- 0 until n / rowsPerBeat) {
      waitFor(dut.clock)(dut.io.writePort(portVec)(0).ready.peek().litToBoolean, s"writePort($portVec) ready")
      dut.io.writePort(portVec)(0).valid.poke(true.B)
      for (r <- 0 until rowsPerBeat) {
        val row = beatIdx * rowsPerBeat + r
        for (lane <- 0 until n) {
          dut.io.writePort(portVec)(0).bits(r * n + lane).poke(mat(row)(lane).U(8.W))
        }
      }
      dut.clock.step()
    }
    dut.io.writePort(portVec)(0).valid.poke(false.B)
  }

  // Runs a weight-stationary a @ b matmul and returns the arrayDim x arrayDim
  // result in natural (unreversed) row/col order.
  //
  // Weights (port 1 / Y) load via a wavefront shift: a value entering PE
  // column 0 at time t reaches column k at time t+k, so whatever is
  // *stationary* in column k once the shift phase ends is the value that
  // entered at time (n-1-k) -- i.e. feeding b naturally would leave PE
  // column k holding row (n-1-k) of b, not row k. Feeding b pre-reversed by
  // row exactly cancels this, so the array computes a standard a @ b. This
  // is an inherent property of the weight-stationary wavefront load (
  // confirmed independent of array size, not something this refactor
  // changes) -- not a workaround for a bug.
  def runMatmul(dut: Grain, a: Array[Array[Int]], b: Array[Array[Int]]): Array[Array[BigInt]] = {
    feedMatrix(dut, 1, b.reverse) // Y = weights, pre-reversed
    feedMatrix(dut, 0, a)         // X = activations, natural order

    dut.clock.step(10)
    dut.io.in.valid.poke(true.B)
    dut.io.in.bits.mode.poke(0.U)
    dut.io.in.bits.size.poke(n.U)
    dut.io.in.bits.sizes(0).poke(n.U)
    dut.clock.step()
    dut.io.in.valid.poke(false.B)
    dut.clock.step(300)

    dut.io.readPort(0).request.valid.poke(true.B)
    Array.tabulate(n) { _ =>
      dut.io.readPort(0).response.valid.expect(true.B)
      val out = Array.tabulate(n)(col => dut.io.readPort(0).response.bits.readData(col).peek().litValue)
      dut.clock.step()
      out
    }
  }

  def assertMatmulCorrect(a: Array[Array[Int]], b: Array[Array[Int]]): Unit = {
    val expected = Array.tabulate(n, n) { (row, col) =>
      (0 until n).map(k => BigInt(a(row)(k)) * BigInt(b(k)(col))).sum
    }
    simulate(new Grain()) { dut =>
      val got = runMatmul(dut, a, b)
      for (row <- 0 until n; col <- 0 until n) {
        assert(got(row)(col) == expected(row)(col),
          s"Mismatch at ($row,$col): got ${got(row)(col)}, expected ${expected(row)(col)}")
      }
    }
  }

  "Grain computes a correct 16x16 matrix multiply at the real target config, fed via wide beats through BeatUnpacker" in {
    val a: Array[Array[Int]] = Array.tabulate(n, n)((row, k) => (row * 7 + k * 5 + 3) % 227)
    val b: Array[Array[Int]] = Array.tabulate(n, n)((k, col) => (k * 11 + col * 13 + 17) % 199)
    assertMatmulCorrect(a, b)
  }

  "Grain 16x16 matmul is correct for a second, independent pair of matrices" in {
    val a: Array[Array[Int]] = Array.tabulate(n, n)((row, k) => (row * 3 + k * 17 + 1) % 211)
    val b: Array[Array[Int]] = Array.tabulate(n, n)((k, col) => (k * 19 + col * 2 + 5) % 193)
    assertMatmulCorrect(a, b)
  }

  // Runs an output-stationary a @ b matmul and returns the arrayDim x
  // arrayDim result in natural (unreversed) row/col order.
  //
  // Output-stationary is a genuinely different (diagonal-wavefront) data
  // flow from weight-stationary: both X and Y stream continuously (each
  // lane's read gated by its own ACT shift-chain, staggered by lane index),
  // so PE(row=R, col=K) accumulates sum_s X_fed[s][K] * Y_fed[s][R] where s
  // is the *feed order* index, not row/col directly -- and the result
  // drains through the same wavefront shift-out PE(R,K) uses to reach
  // column (n-1), surfacing at readout cycle c = (n-1-K). Working through
  // both effects together (confirmed empirically against the live array,
  // independent of array size): feeding Y = b naturally and
  // X = transpose(a.reverse) (a with rows reversed, then transposed) makes
  // the array compute a standard a @ b, with no output-side reversal
  // needed. This is an inherent property of the output-stationary wavefront
  // load, not a workaround for a bug.
  def runMatmulOS(dut: Grain, a: Array[Array[Int]], b: Array[Array[Int]]): Array[Array[BigInt]] = {
    val aReversed = a.reverse
    val xFed = Array.tabulate(n, n)((i, j) => aReversed(j)(i)) // transpose(a.reverse)

    feedMatrix(dut, 1, b)    // Y, natural order
    feedMatrix(dut, 0, xFed) // X, transpose(a.reverse)

    dut.clock.step(10)
    dut.io.in.valid.poke(true.B)
    dut.io.in.bits.mode.poke(1.U) // OS
    dut.io.in.bits.size.poke(n.U)
    dut.io.in.bits.sizes(0).poke(n.U)
    dut.clock.step()
    dut.io.in.valid.poke(false.B)
    dut.clock.step(400)

    dut.io.readPort(0).request.valid.poke(true.B)
    Array.tabulate(n) { _ =>
      dut.io.readPort(0).response.valid.expect(true.B)
      val out = Array.tabulate(n)(col => dut.io.readPort(0).response.bits.readData(col).peek().litValue)
      dut.clock.step()
      out
    }
  }

  def assertMatmulCorrectOS(a: Array[Array[Int]], b: Array[Array[Int]]): Unit = {
    val expected = Array.tabulate(n, n) { (row, col) =>
      (0 until n).map(k => BigInt(a(row)(k)) * BigInt(b(k)(col))).sum
    }
    simulate(new Grain()) { dut =>
      val got = runMatmulOS(dut, a, b)
      for (row <- 0 until n; col <- 0 until n) {
        assert(got(row)(col) == expected(row)(col),
          s"Mismatch at ($row,$col): got ${got(row)(col)}, expected ${expected(row)(col)}")
      }
    }
  }

  "Grain computes a correct 16x16 matrix multiply in OS mode at the real target config, fed via wide beats through BeatUnpacker" in {
    val a: Array[Array[Int]] = Array.tabulate(n, n)((row, k) => (row * 7 + k * 5 + 3) % 227)
    val b: Array[Array[Int]] = Array.tabulate(n, n)((k, col) => (k * 11 + col * 13 + 17) % 199)
    assertMatmulCorrectOS(a, b)
  }

  "Grain 16x16 OS matmul is correct for a second, independent pair of matrices" in {
    val a: Array[Array[Int]] = Array.tabulate(n, n)((row, k) => (row * 3 + k * 17 + 1) % 211)
    val b: Array[Array[Int]] = Array.tabulate(n, n)((k, col) => (k * 19 + col * 2 + 5) % 193)
    assertMatmulCorrectOS(a, b)
  }
}
