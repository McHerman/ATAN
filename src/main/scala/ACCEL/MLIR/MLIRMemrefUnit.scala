// Drop into ATAN at: src/main/scala/ACCEL/MLIR/MLIRMemrefUnit.scala
//
// Generalized memref-shaped unit for hlstool-generated kernels with N input
// memrefs and M output memrefs. Subsumes the previous MLIRMemrefWrapper
// (N=1, M=1, used for examples/scale.mlir) and MLIRConv2DWrapper (N=2, M=1,
// used for examples/conv_2d.mlir).
//
// Kernel port naming follows hlstool's `--dynamic-hw` flow convention:
//   in{i}_ld0_{addr,addr_valid,addr_ready,data,data_valid,data_ready}
//     for read ports i in [0, N)
//   in{N+j}_st0 (packed {addr, data}), in{N+j}_st0_{valid,ready,done_valid,
//     done_ready} for write ports j in [0, M)
//   in{N+M}_{valid,ready} = start token
//   out0_{valid,ready} = done token
//
// Regenerate kernels via:
//   hlstool examples/<kernel>.mlir --dynamic-hw --top-level-function=<name> \
//       --verilog -o examples/<kernel>.sv
package ATA8

import chisel3._
import chisel3.util._

/** Instruction shape: N source addrs + M destination addrs. */
class MLIRMemrefInst(addrsSize: Int, addrdSize: Int)(implicit c: Configuration)
  extends InstBaseExtended(addrsSize, addrdSize)

// Plain Scala case classes — these are containers for the BlackBox's hardware
// wires, not Chisel Bundles (mixed-direction ports inside a Bundle is
// awkward, and ExtModule's IO is naturally a flat list anyway).
final case class KernelReadWires(
    addr:      UInt, addrValid: Bool, addrReady: Bool,
    data:      UInt, dataValid: Bool, dataReady: Bool,
)
final case class KernelWriteWires(
    st:        UInt, stValid: Bool, stReady: Bool,
    doneValid: Bool, doneReady: Bool,
    addrW:     Int,
)

/**
 * Parameterised ExtModule whose port shape matches what hlstool's dynamic-hw
 * flow emits for any function with `inputSizes.length` input memrefs and
 * `outputSizes.length` output memrefs. Each port is renamed via
 * `suggestName` so the elaborated Verilog binds correctly to the kernel
 * file at `svPath`.
 */
class GenericMLIRKernel(
    svPath:      String,
    kernelName:  String,
    inputSizes:  Seq[Int],
    outputSizes: Seq[Int],
) extends ExtModule {

  val clock = IO(Input(Clock()))
  val reset = IO(Input(Bool()))

  val numInputs  = inputSizes.length
  val numOutputs = outputSizes.length

  val readPorts: Seq[KernelReadWires] = inputSizes.zipWithIndex.map { case (sz, i) =>
    val addrW = log2Ceil(sz)
    val addr      = IO(Output(UInt(addrW.W))).suggestName(s"in${i}_ld0_addr")
    val addrValid = IO(Output(Bool()      )).suggestName(s"in${i}_ld0_addr_valid")
    val addrReady = IO(Input(Bool()       )).suggestName(s"in${i}_ld0_addr_ready")
    val data      = IO(Input(UInt(32.W)   )).suggestName(s"in${i}_ld0_data")
    val dataValid = IO(Input(Bool()       )).suggestName(s"in${i}_ld0_data_valid")
    val dataReady = IO(Output(Bool()      )).suggestName(s"in${i}_ld0_data_ready")
    KernelReadWires(addr, addrValid, addrReady, data, dataValid, dataReady)
  }

  val writePorts: Seq[KernelWriteWires] = outputSizes.zipWithIndex.map { case (sz, j) =>
    val i     = numInputs + j
    val addrW = log2Ceil(sz)
    val st        = IO(Output(UInt((addrW + 32).W))).suggestName(s"in${i}_st0")
    val stValid   = IO(Output(Bool()            )).suggestName(s"in${i}_st0_valid")
    val stReady   = IO(Input(Bool()             )).suggestName(s"in${i}_st0_ready")
    val doneValid = IO(Input(Bool()             )).suggestName(s"in${i}_st0_done_valid")
    val doneReady = IO(Output(Bool()            )).suggestName(s"in${i}_st0_done_ready")
    KernelWriteWires(st, stValid, stReady, doneValid, doneReady, addrW)
  }

  private val startIdx = numInputs + numOutputs
  val startValid = IO(Input(Bool() )).suggestName(s"in${startIdx}_valid")
  val startReady = IO(Output(Bool())).suggestName(s"in${startIdx}_ready")
  val doneValid  = IO(Output(Bool())).suggestName("out0_valid")
  val doneReady  = IO(Input(Bool() )).suggestName("out0_ready")

  override def desiredName: String = kernelName

  addPath(svPath)
}

