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
package com.bbn.map.hifi.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.hifi.HiFiAgent;
import com.bbn.map.hifi.simulation.SimDriver;
import com.bbn.map.hifi.simulation.SimRequest;
import com.bbn.map.hifi.simulation.SimResponse;
import com.bbn.map.hifi.simulation.SimResponseStatus;
import com.bbn.map.hifi.util.AbsoluteClock;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.simulator.ClientLoad;
import com.bbn.map.utils.JsonUtils;
import com.bbn.map.utils.LogExceptionHandler;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

/**
 * Driver for a client. The driver starts and stops client applications to run a
 * scenario.
 * 
 * @author jschewe
 *
 */
public final class ClientDriver {
    // put this first to ensure that the correct logging configuration is used
    static {
        System.setProperty("log4j.configurationFactory", MapClientLoggingConfig.class.getName());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientDriver.class);

    /**
     * Command line option name for the configuration directory.
     */
    private static final String CONFIGURATION_DIRECTORY_OPT = "configuration-directory";
    /**
     * Default location to use for the configuration directory.
     */
    public static final String DEFAULT_CONFIGURATION_DIRECTORY = "/etc/map";
    private static final String HELP_OPT = "help";
    private static final String VERSION_OPT = "version";

    private static final long IDLE_THREAD_TIMEOUT_MINUTES = 5;

