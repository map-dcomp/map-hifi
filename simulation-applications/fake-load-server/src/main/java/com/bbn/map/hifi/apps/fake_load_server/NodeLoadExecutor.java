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
package com.bbn.map.hifi.apps.fake_load_server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.hifi.apps.fake_load_server.fakeload.FakeSimulationInfrastructure;
import com.bbn.map.hifi.apps.fake_load_server.fakeload.LoadControllerBackport;
import com.bbn.map.hifi.apps.fake_load_server.fakeload.MapFakeLoadExecutor;
import com.bbn.map.hifi.util.UnitConversions;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.martensigwart.fakeload.CpuSimulator;
import com.martensigwart.fakeload.DefaultFakeLoadScheduler;
import com.martensigwart.fakeload.DiskInputSimulator;
import com.martensigwart.fakeload.DiskOutputSimulator;
import com.martensigwart.fakeload.FakeLoad;
import com.martensigwart.fakeload.FakeLoadExecutor;
import com.martensigwart.fakeload.FakeLoadExecutors;
import com.martensigwart.fakeload.FakeLoads;
import com.martensigwart.fakeload.FibonacciCpuSimulator;
import com.martensigwart.fakeload.MemorySimulator;
import com.martensigwart.fakeload.MemoryUnit;
import com.martensigwart.fakeload.RandomAccessDiskInputSimulator;
import com.martensigwart.fakeload.RandomAccessDiskOutputSimulator;
import com.martensigwart.fakeload.SystemLoad;

/**
 * 
 * @author jschewe
 *
 */
/* package */ class NodeLoadExecutor {

    private static final Logger LOGGER = LogManager.getLogger(NodeLoadExecutor.class);

    /**
     * Execute the specified load. This checks that the cpu load doesn't exceed
     * 100% and cause the fake load library to throw an exception.
     * 
     * @param cpu
     *            CPU percent as a number from 0 to 1
     * 
     * @param memory
     *            memory usage in Gigabytes. See {@link NodeAttribute}
     * @param duration
     *            duration in milliseconds
     * @throws RuntimeException
     *             if there is a problem executing the load, usually a maximum
     *             system load error
     */
    void executeNodeLoad(final double cpu, final double memory, final long duration) throws RuntimeException {
        final int proposedCpuIncrease = (int) Math.round(cpu * 100);

        final long memoryBytes = Math.round(UnitConversions.gigabytesToBytes(memory));

        LOGGER.info("Generating node load with CPU of {}% and Memory of {}MB for a duration of {}ms.",
                proposedCpuIncrease, memory, duration);
        final FakeLoad load = FakeLoads.create().withCpu(proposedCpuIncrease).withMemory(memoryBytes, MemoryUnit.BYTES)
                .lasting(duration, TimeUnit.MILLISECONDS);

        final FakeLoadExecutor executor = newExecutor();
        executor.execute(load);
    }

    // copied from com.martensigwart.fakeload.FakeLoadExecutor
    private static final String DISK_INPUT_FILE = "input.tmp";
    private static final String DISK_OUTPUT_FILE = "output.tmp";
    private static final String DEFAULT_DISK_INPUT_PATH = System.getProperty("java.io.tmpdir") + "/" + DISK_INPUT_FILE;
    private static final String DEFAULT_DISK_OUTPUT_PATH = System.getProperty("java.io.tmpdir") + "/"
            + DISK_OUTPUT_FILE;
    private static FakeSimulationInfrastructure defaultInfrastructure;

    /**
     * Copied from {@link FakeLoadExecutors#newDefaultExecutor()} and modified
     * to change which infrastructure is being used.
     */
    private static synchronized FakeLoadExecutor newExecutor() {
        // create infrastructure if it hasn't been created yet
        if (defaultInfrastructure == null) {

            try {
                final int noOfCores = Runtime.getRuntime().availableProcessors();

                // Create DiskInput Simulator
                final DiskInputSimulator diskInputSimulator;
                diskInputSimulator = new RandomAccessDiskInputSimulator(DEFAULT_DISK_INPUT_PATH);

                // Create DiskOutput Simulator
                final DiskOutputSimulator diskOutputSimulator;
                diskOutputSimulator = new RandomAccessDiskOutputSimulator(DEFAULT_DISK_OUTPUT_PATH);

                // Create Memory Simulator
                final MemorySimulator memorySimulator = new MemorySimulator();

                // Create CPU Simulators
                final List<CpuSimulator> cpuSimulators = new ArrayList<>();
                for (int i = 0; i < noOfCores; i++) {
                    cpuSimulators.add(new FibonacciCpuSimulator());
                }

                // Inject dependencies for LoadController
                final LoadControllerBackport controller = new LoadControllerBackport(new SystemLoad(), cpuSimulators,
                        memorySimulator, diskInputSimulator, diskOutputSimulator);

                // Create thread pool
                final ExecutorService executorService = Executors.newFixedThreadPool(noOfCores + 4,
                        new ThreadFactoryBuilder().setDaemon(true).build());

                defaultInfrastructure = new FakeSimulationInfrastructure(executorService, controller);

                /*
                 * Catch blocks in case paths can be passed as parameters.
                 */
            } catch (final IOException e) {
                LOGGER.error("File {} used for simulating disk output could not be created.", DEFAULT_DISK_OUTPUT_PATH);
                throw new IllegalArgumentException(e);
            }
        }

        return new MapFakeLoadExecutor(new DefaultFakeLoadScheduler(defaultInfrastructure));
    }

}
