package ATA8

import chisel3._
import chisel3.experimental._
import chisel3.util._

class ATA8(config: Configuration, memCfgBase: MemSystemConfig = MemSystemConfig.default()) extends Module {
  implicit val c: Configuration = config

  val io = IO(new Bundle {
    val AXIST_out     = new AXIST_2(c.axiStreamWidth, 2, 1, 1, 1)
    val AXIST_inData  = Flipped(new AXIST_2(c.axiStreamWidth, 2, 1, 1, 1))
    val AXIST_inInst  = Flipped(new AXIST_2(128, 2, 1, 1, 1))
    val axi_s0        = Flipped(new CustomAXI4Lite(32, 32))

    val hostIn   = Flipped(new TilelinkPort(mc.tlBus))
    val mccIn    = if (c.riscv.enabled) Some(Flipped(new TilelinkPort(c.riscv.tlBus))) else None
    val mccSemIn = if (c.riscv.enabled) Some(Flipped(new TilelinkPort(c.riscv.tlBus))) else None
  })

  implicit lazy val mc: MemSystemConfig = memCfgBase.copy(
    sourceWidth = c.sourceWidth,
    semGenWidth = c.semaphoreGenerationWidth,
    tiers = if (c.riscv.enabled) {
      memCfgBase.tiers.updated(0, memCfgBase.tiers(0).copy(mccBus = Some(c.riscv.tlBus)))
    } else memCfgBase.tiers,
  )
  require(config.dataBusSize == mc.dataBusSize,
    s"Configuration.dataBusSize (${config.dataBusSize}) must equal MemSystemConfig.dataBusSize (${mc.dataBusSize})")
  require(config.addrWidth == mc.addrWidth,
    s"Configuration.addrWidth (${config.addrWidth}) must equal MemSystemConfig.addrWidth (${mc.addrWidth})")
  private val nDMAs = mc.tiers.length - 1
  private val nExeSemPorts = 3 * c.grainDim  // writeSem(grainDim) + readSem(2 * grainDim)
  private val nSemPorts = nExeSemPorts + 1 + 1 + 2 * nDMAs + (if (c.riscv.enabled) 1 else 0)

  val FrontEnd     = Module(new FrontEnd)
  val Execute      = Module(new Execute())
  val Load         = Module(new Load())
  val Store        = Module(new Store())
  val MemSys       = Module(new MemSystem(c))
  val Config       = Module(new Config())
  val SemSys       = Module(new SemSystem(nSemPorts))

  //// FRONTEND ////

  FrontEnd.io.AXIST <> io.AXIST_inInst
  Execute.io.instructionStream <> FrontEnd.io.exeStream
  Load.io.instructionStream    <> FrontEnd.io.loadStream
  Store.io.instructionStream   <> FrontEnd.io.storeStream

  MemSys.io.dmaInstructionStream  <> FrontEnd.io.dmaStream
  SemSys.io.instructionStream     <> FrontEnd.io.semProgStream

  //// EXECUTE ////

  Execute.io.scratchIn(0) <> MemSys.io.tier0ReadPorts(0)
  Execute.io.scratchIn(1) <> MemSys.io.tier0ReadPorts(1)

  // Execute semaphore ports: writeSem(grainDim) + readSem(2 * grainDim)
  var semIdx = 0
  for (i <- 0 until c.grainDim) {
    Execute.io.writeSemaphoreIF(i) <> SemSys.io.inPorts(semIdx); semIdx += 1
  }
  for (i <- 0 until 2; j <- 0 until c.grainDim) {
    Execute.io.readSemaphoreIF(i)(j) <> SemSys.io.inPorts(semIdx); semIdx += 1
  }

  //// LOAD ////

  Load.io.AXIST        <> io.AXIST_inData
  Load.io.semaphoreIF  <> SemSys.io.inPorts(semIdx); semIdx += 1

  //// STORE ////

  Store.io.AXIST    <> io.AXIST_out
  Store.io.readPort <> MemSys.io.tier0ReadPorts(2)
  Store.io.semaphoreIF <> SemSys.io.inPorts(semIdx); semIdx += 1

  //// MEMORY SYSTEM ////

  MemSys.io.tier0WritePorts <> VecInit(Execute.io.scratchOut ++ VecInit(Seq(Load.io.scratchOut)))
  MemSys.io.hostIn <> io.hostIn

  if (c.riscv.enabled) {
    MemSys.io.mccIn.get <> io.mccIn.get
  }

  //// SEMAPHORE SYSTEM — DMA semaphore ports ////

  for (i <- 0 until nDMAs) {
    MemSys.io.semaphoreA(i) <> SemSys.io.inPorts(semIdx); semIdx += 1
    MemSys.io.semaphoreB(i) <> SemSys.io.inPorts(semIdx); semIdx += 1
  }

  //// SEMAPHORE SYSTEM — mcc port (field-by-field: 1-bit source ↔ 8-bit source) ////

  if (c.riscv.enabled) {
    val semPort = SemSys.io.inPorts(semIdx)
    val mccSem  = io.mccSemIn.get
    semPort.a.valid        := mccSem.a.valid
    mccSem.a.ready         := semPort.a.ready
    semPort.a.bits.opcode  := mccSem.a.bits.opcode
    semPort.a.bits.param   := mccSem.a.bits.param
    semPort.a.bits.size    := mccSem.a.bits.size
    semPort.a.bits.source  := mccSem.a.bits.source
    semPort.a.bits.address := mccSem.a.bits.address
    semPort.a.bits.data    := mccSem.a.bits.data
    semPort.a.bits.mask    := mccSem.a.bits.mask
    semPort.a.bits.corrupt := mccSem.a.bits.corrupt
    mccSem.d.valid         := semPort.d.valid
    semPort.d.ready        := mccSem.d.ready
    mccSem.d.bits.opcode   := semPort.d.bits.opcode
    mccSem.d.bits.param    := semPort.d.bits.param
    mccSem.d.bits.size     := semPort.d.bits.size
    mccSem.d.bits.source   := semPort.d.bits.source
    mccSem.d.bits.sink     := semPort.d.bits.sink
    mccSem.d.bits.denied   := semPort.d.bits.denied
    mccSem.d.bits.data     := semPort.d.bits.data
    mccSem.d.bits.corrupt  := semPort.d.bits.corrupt
  }

  /// DEBUG ///

  Config.io.axi_s0        <> io.axi_s0
  Config.io.loadDebug     <> Load.io.debug
  Config.io.storeDebug    <> Store.io.debug
  Config.io.exeDebug      <> Execute.io.debug
  Config.io.receiverDebug <> FrontEnd.io.receiverDebug
  Config.io.decodeDebug   <> FrontEnd.io.decodeDebug

  Config.io.decodeOutLoad.bits  := FrontEnd.io.loadStream.bits
  Config.io.decodeOutLoad.valid := FrontEnd.io.loadStream.fire

  Config.io.AXIDebug.data_ready := Load.io.AXIST.tready
  Config.io.AXIDebug.data_valid := io.AXIST_inData.tvalid

  Config.io.AXIDebug.inst_ready := FrontEnd.io.AXIST.tready
  Config.io.AXIDebug.inst_valid := io.AXIST_inInst.tvalid

  Config.io.AXIDebug.out_ready := io.AXIST_out.tready
  Config.io.AXIDebug.out_valid := Store.io.AXIST.tvalid

  Config.io.frontEndDebug := FrontEnd.io.frontEndDebug
}

object ATA8 extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new ATA8(Configuration.large16x16()),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
