import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * The check of one port: copy this file to Check<Name>.java, rename the class, and change the
 * parts marked FILL IN for the method being ported. The example checks
 * {@code Vectors.saxpy(float a, FloatArray x, FloatArray y)} and its kernel
 * {@code Vectors.saxpyKernel}. Run it with verify.sh, which passes the arguments.
 */
public class CheckTemplate {

    public static void main(String[] args) throws Exception {
        PortCheck.Args check = PortCheck.args(args);
        int n = check.size();

        // FILL IN: the method's parameters. Arrays get n elements, filled by PortCheck.fill with
        // a different seed for each array; for an n x n matrix, use n = PortCheck.side(check, 2)
        // and n * n elements. Scalars get a fixed value.
        float a = 2.0f;
        FloatArray x = new FloatArray(n);
        PortCheck.fill(x, 1, check.data());
        FloatArray y = new FloatArray(n);
        PortCheck.fill(y, 2, check.data());

        // FILL IN: a copy of every array, and the original method called on the copies. What
        // the copies hold afterwards is what the kernel must produce. Copy a Java array with
        // clone(), a TornadoVM array with PortCheck.copy.
        FloatArray xExpected = PortCheck.copy(x);
        FloatArray yExpected = PortCheck.copy(y);
        try {
            Vectors.saxpy(a, xExpected, yExpected);
        } catch (Throwable failure) {
            PortCheck.originalFailed(failure);
            return;
        }

        // FILL IN: the kernel task, with every array copied to the device and back on every
        // execution. A Kernel API kernel gets a new KernelContext() as its first argument, a
        // Tile API kernel a new TileContext().
        TaskGraph graph = new TaskGraph("check")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x, y)
                .task("kernel", Vectors::saxpyKernel, a, x, y)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, x, y);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            // FILL IN, Kernel API and Tile API only: the grid from the kernel's Javadoc.
            // WorkerGrid1D grid = new WorkerGrid1D(n);
            // grid.setLocalWork(256, 1, 1);  // with a work-group size; round n up to a multiple of it
            // plan.withGridScheduler(new GridScheduler("check.kernel", grid));

            if (!PortCheck.runOnDevice(plan)) {
                return;
            }

            // FILL IN: compare every array, the inputs too: a kernel must not change what the
            // original leaves alone.
            boolean equal = PortCheck.compare("x", xExpected, x)
                    & PortCheck.compare("y", yExpected, y);
            if (!PortCheck.report(equal)) {
                return;
            }

            // FILL IN: the same call to the original method as above.
            PortCheck.time(() -> Vectors.saxpy(a, xExpected, yExpected), plan, check);
        }
    }
}
