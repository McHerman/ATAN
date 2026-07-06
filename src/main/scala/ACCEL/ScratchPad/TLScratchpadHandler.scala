
package ATA8

import chisel3._
import chisel3.util._

case class TLScratchConfig(
  read: Boolean,
  write: Boolean,
  atomic: Boolean,
) {
  require(read || write, "Must support at least read or write")
  require((atomic && read && write) || !atomic, "Must support both read and write when using atomic")
}


object State extends ChiselEnum {
  val sIdle, sWriteLock, sWriteAck, sReadLock, amoLock, amoRead, amoOp, amoWrite, amoReturn = Value
}

import State._

class TLScratchpadHandler(config: TLScratchConfig)(implicit c: MemBusConfig) extends Module {
  val io = IO(new Bundle {
    val tl   = Flipped(new TilelinkPort)
    val wMem = if (config.write) Some(Decoupled(new Writeport(
      new Bundle {
        val writeData = Vec(c.dataBusSize, UInt(8.W))
        val strb      = Vec(c.dataBusSize, Bool())
      }, 16))) else None
    val rMem = if (config.read) Some(new Readport(Vec(c.dataBusSize, UInt(c.arithDataWidth.W)), Some(16))) else None
  })

  io.tl.a.ready := false.B
  io.tl.d.valid := false.B
  io.tl.d.bits  := DontCare
  io.wMem.foreach { w => w.valid := false.B; w.bits := DontCare }
  io.rMem.foreach { r => r.request.valid := false.B; r.request.bits := DontCare }

  val state        = RegInit(sIdle)
  val beatCnt      = Reg(UInt(24.W))
  val firstBeat    = RegInit(false.B)
  val reg          = Reg(new TilelinkA())
  val originalData = Reg(UInt((c.dataBusSize * 8).W))
  val amoResult    = Reg(UInt((c.dataBusSize * 8).W))

  when(firstBeat) {
    io.tl.d.valid        := true.B
    io.tl.d.bits.opcode  := TilelinkOpcodes.AccessAck
    io.tl.d.bits.param   := 0.U
    io.tl.d.bits.size    := reg.size
    io.tl.d.bits.source  := 0.U
    io.tl.d.bits.sink    := 0.U
    io.tl.d.bits.denied  := 0.U
    io.tl.d.bits.data    := 0.U
    io.tl.d.bits.corrupt := 0.U
    when(io.tl.d.fire) { firstBeat := false.B }
  }

  switch(state) {
    is(sIdle) {
      if (config.write) { io.tl.a.ready := io.wMem.get.ready }
      if (config.read)  { io.tl.a.ready := true.B }
      switch(io.tl.a.bits.opcode) {
        is(TilelinkOpcodes.Get) {
          if (config.read) {
            when(io.tl.a.fire) {
              reg     := io.tl.a.bits
              beatCnt := io.tl.a.bits.size - c.dataBusSize.U
              state   := sReadLock
            }
          }
        }
        is(TilelinkOpcodes.PutFullData, TilelinkOpcodes.PutPartialData) {
          if (config.write) {
            val writeIF = io.wMem.get
            writeIF.valid                := io.tl.a.valid
            writeIF.bits.addr            := io.tl.a.bits.address
            writeIF.bits.data.writeData  := io.tl.a.bits.data.asTypeOf(Vec(c.dataBusSize, UInt(8.W)))
            writeIF.bits.data.strb       := VecInit(io.tl.a.bits.mask.asBools)
            when(io.tl.a.fire) {
              reg := io.tl.a.bits
              when(io.tl.a.bits.size > c.dataBusSize.U) {
                reg.address := io.tl.a.bits.address + 1.U
                beatCnt     := io.tl.a.bits.size - (2 * c.dataBusSize).U
                state       := sWriteLock
              }.otherwise {
                firstBeat := true.B
              }
            }
          }
        }
        is(TilelinkOpcodes.ArithmeticData, TilelinkOpcodes.LogicalData) {
          if (config.atomic) {
            when(io.tl.a.fire) {
              reg     := io.tl.a.bits
              beatCnt := io.tl.a.bits.size - c.dataBusSize.U
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
        io.tl.a.ready               := writeIF.ready
        writeIF.valid                := io.tl.a.valid
        writeIF.bits.addr            := reg.address
        writeIF.bits.data.writeData  := io.tl.a.bits.data.asTypeOf(Vec(c.dataBusSize, UInt(8.W)))
        writeIF.bits.data.strb       := VecInit(io.tl.a.bits.mask.asBools)
        when(io.tl.a.fire) {
          reg.address := reg.address + 1.U
          when(beatCnt === 0.U) {
            firstBeat := true.B
            state     := sIdle
          }.otherwise {
            beatCnt := beatCnt - c.dataBusSize.U
          }
        }
      }
    }
  }

  if (config.read) {
    val readIF = io.rMem.get
    switch(state) {
      is(sReadLock) {
        readIF.request.valid         := true.B
        readIF.request.bits.addr.get := reg.address
        io.tl.d.valid        := readIF.response.valid
        io.tl.d.bits.opcode  := TilelinkOpcodes.AccessAckData
        io.tl.d.bits.param   := 0.U
        io.tl.d.bits.size    := reg.size
        io.tl.d.bits.source  := 0.U
        io.tl.d.bits.sink    := 0.U
        io.tl.d.bits.denied  := 0.U
        io.tl.d.bits.data    := readIF.response.bits.readData.asUInt
        io.tl.d.bits.corrupt := 0.U
        when(readIF.request.fire) { reg.address := reg.address + 1.U }
        when(io.tl.d.fire) {
          beatCnt := beatCnt - c.dataBusSize.U
          when(beatCnt === 0.U) { state := sIdle }
        }
      }
    }
  }

  if (config.atomic) {
    val readIF  = io.rMem.get
    val writeIF = io.wMem.get
    switch(state) {
      is(amoLock) {
        state := amoRead
      }
      is(amoRead) {
        readIF.request.valid         := true.B
        readIF.request.bits.addr.get := reg.address
        when(readIF.request.fire) { state := amoOp }
      }
      is(amoOp) {
        when(readIF.response.valid) {
          val memVal = readIF.response.bits.readData.asUInt
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
        writeIF.valid               := true.B
        writeIF.bits.addr           := reg.address
        writeIF.bits.data.writeData := amoResult.asTypeOf(Vec(c.dataBusSize, UInt(8.W)))
        writeIF.bits.data.strb      := VecInit(Seq.fill(c.dataBusSize)(true.B))
        when(writeIF.fire) { state := amoReturn }
      }
      is(amoReturn) {
        io.tl.d.valid        := true.B
        io.tl.d.bits.opcode  := TilelinkOpcodes.AccessAckData
        io.tl.d.bits.param   := 0.U
        io.tl.d.bits.size    := reg.size
        io.tl.d.bits.source  := reg.source
        io.tl.d.bits.sink    := 0.U
        io.tl.d.bits.denied  := 0.U
        io.tl.d.bits.data    := originalData
        io.tl.d.bits.corrupt := 0.U
        when(io.tl.d.fire) {
          when(beatCnt === 0.U) {
            state := sIdle
          }.otherwise {
            reg.address := reg.address + 1.U
            beatCnt     := beatCnt - c.dataBusSize.U
            state       := amoLock
          }
        }
      }
    }
  }
}
