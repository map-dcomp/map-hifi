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
package com.bbn.map.hifi.simulation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A connection to a client or NCP. The connection is made in the constructor.
 * If/when a connection drops a reconnect is not attempted.
 * 
 * @author jschewe
 *
 */
public class SimConnection {

    private final Logger logger;

    private final Socket socket;

    private final ObjectMapper mapper;
    private final Reader reader;
    private final Writer writer;
    private final Object lock = new Object();
    private final NodeIdentifier nodeId;

    /**
     * @return the node being connected to
     */
    public NodeIdentifier getNodeId() {
        return nodeId;
    }

    private final int port;

    /**
     * @return the port being connected to
     */
    public int getPort() {
        return port;
    }

    private static final int MAX_CONNECTION_ATTEMPTS = 50;
    private static final int SECONDS_BETWEEN_CONNECTION_ATTEMPTS = 30;
    private static final Duration TIME_BETWEEN_CONNETION_ATTEMPTS = Duration
            .ofSeconds(SECONDS_BETWEEN_CONNECTION_ATTEMPTS);

    /**
     * The constuctor connects to the node. This can take a long time if the
     * node isn't up yet. Check {@link #isConnected()} after construction to see
     * if the connection was successful.
     * 
     * @param nodeId
     *            the node to connect to
     * @param port
     *            the port to connect to on the node
     */
    public SimConnection(final NodeIdentifier nodeId, final int port) {
        this.logger = LoggerFactory.getLogger(this.getClass().getName() + "." + nodeId.getName() + "_" + port);
        this.port = port;

        this.nodeId = Objects.requireNonNull(nodeId);
        mapper = JsonUtils.getStandardMapObjectMapper().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
                .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

        this.socket = connectToNode(this.port);
        if (null != socket) {
            try {
                reader = new InputStreamReader(socket.getInputStream(), Charset.defaultCharset());
                writer = new OutputStreamWriter(socket.getOutputStream(), Charset.defaultCharset());
            } catch (final IOException e) {
                throw new RuntimeException("Error getting socket streams", e);
            }
        } else {
            reader = null;
            writer = null;
        }

    }

    private Socket connectToNode(final int port) {
        final String nodeHostname = nodeId.getName();

        int attempt = 0;
        while (attempt < MAX_CONNECTION_ATTEMPTS) {
            try {
                final Socket socket = new Socket(nodeHostname, port);

                return socket;
            } catch (final IOException e) {
                logger.warn("Got error connecting to client at attempt " + attempt, e);
            }

            try {
                Thread.sleep(TIME_BETWEEN_CONNETION_ATTEMPTS.toMillis());
            } catch (final InterruptedException e) {
                logger.warn("Interrupted waiting for connection attempt", e);
            }

            ++attempt;
        }
        return null;
    }

    /**
     * 
     * @return is the client connected?
     */
    public boolean isConnected() {
        synchronized (lock) {
            return null != socket && !socket.isClosed() && socket.isConnected();
        }
    }

    /**
     * Disconnect from the node. Once disconnected the connection cannot be used
     * again.
     */
    public void disconnect() {
        synchronized (lock) {
            try {
                socket.close();
            } catch (final IOException e) {
                logger.warn("Error closing connection", e);
            }
        }
    }

    /**
     * Send a start message.
     * 
     * @return if the message was sent and the response is
     *         {@link SimResponseStatus#OK}
     */
    public boolean sendStart() {
        try {

            final SimRequest req = new SimRequest();
            req.setType(SimRequestType.START);

            mapper.writeValue(writer, req);
            writer.flush();

            final SimResponse resp = mapper.readValue(reader, SimResponse.class);

            return SimResponseStatus.OK.equals(resp.getStatus());

        } catch (final IOException e) {
            logger.warn("Got IO exception sending start message", e);
            return false;
        }
    }

    /**
     * Send a shutdown message.
     * 
     * @return if the message was sent and the response is
     *         {@link SimResponseStatus#OK}
     */
    public boolean sendShutdown() {
        try {
            final SimRequest req = new SimRequest();
            req.setType(SimRequestType.SHUTDOWN);

            mapper.writeValue(writer, req);
            writer.flush();

            final SimResponse resp = mapper.readValue(reader, SimResponse.class);

            return SimResponseStatus.OK.equals(resp.getStatus());

        } catch (final IOException e) {
            logger.warn("Got IO exception sending shutdown message", e);
            return false;
        }
    }

    /**
     * Send a topology update message.
     * 
     * @param msg
     *            the message to send
     * @return if the message was sent and the response is
     *         {@link SimResponseStatus#OK}
     */
    public boolean sendTopologyUpdate(final TopologyUpdateMessage msg) {
        try {
            final SimRequest req = new SimRequest();
            req.setType(SimRequestType.TOPOLOGY_UPDATE);

            final JsonNode payload = mapper.valueToTree(msg);
            req.setPayload(payload);

            mapper.writeValue(writer, req);
            writer.flush();

            final SimResponse resp = mapper.readValue(reader, SimResponse.class);

            return SimResponseStatus.OK.equals(resp.getStatus());

        } catch (final IOException e) {
            logger.warn("Got IO exception sending shutdown message", e);
            return false;
        }
    }
}
