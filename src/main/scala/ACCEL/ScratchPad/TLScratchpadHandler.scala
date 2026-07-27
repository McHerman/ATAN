
package ATA8

import chisel3._
import chisel3.util._

case class TLScratchConfig(
  read: Boolean,
  write: Boolean,
  atomic: Boolean,
  atomicIn: Int = 1,
  tlConfig: TLBusConfig,
) {
  require(read || write, "Must support at least read or write")
  require((atomic && read && write) || !atomic, "Must support both read and write when using atomic")
}


object State extends ChiselEnum {
  val sIdle, sWriteLock, sWriteReturn, sWriteAck, sReadLock, amoLock, amoRead, amoOp, amoWrite, amoRelease, amoReturn = Value
}

object amoReservationOp {
  val acquire = 0.U(1.W)
  val release = 1.U(1.W)
}

class AtomicReservation(implicit c: MemBusConfig) extends Bundle {
  val address = UInt(32.W)
  val opcode = UInt(1.W)
}


import State._

class TLScratchpadHandler(config: TLScratchConfig)(implicit c: MemBusConfig) extends Module {
  implicit val cm: TLScratchConfig = config

  // Narrow-TL-bus-to-wide-scratchpad byte muxing.
  def scatter(data: UInt, mask: UInt, addr: UInt)(implicit c: MemBusConfig, cm: TLScratchConfig): (Vec[UInt], Vec[Bool], UInt) = {
    val muxOut  = c.dataBusSize / cm.tlConfig.dataBusSize
    val tlBytes = cm.tlConfig.dataBusSize

    if (muxOut == 1) {
      (data.asTypeOf(Vec(c.dataBusSize, UInt(8.W))), VecInit(mask.asBools), addr)
    } else {
      val index     = addr(log2Ceil(c.dataBusSize) - 1, log2Ceil(tlBytes))
      val writeData = (data << (index * (tlBytes * 8).U))(c.dataBusSize * 8 - 1, 0).asTypeOf(Vec(c.dataBusSize, UInt(8.W)))
      val writeMask = VecInit((mask << (index * tlBytes.U))(c.dataBusSize - 1, 0).asBools)

      (writeData, writeMask, addr)
    }
  }

  def gather(data: Vec[UInt], addr: UInt)(implicit c: MemBusConfig, cm: TLScratchConfig): UInt = {
    val muxOut  = c.dataBusSize / cm.tlConfig.dataBusSize
    val tlBytes = cm.tlConfig.dataBusSize

    if (muxOut == 1) {
      data.asUInt
    } else {
      val index = addr(log2Ceil(c.dataBusSize) - 1, log2Ceil(tlBytes))
      (data.asUInt >> (index * (tlBytes * 8).U))(tlBytes * 8 - 1, 0)
    }
  }


  val io = IO(new Bundle {
    val tl   = Flipped(new TilelinkPort(config.tlConfig))
    val wMem = if (config.write) Some(Decoupled(new Writeport(
      new Bundle {
        val writeData = Vec(c.dataBusSize, UInt(8.W))
        val strb      = Vec(c.dataBusSize, Bool())
      }, 16))) else None
    val rMem = if (config.read) Some(new Readport(Vec(c.dataBusSize, UInt(c.arithDataWidth.W)), Some(16))) else None
    val amoReserve = if (config.atomic) Some(Decoupled(new AtomicReservation())) else None
    val reserveIn  = if (config.atomic) Some(Vec(config.atomicIn, Flipped(Valid(UInt(32.W))))) else None
  })

  io.tl.a.ready := false.B
  io.tl.d.valid := false.B
  io.tl.d.bits  := DontCare
  io.wMem.foreach { w => w.valid := false.B; w.bits := DontCare }
  io.rMem.foreach { r => r.request.valid := false.B; r.request.bits := DontCare }
  io.amoReserve.foreach { r => r.valid := false.B; r.bits := DontCare }

  val state        = RegInit(sIdle)
  val beatCnt      = Reg(UInt(24.W))

  // acceptRemaining decouples the amount of beats recieved from the beatCnt which counts beats written
  val acceptRemaining = Reg(UInt(24.W))
  val firstBeat    = RegInit(false.B)
  val reg          = Reg(new TilelinkA(c.tlBus))
  val regValid     = RegInit(false.B)
  val originalData = Reg(UInt((config.tlConfig.dataBusSize * 8).W))
  val amoResult    = Reg(UInt((config.tlConfig.dataBusSize * 8).W))

