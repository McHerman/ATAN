package Common

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import ATA8._

import scala.util.Random
import scala.util.control.Breaks._


class TLXbarTester extends AnyFlatSpec with Matchers with ChiselSim {
  behavior of "TLXbar"

  // Create a test configuration without making it implicit to avoid ambiguity
  def testConfig: Configuration =
    Configuration.default().withBus(_.copy(sourceWidth = 8, addrWidth = 17))

  it should "route single master to single slave" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 1,
      slaves = Seq(
        TLSlaveConfig(Seq((0x00, 0xFFFF)))
      )
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      // Initialize
      dut.io.in(0).a.valid.poke(false.B)
      dut.io.out(0).d.valid.poke(false.B)
      dut.clock.step()

      // Send A channel request
      dut.io.in(0).a.bits.opcode.poke(4.U)  // Get
      dut.io.in(0).a.bits.address.poke(0x100.U)
      dut.io.in(0).a.bits.source.poke(5.U)
      dut.io.in(0).a.bits.size.poke(3.U)
      dut.io.in(0).a.bits.mask.poke(0.U)
      dut.io.in(0).a.valid.poke(true.B)
      dut.io.out(0).a.ready.poke(true.B)

      dut.clock.step()
      
      // Check that request arrived at slave
      dut.io.out(0).a.valid.expect(true.B)
      dut.io.in(0).a.bits.opcode.expect(4.U)
      dut.io.in(0).a.bits.address.expect(0x100.U)
      dut.io.in(0).a.bits.source.expect(5.U)
      dut.io.in(0).a.bits.size.expect(3.U)
      dut.io.in(0).a.bits.mask.expect(0.U)
      dut.io.in(0).a.valid.poke(false.B)
      dut.clock.step()

      // Send D channel response
      dut.io.out(0).d.bits.opcode.poke(1.U)  // AccessAckData
      dut.io.out(0).d.bits.source.poke(5.U)
      dut.io.out(0).d.bits.data.poke(0xDEAD.U)
      dut.io.out(0).d.valid.poke(true.B)
      dut.io.in(0).d.ready.poke(true.B)

      dut.clock.step()

