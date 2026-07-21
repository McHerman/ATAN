package ATA8

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class TLScratchpadHandlerTest extends AnyFreeSpec with Matchers with ChiselSim {

  val maxCycles = 500
  implicit val c: Configuration = Configuration.default()

  def waitFor(step: () => Unit)(cond: => Boolean, msg: String): Unit = {
    var cycles = 0
    while (!cond) {
      require(cycles < maxCycles, s"Timeout: $msg (after $maxCycles cycles)")
      step()
      cycles += 1
    }
  }

  "single-beat PutFullData" in {
    simulate(new TLScratchpadHandler(TLScratchConfig(read = false, write = true, atomic = false, tlConfig = c.tlBus))) { dut =>
      /*
      dut.io.tl.a.valid.poke(false.B)
      dut.io.tl.d.ready.poke(false.B)
      dut.io.wMem.get.ready.poke(false.B)
      dut.io.rMem.get.request.ready.poke(false.B)
      dut.io.rMem.get.response.valid.poke(false.B)
      */

      dut.io.tl.a.bits.opcode.poke(TilelinkOpcodes.PutFullData)
      dut.io.tl.a.bits.param.poke(0.U)
      dut.io.tl.a.bits.size.poke(8.U)
      dut.io.tl.a.bits.source.poke(0.U)
      dut.io.tl.a.bits.address.poke(0x0010.U)
      dut.io.tl.a.bits.mask.poke(0xFF.U)
      dut.io.tl.a.bits.data.poke(BigInt("0102030405060708", 16).U)
      dut.io.tl.a.bits.corrupt.poke(0.U)
      dut.io.tl.a.valid.poke(true.B)
      dut.io.wMem.get.ready.poke(true.B)

      dut.clock.step()

      dut.io.tl.a.ready.expect(false.B)
      dut.io.wMem.get.valid.expect(true.B)
      dut.io.wMem.get.bits.addr.expect(0x0010.U)

      dut.clock.step()

      dut.io.tl.a.valid.poke(false.B)
      dut.io.tl.d.ready.poke(true.B)
      dut.io.tl.d.valid.expect(true.B)
      dut.io.tl.d.bits.opcode.expect(TilelinkOpcodes.AccessAck)
      dut.io.tl.d.bits.size.expect(8.U)

      dut.clock.step()
      dut.io.tl.d.valid.expect(false.B)
    }
  }

  "multi-beat PutFullData write burst" in {
    simulate(new TLScratchpadHandler(TLScratchConfig(read = false, write = true, atomic = false, tlConfig = c.tlBus))) { dut =>
      /*
      dut.io.tl.a.valid.poke(false.B)
      dut.io.tl.d.ready.poke(false.B)
      dut.io.wMem.get.ready.poke(false.B)
      dut.io.rMem.get.request.ready.poke(false.B)
      dut.io.rMem.get.response.valid.poke(false.B)
      */

      dut.io.tl.a.bits.opcode.poke(TilelinkOpcodes.PutFullData)
      dut.io.tl.a.bits.param.poke(0.U)
      dut.io.tl.a.bits.size.poke(16.U)
      dut.io.tl.a.bits.source.poke(0.U)
      dut.io.tl.a.bits.address.poke(0x0020.U)
      dut.io.tl.a.bits.mask.poke(0xFF.U)
      dut.io.tl.a.bits.data.poke(BigInt("AAAAAAAAAAAAAAAA", 16).U)
      dut.io.tl.a.bits.corrupt.poke(0.U)
      dut.io.tl.a.valid.poke(true.B)
      dut.io.wMem.get.ready.poke(true.B)

      dut.clock.step()

      dut.io.tl.a.ready.expect(true.B)
      dut.io.wMem.get.valid.expect(true.B)
      dut.io.wMem.get.bits.addr.expect(0x0020.U)

      dut.clock.step()

      // sWriteLock: beat 1 with addrReg=0x0028
      dut.io.tl.a.bits.data.poke(BigInt("BBBBBBBBBBBBBBBB", 16).U)

      dut.io.tl.a.ready.expect(false.B)
      dut.io.wMem.get.valid.expect(true.B)
      dut.io.wMem.get.bits.addr.expect(0x0028.U)

      dut.clock.step()

      dut.io.tl.a.valid.poke(false.B)
      dut.io.tl.d.ready.poke(true.B)
      dut.io.tl.d.valid.expect(true.B)
      dut.io.tl.d.bits.opcode.expect(TilelinkOpcodes.AccessAck)
      dut.io.tl.d.bits.size.expect(16.U)

      dut.clock.step()
      dut.io.tl.d.valid.expect(false.B)
    }
  }

  "single-beat Get read" in {
    simulate(new TLScratchpadHandler(TLScratchConfig(read = true, write = false, atomic = false, tlConfig = c.tlBus))) { dut =>
      dut.io.tl.a.valid.poke(false.B)
      dut.io.tl.d.ready.poke(false.B)
      //dut.io.wMem.get.ready.poke(false.B)
      dut.io.rMem.get.request.ready.poke(false.B)
      dut.io.rMem.get.response.valid.poke(false.B)

      dut.io.tl.a.bits.opcode.poke(TilelinkOpcodes.Get)
      dut.io.tl.a.bits.param.poke(0.U)
      dut.io.tl.a.bits.size.poke(8.U)
      dut.io.tl.a.bits.source.poke(0.U)
      dut.io.tl.a.bits.address.poke(0x0030.U)
      dut.io.tl.a.bits.mask.poke(0xFF.U)
      dut.io.tl.a.bits.data.poke(0.U)
      dut.io.tl.a.bits.corrupt.poke(0.U)
      dut.io.tl.a.valid.poke(true.B)

      dut.io.tl.a.ready.expect(true.B)
      dut.clock.step()

      dut.io.tl.a.valid.poke(false.B)

      dut.io.rMem.get.request.valid.expect(true.B)
      dut.io.rMem.get.request.bits.addr.get.expect(0x0030.U)

      for (i <- 0 until c.dataBusSize) {
        dut.io.rMem.get.response.bits.readData(i).poke((i + 1).U)
      }
      dut.io.rMem.get.response.valid.poke(true.B)
      dut.io.rMem.get.request.ready.poke(true.B)
      dut.io.tl.d.ready.poke(true.B)

      dut.io.tl.d.valid.expect(true.B)
      dut.io.tl.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.tl.d.bits.size.expect(8.U)

      dut.clock.step()

      dut.io.rMem.get.response.valid.poke(false.B)
      dut.io.tl.d.valid.expect(false.B)
    }
  }

  "multi-beat Get read burst" in {
    simulate(new TLScratchpadHandler(TLScratchConfig(read = true, write = false, atomic = false, tlConfig = c.tlBus))) { dut =>
      dut.io.tl.a.valid.poke(false.B)
      dut.io.tl.d.ready.poke(false.B)
      //dut.io.wMem.get.ready.poke(false.B)
      dut.io.rMem.get.request.ready.poke(false.B)
      dut.io.rMem.get.response.valid.poke(false.B)

      dut.io.tl.a.bits.opcode.poke(TilelinkOpcodes.Get)
      dut.io.tl.a.bits.param.poke(0.U)
      dut.io.tl.a.bits.size.poke(16.U)
      dut.io.tl.a.bits.source.poke(0.U)
      dut.io.tl.a.bits.address.poke(0x0040.U)
      dut.io.tl.a.bits.mask.poke(0xFF.U)
      dut.io.tl.a.bits.data.poke(0.U)
      dut.io.tl.a.bits.corrupt.poke(0.U)
      dut.io.tl.a.valid.poke(true.B)

      dut.io.tl.a.ready.expect(true.B)
      dut.clock.step()

      dut.io.tl.a.valid.poke(false.B)
      dut.io.rMem.get.request.ready.poke(true.B)
      dut.io.tl.d.ready.poke(true.B)

      // beat 0: addr=0x0040
      dut.io.rMem.get.request.valid.expect(true.B)
      dut.io.rMem.get.request.bits.addr.get.expect(0x0040.U)
      for (i <- 0 until c.dataBusSize) {
        dut.io.rMem.get.response.bits.readData(i).poke(0x0A.U)
      }
      dut.io.rMem.get.response.valid.poke(true.B)

      dut.io.tl.d.valid.expect(true.B)
      dut.io.tl.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)

      dut.clock.step()

      // beat 1: addr=0x0048
      dut.io.rMem.get.request.valid.expect(true.B)
      dut.io.rMem.get.request.bits.addr.get.expect(0x0048.U)
      for (i <- 0 until c.dataBusSize) {
        dut.io.rMem.get.response.bits.readData(i).poke(0x0B.U)
      }

      dut.io.tl.d.valid.expect(true.B)
      dut.io.tl.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)

      dut.clock.step()

      dut.io.rMem.get.response.valid.poke(false.B)
      dut.io.tl.d.valid.expect(false.B)
    }
  }

  "AMO ADD" in {
    simulate(new TLScratchpadHandler(TLScratchConfig(read = true, write = true, atomic = true, tlConfig = c.tlBus))) { dut =>
      dut.io.tl.a.valid.poke(false.B)
      dut.io.tl.d.ready.poke(false.B)
      dut.io.wMem.get.ready.poke(false.B)
      dut.io.rMem.get.request.ready.poke(false.B)
      dut.io.rMem.get.response.valid.poke(false.B)

      dut.io.tl.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.tl.a.bits.param.poke(ArithmeticDataParam.ADD)
      dut.io.tl.a.bits.size.poke(8.U)
      dut.io.tl.a.bits.source.poke(1.U)
      dut.io.tl.a.bits.address.poke(0x0050.U)
      dut.io.tl.a.bits.mask.poke(0xFF.U)
      dut.io.tl.a.bits.data.poke(42.U)
      dut.io.tl.a.bits.corrupt.poke(0.U)
      dut.io.tl.a.valid.poke(true.B)

      dut.io.tl.a.ready.expect(true.B)
      dut.clock.step()   // sIdle: a.fire → amoLock, reg captured

      dut.io.tl.a.valid.poke(false.B)

      dut.io.amoReserve.get.valid.expect(true.B)
      dut.io.amoReserve.get.bits.address.expect(0x0050.U)

      dut.io.amoReserve.get.ready.poke(true.B)

      dut.clock.step()   // amoLock → amoRead

      dut.io.rMem.get.request.valid.expect(true.B)
      dut.io.rMem.get.request.bits.addr.get.expect(0x0050.U)
      dut.io.rMem.get.request.ready.poke(true.B)
      dut.clock.step()   // amoRead: request fires → amoOp

      dut.io.rMem.get.request.ready.poke(false.B)
      dut.io.rMem.get.response.bits.readData(0).poke(100.U)
      for (i <- 1 until c.dataBusSize) {
        dut.io.rMem.get.response.bits.readData(i).poke(0.U)
      }
      dut.io.rMem.get.response.valid.poke(true.B)
      dut.clock.step()   // amoOp: memVal=100 + opVal=42 = 142, originalData=100 → amoWrite

      dut.io.rMem.get.response.valid.poke(false.B)
      dut.io.wMem.get.valid.expect(true.B)
      dut.io.wMem.get.bits.addr.expect(0x0050.U)
      dut.io.wMem.get.bits.data.writeData(0).expect(142.U)
      for (i <- 1 until c.dataBusSize) {
        dut.io.wMem.get.bits.data.writeData(i).expect(0.U)
      }
      dut.io.wMem.get.ready.poke(true.B)
      dut.clock.step()   // amoWrite: fire → amoReturn

      dut.io.wMem.get.ready.poke(false.B)
      dut.io.tl.d.valid.expect(true.B)
      dut.io.tl.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.tl.d.bits.size.expect(8.U)
      dut.io.tl.d.bits.source.expect(1.U)
      dut.io.tl.d.bits.data.expect(100.U)
      dut.io.tl.d.ready.poke(true.B)
      dut.clock.step()   // amoReturn: d.fire → sIdle

      dut.io.tl.d.valid.expect(false.B)
    }
  }

  "AMO SWAP" in {
    simulate(new TLScratchpadHandler(TLScratchConfig(read = true, write = true, atomic = true, tlConfig = c.tlBus))) { dut =>
      dut.io.tl.a.valid.poke(false.B)
      dut.io.tl.d.ready.poke(false.B)
      dut.io.wMem.get.ready.poke(false.B)
      dut.io.rMem.get.request.ready.poke(false.B)
      dut.io.rMem.get.response.valid.poke(false.B)

      dut.io.tl.a.bits.opcode.poke(TilelinkOpcodes.LogicalData)
      dut.io.tl.a.bits.param.poke(3.U)  // SWAP
      dut.io.tl.a.bits.size.poke(8.U)
      dut.io.tl.a.bits.source.poke(2.U)
      dut.io.tl.a.bits.address.poke(0x0060.U)
      dut.io.tl.a.bits.mask.poke(0xFF.U)
      dut.io.tl.a.bits.data.poke(0xBEEF.U)
      dut.io.tl.a.bits.corrupt.poke(0.U)
      dut.io.tl.a.valid.poke(true.B)

      dut.io.tl.a.ready.expect(true.B)
      dut.clock.step()   // sIdle: a.fire → amoLock

      dut.io.tl.a.valid.poke(false.B)

      dut.io.amoReserve.get.valid.expect(true.B)
      dut.io.amoReserve.get.bits.address.expect(0x0060.U)

      dut.io.amoReserve.get.ready.poke(true.B)

      dut.clock.step()   // amoLock → amoRead

      dut.io.amoReserve.get.ready.poke(false.B)

      dut.io.rMem.get.request.valid.expect(true.B)
      dut.io.rMem.get.request.bits.addr.get.expect(0x0060.U)
      dut.io.rMem.get.request.ready.poke(true.B)
      dut.clock.step()   // amoRead: request fires → amoOp

      dut.io.rMem.get.request.ready.poke(false.B)
      dut.io.rMem.get.response.bits.readData(0).poke(0xAD.U)
      dut.io.rMem.get.response.bits.readData(1).poke(0xDE.U)
      for (i <- 2 until c.dataBusSize) {
        dut.io.rMem.get.response.bits.readData(i).poke(0.U)
      }
      dut.io.rMem.get.response.valid.poke(true.B)
      dut.clock.step()   // amoOp: result=opVal=0xBEEF (SWAP), originalData=0xDEAD → amoWrite

      dut.io.rMem.get.response.valid.poke(false.B)
      dut.io.wMem.get.valid.expect(true.B)
      dut.io.wMem.get.bits.addr.expect(0x0060.U)
      dut.io.wMem.get.bits.data.writeData(0).expect(0xEF.U)
      dut.io.wMem.get.bits.data.writeData(1).expect(0xBE.U)
      for (i <- 2 until c.dataBusSize) {
        dut.io.wMem.get.bits.data.writeData(i).expect(0.U)
      }
      dut.io.wMem.get.ready.poke(true.B)
      dut.clock.step()   // amoWrite: fire → amoReturn

      dut.io.wMem.get.ready.poke(false.B)
      dut.io.tl.d.valid.expect(true.B)
      dut.io.tl.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.tl.d.bits.data.expect(0xDEAD.U)
      dut.io.tl.d.ready.poke(true.B)
      dut.clock.step()   // amoReturn: d.fire → sIdle

      dut.io.tl.d.valid.expect(false.B)
    }
  }

  "multi-beat AMO ADD burst" in {
    simulate(new TLScratchpadHandler(TLScratchConfig(read = true, write = true, atomic = true, tlConfig = c.tlBus))) { dut =>
      dut.io.tl.a.valid.poke(false.B)
      dut.io.tl.d.ready.poke(false.B)
      dut.io.wMem.get.ready.poke(false.B)
      dut.io.rMem.get.request.ready.poke(false.B)
      dut.io.rMem.get.response.valid.poke(false.B)

      dut.io.tl.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.tl.a.bits.param.poke(ArithmeticDataParam.ADD)
      dut.io.tl.a.bits.size.poke(16.U)
      dut.io.tl.a.bits.source.poke(0.U)
      dut.io.tl.a.bits.address.poke(0x0070.U)
      dut.io.tl.a.bits.mask.poke(0xFF.U)
      dut.io.tl.a.bits.data.poke(10.U)
      dut.io.tl.a.bits.corrupt.poke(0.U)
      dut.io.tl.a.valid.poke(true.B)

      dut.io.tl.a.ready.expect(true.B)
      dut.clock.step()   // sIdle: a.fire → amoLock, beatCnt=8

      dut.io.tl.a.valid.poke(false.B)

      dut.io.amoReserve.get.valid.expect(true.B)
      dut.io.amoReserve.get.bits.address.expect(0x0070.U)

      dut.io.amoReserve.get.ready.poke(true.B)

      dut.clock.step()   // amoLock → amoRead

      dut.io.amoReserve.get.ready.poke(false.B)

      // beat 0: addr=0x0070, memVal=100, result=110
      dut.io.rMem.get.request.valid.expect(true.B)
      dut.io.rMem.get.request.bits.addr.get.expect(0x0070.U)
      dut.io.rMem.get.request.ready.poke(true.B)
      dut.clock.step()   // amoRead: request fires → amoOp

      dut.io.rMem.get.request.ready.poke(false.B)
      dut.io.rMem.get.response.bits.readData(0).poke(100.U)
      for (i <- 1 until c.dataBusSize) { dut.io.rMem.get.response.bits.readData(i).poke(0.U) }
      dut.io.rMem.get.response.valid.poke(true.B)
      dut.clock.step()   // amoOp: 100+10=110 → amoWrite

      dut.io.rMem.get.response.valid.poke(false.B)
      dut.io.wMem.get.valid.expect(true.B)
      dut.io.wMem.get.bits.addr.expect(0x0070.U)
      dut.io.wMem.get.bits.data.writeData(0).expect(110.U)
      for (i <- 1 until c.dataBusSize) { dut.io.wMem.get.bits.data.writeData(i).expect(0.U) }
      dut.io.wMem.get.ready.poke(true.B)
      dut.clock.step()   // amoWrite: fire → amoReturn

      dut.io.wMem.get.ready.poke(false.B)
      dut.io.tl.d.valid.expect(true.B)
      dut.io.tl.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.tl.d.bits.data.expect(100.U)
      dut.io.tl.d.ready.poke(true.B)
      dut.clock.step()   // amoReturn: d.fire, beatCnt>0 → amoLock, addr=0x0078, beatCnt=0

      // beat 1: addr=0x0078, memVal=200, result=210
      dut.io.tl.a.valid.poke(false.B)

      dut.io.amoReserve.get.valid.expect(true.B)
      dut.io.amoReserve.get.bits.address.expect(0x0078.U)

      dut.io.amoReserve.get.ready.poke(true.B)

      dut.clock.step()   // amoLock → amoRead

      dut.io.amoReserve.get.ready.poke(false.B)


      dut.io.rMem.get.request.valid.expect(true.B)
      dut.io.rMem.get.request.bits.addr.get.expect(0x0078.U)
      dut.io.rMem.get.request.ready.poke(true.B)
      dut.clock.step()   // amoRead: request fires → amoOp

      dut.io.rMem.get.request.ready.poke(false.B)
      dut.io.rMem.get.response.bits.readData(0).poke(200.U)
      for (i <- 1 until c.dataBusSize) { dut.io.rMem.get.response.bits.readData(i).poke(0.U) }
      dut.io.rMem.get.response.valid.poke(true.B)
      dut.clock.step()   // amoOp: 200+10=210 → amoWrite

      dut.io.rMem.get.response.valid.poke(false.B)
      dut.io.wMem.get.valid.expect(true.B)
      dut.io.wMem.get.bits.addr.expect(0x0078.U)
      dut.io.wMem.get.bits.data.writeData(0).expect(210.U)
      for (i <- 1 until c.dataBusSize) { dut.io.wMem.get.bits.data.writeData(i).expect(0.U) }
      dut.io.wMem.get.ready.poke(true.B)
      dut.clock.step()   // amoWrite: fire → amoReturn (addr=0x0078)

      dut.io.wMem.get.ready.poke(false.B)
      dut.io.tl.d.valid.expect(true.B)
      dut.io.tl.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.tl.d.bits.data.expect(200.U)
      dut.io.tl.d.ready.poke(true.B)
      dut.clock.step()   // amoReturn: d.fire, beatCnt=0 → sIdle

      dut.io.tl.d.valid.expect(false.B)
    }
  }

  // Shared config for all 32→128-bit wide-bus tests.
  val wideC: Configuration = Configuration(bus = BusParams(dataBusSize = 16))
  val wideTlCfg: TLBusConfig = TLBusConfig(dataBusSize = 4, addrWidth = wideC.addrWidth, sourceWidth = wideC.sourceWidth)

  "32-bit TL write into 128-bit memory bus (scatter)" in {
    implicit val _c: Configuration = wideC

    simulate(new TLScratchpadHandler(
      TLScratchConfig(read = false, write = true, atomic = false, tlConfig = wideTlCfg)
    )(wideC)) { dut =>
      // Write 0xDEADBEEF at TL byte address 0x0004.
      // muxOut=4; index=addr[3:2]=1 → slot 1 in the 128-bit word.
      // Scatter: addr=0x0004>>4=0, bytes[4..7]=EF BE AD DE, strobe[4..7]=true.
      dut.io.tl.a.bits.opcode.poke(TilelinkOpcodes.PutFullData)
      dut.io.tl.a.bits.param.poke(0.U)
      dut.io.tl.a.bits.size.poke(4.U)
      dut.io.tl.a.bits.source.poke(0.U)
      dut.io.tl.a.bits.address.poke(0x0004.U)
      dut.io.tl.a.bits.mask.poke(0xF.U)
      dut.io.tl.a.bits.data.poke(BigInt("DEADBEEF", 16).U)
      dut.io.tl.a.bits.corrupt.poke(0.U)
      dut.io.tl.a.valid.poke(true.B)
      dut.io.wMem.get.ready.poke(true.B)

      dut.clock.step()

      dut.io.wMem.get.valid.expect(true.B)
      dut.io.wMem.get.bits.addr.expect(0.U)
      for (i <- 0 until 4)  dut.io.wMem.get.bits.data.strb(i).expect(false.B)
      dut.io.wMem.get.bits.data.writeData(4).expect(0xEF.U)
      dut.io.wMem.get.bits.data.writeData(5).expect(0xBE.U)
      dut.io.wMem.get.bits.data.writeData(6).expect(0xAD.U)
      dut.io.wMem.get.bits.data.writeData(7).expect(0xDE.U)
      for (i <- 4 until 8)  dut.io.wMem.get.bits.data.strb(i).expect(true.B)
      for (i <- 8 until 16) dut.io.wMem.get.bits.data.strb(i).expect(false.B)

      dut.clock.step()

      dut.io.tl.a.valid.poke(false.B)
      dut.io.tl.d.ready.poke(true.B)
      dut.io.tl.d.valid.expect(true.B)
      dut.io.tl.d.bits.opcode.expect(TilelinkOpcodes.AccessAck)

      dut.clock.step()
      dut.io.tl.d.valid.expect(false.B)
    }
  }

  "32-bit TL read from 128-bit memory bus (gather)" in {
    implicit val _c: Configuration = wideC

    simulate(new TLScratchpadHandler(
      TLScratchConfig(read = true, write = false, atomic = false, tlConfig = wideTlCfg)
    )(wideC)) { dut =>
      // Get 4 bytes at TL byte address 0x0004 (slot 1 of the 128-bit word).
      // gather: index=addr[3:2]=1 → extracts bytes[4..7] from the 16-byte response.
      // readData(i)=i+1 → bytes[4..7]=[5,6,7,8] → 0x08070605.
      // addrOld needs one cycle to capture 0x0004, so the request is held one cycle
      // before the memory response is presented.
      dut.io.rMem.get.response.valid.poke(false.B)
      dut.io.rMem.get.request.ready.poke(false.B)

      dut.io.tl.a.bits.opcode.poke(TilelinkOpcodes.Get)
      dut.io.tl.a.bits.param.poke(0.U)
      dut.io.tl.a.bits.size.poke(4.U)
      dut.io.tl.a.bits.source.poke(0.U)
      dut.io.tl.a.bits.address.poke(0x0004.U)
      dut.io.tl.a.bits.mask.poke(0xF.U)
      dut.io.tl.a.bits.data.poke(0.U)
      dut.io.tl.a.bits.corrupt.poke(0.U)
      dut.io.tl.a.valid.poke(true.B)

      dut.io.tl.a.ready.expect(true.B)
      dut.clock.step()   // sIdle → sReadLock; reg.address=0x0004, addrOld still 0

      dut.io.tl.a.valid.poke(false.B)
      dut.io.rMem.get.request.valid.expect(true.B)
      dut.io.rMem.get.request.bits.addr.get.expect(0x0004.U)
      // Hold request not-ready for one cycle so addrOld can latch 0x0004.
      dut.clock.step()   // addrOld = 0x0004

      dut.io.rMem.get.request.ready.poke(true.B)
      for (i <- 0 until 16) dut.io.rMem.get.response.bits.readData(i).poke((i + 1).U)
      dut.io.rMem.get.response.valid.poke(true.B)
      dut.io.tl.d.ready.poke(true.B)

      dut.io.tl.d.valid.expect(true.B)
      dut.io.tl.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.tl.d.bits.data.expect(BigInt("08070605", 16).U)

      dut.clock.step()
      dut.io.rMem.get.response.valid.poke(false.B)
      dut.io.tl.d.valid.expect(false.B)
    }
  }

  "32-bit TL AMO ADD through 128-bit memory bus (scatter+gather)" in {
    implicit val _c: Configuration = wideC

    simulate(new TLScratchpadHandler(
      TLScratchConfig(read = true, write = true, atomic = true, tlConfig = wideTlCfg)
    )(wideC)) { dut =>
      // AMO ADD at TL byte address 0x0008 (slot 2 of the 128-bit word), adding 10.
      // amoRead scatter: memAddr = 0x0008>>4 = 0.
      // gather: index=2 → extracts bytes[8..11]. readData(8)=100, rest=0 → memVal=100.
      // result = 100+10 = 110; originalData = 100.
      // amoWrite scatter: writeAddr=0, bytes[8..11]=0x6E/0/0/0, strobe[8..11]=true.
      // amoReturn: byteOff=0, d.data = originalData = 100.
      dut.io.tl.a.valid.poke(false.B)
      dut.io.tl.d.ready.poke(false.B)
      dut.io.wMem.get.ready.poke(false.B)
      dut.io.rMem.get.request.ready.poke(false.B)
      dut.io.rMem.get.response.valid.poke(false.B)

      dut.io.tl.a.bits.opcode.poke(TilelinkOpcodes.ArithmeticData)
      dut.io.tl.a.bits.param.poke(ArithmeticDataParam.ADD)
      dut.io.tl.a.bits.size.poke(4.U)
      dut.io.tl.a.bits.source.poke(3.U)
      dut.io.tl.a.bits.address.poke(0x0008.U)
      dut.io.tl.a.bits.mask.poke(0xF.U)
      dut.io.tl.a.bits.data.poke(10.U)
      dut.io.tl.a.bits.corrupt.poke(0.U)
      dut.io.tl.a.valid.poke(true.B)

      dut.io.tl.a.ready.expect(true.B)
      dut.clock.step()   // sIdle: a.fire → amoLock

      dut.io.tl.a.valid.poke(false.B)
      dut.io.amoReserve.get.valid.expect(true.B)
      dut.io.amoReserve.get.bits.address.expect(0x0008.U)
      dut.io.amoReserve.get.ready.poke(true.B)
      dut.clock.step()   // amoLock → amoRead

      // amoRead: scatter address = 0x0008>>4 = 0
      dut.io.amoReserve.get.ready.poke(false.B)
      dut.io.rMem.get.request.valid.expect(true.B)
      dut.io.rMem.get.request.bits.addr.get.expect(0.U)
      dut.io.rMem.get.request.ready.poke(true.B)
      dut.clock.step()   // amoRead: request fires → amoOp

      // amoOp: gather(response, reg.address=0x0008): index=2 → bytes[8..11]
      dut.io.rMem.get.request.ready.poke(false.B)
      for (i <- 0 until 16) dut.io.rMem.get.response.bits.readData(i).poke(0.U)
      dut.io.rMem.get.response.bits.readData(8).poke(100.U)
      dut.io.rMem.get.response.valid.poke(true.B)
      dut.clock.step()   // amoOp: memVal=100, result=110, originalData=100 → amoWrite

      // amoWrite: scatter(110, 0xF, 0x0008): writeAddr=0, bytes[8..11]=0x6E/0/0/0
      dut.io.rMem.get.response.valid.poke(false.B)
      dut.io.wMem.get.valid.expect(true.B)
      dut.io.wMem.get.bits.addr.expect(0.U)
      dut.io.wMem.get.bits.data.writeData(8).expect(110.U)
      for (i <- 9 until 12) dut.io.wMem.get.bits.data.writeData(i).expect(0.U)
      for (i <- 0 until 8)  dut.io.wMem.get.bits.data.strb(i).expect(false.B)
      for (i <- 8 until 12) dut.io.wMem.get.bits.data.strb(i).expect(true.B)
      for (i <- 12 until 16) dut.io.wMem.get.bits.data.strb(i).expect(false.B)
      dut.io.wMem.get.ready.poke(true.B)
      dut.clock.step()   // amoWrite: fire → amoReturn

      dut.io.wMem.get.ready.poke(false.B)
      dut.io.tl.d.valid.expect(true.B)
      dut.io.tl.d.bits.opcode.expect(TilelinkOpcodes.AccessAckData)
      dut.io.tl.d.bits.size.expect(4.U)
      dut.io.tl.d.bits.source.expect(3.U)
      dut.io.tl.d.bits.data.expect(100.U)
      dut.io.tl.d.ready.poke(true.B)
      dut.clock.step()   // amoReturn: d.fire → sIdle

      dut.io.tl.d.valid.expect(false.B)
    }
  }
}
