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

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.hifi.util.SimAppUtils;
import com.bbn.map.hifi.util.network.TrafficGenerator;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;

/*package*/ class NodeLoadGeneration implements Runnable {
    private static final Logger LOGGER = LogManager.getLogger(NodeLoadGeneration.class);

    private double cpu;
    private double memory;
    private long duration;
    private final NodeLoadExecutor executor;

    private boolean successful = false;

    private final TrafficGenerator networkLoadGenerator;

    /**
     * 
     * @param networkLoadGenerator
     *            used to stop the network traffic on a failure, can be null
     * @param executor
     *            where to execute node load
     * @param cpu
     *            how much CPU
     * @param memory
     *            how much memory
     * @param duration
     *            how long
     */
    NodeLoadGeneration(final @Nullable TrafficGenerator networkLoadGenerator,
            final NodeLoadExecutor executor,
            final double cpu,
            final double memory,
            final long duration) {
        this.executor = executor;
        this.cpu = cpu;
        this.memory = memory;
        this.duration = duration;
        this.networkLoadGenerator = networkLoadGenerator;
    }

    boolean isSuccessful() {
        return successful;
    }

    @Override
    public void run() {
        successful = generateNodeLoad(cpu, memory, duration);
        if (!successful && null != networkLoadGenerator) {
            // make sure that network traffic stops right away
            networkLoadGenerator.sendFailure();
            networkLoadGenerator.shutdown();
        }
    }

    private static final long MIN_RETRY_MS = 1000;
    private static final long MAX_RETRY_MS = 3000;

    /**
     * Generates node load including CPU and Memory usage for a given amount of
     * time.
     * 
     * @param cpu
     *            CPU percent as a number from 0 to 1
     * @param memory
     *            memory usage in Gigabytes. See {@link NodeAttribute}
     * @param duration
     *            duration in milliseconds
     * @return true if the load could be generated and false otherwise
     */
    private boolean generateNodeLoad(final double cpu, final double memory, final long duration) {
        final long start = System.currentTimeMillis();

        try {
            this.executor.executeNodeLoad(cpu, memory, duration);
            LOGGER.info("Load generation complete.");
            return true;
        } catch (final RuntimeException e) {
            final long diff = System.currentTimeMillis() - start;
            final long delayStart = SimAppUtils.getRetryDelay(MIN_RETRY_MS, MAX_RETRY_MS);
            final long reduceDuration = delayStart + diff;
            final long retryDuration = duration - reduceDuration;
            LOGGER.warn("Delaying start by {} ms, reducing duration by {} ms", delayStart, reduceDuration);

            if (retryDuration < 1) {
                LOGGER.error("Retry duration is less than 1, failing request");
                return false;
            }

            try {
                Thread.sleep(delayStart);
            } catch (final InterruptedException sleepEx) {
                LOGGER.warn("Delayed start interrupted", sleepEx);
            }

            try {
                this.executor.executeNodeLoad(cpu, memory, retryDuration);
                LOGGER.info("Load generation complete.");
                return true;
            } catch (final RuntimeException retryEx) {
                LOGGER.error("Load generation failed.", retryEx);
                return false;
            }
        }
    }

}