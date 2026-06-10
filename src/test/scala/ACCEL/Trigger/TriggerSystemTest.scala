package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class TriggerSystemTest extends AnyFreeSpec with Matchers with ChiselSim {

  def cfg: Configuration =
    Configuration.default()
      .withSemaphore(_.copy(nSemaphores = 8, generationWidth = 3))
      .withTrigger(_.copy(rows = 4, maxGuards = 4, maxAcquires = 2, opMemDepth = 16))

  val timeout = 200

  def fusedAddr(semIdx: Int, gen: Int)(implicit c: Configuration): Int =
    (semIdx << c.semaphoreGenerationWidth) | gen

  def loadInst(
    dut: TriggerSystem,
    semAddr: Int, gen: Int, full: Int, empty: Int,
    guards: Seq[Int],
  )(implicit c: Configuration): Unit = {
    require(guards.length <= eaac.shared.InstructionSet.MaxSemDeps)
    dut.io.load.valid.poke(true.B)
    dut.io.load.bits.length.poke(0.U)
    dut.io.load.bits.opcode.poke(5.U)
    dut.io.load.bits.payload.semAddr.poke(semAddr.U)
    dut.io.load.bits.payload.generation.poke(gen.U)
    dut.io.load.bits.payload.initValues(0).poke(full.U)
    dut.io.load.bits.payload.initValues(1).poke(empty.U)
    dut.io.load.bits.row.depCount.poke(guards.length.U)
    for (i <- 0 until eaac.shared.InstructionSet.MaxSemDeps) {
      val g = if (i < guards.length) guards(i) else 0
      dut.io.load.bits.row.depAddrs(i).poke(g.U)
    }
    var cycles = 0
    while (!dut.io.load.ready.peek().litToBoolean) {
      dut.clock.step(); cycles += 1
      require(cycles < timeout, "Timeout on load")
    }
    dut.clock.step()
    dut.io.load.valid.poke(false.B)
  }


  def driveComplete(dut: TriggerSystem, addrs: Seq[Int])(implicit c: Configuration): Unit = {
    require(addrs.length <= c.triggerMaxAcquires)
    addrs.foreach { addr =>
      dut.io.inputEvent.valid.poke(true.B)
      dut.io.inputEvent.bits.addr.poke(addr.U)
      dut.io.inputEvent.bits.eventCode.poke(SemaphoreEventCodes.Complete)
      dut.clock.step()
    }
    dut.io.inputEvent.valid.poke(false.B)
  }

  def consumeFire(dut: TriggerSystem): (BigInt, BigInt) = {
    var cycles = 0
    while (!dut.io.fire.valid.peek().litToBoolean) {
      dut.clock.step(); cycles += 1
      require(cycles < timeout, s"Timeout waiting for fire (got $cycles cycles)")
    }

    val rowIdx  = dut.io.fire.bits.rowIdx.peek().litValue
    val semAddr = dut.io.fire.bits.payload.semAddr.peek().litValue
    dut.io.fire.ready.poke(true.B)
    dut.clock.step()
    dut.io.fire.ready.poke(false.B)
    (rowIdx, semAddr)
  }


  "Row with empty guard fires immediately" in {
    implicit val c = cfg
    simulate(new TriggerSystem) { dut =>
      loadInst(dut, semAddr = 7, gen = 1, full = 0, empty = 4, guards = Seq.empty)

      val (rowIdx, semAddr) = consumeFire(dut)
      assert(rowIdx == 0, s"Expected rowIdx=0, got $rowIdx")
      assert(semAddr == 7, s"Expected payload semAddr=7, got $semAddr")
    }
  }

  "Row with one guard is held until the guard's done bit is set" in {
    implicit val c = cfg
    simulate(new TriggerSystem) { dut =>
      val guardA = fusedAddr(semIdx = 3, gen = 2)
      loadInst(dut, semAddr = 3, gen = 2, full = 0, empty = 4, guards = Seq(guardA))

      for (_ <- 0 until 10) {
        dut.io.fire.valid.expect(false.B)
        dut.clock.step()
      }

      driveComplete(dut, Seq(guardA))

      val (rowIdx, semAddr) = consumeFire(dut)
      assert(rowIdx == 0)
      assert(semAddr == 3)
    }
  }

  "Row with multiple guards requires every guard done" in {
    implicit val c = cfg
    simulate(new TriggerSystem) { dut =>
      val g0 = fusedAddr(semIdx = 1, gen = 0)
      val g1 = fusedAddr(semIdx = 2, gen = 1)
      loadInst(dut, semAddr = 4, gen = 3, full = 0, empty = 2, guards = Seq(g0, g1))

      driveComplete(dut, Seq(g0))

      for (_ <- 0 until 5) {
        dut.io.fire.valid.expect(false.B)
        dut.clock.step()
      }

      driveComplete(dut, Seq(g1))
      val (_, semAddr) = consumeFire(dut)
      assert(semAddr == 4)
    }
  }

  "Chained rows: row B fires only after row A's completion marks the shared addr done" in {
    implicit val c = cfg
    simulate(new TriggerSystem) { dut =>
      val sharedAddr = fusedAddr(semIdx = 5, gen = 4)

      loadInst(dut, semAddr = 5, gen = 4, full = 0, empty = 8, guards = Seq.empty)
      loadInst(dut, semAddr = 6, gen = 0, full = 0, empty = 1, guards = Seq(sharedAddr))

      val (rowA, _) = consumeFire(dut)
      assert(rowA == 0)

      for (_ <- 0 until 5) {
        dut.io.fire.valid.expect(false.B)
        dut.clock.step()
      }

      driveComplete(dut, Seq(sharedAddr))

      val (rowB, payloadAddr) = consumeFire(dut)
      assert(rowB == 1)
      assert(payloadAddr == 6)
    }
  }

  "Lower-index row wins arbitration when two rows are ready in the same cycle" in {
    implicit val c = cfg
    simulate(new TriggerSystem) { dut =>
      loadInst(dut, semAddr = 1, gen = 0, full = 0, empty = 1, guards = Seq.empty)
      loadInst(dut, semAddr = 2, gen = 0, full = 0, empty = 1, guards = Seq.empty)

      val (first, firstAddr)   = consumeFire(dut)
      val (second, secondAddr) = consumeFire(dut)

      assert(first == 0,  s"Expected row 0 first, got $first")
      assert(second == 1, s"Expected row 1 second, got $second")
      assert(firstAddr  == 1)
      assert(secondAddr == 2)
    }
  }

  "Full table back-pressures load until a row retires" in {
    implicit val c = cfg
    simulate(new TriggerSystem) { dut =>
      val guards = (0 until c.triggerRows).map(i => fusedAddr(semIdx = i, gen = 0))
      for (i <- 0 until c.triggerRows) {
        loadInst(dut, semAddr = i, gen = 0, full = 0, empty = 1, guards = Seq(guards(i)))
      }

      dut.io.load.ready.expect(false.B)

      driveComplete(dut, Seq(guards(0)))
      consumeFire(dut)

      dut.io.load.ready.expect(true.B)
    }
  }

  "Retired slot accepts a new row that subsequently fires" in {
    implicit val c = cfg
    simulate(new TriggerSystem) { dut =>
      loadInst(dut, semAddr = 1, gen = 0, full = 0, empty = 1, guards = Seq.empty)
      consumeFire(dut)

      loadInst(dut, semAddr = 9, gen = 0, full = 0, empty = 1, guards = Seq.empty)
      val (_, semAddr) = consumeFire(dut)
      assert(semAddr == 9)
    }
  }

  "Loading a new row targeting fused addr X clears any prior state[X].done" in {
    implicit val c = cfg
    simulate(new TriggerSystem) { dut =>
      val targetSem = 5
      val targetGen = 4
      val sharedAddr = fusedAddr(semIdx = targetSem, gen = targetGen)

      driveComplete(dut, Seq(sharedAddr))

      loadInst(dut, semAddr = 9, gen = 0, full = 0, empty = 1, guards = Seq(sharedAddr))
      val (_, semA) = consumeFire(dut)
      assert(semA == 9, s"Pre-load sanity: row guarded on X should fire; got semA=$semA")

      loadInst(
        dut,
        semAddr = targetSem, gen = targetGen,
        full = 0, empty = 1,
        guards = Seq.empty,
      )
      consumeFire(dut)

      loadInst(dut, semAddr = 8, gen = 0, full = 0, empty = 1, guards = Seq(sharedAddr))
      for (_ <- 0 until 10) {
        dut.io.fire.valid.expect(false.B)
        dut.clock.step()
      }

      driveComplete(dut, Seq(sharedAddr))
      val (_, semB) = consumeFire(dut)
      assert(semB == 8, s"Post-reset: row should fire only after new Complete; got semB=$semB")
    }
  }
}