    private final ThreadFactory javaClientPoolFactory = new ThreadFactoryBuilder().setNameFormat("CPThread" + "-%d")
            .build();
    private final ExecutorService javaClientPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
            IDLE_THREAD_TIMEOUT_MINUTES, TimeUnit.MINUTES, new SynchronousQueue<Runnable>(), javaClientPoolFactory);

    /**
     * Print out the usage information. Does not exit, that is up to the caller.
     * 
     * @param options
     *            the options for the command line parser
     */
    private static void printUsage(final Options options) {
        new HelpFormatter().printHelp("ClientDriver", options);
    }

    // CHECKSTYLE:OFF value class for storing parameters
    /**
     * Client configuration parameters.
     * 
     * @author jschewe
     *
     */
    public static final class Parameters {

        /**
         * Where to read the information about the client from.
         */
        public Path configurationFile;

        /**
         * Where to read information about how to start and stop services from.
         */
        public Path clientServiceConfigurationFile;

        /**
         * Where to read the client demand from.
         */
        public Path demandFile;

        /**
         * Where to read information about services to populate the application
         * manager.
         */
        public Path serviceConfigurationFile;

        /**
         * Where to read information about service dependencies to populate the
         * application manager.
         */
        public Path serviceDependencyFile;

        /**
         * Hostname where the docker registry is running.
         */
        public String dockerRegistryHostname;

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
        options.addOption(null, CONFIGURATION_DIRECTORY_OPT, true,
                "The directory to read the configuration from (default: " + DEFAULT_CONFIGURATION_DIRECTORY + ")");
        options.addOption("h", HELP_OPT, false, "Show the help");
        options.addOption("v", VERSION_OPT, false, "Display the version");

        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption(HELP_OPT)) {
                printUsage(options);
                System.exit(0);
            } else if (cmd.hasOption(VERSION_OPT)) {
                outputVersionInformation();
                System.exit(0);
            }

            final Path configurationDirectory;
            if (cmd.hasOption(CONFIGURATION_DIRECTORY_OPT)) {
                final String configDirectoryStr = cmd.getOptionValue(CONFIGURATION_DIRECTORY_OPT);
                configurationDirectory = Paths.get(configDirectoryStr);
            } else {
                configurationDirectory = Paths.get(DEFAULT_CONFIGURATION_DIRECTORY);
            }

            try {
                final Parameters parameters = parseConfigurationDirectory(configurationDirectory);
                if (null == parameters) {
                    System.exit(1);
                }

                try {
                    AppMgrUtils.loadApplicationManager(parameters.serviceConfigurationFile,
                            parameters.serviceDependencyFile);
                } catch (final IOException e) {
                    LOGGER.error("Error reading service configurations", e);
                    System.exit(1);
                }

                final ClientDriver driver = new ClientDriver(parameters);
                outputVersionInformation();

                driver.startScenario();
                driver.thread.join();

                LOGGER.info("Shutting down the scenario");
                driver.shutdownScenario();

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
     * The file that ansible will push out to the clients to determine how to
     * start and stop the clients. Must match stage experiment ansible
     * 10-setup.yml.
     */
    public static final String CLIENT_SERVICE_CONFIGURATION_FILENAME = "client-service-configurations.json";

    /**
     * Parse the client configuration directory.
     * 
     * @param configurationDirectory
     *            path to the configuration directory.
     * @return the parameters
     * @throws IOException
     *             if there is a problem reading the properties
     */
    public static Parameters parseConfigurationDirectory(final Path configurationDirectory) throws IOException {
        final Parameters parameters = new Parameters();

        parameters.configurationFile = configurationDirectory.resolve("config.json");
        if (!Files.exists(parameters.configurationFile)) {
            LOGGER.error("{} does not exist", parameters.configurationFile);
            return null;
        }

        parameters.clientServiceConfigurationFile = configurationDirectory
                .resolve(CLIENT_SERVICE_CONFIGURATION_FILENAME);
        if (!Files.exists(parameters.clientServiceConfigurationFile)) {
            LOGGER.error("{} does not exist", parameters.clientServiceConfigurationFile);
            return null;
        }

        parameters.demandFile = configurationDirectory.resolve("client-demand.json");
        if (!Files.exists(parameters.demandFile)) {
            LOGGER.error("{} does not exist", parameters.demandFile);
            return null;
        }

        parameters.serviceConfigurationFile = configurationDirectory.resolve(HiFiAgent.SERVICE_CONFIGURATION_FILENAME);
        if (!Files.exists(parameters.serviceConfigurationFile)) {
            LOGGER.error("{} does not exist", parameters.serviceConfigurationFile);
            return null;
        }

        parameters.serviceDependencyFile = configurationDirectory.resolve(HiFiAgent.SERVICE_DEPENDENCIES_FILENAME);

        final Path globalPropsFile = configurationDirectory.resolve(HiFiAgent.GLOBAL_PROPERTIES_FILENAME);
        if (!Files.exists(globalPropsFile)) {
            LOGGER.error("{} does not exist", globalPropsFile);
            return null;
        }

        final Properties globalProps = new Properties();
        try (Reader reader = Files.newBufferedReader(globalPropsFile)) {
            globalProps.load(reader);
        }

        parameters.dockerRegistryHostname = globalProps.getProperty(HiFiAgent.DOCKER_REGISTRY_HOST_KEY, null);
        if (null == parameters.dockerRegistryHostname) {
            LOGGER.error("{} does not contain property {}", globalPropsFile, HiFiAgent.DOCKER_REGISTRY_HOST_KEY);
            return null;
        }

        return parameters;
    }

    private final ImmutableList<ClientLoad> clientRequests;
    private final ImmutableMap<ApplicationCoordinates, ClientServiceConfiguration> clientServiceConfigs;
    private final Thread thread;
    private final Thread simThread;

    /**
     * 
     * @param parameters
     *            information about configuring the driver
     * @throws IOException
     *             if there is an error parsing the configuration files
     */
    private ClientDriver(final Parameters parameters) throws IOException {
        this.clientRequests = ClientLoad.parseClientDemand(parameters.demandFile);
        this.clientServiceConfigs = ClientServiceConfiguration
                .parseClientServiceConfigurations(parameters.clientServiceConfigurationFile);
        this.thread = new Thread(() -> runScenario(), "Client Driver");

        this.simThread = new Thread(() -> runSimServer(), "Simulation Server listener");
        this.simThread.setDaemon(true);

        this.servicesToExecute = this.clientRequests.stream().map(r -> r.getService()).collect(Collectors.toSet());

        createClientProcessBuilders(parameters.dockerRegistryHostname);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdownScenario()));

        LOGGER.trace("Loaded client load requests: {}", this.clientRequests);
        LOGGER.trace("Loadded client service configurations: {}", this.clientServiceConfigs);
    }

    /**
     * Services that this client will execute.
     */
    private final Set<ApplicationCoordinates> servicesToExecute;

    private final Map<ApplicationCoordinates, ProcessBuilder> clientProcessBuilders = new HashMap<>();

    /**
     * Create all of the ProcessBuilder objects for the clients that start
     * external processes.
     * 
     * This method also checks that all of the client service configurations are
     * valid.
     * 
     * @param registryHostname
     *            the host that the docker registry is running on
     */
    private void createClientProcessBuilders(final String registryHostname) {
        clientServiceConfigs.forEach((service, serviceConfig) -> {
            if (servicesToExecute.contains(service)) {
                if (!serviceConfig.isValid()) {
                    throw new RuntimeException(
                            String.format("Client configuration for service %s is not valid", service));
                }

                final ApplicationSpecification appSpec = AppMgrUtils.getApplicationSpecification(service);
                final String serviceHostname = DnsUtils.getFullyQualifiedServiceHostname(appSpec);

                if (ClientServiceConfiguration.ExecutionType.EXTERNAL_PROCESS.equals(serviceConfig.getStartType())) {
                    final ProcessBuilder builder = new ProcessBuilder(serviceConfig.getStartArguments());
                    builder.environment().put("SERVICE_HOSTNAME", serviceHostname);
                    builder.environment().put("REGISTRY_HOSTNAME", registryHostname);
                    clientProcessBuilders.put(service, builder);
                }
            }
        });
    }

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Shutdown the scenario.
     */
    public void shutdownScenario() {
        running.set(false);
        thread.interrupt();
        stopClients();
    }

    private final List<ClientInstance> clients = new LinkedList<>();

    private void stopClients() {
        final List<ClientInstance> copy;
        synchronized (clients) {
            copy = new LinkedList<>(clients);
            clients.clear();
        }

        copy.forEach(ClientInstance::stopClient);
    }

    /**
     * Start the scenario.
     */
    public void startScenario() {
        if (running.compareAndSet(false, true)) {
            LOGGER.info("Starting scenario");

            startCollectingSystemInformation();

            running.set(true);
            thread.start();
            simThread.start();
        } else {
            LOGGER.error("Already running");
        }
    }

    private void startCollectingSystemInformation() {
        final Thread thread = new Thread(ClientDriver::systemInfoThreadBody, "Collect System Info");
        thread.setDaemon(true);
        thread.start();
    }

    private static final Duration TIME_BETWEEN_SYSTEM_STAT_COLLECTIONS = Duration.ofSeconds(30);

    @SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME", justification = "This is the expected path on our clients")
    private static Path getSystemStatsPath() {
        return Paths.get("/var/lib/map/client/system-stats.csv");
    }

    private static void systemInfoThreadBody() {
        final String[] header = { "timestamp", "memory_total_bytes", "memory_available_bytes", "num_processors",
                "load_average" };

        final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        final SystemInfo si = new SystemInfo();
        final HardwareAbstractionLayer hal = si.getHardware();
        final GlobalMemory memory = hal.getMemory();

        try (Writer writer = Files.newBufferedWriter(getSystemStatsPath());
                CSVPrinter csv = new CSVPrinter(writer, CSVFormat.EXCEL.withHeader(header))) {

            try {
                while (!Thread.interrupted()) {
                    try {
                        csv.printRecord(System.currentTimeMillis(), memory.getTotal(), memory.getAvailable(),
                                osBean.getAvailableProcessors(), osBean.getSystemLoadAverage());
                        csv.flush();
                    } catch (final IOException e) {
                        LOGGER.error("Error writing entry", e);
                    }

                    Thread.sleep(TIME_BETWEEN_SYSTEM_STAT_COLLECTIONS.toMillis());
                }
            } catch (final InterruptedException e) {
                LOGGER.warn("Interrupted, exiting system status thread", e);
            }

        } catch (final IOException e) {
            LOGGER.error("Error opening system status file", e);
        }
    }

    /**
     * How long after the end of the request duration to wait for a client to
     * stop itself before forcing it to stop.
     */
    private static final int CLIENT_STOP_FINISH_MS = 60 * 1000;

    /**
     * How long to wait after the last latest client finish time before cleaning
     * up. This exists so that we don't try and stop clients while the stop
     * timers are still running.
     */
    private static final int CLIENT_WAIT_FINISH_MS = 2 * CLIENT_STOP_FINISH_MS;

    private void runScenario() {
        LOGGER.info("Starting client driver thread");

        final VirtualClock globalClock = new AbsoluteClock();
        globalClock.startClock();

        final Timer stopTimer = new Timer("Client Driver stop timer", true);

        LOGGER.info("Waiting for start command from simulation driver");

        synchronized (waitingForStart) {
            while (waitingForStart.get()) {
                try {
                    waitingForStart.wait();
                } catch (final InterruptedException e) {
                    LOGGER.warn("Interrupted waiting for start, will try again if running is still true", e);
                }
            }
        }

        final long startTime = waitingForStart.getStartTime();
        if (startTime >= 0) {
            LOGGER.info("Waiting until {} to start", startTime);
            globalClock.waitUntilTime(startTime);
        }

        LOGGER.info("Starting the client");
        // need a relative clock to be compatible with the demand files
        final VirtualClock clock = new SimpleClock();
        clock.startClock();

        long latestStop = 0;

        final UnmodifiableIterator<ClientLoad> iter = clientRequests.iterator();
        while (running.get() && iter.hasNext()) {
            final ClientLoad req = iter.next();

            LOGGER.trace("Waiting for request start time: {}", req.getStartTime());

            clock.waitUntilTime(req.getStartTime());
            LOGGER.info("Applying client request: {}", req);

            final ServiceIdentifier<?> service = req.getService();
            final ClientServiceConfiguration serviceConfig = clientServiceConfigs.get(service);
            Objects.requireNonNull(serviceConfig, "Unable to find client service configuration for " + service);

            for (int clientIndex = 0; clientIndex < req.getNumClients(); ++clientIndex) {
                try {
                    LOGGER.info("Starting client {}", clientIndex);
                    final ClientInstance client = startClient(serviceConfig, req, clientIndex);
                    synchronized (clients) {
                        clients.add(client);
                    }

                    final long duration = Math.max(req.getNetworkDuration(), req.getServerDuration())
                            + CLIENT_STOP_FINISH_MS;
                    stopTimer.schedule(new TimerTask() {
                        public void run() {
                            // force client to stop if it hasn't already
                            stopClient(client);
                        }
                    }, duration);

                    final long endTime = clock.getCurrentTime() + duration;
                    latestStop = Math.max(latestStop, endTime);
                } catch (final Exception e) {
                    LOGGER.error("Error starting client {}, skipping", clientIndex, e);
                }
            }

        } // while running and demand left to execute

        // wait until all clients are done
        LOGGER.info("Waiting for clients to finish");
        clock.waitUntilTime(latestStop + CLIENT_WAIT_FINISH_MS);

        LOGGER.info("Client driver thread finished.");
    }

    private final Map<String, Integer> clientNames = new HashMap<>();

    /**
     * Get the name of the client for logging purposes.
     * 
     * @param request
     *            the request
     * @param clientIndex
     *            the index of the request
     * @return a string suitable for a logger name
     */
    private String getClientName(final ClientLoad request, final int clientIndex) {
        final ApplicationCoordinates serviceIdentifier = request.getService();
        final String baseName = String.format("%s.%s.%s_%d_%d", serviceIdentifier.getGroup(),
                serviceIdentifier.getArtifact(), serviceIdentifier.getVersion(), request.getStartTime(), clientIndex);
        final int counter = clientNames.getOrDefault(baseName, 0);

        clientNames.put(baseName, counter + 1);
        return String.format("%s_%d", baseName, counter);
    }

    private ClientInstance startClient(final ClientServiceConfiguration serviceConfig,
            final ClientLoad request,
            final int clientIndex) {

        final String clientName = getClientName(request, clientIndex);

        switch (serviceConfig.getStartType()) {
        case EXTERNAL_PROCESS:
            final ProcessBuilder builder = clientProcessBuilders.get(serviceConfig.getService());
            Objects.requireNonNull(builder, "No process builder found for service " + serviceConfig.getService());
            return startClientProcess(request, clientName, builder);
        case JAVA_CLASS:
            return startJavaClient(serviceConfig, request, clientName);
        default:
            throw new IllegalArgumentException("Unknown start type: " + serviceConfig.getStartType() + " for service "
                    + serviceConfig.getService());
        }
    }

    private JavaClientProcess startJavaClient(final ClientServiceConfiguration serviceConfig,
            final ClientLoad request,
            final String clientName) {

        // only argument is the class name
        final ImmutableList<String> args = serviceConfig.getStartArguments();
        if (args.size() < 1) {
            throw new IllegalArgumentException(
                    "Must specify the class name to execute as the first start argument: " + args);
        }

        final String className = args.get(0);
        final JavaClientProcess client = new JavaClientProcess(javaClientPool, className, request, clientName);
        client.startClient();
        return client;
    }

    private ClientProcess startClientProcess(final ClientLoad request,
            final String clientName,
            final ProcessBuilder builder) {
        final ClientProcess clientProcess = new ClientProcess(builder, request, clientName);
        clientProcess.startClient();
        return clientProcess;
    }

    private void stopClient(final ClientInstance client) {
        synchronized (clients) {
            clients.remove(client);
        }
        client.stopClient();
    }

    /**
     * Get the git version information for the build.
     * 
     * @return the version or a string stating what the problem was
     */
    public static String getGitVersionInformation() {
        final URL url = ClientDriver.class.getResource("git.properties");
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
        try (ServerSocket server = new ServerSocket(SimDriver.PORT)) {
            while (true) {
                try {
                    final Socket socket = server.accept();
                    LOGGER.debug("Got connection from {}", socket.getLocalSocketAddress().toString());

                    final SimHandler handler = new SimHandler(waitingForStart, socket);
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
                                    resp.setMessage("Clients don't know how to handle SHUTDOWN");
                                    break;
                                case TOPOLOGY_UPDATE:
                                    resp.setStatus(SimResponseStatus.OK);
                                    resp.setMessage("Clients don't know how to handle TOPOLOGY_UPDATE");
                                    break;
                                default:
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
