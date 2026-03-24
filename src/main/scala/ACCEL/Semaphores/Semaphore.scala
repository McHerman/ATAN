package ATA8

import chisel3._
import chisel3.util._

class Semaphore()(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val inPorts = Vec(2, Flipped(new TilelinkPort))
    val progPort = Flipped(Decoupled(Vec(2, UInt(16.W))))
  })

  io.inPorts.foreach { port =>
    port.a.ready := false.B
    port.d.valid := false.B
    port.d.bits := DontCare
  }

  val fullReg = RegInit(0.U(16.W))
  val emptyReg = RegInit(0.U(16.W))

  val initReg = RegInit(0.U(1.W))


  io.progPort.ready := true.B 

  when(io.progPort.fire){
    fullReg := io.progPort.bits(0)
    emptyReg := io.progPort.bits(1)
  }

  val idle :: acquire :: acquireReturn :: decrement :: Nil = Enum(4)

  val stateregs        = RegInit(VecInit(Seq.fill(2)(idle)))
  val inputregs        = Reg(Vec(2, io.inPorts(0).a.bits.cloneType))
  val decrementApplied = RegInit(VecInit(Seq.fill(2)(false.B)))
  val newValRegs       = Reg(Vec(2, UInt(16.W)))

  // Explicit write-request wires: pulled high from within state logic
  val reqFull  = Wire(Vec(2, Bool())); reqFull.foreach(_  := false.B)
  val reqEmpty = Wire(Vec(2, Bool())); reqEmpty.foreach(_ := false.B)

  // Round-robin arbiter: token toggles every cycle
  val rrToken = RegInit(0.U(1.W))
  rrToken := ~rrToken

  // Contention: all ports are requesting the same register simultaneously
  val fullContention  = reqFull.reduce(_ && _)
  val emptyContention = reqEmpty.reduce(_ && _)

  // Grant if requesting, and either there is no contention or it is this port's turn
  val grantFull  = Wire(Vec(2, Bool()))
  val grantEmpty = Wire(Vec(2, Bool()))
  grantFull.zipWithIndex.foreach  { case (g, i) => g := reqFull(i)  && (!fullContention  || rrToken === i.U) }
  grantEmpty.zipWithIndex.foreach { case (g, i) => g := reqEmpty(i) && (!emptyContention || rrToken === i.U) }

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

          switch(port.a.bits.param){
            is(ArithmeticDataParam.AQGREQ){
              statereg := acquire 
            }
            is(ArithmeticDataParam.SUBU){
              statereg := decrement 
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
      is(acquire){
        // LSB is used to pick between prod and cons registers
        val inputReg = Mux(reg.address(0), emptyReg, fullReg)

        when(inputReg >= reg.data){ 
          statereg := acquireReturn
        }.otherwise{
          // do nothing until the requirement is met.
        }
      }
      is(acquireReturn){
        port.d.valid := true.B

        val inputReg = Mux(reg.address(0), emptyReg, fullReg)

        port.d.bits.opcode := TilelinkOpcodes.AccessAckData
        port.d.bits.param := 0.U 
        port.d.bits.size := 0.U 
        port.d.bits.source := reg.source
        port.d.bits.sink := DontCare // TODO, find some better use for this
        port.d.bits.denied := false.B
        port.d.bits.data :=  inputReg
        port.d.bits.corrupt := 0.U

        when(port.d.fire){
          statereg := idle
        }
      }
      is(decrement){
        val inputReg = Mux(reg.address(0), emptyReg, fullReg)
        val newVal   = inputReg - reg.data

        when(!applied) {
          // Pull request wire high; arbiter decides who gets the grant
          when(reg.address(0)){
            reqEmpty(idx) := true.B
          }.otherwise{
            reqFull(idx)  := true.B
          }

          val granted = Mux(reg.address(0), grantEmpty(idx), grantFull(idx))
          when(granted) {
            when(reg.address(0)){
              emptyReg := newVal
            }.otherwise{
              fullReg := newVal
            }
            newValRegs(idx) := newVal  // latch result so d.bits.data is stable
            applied := true.B
          }
        }

        // Only present response after the write has been granted and latched
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
    }
  }
}
