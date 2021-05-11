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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.AgentConfiguration;

/**
 * Write the current number of active connections inside a container.
 */
public class ActiveConnectionCountWriter extends Thread {
    private static final Logger LOGGER = LogManager.getLogger(ActiveConnectionCountWriter.class);

    private final AtomicInteger activeConnectionCount;
    private final AtomicBoolean done = new AtomicBoolean(false);

    /**
     * 
     * @param activeConnectionCount
     *            where to get the current count of active connections
     */
    public ActiveConnectionCountWriter(final AtomicInteger activeConnectionCount) {
        this.activeConnectionCount = activeConnectionCount;
    }

    /**
     * Stop writing.
     */
    public void stopWriting() {
        done.set(true);
        this.interrupt();
    }

    @Override
    public void run() {
        done.set(false);

        final long writeInterval = AgentConfiguration.getInstance().getApRoundDuration().toMillis();

        final Path outputPath = SimAppUtils.getActiveConnectionCountPath();
        try (CSVPrinter writer = new CSVPrinter(Files.newBufferedWriter(outputPath), CSVFormat.EXCEL)) {

            writer.printRecord("timestamp", SimAppUtils.ACTIVE_CONNECTION_COUNT_HEADER);

            while (!done.get()) {
                final long timestamp = System.currentTimeMillis();
                final int value = activeConnectionCount.get();
                try {
                    writer.printRecord(timestamp, value);
                    writer.flush();
                } catch (final IOException e) {
                    LOGGER.warn("Error writing active connection data timestamp: {} count: {}", timestamp, value, e);
                }

                try {
                    Thread.sleep(writeInterval);
                } catch (final InterruptedException e) {
                    LOGGER.debug("Interrupted waiting to write next active connection count", e);
                }
            }
        } catch (final IOException e) {
            LOGGER.error("Unable to open active connection count file", e);
        }

    }

} // class ActiveConnectionCountWriter
