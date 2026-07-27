package ATA8

import chisel3._
import chisel3.util._


class XFile(implicit c: Configuration) extends Module {
  var addr_width = log2Ceil(c.grainFIFOSize)
  val io = IO(new Bundle {
    val Out = Output(Vec(c.arrayDim, new PEX(c.arithDataWidth)))
    val Activate = Input(Bool())
    val ActivateOut = Output(Bool())

    val Memport = Flipped(Decoupled(Vec(c.dataBusSize,UInt(8.W)))) //TODO: change name
    val size = Input(UInt(log2Ceil(c.arrayDim + 1).W))
  })

  val unpacker = Module(new BeatUnpacker())
  unpacker.io.beatIn <> io.Memport

  val moduleArray = Seq.fill(c.arrayDim)(Module(new BufferFIFO(c.grainFIFOSize, UInt(8.W))))

  val XACT = RegInit(VecInit.fill(c.arrayDim)(0.U(1.W)))

  moduleArray.zipWithIndex.foreach{case (module,i) =>
    if(i == 0){
      XACT(0) := io.Activate
    }else{
      XACT(i) := XACT(i-1)
    }

    module.io.WriteData.valid := unpacker.io.subRowValid
    module.io.WriteData.bits := unpacker.io.subRow(i)

    module.io.ReadData.request.valid := false.B
    module.io.ReadData.request.bits := DontCare

    when(io.size =/= 0.U){
      module.io.ReadData.request.valid := XACT(i)
    }

    when(module.io.ReadData.request.valid){
      io.Out(i).X := module.io.ReadData.response.bits.readData
    }.otherwise{
      io.Out(i).X := 0.U
    }
  }

  io.ActivateOut := XACT.last
  unpacker.io.subRowReady := VecInit(moduleArray.map(_.io.WriteData.ready)).reduceTree(_ && _)

}

