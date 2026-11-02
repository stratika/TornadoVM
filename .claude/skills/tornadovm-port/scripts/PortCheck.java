import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Helpers for a port check (see CheckTemplate.java): test data, the comparison with the
 * original method, the check that the kernel ran on the device, and the timings. Every result
 * is a line starting with "PORT ", which verify.sh reads. Adapted from TornadoInsight's
 * VerificationHarnessGenerator.
 */
public final class PortCheck {

    /** The arguments verify.sh passes: the data size, the data set (1 or 2), warm-up runs and timed runs. */
    public record Args(int size, int data, int warmups, int runs) {
    }

    private PortCheck() {
    }

    public static Args args(String[] args) {
        return new Args(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                Integer.parseInt(args[3]));
    }

    /**
     * The side of a square (2 dimensions) or cube (3) with about {@code size} elements. It is odd
     * in data set 2, to catch kernels that assume an even or power-of-two size.
     */
    public static int side(Args args, int dimensions) {
        int side = Math.max(2, (int) Math.round(Math.pow(args.size(), 1.0 / dimensions)));
        return args.data() == 2 ? side | 1 : side;
    }

    // Values that differ from element to element: from 0.5 to 1.5 in data set 1, from -1 to 1
    // with every 7th a zero in data set 2
    public static double value(int i, int seed, int data) {
        long step = (i * 37L + seed * 11L) % 101;
        if (data == 1) {
            return step / 101.0 + 0.5;
        }
        return i % 7 == 3 ? 0 : step / 50.5 - 1.0;
    }

    /** Integer test data: the values above times 1000. */
    public static int intValue(int i, int seed, int data) {
        return (int) Math.round(value(i, seed, data) * 1000);
    }

