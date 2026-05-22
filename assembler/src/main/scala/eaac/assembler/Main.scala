package eaac.assembler

import java.io.{FileOutputStream, DataOutputStream}
import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}

/** CLI entry point: reads an EAAC FlatBuffer binary and writes a raw
  * instruction binary that can be streamed to the accelerator.
  *
  * Usage: eaac-asm <input.eaac> [output.bin]
  *
  * Output format: sequence of 128-bit (16-byte) little-endian beats.  The
  * accelerator's front-end realigner re-extracts variable-length (1-, 2-,
  * or 3-slot) instructions from the slot-packed beat stream.
  */
object Main {
  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      System.err.println("Usage: eaac-asm <input.eaac> [output.bin]")
      sys.exit(1)
    }

    val inputPath = args(0)
    val outputPath = if (args.length > 1) args(1)
      else inputPath.replaceAll("\\.[^.]+$", "") + ".bin"

    val bytes = Files.readAllBytes(Paths.get(inputPath))
    val buf = ByteBuffer.wrap(bytes)

    val program = new Assembler(AssemblerConfig(verbose = true)).assemble(buf)
    val allBeats = program.allInstructions   // packed 128-bit beats

    val fos = new FileOutputStream(outputPath)
    val dos = new DataOutputStream(fos)
    try {
      for (beat <- allBeats) {
        // Write 16 bytes in little-endian order.
        val beatBytes = beat.toByteArray.reverse  // BigInt is big-endian
        val padded = new Array[Byte](16)
        System.arraycopy(beatBytes, 0, padded, 0, math.min(beatBytes.length, 16))
        dos.write(padded)
      }
    } finally {
      dos.close()
    }

    println(s"Wrote ${allBeats.length} beats to: $outputPath")
  }
}
