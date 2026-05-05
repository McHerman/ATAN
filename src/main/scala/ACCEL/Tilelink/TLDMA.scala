package ATA8

import chisel3._
import chisel3.util._

case class TLDMAConfig(
  read: Boolean,
  write: Boolean,
  semaphore: Boolean,
) {
  require(read || write, "TLDMA must support at least read or write")
}

/**
 * Parameterised single-channel DMA engine.
 */
class TLDMA(config: TLDMAConfig)(implicit c: MemBusConfig) extends Module {
  val io = IO(new Bundle {
    val tl        = new TilelinkPort
    val semaphoreIF = if (config.semaphore) Some(new TilelinkPort) else None
    val interface = Flipped(new dmaInterface(1, config.semaphore))
    val dataIn    = if (config.write) Some(new Readport(UInt((c.dataBusSize * 8).W))) else None
    val dataOut   = if (config.read)  Some(Decoupled(UInt((c.dataBusSize * 8).W)))          else None
  })

  // Defaults
  io.interface.descriptor.ready := false.B
  io.interface.response.valid   := false.B
  io.interface.response.bits    := DontCare

  io.tl.a.valid := false.B
  io.tl.d.ready := false.B
  io.tl.a.bits  := DontCare

  io.semaphoreIF.foreach { sem =>
    sem.a.valid := false.B
    sem.a.bits  := DontCare
    sem.d.ready := false.B
  }

  io.dataIn.foreach  { d => d.request.valid := false.B; d.request.bits := DontCare }
  io.dataOut.foreach { d => d.valid := false.B; d.bits := DontCare }

  // ── States ───────────────────────────────────────────────────────────────
  val idle :: writeFirst :: writeRest :: writeAck :: writeRespond :: readIssue :: readData :: readRespond :: semAcquireSend :: semAcquireRespond :: semDecrementSend :: semDecrementRespond :: semReleaseSend :: semReleaseRespond :: Nil = Enum(14)

  // ── Registers ─────────────────────────────────────────────────────────────
  val StateReg      = RegInit(idle)
  val reg           = Reg(new dmaDescriptor(config.semaphore))
  val beatCnt       = RegInit(0.U(24.W))
  val effectiveAddr = RegInit(0.U(c.addrWidth.W))
  val effectiveSize = RegInit(0.U(24.W))

  // Only allocated when the config actually needs them
  val isWrite   = if (config.read && config.write) Some(RegInit(true.B)) else None
  val remaining = if (config.semaphore) Some(RegInit(0.U(24.W))) else None



  switch(StateReg) {
    is(idle) {
      io.interface.descriptor.ready := true.B

      when(io.interface.descriptor.valid) {
        val desc = io.interface.descriptor.bits(0)
        reg := desc

        //semAFired.foreach { _ := false.B }
        isWrite.foreach   { _ := desc.writeEn }
        effectiveAddr := desc.addr

        if (config.semaphore) {
          remaining.get := desc.size
          effectiveSize := Mux(desc.semaphore.get.semEnable, desc.semaphore.get.semStepSize, desc.size)
        } else {
          effectiveSize := desc.size
        }

        StateReg := ((config.semaphore, config.read, config.write) match {
          case (true, true, true)   => Mux(desc.semaphore.get.semEnable, semAcquireSend, Mux(desc.writeEn, writeFirst, readIssue))
          case (true, _, true)      => Mux(desc.semaphore.get.semEnable, semAcquireSend, writeFirst)
          case (true, true, _)      => Mux(desc.semaphore.get.semEnable, semAcquireSend, readIssue)
          case (false, true, true)  => Mux(desc.writeEn, writeFirst, readIssue)
          case (false, _, true)     => writeFirst
          case _                    => readIssue
        })
      }
    }
  }

