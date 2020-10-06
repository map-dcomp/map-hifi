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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Nonnull;

import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

/**
 * Spawn an iftop process and turn the output into {@link NICTrafficDataFrame}
 * objects.
 * 
 * @author jschewe
 * @author awald
 *
 */
public class IftopProcessor extends Thread {

    private final Logger log;

    private final Object lock = new Object();

    /**
     * The value used when the port number cannot be determined.
     */
    public static final int UNKNOWN_PORT = 0;

    /**
     * the time interval in milliseconds between sending update messages to
     * request new data from the iftop processes.
     */
    private static final long IFTOP_UPDATE_INTERVAL = 2000;

    /**
     * Command to run iftop.
     */
    private static final String[] IFTOP_COMMAND = { "sudo", "iftop", "-n", "-t", "-N", "-P", "-i", "[nic]" };

    private Process iftopProcess;
    private final NetworkInterface nic;
    private final Timer timer = new Timer();

    private List<IftopTrafficData> lastIftopFrame = null;

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

                    while (lineIter.hasNext()) {
                        final String line = lineIter.next();
                        log.trace("IftopParseThread: read line: {}", line);

                        if (line.contains("===============================================")) {
                            final List<IftopTrafficData> frame = processIftopOutputFrame(nic, iftopOutLines);
                            log.trace("IftopParseThread: created frame: {}", frame);

                            // lastIftopFrame = frame;
                            synchronized (lock) {
                                log.trace("IftopParseThread: add frame for NIC {} to lastIftopFrames.", nic);
                                lastIftopFrame = frame;
                            }

                            iftopOutLines.clear();
                        } else {
                            iftopOutLines.add(line);
                        }
                    }
                } catch (IOException e) {
                    log.error("Error reading from iftop: {}", e.getMessage(), e);
                }

            } catch (IOException e) {
                log.error("Failed to obtain OutputStream from iftop: {}", e.getMessage(), e);
            }
        } catch (IOException e) {
            log.error("Failed to obtain InputStream from iftop: {}", e.getMessage(), e);
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
        String localIP = null;
        int localPort = 0;
        String remoteIP = null;
        int remotePort = 0;
        long last2sBitsSent = -1;
        long last2sBitsReceived = -1;

        for (String line : iftopOutLines) {
            LoggerFactory.getLogger(IftopProcessor.class).trace("Parse line: " + line);

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
                    String[] temp;
                    String[] leftColumns;
                    String[] rightColumns;

                    LoggerFactory.getLogger(IftopProcessor.class).debug("Parse line for NIC '{}': {}", nic, line);
                    final boolean sent = line.contains("=>");
                    final boolean received = line.contains("<=");
                    if (sent) {
                        // 1 192.168.42.104:36888 => 13.8Kb 13.8Kb 13.8Kb 3.44KB
                        temp = line.split("=>");

                        leftColumns = ProcFileParserUtils.splitByWhiteSpace(temp[0]);
                        rightColumns = ProcFileParserUtils.splitByWhiteSpace(temp[1]);

                        final Pair<String, Integer> localResult = parseAddressAndPort(leftColumns[2]);
                        localIP = localResult.getLeft();
                        localPort = localResult.getRight();

                        last2sBitsSent = dataAmountStringToBits(rightColumns[1]);
                    } else if (received) {
                        // 192.1.101.20:443 <= 12.1Kb 12.1Kb 12.1Kb 3.04KB
                        temp = line.split("<=");
                        leftColumns = ProcFileParserUtils.splitByWhiteSpace(temp[0]);
                        rightColumns = ProcFileParserUtils.splitByWhiteSpace(temp[1]);

                        final Pair<String, Integer> remoteResult = parseAddressAndPort(leftColumns[1]);
                        remoteIP = remoteResult.getLeft();
                        remotePort = remoteResult.getRight();

                        last2sBitsReceived = dataAmountStringToBits(rightColumns[1]);

                        // add data to frame
                        LoggerFactory.getLogger(IftopProcessor.class).debug(
                                "Add IP Usage Data: \nLocal IP: {} Remote IP: {} Last 2s Bits Sent: {} Last 2s bits received: {}",
                                localIP, remoteIP, last2sBitsSent, last2sBitsReceived);
                        final IftopTrafficData data = new IftopTrafficData(localIP, localPort, remoteIP, remotePort,
                                last2sBitsSent, last2sBitsReceived, nic);
                        frame.add(data);
                        localIP = null;
                        remoteIP = null;
                        last2sBitsSent = -1;
                        last2sBitsReceived = -1;
                    }

                    break;

                default:
                    break;
                }
            }
        }

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
    public static String[] generateIftopCommand(final String nicName) {
        String[] command = new String[IFTOP_COMMAND.length];

        for (int n = 0; n < command.length; n++) {
            switch (IFTOP_COMMAND[n]) {
            case "[nic]":
                command[n] = nicName;
                break;

            default:
                command[n] = IFTOP_COMMAND[n];
                break;
            }
        }

        return command;
    }

    /**
     * @return the most recently produced frames for this NIC, may be null
     */
    public List<IftopTrafficData> getLastIftopFrames() {
        synchronized (lock) {
            final List<IftopTrafficData> frame = lastIftopFrame;
            // lastIftopFrame = null;

            log.trace("getLastIftopFrames: found frame: {}", frame);

            return frame;
        }
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
