CC       = clang
OBJCOPY  = llvm-objcopy
CFLAGS   = --target=riscv32 -march=rv32ima_zabha -mabi=ilp32 -mcmodel=medany -nostdlib -nostartfiles -fuse-ld=lld -fno-unwind-tables -fno-asynchronous-unwind-tables -O1
LDSCRIPT = link.ld
BIN2HEX  = python3 bin2hex.py
CRT0     = crt0.S

TARGET = riscv-16x16-coexecute

all: $(TARGET).memhex

$(TARGET).elf: $(TARGET).ll $(CRT0) $(LDSCRIPT)
	$(CC) $(CFLAGS) -T $(LDSCRIPT) -o $@ $(CRT0) -x ir $(TARGET).ll

%.memhex: %.elf
	$(OBJCOPY) -O binary $< $@.bin
	$(BIN2HEX) $@.bin $@
	@rm -f $@.bin

%.dump: %.elf
	llvm-objdump -d $< > $@

clean:
	rm -f *.elf *.memhex *.dump *.bin
