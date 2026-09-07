; ModuleID = 'LLVMDialectModule'
source_filename = "LLVMDialectModule"

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_23(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i64 %7, i64 %8, i64 %9, i64 %10, i64 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 33492 to ptr), align 4
  %22 = icmp uge i32 %21, 256
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 33492 to ptr), i32 -256 acquire, align 4
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
  %54 = atomicrmw add ptr inttoptr (i32 33488 to ptr), i32 256 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_22(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %16, %15
  %17 = load volatile i32, ptr inttoptr (i32 33480 to ptr), align 4
  %18 = icmp uge i32 %17, 1024
  br i1 %18, label %19, label %16

19:                                               ; preds = %16
  %20 = atomicrmw add ptr inttoptr (i32 33480 to ptr), i32 -1024 acquire, align 4
  br label %21

21:                                               ; preds = %39, %19
  %22 = phi i32 [ 0, %19 ], [ %40, %39 ]
  %23 = icmp slt i32 %22, 16
  br i1 %23, label %24, label %41

24:                                               ; preds = %21
  br label %25

25:                                               ; preds = %28, %24
  %26 = phi i32 [ 0, %24 ], [ %38, %28 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %39

28:                                               ; preds = %25
  %29 = mul nuw nsw i32 %22, 16
  %30 = add nuw nsw i32 %29, %26
  %31 = getelementptr inbounds nuw i32, ptr %1, i32 %30
  %32 = load i32, ptr %31, align 4
  %33 = icmp sgt i32 %32, %7
  %34 = select i1 %33, i32 %32, i32 %7
  %35 = mul nuw nsw i32 %22, 16
  %36 = add nuw nsw i32 %35, %26
  %37 = getelementptr inbounds nuw i32, ptr %9, i32 %36
  store i32 %34, ptr %37, align 4
  %38 = add i32 %26, 1
  br label %25

39:                                               ; preds = %25
  %40 = add i32 %22, 1
  br label %21

41:                                               ; preds = %21
  %42 = atomicrmw add ptr inttoptr (i32 33484 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_21(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i64 %7, i64 %8, i64 %9, i64 %10, i64 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 33428 to ptr), align 4
  %22 = icmp uge i32 %21, 256
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 33428 to ptr), i32 -256 acquire, align 4
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
  %54 = atomicrmw add ptr inttoptr (i32 33424 to ptr), i32 256 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_20(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %16, %15
  %17 = load volatile i32, ptr inttoptr (i32 33416 to ptr), align 4
  %18 = icmp uge i32 %17, 1024
  br i1 %18, label %19, label %16

19:                                               ; preds = %16
  %20 = atomicrmw add ptr inttoptr (i32 33416 to ptr), i32 -1024 acquire, align 4
  br label %21

21:                                               ; preds = %39, %19
  %22 = phi i32 [ 0, %19 ], [ %40, %39 ]
  %23 = icmp slt i32 %22, 16
  br i1 %23, label %24, label %41

24:                                               ; preds = %21
  br label %25

25:                                               ; preds = %28, %24
  %26 = phi i32 [ 0, %24 ], [ %38, %28 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %39

28:                                               ; preds = %25
  %29 = mul nuw nsw i32 %22, 16
  %30 = add nuw nsw i32 %29, %26
  %31 = getelementptr inbounds nuw i32, ptr %1, i32 %30
  %32 = load i32, ptr %31, align 4
  %33 = icmp sgt i32 %32, %7
  %34 = select i1 %33, i32 %32, i32 %7
  %35 = mul nuw nsw i32 %22, 16
  %36 = add nuw nsw i32 %35, %26
  %37 = getelementptr inbounds nuw i32, ptr %9, i32 %36
  store i32 %34, ptr %37, align 4
  %38 = add i32 %26, 1
  br label %25

39:                                               ; preds = %25
  %40 = add i32 %22, 1
  br label %21

41:                                               ; preds = %21
  %42 = atomicrmw add ptr inttoptr (i32 33420 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_19(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i64 %7, i64 %8, i64 %9, i64 %10, i64 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 33364 to ptr), align 4
  %22 = icmp uge i32 %21, 256
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 33364 to ptr), i32 -256 acquire, align 4
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
  %54 = atomicrmw add ptr inttoptr (i32 33360 to ptr), i32 256 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_18(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %16, %15
  %17 = load volatile i32, ptr inttoptr (i32 33352 to ptr), align 4
  %18 = icmp uge i32 %17, 1024
  br i1 %18, label %19, label %16

19:                                               ; preds = %16
  %20 = atomicrmw add ptr inttoptr (i32 33352 to ptr), i32 -1024 acquire, align 4
  br label %21

21:                                               ; preds = %39, %19
  %22 = phi i32 [ 0, %19 ], [ %40, %39 ]
  %23 = icmp slt i32 %22, 16
  br i1 %23, label %24, label %41

24:                                               ; preds = %21
  br label %25

25:                                               ; preds = %28, %24
  %26 = phi i32 [ 0, %24 ], [ %38, %28 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %39

28:                                               ; preds = %25
  %29 = mul nuw nsw i32 %22, 16
  %30 = add nuw nsw i32 %29, %26
  %31 = getelementptr inbounds nuw i32, ptr %1, i32 %30
  %32 = load i32, ptr %31, align 4
  %33 = icmp sgt i32 %32, %7
  %34 = select i1 %33, i32 %32, i32 %7
  %35 = mul nuw nsw i32 %22, 16
  %36 = add nuw nsw i32 %35, %26
  %37 = getelementptr inbounds nuw i32, ptr %9, i32 %36
  store i32 %34, ptr %37, align 4
  %38 = add i32 %26, 1
  br label %25

39:                                               ; preds = %25
  %40 = add i32 %22, 1
  br label %21

41:                                               ; preds = %21
  %42 = atomicrmw add ptr inttoptr (i32 33356 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_17(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i64 %7, i64 %8, i64 %9, i64 %10, i64 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 33300 to ptr), align 4
  %22 = icmp uge i32 %21, 256
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 33300 to ptr), i32 -256 acquire, align 4
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
  %54 = atomicrmw add ptr inttoptr (i32 33296 to ptr), i32 256 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_16(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %16, %15
  %17 = load volatile i32, ptr inttoptr (i32 33288 to ptr), align 4
  %18 = icmp uge i32 %17, 1024
  br i1 %18, label %19, label %16

19:                                               ; preds = %16
  %20 = atomicrmw add ptr inttoptr (i32 33288 to ptr), i32 -1024 acquire, align 4
  br label %21

21:                                               ; preds = %39, %19
  %22 = phi i32 [ 0, %19 ], [ %40, %39 ]
  %23 = icmp slt i32 %22, 16
  br i1 %23, label %24, label %41

24:                                               ; preds = %21
  br label %25

25:                                               ; preds = %28, %24
  %26 = phi i32 [ 0, %24 ], [ %38, %28 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %39

28:                                               ; preds = %25
  %29 = mul nuw nsw i32 %22, 16
  %30 = add nuw nsw i32 %29, %26
  %31 = getelementptr inbounds nuw i32, ptr %1, i32 %30
  %32 = load i32, ptr %31, align 4
  %33 = icmp sgt i32 %32, %7
  %34 = select i1 %33, i32 %32, i32 %7
  %35 = mul nuw nsw i32 %22, 16
  %36 = add nuw nsw i32 %35, %26
  %37 = getelementptr inbounds nuw i32, ptr %9, i32 %36
  store i32 %34, ptr %37, align 4
  %38 = add i32 %26, 1
  br label %25

39:                                               ; preds = %25
  %40 = add i32 %22, 1
  br label %21

41:                                               ; preds = %21
  %42 = atomicrmw add ptr inttoptr (i32 33292 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_15(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i64 %7, i64 %8, i64 %9, i64 %10, i64 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 33236 to ptr), align 4
  %22 = icmp uge i32 %21, 256
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 33236 to ptr), i32 -256 acquire, align 4
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
  %54 = atomicrmw add ptr inttoptr (i32 33232 to ptr), i32 256 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_14(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %16, %15
  %17 = load volatile i32, ptr inttoptr (i32 33224 to ptr), align 4
  %18 = icmp uge i32 %17, 1024
  br i1 %18, label %19, label %16

19:                                               ; preds = %16
  %20 = atomicrmw add ptr inttoptr (i32 33224 to ptr), i32 -1024 acquire, align 4
  br label %21

21:                                               ; preds = %39, %19
  %22 = phi i32 [ 0, %19 ], [ %40, %39 ]
  %23 = icmp slt i32 %22, 16
  br i1 %23, label %24, label %41

24:                                               ; preds = %21
  br label %25

25:                                               ; preds = %28, %24
  %26 = phi i32 [ 0, %24 ], [ %38, %28 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %39

28:                                               ; preds = %25
  %29 = mul nuw nsw i32 %22, 16
  %30 = add nuw nsw i32 %29, %26
  %31 = getelementptr inbounds nuw i32, ptr %1, i32 %30
  %32 = load i32, ptr %31, align 4
  %33 = icmp sgt i32 %32, %7
  %34 = select i1 %33, i32 %32, i32 %7
  %35 = mul nuw nsw i32 %22, 16
  %36 = add nuw nsw i32 %35, %26
  %37 = getelementptr inbounds nuw i32, ptr %9, i32 %36
  store i32 %34, ptr %37, align 4
  %38 = add i32 %26, 1
  br label %25

39:                                               ; preds = %25
  %40 = add i32 %22, 1
  br label %21

41:                                               ; preds = %21
  %42 = atomicrmw add ptr inttoptr (i32 33228 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_13(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i64 %7, i64 %8, i64 %9, i64 %10, i64 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 33172 to ptr), align 4
  %22 = icmp uge i32 %21, 256
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 33172 to ptr), i32 -256 acquire, align 4
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
  %54 = atomicrmw add ptr inttoptr (i32 33168 to ptr), i32 256 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_12(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %16, %15
  %17 = load volatile i32, ptr inttoptr (i32 33160 to ptr), align 4
  %18 = icmp uge i32 %17, 1024
  br i1 %18, label %19, label %16

19:                                               ; preds = %16
  %20 = atomicrmw add ptr inttoptr (i32 33160 to ptr), i32 -1024 acquire, align 4
  br label %21

21:                                               ; preds = %39, %19
  %22 = phi i32 [ 0, %19 ], [ %40, %39 ]
  %23 = icmp slt i32 %22, 16
  br i1 %23, label %24, label %41

24:                                               ; preds = %21
  br label %25

25:                                               ; preds = %28, %24
  %26 = phi i32 [ 0, %24 ], [ %38, %28 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %39

28:                                               ; preds = %25
  %29 = mul nuw nsw i32 %22, 16
  %30 = add nuw nsw i32 %29, %26
  %31 = getelementptr inbounds nuw i32, ptr %1, i32 %30
  %32 = load i32, ptr %31, align 4
  %33 = icmp sgt i32 %32, %7
  %34 = select i1 %33, i32 %32, i32 %7
  %35 = mul nuw nsw i32 %22, 16
  %36 = add nuw nsw i32 %35, %26
  %37 = getelementptr inbounds nuw i32, ptr %9, i32 %36
  store i32 %34, ptr %37, align 4
  %38 = add i32 %26, 1
  br label %25

39:                                               ; preds = %25
  %40 = add i32 %22, 1
  br label %21

41:                                               ; preds = %21
  %42 = atomicrmw add ptr inttoptr (i32 33164 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_11(i64 %0, i64 %1, i64 %2, i64 %3, i64 %4, ptr %5, ptr %6, i32 %7, i32 %8, i32 %9, i32 %10, i32 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %46, %19
  %21 = phi i32 [ 0, %19 ], [ %47, %46 ]
  %22 = icmp slt i32 %21, 16
  br i1 %22, label %23, label %48

23:                                               ; preds = %20
  br label %24

24:                                               ; preds = %27, %23
  %25 = phi i32 [ 0, %23 ], [ %45, %27 ]
  %26 = icmp slt i32 %25, 16
  br i1 %26, label %27, label %46

27:                                               ; preds = %24
  %28 = mul nuw nsw i32 %21, 16
  %29 = add nuw nsw i32 %28, %25
  %30 = getelementptr inbounds nuw i32, ptr %6, i32 %29
  %31 = load i32, ptr %30, align 4
  %32 = sext i32 %31 to i64
  %33 = mul i64 %32, %0
  %34 = add i64 %33, %1
  %35 = ashr i64 %34, %2
  %36 = add i64 %35, %3
  %37 = icmp sgt i64 %36, %3
  %38 = select i1 %37, i64 %36, i64 %3
  %39 = icmp slt i64 %38, %4
  %40 = select i1 %39, i64 %38, i64 %4
  %41 = trunc i64 %40 to i8
  %42 = mul nuw nsw i32 %21, 16
  %43 = add nuw nsw i32 %42, %25
  %44 = getelementptr inbounds nuw i8, ptr %13, i32 %43
  store i8 %41, ptr %44, align 1
  %45 = add i32 %25, 1
  br label %24

46:                                               ; preds = %24
  %47 = add i32 %21, 1
  br label %20

48:                                               ; preds = %20
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_10(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %16, %15
  %17 = load volatile i32, ptr inttoptr (i32 33144 to ptr), align 4
  %18 = icmp uge i32 %17, 1024
  br i1 %18, label %19, label %16

19:                                               ; preds = %16
  %20 = atomicrmw add ptr inttoptr (i32 33144 to ptr), i32 -1024 acquire, align 4
  br label %21

21:                                               ; preds = %39, %19
  %22 = phi i32 [ 0, %19 ], [ %40, %39 ]
  %23 = icmp slt i32 %22, 16
  br i1 %23, label %24, label %41

24:                                               ; preds = %21
  br label %25

25:                                               ; preds = %28, %24
  %26 = phi i32 [ 0, %24 ], [ %38, %28 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %39

28:                                               ; preds = %25
  %29 = mul nuw nsw i32 %22, 16
  %30 = add nuw nsw i32 %29, %26
  %31 = getelementptr inbounds nuw i32, ptr %1, i32 %30
  %32 = load i32, ptr %31, align 4
  %33 = icmp sgt i32 %32, %7
  %34 = select i1 %33, i32 %32, i32 %7
  %35 = mul nuw nsw i32 %22, 16
  %36 = add nuw nsw i32 %35, %26
  %37 = getelementptr inbounds nuw i32, ptr %9, i32 %36
  store i32 %34, ptr %37, align 4
  %38 = add i32 %26, 1
  br label %25

39:                                               ; preds = %25
  %40 = add i32 %22, 1
  br label %21

41:                                               ; preds = %21
  %42 = atomicrmw add ptr inttoptr (i32 33148 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_9(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i64 %7, i64 %8, i64 %9, i64 %10, i64 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 33092 to ptr), align 4
  %22 = icmp uge i32 %21, 256
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 33092 to ptr), i32 -256 acquire, align 4
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
  %54 = atomicrmw add ptr inttoptr (i32 33088 to ptr), i32 256 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_8(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %16, %15
  %17 = load volatile i32, ptr inttoptr (i32 33080 to ptr), align 4
  %18 = icmp uge i32 %17, 1024
  br i1 %18, label %19, label %16

19:                                               ; preds = %16
  %20 = atomicrmw add ptr inttoptr (i32 33080 to ptr), i32 -1024 acquire, align 4
  br label %21

21:                                               ; preds = %39, %19
  %22 = phi i32 [ 0, %19 ], [ %40, %39 ]
  %23 = icmp slt i32 %22, 16
  br i1 %23, label %24, label %41

24:                                               ; preds = %21
  br label %25

25:                                               ; preds = %28, %24
  %26 = phi i32 [ 0, %24 ], [ %38, %28 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %39

28:                                               ; preds = %25
  %29 = mul nuw nsw i32 %22, 16
  %30 = add nuw nsw i32 %29, %26
  %31 = getelementptr inbounds nuw i32, ptr %1, i32 %30
  %32 = load i32, ptr %31, align 4
  %33 = icmp sgt i32 %32, %7
  %34 = select i1 %33, i32 %32, i32 %7
  %35 = mul nuw nsw i32 %22, 16
  %36 = add nuw nsw i32 %35, %26
  %37 = getelementptr inbounds nuw i32, ptr %9, i32 %36
  store i32 %34, ptr %37, align 4
  %38 = add i32 %26, 1
  br label %25

39:                                               ; preds = %25
  %40 = add i32 %22, 1
  br label %21

41:                                               ; preds = %21
  %42 = atomicrmw add ptr inttoptr (i32 33084 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_7(i64 %0, i64 %1, i64 %2, i64 %3, i64 %4, ptr %5, ptr %6, i32 %7, i32 %8, i32 %9, i32 %10, i32 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %46, %19
  %21 = phi i32 [ 0, %19 ], [ %47, %46 ]
  %22 = icmp slt i32 %21, 16
  br i1 %22, label %23, label %48

23:                                               ; preds = %20
  br label %24

24:                                               ; preds = %27, %23
  %25 = phi i32 [ 0, %23 ], [ %45, %27 ]
  %26 = icmp slt i32 %25, 16
  br i1 %26, label %27, label %46

27:                                               ; preds = %24
  %28 = mul nuw nsw i32 %21, 16
  %29 = add nuw nsw i32 %28, %25
  %30 = getelementptr inbounds nuw i32, ptr %6, i32 %29
  %31 = load i32, ptr %30, align 4
  %32 = sext i32 %31 to i64
  %33 = mul i64 %32, %0
  %34 = add i64 %33, %1
  %35 = ashr i64 %34, %2
  %36 = add i64 %35, %3
  %37 = icmp sgt i64 %36, %3
  %38 = select i1 %37, i64 %36, i64 %3
  %39 = icmp slt i64 %38, %4
  %40 = select i1 %39, i64 %38, i64 %4
  %41 = trunc i64 %40 to i8
  %42 = mul nuw nsw i32 %21, 16
  %43 = add nuw nsw i32 %42, %25
  %44 = getelementptr inbounds nuw i8, ptr %13, i32 %43
  store i8 %41, ptr %44, align 1
  %45 = add i32 %25, 1
  br label %24

46:                                               ; preds = %24
  %47 = add i32 %21, 1
  br label %20

48:                                               ; preds = %20
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_6(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %16, %15
  %17 = load volatile i32, ptr inttoptr (i32 33032 to ptr), align 4
  %18 = icmp uge i32 %17, 1024
  br i1 %18, label %19, label %16

19:                                               ; preds = %16
  %20 = atomicrmw add ptr inttoptr (i32 33032 to ptr), i32 -1024 acquire, align 4
  br label %21

21:                                               ; preds = %39, %19
  %22 = phi i32 [ 0, %19 ], [ %40, %39 ]
  %23 = icmp slt i32 %22, 16
  br i1 %23, label %24, label %41

24:                                               ; preds = %21
  br label %25

25:                                               ; preds = %28, %24
  %26 = phi i32 [ 0, %24 ], [ %38, %28 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %39

28:                                               ; preds = %25
  %29 = mul nuw nsw i32 %22, 16
  %30 = add nuw nsw i32 %29, %26
  %31 = getelementptr inbounds nuw i32, ptr %1, i32 %30
  %32 = load i32, ptr %31, align 4
  %33 = icmp sgt i32 %32, %7
  %34 = select i1 %33, i32 %32, i32 %7
  %35 = mul nuw nsw i32 %22, 16
  %36 = add nuw nsw i32 %35, %26
  %37 = getelementptr inbounds nuw i32, ptr %9, i32 %36
  store i32 %34, ptr %37, align 4
  %38 = add i32 %26, 1
  br label %25

39:                                               ; preds = %25
  %40 = add i32 %22, 1
  br label %21

41:                                               ; preds = %21
  %42 = atomicrmw add ptr inttoptr (i32 33036 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_5(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i64 %7, i64 %8, i64 %9, i64 %10, i64 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 32980 to ptr), align 4
  %22 = icmp uge i32 %21, 256
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 32980 to ptr), i32 -256 acquire, align 4
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
  %54 = atomicrmw add ptr inttoptr (i32 32976 to ptr), i32 256 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_4(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %16, %15
  %17 = load volatile i32, ptr inttoptr (i32 32968 to ptr), align 4
  %18 = icmp uge i32 %17, 1024
  br i1 %18, label %19, label %16

19:                                               ; preds = %16
  %20 = atomicrmw add ptr inttoptr (i32 32968 to ptr), i32 -1024 acquire, align 4
  br label %21

21:                                               ; preds = %39, %19
  %22 = phi i32 [ 0, %19 ], [ %40, %39 ]
  %23 = icmp slt i32 %22, 16
  br i1 %23, label %24, label %41

24:                                               ; preds = %21
  br label %25

25:                                               ; preds = %28, %24
  %26 = phi i32 [ 0, %24 ], [ %38, %28 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %39

28:                                               ; preds = %25
  %29 = mul nuw nsw i32 %22, 16
  %30 = add nuw nsw i32 %29, %26
  %31 = getelementptr inbounds nuw i32, ptr %1, i32 %30
  %32 = load i32, ptr %31, align 4
  %33 = icmp sgt i32 %32, %7
  %34 = select i1 %33, i32 %32, i32 %7
  %35 = mul nuw nsw i32 %22, 16
  %36 = add nuw nsw i32 %35, %26
  %37 = getelementptr inbounds nuw i32, ptr %9, i32 %36
  store i32 %34, ptr %37, align 4
  %38 = add i32 %26, 1
  br label %25

39:                                               ; preds = %25
  %40 = add i32 %22, 1
  br label %21

41:                                               ; preds = %21
  %42 = atomicrmw add ptr inttoptr (i32 32972 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_3(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i64 %7, i64 %8, i64 %9, i64 %10, i64 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 32900 to ptr), align 4
  %22 = icmp uge i32 %21, 256
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 32900 to ptr), i32 -256 acquire, align 4
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
  %54 = atomicrmw add ptr inttoptr (i32 32896 to ptr), i32 256 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_2(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %16, %15
  %17 = load volatile i32, ptr inttoptr (i32 32888 to ptr), align 4
  %18 = icmp uge i32 %17, 1024
  br i1 %18, label %19, label %16

19:                                               ; preds = %16
  %20 = atomicrmw add ptr inttoptr (i32 32888 to ptr), i32 -1024 acquire, align 4
  br label %21

21:                                               ; preds = %39, %19
  %22 = phi i32 [ 0, %19 ], [ %40, %39 ]
  %23 = icmp slt i32 %22, 16
  br i1 %23, label %24, label %41

24:                                               ; preds = %21
  br label %25

25:                                               ; preds = %28, %24
  %26 = phi i32 [ 0, %24 ], [ %38, %28 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %39

28:                                               ; preds = %25
  %29 = mul nuw nsw i32 %22, 16
  %30 = add nuw nsw i32 %29, %26
  %31 = getelementptr inbounds nuw i32, ptr %1, i32 %30
  %32 = load i32, ptr %31, align 4
  %33 = icmp sgt i32 %32, %7
  %34 = select i1 %33, i32 %32, i32 %7
  %35 = mul nuw nsw i32 %22, 16
  %36 = add nuw nsw i32 %35, %26
  %37 = getelementptr inbounds nuw i32, ptr %9, i32 %36
  store i32 %34, ptr %37, align 4
  %38 = add i32 %26, 1
  br label %25

39:                                               ; preds = %25
  %40 = add i32 %22, 1
  br label %21

41:                                               ; preds = %21
  %42 = atomicrmw add ptr inttoptr (i32 32892 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_1(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i64 %7, i64 %8, i64 %9, i64 %10, i64 %11, ptr %12, ptr %13, i32 %14, i32 %15, i32 %16, i32 %17, i32 %18) #0 {
  br label %20

20:                                               ; preds = %20, %19
  %21 = load volatile i32, ptr inttoptr (i32 32836 to ptr), align 4
  %22 = icmp uge i32 %21, 256
  br i1 %22, label %23, label %20

23:                                               ; preds = %20
  %24 = atomicrmw add ptr inttoptr (i32 32836 to ptr), i32 -256 acquire, align 4
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
  %54 = atomicrmw add ptr inttoptr (i32 32832 to ptr), i32 256 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @__impl_riscv_kernel_0(ptr %0, ptr %1, i32 %2, i32 %3, i32 %4, i32 %5, i32 %6, i32 %7, ptr %8, ptr %9, i32 %10, i32 %11, i32 %12, i32 %13, i32 %14) #0 {
  br label %16

16:                                               ; preds = %16, %15
  %17 = load volatile i32, ptr inttoptr (i32 32824 to ptr), align 4
  %18 = icmp uge i32 %17, 1024
  br i1 %18, label %19, label %16

19:                                               ; preds = %16
  %20 = atomicrmw add ptr inttoptr (i32 32824 to ptr), i32 -1024 acquire, align 4
  br label %21

21:                                               ; preds = %39, %19
  %22 = phi i32 [ 0, %19 ], [ %40, %39 ]
  %23 = icmp slt i32 %22, 16
  br i1 %23, label %24, label %41

24:                                               ; preds = %21
  br label %25

25:                                               ; preds = %28, %24
  %26 = phi i32 [ 0, %24 ], [ %38, %28 ]
  %27 = icmp slt i32 %26, 16
  br i1 %27, label %28, label %39

28:                                               ; preds = %25
  %29 = mul nuw nsw i32 %22, 16
  %30 = add nuw nsw i32 %29, %26
  %31 = getelementptr inbounds nuw i32, ptr %1, i32 %30
  %32 = load i32, ptr %31, align 4
  %33 = icmp sgt i32 %32, %7
  %34 = select i1 %33, i32 %32, i32 %7
  %35 = mul nuw nsw i32 %22, 16
  %36 = add nuw nsw i32 %35, %26
  %37 = getelementptr inbounds nuw i32, ptr %9, i32 %36
  store i32 %34, ptr %37, align 4
  %38 = add i32 %26, 1
  br label %25

39:                                               ; preds = %25
  %40 = add i32 %22, 1
  br label %21

41:                                               ; preds = %21
  %42 = atomicrmw add ptr inttoptr (i32 32828 to ptr), i32 1024 release, align 4
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_0() #0 {
  call void @__impl_riscv_kernel_0(ptr inttoptr (i32 512 to ptr), ptr inttoptr (i32 512 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i32 0, ptr inttoptr (i32 1792 to ptr), ptr inttoptr (i32 1792 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_1() #0 {
  call void @__impl_riscv_kernel_1(ptr inttoptr (i32 2816 to ptr), ptr inttoptr (i32 2816 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 1792 to ptr), ptr inttoptr (i32 1792 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_2() #0 {
  call void @__impl_riscv_kernel_2(ptr inttoptr (i32 3072 to ptr), ptr inttoptr (i32 3072 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i32 0, ptr inttoptr (i32 4352 to ptr), ptr inttoptr (i32 4352 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_3() #0 {
  call void @__impl_riscv_kernel_3(ptr inttoptr (i32 5376 to ptr), ptr inttoptr (i32 5376 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 4352 to ptr), ptr inttoptr (i32 4352 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_4() #0 {
  call void @__impl_riscv_kernel_4(ptr inttoptr (i32 5632 to ptr), ptr inttoptr (i32 5632 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i32 0, ptr inttoptr (i32 6912 to ptr), ptr inttoptr (i32 6912 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_5() #0 {
  call void @__impl_riscv_kernel_5(ptr inttoptr (i32 7936 to ptr), ptr inttoptr (i32 7936 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 6912 to ptr), ptr inttoptr (i32 6912 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_6() #0 {
  call void @__impl_riscv_kernel_6(ptr inttoptr (i32 8192 to ptr), ptr inttoptr (i32 8192 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i32 0, ptr inttoptr (i32 9472 to ptr), ptr inttoptr (i32 9472 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_7() #0 {
  call void @__impl_riscv_kernel_7(i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 9472 to ptr), ptr inttoptr (i32 9472 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 10496 to ptr), ptr inttoptr (i32 10496 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_8() #0 {
  call void @__impl_riscv_kernel_8(ptr inttoptr (i32 10752 to ptr), ptr inttoptr (i32 10752 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i32 0, ptr inttoptr (i32 12032 to ptr), ptr inttoptr (i32 12032 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_9() #0 {
  call void @__impl_riscv_kernel_9(ptr inttoptr (i32 13056 to ptr), ptr inttoptr (i32 13056 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 12032 to ptr), ptr inttoptr (i32 12032 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_10() #0 {
  call void @__impl_riscv_kernel_10(ptr inttoptr (i32 13312 to ptr), ptr inttoptr (i32 13312 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i32 0, ptr inttoptr (i32 14592 to ptr), ptr inttoptr (i32 14592 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_11() #0 {
  call void @__impl_riscv_kernel_11(i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 14592 to ptr), ptr inttoptr (i32 14592 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, ptr inttoptr (i32 15616 to ptr), ptr inttoptr (i32 15616 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_12() #0 {
  call void @__impl_riscv_kernel_12(ptr inttoptr (i32 15872 to ptr), ptr inttoptr (i32 15872 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i32 0, ptr inttoptr (i32 16896 to ptr), ptr inttoptr (i32 16896 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_13() #0 {
  call void @__impl_riscv_kernel_13(ptr inttoptr (i32 17920 to ptr), ptr inttoptr (i32 17920 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 16896 to ptr), ptr inttoptr (i32 16896 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_14() #0 {
  call void @__impl_riscv_kernel_14(ptr inttoptr (i32 18176 to ptr), ptr inttoptr (i32 18176 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i32 0, ptr inttoptr (i32 19456 to ptr), ptr inttoptr (i32 19456 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_15() #0 {
  call void @__impl_riscv_kernel_15(ptr inttoptr (i32 20480 to ptr), ptr inttoptr (i32 20480 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 19456 to ptr), ptr inttoptr (i32 19456 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_16() #0 {
  call void @__impl_riscv_kernel_16(ptr inttoptr (i32 20736 to ptr), ptr inttoptr (i32 20736 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i32 0, ptr inttoptr (i32 22016 to ptr), ptr inttoptr (i32 22016 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_17() #0 {
  call void @__impl_riscv_kernel_17(ptr inttoptr (i32 23040 to ptr), ptr inttoptr (i32 23040 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 22016 to ptr), ptr inttoptr (i32 22016 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_18() #0 {
  call void @__impl_riscv_kernel_18(ptr inttoptr (i32 23296 to ptr), ptr inttoptr (i32 23296 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i32 0, ptr inttoptr (i32 24576 to ptr), ptr inttoptr (i32 24576 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_19() #0 {
  call void @__impl_riscv_kernel_19(ptr inttoptr (i32 25600 to ptr), ptr inttoptr (i32 25600 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 24576 to ptr), ptr inttoptr (i32 24576 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_20() #0 {
  call void @__impl_riscv_kernel_20(ptr inttoptr (i32 25856 to ptr), ptr inttoptr (i32 25856 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i32 0, ptr inttoptr (i32 27136 to ptr), ptr inttoptr (i32 27136 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_21() #0 {
  call void @__impl_riscv_kernel_21(ptr inttoptr (i32 28160 to ptr), ptr inttoptr (i32 28160 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 27136 to ptr), ptr inttoptr (i32 27136 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_22() #0 {
  call void @__impl_riscv_kernel_22(ptr inttoptr (i32 28416 to ptr), ptr inttoptr (i32 28416 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i32 0, ptr inttoptr (i32 29696 to ptr), ptr inttoptr (i32 29696 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
  ret void
}

; Function Attrs: null_pointer_is_valid
define void @riscv_kernel_23() #0 {
  call void @__impl_riscv_kernel_23(ptr inttoptr (i32 30720 to ptr), ptr inttoptr (i32 30720 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1, i64 1091133696, i64 17179869184, i64 35, i64 -128, i64 127, ptr inttoptr (i32 29696 to ptr), ptr inttoptr (i32 29696 to ptr), i32 0, i32 16, i32 16, i32 16, i32 1)
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
  ret void
}

attributes #0 = { null_pointer_is_valid }

!llvm.module.flags = !{!0}

!0 = !{i32 2, !"Debug Info Version", i32 3}
