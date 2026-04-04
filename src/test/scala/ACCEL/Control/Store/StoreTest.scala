package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers


class StoreTest extends AnyFreeSpec with Matchers with ChiselSim {

  val maxCycles = 500

  val matrix: Seq[BigInt] = Seq.fill(8)(BigInt("0102030401020304", 16))

  /** Block until `cond` is true, stepping the clock each cycle. */
  def waitFor(dut: Store, cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      dut.clock.step()
      cycles += 1
    }
  }

  /** Respond to a semaphore A-channel request with an AccessAckData on D. */
  def respondSemaphore(dut: Store, returnValue: BigInt = 0): Unit = {
    dut.io.semaphoreIF.a.ready.poke(true.B)
    waitFor(dut, dut.io.semaphoreIF.a.valid.peek().litToBoolean, "semaphoreIF.a.valid")

    val opcode = dut.io.semaphoreIF.a.bits.opcode.peek().litValue
    assert(opcode == 2, s"Expected ArithmeticData opcode (2), got $opcode")

    dut.clock.step()
    dut.io.semaphoreIF.a.ready.poke(false.B)

    dut.io.semaphoreIF.d.valid.poke(true.B)
    dut.io.semaphoreIF.d.bits.opcode.poke(1.U)  // AccessAckData
    dut.io.semaphoreIF.d.bits.param.poke(0.U)
    dut.io.semaphoreIF.d.bits.size.poke(1.U)
    dut.io.semaphoreIF.d.bits.source.poke(0.U)
    dut.io.semaphoreIF.d.bits.sink.poke(0.U)
    dut.io.semaphoreIF.d.bits.denied.poke(0.U)
    dut.io.semaphoreIF.d.bits.data.poke(returnValue.U)
    dut.io.semaphoreIF.d.bits.corrupt.poke(0.U)

    waitFor(dut, dut.io.semaphoreIF.d.ready.peek().litToBoolean, "semaphoreIF.d.ready")
    dut.clock.step()
    dut.io.semaphoreIF.d.valid.poke(false.B)
  }

  // ─────────────────────────────────────────────────────────────────────────
  "Store should store" in {
    implicit val c = Configuration.default()

    simulate(new Store()) { dut =>
      val nBeats = 8
      val srcAddr = 16

      // Defaults
      dut.io.readPort.a.ready.poke(false.B)
      dut.io.readPort.d.valid.poke(false.B)
      dut.io.readPort.d.bits.opcode.poke(0.U)
      dut.io.readPort.d.bits.param.poke(0.U)
      dut.io.readPort.d.bits.size.poke(0.U)
      dut.io.readPort.d.bits.source.poke(0.U)
      dut.io.readPort.d.bits.sink.poke(0.U)
      dut.io.readPort.d.bits.denied.poke(0.U)
      dut.io.readPort.d.bits.data.poke(0.U)
      dut.io.readPort.d.bits.corrupt.poke(0.U)
      dut.io.semaphoreIF.a.ready.poke(false.B)
      dut.io.semaphoreIF.d.valid.poke(false.B)
      dut.io.AXIST.tready.poke(false.B)

      dut.clock.step(2)

      // 1. Enqueue instruction (no semaphore)
      dut.io.instructionStream.valid.poke(true.B)
      dut.io.instructionStream.bits.op.poke(0.U)
      dut.io.instructionStream.bits.mode.poke(0.U)
      dut.io.instructionStream.bits.size.poke(nBeats.U)
      dut.io.instructionStream.bits.addrs(0).addr.poke(srcAddr.U)
      dut.io.instructionStream.bits.addrs(0).sem.valid.poke(false.B)
      dut.io.instructionStream.bits.addrs(0).sem.bits.addr.poke(0.U)
      dut.io.instructionStream.bits.addrs(0).sem.bits.stepSize.valid.poke(false.B)
      dut.io.instructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(0.U)

      waitFor(dut, dut.io.instructionStream.ready.peek().litToBoolean, "instructionStream.ready")
      dut.clock.step()
      dut.io.instructionStream.valid.poke(false.B)

      // 2. TLDMA should issue a Get on the readPort A channel
      dut.io.readPort.a.ready.poke(true.B)
      waitFor(dut, dut.io.readPort.a.valid.peek().litToBoolean, "readPort.a.valid")

      dut.io.readPort.a.bits.opcode.expect(4.U)  // Get
      dut.io.readPort.a.bits.address.expect(srcAddr.U)
      dut.io.readPort.a.bits.size.expect(nBeats.U)

      dut.clock.step()
      dut.io.readPort.a.ready.poke(false.B)

      // 3. Send D-channel AccessAckData beats, verify AXIST output
      dut.io.AXIST.tready.poke(true.B)

      for (beat <- 0 until nBeats) {
        dut.io.readPort.d.valid.poke(true.B)
        dut.io.readPort.d.bits.opcode.poke(1.U)  // AccessAckData
        dut.io.readPort.d.bits.param.poke(0.U)
        dut.io.readPort.d.bits.size.poke(nBeats.U)
        dut.io.readPort.d.bits.source.poke(0.U)
        dut.io.readPort.d.bits.sink.poke(0.U)
        dut.io.readPort.d.bits.denied.poke(0.U)
        dut.io.readPort.d.bits.data.poke(matrix(beat).U)
        dut.io.readPort.d.bits.corrupt.poke(0.U)

        waitFor(dut, dut.io.AXIST.tvalid.peek().litToBoolean, s"AXIST.tvalid (beat $beat)")

        // Verify AXIST output matches the D channel data
        dut.io.AXIST.tdata.expect(matrix(beat).U)

        dut.clock.step()
      }

      dut.io.readPort.d.valid.poke(false.B)
      dut.io.AXIST.tready.poke(false.B)

      // 4. Wait for StoreController to return to idle
      waitFor(dut, dut.io.debug.state.peek().litValue == 0, "StoreController back to idle")
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  "Store should store with semaphore" in {
    implicit val c = Configuration.default()

    simulate(new Store()) { dut =>
      val nBeats   = 4
      val srcAddr  = 32
      val semAddr  = 2
      val stepSize = 4

      // Defaults
      dut.io.readPort.a.ready.poke(false.B)
      dut.io.readPort.d.valid.poke(false.B)
      dut.io.readPort.d.bits.opcode.poke(0.U)
      dut.io.readPort.d.bits.param.poke(0.U)
      dut.io.readPort.d.bits.size.poke(0.U)
      dut.io.readPort.d.bits.source.poke(0.U)
      dut.io.readPort.d.bits.sink.poke(0.U)
      dut.io.readPort.d.bits.denied.poke(0.U)
      dut.io.readPort.d.bits.data.poke(0.U)
      dut.io.readPort.d.bits.corrupt.poke(0.U)
      dut.io.semaphoreIF.a.ready.poke(false.B)
      dut.io.semaphoreIF.d.valid.poke(false.B)
      dut.io.AXIST.tready.poke(false.B)

      dut.clock.step(2)

      // 1. Enqueue instruction with semaphore enabled
      dut.io.instructionStream.valid.poke(true.B)
      dut.io.instructionStream.bits.op.poke(0.U)
      dut.io.instructionStream.bits.mode.poke(0.U)
      dut.io.instructionStream.bits.size.poke(nBeats.U)
      dut.io.instructionStream.bits.addrs(0).addr.poke(srcAddr.U)
      dut.io.instructionStream.bits.addrs(0).sem.valid.poke(true.B)
      dut.io.instructionStream.bits.addrs(0).sem.bits.addr.poke(semAddr.U)
      dut.io.instructionStream.bits.addrs(0).sem.bits.stepSize.valid.poke(true.B)
      dut.io.instructionStream.bits.addrs(0).sem.bits.stepSize.bits.poke(stepSize.U)

      waitFor(dut, dut.io.instructionStream.ready.peek().litToBoolean, "instructionStream.ready")
      dut.clock.step()
      dut.io.instructionStream.valid.poke(false.B)

      // 2. Respond to semaphore AQGREQ (acquire)
      respondSemaphore(dut, returnValue = stepSize)

      // 3. TLDMA issues Get on A channel
      dut.io.readPort.a.ready.poke(true.B)
      waitFor(dut, dut.io.readPort.a.valid.peek().litToBoolean, "readPort.a.valid")

      dut.io.readPort.a.bits.opcode.expect(4.U)  // Get
      dut.io.readPort.a.bits.address.expect(srcAddr.U)

      dut.clock.step()
      dut.io.readPort.a.ready.poke(false.B)

      // 4. Send D-channel data beats, verify AXIST
      dut.io.AXIST.tready.poke(true.B)

      for (beat <- 0 until nBeats) {
        dut.io.readPort.d.valid.poke(true.B)
        dut.io.readPort.d.bits.opcode.poke(1.U)  // AccessAckData
        dut.io.readPort.d.bits.param.poke(0.U)
        dut.io.readPort.d.bits.size.poke(nBeats.U)
        dut.io.readPort.d.bits.source.poke(0.U)
        dut.io.readPort.d.bits.sink.poke(0.U)
        dut.io.readPort.d.bits.denied.poke(0.U)
        dut.io.readPort.d.bits.data.poke(matrix(beat).U)
        dut.io.readPort.d.bits.corrupt.poke(0.U)

        waitFor(dut, dut.io.AXIST.tvalid.peek().litToBoolean, s"AXIST.tvalid (beat $beat)")
        dut.io.AXIST.tdata.expect(matrix(beat).U)

        dut.clock.step()
      }

      dut.io.readPort.d.valid.poke(false.B)
      dut.io.AXIST.tready.poke(false.B)

      // 5. Respond to semaphore ADDU (release)
      respondSemaphore(dut, returnValue = 0)

      // 6. Wait for StoreController to return to idle
      waitFor(dut, dut.io.debug.state.peek().litValue == 0, "StoreController back to idle")
    }
  }
}
