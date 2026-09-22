; ModuleID = 'LLVMDialectModule'
source_filename = "LLVMDialectModule"

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_25(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i64 %7, i64 %8, i64 %9, i64 %10, i64 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 -2147417849 to ptr), align 4
  %22 = icmp uge i32 %21, 256
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 -2147417849 to ptr), i32 -256 acquire, align 4
  br label %25

25:                                               ; preds = %51, %23
  %26 = phi i32 [ 0, %23 ], [ %52, %51 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %53

28:                                               ; preds = %25
  br label %29

29:                                               ; preds = %32, %28
  %30 = phi i32 [ 0, %28 ], [ %50, %32 ]
  %31 = icmp slt i32 %30, 16
  br i1 %31, label %32, label %51

32:                                               ; preds = %29
  %33 = mul nuw nsw i32 %26, 16
  %34 = add nuw nsw i32 %33, %30
  %35 = getelementptr inbounds nuw i32, ptr %13, i32 %34
  %36 = load i32, ptr %35, align 4
  %37 = sext i32 %36 to i64
  %38 = mul i64 %37, %7
  %39 = add i64 %38, %8
  %40 = ashr i64 %39, %9
  %41 = add i64 %40, %10
  %42 = icmp sgt i64 %41, %10
  %43 = select i1 %42, i64 %41, i64 %10
  %44 = icmp slt i64 %43, %11
  %45 = select i1 %44, i64 %43, i64 %11
  %46 = trunc i64 %45 to i8
  %47 = mul nuw nsw i32 %26, 16
  %48 = add nuw nsw i32 %47, %30
  %49 = getelementptr inbounds nuw i8, ptr %1, i32 %48
  store i8 %46, ptr %49, align 1
  %50 = add i32 %30, 1
  br label %29

51:                                               ; preds = %29
  %52 = add i32 %26, 1
  br label %25

53:                                               ; preds = %25
  %54 = atomicrmw add ptr inttoptr (i32 -2147417853 to ptr), i32 256 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_24(i32 %0, ptr %1, ptr %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %34, %15
  %17 = phi i32 [ 0, %15 ], [ %35, %34 ]
  %18 = icmp slt i32 %17, 16
  br i1 %18, label %19, label %36

19:                                               ; preds = %16
  br label %20

20:                                               ; preds = %23, %19
  %21 = phi i32 [ 0, %19 ], [ %33, %23 ]
  %22 = icmp slt i32 %21, 16
  br i1 %22, label %23, label %34

23:                                               ; preds = %20
  %24 = mul nuw nsw i32 %17, 16
  %25 = add nuw nsw i32 %24, %21
  %26 = getelementptr inbounds nuw i32, ptr %2, i32 %25
  %27 = load i32, ptr %26, align 4
  %28 = icmp sgt i32 %27, %0
  %29 = select i1 %28, i32 %27, i32 %0
  %30 = mul nuw nsw i32 %17, 16
  %31 = add nuw nsw i32 %30, %21
  %32 = getelementptr inbounds nuw i32, ptr %9, i32 %31
  store i32 %29, ptr %32, align 4
  %33 = add i32 %21, 1
  br label %20

34:                                               ; preds = %20
  %35 = add i32 %17, 1
  br label %16

36:                                               ; preds = %16
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_23(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, ptr %5, ptr %6, i32 %7, i32 %8, i32 %9, i32 %10, i32 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 -2147417767 to ptr), align 4
  %22 = icmp uge i32 %21, 64
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 -2147417767 to ptr), i32 -64 acquire, align 4
  br label %25

25:                                               ; preds = %44, %23
  %26 = phi i32 [ 0, %23 ], [ %45, %44 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %46

28:                                               ; preds = %25
  br label %29

29:                                               ; preds = %32, %28
  %30 = phi i32 [ 0, %28 ], [ %43, %32 ]
  %31 = icmp slt i32 %30, 16
  br i1 %31, label %32, label %44

32:                                               ; preds = %29
  %33 = mul nuw nsw i32 %26, 16
  %34 = add nuw nsw i32 %33, %30
  %35 = getelementptr inbounds nuw i32, ptr %6, i32 %34
  %36 = load i32, ptr %35, align 4
  %37 = getelementptr inbounds nuw i32, ptr %1, i32 %30
  %38 = load i32, ptr %37, align 4
  %39 = add i32 %36, %38
  %40 = mul nuw nsw i32 %26, 16
  %41 = add nuw nsw i32 %40, %30
  %42 = getelementptr inbounds nuw i32, ptr %13, i32 %41
  store i32 %39, ptr %42, align 4
  %43 = add i32 %30, 1
  br label %29

44:                                               ; preds = %29
  %45 = add i32 %26, 1
  br label %25

46:                                               ; preds = %25
  %47 = atomicrmw add ptr inttoptr (i32 -2147417763 to ptr), i32 64 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_22(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417861 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417861 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417857 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_21(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417877 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417877 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417873 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_20(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417893 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417893 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417889 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_19(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417909 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417909 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417905 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_18(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417925 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417925 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417921 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_17(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417941 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417941 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417937 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_16(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417957 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417957 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417953 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_15(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417973 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417973 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417969 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_14(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417989 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417989 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417985 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_13(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418005 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418005 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147418001 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_12(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418021 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418021 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147418017 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_11(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418037 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418037 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147418033 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_10(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418053 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418053 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147418049 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_9(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418069 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418069 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147418065 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_8(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418085 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418085 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147418081 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_7(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418101 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418101 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147418097 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_6(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417606 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417606 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417602 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_5(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417654 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417654 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417650 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_4(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417702 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417702 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417698 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_3(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417623 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417623 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417619 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_2(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417671 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417671 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417667 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_1(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417719 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417719 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %48, %25
  %28 = phi i32 [ 0, %25 ], [ %49, %48 ]
  %29 = icmp slt i32 %28, 16
  br i1 %29, label %30, label %50

30:                                               ; preds = %27
  br label %31

31:                                               ; preds = %34, %30
  %32 = phi i32 [ 0, %30 ], [ %47, %34 ]
  %33 = icmp slt i32 %32, 16
  br i1 %33, label %34, label %48

34:                                               ; preds = %31
  %35 = mul nuw nsw i32 %28, 16
  %36 = add nuw nsw i32 %35, %32
  %37 = getelementptr inbounds nuw i32, ptr %8, i32 %36
  %38 = load i32, ptr %37, align 4
  %39 = mul nuw nsw i32 %28, 16
  %40 = add nuw nsw i32 %39, %32
  %41 = getelementptr inbounds nuw i32, ptr %1, i32 %40
  %42 = load i32, ptr %41, align 4
  %43 = add i32 %38, %42
  %44 = mul nuw nsw i32 %28, 16
  %45 = add nuw nsw i32 %44, %32
  %46 = getelementptr inbounds nuw i32, ptr %15, i32 %45
  store i32 %43, ptr %46, align 4
  %47 = add i32 %32, 1
  br label %31

48:                                               ; preds = %31
  %49 = add i32 %28, 1
  br label %27

50:                                               ; preds = %27
  %51 = atomicrmw add ptr inttoptr (i32 -2147417715 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_0(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417640 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417640 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %27, %25
  %28 = load volatile i32, ptr inttoptr (i32 -2147417688 to ptr), align 4
  %29 = icmp uge i32 %28, 1024
  br i1 %29, label %30, label %27

30:                                               ; preds = %27
  %31 = atomicrmw add ptr inttoptr (i32 -2147417688 to ptr), i32 -1024 acquire, align 4
  br label %32

32:                                               ; preds = %53, %30
  %33 = phi i32 [ 0, %30 ], [ %54, %53 ]
  %34 = icmp slt i32 %33, 16
  br i1 %34, label %35, label %55

35:                                               ; preds = %32
  br label %36

36:                                               ; preds = %39, %35
  %37 = phi i32 [ 0, %35 ], [ %52, %39 ]
  %38 = icmp slt i32 %37, 16
  br i1 %38, label %39, label %53

39:                                               ; preds = %36
  %40 = mul nuw nsw i32 %33, 16
  %41 = add nuw nsw i32 %40, %37
  %42 = getelementptr inbounds nuw i32, ptr %8, i32 %41
  %43 = load i32, ptr %42, align 4
  %44 = mul nuw nsw i32 %33, 16
  %45 = add nuw nsw i32 %44, %37
  %46 = getelementptr inbounds nuw i32, ptr %1, i32 %45
  %47 = load i32, ptr %46, align 4
  %48 = add i32 %43, %47
  %49 = mul nuw nsw i32 %33, 16
  %50 = add nuw nsw i32 %49, %37
  %51 = getelementptr inbounds nuw i32, ptr %15, i32 %50
  store i32 %48, ptr %51, align 4
  %52 = add i32 %37, 1
  br label %36

53:                                               ; preds = %36
  %54 = add i32 %33, 1
  br label %32

55:                                               ; preds = %32
  %56 = atomicrmw add ptr inttoptr (i32 -2147417636 to ptr), i32 1024 release, align 4
  %57 = atomicrmw add ptr inttoptr (i32 -2147417684 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_0() #0 {
  call void @__impl_riscv_kernel_0(ptr inttoptr (i32 -2147476224 to ptr), ptr inttoptr (i32 -2147476224 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147477504 to ptr), ptr inttoptr (i32 -2147477504 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147446784 to ptr), ptr inttoptr (i32 -2147446784 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_1() #0 {
  call void @__impl_riscv_kernel_1(ptr inttoptr (i32 -2147474944 to ptr), ptr inttoptr (i32 -2147474944 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147446784 to ptr), ptr inttoptr (i32 -2147446784 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147445760 to ptr), ptr inttoptr (i32 -2147445760 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_2() #0 {
  call void @__impl_riscv_kernel_2(ptr inttoptr (i32 -2147473664 to ptr), ptr inttoptr (i32 -2147473664 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147445760 to ptr), ptr inttoptr (i32 -2147445760 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147444736 to ptr), ptr inttoptr (i32 -2147444736 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_3() #0 {
  call void @__impl_riscv_kernel_3(ptr inttoptr (i32 -2147472384 to ptr), ptr inttoptr (i32 -2147472384 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147444736 to ptr), ptr inttoptr (i32 -2147444736 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147443712 to ptr), ptr inttoptr (i32 -2147443712 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_4() #0 {
  call void @__impl_riscv_kernel_4(ptr inttoptr (i32 -2147471104 to ptr), ptr inttoptr (i32 -2147471104 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147443712 to ptr), ptr inttoptr (i32 -2147443712 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147442688 to ptr), ptr inttoptr (i32 -2147442688 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_5() #0 {
  call void @__impl_riscv_kernel_5(ptr inttoptr (i32 -2147469824 to ptr), ptr inttoptr (i32 -2147469824 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147442688 to ptr), ptr inttoptr (i32 -2147442688 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147441664 to ptr), ptr inttoptr (i32 -2147441664 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_6() #0 {
  call void @__impl_riscv_kernel_6(ptr inttoptr (i32 -2147468544 to ptr), ptr inttoptr (i32 -2147468544 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147441664 to ptr), ptr inttoptr (i32 -2147441664 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147440640 to ptr), ptr inttoptr (i32 -2147440640 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_7() #0 {
  call void @__impl_riscv_kernel_7(ptr inttoptr (i32 -2147467264 to ptr), ptr inttoptr (i32 -2147467264 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147440640 to ptr), ptr inttoptr (i32 -2147440640 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147439616 to ptr), ptr inttoptr (i32 -2147439616 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_8() #0 {
  call void @__impl_riscv_kernel_8(ptr inttoptr (i32 -2147465984 to ptr), ptr inttoptr (i32 -2147465984 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147439616 to ptr), ptr inttoptr (i32 -2147439616 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147438592 to ptr), ptr inttoptr (i32 -2147438592 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_9() #0 {
  call void @__impl_riscv_kernel_9(ptr inttoptr (i32 -2147464704 to ptr), ptr inttoptr (i32 -2147464704 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147438592 to ptr), ptr inttoptr (i32 -2147438592 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147437568 to ptr), ptr inttoptr (i32 -2147437568 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_10() #0 {
  call void @__impl_riscv_kernel_10(ptr inttoptr (i32 -2147463424 to ptr), ptr inttoptr (i32 -2147463424 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147437568 to ptr), ptr inttoptr (i32 -2147437568 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147436544 to ptr), ptr inttoptr (i32 -2147436544 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_11() #0 {
  call void @__impl_riscv_kernel_11(ptr inttoptr (i32 -2147462144 to ptr), ptr inttoptr (i32 -2147462144 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147436544 to ptr), ptr inttoptr (i32 -2147436544 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147435520 to ptr), ptr inttoptr (i32 -2147435520 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_12() #0 {
  call void @__impl_riscv_kernel_12(ptr inttoptr (i32 -2147460864 to ptr), ptr inttoptr (i32 -2147460864 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147435520 to ptr), ptr inttoptr (i32 -2147435520 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147434496 to ptr), ptr inttoptr (i32 -2147434496 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_13() #0 {
  call void @__impl_riscv_kernel_13(ptr inttoptr (i32 -2147459584 to ptr), ptr inttoptr (i32 -2147459584 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147434496 to ptr), ptr inttoptr (i32 -2147434496 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147433472 to ptr), ptr inttoptr (i32 -2147433472 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_14() #0 {
  call void @__impl_riscv_kernel_14(ptr inttoptr (i32 -2147458304 to ptr), ptr inttoptr (i32 -2147458304 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147433472 to ptr), ptr inttoptr (i32 -2147433472 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147432448 to ptr), ptr inttoptr (i32 -2147432448 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_15() #0 {
  call void @__impl_riscv_kernel_15(ptr inttoptr (i32 -2147457024 to ptr), ptr inttoptr (i32 -2147457024 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147432448 to ptr), ptr inttoptr (i32 -2147432448 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147431424 to ptr), ptr inttoptr (i32 -2147431424 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_16() #0 {
  call void @__impl_riscv_kernel_16(ptr inttoptr (i32 -2147455744 to ptr), ptr inttoptr (i32 -2147455744 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147431424 to ptr), ptr inttoptr (i32 -2147431424 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147430400 to ptr), ptr inttoptr (i32 -2147430400 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_17() #0 {
  call void @__impl_riscv_kernel_17(ptr inttoptr (i32 -2147454464 to ptr), ptr inttoptr (i32 -2147454464 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147430400 to ptr), ptr inttoptr (i32 -2147430400 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147429376 to ptr), ptr inttoptr (i32 -2147429376 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_18() #0 {
  call void @__impl_riscv_kernel_18(ptr inttoptr (i32 -2147453184 to ptr), ptr inttoptr (i32 -2147453184 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147429376 to ptr), ptr inttoptr (i32 -2147429376 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147428352 to ptr), ptr inttoptr (i32 -2147428352 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_19() #0 {
  call void @__impl_riscv_kernel_19(ptr inttoptr (i32 -2147451904 to ptr), ptr inttoptr (i32 -2147451904 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147428352 to ptr), ptr inttoptr (i32 -2147428352 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147427328 to ptr), ptr inttoptr (i32 -2147427328 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_20() #0 {
  call void @__impl_riscv_kernel_20(ptr inttoptr (i32 -2147450624 to ptr), ptr inttoptr (i32 -2147450624 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147427328 to ptr), ptr inttoptr (i32 -2147427328 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147426304 to ptr), ptr inttoptr (i32 -2147426304 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_21() #0 {
  call void @__impl_riscv_kernel_21(ptr inttoptr (i32 -2147449344 to ptr), ptr inttoptr (i32 -2147449344 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147426304 to ptr), ptr inttoptr (i32 -2147426304 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147425280 to ptr), ptr inttoptr (i32 -2147425280 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_22() #0 {
  call void @__impl_riscv_kernel_22(ptr inttoptr (i32 -2147448064 to ptr), ptr inttoptr (i32 -2147448064 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147425280 to ptr), ptr inttoptr (i32 -2147425280 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147424256 to ptr), ptr inttoptr (i32 -2147424256 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_23() #0 {
  call void @__impl_riscv_kernel_23(ptr inttoptr (i32 -2147422208 to ptr), ptr inttoptr (i32 -2147422208 to ptr), i32 0, i32 16, i32 1, ptr inttoptr (i32 -2147424256 to ptr), ptr inttoptr (i32 -2147424256 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147423232 to ptr), ptr inttoptr (i32 -2147423232 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_24() #0 {
  call void @__impl_riscv_kernel_24(i32 0, ptr inttoptr (i32 -2147423232 to ptr), ptr inttoptr (i32 -2147423232 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147422144 to ptr), ptr inttoptr (i32 -2147422144 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_25() #0 {
  call void @__impl_riscv_kernel_25(ptr inttoptr (i32 -2147421120 to ptr), ptr inttoptr (i32 -2147421120 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 -2147422144 to ptr), ptr inttoptr (i32 -2147422144 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @main() #0 {
  call void @riscv_kernel_0()
  call void @riscv_kernel_1()
  call void @riscv_kernel_2()
  call void @riscv_kernel_3()
  call void @riscv_kernel_4()
  call void @riscv_kernel_5()
  call void @riscv_kernel_6()
  call void @riscv_kernel_7()
  call void @riscv_kernel_8()
  call void @riscv_kernel_9()
  call void @riscv_kernel_10()
  call void @riscv_kernel_11()
  call void @riscv_kernel_12()
  call void @riscv_kernel_13()
  call void @riscv_kernel_14()
  call void @riscv_kernel_15()
  call void @riscv_kernel_16()
  call void @riscv_kernel_17()
  call void @riscv_kernel_18()
  call void @riscv_kernel_19()
  call void @riscv_kernel_20()
  call void @riscv_kernel_21()
  call void @riscv_kernel_22()
  call void @riscv_kernel_23()
  call void @riscv_kernel_24()
  call void @riscv_kernel_25()
  ret void
}

attributes #0 = { null_pointer_is_valid }

!llvm.module.flags = !{!0}

!0 = !{i32 2, !"Debug Info Version", i32 3}