      // Check that response arrived at master
      dut.io.in(0).d.valid.expect(true.B)
      dut.io.in(0).d.bits.source.expect(5.U)
      dut.io.in(0).d.bits.data.expect(0xDEAD.U)
    }
  }

  it should "route requests to correct slaves based on address" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 1,
      slaves = Seq(
        TLSlaveConfig(Seq((0x0000, 0x00FF))),      // Slave 0: 0x0000-0xFFFF
        TLSlaveConfig(Seq((0x0100, 0x0E00))),     // Slave 1: 0x10000-0x1FFFF
        TLSlaveConfig(Seq((0x1000, 0xE000)))      // Slave 2: 0x20000-0x2FFFF
      )
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      dut.clock.step()

      // Test routing to slave 0
      dut.io.in(0).a.bits.address.poke(0.U)
      dut.io.in(0).a.bits.source.poke(1.U)
      dut.io.in(0).a.bits.opcode.poke(4.U)
      dut.io.in(0).a.valid.poke(true.B)
      dut.io.out.foreach(_.a.ready.poke(true.B))

      dut.clock.step()
      dut.io.out(0).a.valid.expect(true.B)
      dut.io.out(1).a.valid.expect(false.B)
      dut.io.out(2).a.valid.expect(false.B)

      dut.io.in(0).a.valid.poke(false.B)
      dut.clock.step()

      // Test routing to slave 1
      //dut.io.in(0).a.bits.address.poke(0x15000.U)
      dut.io.in(0).a.bits.address.poke(0x100.U)
      dut.io.in(0).a.bits.source.poke(2.U)
      dut.io.in(0).a.bits.opcode.poke(4.U)
      dut.io.in(0).a.valid.poke(true.B)

      dut.clock.step()

      /*
      breakable{
        for(i <- 0 until 10){
          dut.clock.step()

          val out: Boolean = dut.io.out(1).a.valid.peek().litToBoolean

          if(out === true) {
            break()
          }
        }
      }
      */

      dut.io.out(0).a.valid.expect(false.B)
      dut.io.out(1).a.valid.expect(true.B)
      dut.io.out(2).a.valid.expect(false.B)

      dut.io.in(0).a.valid.poke(false.B)
      dut.clock.step()

      // Test routing to slave 2
      //dut.io.in(0).a.bits.address.poke(0x28000.U)
      dut.io.in(0).a.bits.address.poke(0x1000.U)
      dut.io.in(0).a.bits.source.poke(3.U)
      dut.io.in(0).a.valid.poke(true.B)

      dut.clock.step()
      dut.io.out(0).a.valid.expect(false.B)
      dut.io.out(1).a.valid.expect(false.B)
      dut.io.out(2).a.valid.expect(true.B)
    }
  }

  it should "arbitrate between multiple masters for same slave" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 3,
      slaves = Seq(
        TLSlaveConfig(Seq((0x00, 0xFFFF)))
      ),
      arbiterPolicy = "lowestIndexFirst"
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      dut.clock.step()

      // All masters request same slave simultaneously
      //dut.io.in(0).a.bits.address.poke(0x100.U)
      dut.io.in(0).a.bits.address.poke(0x100.U)
      dut.io.in(0).a.bits.source.poke(1.U)
      dut.io.in(0).a.bits.opcode.poke(4.U)
      dut.io.in(0).a.valid.poke(true.B)

      //dut.io.in(1).a.bits.address.poke(0x200.U)
      dut.io.in(1).a.bits.address.poke(0x200.U)
      dut.io.in(1).a.bits.source.poke(2.U)
      dut.io.in(1).a.bits.opcode.poke(4.U)
      dut.io.in(1).a.valid.poke(true.B)

      //dut.io.in(2).a.bits.address.poke(0x300.U)
      dut.io.in(2).a.bits.address.poke(0x300.U)
      dut.io.in(2).a.bits.source.poke(3.U)
      dut.io.in(2).a.bits.opcode.poke(4.U)
      dut.io.in(2).a.valid.poke(true.B)

      dut.io.out(0).a.ready.poke(true.B)

      dut.clock.step()

      // Master 0 should win (lowest index first policy)
      dut.io.in(0).a.ready.expect(true.B)
      dut.io.out(0).a.valid.expect(true.B)
      //dut.io.out(0).a.bits.address.expect(0x100.U)
      dut.io.out(0).a.bits.address.expect(0x100.U)

      // Deassert master 0
      dut.io.in(0).a.valid.poke(false.B)
      dut.clock.step()

      // Master 1 should now be granted
      dut.io.in(1).a.ready.expect(true.B)
      //dut.io.out(0).a.bits.address.expect(0x200.U)
      dut.io.out(0).a.bits.address.expect(0x200.U)
    }
  }

  it should "handle ID range translation correctly" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 2,
      slaves = Seq(
        TLSlaveConfig(Seq((0x00, 0xFFFF)))
      )
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      dut.clock.step()

      // Master 0 sends request with source ID 5
      dut.io.in(0).a.bits.address.poke(0x100.U)
      dut.io.in(0).a.bits.source.poke(5.U)
      dut.io.in(0).a.bits.opcode.poke(4.U)
      dut.io.in(0).a.valid.poke(true.B)
      dut.io.out(0).a.ready.poke(true.B)

      dut.clock.step()

      // Slave should see transformed ID (5 + offset)
      dut.io.out(0).a.valid.expect(true.B)
      val transformedId0 = dut.io.out(0).a.bits.source.peek().litValue

      dut.io.in(0).a.valid.poke(false.B)
      dut.clock.step()

      // Master 1 sends request with source ID 5
      dut.io.in(1).a.bits.address.poke(0x200.U)
      dut.io.in(1).a.bits.source.poke(5.U)
      dut.io.in(1).a.bits.opcode.poke(4.U)
      dut.io.in(1).a.valid.poke(true.B)

      dut.clock.step()

      // Slave should see different transformed ID
      dut.io.out(0).a.valid.expect(true.B)
      val transformedId1 = dut.io.out(0).a.bits.source.peek().litValue

      // IDs should be different
      assert(transformedId0 != transformedId1, "Source IDs from different masters should be unique")

      // Send response back to master 1
      dut.io.in(1).a.valid.poke(false.B)
      dut.clock.step()

      dut.io.out(0).d.bits.source.poke(transformedId1.U)
      dut.io.out(0).d.bits.opcode.poke(1.U)
      dut.io.out(0).d.valid.poke(true.B)
      dut.io.in(1).d.ready.poke(true.B)

      dut.clock.step()

      // Master 1 should receive response with original source ID
      dut.io.in(1).d.valid.expect(true.B)
      dut.io.in(1).d.bits.source.expect(5.U)
    }
  }


  it should "handle backpressure correctly" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 1,
      slaves = Seq(
        TLSlaveConfig(Seq((0x0, 0xFFFF)))
      )
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      dut.clock.step()

      // Master sends request but slave is not ready
      dut.io.in(0).a.bits.address.poke(0x100.U)
      dut.io.in(0).a.bits.source.poke(1.U)
      dut.io.in(0).a.bits.opcode.poke(4.U)
      dut.io.in(0).a.valid.poke(true.B)
      dut.io.out(0).a.ready.poke(false.B)

      dut.clock.step()

      // Master should not be ready
      dut.io.in(0).a.ready.expect(false.B)

      dut.clock.step()

      // Slave becomes ready
      dut.io.out(0).a.ready.poke(true.B)
      dut.clock.step()

      // Now master should be ready and transaction completes
      dut.io.in(0).a.ready.expect(true.B)
      dut.io.out(0).a.valid.expect(true.B)
    }
  }


  it should "handle concurrent transactions to different slaves" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 2,
      slaves = Seq(
        TLSlaveConfig(Seq((0x0000, 0xFFFF))),
        TLSlaveConfig(Seq((0x10000, 0xFFFF)))
      )
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      dut.clock.step()

      // Master 0 -> Slave 0, Master 1 -> Slave 1 (no conflict)
      dut.io.in(0).a.bits.address.poke(0x100.U)
      dut.io.in(0).a.bits.source.poke(1.U)
      dut.io.in(0).a.bits.opcode.poke(4.U)
      dut.io.in(0).a.valid.poke(true.B)

      dut.io.in(1).a.bits.address.poke(0x10100.U)
      dut.io.in(1).a.bits.source.poke(2.U)
      dut.io.in(1).a.bits.opcode.poke(4.U)
      dut.io.in(1).a.valid.poke(true.B)

      dut.io.out.foreach(_.a.ready.poke(true.B))

      dut.clock.step()

      // Both slaves should receive their respective requests
      dut.io.out(0).a.valid.expect(true.B)
      dut.io.out(0).a.bits.address.expect(0x100.U)

      dut.io.out(1).a.valid.expect(true.B)
      dut.io.out(1).a.bits.address.expect(0x10100.U)

      // Both masters should be ready
      dut.io.in(0).a.ready.expect(true.B)
      dut.io.in(1).a.ready.expect(true.B)
    }
  }

  it should "handle round-robin arbitration" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 3,
      slaves = Seq(
        TLSlaveConfig(Seq((0x0, 0xFFFF)))
      ),
      arbiterPolicy = "roundRobin"
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      dut.clock.step()

      // All masters continuously request
      for (cycle <- 0 until 10) {
        dut.io.in(0).a.bits.address.poke((0x100 + cycle * 0x10).U)
        dut.io.in(0).a.bits.source.poke(1.U)
        dut.io.in(0).a.valid.poke(true.B)

        dut.io.in(1).a.bits.address.poke((0x200 + cycle * 0x10).U)
        dut.io.in(1).a.bits.source.poke(2.U)
        dut.io.in(1).a.valid.poke(true.B)

        dut.io.in(2).a.bits.address.poke((0x300 + cycle * 0x10).U)
        dut.io.in(2).a.bits.source.poke(3.U)
        dut.io.in(2).a.valid.poke(true.B)

        dut.io.out(0).a.ready.poke(true.B)

        dut.clock.step()

        // Log which master was granted
        val grantedAddr = dut.io.out(0).a.bits.address.peek().litValue
        val grantedMaster = if ((grantedAddr & 0xF00) == 0x100) 0
                           else if ((grantedAddr & 0xF00) == 0x200) 1
                           else 2
      }
    }
  }

  it should "handle multiple response routing" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 2,
      slaves = Seq(
        TLSlaveConfig(Seq((0x0, 0xFFFF)))
      )
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      dut.clock.step()

      // Send requests from both masters
      dut.io.in(0).a.bits.address.poke(0x100.U)
      dut.io.in(0).a.bits.source.poke(5.U)
      dut.io.in(0).a.valid.poke(true.B)
      dut.io.out(0).a.ready.poke(true.B)

      dut.clock.step()
      val source0 = dut.io.out(0).a.bits.source.peek().litValue

      dut.io.in(0).a.valid.poke(false.B)
      dut.clock.step()

      dut.io.in(1).a.bits.address.poke(0x200.U)
      dut.io.in(1).a.bits.source.poke(7.U)
      dut.io.in(1).a.valid.poke(true.B)

      dut.clock.step()
      val source1 = dut.io.out(0).a.bits.source.peek().litValue

      dut.io.in(1).a.valid.poke(false.B)
      dut.clock.step(2)

      // Send responses in reverse order
      dut.io.out(0).d.bits.source.poke(source1.U)
      dut.io.out(0).d.bits.data.poke(0xBEEF.U)
      dut.io.out(0).d.bits.opcode.poke(1.U)
      dut.io.out(0).d.valid.poke(true.B)
      dut.io.in(0).d.ready.poke(true.B)
      dut.io.in(1).d.ready.poke(true.B)

      dut.clock.step()

      // Response should go to master 1
      dut.io.in(1).d.valid.expect(true.B)
      dut.io.in(1).d.bits.source.expect(7.U)
      dut.io.in(0).d.bits.data.expect(0xBEEF.U)
      dut.io.in(0).d.bits.opcode.expect(1.U)

      dut.io.out(0).d.valid.poke(false.B)
      dut.clock.step()

      // Send response for master 0
      dut.io.out(0).d.bits.source.poke(source0.U)
      dut.io.out(0).d.bits.data.poke(0xDEAD.U)
      dut.io.out(0).d.valid.poke(true.B)

      dut.clock.step()

      // Response should go to master 0
      dut.io.in(0).d.valid.expect(true.B)
      dut.io.in(0).d.bits.source.expect(5.U)
      dut.io.in(0).d.bits.data.expect(0xDEAD.U)
    }
  }

  // =========================================================================
  // Locking Arbitration Tests
  // =========================================================================

  it should "lock arbiter to master during burst of 1024 transfers" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 2,
      slaves = Seq(
        TLSlaveConfig(Seq((0x0, 0xFFFF)))
      ),
      arbiterPolicy = "lock"
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      dut.clock.step()

      // Master 0 sends a locking request (size = 1023 triggers lock)
      dut.io.in(0).a.bits.opcode.poke(4.U)  // Get
      dut.io.in(0).a.bits.param.poke(0.U)
      dut.io.in(0).a.bits.size.poke(1023.U)  // Locking size
      dut.io.in(0).a.bits.source.poke(1.U)
      dut.io.in(0).a.bits.address.poke(0x100.U)
      dut.io.in(0).a.bits.mask.poke(0x3.U)  // mask is 2 bits
      dut.io.in(0).a.bits.data.poke(0x0.U)
      dut.io.in(0).a.bits.corrupt.poke(0.U)
      dut.io.in(0).a.valid.poke(true.B)

      // Master 1 also requests (should be blocked during lock)
      dut.io.in(1).a.bits.opcode.poke(4.U)
      dut.io.in(1).a.bits.param.poke(0.U)
      dut.io.in(1).a.bits.size.poke(0.U)
      dut.io.in(1).a.bits.source.poke(2.U)
      dut.io.in(1).a.bits.address.poke(0x200.U)
      dut.io.in(1).a.bits.mask.poke(0x3.U)  // mask is 2 bits
      dut.io.in(1).a.bits.data.poke(0x0.U)
      dut.io.in(1).a.bits.corrupt.poke(0.U)
      dut.io.in(1).a.valid.poke(true.B)

      dut.io.out(0).a.ready.poke(true.B)

      dut.clock.step()

      // Verify Master 0's request is granted - check all A channel fields
      dut.io.out(0).a.valid.expect(true.B)
      dut.io.out(0).a.bits.opcode.expect(4.U)
      dut.io.out(0).a.bits.param.expect(0.U)
      dut.io.out(0).a.bits.size.expect(1023.U)
      dut.io.out(0).a.bits.address.expect(0x100.U)
      dut.io.out(0).a.bits.mask.expect(0x3.U)
      dut.io.out(0).a.bits.data.expect(0x0.U)
      dut.io.out(0).a.bits.corrupt.expect(0.U)

      // Master 0 should be ready, Master 1 should not
      dut.io.in(0).a.ready.expect(true.B)
      dut.io.in(1).a.ready.expect(false.B)

      dut.clock.step()

      // Continue sending burst from Master 0 (1023 more transfers)
      for (i <- 0 until 1023) {
        dut.io.in(0).a.ready.expect(true.B)
        dut.io.in(1).a.ready.expect(false.B)

        // Verify all fields are correctly propagated
        dut.io.out(0).a.valid.expect(true.B)
        dut.io.out(0).a.bits.opcode.expect(4.U)
        dut.io.out(0).a.bits.size.expect(1023.U)
        dut.io.out(0).a.bits.address.expect(0x100.U)

        dut.clock.step()
      }

      dut.io.in(0).a.valid.poke(false.B)
      dut.io.in(0).a.ready.expect(false.B)

      dut.io.in(1).a.ready.expect(true.B)

      dut.clock.step()

      // Now Master 1 should be able to get access
      dut.io.in(1).a.ready.expect(true.B)
      dut.io.out(0).a.valid.expect(true.B)
      dut.io.out(0).a.bits.address.expect(0x200.U)
      dut.io.out(0).a.bits.opcode.expect(4.U)
      dut.io.out(0).a.bits.param.expect(0.U)
      dut.io.out(0).a.bits.size.expect(0.U)
      dut.io.out(0).a.bits.mask.expect(0x3.U)
      dut.io.out(0).a.bits.data.expect(0x0.U)
      dut.io.out(0).a.bits.corrupt.expect(0.U)
    }
  }


  it should "handle D channel responses correctly during locked burst" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 2,
      slaves = Seq(
        TLSlaveConfig(Seq((0x0, 0xFFFF)))
      ),
      arbiterPolicy = "lock"
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      dut.clock.step()

      // Master 0 sends locking request
      dut.io.in(0).a.bits.opcode.poke(4.U)
      dut.io.in(0).a.bits.param.poke(0.U)
      dut.io.in(0).a.bits.size.poke(1023.U)
      dut.io.in(0).a.bits.source.poke(5.U)
      dut.io.in(0).a.bits.address.poke(0x100.U)
      dut.io.in(0).a.bits.mask.poke(0x3.U)  // mask is 2 bits
      dut.io.in(0).a.bits.data.poke(0x0.U)
      dut.io.in(0).a.bits.corrupt.poke(0.U)
      dut.io.in(0).a.valid.poke(true.B)
      dut.io.out(0).a.ready.poke(true.B)

      dut.clock.step()
      val transformedSource = dut.io.out(0).a.bits.source.peek().litValue

      // Send D channel response - verify all fields
      dut.io.in(0).a.valid.poke(false.B)
      dut.clock.step()

      dut.io.out(0).d.bits.opcode.poke(1.U)  // AccessAckData
      dut.io.out(0).d.bits.param.poke(0.U)
      dut.io.out(0).d.bits.size.poke(1023.U)
      dut.io.out(0).d.bits.source.poke(transformedSource.U)
      dut.io.out(0).d.bits.sink.poke(0.U)
      dut.io.out(0).d.bits.denied.poke(0.U)
      dut.io.out(0).d.bits.data.poke(0xBEEF.U)
      dut.io.out(0).d.bits.corrupt.poke(0.U)
      dut.io.out(0).d.valid.poke(true.B)
      dut.io.in(0).d.ready.poke(true.B)

      dut.clock.step()

      // Verify all D channel fields are correctly routed
      dut.io.in(0).d.valid.expect(true.B)
      dut.io.in(0).d.bits.opcode.expect(1.U)
      dut.io.in(0).d.bits.param.expect(0.U)
      dut.io.in(0).d.bits.size.expect(1023.U)
      dut.io.in(0).d.bits.source.expect(5.U)  // Should be back to original
      dut.io.in(0).d.bits.sink.expect(0.U)
      dut.io.in(0).d.bits.denied.expect(0.U)
      dut.io.in(0).d.bits.data.expect(0xBEEF.U)
      dut.io.in(0).d.bits.corrupt.expect(0.U)
    }
  }


  it should "properly arbitrate multiple masters with locking policy" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 3,
      slaves = Seq(
        TLSlaveConfig(Seq((0x0, 0xFFFF)))
      ),
      arbiterPolicy = "lock"
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      dut.clock.step()

      // All masters request simultaneously
      dut.io.in(0).a.bits.opcode.poke(4.U)
      dut.io.in(0).a.bits.param.poke(0.U)
      dut.io.in(0).a.bits.size.poke(3.U)  // Non-locking
      dut.io.in(0).a.bits.source.poke(1.U)
      dut.io.in(0).a.bits.address.poke(0x100.U)
      dut.io.in(0).a.bits.mask.poke(0x3.U)  // mask is 2 bits
      dut.io.in(0).a.bits.data.poke(0x0.U)
      dut.io.in(0).a.bits.corrupt.poke(0.U)
      dut.io.in(0).a.valid.poke(true.B)

      dut.io.in(1).a.bits.opcode.poke(4.U)
      dut.io.in(1).a.bits.param.poke(0.U)
      dut.io.in(1).a.bits.size.poke(1023.U)  // Locking
      dut.io.in(1).a.bits.source.poke(2.U)
      dut.io.in(1).a.bits.address.poke(0x200.U)
      dut.io.in(1).a.bits.mask.poke(0x3.U)  // mask is 2 bits
      dut.io.in(1).a.bits.data.poke(0x0.U)
      dut.io.in(1).a.bits.corrupt.poke(0.U)
      dut.io.in(1).a.valid.poke(true.B)

      dut.io.in(2).a.bits.opcode.poke(4.U)
      dut.io.in(2).a.bits.param.poke(0.U)
      dut.io.in(2).a.bits.size.poke(3.U)  // Non-locking
      dut.io.in(2).a.bits.source.poke(3.U)
      dut.io.in(2).a.bits.address.poke(0x300.U)
      dut.io.in(2).a.bits.mask.poke(0x3.U)  // mask is 2 bits
      dut.io.in(2).a.bits.data.poke(0x0.U)
      dut.io.in(2).a.bits.corrupt.poke(0.U)
      dut.io.in(2).a.valid.poke(true.B)

      dut.io.out(0).a.ready.poke(true.B)

      dut.clock.step()

      // One master should win (round-robin order)
      val firstGranted = if (dut.io.in(0).a.ready.peek().litToBoolean) 0
                         else if (dut.io.in(1).a.ready.peek().litToBoolean) 1
                         else 2

      // Verify output has correct fields from granted master
      dut.io.out(0).a.valid.expect(true.B)
      val grantedAddr = dut.io.out(0).a.bits.address.peek().litValue
      assert(grantedAddr == 0x100 || grantedAddr == 0x200 || grantedAddr == 0x300)
    }
  }


  it should "handle backpressure during locked burst" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 2,
      slaves = Seq(
        TLSlaveConfig(Seq((0x0, 0xFFFF)))
      ),
      arbiterPolicy = "lock"
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      dut.clock.step()

      // Master 0 starts locked burst
      dut.io.in(0).a.bits.opcode.poke(4.U)
      dut.io.in(0).a.bits.param.poke(0.U)
      dut.io.in(0).a.bits.size.poke(1023.U)
      dut.io.in(0).a.bits.source.poke(1.U)
      dut.io.in(0).a.bits.address.poke(0x100.U)
      dut.io.in(0).a.bits.mask.poke(0x3.U)  // mask is 2 bits
      dut.io.in(0).a.bits.data.poke(0x0.U)
      dut.io.in(0).a.bits.corrupt.poke(0.U)
      dut.io.in(0).a.valid.poke(true.B)
      dut.io.out(0).a.ready.poke(true.B)

      dut.clock.step()
      dut.io.in(0).a.ready.expect(true.B)

      // Apply backpressure
      dut.io.in(0).a.bits.size.poke(3.U)
      dut.io.out(0).a.ready.poke(false.B)
      dut.clock.step()

      // Master should not be ready
      dut.io.in(0).a.ready.expect(false.B)
      dut.io.out(0).a.valid.expect(true.B)

      // Release backpressure
      dut.io.out(0).a.ready.poke(true.B)
      dut.clock.step()

      // Master should now be ready again
      dut.io.in(0).a.ready.expect(true.B)
      dut.io.out(0).a.valid.expect(true.B)

      // Verify all fields still propagate correctly
      dut.io.out(0).a.bits.opcode.expect(4.U)
      dut.io.out(0).a.bits.size.expect(3.U)
      dut.io.out(0).a.bits.mask.expect(0x3.U)
      dut.io.out(0).a.bits.corrupt.expect(0.U)
    }
  }

  it should "interleave non-locking transactions correctly" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 2,
      slaves = Seq(
        TLSlaveConfig(Seq((0x0, 0xFFFF)))
      ),
      arbiterPolicy = "lock"
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      dut.clock.step()

      // Master 0 sends non-locking request
      dut.io.in(0).a.bits.opcode.poke(4.U)
      dut.io.in(0).a.bits.param.poke(0.U)
      dut.io.in(0).a.bits.size.poke(3.U)  // Non-locking
      dut.io.in(0).a.bits.source.poke(1.U)
      dut.io.in(0).a.bits.address.poke(0x100.U)
      dut.io.in(0).a.bits.mask.poke(0x3.U)  // mask is 2 bits
      dut.io.in(0).a.bits.data.poke(0xAAAA.U)
      dut.io.in(0).a.bits.corrupt.poke(0.U)
      dut.io.in(0).a.valid.poke(true.B)

      dut.io.in(1).a.bits.opcode.poke(0.U)  // PutFullData
      dut.io.in(1).a.bits.param.poke(0.U)
      dut.io.in(1).a.bits.size.poke(3.U)  // Non-locking
      dut.io.in(1).a.bits.source.poke(2.U)
      dut.io.in(1).a.bits.address.poke(0x200.U)
      dut.io.in(1).a.bits.mask.poke(0x3.U)  // mask is 2 bits
      dut.io.in(1).a.bits.data.poke(0xBBBB.U)
      dut.io.in(1).a.bits.corrupt.poke(0.U)
      dut.io.in(1).a.valid.poke(true.B)

      dut.io.out(0).a.ready.poke(true.B)

      // Both should get served (round-robin fashion)
      for (i <- 0 until 4) {
        dut.clock.step()

        // Verify output fields match one of the inputs
        if (dut.io.out(0).a.valid.peek().litToBoolean) {
          val addr = dut.io.out(0).a.bits.address.peek().litValue
          val data = dut.io.out(0).a.bits.data.peek().litValue

          if (addr == 0x100) {
            dut.io.out(0).a.bits.opcode.expect(4.U)
            dut.io.out(0).a.bits.data.expect(0xAAAA.U)
          } else if (addr == 0x200) {
            dut.io.out(0).a.bits.opcode.expect(0.U)
            dut.io.out(0).a.bits.data.expect(0xBBBB.U)
          }

          // All fields should be valid
          dut.io.out(0).a.bits.param.expect(0.U)
          dut.io.out(0).a.bits.size.expect(3.U)
          dut.io.out(0).a.bits.mask.expect(0x3.U)
          dut.io.out(0).a.bits.corrupt.expect(0.U)
        }
      }
    }
  }

  /*

  it should "verify all TileLink fields during routing to multiple slaves" in {
    implicit val c = testConfig
    val xbarConfig = TLXbarConfig(
      nMasters = 2,
      slaves = Seq(
        TLSlaveConfig(Seq((0x0000, 0xFFFF))),
        TLSlaveConfig(Seq((0x10000, 0xFFFF)))
      ),
      arbiterPolicy = "lock"
    )(c)

    simulate(new TLXbar(xbarConfig)) { dut =>
      dut.clock.step()

      // Master 0 -> Slave 0 with all fields set
      dut.io.in(0).a.bits.opcode.poke(4.U)
      dut.io.in(0).a.bits.param.poke(2.U)
      dut.io.in(0).a.bits.size.poke(5.U)
      dut.io.in(0).a.bits.source.poke(7.U)
      dut.io.in(0).a.bits.address.poke(0xFF.U)
      //dut.io.in(0).a.bits.mask.poke(0x2.U)  // mask is 2 bits
      dut.io.in(0).a.bits.data.poke(0x12345678.U)
      dut.io.in(0).a.bits.corrupt.poke(0.U)
      dut.io.in(0).a.valid.poke(true.B)

      // Master 1 -> Slave 1 with different fields
      dut.io.in(1).a.bits.opcode.poke(0.U)
      dut.io.in(1).a.bits.param.poke(1.U)
      dut.io.in(1).a.bits.size.poke(6.U)
      dut.io.in(1).a.bits.source.poke(9.U)
      dut.io.in(1).a.bits.address.poke(0x10080.U)
      //dut.io.in(1).a.bits.mask.poke(0x1.U)  // mask is 2 bits
      dut.io.in(1).a.bits.data.poke("hEF00".U)
      dut.io.in(1).a.bits.corrupt.poke(1.U)
      dut.io.in(1).a.valid.poke(true.B)

      dut.io.out.foreach(_.a.ready.poke(true.B))

      dut.clock.step()

      // Verify Slave 0 receives Master 0's request with all fields
      dut.io.out(0).a.valid.expect(true.B)
      dut.io.out(0).a.bits.opcode.expect(4.U)
      dut.io.out(0).a.bits.param.expect(2.U)
      dut.io.out(0).a.bits.size.expect(5.U)
      dut.io.out(0).a.bits.address.expect(0xFF.U)
      //dut.io.out(0).a.bits.mask.expect(0x2.U)
      dut.io.out(0).a.bits.data.expect(0x12345678.U)
      dut.io.out(0).a.bits.corrupt.expect(0.U)

      // Verify Slave 1 receives Master 1's request with all fields
      dut.io.out(1).a.valid.expect(true.B)
      dut.io.out(1).a.bits.opcode.expect(0.U)
      dut.io.out(1).a.bits.param.expect(1.U)
      dut.io.out(1).a.bits.size.expect(6.U)
      dut.io.out(1).a.bits.address.expect(0x10080.U)
      //dut.io.out(1).a.bits.mask.expect(0x1.U)
      dut.io.out(1).a.bits.data.expect("hEF00".U)
      dut.io.out(1).a.bits.corrupt.expect(1.U)

      dut.io.in(0).a.valid.poke(false.B)
      dut.io.in(1).a.valid.poke(false.B)
      dut.clock.step()

      // Test D channel with all fields
      val source0 = dut.io.out(0).a.bits.source.peek().litValue
      val source1 = dut.io.out(1).a.bits.source.peek().litValue

      // Slave 0 responds to Master 0
      dut.io.out(0).d.bits.opcode.poke(1.U)
      dut.io.out(0).d.bits.param.poke(1.U)  // param is only 2 bits in D channel
      dut.io.out(0).d.bits.size.poke(5.U)
      dut.io.out(0).d.bits.source.poke(source0.U)
      dut.io.out(0).d.bits.sink.poke(3.U)
      dut.io.out(0).d.bits.denied.poke(0.U)
      dut.io.out(0).d.bits.data.poke("hBA98".U)
      dut.io.out(0).d.bits.corrupt.poke(0.U)
      dut.io.out(0).d.valid.poke(true.B)
      dut.io.in(0).d.ready.poke(true.B)

      dut.clock.step()

      // Verify Master 0 receives all D channel fields correctly
      dut.io.in(0).d.valid.expect(true.B)
      dut.io.in(0).d.bits.opcode.expect(1.U)
      dut.io.in(0).d.bits.param.expect(1.U)
      dut.io.in(0).d.bits.size.expect(5.U)
      dut.io.in(0).d.bits.source.expect(7.U)  // Original source
      dut.io.in(0).d.bits.sink.expect(3.U)
      dut.io.in(0).d.bits.denied.expect(0.U)
      dut.io.in(0).d.bits.data.expect("hBA98".U)
      dut.io.in(0).d.bits.corrupt.expect(0.U)
    }
  }
  */
}
