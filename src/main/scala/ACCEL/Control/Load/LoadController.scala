package ATA8

import chisel3._
import chisel3.experimental._
import chisel3.util._

class LoadController(implicit c: Configuration) extends Module {
  def splitInt(input: UInt, totalWidth: Int, subWidth: Int): Seq[UInt] = {
    require(totalWidth % subWidth == 0, "Total width must be a multiple of sub width")
    val numSplits = totalWidth / subWidth
    (0 until numSplits).map(i => input(subWidth * (i + 1) - 1, subWidth * i))
  }

  val io = IO(new Bundle {
    val instructionStream = new Readport(new LoadInstIssue, 0)
    val AXIST             = Flipped(new AXIST_2(64, 2, 1, 1, 1))
    val writeport         = new TilelinkPort
    val debug             = new LoadDebug
  })

  io.instructionStream.request.valid := false.B
  io.instructionStream.request.bits  := DontCare

  io.writeport.a.valid := false.B
  io.writeport.a.bits  := DontCare
  io.writeport.d.ready := false.B

  io.AXIST.tready := false.B

  val reg           = Reg(new LoadInstIssue)
  val totalBeatsReg = Reg(UInt(24.W))
  val beatCnt       = RegInit(0.U(24.W))
  val StateReg      = RegInit(0.U(4.W))

  /// DEBUG ///
  io.debug.state := StateReg

  switch(StateReg) {
    is(0.U) {
      when(io.instructionStream.request.ready) {
        io.instructionStream.request.valid := true.B
        when(io.instructionStream.response.valid) {
          reg      := io.instructionStream.response.bits.readData
          StateReg := 1.U
        }
      }
    }
    is(1.U) { // Compute totalBeats
      val totalElements = reg.size * reg.size
      val fullTransfers = totalElements >> 3.U
      val partial       = totalElements & 0x7.U
      val hasPartial    = partial =/= 0.U
      totalBeatsReg := fullTransfers + Mux(hasPartial, 1.U, 0.U)
      beatCnt       := 0.U
      StateReg      := 2.U
    }
    is(2.U) { // Send first A beat
      when(io.writeport.a.ready) {
        io.AXIST.tready := true.B
        when(io.AXIST.tvalid) {
          io.writeport.a.valid             := true.B
          io.writeport.a.bits.opcode       := TilelinkOpcodes.PutFullData
          io.writeport.a.bits.param        := 0.U
          io.writeport.a.bits.address      := reg.addrd(0).addr
          io.writeport.a.bits.size         := totalBeatsReg
          io.writeport.a.bits.source       := 0.U
          io.writeport.a.bits.data         := io.AXIST.tdata
          io.writeport.a.bits.mask         := io.AXIST.tstrb
          io.writeport.a.bits.corrupt      := 0.U
          beatCnt := beatCnt + 1.U
          when(io.AXIST.tlast || beatCnt === (totalBeatsReg - 1.U)) {
            StateReg := 4.U
          }.otherwise {
            StateReg := 3.U
          }
        }
      }
    }
    is(3.U) { // Send remaining A beats
      when(io.writeport.a.ready) {
        io.AXIST.tready := true.B
        when(io.AXIST.tvalid) {
          io.writeport.a.valid             := true.B
          io.writeport.a.bits.opcode       := TilelinkOpcodes.PutFullData
          io.writeport.a.bits.param        := 0.U
          io.writeport.a.bits.address      := reg.addrd(0).addr + beatCnt
          io.writeport.a.bits.size         := totalBeatsReg
          io.writeport.a.bits.source       := 0.U
          io.writeport.a.bits.data         := io.AXIST.tdata
          io.writeport.a.bits.mask         := io.AXIST.tstrb
          io.writeport.a.bits.corrupt      := 0.U
          beatCnt := beatCnt + 1.U
          when(io.AXIST.tlast || beatCnt === (totalBeatsReg - 1.U)) {
            StateReg := 4.U
          }
        }
      }
    }
    is(4.U) { // Wait for D AccessAck
      io.writeport.d.ready := true.B
      when(io.writeport.d.valid) {
        StateReg := 0.U
      }
    }
  }
}
