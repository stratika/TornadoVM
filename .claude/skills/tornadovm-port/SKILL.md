---
name: tornadovm-port
description: Port a plain Java method to a TornadoVM kernel (Loop Parallel with @Parallel/@Reduce, Kernel API with KernelContext, or Tile API with TileContext), then prove the port is right and measure it — compare its output with the original method on two data sets, check that it really ran on the device, and time both. Use when asked to GPU-accelerate, parallelise, offload or port Java code to TornadoVM, to turn a loop into a TornadoVM task, or to check whether a TornadoVM kernel is correct or faster. For building TornadoVM itself, see the `tornadovm` skill.
---

# Porting a Java method to TornadoVM

TornadoVM JIT-compiles a Java method to OpenCL C, CUDA or Metal and runs it on a GPU. Only a
subset of Java compiles, and TornadoVM does **not** check that parallel iterations are
independent: a wrong port compiles, runs, and returns wrong numbers. So a port is not done when
it compiles. It is done when `scripts/verify.sh` says the output matches the original and shows
the timings.

## Workflow

1. **Read the method and decide if it can be parallelised.** If every loop carries a value from
   one iteration to the next (`y[i] = a * x[i] + y[i - 1]`) and is not a reduction (sum, product,
   min, max), stop and tell the user why. Do not write a kernel.
2. **Find the device.** Run `tornado --devices`. Note the backend:
   - **Metal** (Apple GPUs) has no `double`. Kernels must use `float`, and `TornadoMath` for
     `log`, `exp`, `sqrt` and so on (`Math.*` returns `double`).
   - **Tile API** runs only on NVIDIA GPUs with the CUDA backend.
3. **Choose the API.** Ask the user if they have a preference; otherwise:
   - **Loop Parallel** (default): the method keeps its loops, independent loops get `@Parallel`.
     Easiest to read, and usually as fast as the others.
   - **Kernel API**: one thread per grid point, with `KernelContext`. Use it when the kernel needs
     local memory, barriers or atomics, or when Loop Parallel is slow.
   - **Tile API**: tiles of elements with `TileContext`. NVIDIA and CUDA only.
   - If the user wants the fastest, write both Loop Parallel and Kernel API (and Tile API on
     CUDA), verify each, and recommend the fastest that matches.
4. **Read the rules.** Always read [references/rules.md](references/rules.md), then the file for
   the chosen API: [loop-parallel.md](references/loop-parallel.md),
   [kernel-api.md](references/kernel-api.md) or [tile-api.md](references/tile-api.md).
5. **Write the kernel** as a new `public static void <name>Kernel(...)` method beside the
   original, in the same class. Do not change the original method yet: the check needs it.
6. **Verify it.** Copy [scripts/CheckTemplate.java](scripts/CheckTemplate.java) to
   `Check<Name>.java` next to it, fill in the marked parts for this method, and run:
   ```bash
   scripts/verify.sh -cp <the project's compiled classes> Check<Name>.java
   ```
   See "Reading the result" below. If the result is not `PASS`, read the reason, fix the kernel
   and run again. After 3 failed attempts, stop and show the user the last error.
7. **Report and ask.** Show the user the kernel, the verify summary (device, both data sets,
   timings, speed-up) and the call-site code below. Ask whether to replace the original method
   with the kernel or keep both. Delete the check class unless the user wants to keep it.

Never say a kernel is faster unless `verify.sh` measured it. On small inputs or cheap loops
(for example SAXPY) the GPU is often **slower** than Java, because the data has to be copied to
the device and back. Say so plainly: it is a useful result.

## Reading the result

`verify.sh` runs the check three times: on 4096 elements, on 4097 elements with negative values
and zeros (to catch a missing bounds check or sign error), and on a large size for timing. It
ends with one of:

| Result | Meaning | What to do |
|---|---|---|
| `PASS` | Both data sets match; timings follow | Report |
| `FAIL different` | Some outputs differ; the line before names the first one | Look for a dependency between iterations, a missing bounds check, or a wrong reduction identity |
| `FAIL not-on-device` | TornadoVM could not compile the kernel and ran it as Java (a *bailout*) | Rerun `verify.sh` with `--debug`, look for the bailout reason, and remove the construct the rules forbid |
| `FAIL kernel-failed` | The kernel threw; the exception follows | Often `TornadoDeviceFP64NotSupported` (use `float` and `TornadoMath`) or an unsupported type |
| `FAIL original-failed` | The original method threw on the test data | Fix the check's inputs, not the kernel |

Values match when they are within `1e-3` relative error; GPU floating point differs from Java
in the last bits. A kernel counts as faster when it is at least 1.2× faster than Java, measured
as the median of 10 runs after 3 warm-up runs, including the copies to and from the device.

## Calling the kernel

The caller builds a `TaskGraph` once and runs it with an execution plan. Read-only inputs that
do not change between runs can use `DataTransferMode.FIRST_EXECUTION`:

```java
TaskGraph graph = new TaskGraph("s0")
        .transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
        .task("t0", Options::blackScholesKernel, input, call, put)
        .transferToHost(DataTransferMode.EVERY_EXECUTION, call, put);
try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
    plan.execute();
}
```

A Kernel API or Tile API kernel also needs a grid, and gets a `new KernelContext()` or
`new TileContext()` as its first argument; see its reference file.

Run the program with the `tornado` launcher instead of `java`
(`tornado -cp <classes> <MainClass>`), or with the JVM flags that `tornado --printJavaFlags`
prints.