  switch(state) {
    is(sIdle) {
      io.tl.a.ready := true.B

      switch(io.tl.a.bits.opcode) {
        is(TilelinkOpcodes.Get) {
          if (config.read) {
            when(io.tl.a.fire) {
              reg     := io.tl.a.bits
              beatCnt := io.tl.a.bits.size - config.tlConfig.dataBusSize.U
              state   := sReadLock
            }
          }
        }
        is(TilelinkOpcodes.PutFullData, TilelinkOpcodes.PutPartialData) {
          if (config.write) {
            when(io.tl.a.fire) {
              reg      := io.tl.a.bits
              regValid := io.tl.a.valid
              beatCnt  := io.tl.a.bits.size
              // Beats still to arrive after this one (total minus the one just accepted).
              acceptRemaining := io.tl.a.bits.size - config.tlConfig.dataBusSize.U
              state    := sWriteLock
            }
          }
        }
        is(TilelinkOpcodes.ArithmeticData, TilelinkOpcodes.LogicalData) {
          if (config.atomic) {
            when(io.tl.a.fire) {
              reg     := io.tl.a.bits
              beatCnt := io.tl.a.bits.size - config.tlConfig.dataBusSize.U
              state   := amoLock
            }
          }
        }
      }
    }
  }

  if (config.write) {
    val writeIF = io.wMem.get
    switch(state) {
      is(sWriteLock) {
        // To prevent deadlock, a.ready cannot depend of writeIF.ready
        io.tl.a.ready := (!regValid || writeIF.fire) && acceptRemaining > 0.U

        if (config.atomic) {
          writeIF.valid := regValid && !HelperFunctions.checkAtomic(reg.address, io.reserveIn.get)
        } else {
          writeIF.valid := regValid
        }

        val (data, mask, addr) = scatter(reg.data, reg.mask, reg.address)

        writeIF.bits.addr           := addr
        writeIF.bits.data.writeData := data
        writeIF.bits.data.strb      := mask

        when(io.tl.a.fire) {
          regValid := true.B
        }.elsewhen(writeIF.fire) {
          regValid := false.B
        }

        when(writeIF.fire) {
          when(beatCnt > config.tlConfig.dataBusSize.U) {
            beatCnt := beatCnt - config.tlConfig.dataBusSize.U
          }.otherwise {
            state := sWriteReturn
          }
        }

        when(io.tl.a.fire) {
          reg.data    := io.tl.a.bits.data
          reg.mask    := io.tl.a.bits.mask
          reg.address := reg.address + config.tlConfig.dataBusSize.U
          when(acceptRemaining >= config.tlConfig.dataBusSize.U) {
            acceptRemaining := acceptRemaining - config.tlConfig.dataBusSize.U
          }.otherwise {
            acceptRemaining := 0.U
          }
        }
      }
      is(sWriteReturn) {
        io.tl.d.valid        := true.B
        io.tl.d.bits.opcode  := TilelinkOpcodes.AccessAck
        io.tl.d.bits.param   := 0.U
        io.tl.d.bits.size    := reg.size
        io.tl.d.bits.source  := 0.U
        io.tl.d.bits.sink    := 0.U
        io.tl.d.bits.denied  := 0.U
        io.tl.d.bits.data    := 0.U
        io.tl.d.bits.corrupt := 0.U

        when(io.tl.d.fire) { state := sIdle }
      }
    }
  }

  if (config.read) {
    val readIF = io.rMem.get

    val respValid   = RegInit(false.B)
    val respData    = Reg(readIF.response.bits.readData.cloneType)
    val outstanding = RegInit(false.B)

    val reqAddr = Reg(UInt(c.addrWidth.W))

    switch(state) {
      is(sReadLock) {
        readIF.request.valid         := !outstanding
        readIF.request.bits.addr.get := reg.address
        io.tl.d.valid        := respValid
        io.tl.d.bits.opcode  := TilelinkOpcodes.AccessAckData
        io.tl.d.bits.param   := 0.U
        io.tl.d.bits.size    := reg.size
        io.tl.d.bits.source  := 0.U
        io.tl.d.bits.sink    := 0.U
        io.tl.d.bits.denied  := 0.U
        io.tl.d.bits.data    := gather(respData, reqAddr)
        io.tl.d.bits.corrupt := 0.U

        when(readIF.request.fire) {
          reqAddr     := reg.address
          reg.address := reg.address + config.tlConfig.dataBusSize.U
          outstanding := true.B
        }
        when(readIF.response.valid) {
          respValid := true.B
          respData  := readIF.response.bits.readData
        }
        when(io.tl.d.fire) {
          respValid   := false.B
          outstanding := false.B
          beatCnt := beatCnt - config.tlConfig.dataBusSize.U
          when(beatCnt === 0.U) { state := sIdle }
        }
      }
    }
  }

