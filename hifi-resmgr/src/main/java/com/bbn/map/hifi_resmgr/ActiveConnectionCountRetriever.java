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
package com.bbn.map.hifi_resmgr;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.hifi.util.ContinuationReader;
import com.bbn.map.hifi.util.SimAppUtils;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;

/**
 * Keep track of the latest value for active connection count for a container.
 * 
 * @author jschewe
 *
 */
/* package */ class ActiveConnectionCountRetriever extends Thread {

    private static final Logger LOGGER = LogManager.getLogger(ActiveConnectionCountRetriever.class);

    private AtomicInteger mostRecentValue = new AtomicInteger(0);

    private final Path hostMountContainerAppMetricsFolder;

    /**
     * 
     * @param containerIdentifier
     *            which container is being monitored
     * @param hostMountContainerAppMetricsFolder
     *            the Path where container metrics will be found
     */
    /* package */ ActiveConnectionCountRetriever(final NodeIdentifier containerIdentifier,
            final Path hostMountContainerAppMetricsFolder) {
        super("Active count retriever for " + containerIdentifier.getName());

        this.hostMountContainerAppMetricsFolder = hostMountContainerAppMetricsFolder;
    }

    /**
     * 
     * @return the current number of active connections
     */
    public int getCurrentCount() {
        return mostRecentValue.intValue();
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
        final Path latencyFile = hostMountContainerAppMetricsFolder
                .resolve(SimAppUtils.ACTIVE_CONNECTION_COUNT_FILENAME);
        while (!done.get() && !Files.exists(latencyFile)) {
            LOGGER.debug("Waiting for {} to appear", latencyFile);

            try {
                Thread.sleep(WAIT_FOR_FILE_POLL.toMillis());
            } catch (final InterruptedException e) {
                LOGGER.debug("Interrupted waiting for file to appear", e);
            }
        }

        LOGGER.debug("{} found, opening", latencyFile);
        try (CSVParser parser = new CSVParser(
                new ContinuationReader(Files.newBufferedReader(latencyFile, Charset.defaultCharset()),
                        WAIT_FOR_FILE_POLL, done),
                CSVFormat.EXCEL.withHeader().withSkipHeaderRecord())) {
            parser.forEach(record -> {
                LOGGER.trace("Read record {}", record);

                if (record.isSet(SimAppUtils.ACTIVE_CONNECTION_COUNT_HEADER)) {
                    final String str = record.get(SimAppUtils.ACTIVE_CONNECTION_COUNT_HEADER);

                    try {
                        final int value = Integer.parseInt(str);

                        LOGGER.trace("Got active count value {}", value);

                        mostRecentValue.set(value);

                    } catch (final NumberFormatException e) {
                        LOGGER.warn("Failed to parse active count value: {}", str);
                    }
                } else {
                    LOGGER.warn("Active count value not set in '{}'", record.toString());
                }
            });

        } catch (final IOException e) {
            LOGGER.error("Error reading active count file, exiting", e);
        }

    }

}
