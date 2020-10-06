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
package com.bbn.map.hifi.dns;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Listen for messages to control the {@link WeightedRoundRobinResolver}. Each
 * message is a {@link WeightedRecordList} encoded as JSON. If there are any
 * errors parsing the message, the socket will be dropped.
 * 
 * @author jschewe
 *
 */
public class WeightedRecordMessageServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeightedRecordMessageServer.class);

    /**
     * Port that the server will listen on for weighted round robin records.
     */
    public static final int PORT = 1053;

    private final Object lock = new Object();
    private ServerSocket server = null;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final WeightedRoundRobinResolver resolver;
    private final Collection<ClientHandler> clients = new LinkedList<>();
    private Thread listener = null;

    /**
     * Sent to indicate the next object is a list of records.
     */
    public static final String UPDATE_COMMAND = "UPDATE";
    /**
     * Sent to indicate that the logs should be flushed.
     */
    public static final String FLUSH_COMMAND = "FLUSH";

    private boolean isRunning() {
        return running.get();
    }

    private void stopRunning() {
        running.set(false);
    }

    /**
     * 
     * @param resolver
     *            the resolver to set messages on
     */
    public WeightedRecordMessageServer(final WeightedRoundRobinResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Start the service.
     */
    public void start() {
        if (isRunning()) {
            LOGGER.debug("Already running, ignoring extra start call");
            return;
        }

        running.set(true);

        listener = new Thread(() -> listenForConnections(), "WeightedRoundRobin listener");
        listener.start();
    }

    private void listenForConnections() {
        synchronized (lock) {
            try {
                server = new ServerSocket(PORT);
                server.setReuseAddress(true);

                LOGGER.trace("Listening for connections on port {}", PORT);
            } catch (final IOException e) {
                LOGGER.error("Unable to create server socket on port " + PORT, e);
            }
        }

        while (isRunning()) {
            LOGGER.trace("Waiting for a client connection");

            try {
                final Socket s = server.accept();
                LOGGER.trace("Accepted connection from {}", s.getRemoteSocketAddress());

                final ClientHandler clientHandler = new ClientHandler(this, s);
                synchronized (lock) {
                    clients.add(clientHandler);
                }
                new Thread(clientHandler, String.format("Client handler for %s", s.getRemoteSocketAddress())).start();
            } catch (final IOException e) {
                LOGGER.warn("Error accepting socket, trying again", e);
            }

        }
    }

    private static final class CollectionOfWeightedRecordListTypeInformation
            extends TypeReference<Collection<WeightedRecordList>> {
        public static final CollectionOfWeightedRecordListTypeInformation INSTANCE = new CollectionOfWeightedRecordListTypeInformation();
    }

    /**
     * Shutdown the service, the service cannot be restarted after it has been
     * shutdown.
     */
    public void shutdown() {
        stopRunning();

        // make a copy so that if removeClient is called in the middle of this
        // we don't get a concurrent modification exception
        final Collection<ClientHandler> clientsCopy;
        synchronized (lock) {
            clientsCopy = new LinkedList<>(clients);
            clients.clear();
        }
        clientsCopy.forEach(ClientHandler::stop);
    }

    /**
     * This method is used by {@link ClientHandler} to clean up when it exists.
     * 
     * @param handler
     *            the handler to remove from the list of known clients
     */
    /* package */ void removeClient(final ClientHandler handler) {
        synchronized (lock) {
            clients.remove(handler);
        }
    }

    /**
     * A reply to a message.
     * 
     * @author jschewe
     *
     */
    public static final class Reply {

        /**
         * @param success
         *            see {@link #isSuccess()}
         * @param message
         *            see {@link #getMessage()}
         */
        public Reply(@JsonProperty("success") final boolean success, @JsonProperty("message") final String message) {
            this.success = success;
            this.message = message;
        }

        private final boolean success;

        /**
         * 
         * @return if the message was successfully received.
         */
        public boolean isSuccess() {
            return success;
        }

        private final String message;

        /**
         * 
         * @return if an error, the error message. May be null.
         */
        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return String.format("success: %b message: %s", isSuccess(), getMessage());
        }
    }

    private static final class ClientHandler implements Runnable {
        private static final Logger LOGGER = LoggerFactory.getLogger(ClientHandler.class);

        private final WeightedRecordMessageServer server;
        private final Socket socket;
        private final AtomicBoolean running = new AtomicBoolean(false);

        /**
         * 
         * @param resolver
         *            the resolver to modify
         * @param socket
         *            the client socket
         */
        ClientHandler(final WeightedRecordMessageServer server, final Socket socket) {
            this.server = server;
            this.socket = socket;
        }

        public void stop() {
            running.set(false);
            try {
                socket.close();
            } catch (final IOException e) {
                LOGGER.debug("Got error closing socket, ignoring", e);
            }
        }

        private Reply flushLogs() {
            try {
                this.server.resolver.flushLogs();
                final Reply reply = new Reply(true, null);
                return reply;
            } catch (final IOException e) {
                LOGGER.error("Got error flushing logs", e);
                final Reply reply = new Reply(false, e.getMessage());
                return reply;
            }
        }

        public void run() {
            try (CloseableThreadContext.Instance ctc = CloseableThreadContext
                    .push(socket.getRemoteSocketAddress().toString())) {

                LOGGER.trace("Top of run");
                running.set(true);

                LOGGER.trace("Received connection from {}", socket.getRemoteSocketAddress());

                try (Reader reader = new InputStreamReader(socket.getInputStream(), Charset.defaultCharset());
                        Writer writer = new OutputStreamWriter(socket.getOutputStream(), Charset.defaultCharset())) {
                    final ObjectMapper jsonMapper = new ObjectMapper().disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)
                            .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
                    final JsonFactory generator = jsonMapper.getFactory();
                    final JsonParser parser = generator.createParser(reader);

                    parser.nextToken();
                    try {
                        final String command = parser.readValueAs(String.class);

                        final Reply reply;
                        if (FLUSH_COMMAND.equalsIgnoreCase(command)) {
                            reply = flushLogs();
                        } else if (UPDATE_COMMAND.equalsIgnoreCase(command)) {
                            final Collection<WeightedRecordList> list = parser
                                    .readValueAs(CollectionOfWeightedRecordListTypeInformation.INSTANCE);

                            server.resolver.setWeightedRecords(list);
                            reply = new Reply(true, null);
                        } else {
                            reply = new Reply(false, "Unknown command: '" + command + "'");
                        }
                        jsonMapper.writeValue(writer, reply);
                        if (socket.isClosed()) {
                            LOGGER.debug("Socket is closed, exiting");
                        }

                    } catch (JsonParseException | JsonMappingException e) {
                        if (socket.isClosed()) {
                            LOGGER.debug("Client closed connection, existing", e);
                        } else {
                            LOGGER.warn("Error parsing input from client, closing connection. closed: {} connected: {}",
                                    socket.isClosed(), socket.isConnected(), e);
                        }

                        final Reply reply = new Reply(false, e.getMessage());
                        jsonMapper.writeValue(writer, reply);
                    }
                } catch (final IOException e) {
                    if (running.get()) {
                        LOGGER.error("Error reading from socket " + socket.getRemoteSocketAddress()
                                + ", giving up on client", e);
                    } else {
                        LOGGER.debug("Error reading from socket during shutdown " + socket.getRemoteSocketAddress(), e);
                    }
                } finally {
                    stop();
                    this.server.removeClient(ClientHandler.this);
                }
            } // logger context
        }
    } // ClientHandler
}