  if (config.atomic) {
    val readIF     = io.rMem.get
    val writeIF    = io.wMem.get
    val amoReserve = io.amoReserve.get

    switch(state) {
      is(amoLock) {
        amoReserve.valid            := true.B
        amoReserve.bits.address     := reg.address
        amoReserve.bits.opcode      := amoReservationOp.acquire
        when(amoReserve.fire) { state := amoRead }
      }
      is(amoRead) {
        readIF.request.valid         := true.B
        readIF.request.bits.addr.get := reg.address
        when(readIF.request.fire) { state := amoOp }
      }
      is(amoOp) {
        when(readIF.response.valid) {
          val memVal = gather(readIF.response.bits.readData, reg.address)
          val opVal  = reg.data
          originalData := memVal
          val result = WireDefault(opVal)
          switch(reg.opcode) {
            is(TilelinkOpcodes.ArithmeticData) {
              switch(reg.param) {
                is(ArithmeticDataParam.ADD)  { result := memVal + opVal }
                is(ArithmeticDataParam.MIN)  {
                  when(memVal.asSInt < opVal.asSInt) { result := memVal }.otherwise { result := opVal }
                }
                is(ArithmeticDataParam.MAX)  {
                  when(memVal.asSInt > opVal.asSInt) { result := memVal }.otherwise { result := opVal }
                }
                is(ArithmeticDataParam.MINU) {
                  when(memVal < opVal) { result := memVal }.otherwise { result := opVal }
                }
                is(ArithmeticDataParam.MAXU) {
                  when(memVal > opVal) { result := memVal }.otherwise { result := opVal }
                }
              }
            }
            is(TilelinkOpcodes.LogicalData) {
              switch(reg.param) {
                is(0.U) { result := memVal ^ opVal }
                is(1.U) { result := memVal | opVal }
                is(2.U) { result := memVal & opVal }
                is(3.U) { result := opVal }
              }
            }
          }
          amoResult := result
          state     := amoWrite
        }
      }
      is(amoWrite) {
        val fullMask                          = Fill(config.tlConfig.dataBusSize, 1.U(1.W))
        val (writeData, writeMask, writeAddr) = scatter(amoResult, fullMask, reg.address)
        writeIF.bits.addr           := writeAddr
        writeIF.bits.data.writeData := writeData
        writeIF.bits.data.strb      := writeMask
        writeIF.valid               := true.B
        when(writeIF.fire) { state := amoReturn }
      }
      is(amoRelease) {
        amoReserve.valid        := true.B
        amoReserve.bits.address := reg.address
        amoReserve.bits.opcode  := amoReservationOp.release
        when(amoReserve.fire) { state := amoReturn }
      }
      is(amoReturn) {
        io.tl.d.valid        := true.B
        io.tl.d.bits.opcode  := TilelinkOpcodes.AccessAckData
        io.tl.d.bits.param   := 0.U
        io.tl.d.bits.size    := reg.size
        io.tl.d.bits.source  := reg.source
        io.tl.d.bits.sink    := 0.U
        io.tl.d.bits.denied  := 0.U
        val byteOff          = reg.address(log2Ceil(config.tlConfig.dataBusSize) - 1, 0)
        val shifted          = (originalData >> Cat(byteOff, 0.U(3.W)))(config.tlConfig.dataBusSize * 8 - 1, 0)
        io.tl.d.bits.data    := Mux(reg.size < config.tlConfig.dataBusSize.U, shifted, originalData)
        io.tl.d.bits.corrupt := 0.U
        when(io.tl.d.fire) {
          when(beatCnt === 0.U) {
            state := sIdle
          }.otherwise {
            reg.address := reg.address + config.tlConfig.dataBusSize.U
            beatCnt     := beatCnt - config.tlConfig.dataBusSize.U
            state       := amoLock
          }
        }
      }
    }
  }
}
