; ModuleID = 'LLVMDialectModule'
source_filename = "LLVMDialectModule"

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_15(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i64 %7, i64 %8, i64 %9, i64 %10, i64 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 -2147417979 to ptr), align 4
  %22 = icmp uge i32 %21, 256
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 -2147417979 to ptr), i32 -256 acquire, align 4
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
  %54 = atomicrmw add ptr inttoptr (i32 -2147417983 to ptr), i32 256 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_14(i32 %0, ptr %1, ptr %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
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
define void @__impl_riscv_kernel_13(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, ptr %5, ptr %6, i32 %7, i32 %8, i32 %9, i32 %10, i32 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 -2147417766 to ptr), align 4
  %22 = icmp uge i32 %21, 64
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 -2147417766 to ptr), i32 -64 acquire, align 4
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
  %47 = atomicrmw add ptr inttoptr (i32 -2147417762 to ptr), i32 64 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_12(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417991 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417991 to ptr), i32 -1024 acquire, align 4
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
  %51 = atomicrmw add ptr inttoptr (i32 -2147417987 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_11(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418007 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418007 to ptr), i32 -1024 acquire, align 4
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
  %51 = atomicrmw add ptr inttoptr (i32 -2147418003 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_10(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418023 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418023 to ptr), i32 -1024 acquire, align 4
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
  %51 = atomicrmw add ptr inttoptr (i32 -2147418019 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_9(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418039 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418039 to ptr), i32 -1024 acquire, align 4
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
  %51 = atomicrmw add ptr inttoptr (i32 -2147418035 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_8(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418055 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418055 to ptr), i32 -1024 acquire, align 4
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
  %51 = atomicrmw add ptr inttoptr (i32 -2147418051 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_7(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418071 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418071 to ptr), i32 -1024 acquire, align 4
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
  %51 = atomicrmw add ptr inttoptr (i32 -2147418067 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_6(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418087 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418087 to ptr), i32 -1024 acquire, align 4
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
  %51 = atomicrmw add ptr inttoptr (i32 -2147418083 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_5(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147418103 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147418103 to ptr), i32 -1024 acquire, align 4
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
  %51 = atomicrmw add ptr inttoptr (i32 -2147418099 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_4(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417608 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417608 to ptr), i32 -1024 acquire, align 4
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
  %51 = atomicrmw add ptr inttoptr (i32 -2147417604 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_3(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417656 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417656 to ptr), i32 -1024 acquire, align 4
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
  %51 = atomicrmw add ptr inttoptr (i32 -2147417652 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_2(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417704 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417704 to ptr), i32 -1024 acquire, align 4
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
  %51 = atomicrmw add ptr inttoptr (i32 -2147417700 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_1(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417752 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417752 to ptr), i32 -1024 acquire, align 4
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
  %51 = atomicrmw add ptr inttoptr (i32 -2147417748 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_0(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, ptr %7, ptr %8, i32 %9, i32 %10, i32 %11, i32 %12, i32 %13, ptr %14, ptr %15, i32 %16, i32 %17, i32 %18, i32 %19, i32 %20) #0 {
  br label %22

22:                                               ; preds = %22, %21
  %23 = load volatile i32, ptr inttoptr (i32 -2147417800 to ptr), align 4
  %24 = icmp uge i32 %23, 1024
  br i1 %24, label %25, label %22

25:                                               ; preds = %22
  %26 = atomicrmw add ptr inttoptr (i32 -2147417800 to ptr), i32 -1024 acquire, align 4
  br label %27

27:                                               ; preds = %27, %25
  %28 = load volatile i32, ptr inttoptr (i32 -2147417848 to ptr), align 4
  %29 = icmp uge i32 %28, 1024
  br i1 %29, label %30, label %27

30:                                               ; preds = %27
  %31 = atomicrmw add ptr inttoptr (i32 -2147417848 to ptr), i32 -1024 acquire, align 4
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
  %56 = atomicrmw add ptr inttoptr (i32 -2147417796 to ptr), i32 1024 release, align 4
  %57 = atomicrmw add ptr inttoptr (i32 -2147417844 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_0() #0 {
  call void @__impl_riscv_kernel_0(ptr inttoptr (i32 -2147478784 to ptr), ptr inttoptr (i32 -2147478784 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147480064 to ptr), ptr inttoptr (i32 -2147480064 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147462144 to ptr), ptr inttoptr (i32 -2147462144 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_1() #0 {
  call void @__impl_riscv_kernel_1(ptr inttoptr (i32 -2147477504 to ptr), ptr inttoptr (i32 -2147477504 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147462144 to ptr), ptr inttoptr (i32 -2147462144 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147461120 to ptr), ptr inttoptr (i32 -2147461120 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_2() #0 {
  call void @__impl_riscv_kernel_2(ptr inttoptr (i32 -2147476224 to ptr), ptr inttoptr (i32 -2147476224 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147461120 to ptr), ptr inttoptr (i32 -2147461120 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147460096 to ptr), ptr inttoptr (i32 -2147460096 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_3() #0 {
  call void @__impl_riscv_kernel_3(ptr inttoptr (i32 -2147474944 to ptr), ptr inttoptr (i32 -2147474944 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147460096 to ptr), ptr inttoptr (i32 -2147460096 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147459072 to ptr), ptr inttoptr (i32 -2147459072 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_4() #0 {
  call void @__impl_riscv_kernel_4(ptr inttoptr (i32 -2147473664 to ptr), ptr inttoptr (i32 -2147473664 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147459072 to ptr), ptr inttoptr (i32 -2147459072 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147458048 to ptr), ptr inttoptr (i32 -2147458048 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_5() #0 {
  call void @__impl_riscv_kernel_5(ptr inttoptr (i32 -2147472384 to ptr), ptr inttoptr (i32 -2147472384 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147458048 to ptr), ptr inttoptr (i32 -2147458048 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147457024 to ptr), ptr inttoptr (i32 -2147457024 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_6() #0 {
  call void @__impl_riscv_kernel_6(ptr inttoptr (i32 -2147471104 to ptr), ptr inttoptr (i32 -2147471104 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147457024 to ptr), ptr inttoptr (i32 -2147457024 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147456000 to ptr), ptr inttoptr (i32 -2147456000 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_7() #0 {
  call void @__impl_riscv_kernel_7(ptr inttoptr (i32 -2147469824 to ptr), ptr inttoptr (i32 -2147469824 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147456000 to ptr), ptr inttoptr (i32 -2147456000 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147454976 to ptr), ptr inttoptr (i32 -2147454976 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_8() #0 {
  call void @__impl_riscv_kernel_8(ptr inttoptr (i32 -2147468544 to ptr), ptr inttoptr (i32 -2147468544 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147454976 to ptr), ptr inttoptr (i32 -2147454976 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147453952 to ptr), ptr inttoptr (i32 -2147453952 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_9() #0 {
  call void @__impl_riscv_kernel_9(ptr inttoptr (i32 -2147467264 to ptr), ptr inttoptr (i32 -2147467264 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147453952 to ptr), ptr inttoptr (i32 -2147453952 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147452928 to ptr), ptr inttoptr (i32 -2147452928 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_10() #0 {
  call void @__impl_riscv_kernel_10(ptr inttoptr (i32 -2147465984 to ptr), ptr inttoptr (i32 -2147465984 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147452928 to ptr), ptr inttoptr (i32 -2147452928 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147451904 to ptr), ptr inttoptr (i32 -2147451904 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_11() #0 {
  call void @__impl_riscv_kernel_11(ptr inttoptr (i32 -2147464704 to ptr), ptr inttoptr (i32 -2147464704 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147451904 to ptr), ptr inttoptr (i32 -2147451904 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147450880 to ptr), ptr inttoptr (i32 -2147450880 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_12() #0 {
  call void @__impl_riscv_kernel_12(ptr inttoptr (i32 -2147463424 to ptr), ptr inttoptr (i32 -2147463424 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147450880 to ptr), ptr inttoptr (i32 -2147450880 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147449856 to ptr), ptr inttoptr (i32 -2147449856 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_13() #0 {
  call void @__impl_riscv_kernel_13(ptr inttoptr (i32 -2147447808 to ptr), ptr inttoptr (i32 -2147447808 to ptr), i32 0, i32 16, i32 1, ptr inttoptr (i32 -2147449856 to ptr), ptr inttoptr (i32 -2147449856 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147448832 to ptr), ptr inttoptr (i32 -2147448832 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_14() #0 {
  call void @__impl_riscv_kernel_14(i32 0, ptr inttoptr (i32 -2147448832 to ptr), ptr inttoptr (i32 -2147448832 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 -2147447744 to ptr), ptr inttoptr (i32 -2147447744 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_15() #0 {
  call void @__impl_riscv_kernel_15(ptr inttoptr (i32 -2147446720 to ptr), ptr inttoptr (i32 -2147446720 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 -2147447744 to ptr), ptr inttoptr (i32 -2147447744 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
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
  ret void
}

attributes #0 = { null_pointer_is_valid }

!llvm.module.flags = !{!0}

!0 = !{i32 2, !"Debug Info Version", i32 3}
