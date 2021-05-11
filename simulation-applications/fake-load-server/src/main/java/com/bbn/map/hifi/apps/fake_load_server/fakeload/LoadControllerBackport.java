/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020,2021>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
the exception of the dcop implementation identified below (see notes).

Dispersed Computing (DCOMP)
Mission-oriented Adaptive Placement of Task and Data (MAP) 

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
BBN_LICENSE_END*/
package com.bbn.map.hifi.apps.fake_load_server.fakeload;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martensigwart.fakeload.CpuSimulator;
import com.martensigwart.fakeload.DiskInputSimulator;
import com.martensigwart.fakeload.DiskOutputSimulator;
import com.martensigwart.fakeload.FakeLoad;
import com.martensigwart.fakeload.LoadController;
import com.martensigwart.fakeload.MaximumLoadExceededException;
import com.martensigwart.fakeload.MemorySimulator;
import com.martensigwart.fakeload.SystemLoad;

import jdk14.OperatingSystemBackport;

/**
 * Use methods backported from JDK 14 to get the system load inside a docker
 * container.
 * 
 * This class is copied from {@link com.martensigwart.fakeload.LoadController}
 * with minor modifications to how the system load is computed.
 * 
 * @author jschewe
 * @see LoadController
 *
 */
public class LoadControllerBackport implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadController.class);
    private static final OperatingSystemBackport OPERATING_SYSTEM = new OperatingSystemBackport();
    private static final int SLEEP_PERIOD = 2000;
    private static final int CPU_CONTROL_THRESHOLD = 1;

    private final SystemLoad systemLoad;
    private final List<CpuSimulator> cpuSimulators;
    private final MemorySimulator memorySimulator;
    private final DiskInputSimulator diskInputSimulator;
    private final DiskOutputSimulator diskOutputSimulator;
    private final double stepSize;
    private final Object lock;

    // Set to lower then CPU_CONTROL_THRESHOLD
    private double lastCpu = -CPU_CONTROL_THRESHOLD - 1;
    private long oldDesiredCpu = 0L;
    private int increaseCpuIndex;
    private int decreaseCpuIndex;

    /**
     * 
     * @param systemLoad
     *            the desired system load
     * @param cpuSimulators
     *            what keeps the CPU busy
     * @param memorySimulator
     *            what keeps the memory busy
     * @param diskInputSimulator
     *            what keeps disk busy
     * @param diskOutputSimulator
     *            what keeps disk busy
     */
    public LoadControllerBackport(final SystemLoad systemLoad,
            final List<CpuSimulator> cpuSimulators,
            final MemorySimulator memorySimulator,
            final DiskInputSimulator diskInputSimulator,
            final DiskOutputSimulator diskOutputSimulator) {
        this.systemLoad = systemLoad;
        this.cpuSimulators = Collections.unmodifiableList(cpuSimulators);
        this.memorySimulator = memorySimulator;
        this.diskInputSimulator = diskInputSimulator;
        this.diskOutputSimulator = diskOutputSimulator;

        int noOfCores = Runtime.getRuntime().availableProcessors();
        this.stepSize = 1.0 / noOfCores;
        this.increaseCpuIndex = 0;
        this.decreaseCpuIndex = noOfCores - 1;
        this.lock = new Object();
    }

    @Override
    public void run() {
        LOGGER.debug("LoadController - Started");

        boolean running = true;
        while (running) {
            try {
                synchronized (lock) {
                    while (systemLoad.getCpu() == 0) {
                        LOGGER.debug("LoadController - Nothing to control, waiting...");
                        lock.wait();
                        LOGGER.debug("LoadController - Woke Up");
                    }
                }
                Thread.sleep(SLEEP_PERIOD);
                controlCpuLoad();

            } catch (InterruptedException e) {
                LOGGER.debug("LoadController - Interrupted");
                running = false;
            }
        }
    }

    /**
     * @param load
     *            how much to increase the load by
     * @throws MaximumLoadExceededException
     *             if the load goes above the limit
     */
    public void increaseSystemLoadBy(FakeLoad load) throws MaximumLoadExceededException {
        systemLoad.increaseBy(load);

        for (CpuSimulator cpuSim : cpuSimulators) {
            cpuSim.setLoad(systemLoad.getCpu());
        }

        synchronized (lock) {
            lock.notify(); // notify thread executing the run method
        }

        memorySimulator.setLoad(systemLoad.getMemory());
        diskInputSimulator.setLoad(systemLoad.getDiskInput());
        diskOutputSimulator.setLoad(systemLoad.getDiskOutput());
    }

    /**
     * 
     * @param load
     *            how much to decrease the load by
     */
    public void decreaseSystemLoadBy(FakeLoad load) {
        systemLoad.decreaseBy(load);

        for (CpuSimulator cpuSim : cpuSimulators) {
            cpuSim.setLoad(systemLoad.getCpu());
        }
        memorySimulator.setLoad(systemLoad.getMemory());
        diskInputSimulator.setLoad(systemLoad.getDiskInput());
        diskOutputSimulator.setLoad(systemLoad.getDiskOutput());
    }

    /**
     * Controls and adjusts the <i>actual</i> CPU load produced by CPU simulator
     * threads.
     *
     * <p>
     * CPU load adjustment is done in the following way:
     *
     * <p>
     * First, the desired CPU load is retrieved from the {@link SystemLoad}
     * instance. The desired load is compared to the last desired CPU load
     * recorded by the method. When the desired load has been adjusted recently
     * and old and new desired load differ, the old load is set to the new one
     * and the method returns with no load adjustment taking place, as simulator
     * threads might not have had the time to catch up to change in desired load
     * yet.
     *
     * <p>
     * When there is no difference between the new and old desired CPU load, the
     * desired load is compared to the actual CPU load currently produced by the
     * simulator threads. When desired and actual CPU load differ by more than a
     * defined threshold, and actual CPU load is <b>not</b> currently changing,
     * CPU load will be adjusted.
     *
     */
    private void controlCpuLoad() {
        long desiredCpu = systemLoad.getCpu();
        if (desiredCpu != oldDesiredCpu) {
            LOGGER.trace("Last desired load: {}, new desired load: {} --> Not adjusting CPU load", oldDesiredCpu,
                    desiredCpu);
            oldDesiredCpu = desiredCpu;
            return;
        }

        double actualCpu = OPERATING_SYSTEM.getCpuLoad() * 100;
        LOGGER.trace("Desired CPU: {}, Actual CPU: {}, Last CPU: {}", desiredCpu, actualCpu, lastCpu);

        double difference = actualCpu - desiredCpu;

        if (Math.abs(difference) > CPU_CONTROL_THRESHOLD && Math.abs(lastCpu - actualCpu) <= CPU_CONTROL_THRESHOLD) {

            int noOfSteps = (int) (Math.abs(difference) / stepSize);
            LOGGER.trace("Number of adjustment steps: {}", noOfSteps);
            if (difference < 0) { // actual load smaller than desired load
                LOGGER.trace("Increasing CPU load, difference {}", difference);
                increaseCpuSimulatorLoads(1, noOfSteps);
            } else {
                LOGGER.trace("Decreasing CPU load, difference {}", difference);
                decreaseCpuSimulatorLoads(1, noOfSteps);
            }

        }

        lastCpu = actualCpu;

    }

    private void increaseCpuSimulatorLoads(int delta, int noOfSteps) {
        for (int i = 0; i < noOfSteps; i++) {
            CpuSimulator cpuSimulator = cpuSimulators.get(increaseCpuIndex);

            decreaseCpuIndex = increaseCpuIndex;
            increaseCpuIndex = (increaseCpuIndex + 1) % cpuSimulators.size();

            /*
             * Only increase if load is not maxed out. Synchronization of
             * simulator is not important here, if-statement only to prevent
             * unnecessary calls to increase load.
             */
            if (!cpuSimulator.isMaximumLoad()) {
                cpuSimulator.increaseLoad(delta);
            }
        }
    }

    private void decreaseCpuSimulatorLoads(int delta, int noOfSteps) {
        for (int i = 0; i < noOfSteps; i++) {
            CpuSimulator cpuSimulator = cpuSimulators.get(decreaseCpuIndex);

            increaseCpuIndex = decreaseCpuIndex;
            decreaseCpuIndex = (cpuSimulators.size() - 1 + decreaseCpuIndex) % cpuSimulators.size();

            /*
             * Only decrease if load is not zero. Synchronization of simulator
             * is not important here, if-statement only to prevent unnecessary
             * calls to decrease load.
             */
            if (!cpuSimulator.isZeroLoad()) {
                cpuSimulator.decreaseLoad(delta);
            }
        }
    }

    /**
     * 
     * @return what keeps the cpu busy
     */
    public List<CpuSimulator> getCpuSimulators() {
        return cpuSimulators;
    }

    /**
     * 
     * @return what keeps the memory busy
     */
    public MemorySimulator getMemorySimulator() {
        return memorySimulator;
    }

    /**
     * 
     * @return what keeps the disk busy
     */
    public DiskInputSimulator getDiskInputSimulator() {
        return diskInputSimulator;
    }

    /**
     * 
     * @return what keeps the disk busy
     */
    public DiskOutputSimulator getDiskOutputSimulator() {
        return diskOutputSimulator;
    }
}
