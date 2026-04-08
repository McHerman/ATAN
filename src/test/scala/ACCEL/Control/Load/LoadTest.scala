package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers


class LoadTest extends AnyFreeSpec with Matchers with ChiselSim {

  val maxCycles = 500

  val matrix: Seq[BigInt] = Seq.fill(8)(BigInt("0102030401020304", 16))

  /** Block until `cond` is true, stepping the clock each cycle. */
  def waitFor(dut: Load, cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout waiting for: $msg (after $maxCycles cycles)")
      dut.clock.step()
      cycles += 1
    }
  }

  /** Send a D-channel AccessAck on the scratchOut port. */
  def sendAccessAck(dut: Load): Unit = {
    dut.io.scratchOut.d.valid.poke(true.B)
    dut.io.scratchOut.d.bits.opcode.poke(0.U)  // AccessAck
    dut.io.scratchOut.d.bits.param.poke(0.U)
    dut.io.scratchOut.d.bits.size.poke(0.U)
    dut.io.scratchOut.d.bits.source.poke(0.U)
    dut.io.scratchOut.d.bits.sink.poke(0.U)
    dut.io.scratchOut.d.bits.denied.poke(0.U)
    dut.io.scratchOut.d.bits.data.poke(0.U)
    dut.io.scratchOut.d.bits.corrupt.poke(0.U)

    waitFor(dut, dut.io.scratchOut.d.ready.peek().litToBoolean, "scratchOut.d.ready")
    dut.clock.step()
    dut.io.scratchOut.d.valid.poke(false.B)
  }

  /** Respond to a semaphore A-channel request with an AccessAckData on D.
   *  Load.io.semaphoreIF is a master TilelinkPort: a=Output (DUT drives), d=Input (test drives).
   */
  def respondSemaphore(dut: Load, returnValue: BigInt = 0): Unit = {
    // Accept the A-channel request from the DUT
    dut.io.semaphoreIF.a.ready.poke(true.B)
    waitFor(dut, dut.io.semaphoreIF.a.valid.peek().litToBoolean, "semaphoreIF.a.valid")

    // Verify it's an ArithmeticData opcode
    val opcode = dut.io.semaphoreIF.a.bits.opcode.peek().litValue
    assert(opcode == 2, s"Expected ArithmeticData opcode (2), got $opcode")

    dut.clock.step() // consume A beat
    dut.io.semaphoreIF.a.ready.poke(false.B)

    // Send D response (AccessAckData)
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
  "Load should load" in {
    implicit val c = Configuration.default()

    simulate(new Load()) { dut =>
      val nBeats = 8
      val destAddr = 16

      // Defaults
      dut.io.scratchOut.a.ready.poke(false.B)
      dut.io.scratchOut.d.valid.poke(false.B)
      dut.io.semaphoreIF.a.ready.poke(false.B)
      dut.io.semaphoreIF.d.valid.poke(false.B)
      dut.io.AXIST.tvalid.poke(false.B)

      dut.clock.step(2)

      // 1. Enqueue instruction (no semaphore)
      dut.io.instructionStream.valid.poke(true.B)
      dut.io.instructionStream.bits.func.poke(0.U)
      dut.io.instructionStream.bits.mode.poke(0.U)
      dut.io.instructionStream.bits.size.poke(nBeats.U)
      dut.io.instructionStream.bits.addrd(0).addr.poke(destAddr.U)
      dut.io.instructionStream.bits.addrd(0).sem.valid.poke(false.B)
      dut.io.instructionStream.bits.addrd(0).sem.bits.addr.poke(0.U)
      dut.io.instructionStream.bits.addrd(0).sem.bits.stepSize.valid.poke(false.B)
      dut.io.instructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(0.U)

      waitFor(dut, dut.io.instructionStream.ready.peek().litToBoolean, "instructionStream.ready")
      dut.clock.step()
      dut.io.instructionStream.valid.poke(false.B)

      // 2. Wait for TLDMA to start requesting data (scratchOut.a.valid)
      //    We need AXIST data and scratchOut.a.ready for the handshake
      dut.io.scratchOut.a.ready.poke(true.B)
      dut.io.AXIST.tvalid.poke(true.B)
      dut.io.AXIST.tstrb.poke(0xFF.U)

      // 3. Stream all beats through AXIST → TLDMA → scratchOut
      for (beat <- 0 until nBeats) {
        dut.io.AXIST.tdata.poke(matrix(beat).U)
        if (beat == nBeats - 1) dut.io.AXIST.tlast.poke(true.B)

        waitFor(dut, dut.io.scratchOut.a.valid.peek().litToBoolean, s"scratchOut.a.valid (beat $beat)")

        // Verify TileLink A channel
        dut.io.scratchOut.a.bits.opcode.expect(0.U)  // PutFullData
        dut.io.scratchOut.a.bits.address.expect(destAddr.U)
        dut.io.scratchOut.a.bits.size.expect(nBeats.U)
        dut.io.scratchOut.a.bits.data.expect(matrix(beat).U)

        dut.clock.step()
      }

      dut.io.AXIST.tvalid.poke(false.B)
      dut.io.AXIST.tlast.poke(false.B)
      dut.io.scratchOut.a.ready.poke(false.B)

      // 4. Send D-channel AccessAck
      sendAccessAck(dut)

      // 5. Wait for LoadController to return to idle (state 0)
      waitFor(dut, dut.io.debug.state.peek().litValue == 0, "LoadController back to idle")
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  "Load should load with semaphore" in {
    implicit val c = Configuration.default()

    simulate(new Load()) { dut =>
      val nBeats   = 4
      val destAddr = 32
      val semAddr  = 2
      val stepSize = 4

      // Defaults
      dut.io.scratchOut.a.ready.poke(false.B)
      dut.io.scratchOut.d.valid.poke(false.B)
      dut.io.semaphoreIF.a.ready.poke(false.B)
      dut.io.semaphoreIF.d.valid.poke(false.B)
      dut.io.AXIST.tvalid.poke(false.B)

      dut.clock.step(2)

      // 1. Enqueue instruction with semaphore enabled
      dut.io.instructionStream.valid.poke(true.B)
      dut.io.instructionStream.bits.func.poke(0.U)
      dut.io.instructionStream.bits.mode.poke(0.U)
      dut.io.instructionStream.bits.size.poke(nBeats.U)
      dut.io.instructionStream.bits.addrd(0).addr.poke(destAddr.U)
      dut.io.instructionStream.bits.addrd(0).sem.valid.poke(true.B)
      dut.io.instructionStream.bits.addrd(0).sem.bits.addr.poke(semAddr.U)
      dut.io.instructionStream.bits.addrd(0).sem.bits.stepSize.valid.poke(true.B)
      dut.io.instructionStream.bits.addrd(0).sem.bits.stepSize.bits.poke(stepSize.U)

      waitFor(dut, dut.io.instructionStream.ready.peek().litToBoolean, "instructionStream.ready")
      dut.clock.step()
      dut.io.instructionStream.valid.poke(false.B)

      // 2. Respond to semaphore AQGREQ (acquire) then SUBU (decrement)
      //    Load is write-only → producer: acquire on emptyReg (base+1)
      respondSemaphore(dut, returnValue = stepSize)  // AQGREQ
      respondSemaphore(dut, returnValue = 0)          // SUBU

      // 3. Stream data beats through AXIST → TLDMA → scratchOut
      dut.io.scratchOut.a.ready.poke(true.B)
      dut.io.AXIST.tvalid.poke(true.B)
      dut.io.AXIST.tstrb.poke(0xFF.U)

      for (beat <- 0 until nBeats) {
        dut.io.AXIST.tdata.poke(matrix(beat).U)
        if (beat == nBeats - 1) dut.io.AXIST.tlast.poke(true.B)

        waitFor(dut, dut.io.scratchOut.a.valid.peek().litToBoolean, s"scratchOut.a.valid (beat $beat)")

        dut.io.scratchOut.a.bits.opcode.expect(0.U)  // PutFullData
        dut.io.scratchOut.a.bits.address.expect(destAddr.U)

        dut.clock.step()
      }

      dut.io.AXIST.tvalid.poke(false.B)
      dut.io.AXIST.tlast.poke(false.B)
      dut.io.scratchOut.a.ready.poke(false.B)

      // 4. Send D-channel AccessAck
      sendAccessAck(dut)

      // 5. Respond to semaphore ADDU (release)
      respondSemaphore(dut, returnValue = 0)

      // 6. TLDMA should now go to writeRespond → idle since remaining = size - stepSize = 0
      //    Wait for LoadController to return to idle
      waitFor(dut, dut.io.debug.state.peek().litValue == 0, "LoadController back to idle")
    }
  }
}
