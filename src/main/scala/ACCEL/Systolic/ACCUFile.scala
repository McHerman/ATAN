package ATA8

import chisel3._
import chisel3.util._

class ACCUFile(val hasDelay: Boolean, val stack: Boolean)(implicit c: Configuration) extends Module {
  var addr_width = log2Ceil(c.grainACCUSize)
  val io = IO(new Bundle {
    val In = Input(Vec(c.arrayDim, new PEY(c.accDataWidth)))
    val Activate = Input(Bool())
    val ActivateOut = Output(Bool())
    val Shift = Input(Bool())

    val Readport = Flipped(new Readport(Vec(c.arrayDim,UInt(c.accDataWidth.W))))
    val size = Input(UInt(log2Ceil(c.arrayDim + 1).W))

    val State = Input(UInt(1.W))
  })

  io.Readport.request.ready := true.B
  io.Readport.response.valid := true.B
  io.Readport.response.bits := DontCare

  //if(stack){
  //  val moduleArray = Seq.fill(c.arrayDim)(Module(new BufferStack(c.grainFIFOSize, UInt(c.accDataWidth.W))))
  //}else{
  //  val moduleArray = Seq.fill(c.arrayDim)(Module(new BufferFIFO(c.grainFIFOSize, UInt(c.accDataWidth.W))))
  //}
  //
  val moduleArray = Seq.fill(c.arrayDim)(Module(new BufferStack(c.grainFIFOSize, UInt(c.accDataWidth.W))))

  val ACCUAct    = RegInit(VecInit.fill(c.arrayDim)(0.U(1.W)))
  val activateIn = Wire(Bool())
  val ActDReg    = RegInit(false.B)

  if (hasDelay) {
    ActDReg    := io.Activate
    activateIn := ActDReg
  } else {
    activateIn := io.Activate
  }

  // Hold the read port off until every per-row stack has reached `size`,
  // then fire all of them in lockstep so the parallel readout stays aligned.
  val allReady = moduleArray.map(_.io.ReadData.request.ready).reduce(_ && _)
  io.Readport.request.ready := allReady

  val readFire = io.Readport.request.valid && allReady
  io.Readport.response.valid := readFire

  moduleArray.zipWithIndex.foreach { case (module, i) =>
    if(stack) {
      module.io.size := io.size
    } 

    if (i == 0) {
      ACCUAct(0) := activateIn
    } else {
      ACCUAct(i) := ACCUAct(i - 1)
    }

    module.io.WriteData.valid := false.B
    when(io.size =/= 0.U) {
      switch(io.State) {
        is(0.U) { module.io.WriteData.valid := ACCUAct(i) }
        is(1.U) { module.io.WriteData.valid := io.Shift }
      }
    }
    module.io.WriteData.bits := io.In(i).Y

    module.io.ReadData.request.valid := readFire
    module.io.ReadData.request.bits  := DontCare

    io.Readport.response.bits.readData(i) := module.io.ReadData.response.bits.readData
  }

  io.ActivateOut := ACCUAct.last
}
