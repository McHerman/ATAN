package ATA8

import chisel3._
import chisel3.util._


class SemStateEntry(implicit c: Configuration) extends Bundle {
  val done = Bool()
}

class TriggerRow(implicit c: Configuration) extends Bundle {
  val valid      = Bool()
  val guardCount = UInt(log2Ceil(c.triggerMaxGuards + 1).W)
  val guardAddrs = Vec(c.triggerMaxGuards, UInt(c.fusedSemAddrWidth.W))
}

class TriggerFire(implicit c: Configuration) extends Bundle {
  val rowIdx  = UInt(log2Ceil(c.triggerRows).W)
  val payload = new SemProgPayload
}

class TriggerEvent(implicit c: Configuration) extends Bundle {
  val addr      = UInt(c.addrWidth.W)
  val eventCode = UInt(3.W)
}


class TriggerSystem(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val load       = Flipped(Decoupled(new SemProgInst))
    val fire       = Decoupled(new TriggerFire)
    val inputEvent = Flipped(Valid(new TriggerEvent))
  })

  val state = RegInit(VecInit(Seq.fill(c.fusedSemStateSize)(0.U.asTypeOf(new SemStateEntry))))
  val opMem = SyncReadMem(c.triggerRows, new SemProgPayload)
  val rows  = RegInit(VecInit(Seq.fill(c.triggerRows)(0.U.asTypeOf(new TriggerRow))))

  val ready = VecInit(rows.map { row =>
    val depsOk = row.guardAddrs.zipWithIndex.map { case (addr, i) =>
      val enabled = i.U < row.guardCount
      !enabled || state(addr).done
    }
    row.valid && depsOk.reduce(_ && _)
  })

  val firePending   = RegInit(false.B)
  val pendingRowIdx = Reg(UInt(log2Ceil(c.triggerRows).W))

  val selecting   = ready.reduce(_ || _) && !firePending
  val selectedIdx = PriorityEncoder(ready)

  val readAddr = WireDefault(selectedIdx)
  when(firePending) { readAddr := pendingRowIdx }
  val payloadRead = opMem.read(readAddr, selecting || firePending)

  when(selecting) {
    firePending   := true.B
    pendingRowIdx := selectedIdx
  }

  io.fire.valid        := firePending
  io.fire.bits.rowIdx  := pendingRowIdx
  io.fire.bits.payload := payloadRead

  when(io.fire.fire) {
    rows(pendingRowIdx).valid := false.B
    firePending := false.B
  }

  val freeMask    = VecInit(rows.map(!_.valid))
  val freeIdx     = PriorityEncoder(freeMask)
  val hasFreeSlot = freeMask.reduce(_ || _)

  def fusedDest(semAddr: UInt, gen: UInt): UInt = {
    val gw = c.semaphoreGenerationWidth
    val raw =
      if (gw == 0) semAddr
      else Cat(semAddr, gen(gw - 1, 0))
    raw(c.fusedSemAddrWidth - 1, 0)
  }

  when(io.inputEvent.valid && io.inputEvent.bits.eventCode === SemaphoreEventCodes.Complete) {
    state(io.inputEvent.bits.addr(c.fusedSemAddrWidth - 1, 0)).done := true.B
  }

  io.load.ready := hasFreeSlot
  when(io.load.fire) {
    rows(freeIdx).valid      := true.B
    rows(freeIdx).guardCount := io.load.bits.row.depCount
    rows(freeIdx).guardAddrs.zipWithIndex.foreach { case (slot, i) =>
      slot :=
        (if (i < eaac.shared.InstructionSet.MaxSemDeps)
          io.load.bits.row.depAddrs(i)(c.fusedSemAddrWidth - 1, 0)
        else
          0.U)
    }
    opMem.write(freeIdx, io.load.bits.payload)
    state(fusedDest(io.load.bits.payload.semAddr, io.load.bits.payload.generation)).done := false.B
  }
}
