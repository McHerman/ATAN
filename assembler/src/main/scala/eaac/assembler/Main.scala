package eaac.assembler

import java.io.{FileOutputStream, DataOutputStream}
import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}

/** CLI entry point: reads an EAAC FlatBuffer binary and writes a raw
  * instruction binary that can be streamed to the accelerator.
  *
  * Usage: eaac-asm <input.eaac> [output.bin]
  *
  * Output format: sequence of 128-bit (16-byte) little-endian instruction words.
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

    // Read FlatBuffer
    val bytes = Files.readAllBytes(Paths.get(inputPath))
    val buf = ByteBuffer.wrap(bytes)

    // Assemble
    val program = Assembler.assemble(buf)

    // Print decoded instructions
    print(PrettyPrinter.prettyPrint(program))

    val allInsts = program.allInstructions

    // Write binary output (128-bit little-endian per instruction)
    val fos = new FileOutputStream(outputPath)
    val dos = new DataOutputStream(fos)
    try {
      for (inst <- allInsts) {
        // Write 16 bytes in little-endian order
        val instBytes = inst.toByteArray.reverse // BigInt is big-endian, reverse for LE
        // Pad to 16 bytes
        val padded = new Array[Byte](16)
        System.arraycopy(instBytes, 0, padded, 0, math.min(instBytes.length, 16))
        dos.write(padded)
      }
    } finally {
      dos.close()
    }

    println(s"Written to: $outputPath")
  }
}
