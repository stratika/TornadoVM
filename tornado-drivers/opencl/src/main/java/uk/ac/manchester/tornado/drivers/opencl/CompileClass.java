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

public class CompileClass {
    private static int numberOfArgsPassed;
    private static int numberOfArgsFromSignature;
    private static int argSizesIndex = 0;

    private static Class<?>[] getMethodTypesFromClass(Class<?> klass, String methodName) {
        try {
            for (Method method : klass.getMethods()) {
                if (method.getName().equals(methodName)) {
                    Class<?>[] types = method.getParameterTypes();
                    numberOfArgsFromSignature = types.length;
                    return types;
                }
            }
        } catch (IllegalArgumentException e) {
            printErrorMessage("[TornadoVM-CompileClass] Load class failed.");
            throw new TornadoRuntimeException("Load class failed.");
        }
        throw new TornadoRuntimeException("[TornadoVM-CompileClass] No method found in the class file.");
    }

    private static void checkParameterSizes() {
        if (numberOfArgsPassed != numberOfArgsFromSignature) {
            throw new TornadoRuntimeException("[TornadoVM-CompileClass] Parameter mismatch: JSON vs. method signature.");
        }
    }

    public Object[] resolveParameters(Class<?> klass, String methodName, Object[] inputData, int[] parameterSizes) {
        Class<?>[] types = getMethodTypesFromClass(klass, methodName);
        checkParameterSizes();
        Object[] parameters = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (inputData != null && inputData.length > i && inputData[i] != null) {
                parameters[i] = inputData[i];
            } else {
                parameters[i] = lookupBoxedTypes(types[i], parameterSizes);
            }
        }
        return parameters;
    }

    private static Object lookupBoxedTypes(Class<?> type, int[] parameterSizes) {
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
                throw new TornadoRuntimeException("[TornadoVM-CompileClass] Unsupported type: " + type.getTypeName());
        }
    }

    public MetaCompilation compileMethod(long executionPlanId, Class<?> klass, String methodName, TornadoDevice tornadoDevice, Object[] parameters) {
        Method methodToCompile = CompilerUtil.getMethodForName(klass, methodName);
        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();
        ResolvedJavaMethod resolvedJavaMethod = tornadoRuntime.resolveMethod(methodToCompile);
        TornadoAcceleratorBackend openCLBackend = tornadoRuntime.getBackend(0);

        ScheduleContext scheduleMetaData = new ScheduleContext("s0");
        CompilableTask compilableTask = new CompilableTask(scheduleMetaData, "t0", methodToCompile, parameters);
        TaskDataContext taskMeta = compilableTask.meta();
        taskMeta.setDevice(tornadoDevice);

        Providers providers = openCLBackend.getProviders();
        TornadoSuitesProvider suites = openCLBackend.getSuitesProvider();
        Sketch sketch = CompilerUtil.buildSketchForJavaMethod(resolvedJavaMethod, taskMeta, providers, suites);

        OCLCompilationResult compilationResult = OCLCompiler.compileSketchForDevice(sketch, compilableTask, (OCLProviders) providers, (OCLBackend) openCLBackend.getDefaultBackend(),
                new EmptyProfiler());

        OCLInstalledCode openCLCode = ((OCLTornadoDevice) tornadoDevice).getDeviceContext().installCode(executionPlanId, compilationResult);

        return new MetaCompilation(taskMeta, openCLCode);
    }

    public void runWithOpenCLAPI(Long executionPlanId, OCLTornadoDevice tornadoDevice, OCLInstalledCode openCLCode, TaskDataContext taskMeta, Object... parameters) {
        OpenCL.run(executionPlanId, tornadoDevice, openCLCode, taskMeta, new Access[] { Access.READ_ONLY, Access.READ_ONLY, Access.WRITE_ONLY }, parameters);
    }

    public void compileWithData(String methodName, String className, Object[] inputData, int[] parameterSizes) {
        TornadoXPUDevice tornadoDevice = TornadoCoreRuntime.getTornadoRuntime().getDefaultDevice();
        if (tornadoDevice == null) {
            throw new TornadoRuntimeException("[TornadoVM-CompileClass] No default device available.");
        }

        Class<?> klass = readClassFromName(className);
        numberOfArgsPassed = parameterSizes.length;
        argSizesIndex = 0;

        Object[] parameters = resolveParameters(klass, methodName, inputData, parameterSizes);
        MetaCompilation meta = compileMethod(0L, klass, methodName, tornadoDevice, parameters);
        runWithOpenCLAPI(0L, (OCLTornadoDevice) tornadoDevice, (OCLInstalledCode) meta.getInstalledCode(), meta.getTaskMeta(), parameters);

        RuntimeUtilities.dumpKernel(meta.getInstalledCode().getCode());
    }

    private static Class<?> readClassFromName(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new TornadoRuntimeException("[TornadoVM-CompileClass] Class not found: " + className);
        }
    }

    public void compile(String[] args) {
        TornadoXPUDevice tornadoDevice = TornadoCoreRuntime.getTornadoRuntime().getDefaultDevice();
        if (tornadoDevice == null) {
            throw new TornadoRuntimeException("[TornadoVM-CompileClass] No default device available.");
        }

        if (args.length != 0) {
            Class<?> klass = TornadoOptions.INPUT_CLASSNAME != null ? readClassFromName(TornadoOptions.INPUT_CLASSNAME) : readClassFromFile(new File(TornadoOptions.INPUT_CLASSFILE_DIR));
            int[] parameterSizes = readArgSizesFromFile(new File(TornadoOptions.PARAMETER_SIZE_DIR));

            numberOfArgsPassed = parameterSizes.length;
            argSizesIndex = 0;

            Object[] parameters = resolveParameters(klass, args[0], null, parameterSizes);
            MetaCompilation metaCompilation = compileMethod(0L, klass, args[0], tornadoDevice, parameters);
            runWithOpenCLAPI(0L, (OCLTornadoDevice) tornadoDevice, (OCLInstalledCode) metaCompilation.getInstalledCode(), metaCompilation.getTaskMeta(), parameters);

            RuntimeUtilities.dumpKernel(metaCompilation.getInstalledCode().getCode());
        } else {
            throw new TornadoRuntimeException("[TornadoVM-CompileClass] Please provide a method name.");
        }
    }

    private Class<?> readClassFromFile(File classFile) {
        if (!classFile.exists()) {
            throw new TornadoRuntimeException("[TornadoVM-CompileClass] File does not exist: " + classFile);
        }
        try {
            return Class.forName(classFile.getName().split("\\.")[0]);
        } catch (Exception e) {
            throw new TornadoRuntimeException("[TornadoVM-CompileClass] Error reading class from file.");
        }
    }

    private int[] readArgSizesFromFile(File parameterSizeFile) {
        if (!parameterSizeFile.exists()) {
            throw new TornadoRuntimeException("[TornadoVM-CompileClass] Parameter size file not found.");
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(parameterSizeFile))) {
            ArrayList<Integer> parsedArgSizes = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                StringTokenizer tokenizer = new StringTokenizer(line, " :");
                if (tokenizer.countTokens() == 2) {
                    tokenizer.nextToken(); // Skip name
                    parsedArgSizes.add(Integer.parseInt(tokenizer.nextToken().replace(",", "")));
                    numberOfArgsPassed++;
                }
            }
            return parsedArgSizes.stream().mapToInt(Integer::intValue).toArray();
        } catch (IOException e) {
            throw new TornadoRuntimeException("[TornadoVM-CompileClass] Failed to read parameter size file.");
        }
    }

    private static void printErrorMessage(String message) {
        System.out.println(message);
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
