# ATAN

A Chisel-based hardware accelerator (ATA8) with a systolic-array matrix compute core (MCC), RISC-V coexecution support, and a custom EAAC assembler/ISA for programming it.

## Layout

- `src/main/scala/ACCEL` — accelerator hardware (Chisel): systolic array, scratchpad, semaphores, trigger system, TileLink interfaces, control logic
- `assembler` — EAAC assembler (parses `.eaac` sources into program binaries/memhex)
- `shared` — instruction set definitions shared between hardware and assembler
- `test` — example EAAC/RISC-V programs and generated test binaries
- `eaac_program.fbs` — FlatBuffer schema for EAAC programs

## Build tool

Uses [Mill](https://mill-build.org), driven through `just`. JDK 11+ and Verilator required.

```sh
just comp          # compile Chisel sources
just test          # run all tests
just test-only <Class>          # run a specific test class
just test-case <Class> "<name>" # run a single test case
just verilog       # emit SystemVerilog
just flatbuffer    # regenerate FlatBuffer Java classes (needs flatc)
just maketest <dir>              # build a test program under test/<dir>
```
