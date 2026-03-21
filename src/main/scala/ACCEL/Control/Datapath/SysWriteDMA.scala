package ATA8

import chisel3._
import chisel3.util._

object HelperFunctions {
  def uintToBoolVec(uint: UInt, n: Int): Vec[Bool] = {
    VecInit((0 until n).map(i => uint > i.U))
  }
}

class SysWriteDMA(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(new DMAWrite)
    val scratchOut = new TilelinkPort
    val readPort = new Readport(Vec(c.dataBusSize, UInt(8.W)), 10)
  })

  io.in.request.ready := false.B

  io.scratchOut.a.valid := false.B
  io.scratchOut.a.bits := DontCare

  io.scratchOut.d.ready := false.B

  io.readPort.request.valid := false.B
  io.readPort.request.bits := DontCare

  val StateReg = RegInit(0.U(4.W))
  val reg = Reg(io.in.request.bits.cloneType)

  val beatCnt = RegInit(0.U(24.W))
  val firstBeat = RegInit(true.B)

  io.in.response.valid := StateReg =/= 0.U
  io.in.response.bits.completed := false.B
  io.in.response.bits.tag := 0.U

  switch(StateReg) {
    is(0.U) {
      io.in.request.ready := true.B

      when(io.in.request.valid) {
        reg := io.in.request.bits
        firstBeat := true.B
        StateReg := 1.U
      }
    }
    is(1.U) { // Send first A beat (PutFull with data from readPort)
      io.readPort.request.bits.addr := reg.addr + beatCnt

      when(reg.burstSize =/= 0.U) {
        io.readPort.request.valid := true.B

        when(io.readPort.response.valid) {
          io.scratchOut.a.valid := true.B
          io.scratchOut.a.bits.opcode := TilelinkOpcodes.PutFullData
          io.scratchOut.a.bits.param := 0.U
          io.scratchOut.a.bits.address := reg.addr
          io.scratchOut.a.bits.size := reg.burstCnt
          io.scratchOut.a.bits.source := 0.U
          io.scratchOut.a.bits.data := io.readPort.response.bits.readData.asUInt
          io.scratchOut.a.bits.mask := HelperFunctions.uintToBoolVec(reg.burstSize, c.dataBusSize).asUInt
          io.scratchOut.a.bits.corrupt := 0.U

          when(io.scratchOut.a.fire) {
            beatCnt := 1.U

            when(reg.burstCnt > 1.U) {
              StateReg := 2.U
            }.otherwise {
              beatCnt := 0.U
              StateReg := 3.U
            }
          }
        }
      }.otherwise {
        StateReg := 3.U
      }
    }
    is(2.U) { // Send remaining A beats
      io.readPort.request.bits.addr := reg.addr + beatCnt
      io.readPort.request.valid := true.B

      when(io.readPort.response.valid) {
        io.scratchOut.a.valid := true.B
        io.scratchOut.a.bits.opcode := TilelinkOpcodes.PutFullData
        io.scratchOut.a.bits.param := 0.U
        io.scratchOut.a.bits.address := reg.addr + beatCnt
        io.scratchOut.a.bits.size := reg.burstCnt
        io.scratchOut.a.bits.source := 0.U
        io.scratchOut.a.bits.data := io.readPort.response.bits.readData.asUInt
        io.scratchOut.a.bits.mask := HelperFunctions.uintToBoolVec(reg.burstSize, c.dataBusSize).asUInt
        io.scratchOut.a.bits.corrupt := 0.U

        when(io.scratchOut.a.fire) {
          when(beatCnt < (reg.burstCnt - 1.U)) {
            beatCnt := beatCnt + 1.U
          }.otherwise {
            beatCnt := 0.U
            StateReg := 3.U
          }
        }
      }
    }
    is(3.U) { // Wait for D AccessAck, then signal completion
      io.scratchOut.d.ready := true.B

      when(reg.burstSize === 0.U || io.scratchOut.d.valid) {
        io.in.response.bits.completed := true.B
        io.in.response.bits.tag := reg.tag

        when(io.in.response.ready) {
          StateReg := 0.U
        }
      }
    }
  }
}
