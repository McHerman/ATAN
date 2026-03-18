import chisel3._
import chisel3.util.log2Ceil

package object ATA8 {

  case class Configuration(
    scratchpadSize: Int,
    bufferReadPorts: Int,
    bufferWritePorts: Int,
    grainDim: Int, // Multiplum of dataBusSize
    sysDim: Int,
    grainFIFOSize: Int,
    grainACCUSize: Int,
    arithDataWidth: Int,
    accDataWidth: Int,
    modeWidth: Int,
    tagCount: Int,
    addrWidth: Int,
    dataBusSize: Int
  ){
    val tagWidth =  log2Ceil(tagCount)
    val grainSizeWidth = log2Ceil(grainDim)
    val accDataBytes = accDataWidth / 8
    require(accDataWidth >= arithDataWidth, "accDataWidth must be >= arithDataWidth")
    require(accDataWidth % 8 == 0, "accDataWidth must be a multiple of 8")
  }

  object Configuration {
    def default(): Configuration = {
      Configuration(
        4096, // scratchpadSize
        3,    // bufferReadPorts
        2,    // bufferWritePorts
        1,    // grainDim
        1,    // sysDim
        64,  // grainFIFOSize
        64,  // grainACCUSize
        8,    // arithDataWidth
        32,   // accDataWidth
        1,    // modeWidth
        8,    // tagCount
        16,   // addrWidth
        8     // dataBusSize
      )
    }
    def sys16(): Configuration = {
      Configuration(
        4096, // scratchpadSize
        3,    // bufferReadPorts
        2,    // bufferWritePorts
        2,    // grainDim
        1,    // sysDim
        64,  // grainFIFOSize
        64,  // grainACCUSize
        8,    // arithDataWidth
        32,   // accDataWidth
        1,    // modeWidth
        8,    // tagCount
        16,   // addrWidth
        8     // dataBusSize
      )
    }
    def sys16_largeMem(): Configuration = {
      Configuration(
        16384, // scratchpadSize
        3,    // bufferReadPorts
        2,    // bufferWritePorts
        2,    // grainDim
        1,    // sysDim
        64,  // grainFIFOSize
        64,  // grainACCUSize
        8,    // arithDataWidth
        32,   // accDataWidth
        1,    // modeWidth
        16,    // tagCount
        16,   // addrWidth
        8     // dataBusSize
      )
    }
    def sys32(): Configuration = {
      Configuration(
        4096, // scratchpadSize
        3,    // bufferReadPorts
        2,    // bufferWritePorts
        4,    // grainDim
        1,    // sysDim
        64,  // grainFIFOSize
        64,  // grainACCUSize
        8,    // arithDataWidth
        32,   // accDataWidth
        1,    // modeWidth
        8,    // tagCount
        16,   // addrWidth
        8     // dataBusSize
      )
    }
    def test(): Configuration = {
      Configuration(
        4096, // scratchpadSize
        3,    // bufferReadPorts
        2,    // bufferWritePorts
        1,    // grainDim
        1,    // sysDim
        64,  // grainFIFOSize
        64,  // grainACCUSize
        8,    // arithDataWidth
        32,   // accDataWidth
        1,    // modeWidth
        8,    // tagCount
        16,   // addrWidth
        8     // dataBusSize
      )
    }
    def test16(): Configuration = {
      Configuration(
        4096, // scratchpadSize
        3,    // bufferReadPorts
        2,    // bufferWritePorts
        2,    // grainDim
        1,    // sysDim
        64,  // grainFIFOSize
        64,  // grainACCUSize
        8,    // arithDataWidth
        32,   // accDataWidth
        1,    // modeWidth
        8,    // tagCount
        16,   // addrWidth
        8     // dataBusSize
      )
    }
    def sys16_largeMem_test(): Configuration = {
      Configuration(
        16384, // scratchpadSize
        3,    // bufferReadPorts
        2,    // bufferWritePorts
        2,    // grainDim
        1,    // sysDim
        64,  // grainFIFOSize
        64,  // grainACCUSize
        8,    // arithDataWidth
        32,   // accDataWidth
        1,    // modeWidth
        16,    // tagCount
        16,   // addrWidth
        8     // dataBusSize
      )
    }
  }
}
