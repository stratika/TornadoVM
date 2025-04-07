/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.phases.util.Providers;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble2;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble3;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble4;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble8;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat2;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat3;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat8;
import uk.ac.manchester.tornado.api.types.collections.VectorInt2;
import uk.ac.manchester.tornado.api.types.collections.VectorInt3;
import uk.ac.manchester.tornado.api.types.collections.VectorInt4;
import uk.ac.manchester.tornado.api.types.collections.VectorInt8;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.common.MetaCompilation;
import uk.ac.manchester.tornado.drivers.common.utils.CompilerUtil;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompiler;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLProviders;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLJITCompiler;
import uk.ac.manchester.tornado.runtime.TornadoAcceleratorBackend;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;
import uk.ac.manchester.tornado.runtime.profiler.EmptyProfiler;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * This class aims to provide a way that TornadoVM can compile at runtime class files.
 */
public class CompileClass {
    private static int numberOfArgsPassed;
    private static int numberOfArgsFromSignature;
    private static int argSizesIndex = 0;
    private static String[] argNames;

    private static Class[] getMethodTypesFromClass(Class<?> klass, String methodName) {
        try {
            Method[] methods = klass.getMethods();
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].getName().equals(methodName)) {
                    Class[] types = methods[i].getParameterTypes();
                    numberOfArgsFromSignature = types.length;
                    return types;
                }
            }
        } catch (IllegalArgumentException e) {
            String message = "[TornadoVM-CompileClass] Load class failed.";
            printErrorMessage(message);
            throw new TornadoRuntimeException(message);
        }
        String message = "[TornadoVM-CompileClass] No method found in the class file.";
        printErrorMessage(message);
        throw new TornadoRuntimeException(message);
    }

    @SuppressWarnings("checkstyle:LineLength")
    private void checkParameterSizes() {
        if (numberOfArgsPassed != numberOfArgsFromSignature) {
            String message = "[TornadoVM-CompileClass] The number of parameters passed in JSON (" + numberOfArgsPassed + ") are not the same as the number of boxed types in the method (" + numberOfArgsFromSignature + ").";
            printErrorMessage(message);
            throw new TornadoRuntimeException(message);
        }
    }

    private static Object[] resolveParametersFromTypes(Class[] types, int[] parameterSizes) {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = lookupBoxedTypes(types[i], parameterSizes);
        }
        return args;
    }

    private static Object lookupBoxedTypes(Class type, int[] parameterSizes) {
        switch (type.getTypeName()) {
            case "int[]":
                return new int[parameterSizes[argSizesIndex++]];
            case "long[]":
                return new long[parameterSizes[argSizesIndex++]];
            case "float[]":
                return new float[parameterSizes[argSizesIndex++]];
            case "double[]":
                return new double[parameterSizes[argSizesIndex++]];
            case "int":
                return Integer.valueOf(0);
            case "long":
                return Long.valueOf(0);
            case "float":
                return Float.valueOf(0.0f);
            case "double":
                return Double.valueOf(0.0f);
            case "uk.ac.manchester.tornado.api.types.collections.VectorInt2":
                return new VectorInt2(parameterSizes[argSizesIndex++]);
            case "uk.ac.manchester.tornado.api.types.collections.VectorInt3":
                return new VectorInt3(parameterSizes[argSizesIndex++]);
            case "uk.ac.manchester.tornado.api.types.collections.VectorInt4":
                return new VectorInt4(parameterSizes[argSizesIndex++]);
            case "uk.ac.manchester.tornado.api.types.collections.VectorInt8":
                return new VectorInt8(parameterSizes[argSizesIndex++]);
            case "uk.ac.manchester.tornado.api.types.collections.VectorFloat2":
                return new VectorFloat2(parameterSizes[argSizesIndex++]);
            case "uk.ac.manchester.tornado.api.types.collections.VectorFloat3":
                return new VectorFloat3(parameterSizes[argSizesIndex++]);
            case "uk.ac.manchester.tornado.api.types.collections.VectorFloat4":
                return new VectorFloat4(parameterSizes[argSizesIndex++]);
            case "uk.ac.manchester.tornado.api.types.collections.VectorFloat8":
                return new VectorFloat8(parameterSizes[argSizesIndex++]);
            case "uk.ac.manchester.tornado.api.types.collections.VectorDouble2":
                return new VectorDouble2(parameterSizes[argSizesIndex++]);
            case "uk.ac.manchester.tornado.api.types.collections.VectorDouble3":
                return new VectorDouble3(parameterSizes[argSizesIndex++]);
            case "uk.ac.manchester.tornado.api.types.collections.VectorDouble4":
                return new VectorDouble4(parameterSizes[argSizesIndex++]);
            case "uk.ac.manchester.tornado.api.types.collections.VectorDouble8":
                return new VectorDouble8(parameterSizes[argSizesIndex++]);
            case "uk.ac.manchester.tornado.api.types.arrays.IntArray":
                return new IntArray(parameterSizes[argSizesIndex++]);
            default:
                String message = "[TornadoVM-CompileClass] - type(" + type.getTypeName() + ") is not recognized by the frontend.";
                printErrorMessage(message);
                throw new TornadoRuntimeException(message);
        }
    }

    public MetaCompilation compileMethod(long executionPlanId, Class<?> klass, String methodName, TornadoDevice tornadoDevice, Object[] parameters) {

        // Get the method object to be compiled
        Method methodToCompile = CompilerUtil.getMethodForName(klass, methodName);

        // Get Tornado Runtime
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();

        // Get the Graal Resolved Java Method
        ResolvedJavaMethod resolvedJavaMethod = tornadoRuntime.resolveMethod(methodToCompile);

        // Get the backend from TornadoVM
        TornadoAcceleratorBackend openCLBackend = tornadoRuntime.getBackend(0);

        // Create a new task for TornadoVM
        ScheduleContext scheduleMetaData = new ScheduleContext("s0");
        // Create a compilable task
        CompilableTask compilableTask = new CompilableTask(scheduleMetaData, "t0", methodToCompile, parameters);
        if (compilableTask == null) {
            String message = "[TornadoVM-CompileClass] Internal error in the TornadoVM compiler.";
            printErrorMessage(message);
            throw new TornadoRuntimeException(message);
        }
        TaskDataContext taskMeta = compilableTask.meta();
        taskMeta.setDevice(tornadoDevice);

        // 1. Build Common Compiler Phase (Sketcher)
        // Utility to build a sketcher and insert into the HashMap for fast LookUps
        Providers providers = openCLBackend.getProviders();
        TornadoSuitesProvider suites = openCLBackend.getSuitesProvider();
        Sketch sketch = CompilerUtil.buildSketchForJavaMethod(resolvedJavaMethod, taskMeta, providers, suites);
        if (sketch == null) {
            String message = "[TornadoVM-CompileClass] Internal error in the TornadoVM Sketcher.";
            printErrorMessage(message);
            throw new TornadoRuntimeException(message);
        }

        OCLCompilationResult compilationResult = OCLCompiler.compileSketchForDevice(sketch, compilableTask, (OCLProviders) providers, (OCLBackend) openCLBackend.getDefaultBackend(),
                new EmptyProfiler());
        if (compilationResult == null) {
            String message = "[TornadoVM-CompileClass] Internal error in the TornadoVM compiler.";
            printErrorMessage(message);
            throw new TornadoRuntimeException(message);
        }

        // Install the OpenCL Code in the VM
        OCLInstalledCode openCLCode = ((OCLTornadoDevice) tornadoDevice).getDeviceContext().installCode(executionPlanId, compilationResult);

        return new MetaCompilation(taskMeta, openCLCode);
    }

    public Object[] resolveParameters(Class<?> klass, String methodName, int[] parameterSizes) {
        // Create a compilable task
        Class[] types = getMethodTypesFromClass(klass, methodName);
        checkParameterSizes();
        Object[] parameters = resolveParametersFromTypes(types, parameterSizes);
        return parameters;
    }

    public void runWithOpenCLAPI(Long executionPlanId, OCLTornadoDevice tornadoDevice, OCLInstalledCode openCLCode, TaskDataContext taskMeta, Object... parameters) {
        OpenCL.run(executionPlanId, tornadoDevice, openCLCode, taskMeta, new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY }, parameters);
    }

    private Class readClassFromFile(File classFile) {
        if (!classFile.exists()) {
            String message = "[TornadoVM-CompileClass] " + classFile + " does not exist.";
            printErrorMessage(message);
            throw new TornadoRuntimeException(message);
        }
        Class klass = null;
        try {
            klass = Class.forName(classFile.getName().split("\\.")[0]);
        } catch (ClassNotFoundException e) {
            String message = "[TornadoVM-CompileClass] ClassNotFoundException for classname: " + classFile.getName().split("\\.")[0];
            printErrorMessage(message);
            throw new TornadoRuntimeException(message);
        } catch (NoClassDefFoundError e) {
            e.printStackTrace();
        }
        return klass;
    }

    private Class readClassFromName(String className) {
        Class klass = null;
        try {
            klass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            String message = "[TornadoVM-CompileClass] ClassNotFoundException for className: " + className;
            printErrorMessage(message);
            throw new TornadoRuntimeException(message);
        } catch (NoClassDefFoundError e) {
            e.printStackTrace();
        }
        return klass;
    }

    private String trimComma(String string) {
        return string.replaceFirst("\\,", "");
    }

    private int[] readArgSizesFromFile(File parameterSizeFile) {
        if (!parameterSizeFile.exists()) {
            String message = "[TornadoVM-CompileClass] " + parameterSizeFile + " does not exist.";
            printErrorMessage(message);
            throw new TornadoRuntimeException(message);
        }
        FileReader fileReader;
        BufferedReader bufferedReader;
        ArrayList<String> parsedArgNames = new ArrayList<>();
        ArrayList<Integer> parsedArgSizes = new ArrayList<>();

        try {
            fileReader = new FileReader(parameterSizeFile);
            bufferedReader = new BufferedReader(fileReader);
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                StringTokenizer tokenizer = new StringTokenizer(line, " :");
                while (tokenizer.hasMoreElements()) {
                    int numberOfTokensInLine = tokenizer.countTokens();
                    String token = tokenizer.nextToken();
                    if (token.contains("{") || token.contains("}")) {
                        break;
                    }
                    if (numberOfTokensInLine == 2) {
                        parsedArgNames.add(token);
                        parsedArgSizes.add(Integer.parseInt(trimComma(tokenizer.nextToken())));
                    }
                    numberOfArgsPassed++;
                }
            }
        } catch (IOException e) {
            String message = "[TornadoVM-CompileClass] Wrong parameter size file or invalid settings.";
            printErrorMessage(message);
            throw new TornadoRuntimeException(message);
        }

        if (numberOfArgsPassed > 0) {
            argNames = new String[numberOfArgsPassed];
            int[] argSizes = new int[numberOfArgsPassed];

            for (int i = 0; i < argSizes.length; i++) {
                argNames[i] = parsedArgNames.get(i);
                argSizes[i] = parsedArgSizes.get(i);
            }
            return argSizes;
        } else {
            String message = "[TornadoVM-CompileClass] No parameter size was loaded from file.";
            printErrorMessage(message);
            throw new TornadoRuntimeException(message);
        }
    }

    private static void printErrorMessage(String message) {
        System.out.println(message);
    }

    public void compile(String[] args) {
        TornadoXPUDevice tornadoDevice = TornadoCoreRuntime.getTornadoRuntime().getDefaultDevice();
        if (tornadoDevice == null) {
            String message = "[TornadoVM-CompileClass] Virtual device has not been obtained.";
            printErrorMessage(message);
            throw new TornadoRuntimeException(message);
        }

        if (args.length != 0) {
            Class klass = null;
            if (TornadoOptions.INPUT_CLASSNAME != null) {
                klass = readClassFromName(TornadoOptions.INPUT_CLASSNAME);
            } else if (TornadoOptions.INPUT_CLASSFILE_DIR != null) {
                klass = readClassFromFile(new File(TornadoOptions.INPUT_CLASSFILE_DIR));
            }
            int[] parameterSizes = readArgSizesFromFile(new File(TornadoOptions.PARAMETER_SIZE_DIR));
            long executionPlanId = 0;

            Object[] parameters = resolveParameters(klass, args[0], parameterSizes);
            MetaCompilation metaCompilation = compileMethod(executionPlanId, klass, args[0], tornadoDevice, parameters);

            // Check with OpenCL API
            runWithOpenCLAPI(executionPlanId, (OCLTornadoDevice) tornadoDevice, (OCLInstalledCode) metaCompilation.getInstalledCode(), metaCompilation.getTaskMeta(), parameters);

            RuntimeUtilities.dumpKernel(metaCompilation.getInstalledCode().getCode());
        } else {
            String message = "[TornadoVM-CompileClass] Please pass the method name as parameter.";
            printErrorMessage(message);
            throw new TornadoRuntimeException(message);
        }
    }

    public static void main(String[] args) {
        try {
            new CompileClass().compile(args);
        } catch (TornadoRuntimeException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}