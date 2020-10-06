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
package com.bbn.map.hifi.apps.simple_webserver;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Servlet to send back a random string response for any get or post request.
 * 
 * @author jschewe
 *
 */
public class RandomStringServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LogManager.getLogger(RandomStringServlet.class);

    /**
     * Key used to find the latency log writer in the servlet context.
     */
    public static final String LATENCY_LOG_WRITER = "latencyLogWriter";

    /**
     * Multiple the request size by this number to get the response size.
     */
    private static final int RESPONSE_MULTIPLIER = 2;

    private static final int MAX_CHARACTERS_AT_ONCE = 4096;

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response);
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response);
    }

    private void handle(final HttpServletRequest request, final HttpServletResponse response)
            throws IOException, ServletException {
        Main.incrementActiveConnectionCount();
        try {

            final long requestStart = System.currentTimeMillis();

            response.setContentType("text/plain; charset=utf-8");

            final PrintWriter writer = response.getWriter();

            // Don't read the request, just generate something longer
            // This should avoid using lots of memory to read the request.
            final int bodyLength = request.getContentLength();

            // don't generate the whole string at once to keep the memory down
            // send a little bit at a time to keep from timing out
            final int totalResponseLength = bodyLength * RESPONSE_MULTIPLIER;
            int sent = 0;
            LOGGER.debug("Starting response. Length: {}", totalResponseLength);
            while (sent < totalResponseLength) {

                final int sendSize = Math.min(MAX_CHARACTERS_AT_ONCE, totalResponseLength - sent);
                LOGGER.trace("generating response string");
                final String responsePayload = RandomStringUtils.randomAlphanumeric(sendSize);
                LOGGER.trace("done generating response string");

                LOGGER.trace("Before writing string");
                writer.write(responsePayload);
                sent += responsePayload.length();
                LOGGER.debug("Added to response {} random characters. {} / {}", responsePayload.length(), sent,
                        totalResponseLength);
            }
            LOGGER.debug("Finished response");

            final long requestEnd = System.currentTimeMillis();
            final LatencyLogWriter latencyWriter = (LatencyLogWriter) request.getServletContext()
                    .getAttribute(LATENCY_LOG_WRITER);
            Objects.requireNonNull(latencyWriter, "latency log writer is not set in the servlet context");
            latencyWriter.enqueueLogEntry(request.getRemoteAddr(), requestStart, requestEnd);
            LOGGER.debug("Latency entry queued");

        } finally {
            Main.decrementActiveConnectionCount();
        }

    }
}
