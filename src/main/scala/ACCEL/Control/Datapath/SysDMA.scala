package ATA8

import chisel3._
import chisel3.util._

class SysDMA(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(new DMARead)
    val scratchIn = new TilelinkPort(c.tlBus)
    val writePort = Decoupled(Vec(c.dataBusSize, UInt(c.arithDataWidth.W)))
  })

  io.in.request.ready := false.B

  io.scratchIn.a.valid := false.B
  io.scratchIn.a.bits := DontCare

  io.scratchIn.d.ready := false.B

  io.writePort.valid := false.B
  io.writePort.bits := DontCare

  val StateReg = RegInit(0.U(4.W))
  val reg = Reg(io.in.request.bits.cloneType)

  val beatCnt = RegInit(0.U(24.W))

  io.in.response.valid := StateReg =/= 0.U
  io.in.response.bits.completed := false.B

  switch(StateReg) {
    is(0.U) { // Receives operation
      io.in.request.ready := true.B

      when(io.in.request.valid) {
        reg := io.in.request.bits
        StateReg := 1.U
      }
    }
    is(1.U) { // Send Get on A channel
      io.scratchIn.a.bits.opcode := TilelinkOpcodes.Get
      io.scratchIn.a.bits.param := 0.U
      io.scratchIn.a.bits.address := reg.addr
      io.scratchIn.a.bits.size := reg.burstCnt
      io.scratchIn.a.bits.source := 0.U
      io.scratchIn.a.bits.mask := HelperFunctions.uintToBoolVec(reg.burstSize, c.dataBusSize).asUInt
      io.scratchIn.a.bits.data := 0.U
      io.scratchIn.a.bits.corrupt := 0.U

      when(reg.burstSize =/= 0.U) {
        io.scratchIn.a.valid := true.B
        when(io.scratchIn.a.fire) {
          beatCnt := 0.U
          StateReg := 2.U
        }
      }.otherwise {
        StateReg := 3.U
      }
    }
    is(2.U) { // Accept D beats, write to systolic buffers
      when(io.writePort.ready) {
        io.scratchIn.d.ready := true.B

        when(io.scratchIn.d.valid) {
          val mask = HelperFunctions.uintToBoolVec(reg.burstSize, c.dataBusSize)
          val dataVec = io.scratchIn.d.bits.data.asTypeOf(Vec(c.dataBusSize, UInt(8.W)))

          (io.writePort.bits zip dataVec zip mask).foreach { case ((port, data), m) =>
            when(m) {
              port := data
            }.otherwise {
              port := 0.U
            }
          }

          io.writePort.valid := true.B
          beatCnt := beatCnt + c.dataBusSize.U

          when(beatCnt === (reg.burstCnt - c.dataBusSize.U)) {
            StateReg := 3.U
          }
        }
      }
    }
    is(3.U) { // Wait for Controller to acknowledge finish
      io.in.response.valid := true.B
      io.in.response.bits.completed := true.B

      when(io.in.response.ready) {
        StateReg := 0.U
      }
    }
  }
}
