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
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xbill.DNS.Address;

import com.bbn.map.hifi.util.SimAppUtils;

/**
 * Base web client that generates random strings.
 * 
 * @author jschewe
 *
 */
public abstract class BaseRandomStringWebClient implements JavaClient {

    private static final Logger LOGGER = LogManager.getLogger(BaseRandomStringWebClient.class);

    private static final String[] CLIENT_LATENCY_CSV_HEADER = { "timestamp", "event", "server", "time_sent",
            "time_ack_received", "latency" };

    private final String host;

    /**
     * @return the host to connect to
     */
    public String getHost() {
        return host;
    }

    private static final int DEFAULT_PORT = 8080;
    private int port = DEFAULT_PORT;

    private final String url;

    /**
     * @return the port to connect to on the specified host.
     */
    public int getPort() {
        return port;
    }

    /**
     * 
     * @param v
     *            see {@link #getPort()}
     */
    public void setPort(final int v) {
        port = v;
    }

    private final CSVPrinter latencyLog;
    private final CSVPrinter requestStatus;

    /**
     * 
     * @param host
     *            the hostname to connect to
     * @param logPath
     *            where to log information
     * @param url
     *            the URL to to connect to on the host. This should not include
     *            the leading slash.
     * @throws IOException
     *             if there is an error opening latencyLogPath
     */
    public BaseRandomStringWebClient(final String host, final Path logPath, final String url) throws IOException {
        final Path latencyLogPath = logPath.resolve(SimAppUtils.LATENCY_LOG_FILENAME);
        LOGGER.debug("Logging latency information to {}", latencyLogPath);

        this.host = host;
        this.latencyLog = new CSVPrinter(Files.newBufferedWriter(latencyLogPath, StandardOpenOption.CREATE_NEW),
                CSVFormat.EXCEL.withHeader(CLIENT_LATENCY_CSV_HEADER));

        final Path requestStatusPath = logPath.resolve(SimAppUtils.REQUEST_STATUS_FILENAME);
        LOGGER.debug("Logging request status to {}", requestStatusPath);
        this.requestStatus = new CSVPrinter(Files.newBufferedWriter(requestStatusPath, StandardOpenOption.CREATE_NEW),
                CSVFormat.EXCEL.withHeader(SimAppUtils.CLIENT_REQUEST_STATUS_HEADER));

        this.url = url;
    }

    /**
     * 1 MB
     */
    private static final int DEFAULT_PAYLOAD_SIZE = 1 * 1024 * 1024;

    private int payloadSize = DEFAULT_PAYLOAD_SIZE;

    /**
     * 
     * @return how many characters to put in the POST request.
     */
    public int getPayloadSize() {
        return payloadSize;
    }

    /**
     * 
     * @param v
     *            see {@link #getPayloadSize()}
     */
    public void setPayloadSize(final int v) {
        payloadSize = v;
    }

    private final AtomicBoolean done = new AtomicBoolean(false);

    @Override
    public void stop() {
        LOGGER.trace("Stopping client");
        done.set(true);
    }

    private static final int SECONDS_BETWEEN_REQUESTS = 10;
    private static final Duration TIME_BETWEEN_REQUESTS = Duration.ofSeconds(SECONDS_BETWEEN_REQUESTS);

    private static final Duration CURL_STOP_TIMEOUT = Duration.ofSeconds(10);

