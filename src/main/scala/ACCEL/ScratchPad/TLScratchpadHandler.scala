
package ATA8

import chisel3._
import chisel3.util._

case class TLScratchConfig(
  read: Boolean,
  write: Boolean,
  atomic: Boolean,
  atomicIn: Int = 1,
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

// Widebanks overrides masking to 32 bits

class TLScratchpadHandler(config: TLScratchConfig, wideBanks: Option[Int] = None)(implicit c: MemBusConfig) extends Module {

  private val groupLen  = c.dataBusSize / 4                    // 32-bit banks spanned by one TL beat
  private val portElems = wideBanks.getOrElse(c.dataBusSize)   // Vec length of wMem/rMem's data
  private val portWidth = wideBanks.map(_ => 32).getOrElse(8)  // bits per element of wMem/rMem's data

  wideBanks.foreach { n =>
    require(c.dataBusSize % 4 == 0, "c.dataBusSize must be a multiple of 4 (32-bit sub-banks) to use wideBanks")
    require(n * 4 >= c.dataBusSize, "wideBanks*4 must be >= the TileLink beat size")
    require((n * 4) % c.dataBusSize == 0, "wideBanks*4 must be a multiple of the TileLink beat size")
  }

  val io = IO(new Bundle {
    val tl   = Flipped(new TilelinkPort(c.tlBus))
    val wMem = if (config.write) Some(Decoupled(new Writeport(
      new Bundle {
        val writeData = Vec(portElems, UInt(portWidth.W))
        val strb      = Vec(portElems, Bool())
      }, 16))) else None
    val rMem = if (config.read) Some(new Readport(Vec(portElems, UInt(wideBanks.map(_ => 32).getOrElse(c.arithDataWidth).W)), Some(16))) else None
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
  val firstBeat    = RegInit(false.B)
  val reg          = Reg(new TilelinkA(c.tlBus))
  val regValid     = RegInit(false.B)
  val originalData = Reg(UInt((c.dataBusSize * 8).W))
  val amoResult    = Reg(UInt((c.dataBusSize * 8).W))

  // Number of groupLen-bank groups per row. When this is 1 — e.g.
  // wideBanks == groupLen, so the "wide" row is exactly one beat wide —
  // there's only one possible group, and no address bits select it; a
  // bit-slice extraction would be a degenerate (and illegal) zero-width
  // range, so that case is a constant instead.
  private val nGroups = wideBanks.map(_ * 4 / c.dataBusSize).getOrElse(1)

  // Row address (into the wide memory) and which group of `groupLen` banks
  // a beat at `addr` lands on. Only used when wideBanks is set.
  private def rowAddr(addr: UInt): UInt  = addr >> log2Ceil(wideBanks.getOrElse(1) * 4)
  private def groupIdx(addr: UInt): UInt =
    if (nGroups <= 1) 0.U
    else addr(log2Ceil(wideBanks.getOrElse(1) * 4) - 1, log2Ceil(c.dataBusSize))

  // Scatter a c.dataBusSize-wide word into the `groupLen` banks of the
  // group addressed by `addr`. `words` is simply tiled across every group
  // (each bank's data is well-defined either way); `strb` alone picks out
  // which one group is actually written.
  private def scatter(data: UInt, addr: UInt): (Vec[UInt], Vec[Bool]) = {
    val words = data.asTypeOf(Vec(groupLen, UInt(32.W)))
    val gi    = groupIdx(addr)
    val dataVec = VecInit(Seq.tabulate(wideBanks.get)(b => words(b % groupLen)))
    val strbVec = VecInit(Seq.tabulate(wideBanks.get)(b => (b / groupLen).U === gi))
    (dataVec, strbVec)
  }

  // Gather the c.dataBusSize-wide word out of group `gi` of a
  // `wideBanks`-wide read response.
  private def gatherByGroup(row: Vec[UInt], gi: UInt): UInt = {
    val groups = VecInit((0 until nGroups).map { g =>
      VecInit((0 until groupLen).map(i => row(g * groupLen + i))).asUInt
    })
    groups(gi)
  }

  // Convenience form for callers (AMO) whose `addr` is still the request's
  // original address when the response is consumed.
  private def gather(row: Vec[UInt], addr: UInt): UInt = gatherByGroup(row, groupIdx(addr))

  switch(state) {
    is(sIdle) {

      io.tl.a.ready := true.B

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
            when(io.tl.a.fire) {
              reg := io.tl.a.bits
              regValid := io.tl.a.valid
              beatCnt := io.tl.a.bits.size

              state := sWriteLock
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
      is(sWriteLock){


        io.tl.a.ready := writeIF.ready && beatCnt > c.dataBusSize.U

        // Check for reservation
        if(config.atomic){
          writeIF.valid := regValid && !HelperFunctions.checkAtomic(reg.address, io.reserveIn.get)
        } else {
          writeIF.valid := regValid
        }

        if (wideBanks.isEmpty) {
          writeIF.bits.addr           := reg.address
          writeIF.bits.data.writeData := reg.data.asTypeOf(Vec(c.dataBusSize, UInt(8.W)))
          writeIF.bits.data.strb      := VecInit(reg.mask.asBools)
        } else {
          val (dataVec, strbVec) = scatter(reg.data, reg.address)
          writeIF.bits.addr           := rowAddr(reg.address)
          writeIF.bits.data.writeData := dataVec
          writeIF.bits.data.strb      := strbVec
        }

        regValid := io.tl.a.fire

        when(beatCnt > c.dataBusSize.U && writeIF.fire) {
          beatCnt := beatCnt - c.dataBusSize.U
        }.otherwise {
          state := sWriteReturn
        }

        when(io.tl.a.fire) {
          reg.data := io.tl.a.bits.data
          reg.mask := io.tl.a.bits.mask
          reg.address := reg.address + c.dataBusSize.U
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

        when(io.tl.d.fire) {
          state := sIdle
        }
      }
    }
  }

  if (config.read) {
    val readIF = io.rMem.get
    // reg.address advances to the next beat's address the same cycle the
    // request fires (below), one cycle before that beat's response
    // arrives — so the group it landed on has to be captured now, not
    // recomputed from reg.address once the response shows up.
    val groupReg = if (wideBanks.isDefined) Some(RegNext(groupIdx(reg.address))) else None
    switch(state) {
      is(sReadLock) {
        readIF.request.valid         := true.B
        readIF.request.bits.addr.get := (if (wideBanks.isEmpty) reg.address else rowAddr(reg.address))
        io.tl.d.valid        := readIF.response.valid
        io.tl.d.bits.opcode  := TilelinkOpcodes.AccessAckData
        io.tl.d.bits.param   := 0.U
        io.tl.d.bits.size    := reg.size
        io.tl.d.bits.source  := 0.U
        io.tl.d.bits.sink    := 0.U
        io.tl.d.bits.denied  := 0.U
        io.tl.d.bits.data    := (if (wideBanks.isEmpty) readIF.response.bits.readData.asUInt
                                  else gatherByGroup(readIF.response.bits.readData, groupReg.get))
        io.tl.d.bits.corrupt := 0.U
        when(readIF.request.fire) { reg.address := reg.address + c.dataBusSize.U }
        when(io.tl.d.fire) {
          beatCnt := beatCnt - c.dataBusSize.U
          when(beatCnt === 0.U) { state := sIdle }
        }
      }
    }
  }

  if (config.atomic) {
    val readIF        = io.rMem.get
    val writeIF       = io.wMem.get
    val amoReserve = io.amoReserve.get

    switch(state) {
      is(amoLock) {
        amoReserve.valid := true.B
        amoReserve.bits.address := reg.address
        amoReserve.bits.opcode := amoReservationOp.acquire

        when(amoReserve.fire){
          state := amoRead
        }
      }
      is(amoRead) {
        readIF.request.valid         := true.B
        readIF.request.bits.addr.get := (if (wideBanks.isEmpty) reg.address else rowAddr(reg.address))
        when(readIF.request.fire) { state := amoOp }
      }
      is(amoOp) {
        when(readIF.response.valid) {
          val memVal = if (wideBanks.isEmpty) readIF.response.bits.readData.asUInt
                       else gather(readIF.response.bits.readData, reg.address)
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
        if (wideBanks.isEmpty) {
          writeIF.bits.addr           := reg.address
          writeIF.bits.data.writeData := amoResult.asTypeOf(Vec(c.dataBusSize, UInt(8.W)))
          writeIF.bits.data.strb      := VecInit(Seq.fill(c.dataBusSize)(true.B))
        } else {
          val (dataVec, strbVec) = scatter(amoResult, reg.address)
          writeIF.bits.addr           := rowAddr(reg.address)
          writeIF.bits.data.writeData := dataVec
          writeIF.bits.data.strb      := strbVec
        }
        writeIF.valid := true.B
        when(writeIF.fire) { state := amoReturn }
      }
      is(amoRelease) {
        amoReserve.valid := true.B
        amoReserve.bits.address := reg.address
        amoReserve.bits.opcode := amoReservationOp.release

        when(amoReserve.fire){
          state := amoReturn
        }
      }
      is(amoReturn) {
        io.tl.d.valid        := true.B
        io.tl.d.bits.opcode  := TilelinkOpcodes.AccessAckData
        io.tl.d.bits.param   := 0.U
        io.tl.d.bits.size    := reg.size
        io.tl.d.bits.source  := reg.source
        io.tl.d.bits.sink    := 0.U
        io.tl.d.bits.denied  := 0.U
        if (wideBanks.isEmpty) {
          val byteOff = reg.address(log2Ceil(c.dataBusSize) - 1, 0)
          val shifted = (originalData >> Cat(byteOff, 0.U(3.W)))(c.dataBusSize * 8 - 1, 0)
          io.tl.d.bits.data := Mux(reg.size < c.dataBusSize.U, shifted, originalData)
        } else {
          io.tl.d.bits.data := originalData
        }
        io.tl.d.bits.corrupt := 0.U
        when(io.tl.d.fire) {
          when(beatCnt === 0.U) {
            state := sIdle
          }.otherwise {
            reg.address := reg.address + c.dataBusSize.U
            beatCnt     := beatCnt - c.dataBusSize.U
            state       := amoLock
          }
        }
      }
    }
  }
}
