package ATA8

import chisel3._
import chisel3.util._

class Scratchpad(writeports: Int)(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
  	//val Readport = Vec(c.bufferReadPorts, Flipped(new ReadportBuf(c.arithDataWidth,10)))
  	//val Writeport = Vec(c.bufferWritePorts, Flipped(new WriteportBuf(c.arithDataWidth,10)))
  	//val Writeport = Vec(writeports, Flipped(Decoupled(new Writeport(Vec(c.grainDim,UInt(c.arithDataWidth.W)),16))))
    //val Readport = Vec(c.bufferReadPorts, Flipped(new Readport(Vec(c.grainDim,UInt(c.arithDataWidth.W)),16)))
    val Writeport = Vec(writeports, Flipped(Decoupled(new Writeport(new Bundle{val writeData = Vec(c.dataBusSize,UInt(8.W)); val strb = Vec(c.dataBusSize, Bool())},16))))
    val Readport = Vec(c.bufferReadPorts, Flipped(new Readport(Vec(c.dataBusSize,UInt(8.W)),16)))
  })

  val numOfBanks = 16
  val memBanks = Seq.fill(numOfBanks)(SyncReadMem(1024, UInt((c.dataBusSize * 8).W)))

  // Write logic
  io.Writeport.foreach { port =>
    port.ready := true.B

    val bankIdx = port.bits.addr(log2Ceil(numOfBanks) - 1, 0)
    val bankAddr = port.bits.addr >> log2Ceil(numOfBanks)

    memBanks.zipWithIndex.foreach { case (mem, i) =>
      when(port.fire && bankIdx === i.U) {
        mem.write(bankAddr, port.bits.data.writeData.asUInt)
      }
    }
  }

  // Read logic
  io.Readport.foreach { port =>
    port.request.ready := true.B

    val bankIdx = port.request.bits.addr(log2Ceil(numOfBanks) - 1, 0)
    val bankAddr = port.request.bits.addr >> log2Ceil(numOfBanks)

    val readResults = VecInit(memBanks.map(_.read(bankAddr, port.request.fire)))
    val bankIdxReg = RegNext(bankIdx)

    port.response.bits.readData := readResults(bankIdxReg).asTypeOf(Vec(c.dataBusSize, UInt(8.W)))
    port.response.valid := RegNext(port.request.fire)
  }
}
