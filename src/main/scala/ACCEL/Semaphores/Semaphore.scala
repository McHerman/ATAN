package ATA8

import chisel3._
import chisel3.util._


object SemEventModes {
  val RW  = 0.U(2.W)
  val R   = 1.U(2.W)
}

class SemaphoreProg(val genWidth: Int) extends Bundle {
  val initFull   = UInt(16.W)
  val initEmpty  = UInt(16.W)
  val generation = UInt(genWidth.W)
  val eventMode  = UInt(2.W)
}

object SemaphoreEventCodes {
  val Complete = 0.U(3.W)
}


class SemaphoreEvent()(implicit c: Configuration) extends Bundle {
  val addr = UInt(c.addrWidth.W) 
  val eventCode = UInt(3.W)
}

class Semaphore(val semIdx: Int)(implicit c: Configuration) extends Module {
  val genWidth     = c.semaphoreGenerationWidth
  val regSelectBit = genWidth // address bit that selects full/empty register: 0 = full, 1 = empty

  val io = IO(new Bundle {
    val inPorts  = Vec(2, Flipped(new TilelinkPort))
    val progPort = Flipped(Decoupled(new SemaphoreProg(genWidth)))
    val eventPort = Decoupled(new SemaphoreEvent)
  })

  io.inPorts.foreach { port =>
    port.a.ready := false.B
    port.d.valid := false.B
    port.d.bits := DontCare
  }

  // Address bit `regSelectBit` selects which: 0 -> full, 1 -> empty
  val full  = RegInit(0.U(16.W))
  val empty = RegInit(0.U(16.W))
  val regs  = VecInit(full, empty)

  // Installed at (re)programming; mismatch with address LSBs is denied.
  val generation = RegInit(0.U(genWidth.W))

  val initFull        = RegInit(0.U(16.W))
  val initEmpty       = RegInit(0.U(16.W))
  val eventMode       = RegInit(SemEventModes.RW)
  val touched         = RegInit(false.B)
  val completePending = RegInit(false.B)

  io.progPort.ready := true.B

  // Should prevent hazards in asynchronous execution
  when(io.progPort.fire){
    full            := io.progPort.bits.initFull
    empty           := io.progPort.bits.initEmpty
    generation      := io.progPort.bits.generation
    initFull        := io.progPort.bits.initFull
    initEmpty       := io.progPort.bits.initEmpty
    eventMode       := io.progPort.bits.eventMode
    when(io.progPort.bits.eventMode === SemEventModes.R) {printf(p"Loaded READMODE!! semIdx=$semIdx\n")}
    touched         := false.B
    completePending := false.B
  }

  val atInit = full === initFull && empty === initEmpty
  val atReverse = full === initEmpty && empty === initFull

  //when(!atInit) { touched := true.B }

  switch(eventMode){
    is(SemEventModes.RW) {
      when(touched && atInit && !completePending && !io.progPort.fire) {
        completePending := true.B
      }
    }
    is(SemEventModes.R) {
      when(touched && atReverse && !completePending && !io.progPort.fire) {
        completePending := true.B
      }
    }
  }


  /*
  when(touched && atInit && !completePending && !io.progPort.fire) {
    completePending := true.B
  }
  */

  val fusedAddr =
    if (genWidth == 0) (semIdx).U(c.addrWidth.W)
    else Cat(semIdx.U((c.addrWidth - genWidth).W), generation)

  io.eventPort.valid          := completePending
  io.eventPort.bits.addr      := fusedAddr
  io.eventPort.bits.eventCode := SemaphoreEventCodes.Complete

  when(io.eventPort.fire) {
    completePending := false.B
    touched         := false.B
  }


  val idle :: acquire :: acquireReturn :: decrement :: increment :: denied :: Nil = Enum(6)

  val stateregs        = RegInit(VecInit(Seq.fill(2)(idle)))
  val inputregs        = Reg(Vec(2, io.inPorts(0).a.bits.cloneType))
  val decrementApplied = RegInit(VecInit(Seq.fill(2)(false.B)))
  val newValRegs       = Reg(Vec(2, UInt(16.W)))

  // Explicit write-request wires: req(portIdx)(regIdx), pulled high from within state logic
  val req = Wire(Vec(2, Vec(2, Bool())))
  req.foreach(_.foreach(_ := false.B))

  // Round-robin arbiter: token toggles every cycle
  val rrToken = RegInit(0.U(1.W))
  rrToken := ~rrToken

  // Contention per register: both ports requesting the same register simultaneously
  val contention = VecInit(Seq.tabulate(2)(r => req(0)(r) && req(1)(r)))

