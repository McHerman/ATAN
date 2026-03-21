package ATA8

import chisel3._
import chisel3.util._

class ReadportSimple[T <: Data](private val dataType: T, val addrWidth: Int) extends Bundle {
  val addr = Output(UInt(addrWidth.W))
  val readData = Input(dataType.cloneType)
}

class Readport[T <: Data](private val dataType: T, val addrWidth: Int) extends Bundle {
  val request = Decoupled(new Bundle {
    val addr = UInt(addrWidth.W)
  })
  val response = Flipped(Valid(new Bundle {
    val readData = dataType.cloneType
  }))
}

class Writeport[T <: Data](private val dataType: T, val addrWidth: Int) extends Bundle {
  val addr  = Output(UInt(addrWidth.W))
  val data = Output(dataType.cloneType)
}



