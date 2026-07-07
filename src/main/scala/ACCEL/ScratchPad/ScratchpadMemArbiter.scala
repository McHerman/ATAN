package ATA8

import chisel3._
import chisel3.util._

/**
 * Fixed-priority arbiter for the scratchpad's single read/write port pair.
 *
 * Port 0 wins on both dimensions when both ports contend simultaneously.
 * Intended use: wPorts(0)/rPorts(0) = regular write/read TL handler (high
 * priority), wPorts(1)/rPorts(1) = atomic handler (low priority).
 *
 * The scratchpad uses SyncReadMem (1-cycle read latency), so a grant register
 * tracks which port fired last so the response is steered to the right client.
 */
class ScratchpadMemArbiter(implicit c: MemBusConfig) extends Module {

  val io = IO(new Bundle {
    // Scratchpad-facing (single port pair)
    val wMem = Decoupled(new Writeport(new Bundle {
      val writeData = Vec(c.dataBusSize, UInt(8.W))
      val strb      = Vec(c.dataBusSize, Bool())
    }, 16))
    val rMem = new Readport(Vec(c.dataBusSize, UInt(c.arithDataWidth.W)), Some(16))

    // Handler-facing (2 clients, index 0 = higher priority)
    val wPorts = Vec(2, Flipped(Decoupled(new Writeport(new Bundle {
      val writeData = Vec(c.dataBusSize, UInt(8.W))
      val strb      = Vec(c.dataBusSize, Bool())
    }, 16))))
    val rPorts = Vec(2, Flipped(new Readport(Vec(c.dataBusSize, UInt(c.arithDataWidth.W)), Some(16))))
  })

  // ── Defaults ──────────────────────────────────────────────────────────────
  io.wMem.valid := false.B
  io.wMem.bits  := DontCare
  io.wPorts.foreach(_.ready := false.B)

  io.rMem.request.valid := false.B
  io.rMem.request.bits  := DontCare
  io.rPorts.foreach { p =>
    p.request.ready          := false.B
    p.response.valid         := false.B
    p.response.bits.readData := DontCare
  }

  // ── Write arbitration ─────────────────────────────────────────────────────
  when(io.wPorts(0).valid) {
    io.wMem.valid      := true.B
    io.wMem.bits       := io.wPorts(0).bits
    io.wPorts(0).ready := io.wMem.ready
  }.elsewhen(io.wPorts(1).valid) {
    io.wMem.valid      := true.B
    io.wMem.bits       := io.wPorts(1).bits
    io.wPorts(1).ready := io.wMem.ready
  }

  // ── Read arbitration ──────────────────────────────────────────────────────
  // false = port 0 fired last, true = port 1 fired last
  val readGrant = RegInit(false.B)

  when(io.rPorts(0).request.valid) {
    io.rMem.request.valid         := true.B
    io.rMem.request.bits.addr.get := io.rPorts(0).request.bits.addr.get
    io.rPorts(0).request.ready    := io.rMem.request.ready
    when(io.rMem.request.fire) { readGrant := false.B }
  }.elsewhen(io.rPorts(1).request.valid) {
    io.rMem.request.valid         := true.B
    io.rMem.request.bits.addr.get := io.rPorts(1).request.bits.addr.get
    io.rPorts(1).request.ready    := io.rMem.request.ready
    when(io.rMem.request.fire) { readGrant := true.B }
  }

  // Steer the 1-cycle-delayed response back to the port that fired
  when(readGrant) {
    io.rPorts(1).response.valid         := io.rMem.response.valid
    io.rPorts(1).response.bits.readData := io.rMem.response.bits.readData
  }.otherwise {
    io.rPorts(0).response.valid         := io.rMem.response.valid
    io.rPorts(0).response.bits.readData := io.rMem.response.bits.readData
  }
}
