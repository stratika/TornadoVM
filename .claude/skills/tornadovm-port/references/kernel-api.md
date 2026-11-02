# Kernel API

<!-- Adapted from TornadoInsight's src/main/resources/ai/target-kernel-api.md. Keep the two in sync. -->

The kernel is written like a GPU kernel: TornadoVM launches one thread per point of a grid, and
each thread computes its own part of the result from its thread index.

## The method
- Add a first parameter `KernelContext context`, before the original parameters. The original
  parameters keep their types, names and order.
- Do not use `@Parallel` or `@Reduce`.
- Write the grid in the kernel's Javadoc, as Java expressions over the parameters, so the caller
  and the check can build it: `Grid: x.length` for one dimension, `Grid: n, n` for two, at most
  three. Add `Local: 256` if the kernel needs a work-group size.
- With a work-group size, the grid is rounded up to a multiple of it, so some threads have no
  element: guard every access, for example `if (i < x.length)`. Give a work-group size when you
  use local memory or barriers.

## Threads
- `context.globalIdx`, `context.globalIdy`, `context.globalIdz`: the thread's index in the grid.
- `context.localIdx` (and `localIdy`, `localIdz`): its index in its work-group;
  `context.groupIdx`: the work-group's index; `context.localGroupSizeX`: the work-group size.
- Replace the loop over the elements by the thread index; do not loop over all elements in
  every thread. A loop that is not parallel (for example the `k` loop of a matrix product) stays
  inside the thread.
- Local memory, shared by the threads of a work-group:
  `float[] partial = context.allocateFloatLocalArray(256);` (also `Int`, `Long`, `Double`), with a
  constant size equal to the work-group size. `context.localBarrier()` waits until every thread
  of the work-group reaches it; every thread of the group must reach the same barriers.
- Atomic update of an element: `context.atomicAdd(array, index, value)` for `int[]`,
  `IntArray`, `LongArray`, `FloatArray` and `DoubleArray`.

## Reductions
A method that returns one value (sum, minimum, maximum) gets a last parameter for the result, a
`FloatArray`, `IntArray`, `LongArray` or `DoubleArray` of length 1 that the caller sets to the
identity (0 for a sum). Each work-group reduces its elements in local memory, and its first
thread adds the group's value with `atomicAdd`.

## Launching it
The task gets a `KernelContext` as its first argument, and the plan gets a grid for the task,
named `"<graph name>.<task name>"`:

```java
KernelContext context = new KernelContext();
TaskGraph graph = new TaskGraph("s0")
        .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
        .task("t0", Vectors::vectorAddKernel, context, a, b, c)
        .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

WorkerGrid1D grid = new WorkerGrid1D(c.length);   // WorkerGrid2D(n, n) for two dimensions
// grid.setLocalWork(256, 1, 1);                  // with "Local: 256"; round the grid up first
GridScheduler scheduler = new GridScheduler("s0.t0", grid);

try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
    plan.withGridScheduler(scheduler).execute();
}
```

`WorkerGrid1D`, `WorkerGrid2D`, `GridScheduler` and `KernelContext` are in
`uk.ac.manchester.tornado.api`.

## Examples
Vector addition:
```java
/** Grid: c.length */
public static void vectorAddKernel(KernelContext context, float[] a, float[] b, float[] c) {
    int i = context.globalIdx;
    c[i] = a[i] + b[i];
}
```

Matrix multiplication on a 2D grid (the `k` loop stays inside each thread):
```java
/** Grid: n, n */
public static void matMulKernel(KernelContext context, float[] a, float[] b, float[] c, int n) {
    int i = context.globalIdx;
    int j = context.globalIdy;
    float sum = 0.0f;
    for (int k = 0; k < n; k++) {
        sum += a[i * n + k] * b[k * n + j];
    }
    c[i * n + j] = sum;
}
```

A method that returns a value, `static float sum(float[] input)`, becomes:
```java
/** Grid: input.length, rounded up to a multiple of 256. Local: 256 */
public static void sumKernel(KernelContext context, float[] input, FloatArray result) {
    float[] partial = context.allocateFloatLocalArray(256);
    int i = context.globalIdx;
    int local = context.localIdx;
    partial[local] = i < input.length ? input[i] : 0.0f;
    for (int stride = 128; stride > 0; stride /= 2) {
        context.localBarrier();
        if (local < stride) {
            partial[local] += partial[local + stride];
        }
    }
    if (local == 0) {
        context.atomicAdd(result, 0, partial[0]);
    }
}
```
