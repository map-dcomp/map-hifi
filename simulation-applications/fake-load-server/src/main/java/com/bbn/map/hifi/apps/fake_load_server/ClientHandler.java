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
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.hifi.util.StoppableGenerator;
import com.bbn.map.hifi.util.network.TrafficGenerator;
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
/* package */ class ClientHandler implements Runnable, StoppableGenerator {
    private final Logger logger;

    private final SocketChannel clientChannel; // the socket used to communicate
    // with the
    // client
    private final int clientNumber; // a number to indicate which client in
                                    // the
    // sequence of connecting clients this thread
    // handles

    private final AtomicInteger numberOfClients;

    private final CSVPrinter latencyLog;

    private final NodeLoadExecutor nodeLoadExecutor;
    private final ExecutorService threadPool;

    private TrafficGenerator networkLoadGenerator;
    private NodeLoadGeneration nodeLoadGenerator;
    private Future<?> networkFuture;
    private Future<?> nodeLoadFuture;

    private final Object lock = new Object();

    private final FakeLoadServer server;

    /**
     * Creates a handler to communicate with a client and produce load.
     * 
     * @param latencyLog
     *            where to log information about processing latency
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
     * @param server
     *            the server that accepted the connection. Used to execute
     *            dependent requests.
     */
    ClientHandler(@Nonnull final FakeLoadServer server,
            @Nonnull final CSVPrinter latencyLog,
            @Nonnull final AtomicInteger numberOfClients,
            @Nonnull final ExecutorService threadPool,
            @Nonnull final SocketChannel channel,
            final int number,
            @Nonnull final NodeLoadExecutor nodeLoadExecutor) {
        this.server = Objects.requireNonNull(server);
        this.clientChannel = Objects.requireNonNull(channel);
        this.clientNumber = number;
        this.numberOfClients = Objects.requireNonNull(numberOfClients);
        this.latencyLog = Objects.requireNonNull(latencyLog);
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

    private static final String LOG_ABORT_MESSAGE = "processing failed";
    private static final String LOG_SUCCESS_MESSAGE = "request_success";
    private static final String LOG_FAILURE_MESSAGE = "request_failure";
    private static final String LOG_WRONG_SERVICE_MESSAGE_FORMAT = "wrong service requested {}, but running {}";

    private void
            writeLatencyLog(final String address, final long requestStart, final long requestEnd, final String message)
                    throws IOException {
        synchronized (latencyLog) {
            latencyLog.printRecord(System.currentTimeMillis(), message, address, requestStart, requestEnd,
                    requestEnd - requestStart);
            latencyLog.flush();
        }
    }

    private String determineClientHostAddress() {
        String clientHostAddress = "unknown";
        try {
            final SocketAddress clientAddress = clientChannel.getRemoteAddress();
            if (clientAddress instanceof InetSocketAddress) {
                final InetAddress addr = ((InetSocketAddress) clientAddress).getAddress();
                if (null != addr) {
                    clientHostAddress = addr.getHostAddress();
                }
            }
        } catch (final IOException e) {
            logger.warn("Error getting remote address of socket", e);
        }

        return clientHostAddress;
    }

    private void handleRequests() {
        final String clientHostAddress = determineClientHostAddress();

        final ObjectMapper jsonMapper = JsonUtils.getStandardMapObjectMapper()
                .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET).disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

        boolean latencyLogWritten = false;
        try (InputStream in = Channels.newInputStream(clientChannel)) {
            final ClientLoad request = jsonMapper.readValue(in, ClientLoad.class);
            final long timeRequestReceived = System.currentTimeMillis();

            final Pair<Boolean, String> result = processRequest(request);
            final long timeRequestProcessed = System.currentTimeMillis();

            final long serverExpectedEndTime = timeRequestReceived + request.getServerDuration();
            final long networkExpectedEndTime = timeRequestReceived + request.getNetworkDuration();
            if (!result.getLeft()) {
                server.getFailedRequestWriter().writeFailedRequest(request, clientHostAddress, serverExpectedEndTime,
                        networkExpectedEndTime);
            }

            try {
                writeLatencyLog(clientHostAddress, timeRequestReceived, timeRequestProcessed, result.getRight());
            } catch (final IOException e) {
                logger.error("Error writing latency log", e);
            }
            latencyLogWritten = true;

            if (result.getLeft()) {
                // only execute dependent load if the initial request was
                // successful
                server.executeDependentLoad(request);
            }

            logger.trace("Closing client channel at end of try");
        } catch (final IOException e) {
            logger.error("Unable to obtain an input stream to communicate with client " + clientNumber, e);
        } finally {
            if (!latencyLogWritten) {
                logger.warn("Latency log not written, doing write with 0 times");
                try {
                    writeLatencyLog(clientHostAddress, 0, 0, LOG_ABORT_MESSAGE);
                } catch (final IOException e) {
                    logger.error("Error writing latency log (finally)", e);
                }
            }
        }
        logger.trace("Finished with request, closed client channel");

    }

    private Pair<Boolean, String> processRequest(final ClientLoad request) {
        String message = null;
        boolean success = true;

        final double cpu = request.getNodeLoad().getOrDefault(NodeAttribute.CPU,
                request.getNodeLoad().getOrDefault(NodeAttribute.TASK_CONTAINERS, 0.0));
        final double memory = request.getNodeLoad().getOrDefault(NodeAttribute.MEMORY, 0.0);

        final double tx = request.getNetworkLoad().getOrDefault(LinkAttribute.DATARATE_TX, 0.0);

        final long serverDuration = request.getServerDuration();
        final long networkDuration = request.getNetworkDuration();

        logger.info("Request for {} CPU and {}GB of memory {} network TX", cpu, memory, tx);
        synchronized (lock) {
            networkLoadGenerator = new TrafficGenerator(threadPool, tx, networkDuration, clientChannel);
            nodeLoadGenerator = new NodeLoadGeneration(networkLoadGenerator, nodeLoadExecutor, cpu, memory,
                    serverDuration);

            networkFuture = threadPool.submit(networkLoadGenerator);
            nodeLoadFuture = threadPool.submit(nodeLoadGenerator);
        }

        if (!request.getService().equals(server.getExecutingService())) {
            logger.error("Wrong service requested. Running: {} requested: {}", server.getExecutingService(),
                    request.getService());

            message = String.format(LOG_WRONG_SERVICE_MESSAGE_FORMAT, request.getService(),
                    server.getExecutingService());
            success = false;
            networkLoadGenerator.sendFailure();
            nodeLoadFuture.cancel(true);
        }

        try {
            logger.debug("Waiting for networkFuture {}", networkFuture);
            networkFuture.get();
        } catch (final ExecutionException e) {
            logger.error("Exception thrown executing network load generation", e);
        } catch (final CancellationException e) {
            logger.debug("Network generation cancelled", e);
        } catch (final InterruptedException e) {
            logger.warn("Interrupted waiting on the network load generation", e);
        }

        try {
            logger.debug("Waiting for nodeLoadFuture {}", nodeLoadFuture);
            nodeLoadFuture.get();
        } catch (final ExecutionException e) {
            logger.error("Exception thrown executing node load generation", e);
        } catch (final CancellationException e) {
            logger.debug("Node load generation cancelled", e);
        } catch (final InterruptedException e) {
            logger.warn("Interrupted waiting on the node load generation", e);
        }

        if (null == message) {
            // if the network load generator is already gone because the
            // noadLoadGenerator has sent a failure these are nops.
            if (nodeLoadGenerator.isSuccessful()) {
                networkLoadGenerator.sendSuccess();
                message = LOG_SUCCESS_MESSAGE;
                success = true;
            } else {
                networkLoadGenerator.sendFailure();
                message = LOG_FAILURE_MESSAGE;
                success = false;
            }
        }

        logger.info("Shutting down network load generation");
        networkLoadGenerator.shutdown();
        try {
            networkLoadGenerator.waitForShutdown();
        } catch (final InterruptedException e) {
            logger.warn("Interrupted waiting for network generator to shutdown", e);
        }

        logger.info("Load request complete");
        return Pair.of(success, message);
    }

    @Override
    public void stopGeneration(final boolean success) {
        synchronized (lock) {
            networkLoadGenerator = null;
            nodeLoadGenerator = null;
            networkFuture = null;
            nodeLoadFuture = null;
        }
    }

}