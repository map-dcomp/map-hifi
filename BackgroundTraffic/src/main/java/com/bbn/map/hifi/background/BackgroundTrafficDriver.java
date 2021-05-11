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
package com.bbn.map.hifi.background;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.hifi.HiFiAgent;
import com.bbn.map.hifi.dns.DnsServer;
import com.bbn.map.hifi.simulation.SimDriver;
import com.bbn.map.hifi.simulation.SimRequest;
import com.bbn.map.hifi.simulation.SimResponse;
import com.bbn.map.hifi.simulation.SimResponseStatus;
import com.bbn.map.hifi.util.AbsoluteClock;
import com.bbn.map.hifi.util.ConcurrencyUtils;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.hifi.util.IdentifierUtils;
import com.bbn.map.simulator.BackgroundNetworkLoad;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.utils.JsonUtils;
import com.bbn.map.utils.LogExceptionHandler;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;

/**
 * Starts iperf server processes and client processes.
 * 
 * @author jschewe
 *
 */
public final class BackgroundTrafficDriver {

    // put this first to ensure that the correct logging configuration is used
    static {
        System.setProperty("log4j.configurationFactory", MapBackgroundTrafficLoggingConfig.class.getName());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundTrafficDriver.class);

    /**
     * Print out the usage information. Does not exit, that is up to the caller.
     * 
     * @param options
     *            the options for the command line parser
     */
    private static void printUsage(final Options options) {
        new HelpFormatter().printHelp("BackgroundTraffic", options);
    }

    // CHECKSTYLE:OFF value class for storing parameters
    /**
     * Configuration parameters.
     * 
     * @author jschewe
     *
     */
    public static final class Parameters {
        public String hostname;

        public Path backgroundTrafficFile;
    }
    // CHECKSTYLE:ON

    private static void outputVersionInformation() {
        LOGGER.info("Git version: {}", getGitVersionInformation());
    }

    /**
     * 
     * @param args
     *            See --help for all options.
     */
    public static void main(final String[] args) {
        LogExceptionHandler.registerExceptionHandler();

        DnsUtils.configureDnsCache();

        final Options options = new Options();
        options.addOption(null, HiFiAgent.CONFIGURATION_DIRECTORY_OPT, true,
                "The directory to read the configuration from (default: " + DnsServer.DEFAULT_CONFIGURATION_DIRECTORY
                        + ")");
        options.addOption("h", HiFiAgent.HELP_OPT, false, "Show the help");
        options.addOption("v", HiFiAgent.VERSION_OPT, false, "Display the version");

        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption(HiFiAgent.HELP_OPT)) {
                printUsage(options);
                System.exit(0);
            } else if (cmd.hasOption(HiFiAgent.VERSION_OPT)) {
                outputVersionInformation();
                System.exit(0);
            }

            final Path configurationDirectory;
            if (cmd.hasOption(HiFiAgent.CONFIGURATION_DIRECTORY_OPT)) {
                final String configDirectoryStr = cmd.getOptionValue(HiFiAgent.CONFIGURATION_DIRECTORY_OPT);
                configurationDirectory = Paths.get(configDirectoryStr);
            } else {
                configurationDirectory = Paths.get(DnsServer.DEFAULT_CONFIGURATION_DIRECTORY);
            }

