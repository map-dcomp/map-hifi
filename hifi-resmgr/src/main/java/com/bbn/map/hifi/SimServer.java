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
package com.bbn.map.hifi;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.Controller;
import com.bbn.map.hifi.simulation.SimDriver;
import com.bbn.map.hifi.simulation.SimRequest;
import com.bbn.map.hifi.simulation.SimResponse;
import com.bbn.map.hifi.simulation.SimResponseStatus;
import com.bbn.map.hifi.simulation.TopologyUpdateMessage;
import com.bbn.map.hifi.ta2.TA2Impl;
import com.bbn.map.hifi.util.ProcessUtils;
import com.bbn.map.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Listen for connections from the simulation driver and respond to messages.
 * 
 * @author jschewe
 *
 */
public final class SimServer {

    private SimServer() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SimServer.class);

    /**
     * How long to wait after sending the ok message before starting shutdown.
     */
    private static final int SECONDS_TO_WAIT_FOR_SHUTDOWN = 30;

    /**
     * Open a listening socket for the simulation driver.
     * 
     * @param ta2
     *            where to send topology update information
     * @param controller
     *            notified when the algorithms should start
     */
    public static void runServer(@Nonnull final TA2Impl ta2, @Nonnull final Controller controller) {
        LOGGER.info("Starting server");
        try (ServerSocket server = new ServerSocket(SimDriver.PORT)) {
            while (true) {
                try {

                    final Socket socket = server.accept();
                    LOGGER.debug("Got connection from {}", socket.getLocalSocketAddress().toString());

                    final SimHandler handler = new SimHandler(ta2, controller, socket);
                    handler.setDaemon(true);
                    handler.start();
                } catch (final IOException e) {
                    throw new RuntimeException("Error accepting client connection", e);
                }

            } // while running
        } catch (final IOException e) {
            throw new RuntimeException("Error creating socket", e);
        }
    }

    private static final class SimHandler extends Thread {
        private final Socket socket;
        private final TA2Impl ta2;
        private final Controller controller;

        private SimHandler(final TA2Impl ta2, final Controller controller, final Socket socket) {
            this.ta2 = ta2;
            this.socket = socket;
            this.controller = controller;
        }

        private AtomicBoolean running = new AtomicBoolean(false);

        @Override
        public void run() {
            try (CloseableThreadContext.Instance ignored = CloseableThreadContext.push(
                    socket.getRemoteSocketAddress().toString() + " <-> " + socket.getLocalSocketAddress().toString())) {

                running.set(true);

                try {
                    final Reader reader = new InputStreamReader(socket.getInputStream(), Charset.defaultCharset());
                    final Writer writer = new OutputStreamWriter(socket.getOutputStream(), Charset.defaultCharset());

                    final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper()
                            .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
                            .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
                    final JsonFactory jsonF = new JsonFactory();

                    while (running.get()) {
                        try {
                            final MappingIterator<SimRequest> iter = mapper.readValues(jsonF.createParser(reader),
                                    SimRequest.class);
                            while (running.get() && iter.hasNext()) {
                                final SimRequest req = iter.next();
                                LOGGER.trace("Got request {} closed: {}", req.getType(), socket.isClosed());

                                final SimResponse resp = new SimResponse();
                                switch (req.getType()) {
                                case START:
                                    LOGGER.debug("Got start request");
                                    final String startResult = handleStartMessage(mapper, req);
                                    if (null == startResult) {
                                        resp.setStatus(SimResponseStatus.OK);
                                    } else {
                                        resp.setStatus(SimResponseStatus.ERROR);
                                        resp.setMessage(startResult);
                                    }
                                    break;
                                case SHUTDOWN:
                                    LOGGER.debug("Got shutdown request");
                                    resp.setStatus(SimResponseStatus.OK);

                                    // schedule to run in 30 seconds
                                    new java.util.Timer().schedule(new java.util.TimerTask() {
                                        @Override
                                        public void run() {
                                            triggerShutdown();
                                        }
                                    }, Duration.ofSeconds(SECONDS_TO_WAIT_FOR_SHUTDOWN).toMillis());
                                    break;
                                case TOPOLOGY_UPDATE:
                                    LOGGER.debug("Got topology update");
                                    final String topologyResult = handleTopologyUpdate(mapper, req);
                                    if (null == topologyResult) {
                                        resp.setStatus(SimResponseStatus.OK);
                                    } else {
                                        resp.setStatus(SimResponseStatus.ERROR);
                                        resp.setMessage(topologyResult);
                                    }
                                    break;
                                default:
                                    LOGGER.warn("Got unknown request: {}", req.getType());
                                    resp.setStatus(SimResponseStatus.ERROR);
                                    resp.setMessage("Unknown request: " + req.getType());
                                    break;
                                }

                                mapper.writeValue(writer, resp);
                            }

                        } catch (final IOException | RuntimeException e) {
                            LOGGER.error("Got exception. Closing socket and stopping loop:", e);

                            try {
                                socket.close();
                            } catch (IOException e2) {
                                LOGGER.error("Error closing socket:", e2);
                            }

                            running.set(false);
                        }
                    } // while running

                    LOGGER.info("Finished run loop");

                } catch (final IOException e) {
                    LOGGER.error("Error getting socket streams", e);
                }

                running.set(false);
            } // log context
        }

        private String handleTopologyUpdate(final ObjectMapper mapper, final SimRequest req) {
            final JsonNode tree = req.getPayload();
            if (null != tree) {
                try {
                    final TopologyUpdateMessage msg = mapper.treeToValue(tree, TopologyUpdateMessage.class);
                    ta2.updateTopologyInformation(msg);

                    // success
                    return null;
                } catch (final JsonProcessingException e) {
                    LOGGER.error("Got error decoding topology update payload", e);

                    final String error = String.format(
                            "Got error decoding topology update payload, skipping processing of message: %s", e);
                    return error;
                }
            } else {
                LOGGER.warn("Skipping topology update with null payload");
                return "Got null payload on topology update, ignoring";
            }
        }

        /**
         * Shutdown this node. The agent is stopped and the networking disabled.
         * However the control network is kept up to allow the final results to
         * be collected.
         */
        @SuppressFBWarnings(value = "DM_EXIT", justification = "This method is expected to shutdown the agent")
        public static void triggerShutdown() {
            final ProcessBuilder pb = new ProcessBuilder("sudo", "/etc/map/shutdown_network_interfaces.py");
            pb.redirectErrorStream(true);

            try {
                LOGGER.info("Start network interface shutdown");
                final Process p = pb.start();

                final Thread logThread = new Thread(() -> ProcessUtils.logProcessStream(LOGGER, p.getInputStream(),
                        "output", LOGGER::info, () -> false), LOGGER.getName() + " error output handler");
                logThread.setDaemon(true);
                logThread.start();

                p.waitFor();
                LOGGER.info("Finished network interface shutdown. Exit status: {}", p.exitValue());

                LOGGER.info("Exiting agent");
                System.exit(0);
            } catch (final IOException e) {
                e.printStackTrace();
                LOGGER.error("Error simulating shutdown", e);
            } catch (final InterruptedException e) {
                e.printStackTrace();
                LOGGER.error("Interrupted waiting for simulated shutdown", e);
            }
        }

        private String handleStartMessage(final ObjectMapper mapper, final SimRequest req) {
            final JsonNode tree = req.getPayload();
            if (null != tree) {
                try {
                    final long time = mapper.treeToValue(tree, long.class);
                    controller.startAlgorithmsAt(time);

                    // success
                    return null;
                } catch (final JsonProcessingException e) {
                    LOGGER.error("Got error decoding topology update payload", e);

                    final String error = String.format(
                            "Got error decoding topology update payload, skipping processing of message: %s", e);
                    return error;
                }
            } else {
                LOGGER.warn("Skipping topology update with null payload");
                return "Got null payload on topology update, ignoring";
            }
        }

    } // class SimHandler

}