  // ── Write states ────────────────────────────────────────────────────────
  if (config.write) {
    val dataIn = io.dataIn.get

    switch(StateReg) {
      // WriteFirst – issue first A-beat
      is(writeFirst) {
        assert(effectiveSize =/= 0.U)

        io.tl.a.valid        := dataIn.request.ready
        io.tl.a.bits.opcode  := TilelinkOpcodes.PutFullData
        io.tl.a.bits.param   := 0.U
        io.tl.a.bits.address := effectiveAddr
        io.tl.a.bits.size    := effectiveSize
        io.tl.a.bits.source  := 0.U
        io.tl.a.bits.data    := dataIn.response.bits.readData
        io.tl.a.bits.mask    := HelperFunctions.uintToBoolVec(effectiveSize, c.dataBusSize).asUInt

        dataIn.request.valid := io.tl.a.ready

        when(io.tl.a.fire) {
          beatCnt := 1.U

          when(effectiveSize > 1.U) {
            StateReg := writeRest
          }.otherwise {
            beatCnt  := 0.U
            StateReg := writeAck
          }
        }
      }

      // WriteRest – remaining A-beats
      is(writeRest) {
        io.tl.a.valid := dataIn.request.ready

        io.tl.a.bits.opcode  := TilelinkOpcodes.PutFullData
        io.tl.a.bits.param   := 0.U
        io.tl.a.bits.address := effectiveAddr
        io.tl.a.bits.size    := effectiveSize
        io.tl.a.bits.source  := 0.U
        io.tl.a.bits.data    := dataIn.response.bits.readData
        io.tl.a.bits.corrupt := 0.U

        when(io.tl.a.fire) {
          dataIn.request.valid := true.B

          when(beatCnt < (effectiveSize - 1.U)) {
            beatCnt := beatCnt + 1.U
          }.otherwise {
            beatCnt  := 0.U
            StateReg := writeAck
          }
        }
      }

      // WriteAck – wait for D AccessAck
      is(writeAck) {
        io.tl.d.ready := true.B

        when(effectiveSize === 0.U || io.tl.d.valid) {
          StateReg := (config.semaphore match {
            case true  => Mux(reg.semaphore.get.semEnable, semReleaseSend, writeRespond)
            case false => writeRespond
          })
        }
      }

      // WriteRespond – send response
      is(writeRespond) {
        io.interface.response.valid := true.B

        when(io.interface.response.fire) {
          io.interface.response.bits.denied  := false.B
          io.interface.response.bits.corrupt := false.B
          StateReg := idle
        }
      }
    }
  }

  // ── Read states ─────────────────────────────────────────────────────────
  if (config.read) {
    val dataOut = io.dataOut.get

    switch(StateReg) {
      // ReadIssue – issue Get
      is(readIssue) {
        assert(effectiveSize =/= 0.U)

        io.tl.a.valid        := true.B
        io.tl.a.bits.opcode  := TilelinkOpcodes.Get
        io.tl.a.bits.param   := 0.U
        io.tl.a.bits.address := effectiveAddr
        io.tl.a.bits.size    := effectiveSize
        io.tl.a.bits.source  := 0.U
        io.tl.a.bits.corrupt := 0.U

        when(io.tl.a.fire) {
          beatCnt  := 0.U
          StateReg := readData
        }
      }

      // ReadData – receive D data beats
      is(readData) {
        when(dataOut.ready) { io.tl.d.ready := true.B }

        when(io.tl.d.fire) {
          dataOut.valid := true.B
          dataOut.bits  := io.tl.d.bits.data

          when(beatCnt < (effectiveSize - 1.U)) {
            beatCnt := beatCnt + 1.U
          }.otherwise {
            beatCnt  := 0.U

            StateReg := (config.semaphore match {
              case true  => Mux(reg.semaphore.get.semEnable, semReleaseSend, readRespond)
              case false => readRespond
            })

          }
        }
      }

      // ReadRespond – send response
      is(readRespond) {
        io.interface.response.valid := true.B

        when(io.interface.response.fire) {
          io.interface.response.bits.denied  := false.B
          io.interface.response.bits.corrupt := false.B
          StateReg := idle
        }
      }
    }
  }

