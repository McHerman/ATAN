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
class TLDMA(config: TLDMAConfig, sourceId: Int = 0)(implicit c: MemBusConfig) extends Module {
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
  val stepSize = RegInit(0.U(24.W))
  val effectiveSize = RegInit(0.U(24.W))

  // Only allocated when the config actually needs them
  val isWrite   = if (config.read && config.write) Some(RegInit(true.B)) else None
  //val remaining = if (config.semaphore) Some(RegInit(0.U(24.W))) else None
  val remaining = RegInit(0.U(24.W))


  switch(StateReg) {
    is(idle) {
      io.interface.descriptor.ready := true.B

      when(io.interface.descriptor.valid) {
        val desc = io.interface.descriptor.bits(0)
        reg := desc

        //semAFired.foreach { _ := false.B }
        isWrite.foreach   { _ := desc.writeEn }
        effectiveAddr := desc.addr

        remaining := desc.size

        if (config.semaphore) {
          stepSize := Mux(desc.semaphore.get.semEnable, desc.semaphore.get.semStepSize, desc.size)
        } else {
          stepSize := desc.size
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
        assert(stepSize =/= 0.U)

        val effectiveSizeVal = Mux(stepSize < remaining, stepSize, remaining)
        val newBeatCnt       = effectiveSizeVal - c.dataBusSize.U

        beatCnt       := newBeatCnt
        effectiveSize := effectiveSizeVal

        io.tl.a.valid        := dataIn.request.ready
        io.tl.a.bits.opcode  := TilelinkOpcodes.PutFullData
        io.tl.a.bits.param   := 0.U
        io.tl.a.bits.address := effectiveAddr
        io.tl.a.bits.size    := effectiveSizeVal
        io.tl.a.bits.source  := sourceId.U
        io.tl.a.bits.data    := dataIn.response.bits.readData
        io.tl.a.bits.mask    := HelperFunctions.uintToBoolVec(stepSize, c.dataBusSize).asUInt

        dataIn.request.valid := io.tl.a.ready

        when(io.tl.a.fire) {
          when(newBeatCnt >= c.dataBusSize.U) {
            StateReg := writeRest
          }.otherwise {
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
        io.tl.a.bits.source  := sourceId.U
        io.tl.a.bits.data    := dataIn.response.bits.readData
        io.tl.a.bits.corrupt := 0.U

        when(io.tl.a.fire) {
          dataIn.request.valid := true.B

          when(beatCnt > c.dataBusSize.U) {
            beatCnt := beatCnt - c.dataBusSize.U
          }.otherwise {
            beatCnt  := 0.U
            StateReg := writeAck
          }
        }
      }

      // WriteAck – wait for D AccessAck
      is(writeAck) {
        io.tl.d.ready := true.B

        //when(stepSize === 0.U || io.tl.d.valid) {
        when(io.tl.d.valid) {
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
        assert(stepSize =/= 0.U)

        val effectiveSizeVal = Mux(stepSize < remaining, stepSize, remaining)
        effectiveSize := effectiveSizeVal

        io.tl.a.valid        := true.B
        io.tl.a.bits.opcode  := TilelinkOpcodes.Get
        io.tl.a.bits.param   := 0.U
        io.tl.a.bits.address := effectiveAddr
        io.tl.a.bits.size    := effectiveSizeVal
        io.tl.a.bits.source  := sourceId.U
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

          when(beatCnt < effectiveSize - c.dataBusSize.U) {
            beatCnt := beatCnt + c.dataBusSize.U
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
  // Assembler bakes gen/port/sem into semAddr; TLDMA only flips the register-select bit (shifted by genWidth).
  if (config.semaphore) {
    val sem = io.semaphoreIF.get

    val isProducer = (config.read, config.write) match {
      case (true, true) => isWrite.get
      case (_, true)    => true.B   // write-only → always producer
      case _            => false.B  // read-only  → always consumer
    }

    val regSelShift = c.semaphoreGenerationWidth
    val semAcquireAddr = reg.semaphore.get.semAddr + (isProducer.asUInt    << regSelShift)
    val semReleaseAddr = reg.semaphore.get.semAddr + ((!isProducer).asUInt << regSelShift)

    switch(StateReg) {
      // SemAcquireSend – AQGREQ, stall until semaphore condition is met
      is(semAcquireSend) {
        sem.a.valid        := true.B
        sem.a.bits.opcode  := TilelinkOpcodes.ArithmeticData
        sem.a.bits.param   := ArithmeticDataParam.AQGREQ
        sem.a.bits.size    := 0.U
        sem.a.bits.source  := sourceId.U
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

        // denied = gen mismatch → spinwait by re-issuing.
        when(sem.d.fire) {
          when(sem.d.bits.denied.asBool) {
            StateReg := semAcquireSend
          }.otherwise {
            StateReg := semDecrementSend
          }
        }
      }

      // SemDecrementSend – ADD(negative) on the acquire register to consume the token
      is(semDecrementSend) {
        sem.a.valid        := true.B
        sem.a.bits.opcode  := TilelinkOpcodes.ArithmeticData
        sem.a.bits.param   := ArithmeticDataParam.ADD
        sem.a.bits.size    := 0.U
        sem.a.bits.source  := sourceId.U
        sem.a.bits.address := semAcquireAddr
        sem.a.bits.data    := (-reg.semaphore.get.semStepSize.asSInt).asUInt
        sem.a.bits.mask    := Fill(c.dataBusSize, 1.U(1.W))
        sem.a.bits.corrupt := 0.U

        when(sem.a.fire) {
          StateReg := semDecrementRespond
        }
      }

      is(semDecrementRespond) {
        sem.d.ready := true.B

        when(sem.d.fire) {
          when(sem.d.bits.denied.asBool) {
            StateReg := semDecrementSend
          }.otherwise {
            (config.read, config.write) match {
              case (true, true) =>
                when(isWrite.get) { StateReg := writeFirst }
                  .otherwise      { StateReg := readIssue  }
              case (_, true) => StateReg := writeFirst
              case _         => StateReg := readIssue
            }
          }
        }
      }

      // SemRelease – ADD(positive) on the opposite register, then loop or finish
      is(semReleaseSend) {
        sem.a.valid        := true.B
        sem.a.bits.opcode  := TilelinkOpcodes.ArithmeticData
        sem.a.bits.param   := ArithmeticDataParam.ADD
        sem.a.bits.size    := 0.U
        sem.a.bits.source  := sourceId.U
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
          when(sem.d.bits.denied.asBool) {
            // gen mismatch: retry without advancing remaining/effectiveAddr.
            StateReg := semReleaseSend
          }.otherwise {
            val next = remaining - effectiveSize
            remaining := next

            when(next > 0.U) {
              effectiveAddr := effectiveAddr + effectiveSize
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
}
