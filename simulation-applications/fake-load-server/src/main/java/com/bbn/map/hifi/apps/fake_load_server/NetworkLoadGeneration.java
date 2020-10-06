/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
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

import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Generate traffic at a specified rate for a specified duration.
 * 
 * @author jschewe
 * @see TrafficGenerator
 */
public class NetworkLoadGeneration implements Runnable {
    private static final Logger LOGGER = LogManager.getLogger(NetworkLoadGeneration.class);

    private long duration;

    private boolean successful = false;

    private final TrafficGenerator generator;

    private final ClientHandler clientHandler;

    /**
     * 
     * @param clientHandler
     *            used to stop node load generation on an error, may be null
     * @param threadPool
     *            Used to start traffic generation threads, must not limit the
     *            number of threads
     * @param mbps
     *            datarate to transmit in mpbs
     * @param duration
     *            time to run in milliseconds
     * @param channel
     *            where to write traffic to and read traffic from
     */
    public NetworkLoadGeneration(final ClientHandler clientHandler,
            @Nonnull final ExecutorService threadPool,
            final double mbps,
            final long duration,
            @Nonnull final SocketChannel channel) {
        this.duration = duration;
        this.generator = new TrafficGenerator(threadPool, mbps, channel);
        this.clientHandler = clientHandler;
    }

    /**
     * @return was the generation successful
     */
    public boolean isSuccessful() {
        return successful;
    }

    /**
     * Stop generating network traffic. Note that this will stop the generation,
     * but not interrupt the sleep. So the thread should be interrupted to force
     * the task to complete early.
     */
    public void stopGenerating() {
        generator.stopGenerating();
    }

    @Override
    public void run() {
        try {
            generator.startGenerating();
            Thread.sleep(duration);
            stopGenerating();
            successful = true;
        } catch (final InterruptedException e) {
            LOGGER.warn("Got interrupted", e);
            successful = false;
            if (null != clientHandler) {
                clientHandler.stopGeneration(false);
            }
        }
    }
}