package ATA8

import chisel3._
import chisel3.util._


class SysCtrl(implicit c: Configuration) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new SysOP))
    
    val activate = Output(Bool()) //TODO: change to camelcase
    /* val Shift = Output(Bool())
    val Enable = Output(Bool())
    val Mode = Output(UInt(1.W)) */

    val ctrl = Output(new Bundle{val state = UInt(1.W); val shift = Bool()})
    
    val sizes = Output(Vec(c.grainDim, UInt(log2Ceil(c.arrayDim + 1).W)))
    val activateLoopBack = Input(Bool())

    val completed = Output(Bool())
  })

  io.in.ready := false.B

  /* io.Shift := false.B
  io.Activate := false.B
  io.Enable := false.B
  io.Mode := 0.U */

  io.completed := false.B

  val ShiftCnt = RegInit(0.U(8.W))
  val ActivateCnt = RegInit(0.U(8.W))
  val EnableCnt = RegInit(0.U(8.W))
  val WaitCnt = RegInit(0.U(8.W))

  val ctrlOutReg = RegInit(0.U.asTypeOf(new Bundle{val state = UInt(1.W); val shift = Bool()}))
  val activateOutReg = RegInit(false.B)

  val ctrlOut = Wire(new Bundle{val state = UInt(1.W); val shift = Bool()})
  ctrlOut.state := 0.U
  ctrlOut.shift := false.B

  ctrlOutReg := ctrlOut
  io.ctrl := ctrlOutReg

  val activateOut = Wire(Bool())
  activateOut := false.B

  activateOutReg := activateOut
  io.activate := activateOutReg

  
  val StateReg = RegInit(0.U(4.W))

  val inReg = RegInit(0.U.asTypeOf(new SysOP))

  io.sizes := inReg.sizes

  val dbgCycle = RegInit(0.U(32.W))
  dbgCycle := dbgCycle + 1.U
  val dbgOpCount = RegInit(0.U(32.W))
  when(io.in.fire) {
    dbgOpCount := dbgOpCount + 1.U
    if(c.verbosePrint) {
      printf(p"cyc=${dbgCycle} [sysctrl-op] #${dbgOpCount} mode=${io.in.bits.mode} size=${io.in.bits.size} rows=${io.in.bits.rows}\n")
    }
  }

  // StateReg edge trace: StateReg==0 is idle (ready for the next op);
  // any other value is "GEMM busy" (shift/activate/OS-mode). Printed only on
  // transitions (not every cycle) so a timing-breakdown script can integrate
  // busy/idle intervals between consecutive events.
  if (c.verbosePrint) {
    val prevStateReg = RegNext(StateReg, 0.U)
    when(StateReg =/= prevStateReg) {
      printf(p"cyc=${dbgCycle} [sysctrl-state] from=${prevStateReg} to=${StateReg}\n")
    }
  }

  switch(StateReg){ // TODO, Add enumerations
    is(0.U){
      io.in.ready := true.B

      when(io.in.valid){
        inReg := io.in.bits
  
        switch(io.in.bits.mode){
          is(0.U){ // WS
            StateReg := 1.U
          }
          is(1.U){ // OS
            StateReg := 5.U
          }
        }
      }
    }
    is(1.U){
      //io.Mode := 0.U
      ctrlOut.state := 0.U

      when(ShiftCnt < inReg.size){
        //io.Shift := true.B
        ctrlOut.shift := true.B
        ShiftCnt := ShiftCnt + 1.U
      }.otherwise{
        ShiftCnt := 0.U
        StateReg := 2.U
      }
    }
    is(2.U){ //
      //io.Mode := 0.U
      ctrlOut.state := 0.U

      when(ActivateCnt < inReg.rows){
        //io.Activate := true.B
        activateOut := true.B
        ActivateCnt := ActivateCnt + 1.U
      }.otherwise{
        ActivateCnt := 0.U
        StateReg := 4.U
      }
    }
    is(4.U){
      when(ActivateCnt < inReg.rows){
        when(io.activateLoopBack){
          ActivateCnt := ActivateCnt + 1.U
        }
      }.otherwise{
        ActivateCnt := 0.U
        StateReg := 0.U

        io.completed := true.B
      }
    }

    is(5.U){
      //io.Mode := 0.U
      ctrlOut.state := 1.U

      when(ActivateCnt < c.arrayDim.U){
        //io.Activate := true.B
        activateOut := true.B
        ActivateCnt := ActivateCnt + 1.U
      }

      when(EnableCnt < (c.arrayDim.U * 2.U)){
        //io.Enable := true.B
        EnableCnt := EnableCnt + 1.U
      }.otherwise{
        ActivateCnt := 0.U
        EnableCnt := 0.U
        StateReg := 6.U
      }
    }
    is(6.U){
      //io.Mode := 0.U
      ctrlOut.state := 1.U

      when(WaitCnt < c.arrayDim.U){
        WaitCnt := WaitCnt + 1.U
      }.otherwise{
        WaitCnt := 0.U
        StateReg := 7.U
      }
    }
    is(7.U){ //
      //io.Mode := 0.U
      ctrlOut.state := 1.U

      when(ShiftCnt < c.arrayDim.U){
        //io.Shift := true.B
        ctrlOut.shift := true.B
        ShiftCnt := ShiftCnt + 1.U
      }.otherwise{
        ShiftCnt := 0.U
        StateReg := 0.U

        io.completed := true.B
      }
    }
  }

}