  // ── Semaphore states ────────────────────────────────────────────────────
  // The semaphore address is treated as a base:
  //   base + 0 = fullReg,  base + 1 = emptyReg
  //
  // Direction is derived from writeEn (or statically from config):
  //   writer = producer: acquire/SUBU on emptyReg (base+1), release/ADDU on fullReg (base+0)
  //   reader = consumer: acquire/SUBU on fullReg  (base+0), release/ADDU on emptyReg (base+1)
  //
  // Flow: semAcquireSend/Respond(AQGREQ) → semDecrementSend/Respond(SUBU) → read/write → semReleaseSend/Respond(ADDU)
  if (config.semaphore) {
    val sem = io.semaphoreIF.get

    val isProducer = (config.read, config.write) match {
      case (true, true) => isWrite.get
      case (_, true)    => true.B   // write-only → always producer
      case _            => false.B  // read-only  → always consumer
    }
    val semAcquireAddr = reg.semaphore.get.semAddr + isProducer.asUInt
    val semReleaseAddr = reg.semaphore.get.semAddr + (!isProducer).asUInt

    switch(StateReg) {
      // SemAcquireSend – AQGREQ, stall until semaphore condition is met
      is(semAcquireSend) {
        sem.a.valid        := true.B
        sem.a.bits.opcode  := TilelinkOpcodes.ArithmeticData
        sem.a.bits.param   := ArithmeticDataParam.AQGREQ
        sem.a.bits.size    := 0.U
        sem.a.bits.source  := 0.U
        sem.a.bits.address := semAcquireAddr
        sem.a.bits.data    := reg.semaphore.get.semStepSize
        sem.a.bits.mask    := Fill(c.dataBusSize, 1.U(1.W))
        sem.a.bits.corrupt := 0.U

        when(sem.a.fire) {
          StateReg := semAcquireRespond
        }
      }

      is(semAcquireRespond) {
        sem.d.ready := true.B

        when(sem.d.fire) {
          StateReg := semDecrementSend
        }
      }

      // SemDecrementSend – SUBU on the acquire register to consume the token
      is(semDecrementSend) {
        sem.a.valid        := true.B
        sem.a.bits.opcode  := TilelinkOpcodes.ArithmeticData
        sem.a.bits.param   := ArithmeticDataParam.SUBU
        sem.a.bits.size    := 0.U
        sem.a.bits.source  := 0.U
        sem.a.bits.address := semAcquireAddr
        sem.a.bits.data    := reg.semaphore.get.semStepSize
        sem.a.bits.mask    := Fill(c.dataBusSize, 1.U(1.W))
        sem.a.bits.corrupt := 0.U

        when(sem.a.fire) {
          StateReg := semDecrementRespond
        }
      }

      is(semDecrementRespond) {
        sem.d.ready := true.B

        when(sem.d.fire) {
          StateReg := ((config.read, config.write) match {
            case (true, true) => Mux(isWrite.get, writeFirst, readIssue)
            case (_, true)    => writeFirst
            case _            => readIssue
          })
        }
      }

      // SemRelease – ADDU on the opposite register, then loop or finish
      is(semReleaseSend) {
        sem.a.valid        := true.B
        sem.a.bits.opcode  := TilelinkOpcodes.ArithmeticData
        sem.a.bits.param   := ArithmeticDataParam.ADDU
        sem.a.bits.size    := 0.U
        sem.a.bits.source  := 0.U
        sem.a.bits.address := semReleaseAddr
        sem.a.bits.data    := reg.semaphore.get.semStepSize
        sem.a.bits.mask    := Fill(c.dataBusSize, 1.U(1.W))
        sem.a.bits.corrupt := 0.U

        when(sem.a.fire) {
          StateReg := semReleaseRespond
        }
      }

      is(semReleaseRespond) {
        sem.d.ready := true.B 

        when(sem.d.fire) {
          val next = remaining.get - reg.semaphore.get.semStepSize
          remaining.get := next

          when(next > 0.U) {
            effectiveAddr := effectiveAddr + reg.semaphore.get.semStepSize
            StateReg := semAcquireSend
          }.otherwise {
            StateReg := ((config.read, config.write) match {
              case (true, true) => Mux(isWrite.get, writeRespond, readRespond)
              case (_, true)    => writeRespond
              case _            => readRespond
            })
          }
        }
      }
    }
  }
}
