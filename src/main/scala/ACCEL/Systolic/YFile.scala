package ATA8

import chisel3._
import chisel3.util._

class YFile(implicit c: Configuration) extends Module {
  var addr_width = log2Ceil(c.grainFIFOSize)
  val io = IO(new Bundle {
    val Out = Output(Vec(c.dataBusSize, new PEY(c.accDataWidth)))
    val Activate = Input(Bool())
    val ActivateOut = Output(Bool())
    val Shift = Input(Bool())

    val Memport = Flipped(Decoupled(Vec(c.dataBusSize,UInt(8.W)))) //TODO: change name 
    val State = Input(UInt(1.W))
    val size = Input(UInt(log2Ceil(c.dataBusSize + 1).W))
  })

 
  val moduleArray = Seq.fill(c.dataBusSize)(Module(new BufferFIFO(c.grainFIFOSize, UInt(8.W))))

  val YACT = RegInit(VecInit.fill(c.dataBusSize)(0.U(1.W)))

  moduleArray.zipWithIndex.foreach { case (module, i) =>
    if(i == 0){
      YACT(0) := io.Activate
    }else{
      YACT(i) := YACT(i-1)
    }

    when(module.io.ReadData.response.valid){
      io.Out(i).Y := module.io.ReadData.response.bits.readData
    }.otherwise{
      io.Out(i).Y := 0.U
    }

    module.io.WriteData.valid := io.Memport.valid
    module.io.WriteData.bits := io.Memport.bits(i)

    module.io.ReadData.request.valid := false.B
    module.io.ReadData.request.bits := DontCare

    when(io.size =/= 0.U){
      switch(io.State){
        is(0.U){
          module.io.ReadData.request.valid := io.Shift
        }
        is(1.U){
          module.io.ReadData.request.valid := YACT(i)
        }
      }
    }
  }

  io.ActivateOut := YACT.last

  io.Memport.ready := VecInit(moduleArray.map(_.io.WriteData.ready)).reduceTree(_ && _)
}
