import chisel3._
import chisel3.util.log2Ceil

package object Common {

  case class Configuration(
      bufferSize: Int,
      frameSize: Int,
      bufferWidth: Int,
      sourceWidth: Int
  ) {
    val busWidth = 8 * bufferWidth
    val maskWidth = bufferWidth
    // val addrWidth = log2Ceil(bufferSize)
    val addrWidth = 24
  }

  object Configuration {
    def default(): Configuration = {
      Configuration(
        307200, // bufferSize
        307200, // frameSize
        2, // bufferWidth 2 bytes
        12 // sourceWidth
      )
    }
  }

  implicit val defaultConfig: Configuration = Configuration.default()
}
