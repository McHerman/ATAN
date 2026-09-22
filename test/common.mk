# Shared cross-compile rules for EAAC riscv-kernel benchmarks: takes the
# LLVM IR (.ll) emitted by --eaac-split-llvm-from-eaac and turns it into a
# .memhex ready for MccHexReader.
#
# Usage, from a per-benchmark test/<name>/Makefile:
#
#   TARGET := <name>
#   include ../common.mk
#
# Override CFLAGS/EXTRA_SRCS/LDSCRIPT/CRT0 *before* the include if a
# benchmark needs something nonstandard (e.g. an extra .S helper, a
# different -march). See test/riscv-coexecute-test/Makefile for an example
# (extra mulsi3.S source, no -O1).

CC      := clang
OBJCOPY := llvm-objcopy
BIN2HEX := python3 $(dir $(lastword $(MAKEFILE_LIST)))bin2hex.py

CRT0     ?= crt0.S
LDSCRIPT ?= link.ld
EXTRA_SRCS ?=

CFLAGS ?= --target=riscv32 -march=rv32ima_zabha -mabi=ilp32 -mcmodel=medany \
          -nostdlib -nostartfiles -fuse-ld=lld \
          -fno-unwind-tables -fno-asynchronous-unwind-tables -O2

# nixpkgs' cc-wrapper force-injects -fzero-call-used-regs (part of its
# default hardening flags) regardless of target, and riscv32 rejects that
# flag outright -- clang errors out before it ever gets to compile
# anything. This is a wrapper behavior, not a compiler-version issue (still
# reproduces with a freshly-pinned clang/lld/llvm-objcopy from flake.nix),
# so it has to be disabled here rather than fixed upstream.
export NIX_HARDENING_ENABLE :=

all: $(TARGET).memhex

$(TARGET).elf: $(TARGET).ll $(CRT0) $(LDSCRIPT) $(EXTRA_SRCS)
	$(CC) $(CFLAGS) -T $(LDSCRIPT) -o $@ $(CRT0) $(EXTRA_SRCS) -x ir $(TARGET).ll

%.memhex: %.elf
	$(OBJCOPY) -O binary $< $@.bin
	$(BIN2HEX) $@.bin $@
	@rm -f $@.bin

%.dump: %.elf
	llvm-objdump -d $< > $@

clean:
	rm -f *.elf *.memhex *.dump *.bin

.PHONY: all clean
