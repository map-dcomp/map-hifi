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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import org.apache.commons.io.LineIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.google.common.collect.Streams;

/**
 * Spawn an iftop process and turn the output into {@link NICTrafficDataFrame}
 * objects. Uses a customized iftop binary.
 * 
 * @author jschewe
 * @author awald
 *
 */
public class IftopProcessor extends BaseIftopProcessor {

    private final Logger log;

    /**
     * The value used when the port number cannot be determined.
     */
    public static final int UNKNOWN_PORT = 0;

    /**
     * the time interval in milliseconds between sending update messages to
     * request new data from the iftop processes.
     */
    private static final long IFTOP_UPDATE_INTERVAL = 2000;

    private static final String NETWORK_INTERFACE_PLACEHOLDER = "[nic]";

    private static final String NICE_PRIORITY_PLACEHOLDER = "[nice_priority]";

    /**
     * Maximum number of flows to read from iftop. If there are more flows than
     * this seen on an interface, they will be ignored.
     */
    private static final int MAX_IFTOP_FLOWS = 1_000_000;

    /**
     * Command to run iftop.
     */
    private static final String[] IFTOP_COMMAND = { "sudo", "nice", "-n", NICE_PRIORITY_PLACEHOLDER,
            "/var/lib/map/iftop", "-n", "-k", "-N", "-P", "-L", String.valueOf(MAX_IFTOP_FLOWS), "-i",
            NETWORK_INTERFACE_PLACEHOLDER };

    private Process iftopProcess;
    private final NetworkInterface nic;
    private final Timer timer = new Timer();

    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * Stop reading data from iftop.
     */
    @Override
    public void stopProcessing() {
        running.set(false);
    }

    /**
     * 
     * @param nic
     *            the interface to monitor
     */
    public IftopProcessor(final NetworkInterface nic) {
        super("Iftop processor for " + nic.getName());
        this.nic = nic;

        log = LoggerFactory.getLogger(String.format("%s.%s", this.getClass().getName(), this.nic.getName()));

        final ProcessBuilder b = new ProcessBuilder();
        b.command(generateIftopCommand(nic.getDisplayName()));

        try {
            iftopProcess = b.start();
            log.debug("Started iftop process for NIC: {}", nic.getDisplayName());
        } catch (IOException e) {
            log.error("Failed to start iftop for NIC: ", nic.getDisplayName(), e);
        }

    }

