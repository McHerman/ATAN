.section .text
.globl __mulsi3
__mulsi3:
    mv      a2, a0
    li      a0, 0
1:
    andi    a3, a1, 1
    beqz    a3, 2f
    add     a0, a0, a2
2:
    srli    a1, a1, 1
    slli    a2, a2, 1
    bnez    a1, 1b
    ret
