package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

// Bundle of information for one TileLink ArithmeticData transaction
case class TLReq(param: UInt, address: Int, data: Int, source: Int = 0)

class SemaphoreBankTest extends AnyFreeSpec with Matchers with ChiselSim {

  // sourceWidth=8 needed: with 2 masters the xbar assigns source ID ranges up to 31
  def bankConfig: Configuration = Configuration.default().withBus(_.copy(sourceWidth = 8))

  // Address of semaphore i, port j, register reg (0=full, 1=empty)
  // semaphore i → xbar slaves 2*i (port 0) and 2*i+1 (port 1)
  // base address = i*4 + j*2; bit 0 selects register
  def semAddr(semIdx: Int, portIdx: Int, reg: Int): Int = semIdx * 4 + portIdx * 2 + reg

  def defaultPokes(dut: SemaphoreBank, noPorts: Int): Unit = {
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
    dut.io.progPort.valid.poke(false.B)
    dut.io.progPort.bits.addr.poke(0.U)
    dut.io.progPort.bits.initValues(0).poke(0.U)
    dut.io.progPort.bits.initValues(1).poke(0.U)
  }

  // progPort.addr == semIdx directly
  def programSemaphore(dut: SemaphoreBank, semIdx: Int, full: Int, empty: Int): Unit = {
    dut.io.progPort.valid.poke(true.B)
    dut.io.progPort.bits.addr.poke(semIdx.U)
    dut.io.progPort.bits.initValues(0).poke(full.U)
    dut.io.progPort.bits.initValues(1).poke(empty.U)
    dut.clock.step()
    dut.io.progPort.valid.poke(false.B)
  }

