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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Objects;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.IOUtils;

import com.bbn.map.hifi.apps.filestore.protocol.Acknowledgement;
import com.bbn.map.hifi.apps.filestore.protocol.FileStoreOperation;
import com.bbn.map.hifi.apps.filestore.server.FileStore;
import com.bbn.map.hifi.util.DnsUtils;

/**
 * Servlet to store a request in a database.
 * 
 * @author awald
 *
 */
public class DatabaseServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LogManager.getLogger(DatabaseServlet.class);

    /**
     * Key used to find the latency log writer in the servlet context.
     */
    public static final String LATENCY_LOG_WRITER = "latencyLogWriter";

    /**
     * Multiple the request size by this number to get the response size.
     */
    private static final int RESPONSE_MULTIPLIER = 2;

    private static final int MAX_CHARACTERS_AT_ONCE = 4096;

    private static final int DATABASE_PORT = FileStore.PORT;
    private static final String DATABASE_HOSTNAME = "database-publish.map.dcomp";
    private String name;

    /**
     * Create a new DatabaseServlet and log the host name being used for the
     * database.
     */
    public DatabaseServlet() {
        LOGGER.info("Configured to use database host name '{}'.", DATABASE_HOSTNAME);
    }

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
            // compute a little bit at a time to keep from timing out
            final int totalResponseLength = bodyLength * RESPONSE_MULTIPLIER;
            int computed = 0;
            LOGGER.debug("Starting response. Length: {}", totalResponseLength);
            while (computed < totalResponseLength) {

                final int sendSize = Math.min(MAX_CHARACTERS_AT_ONCE, totalResponseLength - computed);
                LOGGER.trace("generating response string");
                final String responsePayload = RandomStringUtils.randomAlphanumeric(sendSize);
                LOGGER.trace("done generating response string");

                LOGGER.trace("Before writing string");
                writer.write(".");
                computed += responsePayload.length();
                LOGGER.debug("Added to response {} random characters. {} / {}", responsePayload.length(), computed,
                        totalResponseLength);
            }

            // convert request contents to an array of bytes
            ByteArrayOutputStream bytesOutputStream = new ByteArrayOutputStream();
            Writer bytesWriter = new OutputStreamWriter(bytesOutputStream, Charset.forName("US-ASCII"));
            IOUtils.copy(request.getReader(), bytesWriter);
            final byte[] bodyContent = bytesOutputStream.toByteArray();

            // send the contents of the request to the database
            try {
                InetAddress databaseAddress = DnsUtils.getByName(DATABASE_HOSTNAME);
                storeInDatabase(databaseAddress, bodyContent);
            } catch (UnknownHostException e) {
                LOGGER.error("Could not find node with hostname '{}'.", DATABASE_HOSTNAME, e);
            }

            writer.write("OK");
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

    private boolean storeInDatabase(InetAddress host, byte[] data) {
        try (Socket s = new Socket(host, DATABASE_PORT)) {
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                long time = System.currentTimeMillis();

                LOGGER.info("Sending request data of length {} bytes to database at {}.", data.length, host);

                FileStoreOperation fso = new FileStoreOperation(name, time, "simple-webserver_results" + time, data,
                        "meta_data");

                out.writeObject(fso);
                out.flush();

                Object response = in.readObject();

                if (response instanceof Acknowledgement) {
                    Acknowledgement a = (Acknowledgement) response;
                    LOGGER.info("Received ACK from database at {}: {}", host, a);
                }

                return true;
            } catch (IOException | ClassNotFoundException e) {
                LOGGER.error("Unable to communicate with database at '{}'.", host, e);
            }
        } catch (IOException e) {
            LOGGER.error("Unable to connect to database at '{}'.", host, e);
        }

        return false;
    }
}
