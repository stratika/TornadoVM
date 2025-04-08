package uk.ac.manchester.tornado.drivers.opencl;

/**
 * Generic runner that compiles and executes TornadoVM kernel methods
 * using user-defined input data.
 */
public class TornadoVMRunner {

    /**
     * Generic execution method that compiles and runs a TornadoVM kernel.
     *
     * @param className
     *     Fully qualified class name of the kernel class
     * @param methodName
     *     Method name to compile and run
     * @param inputData
     *     Array of input objects (e.g., IntArray, int[], etc.)
     * @param parameterSizes
     *     Sizes of parameters that require allocation (e.g., arrays)
     */
    public void run(String className, String methodName, Object[] inputData, int[] parameterSizes) {
        if (inputData == null || parameterSizes == null) {
            throw new IllegalArgumentException("Input data and parameter sizes must not be null.");
        }

        CompileClass compiler = new CompileClass();
        compiler.compileWithData(methodName, className, inputData, parameterSizes);

        System.out.println("\u2705 Kernel executed. You can now inspect the output arrays.");
    }
}