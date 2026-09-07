package ATA8

import chisel3._
import chisel3.experimental._
import chisel3.util._

class FrontEnd(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val AXIST = Flipped(new AXIST_2(128, 2, 1, 1, 1))

    val exeStream     = Decoupled(new ExecuteInst)
    val loadStream    = Decoupled(new LoadInst)
    val storeStream   = Decoupled(new StoreInst)
    val dmaStream     = Decoupled(new DMAInst)
    val semProgStream = Decoupled(new SemProgInst)

    val receiverDebug = Valid(UInt(64.W))
    val decodeDebug   = Valid(new LoadInst)

    val frontEndDebug = Output(new Bundle {
      val decodeReady    = Bool()
      val exeOutReady    = Bool()
      val loadOutReady   = Bool()
      val storeOutReady  = Bool()
    })
  })

  val Reciever  = Module(new InstReciever)
  val beatQueue = Module(new Queue(new InstBeat, 256))
  val Realigner = Module(new InstRealigner)
  val Decoder   = Module(new Decoder)
  val Dispatch  = Module(new Dispatch)

  Reciever.io.AXIST            <> io.AXIST
  beatQueue.io.enq             <> Reciever.io.beatStream
  Realigner.io.beatIn          <> beatQueue.io.deq
  Decoder.io.instructionStream <> Realigner.io.instOut
  Dispatch.io.in               <> Decoder.io.issueStream

  io.exeStream     <> Dispatch.io.exeStream
  io.loadStream    <> Dispatch.io.loadStream
  io.storeStream   <> Dispatch.io.storeStream
  io.dmaStream     <> Dispatch.io.dmaStream
  io.semProgStream <> Dispatch.io.semProgStream

  io.receiverDebug.valid := Reciever.io.beatStream.valid
  io.receiverDebug.bits  := Reciever.io.beatStream.bits.data(63, 0)

  io.decodeDebug.valid := Decoder.io.issueStream.valid
  io.decodeDebug.bits  := Decoder.io.issueStream.bits.data(1).asInstanceOf[LoadInst]

  io.frontEndDebug.decodeReady   := Decoder.io.instructionStream.ready
  io.frontEndDebug.exeOutReady   := io.exeStream.ready
  io.frontEndDebug.loadOutReady  := io.loadStream.ready
  io.frontEndDebug.storeOutReady := io.storeStream.ready
}
