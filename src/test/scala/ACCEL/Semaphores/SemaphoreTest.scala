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

      dut.clock.step()

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

      dut.clock.step()

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

      dut.clock.step()

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

      dut.clock.step()

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

      dut.clock.step()

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

      dut.clock.step()

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

      dut.clock.step()

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

      dut.clock.step()

      dut.io.inPorts(1).a.valid.poke(false.B)

      dut.io.inPorts(1).d.valid.expect(true.B)
      dut.io.inPorts(1).d.ready.poke(true.B)

      dut.io.inPorts(1).d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.inPorts(1).d.bits.data.expect(77.U)
    }
  }
}