  // Grant if requesting, and either no contention or it is this port's turn
  val grant = Wire(Vec(2, Vec(2, Bool())))
  grant.zipWithIndex.foreach { case (portGrants, portIdx) =>
    portGrants.zipWithIndex.foreach { case (g, regIdx) =>
      g := req(portIdx)(regIdx) && (!contention(regIdx) || rrToken === portIdx.U)
    }
  }

  // Dual statemachine
  stateregs.indices.foreach { idx =>
    val (statereg, port, reg, applied) =
      (stateregs(idx), io.inPorts(idx), inputregs(idx), decrementApplied(idx))

    switch(statereg){
      is(idle){
        port.a.ready := true.B

        when(port.a.fire){
          reg := port.a.bits

          // We only accept arithmetic atomics
          assert(port.a.bits.opcode === TilelinkOpcodes.ArithmeticData)

          val genMatches =
            if (genWidth == 0) true.B
            else port.a.bits.address(genWidth - 1, 0) === generation

          when(!genMatches){
            statereg := denied
          }.otherwise{
            switch(port.a.bits.param){
              is(ArithmeticDataParam.AQGREQ){
                statereg := acquire
              }
              is(ArithmeticDataParam.SUBU){
                statereg := decrement
              }
              is(ArithmeticDataParam.ADDU){
                statereg := increment
              }
              /*
              default(){
                // Unsupported operation
                assert(false.B)
              }
              */
            }
          }
        }
      }
      is(acquire){
        when(regs(reg.address(regSelectBit)) >= reg.data){
          statereg := acquireReturn
        }.otherwise{
          // do nothing until the requirement is met.
        }
      }
      is(acquireReturn){
        port.d.valid := true.B

        port.d.bits.opcode := TilelinkOpcodes.AccessAckData
        port.d.bits.param  := 0.U
        port.d.bits.size   := 0.U
        port.d.bits.source := reg.source
        port.d.bits.sink   := DontCare // TODO, find some better use for this
        port.d.bits.denied := false.B
        port.d.bits.data   := regs(reg.address(regSelectBit))
        port.d.bits.corrupt := 0.U

        when(port.d.fire){
          statereg := idle
        }
      }
      is(decrement){
        val sel    = reg.address(regSelectBit)
        val newVal = regs(sel) - reg.data

        when(!applied) {
          req(idx)(sel) := true.B

          when(grant(idx)(sel)) {
            when(sel === 0.U) { full := newVal }.otherwise { empty := newVal }
            newValRegs(idx) := newVal
            applied         := true.B
          }
        }

        port.d.valid := applied

        port.d.bits.opcode := TilelinkOpcodes.AccessAckData
        port.d.bits.param  := 0.U
        port.d.bits.size   := 0.U
        port.d.bits.source := reg.source
        port.d.bits.sink   := DontCare // TODO, find some better use for this
        port.d.bits.denied := false.B
        port.d.bits.data   := newValRegs(idx)
        port.d.bits.corrupt := 0.U

        when(port.d.fire){
          touched := true.B
          statereg := idle
          applied  := false.B
        }
      }
      is(increment){
        val sel    = reg.address(regSelectBit)
        val newVal = regs(sel) + reg.data

        when(!applied) {
          req(idx)(sel) := true.B

          when(grant(idx)(sel)) {
            when(sel === 0.U) { full := newVal }.otherwise { empty := newVal }
            newValRegs(idx) := newVal
            applied         := true.B
          }
        }

        port.d.valid := applied

        port.d.bits.opcode := TilelinkOpcodes.AccessAckData
        port.d.bits.param  := 0.U
        port.d.bits.size   := 0.U
        port.d.bits.source := reg.source
        port.d.bits.sink   := DontCare // TODO, find some better use for this
        port.d.bits.denied := false.B
        port.d.bits.data   := newValRegs(idx)
        port.d.bits.corrupt := 0.U

        when(port.d.fire){
          touched := true.B
          statereg := idle
          applied  := false.B
        }
      }
      is(denied){
        // gen mismatch: return denied AccessAckData and drop the request.
        port.d.valid := true.B

        port.d.bits.opcode  := TilelinkOpcodes.AccessAckData
        port.d.bits.param   := 0.U
        port.d.bits.size    := 0.U
        port.d.bits.source  := reg.source
        port.d.bits.sink    := DontCare
        port.d.bits.denied  := true.B
        port.d.bits.data    := 0.U
        port.d.bits.corrupt := 0.U

        when(port.d.fire){
          statereg := idle
        }
      }
    }
  }
}
