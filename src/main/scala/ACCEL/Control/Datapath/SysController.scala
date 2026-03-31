package ATA8

import chisel3._
import chisel3.util._

object VectorFillerFunctions {
  private def truncate(value: UInt, y: Int): UInt = Mux(value > y.U, y.U, value)

  def buildTree(n: UInt, x: Int, y: Int): Vec[UInt] = {
    def recurse(currentValue: UInt, subtractor: UInt, depth: Int, maxDepth: Int): Vec[UInt] = {
      if (depth == maxDepth) {
        VecInit(Seq(truncate(currentValue, y)))
      } else {
        val nextSubtractor  = subtractor >> 1
        val withSubtraction = recurse(Mux(currentValue >= subtractor, currentValue - subtractor, 0.U), nextSubtractor, depth + 1, maxDepth)
        val withoutSubtraction = recurse(currentValue, nextSubtractor, depth + 1, maxDepth)
        VecInit(withoutSubtraction ++ withSubtraction)
      }
    }
    val initialSubtractor = ((x * y) / 2).U
    recurse(n, initialSubtractor, 0, log2Ceil(x))
  }
}

class SysController(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val in  = new Readport(new ExecuteInstIssue)

    /*
    val dmaRead  = Vec(2, Vec(c.grainDim, new DMARead()))
    val dmaWrite = Vec(c.grainDim, new DMAWrite())
    */

    val dmaRead  = Vec(2, Vec(c.grainDim, new dmaInterface(hasSemaphore = true)))
    val dmaWrite = Vec(c.grainDim, new dmaInterface(hasSemaphore = true))

    val out          = Decoupled(new SysOP)
    val sysCompleted = Input(Bool())
    val debug        = new ExeDebug
  })

  val opbuffer   = Module(new BufferFIFO(8, new SysOP))
  // TODO Replace with sem equivalent.
  val readBuffer = Module(new BufferFIFO(8, new Bundle { val addrPkg = new addrPkg; val size = UInt(8.W) }))

  io.in.request.valid := false.B
  io.in.request.bits  := DontCare

  io.out.valid := false.B
  io.out.bits  := DontCare


  /*
  io.dmaRead(0).foreach { e => e.request.valid := false.B; e.request.bits := DontCare; e.response.ready := false.B }
  io.dmaRead(1).foreach { e => e.request.valid := false.B; e.request.bits := DontCare; e.response.ready := false.B }
  io.dmaWrite.foreach   { e => e.request.valid := false.B; e.request.bits := DontCare; e.response.ready := false.B }
  */

  io.dmaRead(0).foreach { e => e.descriptor.valid := false.B; e.descriptor.bits := DontCare; e.response.ready := false.B }
  io.dmaRead(1).foreach { e => e.descriptor.valid := false.B; e.descriptor.bits := DontCare; e.response.ready := false.B }
  io.dmaWrite.foreach   { e => e.descriptor.valid := false.B; e.descriptor.bits := DontCare; e.response.ready := false.B }


  opbuffer.io.WriteData.valid  := false.B
  opbuffer.io.WriteData.bits   := DontCare
  opbuffer.io.ReadData.request.valid := false.B
  opbuffer.io.ReadData.request.bits  := DontCare

  readBuffer.io.WriteData.valid  := false.B
  readBuffer.io.WriteData.bits   := DontCare
  readBuffer.io.ReadData.request.valid := false.B
  readBuffer.io.ReadData.request.bits  := DontCare

  val reg      = Reg(new ExecuteInstIssue)
  val StateReg = RegInit(0.U(4.W))

  /// DEBUG ///
  io.debug.state := StateReg

  switch(StateReg) {
    is(0.U) { // Receive instruction
      when(io.in.request.ready) {
        io.in.request.valid := true.B
        when(io.in.response.valid) {
          reg      := io.in.response.bits.readData
          StateReg := 1.U
        }
      }
    }
    is(1.U) { // Invoke read DMAs
      /*
      val readySignals = VecInit(io.dmaRead.flatten.map(_.request.ready))
      when(readySignals.reduceTree(_ && _)) {
        (reg.addrs zip io.dmaRead).foreach { case (addrs, dmaSeq) =>
          val readSizes = VectorFillerFunctions.buildTree(reg.size, c.grainDim, c.dataBusSize)
          (dmaSeq zip readSizes.zipWithIndex).foreach { case (dma, (size, index)) =>
            val addrSum = if (index == 0) 0.U else readSizes.take(index).reduce(_ + _)
            dma.request.bits.addr      := addrs.addr + addrSum
            dma.request.bits.burstSize := size
            dma.request.bits.burstCnt  := reg.size
            dma.request.valid          := true.B
          }
        }
        StateReg := 2.U
      }
      */

      val readySignals = VecInit(io.dmaRead.flatten.map(_.descriptor.ready))
      when(readySignals.reduceTree(_ && _)) {
        (reg.addrs zip io.dmaRead).foreach { case (addrs, dmaSeq) =>
          val readSizes = VectorFillerFunctions.buildTree(reg.size, c.grainDim, c.dataBusSize)
          (dmaSeq zip readSizes.zipWithIndex).foreach { case (dma, (size, index)) =>
            val addrSum = if (index == 0) 0.U else readSizes.take(index).reduce(_ + _)
            dma.descriptor.bits(0).addr := addrs.addr + addrSum
            dma.descriptor.bits(0).size := size
            dma.descriptor.valid := true.B
            dma.descriptor.bits(0).writeEn := false.B
            dma.descriptor.bits(0).source := DontCare
            dma.descriptor.bits(0).sink := DontCare

            dma.descriptor.bits(0).semaphore.get.semEnable := addrs.sem.valid
            dma.descriptor.bits(0).semaphore.get.semAddr := addrs.sem.bits.addr
            dma.descriptor.bits(0).semaphore.get.semStepSize := addrs.sem.bits.stepSize.bits
          }
        }
        StateReg := 2.U
      }




    }
    is(2.U) { // Wait for read DMAs
      val completedSignals = VecInit(io.dmaRead.flatten.map { c => c.response.valid && !(c.response.bits.denied.asBool || c.response.bits.corrupt.asBool) })
      when(completedSignals.reduceTree(_ && _)) {
        io.dmaRead.flatten.foreach { e => e.response.ready := true.B }
        StateReg := 3.U
      }
    }
    is(3.U) { // Push to opbuffer and readBuffer
      when(opbuffer.io.WriteData.ready && readBuffer.io.WriteData.ready) {
        opbuffer.io.WriteData.valid       := true.B
        opbuffer.io.WriteData.bits.mode   := reg.mode
        opbuffer.io.WriteData.bits.size   := reg.size
        opbuffer.io.WriteData.bits.sizes  := VectorFillerFunctions.buildTree(reg.size, c.grainDim, c.dataBusSize)

        readBuffer.io.WriteData.valid      := true.B
        readBuffer.io.WriteData.bits.addrPkg  := reg.addrd(0)
        readBuffer.io.WriteData.bits.size  := reg.size

        StateReg := 0.U
      }
    }
  }

  // Write-back when systolic array completes
  when(io.sysCompleted && readBuffer.io.ReadData.request.ready) {
    val readySignals = VecInit(io.dmaWrite.map(_.descriptor.ready))
    when(readySignals.reduceTree(_ && _)) {
      readBuffer.io.ReadData.request.valid := true.B
      val op         = readBuffer.io.ReadData.response.bits.readData
      val writeSizes = VectorFillerFunctions.buildTree(op.size, c.grainDim, c.dataBusSize)
      (io.dmaWrite zip writeSizes.zipWithIndex).foreach { case (dma, (size, index)) =>
        val addrSum = if (index == 0) 0.U else writeSizes.take(index).reduce(_ + _)

        /*
        dma.request.bits.addr      := op.addr + addrSum
        dma.request.bits.burstSize := size
        dma.request.bits.burstCnt  := op.size
        dma.request.valid          := true.B
        */

        dma.descriptor.bits(0).addr := op.addrPkg.addr + addrSum
        dma.descriptor.bits(0).size := op.size
        dma.descriptor.valid := true.B

        dma.descriptor.bits(0).semaphore.get.semEnable := op.addrPkg.sem.valid
        dma.descriptor.bits(0).semaphore.get.semAddr := op.addrPkg.sem.bits.addr
        dma.descriptor.bits(0).semaphore.get.semStepSize := op.addrPkg.sem.bits.stepSize.bits
      }
    }
  }

  // Acknowledge write DMAs when all complete
  val writeCompletedSignals = VecInit(io.dmaWrite.map { c => c.response.valid && !c.response.bits.denied.asBool && !c.response.bits.corrupt.asBool })
  when(writeCompletedSignals.reduceTree(_ && _)) {
    io.dmaWrite.foreach { e => e.response.ready := true.B }
  }

  // Feed SysCtrl from opbuffer
  when(opbuffer.io.ReadData.request.ready && io.out.ready) {
    io.out.valid                       := true.B
    opbuffer.io.ReadData.request.valid := true.B
    io.out.bits                        <> opbuffer.io.ReadData.response.bits.readData
  }
}
