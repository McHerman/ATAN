package ATA8 
import chisel3._
import chisel3.util._

// Configuration for each slave port
case class TLSlaveConfig(
  addressSet: Seq[(BigInt, BigInt)], // Seq of (base, mask) pairs
  beatBytes: Int = 8
)

// Crossbar configuration
case class TLXbarConfig(
  nMasters: Int,
  slaves: Seq[TLSlaveConfig],
  arbiterPolicy: String = "roundRobin", // or "lowestIndexFirst"
  tl: TLBusConfig,
)


class TLXbar(config: TLXbarConfig) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(config.nMasters, new TilelinkPort(config.tl)))
    val out = Vec(config.slaves.size, new TilelinkPort(config.tl))
  })

  val nMasters = config.nMasters
  val nSlaves = config.slaves.size

  // =========================================================================
  // ID Range Assignment
  // =========================================================================
  def assignIdRanges(sizes: Seq[Int]): Seq[(Int, Int)] = {
    val pow2Sizes = sizes.map(z => if (z == 0) 0 else 1 << log2Ceil(z))
    val indexed = pow2Sizes.zipWithIndex.sortBy(_._1)
    val starts = indexed.scanRight(0)(_._1 + _).tail
    indexed.zip(starts)
      .map { case ((sz, i), st) => (if (sz == 0) (0, 0) else (st, st + sz), i) }
      .sortBy(_._2)
      .map(_._1)
  }

  // Assign source ID ranges for each master
  val masterIdRanges = assignIdRanges(Seq.fill(nMasters)(10)) // 10 ids each 
  
  // Helper function to trim ID based on range size
  def trimId(id: UInt, rangeSize: Int): UInt = 
    if (rangeSize <= 1) 0.U else id(log2Ceil(rangeSize) - 1, 0)

  // =========================================================================
  // Address Decode Logic
  // =========================================================================
  def addressMatches(addr: UInt, addressSet: Seq[(BigInt, BigInt)]): Bool = {
    addressSet.map { case (base, mask) =>
      // For each address range, check if addr matches
      // mask indicates which bits are "don't care"
      // We want: (addr & ~mask) == base
      val baseBits = base.U(config.tl.addrWidth.W)
      val maskBits = mask.U(config.tl.addrWidth.W)
      (addr & ~maskBits) === baseBits
      //(addr & maskBits) === baseBits
    }.reduce(_ || _)
  }

  // Routing matrix: For each master-slave pair, determine if reachable
  val routingMatrix = VecInit(
    io.in.map { master =>
      VecInit(config.slaves.map { slave =>
        addressMatches(master.a.bits.address, slave.addressSet)
      })
    }
  )

  val lockReg = Reg(Vec(config.slaves.size, Vec(config.nMasters, Bool())))

  // =========================================================================
  // Intermediate Wires with Transformed IDs
  // =========================================================================
  
  val masterA = Wire(Vec(nMasters, Decoupled(new TilelinkA(config.tl))))
  val masterD = Wire(Vec(nMasters, Flipped(Decoupled(new TilelinkD(config.tl)))))
  
  // Master-side A channel: Add ID offset
  (io.in zip masterA zip masterIdRanges).foreach { case ((ioIn, mA), (idStart, idEnd)) =>
    mA.bits := ioIn.a.bits
    mA.bits.source := ioIn.a.bits.source + idStart.U
    mA.valid := ioIn.a.valid
    ioIn.a.ready := mA.ready
  }
  
  // Master-side D channel: Remove ID offset
  (io.in zip masterD zip masterIdRanges).foreach { case ((ioIn, mD), (idStart, idEnd)) =>
    ioIn.d.bits := mD.bits
    ioIn.d.bits.source := trimId(mD.bits.source - idStart.U, idEnd - idStart)
    ioIn.d.valid := mD.valid
    mD.ready := ioIn.d.ready
  }

  // =========================================================================
  // Fanout: Replicate master A channels to all reachable slaves
  // =========================================================================
  val masterToSlave = (masterA zip routingMatrix).map { case (mA, routes) =>
    VecInit(routes.zipWithIndex.map { case (route, slaveIdx) =>
      val req = Wire(Decoupled(new TilelinkA(config.tl)))
      req.bits := mA.bits
      req.valid := mA.valid && (route || (nSlaves == 1).B)
      req
    })
  }
  
  // Master ready when any selected slave is ready
  (masterA zip routingMatrix zip masterToSlave).foreach { case ((mA, routes), reqsToSlaves) =>
    mA.ready := Mux1H(routes, reqsToSlaves.map(_.ready))
  }

  // =========================================================================
  // A Channel Arbitration (Master -> Slave)
  // =========================================================================
  
  //val arbiterFn: Seq[DecoupledIO[TilelinkA]] => DecoupledIO[TilelinkA] = 
  //  RRLockArbiter.apply
  
  val arbiterFn: Seq[DecoupledIO[TilelinkA]] => DecoupledIO[TilelinkA] = 
    if (config.arbiterPolicy == "roundRobin") RRArbiter.apply else if (config.arbiterPolicy == "lock") RRLockArbiter.apply else Arbiter.apply

  io.out.zipWithIndex.foreach { case (slaveOut, slaveIdx) =>
    val requests = masterToSlave.map(_(slaveIdx))
    slaveOut.a <> arbiterFn(requests)
  }

  // =========================================================================
  // D Channel Routing (Slave -> Master)
  // =========================================================================
  
  // For each slave D response, check which master ID range it belongs to
  def sourceInRange(source: UInt, range: (Int, Int)): Bool =
    (source >= range._1.U) && (source < range._2.U)
  
  val slaveToMaster = io.out.map { slaveOut =>
    VecInit(masterIdRanges.map { idRange =>
      val resp = Wire(Decoupled(new TilelinkD(config.tl)))
      resp.bits := slaveOut.d.bits
      resp.valid := slaveOut.d.valid && sourceInRange(slaveOut.d.bits.source, idRange)
      resp
    })
  }
  
  // Slave ready when the target master is ready
  (io.out zip slaveToMaster).foreach { case (slaveOut, respsToMasters) =>
    val targetMaster = VecInit(masterIdRanges.map { idRange =>
      sourceInRange(slaveOut.d.bits.source, idRange)
    })
    slaveOut.d.ready := Mux1H(targetMaster, respsToMasters.map(_.ready))
  }

  // =========================================================================
  // D Channel Arbitration (Slave -> Master)
  // =========================================================================
  val dArbiterFn: Seq[DecoupledIO[TilelinkD]] => DecoupledIO[TilelinkD] = 
    if (config.arbiterPolicy == "roundRobin") RRArbiter.apply else Arbiter.apply

  (masterD zip slaveToMaster.transpose).foreach { case (mD, responses) =>
    mD <> dArbiterFn(responses)
  }

}