    public static void fill(float[] a, int seed, int data) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (float) value(i, seed, data);
        }
    }

    public static void fill(double[] a, int seed, int data) {
        for (int i = 0; i < a.length; i++) {
            a[i] = value(i, seed, data);
        }
    }

    public static void fill(int[] a, int seed, int data) {
        for (int i = 0; i < a.length; i++) {
            a[i] = intValue(i, seed, data);
        }
    }

    public static void fill(long[] a, int seed, int data) {
        for (int i = 0; i < a.length; i++) {
            a[i] = intValue(i, seed, data);
        }
    }

    public static void fill(FloatArray a, int seed, int data) {
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, (float) value(i, seed, data));
        }
    }

    public static void fill(DoubleArray a, int seed, int data) {
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, value(i, seed, data));
        }
    }

    public static void fill(IntArray a, int seed, int data) {
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, intValue(i, seed, data));
        }
    }

    public static void fill(LongArray a, int seed, int data) {
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, intValue(i, seed, data));
        }
    }

    public static FloatArray copy(FloatArray a) {
        return FloatArray.fromArray(a.toHeapArray());
    }

    public static DoubleArray copy(DoubleArray a) {
        return DoubleArray.fromArray(a.toHeapArray());
    }

    public static IntArray copy(IntArray a) {
        return IntArray.fromArray(a.toHeapArray());
    }

    public static LongArray copy(LongArray a) {
        return LongArray.fromArray(a.toHeapArray());
    }

    /** Reports that the original method threw on the test data. */
    public static void originalFailed(Throwable failure) {
        System.out.println("PORT original-failed " + oneLine(failure));
    }

    /**
     * Runs the plan once, with the profiler on. Returns false, and says why, when the kernel threw
     * or ran as Java instead of on the device (a bailout: the profiler then reports no device time).
     */
    public static boolean runOnDevice(TornadoExecutionPlan plan) {
        plan.withProfiler(ProfilerMode.SILENT);
        try {
            TornadoExecutionResult result = plan.execute();
            if (result.getProfilerResult().getDeviceKernelTime() <= 0) {
                System.out.println("PORT not-on-device");
                return false;
            }
            return true;
        } catch (Throwable failure) {
            System.out.println("PORT kernel-failed " + oneLine(failure));
            return false;
        }
    }

    /**
     * Compares what the original method and the kernel left in one array (or a single value).
     * Floating-point values match within a relative error of 1e-3.
     */
    public static boolean compare(String name, Object expected, Object actual) {
        double[] want = values(expected);
        double[] got = values(actual);
        if (got.length < want.length) {
            System.out.println("PORT mismatch " + name + ": " + want.length + " values expected, the kernel gives " + got.length);
            return false;
        }
        int wrong = 0;
        int first = -1;
        for (int i = 0; i < want.length; i++) {
            boolean same = want[i] == got[i]
                    || (Double.isNaN(want[i]) ? Double.isNaN(got[i])
                    : Math.abs(want[i] - got[i]) <= 1e-3 * Math.max(1.0, Math.abs(want[i])));
            if (!same) {
                wrong++;
                if (first < 0) {
                    first = i;
                }
            }
        }
        if (wrong > 0) {
            System.out.println("PORT mismatch " + name + ": " + wrong + " of " + want.length
                    + " values differ; at index " + first + " the method gives " + want[first]
                    + " and the kernel " + got[first]);
        }
        return wrong == 0;
    }

    /** Reports the comparison; returns whether the outputs are equal. */
    public static boolean report(boolean equal) {
        System.out.println(equal ? "PORT equal" : "PORT different");
        return equal;
    }

    /**
     * Times the original method and the plan, when verify.sh asks for timed runs. Prints the
     * medians in nanoseconds: Java, the kernel with its copies, the kernel on the device, and
     * the copies.
     */
    public static void time(Runnable original, TornadoExecutionPlan plan, Args args) {
        if (args.runs() <= 0) {
            return;
        }
        long[] java = new long[args.runs()];
        int timed = 0;
        long spent = 0;
        for (int run = 0; more(timed, args.runs(), spent); run++) {
            long start = System.nanoTime();
            original.run();
            long time = System.nanoTime() - start;
            spent += time;
            if (!warmup(run, args.warmups(), timed, time)) {
                java[timed++] = time;
            }
        }
        long javaTime = median(Arrays.copyOf(java, timed));

        long[] total = new long[args.runs()];
        long[] device = new long[args.runs()];
        long[] copies = new long[args.runs()];
        timed = 0;
        spent = 0;
        for (int run = 0; more(timed, args.runs(), spent); run++) {
            long start = System.nanoTime();
            TornadoExecutionResult result = plan.execute();
            long time = System.nanoTime() - start;
            spent += time;
            if (!warmup(run, args.warmups(), timed, time)) {
                total[timed] = time;
                device[timed] = result.getProfilerResult().getDeviceKernelTime();
                copies[timed] = result.getProfilerResult().getDataTransfersTime();
                timed++;
            }
        }
        System.out.println("PORT time " + javaTime + " " + median(Arrays.copyOf(total, timed)) + " "
                + median(Arrays.copyOf(device, timed)) + " " + median(Arrays.copyOf(copies, timed)));
    }

    // A run of over a second is warm enough: the JIT has compiled the method
    private static boolean warmup(int run, int warmups, int timed, long time) {
        return timed == 0 && run < warmups && time < 1_000_000_000L;
    }

    // At most the runs asked for, and once there are 3, no more than 10 seconds of them
    private static boolean more(int timed, int runs, long spent) {
        return timed < runs && (timed < 3 || spent < 10_000_000_000L);
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static double[] values(Object value) {
        if (value instanceof FloatArray a) {
            value = a.toHeapArray();
        } else if (value instanceof DoubleArray a) {
            value = a.toHeapArray();
        } else if (value instanceof IntArray a) {
            value = a.toHeapArray();
        } else if (value instanceof LongArray a) {
            value = a.toHeapArray();
        }
        if (value instanceof Number n) {
            return new double[] {n.doubleValue()};
        }
        if (value instanceof Boolean b) {
            return new double[] {b ? 1 : 0};
        }
        int length = Array.getLength(value);
        double[] values = new double[length];
        for (int i = 0; i < length; i++) {
            Object element = Array.get(value, i);
            values[i] = element instanceof Number n ? n.doubleValue()
                    : element instanceof Boolean b ? (b ? 1 : 0) : (Character) element;
        }
        return values;
    }

    private static String oneLine(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = failure; t != null && text.length() < 1500; t = t.getCause() == t ? null : t.getCause()) {
            if (!text.isEmpty()) {
                text.append(" | caused by ");
            }
            text.append(t.getClass().getName()).append(": ").append(t.getMessage());
        }
        String line = text.toString().replaceAll("\\s+", " ");
        return line.length() > 1500 ? line.substring(0, 1500) + "..." : line;
    }
}
