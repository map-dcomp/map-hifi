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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.LineIterator;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

/**
 * Test parsing of various iftop output.
 * 
 * @author jschewe
 *
 */
public class TestParseIftop {

    private static List<String> readLines(final InputStream stream) throws IOException {
        final List<String> lines = new LinkedList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()))) {
            try (LineIterator it = new LineIterator(reader)) {
                while (it.hasNext()) {
                    final String line = it.next();
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private static NetworkInterface getTestNetworkInterface() throws SocketException {
        final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        assertThat("No network interfaces found", interfaces, notNullValue());

        assertTrue("Empty list of network interfaces", interfaces.hasMoreElements());
        return interfaces.nextElement();
    }

    /**
     * Test parsing IPv4 addresses.
     * 
     * @throws IOException
     *             test error
     */
    @Test
    public void testIpv4() throws IOException {
        final String expectedIp1 = "73.37.165.179";
        final int expectedPort1 = 39430;

        final String expectedIp2 = "54.172.71.18";
        final int expectedPort2 = 443;

        try (InputStream stream = TestParseIftop.class.getResourceAsStream("ipv4_capture.txt")) {
            assertThat(stream, notNullValue());

            final List<String> lines = readLines(stream);

            final List<IftopTrafficData> parsedData = IftopProcessor.processIftopOutputFrame(getTestNetworkInterface(),
                    lines);
            assertThat(parsedData, notNullValue());
            assertThat(parsedData, hasSize(1));

            final IftopTrafficData data = parsedData.get(0);

            assertThat(data, MatchIpAndPort.matchIpAndPort(expectedIp1, expectedPort1));
            assertThat(data, MatchIpAndPort.matchIpAndPort(expectedIp2, expectedPort2));
        }
    }

    /**
     * Test parsing IPv6 addresses.
     * 
     * @throws IOException
     *             test error
     */
    @Test
    public void testIpv6() throws IOException {
        final String expectedIp1 = "2607:f8b0:4009:810::201e";
        final String expectedIp2 = "2607:f8b0:4009:810::200e";
        final int expectedPort = 443;

        try (InputStream stream = TestParseIftop.class.getResourceAsStream("ipv6_capture.txt")) {
            assertThat(stream, notNullValue());

            final List<String> lines = readLines(stream);

            final List<IftopTrafficData> parsedData = IftopProcessor.processIftopOutputFrame(getTestNetworkInterface(),
                    lines);
            assertThat(parsedData, notNullValue());
            assertThat(parsedData, hasSize(1));

            final IftopTrafficData data = parsedData.get(0);

            assertThat(data, MatchIpAndPort.matchIpAndPort(expectedIp1, expectedPort));
            assertThat(data, MatchIpAndPort.matchIpAndPort(expectedIp2, expectedPort));
        }
    }

    /**
     * Test parsing IPv6 addresses where one is truncated.
     * 
     * @throws IOException
     *             test error
     */
    @Test
    public void testIpv6truncated() throws IOException {
        final String expectedIp1 = "2601:444:47f:c71e:617a:817:f3be:8:56640";
        final String expectedIp2 = "2607:f8b0:4009:810::200e";
        final int expectedPort2 = 443;

        try (InputStream stream = TestParseIftop.class.getResourceAsStream("ipv6_capture_truncated.txt")) {
            assertThat(stream, notNullValue());

            final List<String> lines = readLines(stream);

            final List<IftopTrafficData> parsedData = IftopProcessor.processIftopOutputFrame(getTestNetworkInterface(),
                    lines);
            assertThat(parsedData, notNullValue());
            assertThat(parsedData, hasSize(1));

            final IftopTrafficData data = parsedData.get(0);

            final ImmutableList<String> ips = ImmutableList.of(data.getLocalIP(), data.getRemoteIP());
            assertThat(ips, hasItem(expectedIp1));
            assertThat(data, MatchIpAndPort.matchIpAndPort(expectedIp2, expectedPort2));
        }
    }

    private static final class MatchIpAndPort extends TypeSafeMatcher<IftopTrafficData> {

        /**
         * Match either local or remote against the provided data.
         * 
         * @param ip
         *            the IP to match
         * @param port
         *            the port to match
         * @return matcher for use with assertions
         */
        public static Matcher<IftopTrafficData> matchIpAndPort(final String ip, final int port) {
            return new MatchIpAndPort(ip, port);
        }

        private final String ip;
        private final int port;

        private MatchIpAndPort(final String ip, final int port) {
            this.ip = ip;
            this.port = port;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText(String.format("IP: %s and Port: %d", ip, port));

        }

        @Override
        protected boolean matchesSafely(final IftopTrafficData item) {
            if (null == item) {
                return false;
            }
            return (this.ip.equals(item.getLocalIP()) && this.port == item.getLocalPort())
                    || (this.ip.equals(item.getRemoteIP()) && this.port == item.getRemotePort());
        }

    }
}
