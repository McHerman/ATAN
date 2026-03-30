package ATA8

import chisel3._
import chisel3.experimental._
import chisel3.util._

class StoreController(implicit c: Configuration) extends Module {
  def splitInt(input: UInt, totalWidth: Int, subWidth: Int): Seq[UInt] = {
    require(totalWidth % subWidth == 0, "Total width must be a multiple of sub width")
    val numSplits = totalWidth / subWidth
    (0 until numSplits).map(i => input(subWidth * (i + 1) - 1, subWidth * i))
  }

  val io = IO(new Bundle {
    val instructionStream = new Readport(new StoreInstIssue)
    val AXIST = new AXIST_2(64, 2, 1, 1, 1)
    val readport = new TilelinkPort

    val debug = new StoreDebug
  })

  io.instructionStream.request.valid := false.B
  io.instructionStream.request.bits := DontCare

  io.AXIST := DontCare
  io.AXIST.tvalid := false.B
  io.AXIST.tlast := false.B

  io.readport.a.valid := false.B
  io.readport.a.bits := DontCare
  io.readport.d.ready := false.B

  val reg = Reg(new StoreInstIssue)
  val StateReg = RegInit(0.U(4.W))

  val transferReg = Reg(new Bundle { val totalTransfers = UInt(24.W); val hasPartial = Bool(); val partial = UInt(log2Ceil(c.dataBusSize + 1).W) })
  val beatCnt = RegInit(0.U(24.W))

  /// DEBUG ///
  io.debug.state := StateReg
  io.debug.axiReady := io.AXIST.tready
  io.debug.readPortValid := io.readport.d.valid

  switch(StateReg) {
    is(0.U) {
      when(io.instructionStream.request.ready) {
        io.instructionStream.request.valid := true.B

        when(io.instructionStream.response.valid) {
          reg := io.instructionStream.response.bits.readData
          StateReg := 1.U
        }
      }
    }
    is(1.U) {
      val totalElements = reg.size * reg.size

      val fullTransfers = totalElements >> 3.U
      val partial = totalElements & 0x7.U
      val hasPartial = partial =/= 0.U

      val totalTransfers = fullTransfers + Mux(hasPartial, 1.U, 0.U)

      transferReg.totalTransfers := totalTransfers
      transferReg.hasPartial := hasPartial
      transferReg.partial := partial

      StateReg := 2.U
    }
    is(2.U) { // Send Get on A channel
      io.readport.a.bits.opcode := TilelinkOpcodes.Get
      io.readport.a.bits.param := 0.U
      io.readport.a.bits.address := reg.addrs(0).addr
      io.readport.a.bits.size := transferReg.totalTransfers
      io.readport.a.bits.source := 0.U
      io.readport.a.bits.mask := Fill(c.dataBusSize, 1.U(1.W))
      io.readport.a.bits.data := 0.U
      io.readport.a.bits.corrupt := 0.U

      when(io.readport.a.ready) {
        io.readport.a.valid := true.B
        beatCnt := 0.U
        StateReg := 3.U
      }
    }
    is(3.U) { // Accept D beats, drive AXI-S
      when(io.AXIST.tready) {
        io.readport.d.ready := true.B

        when(io.readport.d.valid) {
          io.AXIST.tdata := io.readport.d.bits.data
          io.AXIST.tkeep := "hff".U
          io.AXIST.tstrb := "hff".U
          io.AXIST.tvalid := true.B

          beatCnt := beatCnt + 1.U

          when(beatCnt === (transferReg.totalTransfers - 1.U)) {
            when(transferReg.hasPartial) {
              io.AXIST.tstrb := HelperFunctions.uintToBoolVec(transferReg.partial, c.dataBusSize).asUInt
            }

            io.AXIST.tlast := true.B
            StateReg := 0.U
          }
        }
      }
    }
  }
}
