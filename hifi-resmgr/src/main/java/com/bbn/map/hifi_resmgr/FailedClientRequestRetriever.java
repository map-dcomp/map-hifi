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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.hifi.util.ContinuationReader;
import com.bbn.map.hifi.util.IdentifierUtils;
import com.bbn.map.hifi.util.SimAppUtils;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;

/**
 * Read failed client request messages from a container.
 * 
 * @author jschewe
 *
 */
/* package */ class FailedClientRequestRetriever extends Thread {

    private static final Logger LOGGER = LogManager.getLogger(FailedClientRequestRetriever.class);

    private final Path hostMountContainerAppMetricsFolder;

    private final ResourceManager<?> resourceManager;

    private final NodeIdentifier containerIdentifier;

    /**
     * 
     * @param resourceManager
     *            the resource manager to notify of failed requests
     * @param containerIdentifier
     *            which container is being monitored
     * @param hostMountContainerAppMetricsFolder
     *            the Path where container metrics will be found
     */
    /* package */ FailedClientRequestRetriever(final ResourceManager<?> resourceManager,
            final NodeIdentifier containerIdentifier,
            final Path hostMountContainerAppMetricsFolder) {
        super("Failed client request retriever for " + containerIdentifier.getName());
        this.resourceManager = resourceManager;
        this.containerIdentifier = containerIdentifier;

        this.hostMountContainerAppMetricsFolder = hostMountContainerAppMetricsFolder;
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
        final Path file = hostMountContainerAppMetricsFolder.resolve(SimAppUtils.FAILED_REQUESTS_FILENAME);
        while (!done.get() && !Files.exists(file)) {
            LOGGER.debug("Waiting for {} to appear", file);

            try {
                Thread.sleep(WAIT_FOR_FILE_POLL.toMillis());
            } catch (final InterruptedException e) {
                LOGGER.debug("Interrupted waiting for file to appear", e);
            }
        }

        LOGGER.debug("{} found, opening", file);
        try (CSVParser parser = new CSVParser(
                new ContinuationReader(Files.newBufferedReader(file, Charset.defaultCharset()), WAIT_FOR_FILE_POLL,
                        done),
                CSVFormat.EXCEL.withHeader().withSkipHeaderRecord())) {
            parser.forEach(record -> {
                LOGGER.trace("Read record {}", record);

                if (record.isSet(SimAppUtils.FAILED_REQUESTS_SOURCE_IP_HEADER) //
                        && record.isSet(SimAppUtils.FAILED_REQUESTS_SERVER_END_TIME_HEADER) //
                        && record.isSet(SimAppUtils.FAILED_REQUESTS_SERVER_LOAD_HEADER) //
                        && record.isSet(SimAppUtils.FAILED_REQUESTS_NETWORK_END_TIME_HEADER) //
                        && record.isSet(SimAppUtils.FAILED_REQUESTS_NETWORK_LOAD_HEADER)) {
                    final String sourceIp = record.get(SimAppUtils.FAILED_REQUESTS_SOURCE_IP_HEADER);
                    final String serverEndTimeStr = record.get(SimAppUtils.FAILED_REQUESTS_SERVER_END_TIME_HEADER);
                    final String networkEndTimeStr = record.get(SimAppUtils.FAILED_REQUESTS_NETWORK_END_TIME_HEADER);
                    final String serverLoadStr = record.get(SimAppUtils.FAILED_REQUESTS_SERVER_LOAD_HEADER);
                    final String networkLoadStr = record.get(SimAppUtils.FAILED_REQUESTS_NETWORK_LOAD_HEADER);

                    final NodeIdentifier source = IdentifierUtils.getNodeIdentifier(sourceIp);
                    try {
                        final long serverEndTime = Long.parseLong(serverEndTimeStr);
                        final long networkEndTime = Long.parseLong(networkEndTimeStr);
                        final Map<NodeAttribute, Double> serverLoad = parseServerLoad(serverLoadStr);
                        final Map<LinkAttribute, Double> networkLoad = parseNetworkLoad(networkLoadStr);

                        resourceManager.addFailedRequest(source, containerIdentifier, serverEndTime, serverLoad,
                                networkEndTime, networkLoad);

                        LOGGER.debug("run: resourceManager.addFailedRequest source: {}, containerIdentifier: {}, "
                                + "serverEndTime: {}, serverLoad: {}, networkEndTime: {}, networkLoad: {}",
                                source, containerIdentifier, serverEndTime, serverLoad, networkEndTime, networkLoad);
                    } catch (final NumberFormatException e) {
                        LOGGER.warn("Failed to parse end time: {} or {}", serverEndTimeStr, networkEndTimeStr, e);
                    }
                } else {
                    LOGGER.warn("Malformed line '{}'", record.toString());
                }
            });

        } catch (final IOException e) {
            LOGGER.error("Error reading failed client request file, exiting", e);
        }

    }

    private static Map<NodeAttribute, Double> parseServerLoad(final String str) {
        final Map<NodeAttribute, Double> serverLoad = new HashMap<>();
        final String[] pairs = str.split(";");
        for (final String pair : pairs) {
            final String[] pieces = pair.split(":");
            if (pieces.length != 2) {
                LOGGER.error("Error parsing server load attribute pair '{}', ignoring", str);
            } else {
                try {
                    final NodeAttribute attr = new NodeAttribute(pieces[0]);
                    final double value = Double.parseDouble(pieces[1]);
                    serverLoad.merge(attr, value, Double::sum);
                } catch (final NumberFormatException e) {
                    LOGGER.error("Error parsing value in load attribute pair '{}'", str, e);
                }
            }
        }

        return serverLoad;
    }

    private static Map<LinkAttribute, Double> parseNetworkLoad(final String str) {
        final Map<LinkAttribute, Double> serverLoad = new HashMap<>();
        final String[] pairs = str.split(";");
        for (final String pair : pairs) {
            final String[] pieces = pair.split(":");
            if (pieces.length != 2) {
                LOGGER.error("Error parsing server link attribute pair '{}', ignoring", str);
            } else {
                try {
                    final LinkAttribute attr = new LinkAttribute(pieces[0]);
                    final double value = Double.parseDouble(pieces[1]);
                    serverLoad.merge(attr, value, Double::sum);
                } catch (final NumberFormatException e) {
                    LOGGER.error("Error parsing value in link attribute pair '{}'", str, e);
                }
            }
        }

        return serverLoad;
    }

}
