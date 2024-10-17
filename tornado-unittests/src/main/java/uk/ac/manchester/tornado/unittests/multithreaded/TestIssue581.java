package uk.ac.manchester.tornado.unittests.multithreaded;

import org.junit.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.HashSet;

/**
 * Test to check the invocation of multiple execution plans from different Java threads.
 * The different execution plans are built using the same instance of the Task-Graph.
 *
 * How to run?
 *
 * <p>
 * <code>
 * $ tornado-test --printKernel -V uk.ac.manchester.tornado.unittests.multithreaded.TestIssue581
 * </code>
 * </p>
 */
public class TestIssue581 extends TornadoTestBase {
    @Test
    public void fatalError() {
        HashSet<String> s = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            s.add(Integer.toString(i));
        }

        s.parallelStream().forEach(k -> {
            try {
                TestIssue581.dummy();
            } catch (TornadoExecutionPlanException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
        System.out.println();
    }

    public static void dummy() throws TornadoExecutionPlanException {
        DoubleArray tris1 = new DoubleArray(100);
        DoubleArray tris2 = new DoubleArray(100);
        DoubleArray res = new DoubleArray(10000);
        String threadName = Thread.currentThread().getName();
        TaskGraph taskGraph = new TaskGraph("s1") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, tris1, tris2) //
                .task(threadName, TestIssue581::calculateCrossPoint, tris1, tris2, res) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, res);
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph)) {
            executor.execute();
        }
    }

    private static void calculateCrossPoint(final DoubleArray a, final DoubleArray b, DoubleArray res) {

        for (@Parallel int i = 0; i < a.getSize(); i += 9) {
            for (@Parallel int j = 0; j < b.getSize(); j += 9) {
                //				Double3 point = getTrianglesIntersectionPoint(new Double3(a.get(i),a.get(i+1),a.get(i+2)),new Double3(a.get(i+3),a.get(i+4),a.get(i+5)),
                //						new Double3(a.get(i+6),a.get(i+7),a.get(i+8)),new Double3(b.get(j),b.get(j+1),b.get(j+2)),
                //						new Double3(b.get(j+3),b.get(j+4),b.get(j+5)),new Double3(b.get(j+6),b.get(j+7),b.get(j+8)));
                //				res.set((i/9 * b.getSize()/9 + j/9) * 3, point.getX());
                //				res.set((i/9 * b.getSize()/9 + j/9) * 3 + 1, point.getY());
                //				res.set((i/9 * b.getSize()/9 + j/9) * 3 + 2, point.getZ());
            }
        }
    }
}