// Helper: Round-robin arbiter
object RRArbiter {
  def apply[T <: Data](requests: Seq[DecoupledIO[T]]): DecoupledIO[T] = {
    val arb = Module(new RRArbiter(chiselTypeOf(requests.head.bits), requests.size))
    arb.io.in.zip(requests).foreach { case (in, req) => in <> req }
    arb.io.out
  }
}

class ValidRotatingLockingRRArbiter[T <: Data](
  gen: T,
  n: Int,
  count: Int,
  needsLock: Option[T => Bool] = None,
  initLastGrant: Boolean = false
) extends LockingRRArbiter[T](gen, n, count, needsLock, initLastGrant) {
  
  // Override to update on valid instead of fire
  override lazy val lastGrant = {
    val reg = if (initLastGrant) {
      RegInit(0.U(log2Ceil(n).W))
    } else {
      Reg(UInt(log2Ceil(n).W))
    }
    
    // Update whenever any input is valid (not just on fire)
    when(io.in.map(_.valid).reduce(_ || _)) {
      reg := io.chosen
    }
    
    reg
  }
}


object RRLockArbiter {
  def apply[T <: Data](requests: Seq[DecoupledIO[T]]): DecoupledIO[T] = {
    val arb = Module(new ValidRotatingLockingRRArbiter(chiselTypeOf(requests.head.bits), requests.size, count = 1024,
    needsLock = Some((data: T) => {
      // Lock when size = 1023 (indicating a 1024-beat burst)
      data.asInstanceOf[TilelinkA].size === 1023.U
    })))

    arb.io.in.zip(requests).foreach { case (in, req) => in <> req }
    arb.io.out
  }
}

