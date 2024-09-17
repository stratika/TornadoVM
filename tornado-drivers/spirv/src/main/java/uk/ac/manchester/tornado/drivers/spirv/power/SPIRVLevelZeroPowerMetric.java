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
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroPowerMonitor;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeResult;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZesPowerEnergyCounter;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.util.ArrayList;
import java.util.List;

public class SPIRVLevelZeroPowerMetric implements PowerMetric {
    private final SPIRVDeviceContext deviceContext;
    private final LevelZeroPowerMonitor levelZeroPowerMonitor;
    private List<ZesPowerEnergyCounter> initialEnergyCounters;
    private List<ZesPowerEnergyCounter> finalEnergyCounters;

    public SPIRVLevelZeroPowerMetric(SPIRVDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        initializePowerLibrary();
        levelZeroPowerMonitor = new LevelZeroPowerMonitor();
    }

    @Override
    public void initializePowerLibrary() {

    }

    @Override
    public void getHandleByIndex(long[] devices) {
        if (isPowerFunctionsSupportedForDevice(this.deviceContext.getDevice().getDeviceIndex())) {
            System.out.println("[SPIRV] Level Zero device supports power functions");
        } else {
            System.out.println("[SPIRV] Level Zero device does not support power functions");
        }
    }

    @Override
    public void getPowerUsage(long[] devices, long[] powerUsage) {
        long device = devices[this.deviceContext.getDevice().getDeviceIndex()];
        if (isPowerFunctionsSupportedForDevice(device)) {
            powerUsage = new long[1];
            powerUsage[0] = (long) calculateEnergyCounters(initialEnergyCounters, finalEnergyCounters);
        }

    }

    public void readInitialCounters(long device) {
        initialEnergyCounters = getEnergyCounters(device);
    }

    public void readFinalCounters(long device) {
        finalEnergyCounters = getEnergyCounters(device);
    }

    private List<ZesPowerEnergyCounter> getEnergyCounters(long device) {
        //        long sysmanDeviceIndex = device.getDeviceIndex();
        List<ZesPowerEnergyCounter> energyCounters = new ArrayList<>();
        int result = levelZeroPowerMonitor.getEnergyCounters(device, energyCounters);
        if (result != ZeResult.ZE_RESULT_SUCCESS) {
            throw new RuntimeException("Failed to get energy counters. Error code: " + result);
        }
        return energyCounters;
    }

    public double calculateEnergyCounters(List<ZesPowerEnergyCounter> initialEnergyCounters, List<ZesPowerEnergyCounter> finalEnergyCounters) {
        return levelZeroPowerMonitor.calculatePowerUsage_mW(initialEnergyCounters, finalEnergyCounters);
    }

    public boolean isPowerFunctionsSupportedForDevice(long device) {
        return levelZeroPowerMonitor.getPowerSupportStatusForDevice(device);
    }

}
