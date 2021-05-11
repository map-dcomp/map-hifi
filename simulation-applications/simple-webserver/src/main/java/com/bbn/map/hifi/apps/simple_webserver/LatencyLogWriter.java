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
package com.bbn.map.hifi.apps.simple_webserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Queue up log objects and then write them out on a separate thread to keep
 * from slowing down the web processing.
 * 
 * @author jschewe
 *
 */
/* package */ class LatencyLogWriter extends Thread {

    private static final Logger LOGGER = LogManager.getLogger(LatencyLogWriter.class);

    // CHECKSTYLE:OFF value class
    private static final class LogEntry {
        public LogEntry(final String address, final long requestStart, final long requestEnd) {
            this.address = address;
            this.requestStart = requestStart;
            this.requestEnd = requestEnd;
        }

        public final String address;
        public final long requestStart;
        public final long requestEnd;
    }
    // CHECKSTYLE:ON

    private static final String[] CSV_HEADER = { "timestamp", "event", "client", "time_received", "time_finished",
            "latency" };

    private final CSVPrinter latencyLogger;

    private final LinkedBlockingQueue<LogEntry> logQueue = new LinkedBlockingQueue<>();

    private final AtomicBoolean allowNewRecords = new AtomicBoolean(true);

    /**
     * 
     * @param latencyFile
     *            where to write latency information
     * @throws IOException
     *             if there is an error opening the latency file
     */
    /* package */ LatencyLogWriter(final Path latencyFile) throws IOException {
        super("Log processor for " + latencyFile.toString());

        LOGGER.info("Writing latency information to {}", latencyFile);
        latencyLogger = new CSVPrinter(Files.newBufferedWriter(latencyFile), CSVFormat.EXCEL.withHeader(CSV_HEADER));

        // make sure to clear the queue on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(this::finishQueue));
    }

    /**
     * 
     * @param address
     *            the client address
     * @param requestStart
     *            the start of processing the request
     * @param requestEnd
     *            the end of processing the request
     * @see System#currentTimeMillis()
     */
    public void enqueueLogEntry(final String address, final long requestStart, final long requestEnd) {
        if (!allowNewRecords.get()) {
            throw new IllegalStateException("Latency writer is not accepting new records");
        }

        final LogEntry entry = new LogEntry(address, requestStart, requestEnd);
        logQueue.add(entry);
    }

    @Override
    public void run() {
        try {
            while (allowNewRecords.get()) {
                final LogEntry entry = logQueue.take();
                writeLatencyLog(entry);
            }
            LOGGER.info("Finished processing log entries. interrupted: {}", isInterrupted());
            finishQueue();
        } catch (final InterruptedException e) {
            LOGGER.warn("Interrupted while processing log queue, assuming time to shutdown. Queue has {} elements",
                    logQueue.size(), e);
            allowNewRecords.set(false);
        }
    }

    private void finishQueue() {
        allowNewRecords.set(false);
        LOGGER.info("Starting processing remaining log entries");
        final Collection<LogEntry> finalElements = new LinkedList<>();
        logQueue.drainTo(finalElements);
        LOGGER.info("Found {} log entries to process", finalElements.size());
        finalElements.stream().forEach(this::writeLatencyLog);
        LOGGER.info("Finished processing remaining log entries");
    }

    private void writeLatencyLog(final LogEntry entry) {
        try {
            latencyLogger.printRecord(entry.requestEnd, "request_processed", entry.address, entry.requestStart,
                    entry.requestEnd, entry.requestEnd - entry.requestStart);
            latencyLogger.flush();
        } catch (final IOException e) {
            LOGGER.error("Unable to write log entry ", e);
        }
    }

}
