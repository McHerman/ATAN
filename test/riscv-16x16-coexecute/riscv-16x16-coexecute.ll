; ModuleID = 'LLVMDialectModule'
source_filename = "LLVMDialectModule"

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_1(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, i64 %14, i64 %15, i64 %16, i64 %17, i64 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 16452 to ptr), align 4
  %22 = icmp uge i32 %21, 256
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 16452 to ptr), i32 -256 acquire, align 4
  br label %25

25:                                               ; preds = %25, %23
  %26 = load volatile i32, ptr inttoptr (i32 16440 to ptr), align 4
  %27 = icmp uge i32 %26, 1024
  br i1 %27, label %28, label %25

28:                                               ; preds = %25
  %29 = atomicrmw add ptr inttoptr (i32 16440 to ptr), i32 -1024 acquire, align 4
  br label %30

30:                                               ; preds = %56, %28
  %31 = phi i32 [ 0, %28 ], [ %57, %56 ]
  %32 = icmp slt i32 %31, 16
  br i1 %32, label %33, label %58

33:                                               ; preds = %30
  br label %34

34:                                               ; preds = %37, %33
  %35 = phi i32 [ 0, %33 ], [ %55, %37 ]
  %36 = icmp slt i32 %35, 16
  br i1 %36, label %37, label %56

37:                                               ; preds = %34
  %38 = mul nuw nsw i32 %31, 16
  %39 = add nuw nsw i32 %38, %35
  %40 = getelementptr inbounds nuw i32, ptr %8, i32 %39
  %41 = load i32, ptr %40, align 4
  %42 = sext i32 %41 to i64
  %43 = mul i64 %42, %14
  %44 = add i64 %43, %15
  %45 = ashr i64 %44, %16
  %46 = add i64 %45, %17
  %47 = icmp sgt i64 %46, %17
  %48 = select i1 %47, i64 %46, i64 %17
  %49 = icmp slt i64 %48, %18
  %50 = select i1 %49, i64 %48, i64 %18
  %51 = trunc i64 %50 to i8
  %52 = mul nuw nsw i32 %31, 16
  %53 = add nuw nsw i32 %52, %35
  %54 = getelementptr inbounds nuw i8, ptr %1, i32 %53
  store i8 %51, ptr %54, align 1
  %55 = add i32 %35, 1
  br label %34

56:                                               ; preds = %34
  %57 = add i32 %31, 1
  br label %30

58:                                               ; preds = %30
  %59 = atomicrmw add ptr inttoptr (i32 16448 to ptr), i32 256 release, align 4
  %60 = atomicrmw add ptr inttoptr (i32 16444 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_0(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %16, %15
  %17 = load volatile i32, ptr inttoptr (i32 16436 to ptr), align 4
  %18 = icmp uge i32 %17, 1024
  br i1 %18, label %19, label %16

19:                                               ; preds = %16
  %20 = atomicrmw add ptr inttoptr (i32 16436 to ptr), i32 -1024 acquire, align 4
  br label %21

21:                                               ; preds = %21, %19
  %22 = load volatile i32, ptr inttoptr (i32 16424 to ptr), align 4
  %23 = icmp uge i32 %22, 1024
  br i1 %23, label %24, label %21

24:                                               ; preds = %21
  %25 = atomicrmw add ptr inttoptr (i32 16424 to ptr), i32 -1024 acquire, align 4
  br label %26

26:                                               ; preds = %44, %24
  %27 = phi i32 [ 0, %24 ], [ %45, %44 ]
  %28 = icmp slt i32 %27, 16
  br i1 %28, label %29, label %46

29:                                               ; preds = %26
  br label %30

30:                                               ; preds = %33, %29
  %31 = phi i32 [ 0, %29 ], [ %43, %33 ]
  %32 = icmp slt i32 %31, 16
  br i1 %32, label %33, label %44

33:                                               ; preds = %30
  %34 = mul nuw nsw i32 %27, 16
  %35 = add nuw nsw i32 %34, %31
  %36 = getelementptr inbounds nuw i32, ptr %8, i32 %35
  %37 = load i32, ptr %36, align 4
  %38 = icmp sgt i32 %37, %14
  %39 = select i1 %38, i32 %37, i32 %14
  %40 = mul nuw nsw i32 %27, 16
  %41 = add nuw nsw i32 %40, %31
  %42 = getelementptr inbounds nuw i32, ptr %1, i32 %41
  store i32 %39, ptr %42, align 4
  %43 = add i32 %31, 1
  br label %30

44:                                               ; preds = %30
  %45 = add i32 %27, 1
  br label %26

46:                                               ; preds = %26
  %47 = atomicrmw add ptr inttoptr (i32 16432 to ptr), i32 1024 release, align 4
  %48 = atomicrmw add ptr inttoptr (i32 16428 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_0() #0 {
  call void @__impl_riscv_kernel_0(ptr inttoptr (i32 1536 to ptr), ptr inttoptr (i32 1536 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 512 to ptr), ptr inttoptr (i32 512 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i32 0)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_1() #0 {
  call void @__impl_riscv_kernel_1(ptr null, ptr null, i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 1536 to ptr), ptr inttoptr (i32 1536 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i64 1699402752, i64 17179869184, i64 35, i64 -128, i64 127)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @main() #0 {
  call void @riscv_kernel_0()
  call void @riscv_kernel_1()
  ret void
}

attributes #0 = { null_pointer_is_valid }

!llvm.module.flags = !{!0}

!0 = !{i32 2, !"Debug Info Version", i32 3}
