// Drop into ATAN at: src/test/scala/ACCEL/MLIR/MLIRMemrefUnitTestHelpers.scala
//
// Shared test helpers used by every MLIRMemrefUnit-based test. Mix this
// trait into the test class alongside ChiselSim.
package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim

/** Test-side description of one addrPkg field within an instruction. */
final case class AddrSpec(addr: Long, semBase: Option[Long] = None)

trait MLIRMemrefUnitTestHelpers { this: ChiselSim =>
  def maxCycles: Int

  protected def waitFor(dut: MLIRMemrefUnit)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles,
        s"Timeout waiting for: $msg (after $maxCycles cycles)")
      dut.clock.step()
      cycles += 1
    }
  }

  protected def pokeTLIdle(p: TilelinkPort): Unit = {
    p.a.ready.poke(false.B)
    p.d.valid.poke(false.B)
    p.d.bits.opcode.poke(0.U)
    p.d.bits.param.poke(0.U)
    p.d.bits.size.poke(0.U)
    p.d.bits.source.poke(0.U)
    p.d.bits.sink.poke(0.U)
    p.d.bits.denied.poke(0.U)
    p.d.bits.data.poke(0.U)
    p.d.bits.corrupt.poke(0.U)
  }

  protected def setupIdle(dut: MLIRMemrefUnit): Unit = {
    dut.io.scratchIn.foreach(pokeTLIdle)
    dut.io.scratchOut.foreach(pokeTLIdle)
    dut.io.readSemaphoreIF.foreach(pokeTLIdle)
    dut.io.writeSemaphoreIF.foreach(pokeTLIdle)

    dut.io.in.valid.poke(false.B)
    dut.io.in.bits.length.poke(0.U)
    dut.io.in.bits.opcode.poke(0.U)
    dut.io.in.bits.func.poke(0.U)
    dut.io.in.bits.size.poke(0.U)
    for (i <- 0 until dut.io.in.bits.addrs.length) {
      val ap = dut.io.in.bits.addrs(i)
      ap.addr.poke(0.U)
      ap.sem.valid.poke(false.B)
      ap.sem.bits.addr.poke(0.U)
      ap.sem.bits.stepSize.valid.poke(false.B)
      ap.sem.bits.stepSize.bits.poke(0.U)
    }
    for (j <- 0 until dut.io.in.bits.addrd.length) {
      val ap = dut.io.in.bits.addrd(j)
      ap.addr.poke(0.U)
      ap.sem.valid.poke(false.B)
      ap.sem.bits.addr.poke(0.U)
      ap.sem.bits.stepSize.valid.poke(false.B)
      ap.sem.bits.stepSize.bits.poke(0.U)
    }
  }

  /** Drive one instruction (optionally with semaphore fields), then step the
    * handshake. Sequence lengths must match the unit's instantiation. */
  protected def sendInstr(
      dut: MLIRMemrefUnit,
      srcs: Seq[AddrSpec],
      dsts: Seq[AddrSpec],
  ): Unit = {
    require(srcs.length == dut.io.in.bits.addrs.length,
      s"srcs length ${srcs.length} != unit addrs length ${dut.io.in.bits.addrs.length}")
    require(dsts.length == dut.io.in.bits.addrd.length,
      s"dsts length ${dsts.length} != unit addrd length ${dut.io.in.bits.addrd.length}")

    for ((spec, i) <- srcs.zipWithIndex) {
      val ap = dut.io.in.bits.addrs(i)
      ap.addr.poke(spec.addr.U)
      ap.sem.valid.poke(spec.semBase.isDefined.B)
      spec.semBase.foreach(a => ap.sem.bits.addr.poke(a.U))
    }
    for ((spec, j) <- dsts.zipWithIndex) {
      val ap = dut.io.in.bits.addrd(j)
      ap.addr.poke(spec.addr.U)
      ap.sem.valid.poke(spec.semBase.isDefined.B)
      spec.semBase.foreach(a => ap.sem.bits.addr.poke(a.U))
    }

    dut.io.in.valid.poke(true.B)
    waitFor(dut)(dut.io.in.ready.peek().litToBoolean, "wrapper ready for instruction")
    dut.clock.step()
    dut.io.in.valid.poke(false.B)
  }

  /** Bus slave for a Get: accept A, stream N data beats on D. */
  protected def respondTLGet(
      dut: MLIRMemrefUnit, p: TilelinkPort,
      expectedAddr: Long, data: Seq[Long], label: String,
  ): Unit = {
    waitFor(dut)(p.a.valid.peek().litToBoolean, s"$label A.valid (Get)")
    p.a.bits.opcode.expect(TilelinkOpcodes.Get)
    p.a.bits.address.expect(expectedAddr.U)
    p.a.bits.size.expect(data.length.U)
    p.a.ready.poke(true.B)
    dut.clock.step()
    p.a.ready.poke(false.B)

    for ((d, i) <- data.zipWithIndex) {
      p.d.valid.poke(true.B)
      p.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
      p.d.bits.size.poke(data.length.U)
      p.d.bits.data.poke(d.U(32.W))
      waitFor(dut)(p.d.ready.peek().litToBoolean, s"$label D.ready beat $i")
      dut.clock.step()
    }
    p.d.valid.poke(false.B)
    p.d.bits.data.poke(0.U)
  }

  /** Bus slave for a PutFull burst: accept N A beats, then one AccessAck. */
  protected def respondTLPut(
      dut: MLIRMemrefUnit, p: TilelinkPort,
      expectedAddr: Long, expectedData: Seq[Long], label: String,
  ): Unit = {
    for ((d, i) <- expectedData.zipWithIndex) {
      waitFor(dut)(p.a.valid.peek().litToBoolean, s"$label A.valid (Put beat $i)")
      p.a.bits.opcode.expect(TilelinkOpcodes.PutFullData)
      p.a.bits.address.expect(expectedAddr.U)
      p.a.bits.data.expect(d.U(32.W))
      p.a.ready.poke(true.B)
      dut.clock.step()
      p.a.ready.poke(false.B)
    }
    p.d.valid.poke(true.B)
    p.d.bits.opcode.poke(TilelinkOpcodes.AccessAck)
    p.d.bits.size.poke(expectedData.length.U)
    waitFor(dut)(p.d.ready.peek().litToBoolean, s"$label D.ready (Put ack)")
    dut.clock.step()
    p.d.valid.poke(false.B)
  }

  /** Bus slave for one semaphore transaction. Accepts A with the expected
    * ArithmeticData param + address, returns any D beat. */
  protected def respondSem(
      dut: MLIRMemrefUnit, p: TilelinkPort,
      expectedAddr: Long, expectedParam: UInt, label: String,
  ): Unit = {
    waitFor(dut)(p.a.valid.peek().litToBoolean, s"$label A.valid")
    p.a.bits.opcode.expect(TilelinkOpcodes.ArithmeticData)
    p.a.bits.param.expect(expectedParam)
    p.a.bits.address.expect(expectedAddr.U)
    p.a.ready.poke(true.B)
    dut.clock.step()
    p.a.ready.poke(false.B)

    p.d.valid.poke(true.B)
    p.d.bits.opcode.poke(TilelinkOpcodes.AccessAckData)
    p.d.bits.size.poke(0.U)
    p.d.bits.data.poke(0.U)
    waitFor(dut)(p.d.ready.peek().litToBoolean, s"$label D.ready")
    dut.clock.step()
    p.d.valid.poke(false.B)
  }
}