class MLIRMemrefUnit(
    kernelSvPath: String,
    kernelName:   String,
    inputSizes:   Seq[Int],
    outputSizes:  Seq[Int],
    sourceIdBase: Int = 0,
)(implicit c: Configuration) extends Module {

  require(c.dataBusSize * 8 == 32,
    s"memref unit assumes a 32-bit data bus; got ${c.dataBusSize * 8}-bit")
  require(inputSizes.nonEmpty,  "need at least one input memref")
  require(outputSizes.nonEmpty, "need at least one output memref")

  private val numInputs  = inputSizes.length
  private val numOutputs = outputSizes.length

  val io = IO(new Bundle {
    val in                = Flipped(Decoupled(new MLIRMemrefInst(numInputs, numOutputs)))
    val scratchIn         = Vec(numInputs,  new TilelinkPort)
    val scratchOut        = Vec(numOutputs, new TilelinkPort)
    val readSemaphoreIF   = Vec(numInputs,  new TilelinkPort)
    val writeSemaphoreIF  = Vec(numOutputs, new TilelinkPort)
  })

  private val readConf  = TLDMAConfig(read = true,  write = false, semaphore = true)
  private val writeConf = TLDMAConfig(read = false, write = true,  semaphore = true)

  val readDMAs = inputSizes.indices.map { i =>
    Module(new TLDMA(readConf, sourceIdBase + i))
  }
  val writeDMAs = outputSizes.indices.map { j =>
    Module(new TLDMA(writeConf, sourceIdBase + numInputs + j))
  }
  val kernel = Module(new GenericMLIRKernel(
    kernelSvPath, kernelName, inputSizes, outputSizes,
  ))

  val inputMems  = inputSizes.map  { sz => Mem(sz, UInt(32.W)) }
  val outputMems = outputSizes.map { sz => Mem(sz, UInt(32.W)) }

  // ── Bus plumbing ─────────────────────────────────────────────────────────
  for (i <- 0 until numInputs) {
    readDMAs(i).io.tl              <> io.scratchIn(i)
    readDMAs(i).io.semaphoreIF.get <> io.readSemaphoreIF(i)
  }
  for (j <- 0 until numOutputs) {
    writeDMAs(j).io.tl              <> io.scratchOut(j)
    writeDMAs(j).io.semaphoreIF.get <> io.writeSemaphoreIF(j)
  }

  kernel.clock := clock
  kernel.reset := reset.asBool

  // ── FSM ──────────────────────────────────────────────────────────────────
  private val sIdle :: sLoad :: sCompute :: sStore :: Nil = Enum(4)
  private val state = RegInit(sIdle)

  private val inst = Reg(new MLIRMemrefInst(numInputs, numOutputs))

  private val readFired  = RegInit(VecInit(Seq.fill(numInputs)(false.B)))
  private val readDone   = RegInit(VecInit(Seq.fill(numInputs)(false.B)))
  private val writeFired = RegInit(VecInit(Seq.fill(numOutputs)(false.B)))
  private val writeDone  = RegInit(VecInit(Seq.fill(numOutputs)(false.B)))
  private val startFired = RegInit(false.B)
  private val doneFired  = RegInit(false.B)

  private val inputWritePtrs =
    inputSizes.map(sz => RegInit(0.U(log2Ceil(sz).max(1).W)))
  private val outputReadPtrs =
    outputSizes.map(sz => RegInit(0.U(log2Ceil(sz).max(1).W)))

  io.in.ready := state === sIdle

  // ── Descriptor build (shared helper) ─────────────────────────────────────
  private def fillDescriptor(
      desc:     dmaDescriptor,
      ap:       addrPkg,
      writeEn:  Bool,
      size:     Int,
      sourceId: Int,
  ): Unit = {
    desc.addr    := ap.addr
    desc.size    := size.U(24.W)
    desc.writeEn := writeEn
    desc.source  := sourceId.U
    desc.sink    := 0.U
    val sa = desc.semaphore.get
    sa.semEnable := ap.sem.valid
    sa.mode      := SemaphoreAccessModes.Uninterupted
    sa.semAddr   := ap.sem.bits.addr.pad(16)
    sa.semStepSize := Mux(ap.sem.bits.stepSize.valid,
                          ap.sem.bits.stepSize.bits,
                          size.U(16.W))
  }

  for (i <- 0 until numInputs) {
    fillDescriptor(readDMAs(i).io.interface.descriptor.bits(0),
                   inst.addrs(i), writeEn = false.B,
                   size = inputSizes(i),
                   sourceId = sourceIdBase + i)
    readDMAs(i).io.interface.descriptor.valid := false.B
    readDMAs(i).io.interface.response.ready   := true.B
  }
  for (j <- 0 until numOutputs) {
    fillDescriptor(writeDMAs(j).io.interface.descriptor.bits(0),
                   inst.addrd(j), writeEn = true.B,
                   size = outputSizes(j),
                   sourceId = sourceIdBase + numInputs + j)
    writeDMAs(j).io.interface.descriptor.valid := false.B
    writeDMAs(j).io.interface.response.ready   := true.B
  }

  // ── Load: each DMA fills its inputMem in parallel ────────────────────────
  for (i <- 0 until numInputs) {
    val rdOut = readDMAs(i).io.dataOut.get
    rdOut.ready := state === sLoad
    when(state === sLoad && rdOut.fire) {
      inputMems(i).write(inputWritePtrs(i), rdOut.bits)
      inputWritePtrs(i) := inputWritePtrs(i) + 1.U
    }
  }

  // ── Kernel read ports: per-port 2-deep skid ──────────────────────────────
  for ((kp, i) <- kernel.readPorts.zipWithIndex) {
    val mem = inputMems(i)
    val q   = Module(new Queue(UInt(32.W), 2))
    val fire = kp.addrValid && kp.addrReady
    kp.addrReady     := q.io.enq.ready
    q.io.enq.valid   := RegNext(fire, init = false.B)
    q.io.enq.bits    := RegEnable(mem.read(kp.addr), fire)
    kp.data          := q.io.deq.bits
    kp.dataValid     := q.io.deq.valid
    q.io.deq.ready   := kp.dataReady
  }

  // ── Kernel write ports: per-port store-done queue ────────────────────────
  for ((kp, j) <- kernel.writePorts.zipWithIndex) {
    val mem = outputMems(j)
    val q   = Module(new Queue(Bool(), 2))
    kp.stReady := q.io.enq.ready
    val stFire = kp.stValid && kp.stReady
    val addr   = kp.st(kp.addrW + 32 - 1, 32)
    val data   = kp.st(31, 0)
    when(stFire) { mem.write(addr, data) }
    q.io.enq.valid := stFire
    q.io.enq.bits  := true.B
    kp.doneValid   := q.io.deq.valid
    q.io.deq.ready := kp.doneReady
  }

  // ── Invocation tokens ────────────────────────────────────────────────────
  kernel.startValid := state === sCompute && !startFired
  kernel.doneReady  := state === sCompute && !doneFired
  when(state === sCompute && kernel.startValid && kernel.startReady) {
    startFired := true.B
  }
  when(state === sCompute && kernel.doneValid && kernel.doneReady) {
    doneFired := true.B
  }

  // ── Store: each outputMem feeds its WriteDMA in parallel ─────────────────
  for (j <- 0 until numOutputs) {
    val wrIn = writeDMAs(j).io.dataIn.get
    wrIn.request.ready          := state === sStore
    wrIn.response.valid         := state === sStore
    wrIn.response.bits.readData := outputMems(j).read(outputReadPtrs(j))
    when(state === sStore && wrIn.request.fire) {
      outputReadPtrs(j) := outputReadPtrs(j) + 1.U
    }
  }

  // ── State transitions ────────────────────────────────────────────────────
  switch(state) {
    is(sIdle) {
      when(io.in.fire) {
        inst       := io.in.bits
        state      := sLoad
        for (i <- 0 until numInputs) {
          readFired(i)       := false.B
          readDone(i)        := false.B
          inputWritePtrs(i)  := 0.U
        }
        for (j <- 0 until numOutputs) {
          writeFired(j)      := false.B
          writeDone(j)       := false.B
          outputReadPtrs(j)  := 0.U
        }
        startFired := false.B
        doneFired  := false.B
      }
    }
    is(sLoad) {
      for (i <- 0 until numInputs) {
        readDMAs(i).io.interface.descriptor.valid := !readFired(i)
        when(readDMAs(i).io.interface.descriptor.fire) { readFired(i) := true.B }
        when(readDMAs(i).io.interface.response.fire)   { readDone(i)  := true.B }
      }
      val allComplete = (0 until numInputs).map { i =>
        readDone(i) || readDMAs(i).io.interface.response.fire
      }.reduce(_ && _)
      when(allComplete) { state := sCompute }
    }
    is(sCompute) {
      val doneNow = (kernel.doneValid && kernel.doneReady) || doneFired
      when(doneNow) { state := sStore }
    }
    is(sStore) {
      for (j <- 0 until numOutputs) {
        writeDMAs(j).io.interface.descriptor.valid := !writeFired(j)
        when(writeDMAs(j).io.interface.descriptor.fire) { writeFired(j) := true.B }
        when(writeDMAs(j).io.interface.response.fire)   { writeDone(j)  := true.B }
      }
      val allComplete = (0 until numOutputs).map { j =>
        writeDone(j) || writeDMAs(j).io.interface.response.fire
      }.reduce(_ && _)
      when(allComplete) { state := sIdle }
    }
  }
}
