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

  val stateregs = RegInit(VecInit(Seq.fill(2)(idle)))
  val inputregs = Reg(Vec(2,io.inPorts(0).a.bits.cloneType))

  // Dual statemachine
  ((stateregs zip io.inPorts) zip inputregs).foreach { case ((statereg, port), reg) =>
    switch(statereg){
      is(idle){
        port.a.ready := true.B

        when(port.a.fire){
          reg := port.a.bits 

          // We only accept arithmetic atomics
          assert(port.a.bits.opcode === TilelinkOpcodes.ArithmeticData)

          switch(port.a.bits.param){
            is(ArithmeticDataParam.AQGREQ){
              statereg := acquireReturn 
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

        val newVal = inputReg - reg.data
emptyReg := newVal

        port.d.valid := true.B

        port.d.bits.opcode := TilelinkOpcodes.AccessAckData
        port.d.bits.param := 0.U 
        port.d.bits.size := 0.U 
        port.d.bits.source := reg.source
        port.d.bits.sink := DontCare // TODO, find some better use for this
        port.d.bits.denied := false.B
        port.d.bits.data := newVal
        port.d.bits.corrupt := 0.U

        when(port.d.fire){
          statereg := idle
        }
      }
    }
  }
}
