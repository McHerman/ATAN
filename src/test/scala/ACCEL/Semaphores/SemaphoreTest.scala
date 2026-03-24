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
    dut.io.progPort.bits(0).poke(0.U)
    dut.io.progPort.bits(1).poke(0.U)
  }

  "AQGREQ on even address should return fullReg" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore()) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits(0).poke(42.U)
      dut.io.progPort.bits(1).poke(7.U)
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
    simulate(new Semaphore()) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits(0).poke(42.U)
      dut.io.progPort.bits(1).poke(7.U)
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

  "SUBU should decrement emptyReg and return the new value" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore()) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits(0).poke(20.U)
      dut.io.progPort.bits(1).poke(15.U)
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)


      ////////////////////////////////////////////////// 


      val port = dut.io.inPorts(0)

      port.a.ready.expect(true.B)
      port.a.valid.poke(true.B)

      port.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      port.a.bits.param.poke(ArithmeticDataParam.SUBU)
      port.a.bits.address.poke(1.U)
      port.a.bits.data.poke(3.U)
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
    simulate(new Semaphore()) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits(0).poke(5.U)
      dut.io.progPort.bits(1).poke(3.U)
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
    simulate(new Semaphore()) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits(0).poke(50.U)
      dut.io.progPort.bits(1).poke(30.U)
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

  "Simultaneous SUBU on the same register: both ports complete and final value is correct" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore()) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits(0).poke(0.U)
      dut.io.progPort.bits(1).poke(20.U)  // emptyReg = 20
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)

      //////////////////////////////////////////////////

      // Send SUBU to emptyReg (address=1) from both ports simultaneously
      for (i <- 0 until 2) {
        dut.io.inPorts(i).a.valid.poke(true.B)
        dut.io.inPorts(i).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
        dut.io.inPorts(i).a.bits.param.poke(ArithmeticDataParam.SUBU)
        dut.io.inPorts(i).a.bits.address.poke(1.U)
        dut.io.inPorts(i).a.bits.data.poke((if (i == 0) 3 else 5).U)
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

  "Simultaneous SUBU on different registers: no contention, both ports proceed in parallel" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore()) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits(0).poke(50.U)  // fullReg  = 50
      dut.io.progPort.bits(1).poke(30.U)  // emptyReg = 30
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)

      //////////////////////////////////////////////////

      // Port 0 decrements fullReg (address=0) by 10; port 1 decrements emptyReg (address=1) by 7
      dut.io.inPorts(0).a.valid.poke(true.B)
      dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(0).a.bits.param.poke(ArithmeticDataParam.SUBU)
      dut.io.inPorts(0).a.bits.address.poke(0.U)
      dut.io.inPorts(0).a.bits.data.poke(10.U)
      dut.io.inPorts(0).a.bits.source.poke(0.U)
      dut.io.inPorts(0).a.bits.size.poke(0.U)
      dut.io.inPorts(0).a.bits.mask.poke(0.U)
      dut.io.inPorts(0).a.bits.corrupt.poke(0.U)

      dut.io.inPorts(1).a.valid.poke(true.B)
      dut.io.inPorts(1).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(1).a.bits.param.poke(ArithmeticDataParam.SUBU)
      dut.io.inPorts(1).a.bits.address.poke(1.U)
      dut.io.inPorts(1).a.bits.data.poke(7.U)
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
    simulate(new Semaphore()) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits(0).poke(0.U)
      dut.io.progPort.bits(1).poke(60.U)  // emptyReg = 60; 3 rounds * (4+6) = 30 total decrement
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)

      // Run 3 rounds of simultaneous contention on emptyReg
      for (round <- 0 until 3) {
        for (i <- 0 until 2) {
          dut.io.inPorts(i).a.valid.poke(true.B)
          dut.io.inPorts(i).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
          dut.io.inPorts(i).a.bits.param.poke(ArithmeticDataParam.SUBU)
          dut.io.inPorts(i).a.bits.address.poke(1.U)
          dut.io.inPorts(i).a.bits.data.poke((if (i == 0) 4 else 6).U)
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

  "ADDU should increment the target register and return the new value" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore()) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits(0).poke(10.U)  // fullReg  = 10
      dut.io.progPort.bits(1).poke(5.U)   // emptyReg = 5
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)

      //////////////////////////////////////////////////

      val port = dut.io.inPorts(0)

      port.a.ready.expect(true.B)
      port.a.valid.poke(true.B)
      port.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      port.a.bits.param.poke(ArithmeticDataParam.ADDU)
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

  "Simultaneous ADDU and SUBU on the same register: both complete and final value is correct" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore()) { dut =>
      defaultPokes(dut)
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits(0).poke(0.U)
      dut.io.progPort.bits(1).poke(20.U)  // emptyReg = 20
      dut.clock.step()
      dut.io.progPort.valid.poke(false.B)

      //////////////////////////////////////////////////

      // Port 0: ADDU emptyReg by 8; port 1: SUBU emptyReg by 5 — simultaneously
      dut.io.inPorts(0).a.valid.poke(true.B)
      dut.io.inPorts(0).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(0).a.bits.param.poke(ArithmeticDataParam.ADDU)
      dut.io.inPorts(0).a.bits.address.poke(1.U)
      dut.io.inPorts(0).a.bits.data.poke(8.U)
      dut.io.inPorts(0).a.bits.source.poke(0.U)
      dut.io.inPorts(0).a.bits.size.poke(0.U)
      dut.io.inPorts(0).a.bits.mask.poke(0.U)
      dut.io.inPorts(0).a.bits.corrupt.poke(0.U)

      dut.io.inPorts(1).a.valid.poke(true.B)
      dut.io.inPorts(1).a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.inPorts(1).a.bits.param.poke(ArithmeticDataParam.SUBU)
      dut.io.inPorts(1).a.bits.address.poke(1.U)
      dut.io.inPorts(1).a.bits.data.poke(5.U)
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

  "progPort reprogramming should take effect on subsequent requests" in {
    implicit val c = Configuration.default()
    simulate(new Semaphore()) { dut =>
      defaultPokes(dut)

      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits(0).poke(10.U)
      dut.io.progPort.bits(1).poke(5.U)
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
      dut.io.inPorts(1).d.bits.data.expect(10.U)


      dut.clock.step()


      ////////////////////////////////////////////////// 

      // Reprogram
      dut.io.progPort.valid.poke(true.B)
      dut.io.progPort.bits(0).poke(99.U)
      dut.io.progPort.bits(1).poke(77.U)
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
}