    @Override
    public void run() {
        if (null == iftopProcess) {
            log.error("iftop not running, cannot process data");
            return;
        }

        running.set(true);

        log.debug("Begin processing for iftop for: {}", nic.getDisplayName());

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(iftopProcess.getInputStream(), Charset.defaultCharset()))) {
            // log.info("After BufferedReader reader = ");

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(iftopProcess.getOutputStream(), Charset.defaultCharset()))) {
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        // send update command to iftop to prepare for next
                        // parse iteration
                        try {
                            writer.write('u');
                            writer.flush();
                        } catch (IOException e) {
                            cancel();
                            log.error("Failed to write 'u' to update iftop output", e);
                        }
                    }
                }, 0, IFTOP_UPDATE_INTERVAL);

                try (LineIterator lineIter = new LineIterator(reader)) {
                    final List<String> iftopOutLines = new LinkedList<>();

                    while (running.get() && lineIter.hasNext()) {
                        final String line = lineIter.next();
                        log.trace("IftopParseThread: read line: {}", line);

                        if (line.contains("===============================================")) {
                            final List<IftopTrafficData> frame = processIftopOutputFrame(nic, iftopOutLines);
                            log.trace("IftopParseThread: created frame: {}", frame);

                            setLastIftopFrames(frame);

                            iftopOutLines.clear();
                        } else {
                            iftopOutLines.add(line);
                        }
                    }
                } catch (IOException e) {
                    log.error("Error reading from iftop: {}", e.getMessage(), e);
                } finally {
                    log.info("Sending 'q' to iftop to ask it to stop");
                    try {
                        writer.write('q');
                        writer.flush();
                    } catch (IOException e) {
                        log.error("Failed to write 'q' to update iftop output", e);
                    }
                }

            } catch (IOException e) {
                log.error("Failed to obtain OutputStream from iftop: {}", e.getMessage(), e);
            }
        } catch (IOException e) {
            log.error("Failed to obtain InputStream from iftop: {}", e.getMessage(), e);
        } finally {
            stopIftop();
        }
    }

    /**
     * If the process doesn't die after this much time, force kill.
     */
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);

    private void stopIftop() {
        if (null != iftopProcess) {
            log.info("Killing iftop");

            iftopProcess.destroy();
            try {
                iftopProcess.waitFor(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                log.warn("Interrupted waiting for client process to exit", e);
            }
            if (iftopProcess.isAlive()) {
                log.info("Forcibly killing iftop");
                iftopProcess.destroyForcibly();
            }
            iftopProcess = null;
        }
    }

    /**
     * Process iftop data.
     * 
     * @param nic
     *            the network interface being monitored
     * @param iftopOutLines
     *            the lines from iftop
     * @return the parsed data
     */
    private static List<IftopTrafficData> processIftopOutputFrame(@Nonnull final NetworkInterface nic,
            @Nonnull final List<String> iftopOutLines) {
        final List<IftopTrafficData> frame = new LinkedList<>();

        for (String line : iftopOutLines) {
            LoggerFactory.getLogger(IftopProcessor.class).trace("Parse line: " + line);

            try {
                if (line.indexOf(';') < 0) {
                    // not a line we will parse
                    continue;
                } else {
                    /*
                     * Using indexOf and StringTokenizer here rather than split
                     * as it's been benchmarked by others to be much faster.
                     * http://demeranville.com/battle-of-the-tokenizers-
                     * delimited-text-parser-performance/
                     */
                    LoggerFactory.getLogger(IftopProcessor.class).debug("Parse line for NIC '{}': {}", nic, line);

                    final StringTokenizer tokens = new StringTokenizer(line, ";");
                    final String localIp = tokens.nextToken();
                    final int localPort = Integer.parseInt(tokens.nextToken());
                    final String remoteIp = tokens.nextToken();
                    final int remotePort = Integer.parseInt(tokens.nextToken());
                    final long sentBitsStr = Long.parseLong(tokens.nextToken());
                    final long recvBits = Long.parseLong(tokens.nextToken());

                    // add data to frame, skip if missing data from
                    // a parsing error
                    LoggerFactory.getLogger(IftopProcessor.class).debug(
                            "Add IP Usage Data: \nLocal IP: {} Remote IP: {} Last 2s Bits Sent: {} Last 2s bits received: {}",
                            localIp, remoteIp, sentBitsStr, recvBits);
                    final IftopTrafficData data = new IftopTrafficData(localIp, localPort, remoteIp, remotePort,
                            sentBitsStr, recvBits, nic);
                    frame.add(data);

                } // line to parse
            } catch (final RuntimeException e) {
                LoggerFactory.getLogger(IftopProcessor.class).error("Error parsing line '{}', skipping", line, e);
            }
        } // foreach line

        return frame;
    }

    /**
     * Generate the command to use iftop to monitor the specified network
     * interface.
     * 
     * @param nicName
     *            the network interface to monitor
     * @return the command and arguments to be executed
     */
    private static String[] generateIftopCommand(final String nicName) {
        final List<String> extraArguments = AgentConfiguration.getInstance().getExtraIftopArguments();
        final String[] command = Streams.concat(Arrays.stream(IFTOP_COMMAND), extraArguments.stream()) //
                .map(arg -> {
                    switch (arg) {
                    case NETWORK_INTERFACE_PLACEHOLDER:
                        return nicName;
                    case NICE_PRIORITY_PLACEHOLDER:
                        return String.valueOf(AgentConfiguration.getInstance().getIftopPriority());
                    default:
                        return arg;
                    }
                }) //
                .toArray(String[]::new);

        return command;
    }

}
