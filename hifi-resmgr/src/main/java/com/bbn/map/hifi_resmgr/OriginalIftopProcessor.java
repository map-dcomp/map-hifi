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
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;

/**
 * Spawn an iftop process and turn the output into {@link NICTrafficDataFrame}
 * objects. Works with a stock iftop binary.
 * 
 * @author jschewe
 * @author awald
 *
 */
public class OriginalIftopProcessor extends BaseIftopProcessor {

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
    private static final String[] IFTOP_COMMAND = { "sudo", "nice", "-n", NICE_PRIORITY_PLACEHOLDER, "iftop", "-n",
            "-t", "-N", "-P", "-L", String.valueOf(MAX_IFTOP_FLOWS), "-i", NETWORK_INTERFACE_PLACEHOLDER };

    private Process iftopProcess;
    private final NetworkInterface nic;
    private final Timer timer = new Timer();

    private final AtomicBoolean running = new AtomicBoolean();

    @Override
    public void stopProcessing() {
        running.set(false);
    }

    /**
     * 
     * @param nic
     *            the interface to monitor
     */
    public OriginalIftopProcessor(final NetworkInterface nic) {
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
     * Parse an ip address and port. If the port is not in the string, return 0
     * for the port.
     * 
     * @param str
     *            the string to be parsed, expecting ip:port.
     * @return (address, port)
     */
    private static Pair<String, Integer> parseAddressAndPort(final String str) {
        final int indexOfOpenBracket = str.indexOf('[');
        final int indexOfCloseBracket = str.indexOf(']');

        if ("::".equals(str)) {
            // Java should handle this properly, but for some reason it doesn't
            // always resolve properly
            // :: is IPv6 for localhost
            final InetAddress localLoopback = InetAddress.getLoopbackAddress();
            return Pair.of(localLoopback.getHostAddress(), UNKNOWN_PORT);
        } else if (-1 != indexOfOpenBracket && -1 != indexOfCloseBracket) {
            // [IPv6]:port
            // [2607:f8b0:4009:810::201e]:443
            final String ip = str.substring(indexOfOpenBracket + 1, indexOfCloseBracket);
            final String portStr = str.substring(indexOfCloseBracket + 2);
            try {
                final int port = Integer.parseInt(portStr);
                return Pair.of(ip, port);
            } catch (final NumberFormatException e) {
                LoggerFactory.getLogger(IftopProcessor.class)
                        .warn("Got odd value from iftop for IP and port (invalid number): '{}'", str);
                return Pair.of(ip, UNKNOWN_PORT);
            }
        } else if (-1 != indexOfOpenBracket) {
            // [IPv6:port
            // [2601:444:47f:c71e:617a:817:f3be:8:56640
            final String ip = str.substring(indexOfOpenBracket + 1);

            final int lastColon = str.lastIndexOf(':');
            final String portStr = str.substring(lastColon + 1);
            try {
                final int port = Integer.parseInt(portStr);
                return Pair.of(ip, port);
            } catch (final NumberFormatException e) {
                LoggerFactory.getLogger(IftopProcessor.class)
                        .warn("Got odd value from iftop for IPv6 and port (invalid number): '{}'", str);
                return Pair.of(ip, UNKNOWN_PORT);
            }
        } else if (str.chars().filter(c -> c == ':').count() > 1) {
            // IPv6 address and no known port
            return Pair.of(str, UNKNOWN_PORT);
        } else if (str.contains(":")) {
            // IPv4
            // 73.37.165.179:39430
            final String[] tokens = str.split(":");
            if (tokens.length < 2) {
                LoggerFactory.getLogger(IftopProcessor.class)
                        .warn("Got odd value from iftop for IP and port (too few tokens): '{}'", str);
                return Pair.of(str, UNKNOWN_PORT);
            } else {
                final String ip = tokens[0];
                try {
                    final int port = Integer.parseInt(tokens[1]);
                    return Pair.of(ip, port);
                } catch (final NumberFormatException e) {
                    LoggerFactory.getLogger(IftopProcessor.class)
                            .warn("Got odd value from iftop for IP and port (invalid number): '{}'", str);
                    return Pair.of(ip, UNKNOWN_PORT);
                }
            }
        } else {
            return Pair.of(str, UNKNOWN_PORT);
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
    public static List<IftopTrafficData> processIftopOutputFrame(@Nonnull final NetworkInterface nic,
            @Nonnull final List<String> iftopOutLines) {
        final List<IftopTrafficData> frame = new LinkedList<>();

        // if (true)
        // return frame;

        int frameSection = 0;

        // int last2sColumn;

        // local is sending to remote
        boolean parsedSent = false;
        String localIP = null;
        int localPort = 0;
        String remoteIP = null;
        int remotePort = 0;
        long last2sBitsSent = -1;
        long last2sBitsReceived = -1;

        for (String line : iftopOutLines) {
            LoggerFactory.getLogger(IftopProcessor.class).trace("Parse line: " + line);

            try {
                // check for section divider
                if (line.contains("-----------------")) {
                    frameSection++;
                } else {
                    switch (frameSection) {
                    // header
                    case 0:
                        break;

                    // IP Address Specific network usage section
                    case 1:
                        /*
                         * Using indexOf and StringTokenizer here rather than
                         * split as it's been benchmarked by others to be much
                         * faster.
                         * http://demeranville.com/battle-of-the-tokenizers-
                         * delimited-text-parser-performance/
                         */
                        LoggerFactory.getLogger(IftopProcessor.class).debug("Parse line for NIC '{}': {}", nic, line);

                        final String sentDelimiter = "=>";
                        final String receivedDelimiter = "<=";
                        final boolean sent = line.contains(sentDelimiter);
                        final boolean received = line.contains(receivedDelimiter);

                        if (sent) {
                            // 1 172.30.0.65:22 => 4.44Kb 4.44Kb 4.44Kb 1.11KB
                            final int sentIndex = line.indexOf(sentDelimiter);

                            final String left = line.substring(0, sentIndex);
                            final String right = line.substring(sentIndex + sentDelimiter.length());

                            final StringTokenizer leftTokens = new StringTokenizer(left);
                            leftTokens.nextToken(); // ignore line number
                            final String addrAndPort = leftTokens.nextToken();

                            final Pair<String, Integer> localResult = parseAddressAndPort(addrAndPort);
                            localIP = localResult.getLeft();
                            localPort = localResult.getRight();

                            final StringTokenizer rightTokens = new StringTokenizer(right);
                            final String dataAmount = rightTokens.nextToken();
                            last2sBitsSent = dataAmountStringToBits(dataAmount);

                            parsedSent = true;
                        } else if (received) {
                            // 192.168.254.21:43210 <= 2.05Kb 2.05Kb 2.05Kb 524B
                            final int receivedIndex = line.indexOf(receivedDelimiter);

                            final String left = line.substring(0, receivedIndex);
                            final String right = line.substring(receivedIndex + receivedDelimiter.length());

                            final StringTokenizer leftTokens = new StringTokenizer(left);
                            final String addrAndPort = leftTokens.nextToken();

                            final StringTokenizer rightTokens = new StringTokenizer(right);
                            final String dataAmount = rightTokens.nextToken();

                            final Pair<String, Integer> remoteResult = parseAddressAndPort(addrAndPort);
                            remoteIP = remoteResult.getLeft();
                            remotePort = remoteResult.getRight();

                            last2sBitsReceived = dataAmountStringToBits(dataAmount);

                            if (parsedSent) {
                                // add data to frame, skip if missing data from
                                // a parsing error
                                LoggerFactory.getLogger(IftopProcessor.class).debug(
                                        "Add IP Usage Data: \nLocal IP: {} Remote IP: {} Last 2s Bits Sent: {} Last 2s bits received: {}",
                                        localIP, remoteIP, last2sBitsSent, last2sBitsReceived);
                                final IftopTrafficData data = new IftopTrafficData(localIP, localPort, remoteIP,
                                        remotePort, last2sBitsSent, last2sBitsReceived, nic);
                                frame.add(data);
                            }

                            localIP = null;
                            remoteIP = null;
                            last2sBitsSent = -1;
                            last2sBitsReceived = -1;
                            parsedSent = false;
                        }

                        break;

                    default:
                        break;
                    }
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

    private static final long KB_TO_BYTES = 1024L;
    private static final ImmutableMap<String, Long> DATA_UNIT_TO_BITS = ImmutableMap.of("b", 1L, //
            "Kb", KB_TO_BYTES, //
            "Mb", KB_TO_BYTES * KB_TO_BYTES, //
            "Gb", KB_TO_BYTES * KB_TO_BYTES * KB_TO_BYTES);

    /**
     * Parses a string representation of an amount of a certain unit of data.
     * 
     * @param dataAmount
     *            a number followed by a unit of data: 'b' for bits 'Kb' for
     *            Kilobits 'Mb' for Megabits 'Gb' for Gigabits
     * @return the number of bits that the string represents
     */
    private static long dataAmountStringToBits(final String dataAmount) {
        long multiplier = 0;
        float number = 0;
        final String unit = dataAmount.replaceFirst("^[0-9]*\\.[0-9]+|[0-9]+", "");

        for (Map.Entry<String, Long> e : DATA_UNIT_TO_BITS.entrySet()) {
            final String mapUnit = e.getKey();
            if (unit.equals(mapUnit)) {
                number = Float.parseFloat(dataAmount.substring(0, dataAmount.length() - mapUnit.length()));
                multiplier = e.getValue();
                break;
            }
        }

        return (long) (number * multiplier);
    }

}
