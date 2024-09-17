/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv.power;

import uk.ac.manchester.tornado.drivers.common.power.PowerMetric;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroPowerMonitor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeResult;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZesPowerEnergyCounter;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.util.ArrayList;
import java.util.List;

public class SPIRVLevelZeroPowerMetric implements PowerMetric {
    private final SPIRVDeviceContext deviceContext;
    private final LevelZeroDevice l0Device;
    private final LevelZeroPowerMonitor levelZeroPowerMonitor;
    private List<ZesPowerEnergyCounter> initialEnergyCounters;
    private List<ZesPowerEnergyCounter> finalEnergyCounters;

    public SPIRVLevelZeroPowerMetric(SPIRVDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        initializePowerLibrary();
        levelZeroPowerMonitor = new LevelZeroPowerMonitor();
        l0Device = (LevelZeroDevice) deviceContext.getDevice().getDeviceRuntime();
    }

    @Override
    public void initializePowerLibrary() {

    }

    @Override
    public void getHandleByIndex(long[] devices) {
        if (isPowerFunctionsSupportedForDevice()) {
            System.out.println("[SPIRV] Level Zero device supports power functions");
        } else {
            System.out.println("[SPIRV] Level Zero device does not support power functions");
        }
    }

    @Override
    public void getPowerUsage(long[] device, long[] powerUsage) {

    }

    @Override
    public void getPowerUsage(long[] devices, double[] powerUsage) {
        if (isPowerFunctionsSupportedForDevice()) {
            powerUsage = new double[1];
            System.out.println("[SPIRV] Level Zero calculateEnergyCounters= initial: " + initialEnergyCounters.getFirst().getEnergy() + " - final: " + finalEnergyCounters.getFirst().getEnergy());
            double result = calculateEnergyCounters(initialEnergyCounters, finalEnergyCounters);
            System.out.println("Power usage result: " + result);
            double d = 94728.62453531599;
            System.out.println(d);  // Check the value before casting
            long value = (long) d;
            System.out.println(value);  // Should print 94728
            powerUsage[0] = (long) result;
        }

    }

    public void readInitialCounters() {
        initialEnergyCounters = getEnergyCounters();
    }

    public void readFinalCounters() {
        finalEnergyCounters = getEnergyCounters();
    }

    private List<ZesPowerEnergyCounter> getEnergyCounters() {
        List<ZesPowerEnergyCounter> energyCounters = new ArrayList<>();
        int result = levelZeroPowerMonitor.getEnergyCounters(l0Device.getDeviceHandlerPtr(), energyCounters);
        if (result != ZeResult.ZE_RESULT_SUCCESS) {
            throw new RuntimeException("Failed to get energy counters. Error code: " + result);
        }
        return energyCounters;
    }

    public double calculateEnergyCounters(List<ZesPowerEnergyCounter> initialEnergyCounters, List<ZesPowerEnergyCounter> finalEnergyCounters) {
        return levelZeroPowerMonitor.calculatePowerUsage_mW(initialEnergyCounters, finalEnergyCounters);
    }

    public boolean isPowerFunctionsSupportedForDevice() {
        return levelZeroPowerMonitor.getPowerSupportStatusForDevice(l0Device.getDeviceHandlerPtr());
    }

}
