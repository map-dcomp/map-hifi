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
package com.bbn.map.hifi.simulation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.hifi.util.IdentifierUtils;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A connection to a client or NCP. The connection is made in the constructor.
 * If/when a connection drops a reconnect is not attempted.
 * 
 * @author jschewe
 *
 */
public class SimConnection {

    private final Logger logger;

    private Socket socket;

    private final ObjectMapper mapper;
    private Reader reader;
    private Writer writer;
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

    /**
     * Maximum number of times to try and make the initial connection.
     */
    private static final int MAX_INITIAL_CONNECTION_ATTEMPTS = 50;

    /**
     * Maximum number of times to try and make a connection when trying to send
     * a command.
     */
    private static final int MAX_COMMAND_CONNECTION_ATTEMPTS = 3;

    /**
     * Maximum number of times to try and send a command before failing.
     */
    private static final int MAX_COMMAND_SEND_ATTEMPTS = 10;

    private static final Duration TIME_BETWEEN_CONNETION_ATTEMPTS = Duration.ofSeconds(30);

    /**
     * How long to wait for a command send and receive before the command is
     * failed.
     */
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration TIME_BETWEEN_COMMAND_ATTEMPTS = Duration.ofSeconds(10);

    /**
     * Used to execute commands.
     */
    private final ExecutorService commandExecutor = Executors.newFixedThreadPool(1);

    private final Map<String, String> nodeControlNames;

    /**
     * The constuctor connects to the node. This can take a long time if the
     * node isn't up yet. Check {@link #isConnected()} after construction to see
     * if the connection was successful.
     * 
     * @param nodeId
     *            the node to connect to
     * @param port
     *            the port to connect to on the node
     * @param nodeControlNames
     *            used to find the address to use to connect to a node
     */
    public SimConnection(@Nonnull final NodeIdentifier nodeId,
            final int port,
            @Nonnull final Map<String, String> nodeControlNames) {
        this.logger = LoggerFactory.getLogger(this.getClass().getName() + "." + nodeId.getName() + "_" + port);
        this.port = port;

        this.nodeId = Objects.requireNonNull(nodeId);
        mapper = JsonUtils.getStandardMapObjectMapper().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
                .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

        this.nodeControlNames = Objects.requireNonNull(nodeControlNames);

        connectToNode(MAX_INITIAL_CONNECTION_ATTEMPTS);

    }

    private String getNodeHostname(final NodeIdentifier node) {
        final String name = IdentifierUtils.getSimpleNodeName(node);
        final String controlNetworkName = nodeControlNames.get(name);
        if (null == controlNetworkName) {
            logger.warn("Unable to find {} in control node names, connection will be across the experiment network",
                    name);
            return name;
        } else {
            return controlNetworkName;
        }
    }

    /**
     * Connect to a node. If already connected, just return.
     */
    @SuppressFBWarnings(value = "SWL_SLEEP_WITH_LOCK_HELD", justification = "Don't want other threads to try and connect while this is running")
    private void connectToNode(final int maxConnectionAttempts) {
        synchronized (lock) {
            if (internalIsConnected()) {
                logger.debug("Already connected");
                return;
            }

            final String nodeHostname = getNodeHostname(nodeId);
            logger.info("Connecting to {}", nodeHostname);

            int attempt = 0;
            while (attempt < maxConnectionAttempts) {
                logger.info("Connection attempt {} out of {}", attempt, maxConnectionAttempts);

                try {
                    this.socket = new Socket(nodeHostname, this.port);
                    try {
                        reader = new InputStreamReader(socket.getInputStream(), Charset.defaultCharset());
                        writer = new OutputStreamWriter(socket.getOutputStream(), Charset.defaultCharset());
                        logger.debug("Connected");
                        return;
                    } catch (final IOException e) {
                        logger.error("Error getting socket streams", e);
                        disconnect();
                    }
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
        }
    }

    /**
     * 
     * @return is the client connected?
     */
    public boolean isConnected() {
        synchronized (lock) {
            return internalIsConnected();
        }
    }

    @GuardedBy("lock")
    private boolean internalIsConnected() {
        return null != socket && !socket.isClosed() && socket.isConnected();
    }

    @GuardedBy("lock")
    private void disconnect() {
        try {
            socket.close();
        } catch (final IOException e) {
            logger.warn("Error closing connection", e);
        }
        socket = null;
        reader = null;
        writer = null;
    }

    /**
     * Send a start message.
     * 
     * @param startTime
     *            when the recipient should start, this is a value in
     *            milliseconds and should be compared with the VirtualClock on
     *            the receiving side.
     * @return if the message was sent and the response is
     *         {@link SimResponseStatus#OK}
     */
    public boolean sendStart(final long startTime) {
        final SimRequest req = new SimRequest();
        req.setType(SimRequestType.START);

        final JsonNode payload = mapper.valueToTree(startTime);
        req.setPayload(payload);

        return sendCommandWithRetry(req);
    }

    /**
     * Send a shutdown message.
     * 
     * @return if the message was sent and the response is
     *         {@link SimResponseStatus#OK}
     */
    public boolean sendShutdown() {
        logger.debug("Sending shutdown");
        final SimRequest req = new SimRequest();
        req.setType(SimRequestType.SHUTDOWN);

        return sendCommandWithRetry(req);
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
        final SimRequest req = new SimRequest();
        req.setType(SimRequestType.TOPOLOGY_UPDATE);

        final JsonNode payload = mapper.valueToTree(msg);
        req.setPayload(payload);

        return sendCommandWithRetry(req);
    }

    private boolean sendCommandWithRetry(final SimRequest req) {
        int attempt = 0;
        while (attempt < MAX_COMMAND_SEND_ATTEMPTS) {
            logger.debug("Attempt {} sending {}", attempt, req.getType());

            connectToNode(MAX_COMMAND_CONNECTION_ATTEMPTS);

            final Future<Boolean> future = commandExecutor.submit(() -> this.sendCommand(req));

            try {
                logger.debug("Waiting up to {} ms for command results", COMMAND_TIMEOUT.toMillis());
                final boolean result = future.get(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                logger.debug("Received response to {} attempt {} -> {}", req.getType(), attempt, result);
                return result;
            } catch (final TimeoutException e) {
                logger.info("Timed out waiting for command, will disconnect and try again", e);
                disconnect();
            } catch (final InterruptedException e) {
                logger.warn("Interrupted waiting for command result, trying again", e);
            } catch (final ExecutionException e) {
                logger.error("Exception sending command, trying again", e.getCause());
            }

            try {
                Thread.sleep(TIME_BETWEEN_COMMAND_ATTEMPTS.toMillis());
            } catch (final InterruptedException e) {
                logger.warn("Interrupted waiting for send attempt", e);
            }

            ++attempt;
        }
        logger.warn("Failed to send command of type {} in {} attempts", req.getType(), MAX_COMMAND_SEND_ATTEMPTS);

        return false;
    }

    private boolean sendCommand(final SimRequest req) throws IOException {
        synchronized (lock) {
            mapper.writeValue(writer, req);
            writer.flush();

            final SimResponse resp = mapper.readValue(reader, SimResponse.class);

            return SimResponseStatus.OK.equals(resp.getStatus());
        }
    }
}
