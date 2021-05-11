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
package com.bbn.map.hifi.background;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.hifi.util.IdentifierUtils;
import com.bbn.map.hifi.util.ProcessExecutor;
import com.bbn.map.simulator.BackgroundNetworkLoad;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.google.common.collect.ImmutableMap;

/**
 * Execute iperf as a client for background traffic. If the logger value is set
 * to trace, then all output from iperf will be reported in the logs.
 * 
 * @author jschewe
 *
 */
/* package */ class IperfClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(IperfClient.class);

    private final ProcessExecutor rxExecutor;
    private final ProcessExecutor txExecutor;

    private static String getLoggerName(final BackgroundNetworkLoad request, final String direction) {
        return String.format("%s.%d_%d_%s", IperfClient.class.getName(), request.getStartTime(),
                request.getNetworkDuration(), direction);
    }

    private static Logger getRxLogger(final BackgroundNetworkLoad request) {
        return LoggerFactory.getLogger(getLoggerName(request, "RX"));
    }

    private static Logger getTxLogger(final BackgroundNetworkLoad request) {
        return LoggerFactory.getLogger(getLoggerName(request, "TX"));
    }

    /**
     * 
     * @param request
     *            the request defining the ports to listen on
     */
    /* package */ IperfClient(@Nonnull final BackgroundNetworkLoad request) {
        Objects.requireNonNull(request);

        final ProcessBuilder rxBuilder = createRxProcessBuilder(request);
        if (null == rxBuilder) {
            this.rxExecutor = null;
        } else {
            this.rxExecutor = new ProcessExecutor(rxBuilder, getRxLogger(request));
        }

        final ProcessBuilder txBuilder = createTxProcessBuilder(request);
        if (null == txBuilder) {
            this.txExecutor = null;
        } else {
            this.txExecutor = new ProcessExecutor(txBuilder, getTxLogger(request));
        }
    }

    private static ProcessBuilder createRxProcessBuilder(final BackgroundNetworkLoad request) {
        return createProcessBuilder(request, false);
    }

    private static ProcessBuilder createTxProcessBuilder(final BackgroundNetworkLoad request) {
        return createProcessBuilder(request, true);
    }

    private static final long MILLISECONDS_PER_SECOND = 1000;

    private static long millisecondsToSeconds(final long ms) {
        return ms / MILLISECONDS_PER_SECOND;
    }

    private static ProcessBuilder createProcessBuilder(final BackgroundNetworkLoad request, final boolean tx) {
        final int port = tx ? request.getTxPort() : request.getRxPort();
        if (port < 0) {
            throw new IllegalArgumentException("Port number (" + port + ") must be greater than or equal to 0");
        }

        final List<String> command = new LinkedList<>();

        final NodeIdentifier server = IdentifierUtils.getNodeIdentifier(request.getServer());

        final ImmutableMap<LinkAttribute, Double> networkLoad = request.getNetworkLoad();
        final double megabitsPerSecond;
        if (tx) {
            if (networkLoad.containsKey(LinkAttribute.DATARATE_TX)) {
                megabitsPerSecond = networkLoad.get(LinkAttribute.DATARATE_TX);
            } else {
                return null;
            }
        } else {
            if (networkLoad.containsKey(LinkAttribute.DATARATE_RX)) {
                megabitsPerSecond = networkLoad.get(LinkAttribute.DATARATE_RX);
            } else {
                return null;
            }
        }

        command.add("iperf3");

        command.add("-c");
        command.add(server.getName());

        command.add("-p");
        command.add(String.valueOf(port));

        command.add("-t");
        command.add(String.valueOf(millisecondsToSeconds(request.getNetworkDuration())));

        command.add("-b");
        command.add(megabitsPerSecond + "M");

        if (tx) {
            command.add("-R");
        }
        if (!LOGGER.isTraceEnabled()) {
            command.add("--logfile");
            command.add("/dev/null");
        }

        return new ProcessBuilder(command);
    }

    /**
     * Start generating traffic.
     */
    public void startExecution() {
        if (null != rxExecutor) {
            rxExecutor.startProcess();
        }
        if (null != txExecutor) {
            txExecutor.startProcess();
        }
    }

    /**
     * Stop generating traffic if not already stopped.
     */
    public void shutdown() {
        if (null != rxExecutor) {
            rxExecutor.stopProcess();
        }
        if (null != txExecutor) {
            txExecutor.stopProcess();
        }
    }
}
