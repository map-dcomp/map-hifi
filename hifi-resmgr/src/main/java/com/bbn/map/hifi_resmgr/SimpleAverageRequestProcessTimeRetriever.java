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
package com.bbn.map.hifi_resmgr;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.hifi.util.ContinuationReader;
import com.bbn.map.hifi.util.SimAppUtils;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.google.common.util.concurrent.AtomicDouble;

/**
 * Implementation of {@link AverageRequestProcessTimeRetriever} for getting
 * average processing time from a container.
 * 
 * @author awald
 *
 */
/* package */ class SimpleAverageRequestProcessTimeRetriever extends Thread
        implements AverageRequestProcessTimeRetriever {

    private static final Logger LOGGER = LogManager.getLogger(SimpleAverageRequestProcessTimeRetriever.class);

    private static final int AVERAGE_REQUEST_PROCESSING_TIME_FILE_LINES = 20;

    private static final String LATENCY_HEADER_LABEL = "latency";

    private AtomicDouble mostRecentValue = new AtomicDouble(Double.NaN);

    private final NodeIdentifier containerIdentifier;
    private final Path hostMountContainerAppMetricsFolder;

    /**
     * 
     * @param containerIdentifier
     *            which container is being monitored
     * @param hostMountContainerAppMetricsFolder
     *            the Path where container metrics will be found
     */
    /* package */ SimpleAverageRequestProcessTimeRetriever(final NodeIdentifier containerIdentifier,
            final Path hostMountContainerAppMetricsFolder) {
        super("Average processing time for " + containerIdentifier.getName());

        this.containerIdentifier = containerIdentifier;
        this.hostMountContainerAppMetricsFolder = hostMountContainerAppMetricsFolder;
    }

    @Override
    public double getAverageProcessingTime() {
        return mostRecentValue.doubleValue();
    }

    private final AtomicBoolean done = new AtomicBoolean(false);

    /**
     * Stop reading the CSV file in preparation for shutdown.
     */
    public void stopReading() {
        done.set(true);
        this.interrupt();
    }

    private static final Duration WAIT_FOR_FILE_POLL = Duration.ofSeconds(1);

    @Override
    public void run() {
        final Path latencyFile = hostMountContainerAppMetricsFolder.resolve(SimAppUtils.LATENCY_LOG_FILENAME);
        while (!done.get() && !Files.exists(latencyFile)) {
            LOGGER.debug("Waiting for {} to appear", latencyFile);

            try {
                Thread.sleep(WAIT_FOR_FILE_POLL.toMillis());
            } catch (final InterruptedException e) {
                LOGGER.debug("Interrupted waiting for file to appear", e);
            }
        }

        LOGGER.debug("{} found, opening", latencyFile);
        final Queue<Long> latencies = new ArrayDeque<>(AVERAGE_REQUEST_PROCESSING_TIME_FILE_LINES);
        try (CSVParser parser = new CSVParser(
                new ContinuationReader(Files.newBufferedReader(latencyFile, Charset.defaultCharset()),
                        WAIT_FOR_FILE_POLL, done),
                CSVFormat.EXCEL.withHeader().withSkipHeaderRecord())) {
            parser.forEach(record -> {
                LOGGER.trace("Read record {}", record);

                if (record.isSet(LATENCY_HEADER_LABEL)) {
                    final String latencyString = record.get(LATENCY_HEADER_LABEL);

                    try {
                        final long latency = Long.parseLong(latencyString);
                        latencies.add(latency);

                        LOGGER.trace("Got latency value {}", latency);

                        processLatencies(latencies);

                    } catch (final NumberFormatException e) {
                        LOGGER.warn("Failed to parse latency value: {}", latencyString);
                    }
                } else {
                    LOGGER.warn("Latency value not set in '{}'", record.toString());
                }
            });

        } catch (final IOException e) {
            LOGGER.error("Error reading latency file, exiting", e);
        }

    }

    private void processLatencies(final Queue<Long> latencies) {
        while (latencies.size() > AVERAGE_REQUEST_PROCESSING_TIME_FILE_LINES) {
            latencies.remove();
        }

        final long totalLatency = latencies.stream().mapToLong(Long::longValue).sum();

        final int count = latencies.size();
        final double averageLatency = totalLatency * 1.0 / count;
        LOGGER.debug("Average processing latency computed for container '{}': {} / {} = {}", containerIdentifier,
                totalLatency, count, averageLatency);

        mostRecentValue.set(averageLatency);
    }

}