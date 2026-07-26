; ModuleID = 'LLVMDialectModule'
source_filename = "LLVMDialectModule"

define void @__impl_riscv_kernel_0(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, i8 %14, i8 %15) {
  br label %17

17:                                               ; preds = %17, %16
  %18 = load i32, ptr inttoptr (i32 16452 to ptr), align 4
  %19 = icmp uge i32 %18, 64
  br i1 %19, label %20, label %17

20:                                               ; preds = %17
  %21 = atomicrmw add ptr inttoptr (i32 16452 to ptr), i32 -64 acquire, align 4
  br label %22

22:                                               ; preds = %22, %20
  %23 = load i32, ptr inttoptr (i32 16440 to ptr), align 4
  %24 = icmp uge i32 %23, 64
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 16440 to ptr), i32 -64 acquire, align 4
  br label %27

27:                                               ; preds = %45, %25
  %28 = phi i32 [ 0, %25 ], [ %46, %45 ]
  %29 = icmp slt i32 %28, 8
  br i1 %29, label %30, label %47

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %44, %34 ]
  %33 = icmp slt i32 %32, 8
  br i1 %33, label %34, label %45

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 8
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i8, ptr %8, i32 %36
  %38 = load i8, ptr %37, align 1
  %39 = mul i8 %38, %14
  %40 = add i8 %39, %15
  %41 = mul nuw nsw i32 %28, 8
  %42 = add nuw nsw i32 %41, %32
  %43 = getelementptr inbounds nuw i8, ptr %1, i32 %42
  store i8 %40, ptr %43, align 1
  %44 = add i32 %32, 1
  br label %31

45:                                               ; preds = %31
  %46 = add i32 %28, 1
  br label %27

47:                                               ; preds = %27
  %48 = atomicrmw add ptr inttoptr (i32 16448 to ptr), i32 64 release, align 4
  %49 = atomicrmw add ptr inttoptr (i32 16444 to ptr), i32 64 release, align 4
  ret void
}

define void @riscv_kernel_0() {
  call void @__impl_riscv_kernel_0(ptr null, ptr null, i32 0, i32 8, i32 8, i32 8, i32 1, ptr inttoptr (i32 64 to ptr), ptr inttoptr (i32 64 to ptr), i32 0, i32 8, i32 8, i32 8, i32 1, i8 2, i8 1)
  ret void
}

define void @main() {
  call void @riscv_kernel_0()
  ret void
}

!llvm.module.flags = !{!0}

!0 = !{i32 2, !"Debug Info Version", i32 3}
