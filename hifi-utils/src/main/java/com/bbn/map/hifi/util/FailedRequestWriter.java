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
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.simulator.ClientLoad;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;

/**
 * Used to write out failed client request information.
 * 
 * @author jschewe
 *
 */
public class FailedRequestWriter {

    private static final Logger LOGGER = LogManager.getLogger(FailedRequestWriter.class);

    private final Object lock = new Object();

    private final CSVPrinter writer;

    /**
     * 
     * @throws IOException
     *             if there is a problem opening the output file or writing the
     *             header
     */
    public FailedRequestWriter() throws IOException {
        final Path outputPath = SimAppUtils.getFailedRequestsPath();

        writer = new CSVPrinter(Files.newBufferedWriter(outputPath), CSVFormat.EXCEL);

        // header
        writer.printRecord(SimAppUtils.FAILED_REQUESTS_SOURCE_IP_HEADER,
                SimAppUtils.FAILED_REQUESTS_SERVER_END_TIME_HEADER, SimAppUtils.FAILED_REQUESTS_SERVER_LOAD_HEADER,
                SimAppUtils.FAILED_REQUESTS_NETWORK_END_TIME_HEADER, SimAppUtils.FAILED_REQUESTS_NETWORK_LOAD_HEADER);

    }

    /**
     * Write out the request and flush the output file.
     * 
     * @param request
     *            the request that failed
     * @param serverEndTime
     *            the expected end time of the server load if it did not fail
     * @param networkEndTime
     *            the expected end time of the network load if it did not fail
     * @param clientHostAddress
     *            the address of the client
     */
    public void writeFailedRequest(@Nonnull final ClientLoad request,
            @Nonnull final String clientHostAddress,
            final long serverEndTime,
            final long networkEndTime) {

        final String serverLoad = request.getNodeLoad().entrySet().stream() //
                .map(FailedRequestWriter::nodeAttributeEntryToString) //
                .collect(Collectors.joining(";"));

        final String networkLoad = request.getNetworkLoadAsAttribute().entrySet().stream() //
                .map(FailedRequestWriter::linkAttributeEntryToString) //
                .collect(Collectors.joining(";"));

        try {
            synchronized (lock) {
                writer.printRecord(clientHostAddress, serverEndTime, serverLoad, networkEndTime, networkLoad);
                writer.flush();
            }
        } catch (final IOException e) {
            LOGGER.error("Error writing failed request {}", request, e);
        }

    }

    private static String nodeAttributeEntryToString(final Map.Entry<NodeAttribute, Double> entry) {
        return entry.getKey().getName() + ":" + entry.getValue();
    }

    private static String linkAttributeEntryToString(final Map.Entry<LinkAttribute, Double> entry) {
        return entry.getKey().getName() + ":" + entry.getValue();
    }
}
