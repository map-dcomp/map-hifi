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
package com.bbn.map.hifi.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Utilities for the simulated applications.
 * 
 * @author jschewe
 *
 */
public final class SimAppUtils {

    /**
     * Port that the fake load server listens on.
     */
    public static final int FAKE_LOAD_SERVER_PORT = 7000;

    private SimAppUtils() {
    }

    /**
     * Name of directory that the host will put information in for the
     * container.
     */
    public static final String HOST_CONTAINER_DATA_LOCATION = "instance_data";

    /**
     * Name of the file written out that specifies the service running in a
     * container.
     */
    public static final String SERVICE_FILENAME = "service.json";

    /**
     * Standard header for request status written by client applications.
     */
    public static final String[] CLIENT_REQUEST_STATUS_HEADER = { "timestamp", "address", "success", "message" };

    /**
     * Standard header for processing latency CSV file written by applications.
     */
    public static final String[] SERVER_LATENCY_CSV_HEADER = { "timestamp", "event", "client", "time_received",
            "time_ack_sent", "latency" };

    /**
     * Folder name for application metrics data.
     */
    public static final String APP_METRICS_FOLDER = "app_metrics_data";

    @SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME", justification = "Paths inside containers are absolute")
    @Nonnull
    private static Path containerRoot() {
        return Paths.get("/");
    }

    /**
     * Path to the information passed into the container.
     * 
     * @see #HOST_CONTAINER_DATA_LOCATION
     */
    public static final Path CONTAINER_INSTANCE_PATH = containerRoot()
            .resolve(SimAppUtils.HOST_CONTAINER_DATA_LOCATION);

    /**
     * Path to the application metrics inside a container.
     * 
     * @see #APP_METRICS_FOLDER
     */
    public static final Path CONTAINER_APP_METRICS_PATH = containerRoot().resolve(APP_METRICS_FOLDER);

    /**
     * Header used in {@link #getActiveConnectionCountPath()} for the column
     * with the value of the number of active connections.
     */
    public static final String ACTIVE_CONNECTION_COUNT_HEADER = "active_connection_count";

    /**
     * Name of the file that the active connection count information is written
     * to.
     */
    public static final String ACTIVE_CONNECTION_COUNT_FILENAME = "active_connections.csv";

    /**
     * Name of the file that the processing latency information is written to.
     */
    public static final String LATENCY_LOG_FILENAME = "processing_latency.csv";

    /**
     * Name of the file that the request status information is written to.
     */
    public static final String REQUEST_STATUS_FILENAME = "request_status.csv";

    /**
     * @return where to write information about active connections inside an
     *         application container.
     */
    @SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME", justification = "Path to write the active connections file in the container is absolute")
    public static Path getActiveConnectionCountPath() {
        return CONTAINER_APP_METRICS_PATH.resolve(ACTIVE_CONNECTION_COUNT_FILENAME);
    }

    /**
     * Name of the file that the failed request information is written to.
     */
    public static final String FAILED_REQUESTS_FILENAME = "failed_requests.csv";

    /**
     * Header used in {@link #getFailedRequestsPath()} for the source ip.
     */
    public static final String FAILED_REQUESTS_SOURCE_IP_HEADER = "source ip";

    /**
     * Header used in {@link #getFailedRequestsPath()} for the network end time.
     */
    public static final String FAILED_REQUESTS_NETWORK_END_TIME_HEADER = "network end time";

    /**
     * Header used in {@link #getFailedRequestsPath()} for the server end time.
     */
    public static final String FAILED_REQUESTS_SERVER_END_TIME_HEADER = "server end time";

    /**
     * Header used in {@link #getFailedRequestsPath()} for the server load.
     */
    public static final String FAILED_REQUESTS_SERVER_LOAD_HEADER = "server load";

    /**
     * Header used in {@link #getFailedRequestsPath()} for the network load.
     */
    public static final String FAILED_REQUESTS_NETWORK_LOAD_HEADER = "network load";

    /**
     * @return where to write information about failed requests inside an
     *         application container.
     */
    @SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME", justification = "Path to write the failed requests file in the container is absolute")
    public static Path getFailedRequestsPath() {
        return CONTAINER_APP_METRICS_PATH.resolve(FAILED_REQUESTS_FILENAME);
    }

    // these values should be large enough that a retry occurs after the
    // negative cache expires
    private static final long UNKNOWN_HOST_RETRY_MIN_DELAY_MS = 1010;
    private static final long UNKNOWN_HOST_RETRY_MAX_DELAY_MS = 1500;

    /**
     * @return milliseconds that a client should wait before trying a host again
     *         due to a host not found exception
     */
    public static long getClientRetryDelay() {
        return getRetryDelay(UNKNOWN_HOST_RETRY_MIN_DELAY_MS, UNKNOWN_HOST_RETRY_MAX_DELAY_MS);
    }

    /**
     * Generates a random delay for the purpose of waiting before retrying a
     * call or request. The min and max delays should be sufficiently large to
     * ensure that any negative cached entry for this request has expired.
     * Otherwise, the retries may be useless.
     * 
     * @param minDelay
     *            the minimum delay in milliseconds
     * @param maxDelay
     *            the minimum delay in milliseconds
     * @return milliseconds before making another attempt
     */
    public static long getRetryDelay(long minDelay, long maxDelay) {
        return Math.abs(ThreadLocalRandom.current().nextLong() / 2) % (maxDelay - minDelay) + minDelay;
    }
}
