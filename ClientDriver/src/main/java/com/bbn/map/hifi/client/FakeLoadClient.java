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
package com.bbn.map.hifi.client;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xbill.DNS.Address;

import com.bbn.map.hifi.apps.fake_load_server.FakeLoadServer;
import com.bbn.map.hifi.apps.fake_load_server.NetworkLoadGeneration;
import com.bbn.map.hifi.util.SimAppUtils;
import com.bbn.map.simulator.ClientLoad;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Send requests to the fake load server. (
 * 
 * @author jschewe
 *
 */
public class FakeLoadClient implements JavaClient {

    private final Logger logger;

    private final String host;

    private final CSVPrinter latencyLog;
    private final CSVPrinter requestStatus;

    private final ClientLoad request;

    private static final String[] CLIENT_LATENCY_CSV_HEADER = { "timestamp", "server", "time_sent", "time_ack_received",
            "latency", "expected_duration" };

    private final ExecutorService threadPool;

    private static Logger getLogger(final String host) {
        return LogManager.getLogger(String.format("%s.%s", FakeLoadClient.class.getName(), host));
    }

    /**
     * 
     * @param host
     *            the hostname to connect to
     * @param logPath
     *            where to log information
     * @param request
     *            how much load to generate, sent to the server
     * @param threadPool
     *            used to start additional threads
     * @throws IOException
     *             if there is an error opening latencyLogPath
     */
    public FakeLoadClient(final String host,
            final Path logPath,
            final ClientLoad request,
            final ExecutorService threadPool) throws IOException {
        this.logger = getLogger(host);
        this.host = host;

        final Path latencyLogPath = logPath.resolve(SimAppUtils.LATENCY_LOG_FILENAME);
        this.latencyLog = new CSVPrinter(Files.newBufferedWriter(latencyLogPath, StandardOpenOption.CREATE_NEW),
                CSVFormat.EXCEL.withHeader(CLIENT_LATENCY_CSV_HEADER));

        final Path requestStatusPath = logPath.resolve(SimAppUtils.REQUEST_STATUS_FILENAME);
        this.requestStatus = new CSVPrinter(Files.newBufferedWriter(requestStatusPath, StandardOpenOption.CREATE_NEW),
                CSVFormat.EXCEL.withHeader(SimAppUtils.CLIENT_REQUEST_STATUS_HEADER));

        this.request = request;
        this.threadPool = threadPool;
    }

    private final AtomicBoolean done = new AtomicBoolean(false);

    @Override
    public void stop() {
        logger.trace("Stopping client");
        done.set(true);
    }

    private static final int MAX_ATTEMPTS = 3;

    @Override
    public void run() {
        logger.info("Starting request {}", request);

        for (int attempt = 0; attempt < MAX_ATTEMPTS && !done.get(); ++attempt) {
            logger.info("Connection attempt {} of {}", (attempt + 1), MAX_ATTEMPTS);

            final long requestStart = System.currentTimeMillis();
            logger.trace("Starting request");

            String errorMessage = null;
            Throwable exception = null;
            InetAddress addr = null;
            try {
                // explicitly do the DNS resolution so that we can
                // write the IP of the server into the log
                addr = Address.getByName(host);
                logger.info("Connecting to {}", addr);

                try (SocketChannel channel = SocketChannel.open(new InetSocketAddress(addr, FakeLoadServer.PORT))) {

                    final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper()
                            .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
                            .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

                    try (OutputStream out = Channels.newOutputStream(channel)) {

                        // send request
                        mapper.writeValue(out, request);
                        out.flush();
                        final long networkDuration = request.getNetworkDuration();
                        final double rx = request.getNetworkLoad().getOrDefault(LinkAttribute.DATARATE_RX, 0.0);

                        logger.info("Generating {} mbps network traffic", rx);

                        final NetworkLoadGeneration generator = new NetworkLoadGeneration(null, threadPool, rx,
                                networkDuration, channel);
                        final Future<?> future = threadPool.submit(generator);
                        try {
                            future.get();
                        } catch (final ExecutionException e) {
                            logger.error("Got exception from network load generator", e);
                        }

                        final long requestEnd = System.currentTimeMillis();

                        try {
                            // write log entry
                            logger.trace("Starting log write");
                            writeLatencyLog(addr, requestStart, requestEnd,
                                    Math.max(request.getNetworkDuration(), request.getServerDuration()));
                            logger.trace("Finished log write");
                        } catch (final IOException e) {
                            logger.error("Error writing to the latency log", e);
                        }

                        writeRequestStatus(requestStart, addr, true, null);

                        // note that the request finished
                        done.set(true);
                    } catch (final InterruptedException e) {
                        writeRequestStatus(requestStart, addr, false, "interrupted");

                        if (done.get()) {
                            logger.debug("Interrupted waiting for the generator to finish, done is true", e);
                        } else {
                            logger.warn("Interrupted waiting for the generator to finish", e);
                        }
                    }
                } // socket allocation

            } catch (final UnsupportedEncodingException e) {
                writeRequestStatus(requestStart, addr, false, "internal error: encoding");

                logger.error("Internal error, unknown encoding UTF-8", e);
                done.set(true);
            } catch (final UnknownHostException e) {
                writeRequestStatus(requestStart, addr, false, "unknown host");

                errorMessage = String.format("Unable to find host %s.", host);
                exception = e;
            } catch (final SocketException e) {
                writeRequestStatus(requestStart, addr, false, "socket error");

                errorMessage = "Socket error talking to the server.";
                exception = e;
            } catch (final IOException e) {
                writeRequestStatus(requestStart, addr, false, "I/O error");

                errorMessage = "General error talking to the server.";
                exception = e;
            } finally {
                if (null != errorMessage) {
                    if (!done.get()) {
                        if (MAX_ATTEMPTS == attempt + 1) {
                            logger.error("All attempts to connect have failed");
                        } else {
                            final long retryDelay = SimAppUtils.getClientRetryDelay();
                            logger.error("{} Trying again in {} ms.", errorMessage, retryDelay, exception);
                            try {
                                Thread.sleep(retryDelay);
                            } catch (InterruptedException e1) {
                                logger.error("Problem waiting retry delay of {} ms.", retryDelay, e1);
                            }
                        }
                    } else {
                        logger.info("{} Stopping, so no sleep", errorMessage, exception);
                    }
                }
            }

        } // foreach attempt

        logger.info("Finished request {}", request);
    }

    private void writeLatencyLog(final InetAddress address,
            final long requestStart,
            final long requestEnd,
            final long expectedDuration) throws IOException {
        latencyLog.printRecord(System.currentTimeMillis(), address.getHostAddress(), requestStart, requestEnd,
                requestEnd - requestStart, expectedDuration);
        latencyLog.flush();
    }

    private void writeRequestStatus(final long timestamp,
            final InetAddress addr,
            final boolean success,
            final String message) {
        try {
            requestStatus.printRecord(timestamp, null == addr ? null : addr.getHostAddress(), success, message);
            requestStatus.flush();
        } catch (final IOException e) {
            logger.error("Error writing request status {} {} {}", timestamp, success, message, e);
        }
    }

    /*
     * public static void main(final String[] args) throws IOException { final
     * long startTime = 0; final long serverDuration = 30000; final long
     * networkDuration = serverDuration; final int numClients = 2; final
     * ApplicationCoordinates service = ApplicationCoordinates.UNMANAGED; final
     * ImmutableMap<NodeAttribute, Double> nodeLoad =
     * ImmutableMap.of(NodeAttribute.CPU, 0.2, NodeAttribute.MEMORY, 0.3); final
     * ImmutableMap<LinkAttribute, Double> networkLoad =
     * ImmutableMap.of(LinkAttribute.DATARATE_RX, 0.4,
     * LinkAttribute.DATARATE_TX, 0.7);
     * 
     * final ClientLoad request = new ClientLoad(startTime, serverDuration,
     * networkDuration, numClients, service, nodeLoad, networkLoad);
     * 
     * final FakeLoadClient client = new FakeLoadClient("localhost", Paths.get(
     * "/home/jschewe/projects/map/issues/412.fake-load/client_processing_latency.csv"
     * ), request); client.run(); }
     */

}
