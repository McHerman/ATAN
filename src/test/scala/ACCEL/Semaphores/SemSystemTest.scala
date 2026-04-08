package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class SemSystemTest extends AnyFreeSpec with Matchers with ChiselSim {

  implicit val c: Configuration = Configuration.default().copy(sourceWidth = 8)

  val noPorts = 4

  def semAddr(semIdx: Int, portIdx: Int, reg: Int): Int = semIdx * 4 + portIdx * 2 + reg

  def defaultPokes(dut: SemSystem): Unit = {
    for (i <- 0 until noPorts) {
      dut.io.inPorts(i).a.valid.poke(false.B)
      dut.io.inPorts(i).a.bits.opcode.poke(0.U)
      dut.io.inPorts(i).a.bits.param.poke(0.U)
      dut.io.inPorts(i).a.bits.size.poke(0.U)
      dut.io.inPorts(i).a.bits.source.poke(0.U)
      dut.io.inPorts(i).a.bits.address.poke(0.U)
      dut.io.inPorts(i).a.bits.mask.poke(0.U)
      dut.io.inPorts(i).a.bits.data.poke(0.U)
      dut.io.inPorts(i).a.bits.corrupt.poke(0.U)
      dut.io.inPorts(i).d.ready.poke(false.B)
    }
    dut.io.instructionStream.valid.poke(false.B)
    dut.io.instructionStream.bits.opcode.poke(0.U)
    dut.io.instructionStream.bits.semAddr.poke(0.U)
    dut.io.instructionStream.bits.initValues(0).poke(0.U)
    dut.io.instructionStream.bits.initValues(1).poke(0.U)
  }

  def sendAndReceive(dut: SemSystem, masterIdx: Int, req: TLReq): BigInt = {
    val port = dut.io.inPorts(masterIdx)

    var cycles = 0
    while (!port.a.ready.peek().litToBoolean) {
      dut.clock.step(); cycles += 1
      require(cycles < 200, s"Timeout waiting for a.ready on master $masterIdx")
    }

    port.a.valid.poke(true.B)
    port.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
    port.a.bits.param.poke(req.param)
    port.a.bits.address.poke(req.address.U)
    port.a.bits.data.poke(req.data.U)
    port.a.bits.source.poke(req.source.U)
    port.a.bits.size.poke(0.U)
    port.a.bits.mask.poke(0.U)
    port.a.bits.corrupt.poke(0.U)
    dut.clock.step()
    port.a.valid.poke(false.B)

    port.d.ready.poke(true.B)
    cycles = 0
    while (!port.d.valid.peek().litToBoolean) {
      dut.clock.step(); cycles += 1
      require(cycles < 200, s"Timeout waiting for d.valid on master $masterIdx")
    }

    val result = port.d.bits.data.peek().litValue
    port.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
    dut.clock.step()
    port.d.ready.poke(false.B)
    result
  }

  "Single instruction through queue programs a semaphore" in {
    simulate(new SemSystem(noPorts)) { dut =>
      defaultPokes(dut)

      // Enqueue: program semaphore 0 with full=20, empty=5
      dut.io.instructionStream.valid.poke(true.B)
      dut.io.instructionStream.bits.semAddr.poke(0.U)
      dut.io.instructionStream.bits.initValues(0).poke(20.U)
      dut.io.instructionStream.bits.initValues(1).poke(5.U)

      var cycles = 0
      while (!dut.io.instructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < 50, "Timeout waiting for instructionStream.ready")
      }
      dut.clock.step()
      dut.io.instructionStream.valid.poke(false.B)

      // Let state machine drain the queue and write to the bank
      dut.clock.step(10)

      // Read back full register
      val full = sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(0, 0, 0), data = 0))
      assert(full == 20, s"Expected full=20, got $full")

      // Read back empty register
      val empty = sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(0, 0, 1), data = 0))
      assert(empty == 5, s"Expected empty=5, got $empty")
    }
  }

  "Multiple instructions queued back-to-back program different semaphores" in {
    simulate(new SemSystem(noPorts)) { dut =>
      defaultPokes(dut)

      val programs = Seq(
        (0, 10, 20),  // sem 0: full=10, empty=20
        (1, 30, 40),  // sem 1: full=30, empty=40
        (3, 50, 60),  // sem 3: full=50, empty=60
      )

      for ((semIdx, fullVal, emptyVal) <- programs) {
        dut.io.instructionStream.valid.poke(true.B)
        dut.io.instructionStream.bits.semAddr.poke(semIdx.U)
        dut.io.instructionStream.bits.initValues(0).poke(fullVal.U)
        dut.io.instructionStream.bits.initValues(1).poke(emptyVal.U)

        var cycles = 0
        while (!dut.io.instructionStream.ready.peek().litToBoolean) {
          dut.clock.step(); cycles += 1
          require(cycles < 50, s"Timeout enqueuing instruction for semaphore $semIdx")
        }
        dut.clock.step()
      }
      dut.io.instructionStream.valid.poke(false.B)

      dut.clock.step(30)

      for ((semIdx, fullVal, emptyVal) <- programs) {
        val full = sendAndReceive(dut, masterIdx = 0,
          TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(semIdx, 0, 0), data = 0))
        assert(full == fullVal, s"Semaphore $semIdx: expected full=$fullVal, got $full")

        val empty = sendAndReceive(dut, masterIdx = 0,
          TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(semIdx, 0, 1), data = 0))
        assert(empty == emptyVal, s"Semaphore $semIdx: expected empty=$emptyVal, got $empty")
      }
    }
  }

  "Reprogramming stalls until semaphore counters reach zero" in {
    simulate(new SemSystem(noPorts)) { dut =>
      defaultPokes(dut)

      dut.io.instructionStream.valid.poke(true.B)
      dut.io.instructionStream.bits.semAddr.poke(2.U)
      dut.io.instructionStream.bits.initValues(0).poke(3.U)
      dut.io.instructionStream.bits.initValues(1).poke(0.U)

      var cycles = 0
      while (!dut.io.instructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < 50, "Timeout on first enqueue")
      }
      dut.clock.step()

      dut.io.instructionStream.bits.semAddr.poke(2.U)
      dut.io.instructionStream.bits.initValues(0).poke(99.U)
      dut.io.instructionStream.bits.initValues(1).poke(77.U)

      cycles = 0
      while (!dut.io.instructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < 50, "Timeout on second enqueue")
      }
      dut.clock.step()
      dut.io.instructionStream.valid.poke(false.B)

      dut.clock.step(10)

      val full1 = sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(2, 0, 0), data = 0))
      assert(full1 == 3, s"Expected full=3 from first program, got $full1")

      // Drain full to 0: three decrements
      for (_ <- 0 until 3) {
        sendAndReceive(dut, masterIdx = 0,
          TLReq(param = ArithmeticDataParam.SUBU, address = semAddr(2, 0, 0), data = 1))
      }

      // Now both regs are 0/0 — the stalled reprogram should go through
      dut.clock.step(10)

      // Verify the second programming took effect
      val full2 = sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(2, 0, 0), data = 0))
      assert(full2 == 99, s"Expected full=99 after reprogram, got $full2")

      val empty2 = sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(2, 0, 1), data = 0))
      assert(empty2 == 77, s"Expected empty=77 after reprogram, got $empty2")
    }
  }

  "Queue accepts instructions while state machine is busy draining" in {
    simulate(new SemSystem(noPorts)) { dut =>
      defaultPokes(dut)

      val programs = Seq(
        (0, 1, 2),
        (1, 3, 4),
        (2, 5, 6),
        (3, 7, 8),
      )

      for ((semIdx, fullVal, emptyVal) <- programs) {
        dut.io.instructionStream.valid.poke(true.B)
        dut.io.instructionStream.bits.semAddr.poke(semIdx.U)
        dut.io.instructionStream.bits.initValues(0).poke(fullVal.U)
        dut.io.instructionStream.bits.initValues(1).poke(emptyVal.U)

        var cycles = 0
        while (!dut.io.instructionStream.ready.peek().litToBoolean) {
          dut.clock.step(); cycles += 1
          require(cycles < 100, s"Timeout enqueuing semaphore $semIdx")
        }
        dut.clock.step()
      }
      dut.io.instructionStream.valid.poke(false.B)

      dut.clock.step(40)

      for ((semIdx, fullVal, emptyVal) <- programs) {
        val full = sendAndReceive(dut, masterIdx = 0,
          TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(semIdx, 0, 0), data = 0))
        assert(full == fullVal, s"Sem $semIdx: expected full=$fullVal, got $full")

        val empty = sendAndReceive(dut, masterIdx = 0,
          TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(semIdx, 0, 1), data = 0))
        assert(empty == emptyVal, s"Sem $semIdx: expected empty=$emptyVal, got $empty")
      }
    }
  }

  "Semaphore is usable after programming through queue" in {
    simulate(new SemSystem(noPorts)) { dut =>
      defaultPokes(dut)

      // Program semaphore 1: full=3, empty=0
      dut.io.instructionStream.valid.poke(true.B)
      dut.io.instructionStream.bits.semAddr.poke(1.U)
      dut.io.instructionStream.bits.initValues(0).poke(3.U)
      dut.io.instructionStream.bits.initValues(1).poke(0.U)

      var cycles = 0
      while (!dut.io.instructionStream.ready.peek().litToBoolean) {
        dut.clock.step(); cycles += 1
        require(cycles < 50, "Timeout enqueuing")
      }
      dut.clock.step()
      dut.io.instructionStream.valid.poke(false.B)

      dut.clock.step(10)

      // Acquire: full >= 1, returns current value (3)
      val r0 = sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(1, 0, 0), data = 1))
      assert(r0 == 3, s"Expected full=3 on acquire, got $r0")

      // Decrement full by 1
      sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.SUBU, address = semAddr(1, 0, 0), data = 1))

      // Read full again: should be 2
      val r1 = sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(1, 0, 0), data = 0))
      assert(r1 == 2, s"Expected full=2 after decrement, got $r1")

      // Increment empty by 1
      sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.ADDU, address = semAddr(1, 0, 1), data = 1))

      // Read empty: should be 1
      val r2 = sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(1, 0, 1), data = 0))
      assert(r2 == 1, s"Expected empty=1 after increment, got $r2")
    }
  }
}
