package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import scala.util.Random
import scala.io.Source

class SysArrayTest extends AnyFreeSpec with Matchers with ChiselSim {

  val maxCycles = 1000

  def waitFor(clock: chisel3.Clock)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      clock.step()
      cycles += 1
    }
  }

  //val n = 2 + Random.nextInt(30)
  val n = 8
  val weight = "W_Matrix.txt"
  val activation = "A_Matrix.txt"

  val matrix: Array[Array[Int]] = Array(
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

  "SysArray should execute WS" in {
    //val n = 2 + Random.nextInt(30)
    //val n = 16

    // Grain-level unit test: keep arrayDim == dataBusSize (both 8) so a raw
    // writePort beat maps 1:1 onto array lanes without exercising the
    // BeatUnpacker deserialization logic, which is covered separately by
    // BeatUnpackerTest at the real 16/64 configuration. All three groups must
    // be overridden together since Configuration's require()s are checked on
    // every intermediate .withX() copy.
    implicit val c = Configuration(
      bus      = BusParams(dataBusSize = 8, axiStreamWidth = 64),
      data     = DatapathParams(accDataWidth = 8),
      systolic = SystolicParams(arrayDim = 8),
    )

    simulate(new Grain()) { dut =>

      for (i <- 0 until 8) {

        waitFor(dut.clock)(dut.io.writePort(1)(0).ready.peek().litToBoolean, "writePort(1)(0).ready")
        dut.io.writePort(1)(0).valid.poke(true.B)

        for(k <- 0 until 8){
          dut.io.writePort(1)(0).bits(k).poke(matrix(i)(k).U(8.W))
        }

        dut.clock.step()
      }

      dut.io.writePort(1)(0).valid.poke(false.B)

      for (i <- 0 until 8) {
        waitFor(dut.clock)(dut.io.writePort(0)(0).ready.peek().litToBoolean, "writePort(0)(0).ready")
        dut.io.writePort(0)(0).valid.poke(true.B)


        for(k <- 0 until 8){
          dut.io.writePort(0)(0).bits(k).poke(matrix(i)(k).U(8.W))
        }

        dut.clock.step()
      }

      dut.io.writePort(0)(0).valid.poke(false.B)

      dut.clock.step(10)

      /* dut.io.Trigger.poke(true.B)
      dut.io.Size.poke(8.U) */

      dut.io.in.valid.poke(true.B)
      dut.io.in.bits.mode.poke(0.U)
      dut.io.in.bits.size.poke(8.U)
      dut.io.in.bits.sizes(0).poke(8.U)
      dut.io.in.bits.rows.poke(8.U)

      dut.clock.step()

      dut.io.in.valid.poke(false.B)


      //dut.io.Trigger.poke(false.B)

      dut.clock.step(80)

      val expectedResult = matrixDotProduct(matrix, matrix)

      dut.io.readPort(0).request.ready.expect(true.B)
      dut.io.readPort(0).request.valid.poke(true.B)

      for (row <- 0 until n) {
        dut.io.readPort(0).response.valid.expect(true.B)

        for (col <- 0 until n) {
          dut.io.readPort(0).response.bits.readData(col).expect(expectedResult(row)(col).U(8.W))
        }

        dut.clock.step()
      }
    }
  }

  "SysArray should execute OS" in {
    // Grain-level unit test: keep arrayDim == dataBusSize (both 8) so a raw
    // writePort beat maps 1:1 onto array lanes without exercising the
    // BeatUnpacker deserialization logic, which is covered separately by
    // BeatUnpackerTest at the real 16/64 configuration. All three groups must
    // be overridden together since Configuration's require()s are checked on
    // every intermediate .withX() copy.
    implicit val c = Configuration(
      bus      = BusParams(dataBusSize = 8, axiStreamWidth = 64),
      data     = DatapathParams(accDataWidth = 8),
      systolic = SystolicParams(arrayDim = 8),
    )

    simulate(new Grain()) { dut =>

      for (i <- 0 until 8) {

        waitFor(dut.clock)(dut.io.writePort(1)(0).ready.peek().litToBoolean, "writePort(1)(0).ready")
        dut.io.writePort(1)(0).valid.poke(true.B)

        for(k <- 0 until 8){
          dut.io.writePort(1)(0).bits(k).poke(matrix(i)(k).U(8.W))
        }

        dut.clock.step()
      }

      dut.io.writePort(1)(0).valid.poke(false.B)

      for (i <- 0 until 8) {
        waitFor(dut.clock)(dut.io.writePort(0)(0).ready.peek().litToBoolean, "writePort(0)(0).ready")
        dut.io.writePort(0)(0).valid.poke(true.B)


        for(k <- 0 until 8){
          dut.io.writePort(0)(0).bits(k).poke(matrix(k)(i).U(8.W))
        }

        dut.clock.step()
      }

      dut.io.writePort(0)(0).valid.poke(false.B)

      dut.clock.step(10)

      /* dut.io.Trigger.poke(true.B)
      dut.io.Size.poke(8.U) */

      dut.io.in.valid.poke(true.B)
      dut.io.in.bits.mode.poke(1.U)
      dut.io.in.bits.size.poke(8.U)
      dut.io.in.bits.sizes(0).poke(8.U)
      dut.io.in.bits.rows.poke(8.U)

      dut.clock.step()

      dut.io.in.valid.poke(false.B)


      //dut.io.Trigger.poke(false.B)

      dut.clock.step(80)

      val expectedResult = matrixDotProduct(matrix, matrix)

      dut.io.readPort(0).request.ready.expect(true.B)
      dut.io.readPort(0).request.valid.poke(true.B)

      for (row <- 0 until n) {
        dut.io.readPort(0).response.valid.expect(true.B)

        for (col <- 0 until n) {
          dut.io.readPort(0).response.bits.readData(col).expect(expectedResult(row)(col).U(8.W))
        }

        dut.clock.step()
      }
    }
  }
}

