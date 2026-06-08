package ATA8

import chisel3._
import chisel3.util._

class SemaphoreProg(val genWidth: Int) extends Bundle {
  val initValues = Vec(2, UInt(16.W))
  val generation = UInt(genWidth.W)
}

class Semaphore()(implicit c: Configuration) extends Module {
  val genWidth     = c.semaphoreGenerationWidth
  val regSelectBit = genWidth // address bit that selects full/empty register

  val io = IO(new Bundle {
    val inPorts  = Vec(2, Flipped(new TilelinkPort))
    val progPort = Flipped(Decoupled(new SemaphoreProg(genWidth)))
  })

  io.inPorts.foreach { port =>
    port.a.ready := false.B
    port.d.valid := false.B
    port.d.bits := DontCare
  }

  // regs(0) = fullReg, regs(1) = emptyReg; address bit `regSelectBit` selects which
  val regs = RegInit(VecInit(Seq.fill(2)(0.U(16.W))))

  // Installed at (re)programming; mismatch with address LSBs is denied.
  val generation = RegInit(0.U(genWidth.W))

  io.progPort.ready := regs(0) === 0.U && regs(1) === 0.U // We only accept reprogramming when semaphore execution has finished

  // Should prevent hazards in asynchronous execution
  when(io.progPort.fire){
    regs(0)    := io.progPort.bits.initValues(0)
    regs(1)    := io.progPort.bits.initValues(1)
    generation := io.progPort.bits.generation
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
        val newVal = regs(reg.address(regSelectBit)) - reg.data

        when(!applied) {
          req(idx)(reg.address(regSelectBit)) := true.B

          when(grant(idx)(reg.address(regSelectBit))) {
            regs(reg.address(regSelectBit)) := newVal
            newValRegs(idx)                 := newVal
            applied                         := true.B
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
          statereg := idle
          applied  := false.B
        }
      }
      is(increment){
        val newVal = regs(reg.address(regSelectBit)) + reg.data

        when(!applied) {
          req(idx)(reg.address(regSelectBit)) := true.B

          when(grant(idx)(reg.address(regSelectBit))) {
            regs(reg.address(regSelectBit)) := newVal
            newValRegs(idx)                 := newVal
            applied                         := true.B
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
