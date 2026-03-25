package ATA8

import chisel3._
import chisel3.experimental._
import chisel3.util._

class FrontEnd(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val AXIST = Flipped(new AXIST_2(64, 2, 1, 1, 1))

    val exeStream   = Decoupled(new ExecuteInstIssue)
    val loadStream  = Decoupled(new LoadInstIssue)
    val storeStream = Decoupled(new StoreInstIssue)

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
  val instQueue = Module(new Queue(new InstructionPackage, 32))
  val Decoder   = Module(new Decoder)

  Reciever.io.AXIST <> io.AXIST
  instQueue.io.enq  <> Reciever.io.instructionStream
  Decoder.io.instructionStream <> instQueue.io.deq

  io.receiverDebug.valid := Reciever.io.instructionStream.valid
  io.receiverDebug.bits  := Reciever.io.instructionStream.bits.instruction

  io.decodeDebug.valid := Decoder.io.issueStream.valid
  io.decodeDebug.bits  := Decoder.io.issueStream.bits.data(1)

  // Default outputs
  io.exeStream.valid   := false.B
  io.exeStream.bits    := DontCare
  io.loadStream.valid  := false.B
  io.loadStream.bits   := DontCare
  io.storeStream.valid := false.B
  io.storeStream.bits  := DontCare

  val op = Decoder.io.issueStream.bits.op

  // Dispatch decoded instruction to the appropriate typed stream
  Decoder.io.issueStream.ready := MuxCase(false.B, Seq(
    (op === 1.U) -> io.exeStream.ready,
    (op === 2.U) -> io.loadStream.ready,
    (op === 3.U) -> io.storeStream.ready,
  ))

  when(Decoder.io.issueStream.valid) {
    switch(op) {
      is(1.U) { // Execute
        val inst = Decoder.io.issueStream.bits.data(0).asInstanceOf[ExecuteInst]
        io.exeStream.valid := true.B
        inst.addrs.zipWithIndex.foreach { case (a, i) => io.exeStream.bits.addrs(i).addr := a.addr }
        inst.addrd.zipWithIndex.foreach { case (a, i) => io.exeStream.bits.addrd(i).addr := a.addr }
        io.exeStream.bits.op        := inst.op
        io.exeStream.bits.size      := inst.size
        io.exeStream.bits.mode      := inst.mode
        io.exeStream.bits.grainSize := 0.U
      }
      is(2.U) { // Load
        val inst = Decoder.io.issueStream.bits.data(1).asInstanceOf[LoadInst]
        io.loadStream.valid := true.B
        inst.addrd.zipWithIndex.foreach { case (a, i) => io.loadStream.bits.addrd(i).addr := a.addr }
        io.loadStream.bits.op   := inst.op
        io.loadStream.bits.size := inst.size
        io.loadStream.bits.mode := inst.mode
      }
      is(3.U) { // Store
        val inst = Decoder.io.issueStream.bits.data(2).asInstanceOf[StoreInst]
        io.storeStream.valid := true.B
        inst.addrs.zipWithIndex.foreach { case (a, i) => io.storeStream.bits.addrs(i).addr := a.addr }
        io.storeStream.bits.op   := inst.op
        io.storeStream.bits.size := inst.size
        io.storeStream.bits.mode := 0.U
      }
    }
  }

  io.frontEndDebug.decodeReady   := Decoder.io.instructionStream.ready
  io.frontEndDebug.exeOutReady   := io.exeStream.ready
  io.frontEndDebug.loadOutReady  := io.loadStream.ready
  io.frontEndDebug.storeOutReady := io.storeStream.ready
}