/*
object ValidRotatingRRArbiter {
  def apply[T <: Data](requests: Seq[DecoupledIO[T]]): DecoupledIO[T] = {
    val n = requests.size
    val arb = Module(new Module {
      val io = IO(new Bundle {
        val in = Flipped(Vec(n, Decoupled(chiselTypeOf(requests.head.bits))))
        val out = Decoupled(chiselTypeOf(requests.head.bits))
        val chosen = Output(UInt(log2Ceil(n).W))
      })
      
      // Update pointer on ANY valid request (not just fire)
      val lastGrant = RegInit(0.U(log2Ceil(n).W))
      when(io.in.map(_.valid).reduce(_ || _)) {
        lastGrant := io.chosen
      }
      
      val grantMask = VecInit((0 until n).map(_.U > lastGrant))
      val validMask = io.in.zip(grantMask).map { case (in, g) => in.valid && g }
      
      // Standard ArbiterCtrl logic
      def arbiterCtrl(request: Seq[Bool]): Seq[Bool] = request.length match {
        case 0 => Seq()
        case 1 => Seq(true.B)
        case _ => true.B +: request.tail.init.scanLeft(request.head)(_ || _).map(!_)
      }
      
      val ctrl = arbiterCtrl((0 until n).map(i => validMask(i)) ++ io.in.map(_.valid))
      val grant = (0 until n).map(i => ctrl(i) && grantMask(i) || ctrl(i + n))
      
      io.chosen := (n - 1).U
      for (i <- n - 2 to 0 by -1)
        when(io.in(i).valid) { io.chosen := i.U }
      for (i <- n - 1 to 1 by -1)
        when(validMask(i)) { io.chosen := i.U }
      
      io.out.valid := io.in(io.chosen).valid
      io.out.bits := io.in(io.chosen).bits
      
      for ((in, g) <- io.in.zip(grant))
        in.ready := g && io.out.ready
    })
    
    arb.io.in.zip(requests).foreach { case (in, req) => in <> req }
    arb.io.out
  }
}
*/

/*
object RRLockArbiter {

  class ArbiterIO[T <: Data](private val gen: T, val n: Int) extends Bundle {
    val in = Flipped(Vec(n, Decoupled(gen)))
    val out = Decoupled(gen)
    val chosen = Output(UInt(log2Ceil(n).W))
  }

  class TLLockingRRArbiter[T <: Data](val gen: T, val n: Int) extends Module {
    override def desiredName = s"Arbiter${n}_${gen.typeName}"
  
    val io = IO(new ArbiterIO(gen, n))





  
    io.chosen := (n - 1).asUInt
    io.out.bits := io.in(n - 1).bits
    for (i <- n - 2 to 0 by -1) {
      when(io.in(i).valid) {
        io.chosen := i.asUInt
        io.out.bits := io.in(i).bits
      }
    }
  
    val grant = ArbiterCtrl(io.in.map(_.valid))
    for ((in, g) <- io.in.zip(grant))
      in.ready := g && io.out.ready
    io.out.valid := !grant.last || io.in.last.valid
  }




  def apply[T <: Data](requests: Seq[DecoupledIO[T]]): DecoupledIO[T] = {
    val arb = Module(new LockingRRArbiter(chiselTypeOf(requests.head.bits), requests.size, count = 1023,
    needsLock = Some((data: T) => {
      // Lock when size = 1023 (indicating a 1024-beat burst)
      data.asInstanceOf[TilelinkA].size === 1023.U
    })))

    arb.io.in.zip(requests).foreach { case (in, req) => in <> req }
    arb.io.out
  }
}
*/





// Helper: Priority arbiter (lowest index first)
object Arbiter {
  def apply[T <: Data](requests: Seq[DecoupledIO[T]]): DecoupledIO[T] = {
    val arb = Module(new Arbiter(chiselTypeOf(requests.head.bits), requests.size))
    arb.io.in.zip(requests).foreach { case (in, req) => in <> req }
    arb.io.out
  }
}

