OUTPUT_ARCH("riscv")
ENTRY(_start)

SECTIONS {
    . = 0x80000000;

    /* clang emits CFI/unwind-table sections even for -x ir input regardless
     * of -fno-(asynchronous-)unwind-tables (those are C-frontend flags and
     * don't apply once IR bypasses the frontend); discard them so nothing
     * lands before .text.init at the reset vector. */
    /DISCARD/ : { *(.eh_frame) *(.eh_frame_hdr) }

    .text : {
        *(.text.init)
        *(.text*)
    }

    .rodata : { *(.rodata*) }

    .data : {
        *(.data*)
        *(.sdata*)
    }

    .bss : {
        __bss_start = .;
        *(.bss*)
        *(.sbss*)
        __bss_end = .;
    }

    . = 0x80001000;
    .tohost : {
        tohost   = .;
        . += 4;
        fromhost = .;
        . += 4;
    }
}
