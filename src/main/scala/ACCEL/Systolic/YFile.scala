package ATA8

import chisel3._
import chisel3.util._

class YFile(implicit c: Configuration) extends Module {
  var addr_width = log2Ceil(c.grainFIFOSize)
  val io = IO(new Bundle {
    val Out = Output(Vec(c.arrayDim, new PEY(c.accDataWidth)))
    val Activate = Input(Bool())
    val ActivateOut = Output(Bool())
    val Shift = Input(Bool())

    val Memport = Flipped(Decoupled(Vec(c.dataBusSize,UInt(8.W)))) //TODO: change name
    val State = Input(UInt(1.W))
    val size = Input(UInt(log2Ceil(c.arrayDim + 1).W))
    // See XFile.io.loadSize: known earlier than io.size (which only updates
    // once Grain's own inReg is set, after this op's Memport has already
    // drained), needed so the unpacker sizes rows correctly while loading.
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
      printf(p"cyc=${dbgCycle} [yfile-beat] #${dbgLoadCount} loadSize=${io.loadSize} bytes=${io.Memport.bits}\n")
    }
  }

  val moduleArray = Seq.fill(c.arrayDim)(Module(new BufferStack(c.grainFIFOSize, UInt(8.W))))
  //val moduleArray = Seq.fill(c.arrayDim)(Module(new BufferFIFO(c.grainFIFOSize, UInt(8.W))))

  val YACT = RegInit(VecInit.fill(c.arrayDim)(0.U(1.W)))

  moduleArray.zipWithIndex.foreach { case (module, i) =>
    module.io.size := io.loadSize

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

    module.io.WriteData.valid := unpacker.io.subRowValid
    module.io.WriteData.bits := unpacker.io.subRow(i)
    
    //module.io.WriteData.valid := io.Memport.valid 
    //module.io.WriteData.bits := io.Memport.bits(i)

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

  //io.Memport.ready := VecInit(moduleArray.map(_.io.WriteData.ready)).reduceTree(_ && _)
  unpacker.io.subRowReady := VecInit(moduleArray.map(_.io.WriteData.ready)).reduceTree(_ && _)
}
