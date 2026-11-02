# Rules for every TornadoVM kernel

<!-- Adapted from TornadoInsight's src/main/resources/ai/tornadovm-rules.md. Keep the two in sync. -->

The kernel must compute exactly the same results as the original method for every input.

TornadoVM does not check that iterations are independent: a parallel loop or kernel whose
iterations depend on each other compiles and runs, but computes wrong results. If every loop of
the method carries a value from one iteration to the next (for example `y[i] = a * x[i] + y[i - 1]`)
and is not a reduction (a sum, product, minimum or maximum), do not write a kernel: tell the user
that the method cannot be parallelised, and why.

## The kernel method
- Write one method, `public static void <name>Kernel(<parameters>)`, in the same class as the
  original. A kernel cannot be an instance method.
- Keep the original parameters: the same types, names and order. Do not change `float[]` into
  `FloatArray` or the other way round.
- If the original method returns a value, the kernel is still `void`: add one last parameter that
  receives the result, an array of length 1 for a single value (for example `float[] result`) or
  the output array.
- Add the imports the kernel needs, from `uk.ac.manchester.tornado.api`:
  `annotations.Parallel`, `annotations.Reduce`, `KernelContext`, `tile.TileContext`,
  `tile.PartitionView`, `tile.Tile`, `math.TornadoMath`, `types.arrays.FloatArray` and so on.

## Not allowed inside a kernel
- Exceptions: no `throw`, `try`, `catch` or `throws`.
- No `assert` statements.
- No recursion, direct or through other methods.
- No `System.out`, `System.err`, other `System` calls, I/O, threads or `synchronized`.
- No calls to library methods other than `java.lang.Math` and TornadoVM's `TornadoMath`: no
  collections, streams, lambdas or `String`.
- Data types: primitives (`int`, `long`, `float`, `double`, `short`, `byte`, `char`, `boolean`),
  arrays of primitives, and TornadoVM types such as `FloatArray`, `IntArray`, `VectorFloat`,
  `Matrix2DFloat`. No other objects, and no `new` objects.
- Static helper methods of the same class may be called if they follow the same rules. The
  helper's code must be in the project, not in a library jar.

## Backends
- **Metal** has no 64-bit floating point. Use `float`, and call `TornadoMath.log`,
  `TornadoMath.exp`, `TornadoMath.sqrt` and so on with `float` arguments instead of `Math.*`,
  which works in `double`. A `double` anywhere in the kernel, including a helper it calls, fails
  with `TornadoDeviceFP64NotSupported`. Leave the original method as it is.
- **OpenCL** devices may or may not support `double`; `tornado --devices` does not say, so
  verify.
- **Tile API** kernels run only on NVIDIA GPUs through the CUDA backend.