            try {
                final Parameters parameters = parseConfigurationDirectory(configurationDirectory);
                if (null == parameters) {
                    System.exit(1);
                }

                final BackgroundTrafficDriver driver = new BackgroundTrafficDriver(parameters);
                outputVersionInformation();

                driver.startScenario();
                driver.thread.join();

                LOGGER.info("Shutting down");
                driver.shutdownDriver();

                LOGGER.info("Exiting...");
                System.exit(0);
            } catch (final IOException e) {
                LOGGER.error("Error parsing configuration files: {}", e.getMessage(), e);
                System.exit(1);
            } catch (final InterruptedException e) {
                LOGGER.info("Got interrupted, exiting", e);
                System.exit(1);
            }

        } catch (final ParseException e) {
            LOGGER.error("Error parsing the command line: {}", e.getMessage());
            new HelpFormatter().printHelp("SimulationRunner", options);
            System.exit(1);
        }
    }

    /**
     * Parse the configuration directory.
     * 
     * @param configurationDirectory
     *            path to the configuration directory.
     * @return the parameters
     * @throws IOException
     *             if there is a problem reading the properties
     */
    public static Parameters parseConfigurationDirectory(final Path configurationDirectory) throws IOException {
        final Parameters parameters = new Parameters();

        parameters.backgroundTrafficFile = configurationDirectory.resolve(Simulation.BACKGROUND_TRAFFIC_FILENAME);
        if (!Files.exists(parameters.backgroundTrafficFile)) {
            LOGGER.error("{} does not exist", parameters.backgroundTrafficFile);
            return null;
        }

        final Path localPropsFile = configurationDirectory.resolve(HiFiAgent.LOCAL_PROPERTIES_FILENAME);
        if (!Files.exists(localPropsFile)) {
            LOGGER.error("{} does not exist", localPropsFile);
            return null;
        }

        final Properties localProps = new Properties();
        try (Reader reader = Files.newBufferedReader(localPropsFile)) {
            localProps.load(reader);
        }

        parameters.hostname = localProps.getProperty(HiFiAgent.HOSTNAME_PROPERTY_KEY, null);
        if (null == parameters.hostname) {
            LOGGER.error("{} does not contain property {}", localPropsFile, HiFiAgent.HOSTNAME_PROPERTY_KEY);
            return null;
        }

        final Path globalPropsFile = configurationDirectory.resolve(HiFiAgent.GLOBAL_PROPERTIES_FILENAME);
        if (!Files.exists(globalPropsFile)) {
            LOGGER.error("{} does not exist", globalPropsFile);
            return null;
        }

        final Properties globalProps = new Properties();
        try (Reader reader = Files.newBufferedReader(globalPropsFile)) {
            globalProps.load(reader);
        }

        return parameters;
    }

    private final ImmutableList<BackgroundNetworkLoad> requests;
    private final Thread thread;
    private final Thread simThread;
    private final NodeIdentifier id;

    /**
     * 
     * @param parameters
     *            information about configuring the driver
     * @throws IOException
     *             if there is an error parsing the configuration files
     */
    private BackgroundTrafficDriver(final Parameters parameters) throws IOException {
        this.requests = BackgroundNetworkLoad.parseBackgroundTraffic(parameters.backgroundTrafficFile);
        this.thread = new Thread(() -> runScenario(), "Background Traffic Driver");

        this.id = IdentifierUtils.getNodeIdentifier(parameters.hostname);

        this.simThread = new Thread(() -> runSimServer(), "Simulation Server listener");
        this.simThread.setDaemon(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdownDriver()));

        LOGGER.trace("Loaded background traffic requests: {}", this.requests);
    }

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Shutdown the scenario.
     */
    public void shutdownDriver() {
        running.set(false);
        thread.interrupt();
        shutdownClients();
        shutdownServers();
    }

    private final List<IperfServer> servers = new LinkedList<>();
    private final List<IperfClient> clients = new LinkedList<>();

    private void shutdownClients() {
        final List<IperfClient> copy;
        synchronized (clients) {
            copy = new LinkedList<>(clients);
            clients.clear();
        }

        copy.forEach(IperfClient::shutdown);
    }

    private void shutdownServers() {
        final List<IperfServer> copy;
        synchronized (servers) {
            copy = new LinkedList<>(servers);
            servers.clear();
        }

        copy.forEach(IperfServer::shutdown);
    }

    /**
     * Start the scenario.
     */
    public void startScenario() {
        if (running.compareAndSet(false, true)) {
            LOGGER.info("Starting scenario");

            createAndStartServers();

            running.set(true);
            thread.start();
            simThread.start();
        } else {
            LOGGER.error("Already running");
        }
    }

    private void createAndStartServers() {
        for (final BackgroundNetworkLoad request : requests) {
            final NodeIdentifier serverId = IdentifierUtils.getNodeIdentifier(request.getServer());
            if (id.equals(serverId)) {
                final IperfServer iperf = new IperfServer(request);
                servers.add(iperf);
                iperf.startProcesses();
            }
        }

    }

    /**
     * How long to wait after the last latest background request finish time
     * before cleaning up. This exists so that we don't try and stop processes
     * while the stop timers are still running.
     */
    private static final int WAIT_FINISH_MS = 60 * 1000;

    private void runScenario() {
        LOGGER.info("Starting background traffic driver thread");

        final Timer stopTimer = new Timer("Background Traffic Driver stop timer", true);

        final VirtualClock globalClock = new AbsoluteClock();
        globalClock.startClock();

        LOGGER.info("Waiting for start command from simulation driver");

        synchronized (waitingForStart) {
            while (waitingForStart.get()) {
                try {
                    waitingForStart.wait();
                } catch (final InterruptedException e) {
                    LOGGER.warn("Interrupted waiting for start, will try again", e);
                }
            }
        }

        final long startTime = waitingForStart.getStartTime();
        if (startTime >= 0) {
            LOGGER.info("Waiting until {} to start", startTime);
            globalClock.waitUntilTime(startTime);
        }

        LOGGER.info("Starting background traffic driver");

        // need a relative clock to be compatible with the demand files
        final VirtualClock simulationClock = new SimpleClock();
        simulationClock.startClock();

        long latestStop = 0;

        final UnmodifiableIterator<BackgroundNetworkLoad> iter = requests.iterator();
        while (running.get() && iter.hasNext()) {
            final BackgroundNetworkLoad req = iter.next();

            final NodeIdentifier clientId = IdentifierUtils.getNodeIdentifier(req.getClient());
            if (!id.equals(clientId)) {
                continue;
            }

            LOGGER.trace("Waiting for request start time: {}", req.getStartTime());

            simulationClock.waitUntilTime(req.getStartTime());
            LOGGER.info("Applying background traffic request: {}", req);

            final IperfClient client = startClient(req);
            synchronized (clients) {
                clients.add(client);
            }

            // iperf clients will exit on their own, this ensures that we do
            // proper cleanup
            final long duration = req.getNetworkDuration() + WAIT_FINISH_MS;
            stopTimer.schedule(new TimerTask() {
                public void run() {
                    shutdownClient(client);
                }
            }, duration);

            final long endTime = simulationClock.getCurrentTime() + duration;
            latestStop = Math.max(latestStop, endTime);

        } // while running and demand left to execute

        // wait until all clients are done
        LOGGER.info("Waiting for background requests to finish");
        simulationClock.waitUntilTime(latestStop + WAIT_FINISH_MS);

        LOGGER.info("Background requests have finished, leaving the driver up to keep servers alive");

        try {
            ConcurrencyUtils.waitForever();
        } catch (final InterruptedException e) {
            LOGGER.info("Interrupted waiting for the process to die, probably time to exit", e);
        }

        LOGGER.info("Background traffic driver thread finished.");
    }

    private IperfClient startClient(final BackgroundNetworkLoad request) {
        final IperfClient client = new IperfClient(request);
        client.startExecution();
        return client;
    }

    private void shutdownClient(final IperfClient client) {
        synchronized (clients) {
            clients.remove(client);
        }
        client.shutdown();
    }

    /**
     * Get the git version information for the build.
     * 
     * @return the version or a string stating what the problem was
     */
    public static String getGitVersionInformation() {
        final URL url = BackgroundTrafficDriver.class.getResource("git.properties");
        if (null == url) {
            return "UNKNOWN";
        }
        try (InputStream is = url.openStream()) {
            final Properties props = new Properties();
            props.load(is);
            return props.getProperty("git.commit.id", "MISSING-PROPERTY");
        } catch (final IOException e) {
            LOGGER.error("Unable to read version properties", e);
            return "ERROR-READING-VERSION";
        }
    }

    private final WaitingForStart waitingForStart = new WaitingForStart();

    /**
     * Open a listening socket for the simulation driver.
     */
    private void runSimServer() {
        LOGGER.info("Starting server");
        try (ServerSocket server = new ServerSocket(SimDriver.BACKGROUND_TRAFFIC_PORT)) {
            while (true) {
                try {
                    final Socket socket = server.accept();
                    LOGGER.debug("Got connection from {}", socket.getLocalSocketAddress().toString());

                    final SimHandler handler = new SimHandler(waitingForStart, socket);
                    handler.setDaemon(true);
                    handler.start();
                } catch (final IOException e) {
                    throw new RuntimeException("Error accepting sim driver connection", e);
                }

            } // while running
        } catch (final IOException e) {
            throw new RuntimeException("Error creating socket", e);
        }
    }

    private static final class SimHandler extends Thread {
        private final Socket socket;
        private final WaitingForStart waitingForStart;

        /**
         * 
         * @param startLock
         *            the object to notify when to start
         * @param socket
         *            the socket from the simulation driver
         */
        private SimHandler(final WaitingForStart startLock, final Socket socket) {
            this.socket = socket;
            this.waitingForStart = startLock;
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
                                    LOGGER.trace("Got start request");
                                    final String startResult = handleStartMessage(mapper, req);
                                    if (null == startResult) {
                                        resp.setStatus(SimResponseStatus.OK);
                                    } else {
                                        resp.setStatus(SimResponseStatus.ERROR);
                                        resp.setMessage(startResult);
                                    }
                                    break;
                                case SHUTDOWN:
                                    resp.setStatus(SimResponseStatus.OK);
                                    resp.setMessage("Background traffic doesn't know how to handle SHUTDOWN");
                                    break;
                                case TOPOLOGY_UPDATE:
                                    resp.setStatus(SimResponseStatus.OK);
                                    resp.setMessage("Bacckground traffic doesn't know how to handle TOPOLOGY_UPDATE");
                                    break;
                                default:
                                    resp.setStatus(SimResponseStatus.ERROR);
                                    resp.setMessage("Unknown request: " + req.getType());
                                    break;
                                }

                                mapper.writeValue(writer, resp);
                            }

                        } catch (final IOException e) {
                            LOGGER.warn("Got IO exception", e);
                            if (socket.isClosed()) {
                                running.set(false);
                            }
                        } catch (final RuntimeException e) {
                            if (e.getCause() instanceof IOException) {
                                LOGGER.warn("Got IOException inside RuntimeException, likely from iter.hasNext", e);
                                if (socket.isClosed()) {
                                    running.set(false);
                                }
                            } else {
                                throw e;
                            }
                        }
                    } // while running

                } catch (final IOException e) {
                    LOGGER.error("Error getting socket streams", e);
                }

                running.set(false);
            } // log context
        }

        private String handleStartMessage(final ObjectMapper mapper, final SimRequest req) {
            final JsonNode tree = req.getPayload();
            if (null != tree) {
                try {
                    final long time = mapper.treeToValue(tree, long.class);
                    waitingForStart.setStartTime(time);

                    synchronized (waitingForStart) {
                        waitingForStart.set(false);
                        waitingForStart.notifyAll();
                    }

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

    /**
     * Simple class to track the state of waiting for start. This isn't just a
     * primitive boolean so that one can use the synchronization lock and
     * condition variable from {@link Object}.
     */
    private static final class WaitingForStart extends Object {
        private boolean value = true;

        public void set(final boolean v) {
            value = v;
        }

        public boolean get() {
            return value;
        }

        private long startTime = -1;

        public void setStartTime(final long v) {
            startTime = v;
        }

        public long getStartTime() {
            return startTime;
        }
    }

}
