package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class SemSystemTest extends AnyFreeSpec with Matchers with ChiselSim {

  implicit val c: Configuration = Configuration.default()
    .withBus(_.copy(sourceWidth = 8))
    .withSemaphore(_.copy(queueSize = 4))

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
    dut.io.instructionStream.bits.payload.semAddr.poke(0.U)
    dut.io.instructionStream.bits.payload.initFull.poke(0.U)
    dut.io.instructionStream.bits.payload.initEmpty.poke(0.U)
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
      dut.io.instructionStream.bits.payload.semAddr.poke(0.U)
      dut.io.instructionStream.bits.payload.initFull.poke(20.U)
      dut.io.instructionStream.bits.payload.initEmpty.poke(5.U)

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
        dut.io.instructionStream.bits.payload.semAddr.poke(semIdx.U)
        dut.io.instructionStream.bits.payload.initFull.poke(fullVal.U)
        dut.io.instructionStream.bits.payload.initEmpty.poke(emptyVal.U)

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

  "Two virtual sems on the same physical sem are serialized by chain dep" in {
    implicit val cChain: Configuration = Configuration.default()
      .withBus(_.copy(sourceWidth = 8))
      .withSemaphore(_.copy(generationWidth = 3, queueSize = 4))

    val gw            = cChain.semaphoreGenerationWidth
    val perSlave      = 2 << gw
    val semIdxTarget  = 2
    val gen0          = 0
    val gen1          = 1
    def tlAddr(semIdx: Int, portIdx: Int, reg: Int, gen: Int): Int =
      (semIdx * 2 + portIdx) * perSlave + (reg << gw) + gen
    def fusedAddr(semIdx: Int, gen: Int): Int = (semIdx << gw) | gen
    val depAddr = fusedAddr(semIdxTarget, gen0)

    simulate(new SemSystem(noPorts)(cChain)) { dut =>
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
      dut.io.instructionStream.bits.payload.semAddr.poke(0.U)
      dut.io.instructionStream.bits.payload.initFull.poke(0.U)
      dut.io.instructionStream.bits.payload.initEmpty.poke(0.U)
      dut.io.instructionStream.bits.payload.generation.poke(0.U)
      dut.io.instructionStream.bits.row.depCount.poke(0.U)
      for (i <- 0 until eaac.shared.InstructionSet.MaxSemDeps) {
        dut.io.instructionStream.bits.row.depAddrs(i).poke(0.U)
      }

      def enqueueProg(semIdx: Int, gen: Int, full: Int, empty: Int, deps: Seq[Int]): Unit = {
        dut.io.instructionStream.valid.poke(true.B)
        dut.io.instructionStream.bits.payload.semAddr.poke(semIdx.U)
        dut.io.instructionStream.bits.payload.initFull.poke(full.U)
        dut.io.instructionStream.bits.payload.initEmpty.poke(empty.U)
        dut.io.instructionStream.bits.payload.generation.poke(gen.U)
        dut.io.instructionStream.bits.row.depCount.poke(deps.length.U)
        for (i <- 0 until eaac.shared.InstructionSet.MaxSemDeps) {
          val d = if (i < deps.length) deps(i) else 0
          dut.io.instructionStream.bits.row.depAddrs(i).poke(d.U)
        }
        var cycles = 0
        while (!dut.io.instructionStream.ready.peek().litToBoolean) {
          dut.clock.step(); cycles += 1
          require(cycles < 50, "enqueue timeout")
        }
        dut.clock.step()
        dut.io.instructionStream.valid.poke(false.B)
      }

      enqueueProg(semIdxTarget, gen0, full = 0, empty = 1, deps = Seq.empty)
      enqueueProg(semIdxTarget, gen1, full = 0, empty = 99, deps = Seq(depAddr))

      dut.clock.step(20)

      val empty0 = sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = tlAddr(semIdxTarget, 0, 1, gen0), data = 0))
      assert(empty0 == 1, s"Expected gen0 empty=1 from first program, got $empty0")

      val ops = Seq(
        TLReq(param = ArithmeticDataParam.AQGREQ, address = tlAddr(semIdxTarget, 0, 1, gen0), data = 1),
        TLReq(param = ArithmeticDataParam.SUBU,   address = tlAddr(semIdxTarget, 0, 1, gen0), data = 1),
        TLReq(param = ArithmeticDataParam.ADDU,   address = tlAddr(semIdxTarget, 0, 0, gen0), data = 1),
        TLReq(param = ArithmeticDataParam.AQGREQ, address = tlAddr(semIdxTarget, 1, 0, gen0), data = 1),
        TLReq(param = ArithmeticDataParam.SUBU,   address = tlAddr(semIdxTarget, 1, 0, gen0), data = 1),
        TLReq(param = ArithmeticDataParam.ADDU,   address = tlAddr(semIdxTarget, 1, 1, gen0), data = 1),
      )
      ops.foreach(op => sendAndReceive(dut, masterIdx = 0, op))

      dut.clock.step(20)

      val empty1 = sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = tlAddr(semIdxTarget, 0, 1, gen1), data = 0))
      assert(empty1 == 99, s"Expected gen1 empty=99 after chain unlocked second program, got $empty1")
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
        dut.io.instructionStream.bits.payload.semAddr.poke(semIdx.U)
        dut.io.instructionStream.bits.payload.initFull.poke(fullVal.U)
        dut.io.instructionStream.bits.payload.initEmpty.poke(emptyVal.U)

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
      dut.io.instructionStream.bits.payload.semAddr.poke(1.U)
      dut.io.instructionStream.bits.payload.initFull.poke(3.U)
      dut.io.instructionStream.bits.payload.initEmpty.poke(0.U)

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
