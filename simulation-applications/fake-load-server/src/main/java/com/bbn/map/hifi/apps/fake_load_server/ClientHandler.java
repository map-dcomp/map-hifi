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

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.simulator.ClientLoad;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Runnable for communicating with a client and producing load.
 * 
 * @author awald
 */
/* package */ class ClientHandler implements Runnable {
    private final Logger logger;

    private final SocketChannel clientChannel; // the socket used to communicate
    // with the
    // client
    private final int clientNumber; // a number to indicate which client in
                                    // the
    // sequence of connecting clients this thread
    // handles

    private final AtomicInteger numberOfClients;

    private final double physicalCores;

    private final CSVPrinter latencyLog;

    private final NodeLoadExecutor nodeLoadExecutor;
    private final ExecutorService threadPool;

    private NetworkLoadGeneration networkLoadGenerator;
    private NodeLoadGeneration nodeLoadGenerator;
    private Future<?> networkFuture;
    private Future<?> nodeLoadFuture;

    // default to true and if stopGeneration is called, then this is false
    private boolean success = true;

    private final Object lock = new Object();

    /**
     * Creates a handler to communicate with a client and produce load.
     * 
     * @param latencyLog
     *            where to log information about processing latency
     * @param physicalCores
     *            number of physical cores in the system. This needs to be
     *            consistent with the number of cores that the fake load library
     *            sees.
     * @param numberOfClients
     *            used to track how many clients are currently active
     * @param threadPool
     *            used to spawn threads for network and load generation
     * @param channel
     *            the channel that is connected to the client
     * @param number
     *            the client number
     * @param nodeLoadExecutor
     *            Used to execute node load
     */
    ClientHandler(@Nonnull final CSVPrinter latencyLog,
            final double physicalCores,
            @Nonnull final AtomicInteger numberOfClients,
            @Nonnull final ExecutorService threadPool,
            @Nonnull final SocketChannel channel,
            final int number,
            @Nonnull final NodeLoadExecutor nodeLoadExecutor) {
        this.clientChannel = Objects.requireNonNull(channel);
        this.clientNumber = number;
        this.numberOfClients = Objects.requireNonNull(numberOfClients);
        this.latencyLog = Objects.requireNonNull(latencyLog);
        this.physicalCores = physicalCores;
        this.threadPool = Objects.requireNonNull(threadPool);
        this.nodeLoadExecutor = nodeLoadExecutor;
        this.logger = LoggerFactory.getLogger(this.getClass().getName() + "." + String.valueOf(number) + "." + channel);
    }

    @Override
    public void run() {
        numberOfClients.incrementAndGet();
        try {
            logger.info("Started communication with client " + clientNumber + " on socket " + clientChannel + ".");

            handleRequests();
        } finally {
            numberOfClients.decrementAndGet();
        }
    }

    private void
            writeLatencyLog(final String address, final long requestStart, final long requestEnd, final boolean success)
                    throws IOException {
        synchronized (latencyLog) {
            latencyLog.printRecord(System.currentTimeMillis(), success ? "request_success" : "request_failure", address,
                    requestStart, requestEnd, requestEnd - requestStart);
            latencyLog.flush();
        }
    }

    private void handleRequests() {

        final ObjectMapper jsonMapper = JsonUtils.getStandardMapObjectMapper()
                .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET).disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

        String serverHostAddress = "unknown";
        try {
            final SocketAddress serverAddress = clientChannel.getRemoteAddress();
            if (serverAddress instanceof InetSocketAddress) {
                final InetAddress addr = ((InetSocketAddress) serverAddress).getAddress();
                if (null != addr) {
                    serverHostAddress = addr.getHostAddress();
                }
            }
        } catch (final IOException e) {
            logger.warn("Error getting remote address of socket", e);
        }

        try (InputStream in = Channels.newInputStream(clientChannel)) {
            final ClientLoad request = jsonMapper.readValue(in, ClientLoad.class);

            final long timeRequestReceived = System.currentTimeMillis();
            processRequest(request);
            final long timeRequestProcessed = System.currentTimeMillis();

            final boolean successLocal;
            synchronized (lock) {
                successLocal = success;
            }

            try {
                writeLatencyLog(serverHostAddress, timeRequestReceived, timeRequestProcessed, successLocal);
            } catch (final IOException e) {
                logger.error("Error writing latency log", e);
            }
        } catch (final IOException e) {
            logger.error("Unable to obtain an input stream to communicate with client " + clientNumber, e);
        }

    }

    private void processRequest(final ClientLoad request) {
        final double cpu = request.getNodeLoad().getOrDefault(NodeAttribute.CPU,
                request.getNodeLoad().getOrDefault(NodeAttribute.TASK_CONTAINERS, 0.0));
        final double scaledCpu = cpu / physicalCores;
        final double memory = request.getNodeLoad().getOrDefault(NodeAttribute.MEMORY, 0.0);

        final double tx = request.getNetworkLoad().getOrDefault(LinkAttribute.DATARATE_TX, 0.0);

        final long serverDuration = request.getServerDuration();
        final long networkDuration = request.getNetworkDuration();

        logger.info("Request for {} CPU ({} scaled) and {}GB of memory {} network TX", cpu, scaledCpu, memory, tx);
        try {
            synchronized (lock) {
                networkLoadGenerator = new NetworkLoadGeneration(this, threadPool, tx, networkDuration, clientChannel);
                nodeLoadGenerator = new NodeLoadGeneration(this, nodeLoadExecutor, scaledCpu, memory, serverDuration);

                networkFuture = threadPool.submit(networkLoadGenerator);
                nodeLoadFuture = threadPool.submit(nodeLoadGenerator);
            }

            try {
                networkFuture.get();
            } catch (final ExecutionException e) {
                logger.error("Exception thrown executing network load generation", e);
            } catch (final CancellationException e) {
                logger.debug("Network generation cancelled", e);
            } catch (final InterruptedException e) {
                logger.warn("Interrupted waiting on the network load generation", e);
            }

            try {
                nodeLoadFuture.get();
            } catch (final ExecutionException e) {
                logger.error("Exception thrown executing node load generation", e);
            } catch (final CancellationException e) {
                logger.debug("Node load generation cancelled", e);
            } catch (final InterruptedException e) {
                logger.warn("Interrupted waiting on the node load generation", e);
            }

            synchronized (lock) {
                networkLoadGenerator = null;
                nodeLoadGenerator = null;
                networkFuture = null;
                nodeLoadFuture = null;
            }

        } catch (final RuntimeException e) {
            final String message = String.format("Cannot generate load for request %s", request);
            logger.error(message, e);
        }
    }

    /**
     * Stop execution of any load.
     */
    public void stopGeneration(final boolean success) {
        synchronized (lock) {
            this.success = success;
            if (null != networkLoadGenerator) {
                networkLoadGenerator.stopGenerating();
            }
            if (null != networkFuture) {
                networkFuture.cancel(true);
            }
            if (null != nodeLoadFuture) {
                nodeLoadFuture.cancel(true);
            }
        }
    }

}