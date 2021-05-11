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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martensigwart.fakeload.CpuSimulator;
import com.martensigwart.fakeload.DefaultSimulationInfrastructure;
import com.martensigwart.fakeload.DiskInputSimulator;
import com.martensigwart.fakeload.DiskOutputSimulator;
import com.martensigwart.fakeload.FakeLoad;
import com.martensigwart.fakeload.LoadController;
import com.martensigwart.fakeload.MaximumLoadExceededException;
import com.martensigwart.fakeload.MemorySimulator;
import com.martensigwart.fakeload.SimulationInfrastructure;

/**
 * MAP implementation class of {@code SimulationInfrastructure}.
 *
 * @since 1.8
 * @see DefaultSimulationInfrastructure
 * @see SimulationInfrastructure
 * @see FakeLoad
 * @see LoadController
 * @see ExecutorService
 *
 * @author Marten Sigwart
 */
@ThreadSafe
public final class FakeSimulationInfrastructure implements SimulationInfrastructure {

    private static final Logger LOGGER = LoggerFactory.getLogger(FakeSimulationInfrastructure.class);

    /**
     * Thread pool which is used for executing the different simulator threads
     */
    private final ExecutorService executorService;

    /**
     * Controller thread responsible for controlling the load created by
     * simulator threads, as well as taking track of the currently executed
     * system load (especially important in multithreaded scenarios).
     */
    private final LoadControllerBackport controller;

    @GuardedBy("this")
    private boolean started;

    /**
     * Creates a new {@code DefaultSimulationInfrastructure} instance using the
     * provided {@link ExecutorService} and {@link LoadController}.
     * 
     * @param executorService
     *            the thread pool used for executing simulator threads
     * @param controller
     *            the controller used for controlling simulator threads and
     *            overall system load in concurrent scenarios
     */
    public FakeSimulationInfrastructure(ExecutorService executorService, LoadControllerBackport controller) {

        this.executorService = executorService;
        this.controller = controller;
        this.started = false;

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    @Override
    public void increaseSystemLoadBy(FakeLoad load) throws MaximumLoadExceededException {
        start();
        controller.increaseSystemLoadBy(load);
    }

    @Override
    public void decreaseSystemLoadBy(FakeLoad load) {
        controller.decreaseSystemLoadBy(load);
        // TODO check if system load is now zero, if yes start timer to cancel
        // simulator tasks.
    }

    /**
     * Starts the simulation infrastructure, if it was not already started.
     * <p>
     * Boolean {@code started} indicates whether or not the simulator threads
     * have already been submitted to the {@code ExecutorService}. If not, all
     * simulator tasks are submitted and {@code started} is set to true.
     */
    private synchronized void start() {
        if (!started) {
            LOGGER.debug("Starting infrastructure...");

            startLoadController();
            startCpuSimulators();
            startMemorySimulator();
            startDiskInputSimulator();
            startDiskOutputSimulator();

            started = true;

            // TODO save returned Future references
            // --> Could be useful for cancelling simulator tasks in time of
            // inactivity.

            LOGGER.debug("Successfully started infrastructure");
        }
    }

    private void startLoadController() {
        CompletableFuture.runAsync(controller, executorService);
        LOGGER.debug("Started Simulation Control");
    }

    private void startCpuSimulators() {
        List<CpuSimulator> cpuSimulators = controller.getCpuSimulators();

        for (Runnable thread : cpuSimulators) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(thread, executorService);
            future.exceptionally(e -> {
                LOGGER.error("Cpu Simulator died: {}", e.getMessage());
                e.printStackTrace();
                return null;
            });
        }
        LOGGER.debug("Started {} CPU Simulators", cpuSimulators.size());
    }

    private void startMemorySimulator() {

        MemorySimulator memorySimulator = controller.getMemorySimulator();
        if (memorySimulator == null) {
            return;
        }

        memorySimulator.setLoad(0);
        CompletableFuture<Void> future = CompletableFuture.runAsync(memorySimulator, executorService);

        // In case of MemorySimulator thread dies because OutOfMemoryError,
        // it is restarted immediately using CompletableFuture.exceptionally.
        future.exceptionally(e -> {
            LOGGER.warn("Memory Simulator died: {}, starting new one...", e.getMessage());
            startMemorySimulator();
            return null;
        });
        LOGGER.debug("Started Memory Simulator");

    }

    private void startDiskInputSimulator() {
        DiskInputSimulator diskInputSimulator = controller.getDiskInputSimulator();
        if (diskInputSimulator == null) {
            return;
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(diskInputSimulator, executorService);
        future.exceptionally(e -> {
            LOGGER.error("Disk Input Simulator died: {}", e.getMessage());
            e.printStackTrace();
            return null;
        });
        LOGGER.debug("Started DiskInput Simulator");
    }

    private void startDiskOutputSimulator() {
        DiskOutputSimulator diskOutputSimulator = controller.getDiskOutputSimulator();
        if (diskOutputSimulator == null) {
            return;
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(diskOutputSimulator, executorService);
        future.exceptionally(e -> {
            LOGGER.error("Disk Output Simulator died: {}", e.getMessage());
            e.printStackTrace();
            return null;
        });
        LOGGER.debug("Started Disk Output Simulator");
    }

    private static final int TERMINATION_TIMEOUT_SECONDS = 50;

    /**
     * Gracefully shuts down the simulation infrastructure.
     * <p>
     * This means shutting down the {@code ExecutorService} with which the
     * simulation tasks are run.
     */
    private void shutdown() {
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS)) {
                LOGGER.warn("Still waiting for termination...");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.debug("ExecutorService shutdown");
    }

}