  def sendAndReceive(dut: SemaphoreBank, masterIdx: Int, req: TLReq): BigInt = {
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

  "Routing: request to semaphore 0 is correctly handled" in {
    implicit val c = bankConfig
    simulate(new SemaphoreBank(2)) { dut =>
      defaultPokes(dut, 2)
      programSemaphore(dut, semIdx = 0, full = 10, empty = 5)

      val result = sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(0, 0, 0), data = 0))
      assert(result == 10, s"Expected full=10 from semaphore 0, got $result")
    }
  }

  "Routing: request to semaphore 5 is correctly handled" in {
    implicit val c = bankConfig
    simulate(new SemaphoreBank(2)) { dut =>
      defaultPokes(dut, 2)
      programSemaphore(dut, semIdx = 5, full = 99, empty = 1)

      val result = sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(5, 0, 0), data = 0))
      assert(result == 99, s"Expected full=99 from semaphore 5, got $result")
    }
  }

  "Routing: both ports of the same semaphore alias the same physical registers" in {
    implicit val c = bankConfig
    simulate(new SemaphoreBank(2)) { dut =>
      defaultPokes(dut, 2)
      programSemaphore(dut, semIdx = 2, full = 0, empty = 0)

      // Write full reg via port 0
      sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.ADDU, address = semAddr(2, 0, 0), data = 7))

      // Read full reg via port 1 — should see the value written through port 0
      val result = sendAndReceive(dut, masterIdx = 1,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(2, 1, 0), data = 0))
      assert(result == 7, s"Expected aliased full=7 via port 1, got $result")
    }
  }

  "Routing: two masters access different semaphores independently" in {
    implicit val c = bankConfig
    simulate(new SemaphoreBank(2)) { dut =>
      defaultPokes(dut, 2)
      programSemaphore(dut, semIdx = 1, full = 11, empty = 0)
      programSemaphore(dut, semIdx = 4, full = 44, empty = 0)

      val r0 = sendAndReceive(dut, masterIdx = 0,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(1, 0, 0), data = 0))
      val r1 = sendAndReceive(dut, masterIdx = 1,
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(4, 0, 0), data = 0))

      assert(r0 == 11, s"Expected 11 from semaphore 1, got $r0")
      assert(r1 == 44, s"Expected 44 from semaphore 4, got $r1")
    }
  }

  "Producer/consumer through both ports of semaphore 0" in {
    implicit val c = bankConfig
    simulate(new SemaphoreBank(2)) { dut =>
      defaultPokes(dut, 2)

      val bufferSize = 4
      val transfers  = 8

      programSemaphore(dut, semIdx = 0, full = 0, empty = bufferSize)

      // Producer (master 0, port 0 of semaphore 0):
      //   AQGREQ(empty >= 1) → SUBU(empty, 1) → ADDU(full, 1)
      // Consumer (master 1, port 1 of semaphore 0):
      //   AQGREQ(full >= 1)  → SUBU(full, 1)  → ADDU(empty, 1)
      // Both ports alias the same registers — the semaphore handles arbitration internally.
      val prodOps = Seq(
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(0, 0, 1), data = 1),
        TLReq(param = ArithmeticDataParam.SUBU,   address = semAddr(0, 0, 1), data = 1),
        TLReq(param = ArithmeticDataParam.ADDU,   address = semAddr(0, 0, 0), data = 1)
      )
      val consOps = Seq(
        TLReq(param = ArithmeticDataParam.AQGREQ, address = semAddr(0, 1, 0), data = 1),
        TLReq(param = ArithmeticDataParam.SUBU,   address = semAddr(0, 1, 0), data = 1),
        TLReq(param = ArithmeticDataParam.ADDU,   address = semAddr(0, 1, 1), data = 1)
      )

      var prodUnits = 0; var consUnits = 0
      var prodPhase = 0; var consPhase = 0
      var prodSent  = false; var consSent = false

      var cycles = 0
      while (consUnits < transfers) {
        require(cycles < transfers * 200, s"Timeout after $cycles cycles")

        dut.io.inPorts(0).a.valid.poke(false.B)
        dut.io.inPorts(1).a.valid.poke(false.B)
        dut.io.inPorts(0).d.ready.poke(true.B)
        dut.io.inPorts(1).d.ready.poke(true.B)

        // Producer
        if (prodUnits < transfers) {
          val req = prodOps(prodPhase)
          if (!prodSent && dut.io.inPorts(0).a.ready.peek().litToBoolean) {
            dut.io.inPorts(0).a.valid.poke(true.B)
            dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
            dut.io.inPorts(0).a.bits.param.poke(req.param)
            dut.io.inPorts(0).a.bits.address.poke(req.address.U)
            dut.io.inPorts(0).a.bits.data.poke(req.data.U)
            dut.io.inPorts(0).a.bits.source.poke(0.U)
            dut.io.inPorts(0).a.bits.size.poke(0.U)
            dut.io.inPorts(0).a.bits.mask.poke(0.U)
            dut.io.inPorts(0).a.bits.corrupt.poke(0.U)
            prodSent = true
          } else if (prodSent && dut.io.inPorts(0).d.valid.peek().litToBoolean) {
            dut.io.inPorts(0).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
            prodSent = false
            prodPhase = (prodPhase + 1) % 3
            if (prodPhase == 0) prodUnits += 1
          }
        }

        // Consumer
        if (consUnits < transfers) {
          val req = consOps(consPhase)
          if (!consSent && dut.io.inPorts(1).a.ready.peek().litToBoolean) {
            dut.io.inPorts(1).a.valid.poke(true.B)
            dut.io.inPorts(1).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
            dut.io.inPorts(1).a.bits.param.poke(req.param)
            dut.io.inPorts(1).a.bits.address.poke(req.address.U)
            dut.io.inPorts(1).a.bits.data.poke(req.data.U)
            dut.io.inPorts(1).a.bits.source.poke(0.U)
            dut.io.inPorts(1).a.bits.size.poke(0.U)
            dut.io.inPorts(1).a.bits.mask.poke(0.U)
            dut.io.inPorts(1).a.bits.corrupt.poke(0.U)
            consSent = true
          } else if (consSent && dut.io.inPorts(1).d.valid.peek().litToBoolean) {
            dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
            consSent = false
            consPhase = (consPhase + 1) % 3
            if (consPhase == 0) consUnits += 1
          }
        }

        dut.clock.step()
        cycles += 1
      }
    }
  }
}
