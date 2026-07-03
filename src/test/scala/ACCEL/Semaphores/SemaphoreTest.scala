package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class SemaphoreTest extends AnyFreeSpec with Matchers with ChiselSim {

  val maxCycles = 100

  def defaultPokes(dut: Semaphore): Unit = {
    for (i <- 0 until 2) {
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
    dut.io.progPort.bits.initFull.poke(0.U)
    dut.io.progPort.bits.initEmpty.poke(0.U)
  }

  // Producer (port 0): AQGREQ(empty>=1) → ADD(empty,-1) → ADD(full,1), repeated
  // Consumer (port 1): AQGREQ(full>=1)  → ADD(full,-1)  → ADD(empty,1), repeated
  // regs(0)=full (address=0), regs(1)=empty (address=1)
  // Both ports are driven concurrently each cycle via a per-port state machine.
  def producerConsumerRun(dut: Semaphore, bufferSize: Int, transfers: Int): Unit = {
    defaultPokes(dut)
    dut.io.progPort.valid.poke(true.B)
    dut.io.progPort.bits.initFull.poke(0.U)
    dut.io.progPort.bits.initEmpty.poke(bufferSize.U)
    dut.clock.step()
    dut.io.progPort.valid.poke(false.B)

    val prodOps = Seq(
      (ArithmeticDataParam.AQGREQ, 1,  1),
      (ArithmeticDataParam.ADD,    1, -1),
      (ArithmeticDataParam.ADD,    0,  1)
    )
    val consOps = Seq(
      (ArithmeticDataParam.AQGREQ, 0,  1),
      (ArithmeticDataParam.ADD,    0, -1),
      (ArithmeticDataParam.ADD,    1,  1)
    )

    var prodUnits = 0; var consUnits = 0
    var prodPhase = 0; var consPhase = 0
    var prodSent  = false; var consSent = false

    var cycles = 0
    while (consUnits < transfers) {
      require(cycles < transfers * 200, s"Timeout after $cycles cycles")

      // Reset a.valid each cycle; only assert when sending this cycle
      dut.io.inPorts(0).a.valid.poke(false.B)
      dut.io.inPorts(1).a.valid.poke(false.B)
      dut.io.inPorts(0).d.ready.poke(true.B)
      dut.io.inPorts(1).d.ready.poke(true.B)

      // Producer
      if (prodUnits < transfers) {
        if (!prodSent && dut.io.inPorts(0).a.ready.peek().litToBoolean) {
          val (param, addr, data) = prodOps(prodPhase)
          dut.io.inPorts(0).a.valid.poke(true.B)
          dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
          dut.io.inPorts(0).a.bits.param.poke(param)
          dut.io.inPorts(0).a.bits.address.poke(addr.U)
          dut.io.inPorts(0).a.bits.data.poke((data & 0xFFFF).U)
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
        if (!consSent && dut.io.inPorts(1).a.ready.peek().litToBoolean) {
          val (param, addr, data) = consOps(consPhase)
          dut.io.inPorts(1).a.valid.poke(true.B)
          dut.io.inPorts(1).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
          dut.io.inPorts(1).a.bits.param.poke(param)
          dut.io.inPorts(1).a.bits.address.poke(addr.U)
          dut.io.inPorts(1).a.bits.data.poke((data & 0xFFFF).U)
          dut.io.inPorts(1).a.bits.source.poke(1.U)
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

    // Let any in-flight d.fire complete so both ports return to idle
    dut.io.inPorts(0).d.ready.poke(true.B)
    dut.io.inPorts(1).d.ready.poke(true.B)
    dut.clock.step()
    dut.io.inPorts(0).d.ready.poke(false.B)
    dut.io.inPorts(1).d.ready.poke(false.B)

    // Both registers must be back to initial: full=0, empty=bufferSize
    for ((regAddr, expected) <- Seq((0, 0), (1, bufferSize))) {
      dut.io.inPorts(0).a.valid.poke(true.B)
      dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(0).a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      dut.io.inPorts(0).a.bits.address.poke(regAddr.U)
      dut.io.inPorts(0).a.bits.data.poke(0.U)
      dut.io.inPorts(0).a.bits.source.poke(0.U)
      dut.io.inPorts(0).a.bits.size.poke(0.U)
      dut.io.inPorts(0).a.bits.mask.poke(0.U)
      dut.io.inPorts(0).a.bits.corrupt.poke(0.U)
      dut.clock.step(2)
      dut.io.inPorts(0).a.valid.poke(false.B)
      dut.io.inPorts(0).d.ready.poke(true.B)
      dut.io.inPorts(0).d.valid.expect(true.B)
      dut.io.inPorts(0).d.bits.data.expect(expected.U)
      dut.clock.step()
      dut.io.inPorts(0).d.ready.poke(false.B)
    }
  }

  "Producer/consumer: bounded buffer of 4, 16 transfers of 1 unit each" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut => producerConsumerRun(dut, bufferSize = 4,  transfers = 16) }
  }

  "Producer/consumer: unbounded buffer of 16, 16 transfers of 1 unit each" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut => producerConsumerRun(dut, bufferSize = 16, transfers = 16) }
  }

  "AQGREQ on even address should return fullReg" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(42.U)
      dut.io.progPort.bits.initEmpty.poke(7.U)
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)

      ////////////////////////////////////////////////// 

      val port = dut.io.inPorts(0)

      port.a.ready.expect(true.B)
      port.a.valid.poke(true.B)

      port.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      port.a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      port.a.bits.address.poke(0.U)
      port.a.bits.data.poke(0.U)
      port.a.bits.source.poke(0.U)
      port.a.bits.size.poke(0.U)
      port.a.bits.mask.poke(0.U)
      port.a.bits.corrupt.poke(0.U)

      dut.clock.step(2)

      port.a.valid.poke(false.B)

      port.d.valid.expect(true.B)
      port.d.ready.poke(true.B)

      port.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      port.d.bits.data.expect(42.U)
    }
  }

  "AQGREQ on odd address should return emptyReg" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(42.U)
      dut.io.progPort.bits.initEmpty.poke(7.U)
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)


      ////////////////////////////////////////////////// 


      val port = dut.io.inPorts(0)

      port.a.ready.expect(true.B)
      port.a.valid.poke(true.B)

      port.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      port.a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      port.a.bits.address.poke(1.U)
      port.a.bits.data.poke(0.U)
      port.a.bits.source.poke(0.U)
      port.a.bits.size.poke(0.U)
      port.a.bits.mask.poke(0.U)
      port.a.bits.corrupt.poke(0.U)

      dut.clock.step(2)

      port.a.valid.poke(false.B)

      port.d.valid.expect(true.B)
      port.d.ready.poke(true.B)

      port.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      port.d.bits.data.expect(7.U)
    }
  }

  "ADD with negative data should decrement emptyReg and return the new value" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(20.U)
      dut.io.progPort.bits.initEmpty.poke(15.U)
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)


      ////////////////////////////////////////////////// 


      val port = dut.io.inPorts(0)

      port.a.ready.expect(true.B)
      port.a.valid.poke(true.B)

      port.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      port.a.bits.param.poke(ArithmeticDataParam.ADD)
      port.a.bits.address.poke(1.U)
      port.a.bits.data.poke((-3 & 0xFFFF).U)
      port.a.bits.source.poke(0.U)
      port.a.bits.size.poke(0.U)
      port.a.bits.mask.poke(0.U)
      port.a.bits.corrupt.poke(0.U)

      dut.clock.step()

      port.a.valid.poke(false.B)

      while(!port.d.valid.peek().litToBoolean){
        dut.clock.step()
      }

      port.d.valid.expect(true.B)
      port.d.ready.poke(true.B)

      port.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      port.d.bits.data.expect(12.U)

      dut.clock.step()


      ////////////////////////////////////////////////// 

      // Verify new emptyReg value persisted

      port.a.ready.expect(true.B)
      port.a.valid.poke(true.B)

      port.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      port.a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      port.a.bits.address.poke(1.U)
      port.a.bits.data.poke(0.U)
      port.a.bits.source.poke(0.U)
      port.a.bits.size.poke(0.U)
      port.a.bits.mask.poke(0.U)
      port.a.bits.corrupt.poke(0.U)

      dut.clock.step(2)

      port.a.valid.poke(false.B)

      port.d.valid.expect(true.B)
      port.d.ready.poke(true.B)

      port.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      port.d.bits.data.expect(12.U)
    }
  }

  "Port should return to idle after a transaction and accept new requests" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(5.U)
      dut.io.progPort.bits.initEmpty.poke(3.U)
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)


      ////////////////////////////////////////////////// 


      dut.io.inPorts(0).a.ready.expect(true.B)
      dut.io.inPorts(0).a.valid.poke(true.B)

      dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(0).a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      dut.io.inPorts(0).a.bits.address.poke(0.U)
      dut.io.inPorts(0).a.bits.data.poke(0.U)
      dut.io.inPorts(0).a.bits.source.poke(0.U)
      dut.io.inPorts(0).a.bits.size.poke(0.U)
      dut.io.inPorts(0).a.bits.mask.poke(0.U)
      dut.io.inPorts(0).a.bits.corrupt.poke(0.U)

      dut.clock.step(2)

      dut.io.inPorts(0).a.valid.poke(false.B)

      dut.io.inPorts(0).d.valid.expect(true.B)
      dut.io.inPorts(0).d.ready.poke(true.B)

      dut.io.inPorts(0).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.inPorts(0).d.bits.data.expect(5.U)

      dut.clock.step()


      ////////////////////////////////////////////////// 


      dut.io.inPorts(1).a.ready.expect(true.B)
      dut.io.inPorts(1).a.valid.poke(true.B)

      dut.io.inPorts(1).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(1).a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      dut.io.inPorts(1).a.bits.address.poke(0.U)
      dut.io.inPorts(1).a.bits.data.poke(0.U)
      dut.io.inPorts(1).a.bits.source.poke(0.U)
      dut.io.inPorts(1).a.bits.size.poke(0.U)
      dut.io.inPorts(1).a.bits.mask.poke(0.U)
      dut.io.inPorts(1).a.bits.corrupt.poke(0.U)

      dut.clock.step(2)

      dut.io.inPorts(1).a.valid.poke(false.B)

      dut.io.inPorts(1).d.valid.expect(true.B)
      dut.io.inPorts(1).d.ready.poke(true.B)

      dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.inPorts(1).d.bits.data.expect(5.U)

      dut.clock.step()
    }
  }

  "Both ports should operate concurrently and independently" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(50.U)
      dut.io.progPort.bits.initEmpty.poke(30.U)
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)

      // Send AQGREQ on both ports simultaneously: port 0 -> fullReg, port 1 -> emptyReg
      for (i <- 0 until 2) {
        dut.io.inPorts(i).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
        dut.io.inPorts(i).a.bits.param.poke(ArithmeticDataParam.AQGREQ)
        dut.io.inPorts(i).a.bits.address.poke((i & 1).U)
        dut.io.inPorts(i).a.bits.data.poke(0.U)
        dut.io.inPorts(i).a.bits.source.poke(i.U)
        dut.io.inPorts(i).a.bits.size.poke(0.U)
        dut.io.inPorts(i).a.bits.mask.poke(0.U)
        dut.io.inPorts(i).a.bits.corrupt.poke(0.U)
        dut.io.inPorts(i).a.valid.poke(true.B)
      }
      dut.clock.step()
      for (i <- 0 until 2) dut.io.inPorts(i).a.valid.poke(false.B)

      // Collect responses from both ports
      for (i <- 0 until 2) dut.io.inPorts(i).d.ready.poke(true.B)

      var cycles = 0
      var port0Resp = BigInt(-1)
      var port1Resp = BigInt(-1)
      while (port0Resp < 0 || port1Resp < 0) {
        require(cycles < maxCycles, "Timeout waiting for both ports to respond")
        if (port0Resp < 0 && dut.io.inPorts(0).d.valid.peek().litToBoolean)
          port0Resp = dut.io.inPorts(0).d.bits.data.peek().litValue
        if (port1Resp < 0 && dut.io.inPorts(1).d.valid.peek().litToBoolean)
          port1Resp = dut.io.inPorts(1).d.bits.data.peek().litValue
        if (port0Resp < 0 || port1Resp < 0) dut.clock.step()
        cycles += 1
      }
      for (i <- 0 until 2) dut.io.inPorts(i).d.ready.poke(false.B)

      port0Resp mustBe 50 // even address -> fullReg
      port1Resp mustBe 30 // odd address  -> emptyReg
    }
  }

  "Simultaneous ADD(negative) on the same register: both ports complete and final value is correct" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(0.U)
      dut.io.progPort.bits.initEmpty.poke(20.U)  // emptyReg = 20
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)

      //////////////////////////////////////////////////

      // Send ADD(negative) to emptyReg (address=1) from both ports simultaneously
      for (i <- 0 until 2) {
        dut.io.inPorts(i).a.valid.poke(true.B)
        dut.io.inPorts(i).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
        dut.io.inPorts(i).a.bits.param.poke(ArithmeticDataParam.ADD)
        dut.io.inPorts(i).a.bits.address.poke(1.U)
        dut.io.inPorts(i).a.bits.data.poke(((if (i == 0) -3 else -5) & 0xFFFF).U)
        dut.io.inPorts(i).a.bits.source.poke(i.U)
        dut.io.inPorts(i).a.bits.size.poke(0.U)
        dut.io.inPorts(i).a.bits.mask.poke(0.U)
        dut.io.inPorts(i).a.bits.corrupt.poke(0.U)
      }
      // Wait until both ports have accepted their request (a.ready && a.valid)
      var accepted = Array(false, false)
      var waitCycles = 0
      while (!accepted(0) || !accepted(1)) {
        require(waitCycles < maxCycles, "Timeout waiting for a.ready on both ports")
        for (i <- 0 until 2) {
          if (!accepted(i) && dut.io.inPorts(i).a.ready.peek().litToBoolean) {
            accepted(i) = true
          }
        }
        dut.clock.step()
        waitCycles += 1
      }
      for (i <- 0 until 2) dut.io.inPorts(i).a.valid.poke(false.B)

      // Wait for both ports to respond
      for (i <- 0 until 2) dut.io.inPorts(i).d.ready.poke(true.B)
      var port0Done = false
      var port1Done = false
      var cycles = 0
      while (!port0Done || !port1Done) {
        require(cycles < maxCycles, "Timeout waiting for both ports to respond")
        if (!port0Done && dut.io.inPorts(0).d.valid.peek().litToBoolean) {
          dut.io.inPorts(0).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
          port0Done = true
        }
        if (!port1Done && dut.io.inPorts(1).d.valid.peek().litToBoolean) {
          dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
          port1Done = true
        }
        if (!port0Done || !port1Done) dut.clock.step()
        cycles += 1
      }
      dut.clock.step()
      for (i <- 0 until 2) dut.io.inPorts(i).d.ready.poke(false.B)

      //////////////////////////////////////////////////

      // Final emptyReg must be 20 - 3 - 5 = 12 regardless of grant order
      dut.io.inPorts(0).a.valid.poke(true.B)
      dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(0).a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      dut.io.inPorts(0).a.bits.address.poke(1.U)
      dut.io.inPorts(0).a.bits.data.poke(0.U)
      dut.io.inPorts(0).a.bits.source.poke(0.U)
      dut.io.inPorts(0).a.bits.size.poke(0.U)
      dut.io.inPorts(0).a.bits.mask.poke(0.U)
      dut.io.inPorts(0).a.bits.corrupt.poke(0.U)
      dut.clock.step(2)
      dut.io.inPorts(0).a.valid.poke(false.B)
      dut.io.inPorts(0).d.ready.poke(true.B)
      dut.io.inPorts(0).d.valid.expect(true.B)
      dut.io.inPorts(0).d.bits.data.expect(12.U)
    }
  }

  "Simultaneous ADD(negative) on different registers: no contention, both ports proceed in parallel" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(50.U)  // fullReg  = 50
      dut.io.progPort.bits.initEmpty.poke(30.U)  // emptyReg = 30
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)

      //////////////////////////////////////////////////

      // Port 0 decrements fullReg (address=0) by 10; port 1 decrements emptyReg (address=1) by 7
      dut.io.inPorts(0).a.valid.poke(true.B)
      dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(0).a.bits.param.poke(ArithmeticDataParam.ADD)
      dut.io.inPorts(0).a.bits.address.poke(0.U)
      dut.io.inPorts(0).a.bits.data.poke((-10 & 0xFFFF).U)
      dut.io.inPorts(0).a.bits.source.poke(0.U)
      dut.io.inPorts(0).a.bits.size.poke(0.U)
      dut.io.inPorts(0).a.bits.mask.poke(0.U)
      dut.io.inPorts(0).a.bits.corrupt.poke(0.U)

      dut.io.inPorts(1).a.valid.poke(true.B)
      dut.io.inPorts(1).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(1).a.bits.param.poke(ArithmeticDataParam.ADD)
      dut.io.inPorts(1).a.bits.address.poke(1.U)
      dut.io.inPorts(1).a.bits.data.poke((-7 & 0xFFFF).U)
      dut.io.inPorts(1).a.bits.source.poke(1.U)
      dut.io.inPorts(1).a.bits.size.poke(0.U)
      dut.io.inPorts(1).a.bits.mask.poke(0.U)
      dut.io.inPorts(1).a.bits.corrupt.poke(0.U)

      dut.clock.step()
      for (i <- 0 until 2) dut.io.inPorts(i).a.valid.poke(false.B)
      for (i <- 0 until 2) dut.io.inPorts(i).d.ready.poke(true.B)

      // With no contention both grants are given simultaneously — both should respond
      // at the same time (within 2 cycles of each other at most)
      var port0Done = false
      var port1Done = false
      var cycles = 0
      while (!port0Done || !port1Done) {
        require(cycles < maxCycles, "Timeout waiting for both ports to respond")
        if (!port0Done && dut.io.inPorts(0).d.valid.peek().litToBoolean) {
          dut.io.inPorts(0).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
          dut.io.inPorts(0).d.bits.data.expect(40.U)  // 50 - 10
          port0Done = true
        }
        if (!port1Done && dut.io.inPorts(1).d.valid.peek().litToBoolean) {
          dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
          dut.io.inPorts(1).d.bits.data.expect(23.U)  // 30 - 7
          port1Done = true
        }
        if (!port0Done || !port1Done) dut.clock.step()
        cycles += 1
      }
      for (i <- 0 until 2) dut.io.inPorts(i).d.ready.poke(false.B)
    }
  }

  "Repeated same-register contention: neither port starves across multiple rounds" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(0.U)
      dut.io.progPort.bits.initEmpty.poke(60.U)  // emptyReg = 60; 3 rounds * (4+6) = 30 total decrement
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)

      // Run 3 rounds of simultaneous contention on emptyReg
      for (round <- 0 until 3) {
        for (i <- 0 until 2) {
          dut.io.inPorts(i).a.valid.poke(true.B)
          dut.io.inPorts(i).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
          dut.io.inPorts(i).a.bits.param.poke(ArithmeticDataParam.ADD)
          dut.io.inPorts(i).a.bits.address.poke(1.U)
          dut.io.inPorts(i).a.bits.data.poke(((if (i == 0) -4 else -6) & 0xFFFF).U)
          dut.io.inPorts(i).a.bits.source.poke(i.U)
          dut.io.inPorts(i).a.bits.size.poke(0.U)
          dut.io.inPorts(i).a.bits.mask.poke(0.U)
          dut.io.inPorts(i).a.bits.corrupt.poke(0.U)
        }
        // Wait until both ports have accepted their request (a.ready && a.valid)
        var accepted = Array(false, false)
        var waitCycles = 0
        while (!accepted(0) || !accepted(1)) {
          require(waitCycles < maxCycles, s"Timeout in round $round waiting for a.ready on both ports")
          for (i <- 0 until 2) {
            if (!accepted(i) && dut.io.inPorts(i).a.ready.peek().litToBoolean) {
              accepted(i) = true
            }
          }
          dut.clock.step()
          waitCycles += 1
        }
        for (i <- 0 until 2) dut.io.inPorts(i).a.valid.poke(false.B)
        for (i <- 0 until 2) dut.io.inPorts(i).d.ready.poke(true.B)

        var port0Done = false
        var port1Done = false
        var cycles = 0
        while (!port0Done || !port1Done) {
          require(cycles < maxCycles, s"Timeout in round $round waiting for both ports")
          if (!port0Done && dut.io.inPorts(0).d.valid.peek().litToBoolean) {
            dut.io.inPorts(0).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
            port0Done = true
          }
          if (!port1Done && dut.io.inPorts(1).d.valid.peek().litToBoolean) {
            dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
            port1Done = true
          }
          if (!port0Done || !port1Done) dut.clock.step()
          cycles += 1
        }

        dut.clock.step()
        for (i <- 0 until 2) dut.io.inPorts(i).d.ready.poke(false.B)
      }

      //////////////////////////////////////////////////
      // Read the final state of the register

      // Final emptyReg = 60 - 3*(4+6) = 30
      dut.io.inPorts(0).a.valid.poke(true.B)
      dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(0).a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      dut.io.inPorts(0).a.bits.address.poke(1.U)
      dut.io.inPorts(0).a.bits.data.poke(0.U)
      dut.io.inPorts(0).a.bits.source.poke(0.U)
      dut.io.inPorts(0).a.bits.size.poke(0.U)
      dut.io.inPorts(0).a.bits.mask.poke(0.U)
      dut.io.inPorts(0).a.bits.corrupt.poke(0.U)
      dut.clock.step(2)
      dut.io.inPorts(0).a.valid.poke(false.B)
      dut.io.inPorts(0).d.ready.poke(true.B)
      dut.io.inPorts(0).d.valid.expect(true.B)
      dut.io.inPorts(0).d.bits.data.expect(30.U)
    }
  }

  "ADD with positive data should increment the target register and return the new value" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(10.U)  // fullReg  = 10
      dut.io.progPort.bits.initEmpty.poke(5.U)   // emptyReg = 5
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)

      //////////////////////////////////////////////////

      val port = dut.io.inPorts(0)

      port.a.ready.expect(true.B)
      port.a.valid.poke(true.B)
      port.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      port.a.bits.param.poke(ArithmeticDataParam.ADD)
      port.a.bits.address.poke(1.U)  // emptyReg
      port.a.bits.data.poke(7.U)
      port.a.bits.source.poke(0.U)
      port.a.bits.size.poke(0.U)
      port.a.bits.mask.poke(0.U)
      port.a.bits.corrupt.poke(0.U)

      dut.clock.step()
      port.a.valid.poke(false.B)

      while (!port.d.valid.peek().litToBoolean) { dut.clock.step() }

      port.d.ready.poke(true.B)
      port.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      port.d.bits.data.expect(12.U)  // 5 + 7

      dut.clock.step()
      port.d.ready.poke(false.B)

      //////////////////////////////////////////////////

      // Verify the new value persisted
      port.a.valid.poke(true.B)
      port.a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      port.a.bits.address.poke(1.U)
      port.a.bits.data.poke(0.U)
      dut.clock.step(2)
      port.a.valid.poke(false.B)
      port.d.ready.poke(true.B)
      port.d.valid.expect(true.B)
      port.d.bits.data.expect(12.U)
    }
  }

  "Simultaneous ADD(positive) and ADD(negative) on the same register: both complete and final value is correct" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(0.U)
      dut.io.progPort.bits.initEmpty.poke(20.U)  // emptyReg = 20
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)

      //////////////////////////////////////////////////

      // Port 0: ADD(+8) emptyReg; port 1: ADD(-5) emptyReg — simultaneously
      dut.io.inPorts(0).a.valid.poke(true.B)
      dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(0).a.bits.param.poke(ArithmeticDataParam.ADD)
      dut.io.inPorts(0).a.bits.address.poke(1.U)
      dut.io.inPorts(0).a.bits.data.poke(8.U)
      dut.io.inPorts(0).a.bits.source.poke(0.U)
      dut.io.inPorts(0).a.bits.size.poke(0.U)
      dut.io.inPorts(0).a.bits.mask.poke(0.U)
      dut.io.inPorts(0).a.bits.corrupt.poke(0.U)

      dut.io.inPorts(1).a.valid.poke(true.B)
      dut.io.inPorts(1).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(1).a.bits.param.poke(ArithmeticDataParam.ADD)
      dut.io.inPorts(1).a.bits.address.poke(1.U)
      dut.io.inPorts(1).a.bits.data.poke((-5 & 0xFFFF).U)
      dut.io.inPorts(1).a.bits.source.poke(1.U)
      dut.io.inPorts(1).a.bits.size.poke(0.U)
      dut.io.inPorts(1).a.bits.mask.poke(0.U)
      dut.io.inPorts(1).a.bits.corrupt.poke(0.U)

      dut.clock.step()
      for (i <- 0 until 2) dut.io.inPorts(i).a.valid.poke(false.B)
      for (i <- 0 until 2) dut.io.inPorts(i).d.ready.poke(true.B)

      var port0Done = false
      var port1Done = false
      var cycles = 0
      while (!port0Done || !port1Done) {
        require(cycles < maxCycles, "Timeout waiting for both ports to respond")
        if (!port0Done && dut.io.inPorts(0).d.valid.peek().litToBoolean) {
          dut.io.inPorts(0).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
          port0Done = true
        }
        if (!port1Done && dut.io.inPorts(1).d.valid.peek().litToBoolean) {
          dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
          port1Done = true
        }
        if (!port0Done || !port1Done) dut.clock.step()
        cycles += 1
      }
      dut.clock.step()
      for (i <- 0 until 2) dut.io.inPorts(i).d.ready.poke(false.B)

      //////////////////////////////////////////////////

      // Final emptyReg = 20 + 8 - 5 = 23 regardless of grant order
      dut.io.inPorts(0).a.valid.poke(true.B)
      dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(0).a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      dut.io.inPorts(0).a.bits.address.poke(1.U)
      dut.io.inPorts(0).a.bits.data.poke(0.U)
      dut.io.inPorts(0).a.bits.source.poke(0.U)
      dut.io.inPorts(0).a.bits.size.poke(0.U)
      dut.io.inPorts(0).a.bits.mask.poke(0.U)
      dut.io.inPorts(0).a.bits.corrupt.poke(0.U)
      dut.clock.step(2)
      dut.io.inPorts(0).a.valid.poke(false.B)
      dut.io.inPorts(0).d.ready.poke(true.B)
      dut.io.inPorts(0).d.valid.expect(true.B)
      dut.io.inPorts(0).d.bits.data.expect(23.U)
    }
  }

  // Test is deprecated after introducing triggersystem to semaphore programming, programming doesnt block.

  /*
  "progPort reprogramming should take effect on subsequent requests after completion" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)

      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(10.U)
      dut.io.progPort.bits.initEmpty.poke(0.U)
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)


      ////////////////////////////////////////////////// 


      dut.io.inPorts(1).a.ready.expect(true.B)
      dut.io.inPorts(1).a.valid.poke(true.B)

      dut.io.inPorts(1).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(1).a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      dut.io.inPorts(1).a.bits.address.poke(0.U)
      dut.io.inPorts(1).a.bits.data.poke(10.U)
      dut.io.inPorts(1).a.bits.source.poke(0.U)
      dut.io.inPorts(1).a.bits.size.poke(0.U)
      dut.io.inPorts(1).a.bits.mask.poke(0.U)
      dut.io.inPorts(1).a.bits.corrupt.poke(0.U)

      dut.clock.step(2)

      dut.io.inPorts(1).a.valid.poke(false.B)

      dut.io.inPorts(1).d.valid.expect(true.B)
      dut.io.inPorts(1).d.ready.poke(true.B)

      dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.inPorts(1).d.bits.data.expect(10.U)


      dut.clock.step()


      ////////////////////////////////////////////////// 

      // Attempt reprogram
      /*
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(99.U)
      dut.io.progPort.bits.initEmpty.poke(77.U)
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)
      */

      dut.io.progPort.ready.expect(false.B)
      dut.clock.step()


      ////////////////////////////////////////////////// 
      // Decrement and reattempt reprogramming

      dut.io.inPorts(1).a.ready.expect(true.B)
      dut.io.inPorts(1).a.valid.poke(true.B)

      dut.io.inPorts(1).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(1).a.bits.param.poke(ArithmeticDataParam.SUBU)
      dut.io.inPorts(1).a.bits.address.poke(0.U)
      dut.io.inPorts(1).a.bits.data.poke(10.U)
      dut.io.inPorts(1).a.bits.source.poke(0.U)
      dut.io.inPorts(1).a.bits.size.poke(0.U)
      dut.io.inPorts(1).a.bits.mask.poke(0.U)
      dut.io.inPorts(1).a.bits.corrupt.poke(0.U)

      dut.clock.step(2)

      dut.io.inPorts(1).a.valid.poke(false.B)

      dut.io.inPorts(1).d.valid.expect(true.B)
      dut.io.inPorts(1).d.ready.poke(true.B)

      dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.inPorts(1).d.bits.data.expect(0.U)

      ////////////////////////////////////////////////// 

      // Attempt reprogram
      /*
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(99.U)
      dut.io.progPort.bits.initEmpty.poke(77.U)
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)
      */

      dut.io.progPort.ready.expect(true.B)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(99.U)
      dut.io.progPort.bits.initEmpty.poke(77.U)
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)


      ////////////////////////////////////////////////// 


      dut.io.inPorts(1).a.ready.expect(true.B)
      dut.io.inPorts(1).a.valid.poke(true.B)

      dut.io.inPorts(1).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(1).a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      dut.io.inPorts(1).a.bits.address.poke(0.U)
      dut.io.inPorts(1).a.bits.data.poke(0.U)
      dut.io.inPorts(1).a.bits.source.poke(0.U)
      dut.io.inPorts(1).a.bits.size.poke(0.U)
      dut.io.inPorts(1).a.bits.mask.poke(0.U)
      dut.io.inPorts(1).a.bits.corrupt.poke(0.U)

      dut.clock.step(2)

      dut.io.inPorts(1).a.valid.poke(false.B)

      dut.io.inPorts(1).d.valid.expect(true.B)
      dut.io.inPorts(1).d.ready.poke(true.B)

      dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.inPorts(1).d.bits.data.expect(99.U)

      dut.clock.step()


      ////////////////////////////////////////////////// 
 

      dut.io.inPorts(1).a.ready.expect(true.B)
      dut.io.inPorts(1).a.valid.poke(true.B)

      dut.io.inPorts(1).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(1).a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      dut.io.inPorts(1).a.bits.address.poke(1.U)
      dut.io.inPorts(1).a.bits.data.poke(0.U)
      dut.io.inPorts(1).a.bits.source.poke(0.U)
      dut.io.inPorts(1).a.bits.size.poke(0.U)
      dut.io.inPorts(1).a.bits.mask.poke(0.U)
      dut.io.inPorts(1).a.bits.corrupt.poke(0.U)

      dut.clock.step(2)

      dut.io.inPorts(1).a.valid.poke(false.B)

      dut.io.inPorts(1).d.valid.expect(true.B)
      dut.io.inPorts(1).d.ready.poke(true.B)

      dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.inPorts(1).d.bits.data.expect(77.U)
    }
  }
  */

  "AQGREQ should stall until acquire is valid" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)

      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(0.U) // fullReg
      dut.io.progPort.bits.initEmpty.poke(5.U)  // emptyReg
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)


      ////////////////////////////////////////////////// 


      dut.io.inPorts(1).a.ready.expect(true.B)
      dut.io.inPorts(1).a.valid.poke(true.B)

      dut.io.inPorts(1).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(1).a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      dut.io.inPorts(1).a.bits.address.poke(0.U)
      dut.io.inPorts(1).a.bits.data.poke(1.U) // acquire when greater or equal to 1 
      dut.io.inPorts(1).a.bits.source.poke(0.U)
      dut.io.inPorts(1).a.bits.size.poke(0.U)
      dut.io.inPorts(1).a.bits.mask.poke(0.U)
      dut.io.inPorts(1).a.bits.corrupt.poke(0.U)

      dut.clock.step(2)

      dut.io.inPorts(1).a.valid.poke(false.B)

      dut.io.inPorts(1).d.valid.expect(false.B) // Should stall 
      /*
      dut.io.inPorts(1).d.ready.poke(true.B)

      dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.inPorts(1).d.bits.data.expect(10.U)
      */

      dut.clock.step()


      ////////////////////////////////////////////////// 

      dut.io.inPorts(0).a.ready.expect(true.B)
      dut.io.inPorts(0).a.valid.poke(true.B)

      dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(0).a.bits.param.poke(ArithmeticDataParam.ADD)
      dut.io.inPorts(0).a.bits.address.poke(0.U)
      dut.io.inPorts(0).a.bits.data.poke(1.U) // Signed add +1
      dut.io.inPorts(0).a.bits.source.poke(0.U)
      dut.io.inPorts(0).a.bits.size.poke(0.U)
      dut.io.inPorts(0).a.bits.mask.poke(0.U)
      dut.io.inPorts(0).a.bits.corrupt.poke(0.U)

      dut.clock.step(2)

      dut.io.inPorts(0).a.valid.poke(false.B)

      dut.io.inPorts(0).d.valid.expect(true.B)
      dut.io.inPorts(0).d.ready.poke(true.B)

      dut.io.inPorts(0).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.inPorts(0).d.bits.data.expect(1.U)

      ////////////////////////////////////////////////// 

      // Acquire

      dut.clock.step()

      dut.io.inPorts(1).d.valid.expect(true.B)
      dut.io.inPorts(1).d.ready.poke(true.B)

      dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.inPorts(1).d.bits.data.expect(1.U)

      dut.clock.step()

      ////////////////////////////////////////////////// 

      // SUB 1

      dut.io.inPorts(1).a.ready.expect(true.B)
      dut.io.inPorts(1).a.valid.poke(true.B)

      dut.io.inPorts(1).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(1).a.bits.param.poke(ArithmeticDataParam.ADD)
      dut.io.inPorts(1).a.bits.address.poke(0.U)
      dut.io.inPorts(1).a.bits.data.poke((-1 & 0xFFFF).U)
      dut.io.inPorts(1).a.bits.source.poke(0.U)
      dut.io.inPorts(1).a.bits.size.poke(0.U)
      dut.io.inPorts(1).a.bits.mask.poke(0.U)
      dut.io.inPorts(1).a.bits.corrupt.poke(0.U)

      dut.clock.step(2)

      dut.io.inPorts(1).a.valid.poke(false.B)

      dut.io.inPorts(1).d.valid.expect(true.B)
      dut.io.inPorts(1).d.ready.poke(true.B)

      dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.inPorts(1).d.bits.data.expect(0.U)

    }
  }

  def runTx(dut: Semaphore, portIdx: Int, param: UInt, addr: Int, data: Int): Unit = {
    val port = dut.io.inPorts(portIdx)
    var cycles = 0
    while (!port.a.ready.peek().litToBoolean) {
      dut.clock.step(); cycles += 1
      require(cycles < 200, s"Timeout waiting for a.ready on port $portIdx")
    }
    port.a.valid.poke(true.B)
    port.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
    port.a.bits.param.poke(param)
    port.a.bits.address.poke(addr.U)
    port.a.bits.data.poke((data & 0xFFFF).U)
    port.a.bits.source.poke(portIdx.U)
    port.a.bits.size.poke(0.U)
    port.a.bits.mask.poke(0.U)
    port.a.bits.corrupt.poke(0.U)
    dut.clock.step()
    port.a.valid.poke(false.B)

    port.d.ready.poke(true.B)
    cycles = 0
    while (!port.d.valid.peek().litToBoolean) {
      dut.clock.step(); cycles += 1
      require(cycles < 200, s"Timeout waiting for d.valid on port $portIdx")
    }
    dut.clock.step()
    port.d.ready.poke(false.B)
  }

  "Semaphore emits Complete event after a full producer/consumer cycle" in {
    implicit val c = Configuration.default()
    val semIdx = 3
    simulate(new Semaphore(semIdx)) { dut =>
      defaultPokes(dut)

      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(0.U)
      dut.io.progPort.bits.initEmpty.poke(1.U)
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)

      dut.io.eventPort.valid.expect(false.B)

      runTx(dut, 0, ArithmeticDataParam.AQGREQ, 1,  1)
      runTx(dut, 0, ArithmeticDataParam.ADD,    1, -1)
      runTx(dut, 0, ArithmeticDataParam.ADD,    0,  1)

      dut.io.eventPort.valid.expect(false.B)

      runTx(dut, 1, ArithmeticDataParam.AQGREQ, 0,  1)
      runTx(dut, 1, ArithmeticDataParam.ADD,    0, -1)
      runTx(dut, 1, ArithmeticDataParam.ADD,    1,  1)

      dut.clock.step()
      dut.io.eventPort.valid.expect(true.B)
      dut.io.eventPort.bits.eventCode.expect(SemaphoreEventCodes.Complete)
      dut.io.eventPort.bits.addr.expect(semIdx.U)

      dut.io.eventPort.ready.poke(true.B)
      dut.clock.step()
      dut.io.eventPort.ready.poke(false.B)
      dut.io.eventPort.valid.expect(false.B)
    }
  }


  "Get with mismatched gen should return 0" in {
    implicit val c = Configuration(semaphore = SemaphoreParams(generationWidth = 1))
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)

      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(5.U) // fullReg
      dut.io.progPort.bits.initEmpty.poke(0.U)  // emptyReg
      dut.io.progPort.bits.generation.poke(1.U)  // gen 
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)


      ////////////////////////////////////////////////// 


      dut.io.inPorts(0).a.ready.expect(true.B)
      dut.io.inPorts(0).a.valid.poke(true.B)

      dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.Get)
      //dut.io.inPort0(1).a.bits.param.poke(ArithmeticDataParam.AQGREQ)
      dut.io.inPorts(0).a.bits.address.poke(0.U)
      //dut.io.inPort0(1).a.bits.data.poke(1.U) // acquire when greater or equal to 1 
      dut.io.inPorts(0).a.bits.source.poke(0.U)
      dut.io.inPorts(0).a.bits.size.poke(2.U)
      dut.io.inPorts(0).a.bits.mask.poke(0.U)
      dut.io.inPorts(0).a.bits.corrupt.poke(0.U)

      dut.clock.step(1)

      dut.io.inPorts(0).a.valid.poke(false.B)

      dut.io.inPorts(0).d.ready.poke(true.B)
      dut.io.inPorts(0).d.valid.expect(true.B)
      dut.io.inPorts(0).d.bits.data.expect(0.U)
    }
  }

  "Get with correct gen should return data" in {
    implicit val c = Configuration(semaphore = SemaphoreParams(generationWidth = 1))
    simulate(new Semaphore(0)) { dut =>
      defaultPokes(dut)

      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits.initFull.poke(5.U) // fullReg
      dut.io.progPort.bits.initEmpty.poke(0.U)  // emptyReg
      dut.io.progPort.bits.generation.poke(1.U)  // gen 
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)


      ////////////////////////////////////////////////// 


      dut.io.inPorts(0).a.ready.expect(true.B)
      dut.io.inPorts(0).a.valid.poke(true.B)

      dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.Get)
      dut.io.inPorts(0).a.bits.address.poke(1.U)
      dut.io.inPorts(0).a.bits.source.poke(0.U)
      dut.io.inPorts(0).a.bits.size.poke(2.U)
      dut.io.inPorts(0).a.bits.mask.poke(0.U)
      dut.io.inPorts(0).a.bits.corrupt.poke(0.U)

      dut.clock.step(1)

      dut.io.inPorts(0).a.valid.poke(false.B)

      dut.io.inPorts(0).d.ready.poke(true.B)
      dut.io.inPorts(0).d.valid.expect(true.B)
      dut.io.inPorts(0).d.bits.data.expect(5.U)
    }
  }




}
