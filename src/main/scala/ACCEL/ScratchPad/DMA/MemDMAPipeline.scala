package ATA8

import chisel3._
import chisel3.util._

class MemDMAPipeline(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val tl = new TilelinkPort
    val interface = Flipped(new dmaInterface(1))
    val dataOut = Decoupled(UInt((c.dataBusSize * 8).W))
    /*
    val dataIn = new Bundle {
      val bits  = Input(UInt((c.dataBusSize * 8).W))
      val ready = Input(Bool())
      val valid = Output(Bool())
    }
    */
    val dataIn = Flipped(Decoupled(UInt((c.dataBusSize * 8).W)))

  })

  io.interface.descriptor.ready := false.B
  io.interface.response.valid := false.B
  io.interface.response.bits := DontCare

  io.tl.a.valid := false.B
  io.tl.d.ready := false.B
  io.tl.a.bits := DontCare

  io.dataIn.ready := false.B
  io.dataOut.valid := false.B
  io.dataOut.bits := DontCare

  val StateReg = RegInit(0.U(4.W))
  val reg = Reg(new dmaDescriptor)

  val beatCnt = RegInit(0.U(24.W))
  val firstBeat = RegInit(true.B)

  val tlResponseReg = Reg(io.tl.d.bits.cloneType)

  switch(StateReg) {
    is(0.U) {
      io.interface.descriptor.ready := true.B

      when(io.interface.descriptor.valid) {
        reg := io.interface.descriptor.bits(0)
        firstBeat := true.B
        when(io.interface.descriptor.bits(0).writeEn){ //Write
          StateReg := 1.U
        }.otherwise{
          StateReg := 5.U
        }
      }
    }
    is(1.U) { // Write
      assert(reg.size =/= 0.U)

      // Set up tl
      io.tl.a.valid := true.B
      io.tl.a.bits.opcode := TilelinkOpcodes.PutFullData
      io.tl.a.bits.param := 0.U
      io.tl.a.bits.address := reg.addr
      io.tl.a.bits.size := reg.size
      io.tl.a.bits.source := 0.U
      io.tl.a.bits.data := io.dataIn.bits
      io.tl.a.bits.mask := HelperFunctions.uintToBoolVec(reg.size, c.dataBusSize).asUInt

      when(io.tl.a.fire) {
        io.dataIn.ready := true.B

        beatCnt := 1.U

        when(reg.size > 1.U) {
          StateReg := 2.U
        }.otherwise {
          beatCnt := 0.U
          StateReg := 3.U
        }
      }
    }
    is(2.U) { // Send remaining A beats

      when(io.dataIn.valid){ // Check that buffer has data
        io.tl.a.valid := true.B
      }

      io.tl.a.bits.opcode := TilelinkOpcodes.PutFullData
      io.tl.a.bits.param := 0.U
      //io.tl.a.bits.address := reg.addr + beatCnt
      io.tl.a.bits.address := reg.addr
      io.tl.a.bits.size := reg.size
      io.tl.a.bits.source := 0.U
      io.tl.a.bits.data := io.dataIn.bits
      //io.tl.a.bits.mask := HelperFunctions.uintToBoolVec(reg.size, c.dataBusSize).asUInt
      io.tl.a.bits.corrupt := 0.U

      when(io.tl.a.fire) {

        io.dataIn.ready := true.B

        when(beatCnt < (reg.size - 1.U)) {
          beatCnt := beatCnt + 1.U
        }.otherwise {
          beatCnt := 0.U
          StateReg := 3.U
        }
      }
    }
    is(3.U) { // Wait for D AccessAck, then signal completion
      io.tl.d.ready := true.B

      when(reg.size === 0.U || io.tl.d.valid) {
        tlResponseReg := io.tl.d.bits
        StateReg := 4.U
      }
    }
    is(4.U) { // Send response back to host
      io.interface.response.valid := true.B

      when(io.interface.response.fire) {
        io.interface.response.bits.denied := false.B
        io.interface.response.bits.corrupt := false.B
        StateReg := 0.U
      }
    }
    is(5.U) { // Send read command
      assert(reg.size =/= 0.U)

      // Set up tl
      io.tl.a.valid := true.B
      io.tl.a.bits.opcode := TilelinkOpcodes.Get
      io.tl.a.bits.param := 0.U
      io.tl.a.bits.address := reg.addr
      io.tl.a.bits.size := reg.size
      io.tl.a.bits.source := 0.U
      io.tl.a.bits.corrupt := 0.U

      when(io.tl.a.fire) {
        beatCnt := 0.U
        StateReg := 6.U
      }
    }
    is(6.U) { // Read data beats

      when(io.dataOut.ready){ // Check that buffer has space 
        io.tl.d.ready := true.B
      }

      io.tl.a.bits.opcode := TilelinkOpcodes.Get
      io.tl.a.bits.param := 0.U
      io.tl.a.bits.address := reg.addr
      io.tl.a.bits.size := reg.size
      io.tl.a.bits.source := 0.U
      //io.tl.a.bits.data := io.dataIn.bits
      //io.tl.a.bits.mask := HelperFunctions.uintToBoolVec(reg.size, c.dataBusSize).asUInt
      io.tl.a.bits.corrupt := 0.U

      when(io.tl.d.fire) {

        io.dataOut.valid := true.B
        io.dataOut.bits := io.tl.d.bits.data

        when(beatCnt < (reg.size - 1.U)) {
          beatCnt := beatCnt + 1.U
        }.otherwise {
          beatCnt := 0.U
          StateReg := 7.U
        }
      }
    }
    is(7.U){
      io.interface.response.valid := true.B

      when(io.interface.response.fire) {
        io.interface.response.bits.denied := false.B
        io.interface.response.bits.corrupt := false.B
        StateReg := 0.U
      }
    }
  }
}
