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
    // Known as soon as the *next* op's instruction is fetched by
    // SysController -- well before io.size (from Grain's own inReg, which
    // only updates once this op is fully loaded and dispatched). The
    // unpacker needs the real size *while* Memport is actively draining,
    // not after.
    val loadSize = Input(UInt(log2Ceil(c.arrayDim + 1).W))
  })

  val unpacker = Module(new BeatUnpacker())
  unpacker.io.beatIn <> io.Memport
  unpacker.io.size := io.loadSize

  val dbgCycle = RegInit(0.U(32.W))
  dbgCycle := dbgCycle + 1.U
  val dbgLoadCount = RegInit(0.U(32.W))
  when(io.Memport.fire) {
    dbgLoadCount := dbgLoadCount + 1.U

    if(c.verbosePrint) {
      printf(p"cyc=${dbgCycle} [xfile-beat] #${dbgLoadCount} loadSize=${io.loadSize} bytes=${io.Memport.bits}\n")
    }
  }

  val moduleArray = Seq.fill(c.arrayDim)(Module(new BufferStack(c.grainFIFOSize, UInt(8.W))))

  val XACT = RegInit(VecInit.fill(c.arrayDim)(0.U(1.W)))

  moduleArray.zipWithIndex.foreach{case (module,i) =>
    module.io.size := io.loadSize

    if(i == 0){
      XACT(0) := io.Activate
    }else{
      XACT(i) := XACT(i-1)
    }

    module.io.WriteData.valid := unpacker.io.subRowValid
    module.io.WriteData.bits := unpacker.io.subRow(i)
    
    //module.io.WriteData.valid := io.Memport.valid 
    //module.io.WriteData.bits := io.Memport.bits(i)
    
    module.io.ReadData.request.valid := false.B
    module.io.ReadData.request.bits := DontCare

    when(io.size =/= 0.U){
      module.io.ReadData.request.valid := XACT(i)
    }

    when(module.io.ReadData.response.valid){
      io.Out(i).X := module.io.ReadData.response.bits.readData
    }.otherwise{
      io.Out(i).X := 0.U
    }
  }

  io.ActivateOut := XACT.last

  //io.Memport.ready := VecInit(moduleArray.map(_.io.WriteData.ready)).reduceTree(_ && _)
  unpacker.io.subRowReady := VecInit(moduleArray.map(_.io.WriteData.ready)).reduceTree(_ && _)
}