    @Override
    public void run() {
        LOGGER.trace("Client started. Done? {}", done);

        // generate payload once
        LOGGER.trace("Generating payload");
        final String postDataStr = String.format("{\"payload\": \"%s\"", generatePayload());
        LOGGER.trace("Finished generating payload");

        try {
            final Path payloadFile = Files.createTempFile("payload", ".json");

            try {
                try (Writer w = Files.newBufferedWriter(payloadFile)) {
                    w.write(postDataStr);
                }

                while (!done.get()) {
                    try {
                        final Path outputFile = Files.createTempFile("output", ".txt");

                        final long requestStart = System.currentTimeMillis();
                        InetAddress addr = null;
                        LOGGER.trace("Starting request");
                        try {

                            // explicitly do the DNS resolution so that we can
                            // write
                            // the IP of the server into the log
                            addr = Address.getByName(host);
                            final String urlStr = String.format("http://%s:%d/%s", addr.getHostAddress(), port, url);

                            // One should be able to just use Apache HTTP
                            // components
                            // to do the request, but it hangs reading the reply
                            // from the server.
                            final ProcessBuilder builder = new ProcessBuilder("curl", "-X", "POST", "--header",
                                    "Content-Type: application/json", "-d",
                                    String.format("@%s", payloadFile.toString()), "-o", outputFile.toString(), urlStr);
                            LOGGER.trace("Command {}", builder.command());

                            final Process process = builder.start();
                            try {
                                process.waitFor();
                            } catch (final InterruptedException e) {
                                LOGGER.info("Interrupted waiting for curl to finish, killing curl", e);

                                process.destroy();
                                try {
                                    process.waitFor(CURL_STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                                } catch (final InterruptedException e2) {
                                    LOGGER.debug("Interrupted waiting for curl process to exit", e2);
                                }
                                if (process.isAlive()) {
                                    process.destroyForcibly();
                                }
                            }
                            final long requestEnd = System.currentTimeMillis();

                            LOGGER.trace("Starting log write");
                            writeLatencyLog(addr, requestStart, requestEnd);
                            LOGGER.trace("Finished log write");

                            writeRequestStatus(requestStart, addr, true, null);

                            try {
                                Thread.sleep(TIME_BETWEEN_REQUESTS.toMillis());
                            } catch (final InterruptedException e) {
                                Thread.currentThread().interrupt();
                                LOGGER.info("Interrupted waiting between requests", e);
                            }

                        } catch (final UnknownHostException e) {
                            writeRequestStatus(requestStart, addr, false, "unknown host");

                            final long retryDelay = SimAppUtils.getClientRetryDelay();
                            LOGGER.error("Unable to find host {}. Trying again in {} ms.", host, retryDelay, e);
                            try {
                                Thread.sleep(retryDelay);
                            } catch (InterruptedException e1) {
                                LOGGER.error("Problem waiting retry delay of {} ms.", retryDelay, e1);
                            }
                        } catch (final UnsupportedEncodingException e) {
                            writeRequestStatus(requestStart, addr, false, "internal error: encoding");

                            LOGGER.error("Internal error, unknown encoding UTF-8", e);
                        } catch (final ClientProtocolException e) {
                            writeRequestStatus(requestStart, addr, false, "internal error: client protocol");

                            LOGGER.error("Internal error in HTTP protocol", e);
                        } catch (final SocketException e) {
                            writeRequestStatus(requestStart, addr, false, "general error");

                            final long retryDelay = SimAppUtils.getClientRetryDelay();
                            LOGGER.error("Error talking to the server. Trying again in {} ms.", retryDelay, e);
                            try {
                                Thread.sleep(retryDelay);
                            } catch (InterruptedException e1) {
                                LOGGER.error("Problem waiting retry delay of {} ms.", retryDelay, e1);
                            }
                        } catch (final IOException e) {
                            writeRequestStatus(requestStart, addr, false, "I/O error");

                            long retryDelay = SimAppUtils.getClientRetryDelay();
                            LOGGER.error(
                                    "Error talking to the server or writing to the latency log. Trying again in {} ms.",
                                    retryDelay, e);
                            try {
                                Thread.sleep(retryDelay);
                            } catch (InterruptedException e1) {
                                LOGGER.error("Problem waiting retry delay of {} ms.", retryDelay, e1);
                            }
                        } finally {
                            Files.delete(outputFile);
                        }

                    } catch (final IOException e) {
                        LOGGER.error("Error creating temporary outputfile");
                    }
                } // while not done
            } finally {
                Files.delete(payloadFile);
            }
        } catch (

        final IOException e) {
            throw new RuntimeException("I/O error executing creating payload file", e);
        }

        LOGGER.trace("Client finished");
    }

    private void writeLatencyLog(final InetAddress address, final long requestStart, final long requestEnd)
            throws IOException {
        latencyLog.printRecord(System.currentTimeMillis(), "request_processed", address.getHostAddress(), requestStart,
                requestEnd, requestEnd - requestStart);
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
            LOGGER.error("Error writing request status {} {} {}", timestamp, success, message, e);
        }
    }

    private String generatePayload() {
        return RandomStringUtils.randomAlphanumeric(getPayloadSize());
    }
}
