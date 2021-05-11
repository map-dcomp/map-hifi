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
package com.bbn.map.hifi.apps.fake_load_server;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.net.URL;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.common.value.Dependency;
import com.bbn.map.hifi.client.FakeLoadClient;
import com.bbn.map.hifi.util.ActiveConnectionCountWriter;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.hifi.util.FailedRequestWriter;
import com.bbn.map.hifi.util.SimAppUtils;
import com.bbn.map.simulator.ClientLoad;
import com.bbn.map.simulator.ClientSim;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.utils.JsonUtils;
import com.bbn.map.utils.LogExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Server that generates artificial CPU load in response to client requests.
 * 
 * @author awald
 *
 */
public final class FakeLoadServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FakeLoadServer.class);

    private final CSVPrinter latencyLog;

    private static final long IDLE_THREAD_TIMEOUT_MINUTES = 3;
    private final ExecutorService threadPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, IDLE_THREAD_TIMEOUT_MINUTES,
            TimeUnit.MINUTES, new SynchronousQueue<Runnable>());

    private final ApplicationCoordinates executingService;

    /**
     * 
     * @return the service that is being executed
     */
    public ApplicationCoordinates getExecutingService() {
        return executingService;
    }

    private final ApplicationSpecification appSpec;
    private final ApplicationManagerApi applicationManager;

    private final FailedRequestWriter failedRequestWriter;

    /**
     * 
     * @return used to write failed client requests
     */
    public FailedRequestWriter getFailedRequestWriter() {
        return failedRequestWriter;
    }

    /**
     * 
     * @return the service executing, maybe null if the file is not found
     */
    private static ApplicationCoordinates readExecutingService() {
        final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper();

        try (Reader reader = Files
                .newBufferedReader(SimAppUtils.CONTAINER_INSTANCE_PATH.resolve(SimAppUtils.SERVICE_FILENAME))) {
            final ApplicationCoordinates service = mapper.readValue(reader, ApplicationCoordinates.class);
            return service;
        } catch (final IOException e) {
            LOGGER.error("Error reading container service information", e);
            return null;
        }
    }

    private static void initializeApplicationManager() {
        try {
            final Path serviceConfigPath = SimAppUtils.CONTAINER_INSTANCE_PATH
                    .resolve(Simulation.SERVICE_CONFIGURATIONS_FILENAME);
            final Path serviceDepsPath = SimAppUtils.CONTAINER_INSTANCE_PATH
                    .resolve(Simulation.SERVICE_DEPENDENCIES_FILENAME);

            AppMgrUtils.loadApplicationManager(serviceConfigPath, serviceDepsPath);
        } catch (final IOException e) {
            LOGGER.error("Error initializating application manager. Dependent load will not occur", e);
        }
    }

    private FakeLoadServer(final FailedRequestWriter failedRequestWriter) throws IOException {
        this.failedRequestWriter = failedRequestWriter;
        
        final Path latencyLogPath = SimAppUtils.CONTAINER_APP_METRICS_PATH.resolve(SimAppUtils.LATENCY_LOG_FILENAME);
        LOGGER.info("Writing latency log to {}", latencyLogPath);

        this.latencyLog = new CSVPrinter(Files.newBufferedWriter(latencyLogPath, StandardOpenOption.CREATE_NEW),
                CSVFormat.EXCEL.withHeader(SimAppUtils.SERVER_LATENCY_CSV_HEADER));
        this.executingService = readExecutingService();
        LOGGER.info("Executing service {}", this.executingService);

        this.applicationManager = AppMgrUtils.getApplicationManager();
        if (null != this.executingService) {
            this.appSpec = applicationManager.getApplicationSpecification(this.executingService);
            if (null == appSpec) {
                LOGGER.error("Cannot find application specification for {}. Dependent load will not occur.",
                        this.executingService);
            }
        } else {
            this.appSpec = null;
        }

        LOGGER.info("Executing service {}", executingService);
    }

    // private static final String DEFAULT_CPU_LOAD_PER_REQUEST_OPT =
    // "cpuLoadPerRequest";
    // private static final String DEFAULT_MEMORY_LOAD_PER_REQUEST_OPT =
    // "memoryLoadPerRequest";
    // private static final String DEFAULT_LOAD_DURATION_OPT = "loadDuration";
    // private static final String CLIENT_SPECIFIES_LOAD_OPT =
    // "clientSpecifiesLoad";
    // private static final String TEST_REQUEST_OPT = "testRequest";
    private static final String BASE_CPU_LOAD_OPT = "baseCpuLoad";
    private static final String BASE_MEMORY_LOAD_OPT = "baseMemoryLoad";
    private static final String HELP_OPT = "help";

    /**
     * Print out the usage information. Does not exit, that is up to the caller.
     * 
     * @param options
     *            the options for the command line parser
     */
    private static void printUsage(final Options options) {
        new HelpFormatter().printHelp(FakeLoadServer.class.getSimpleName(), options);
    }

    // the number of clients currently being serviced
    private AtomicInteger numberOfClients = new AtomicInteger(0);

    /**
     * Runs a fake load server.
     * 
     * @param args
     *            see the help for options
     */
    public static void main(final String[] args) {
        LogExceptionHandler.registerExceptionHandler();

        DnsUtils.configureDnsCache();

        LOGGER.info("Built from git revision {}", getGitVersionInformation());

        final Options options = new Options();

        // options.addOption(null, DEFAULT_CPU_LOAD_PER_REQUEST_OPT, true, "Set
        // a default amount of CPU load per request.");
        // options.addOption(null, DEFAULT_MEMORY_LOAD_PER_REQUEST_OPT, true,
        // "Set a default amount of Memory load per request.");
        // options.addOption(null, DEFAULT_LOAD_DURATION_OPT, true, "Set a
        // default duration per request.");
        // options.addOption(null, CLIENT_SPECIFIES_LOAD_OPT, false, "Specify
        // that load amount and duration information is to be received by the
        // requests.");
        options.addOption(null, BASE_CPU_LOAD_OPT, true,
                "Set the amount of base CPU load that the server always produces when running independent of client requests.");
        options.addOption(null, BASE_MEMORY_LOAD_OPT, true,
                "Set the amount of base Memory load that the server always produces when running independent of client requests.");
        options.addOption("h", HELP_OPT, false, "Show the help");

        final CommandLineParser parser = new DefaultParser();

        try {
            final CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption(HELP_OPT)) {
                printUsage(options);
                System.exit(0);
            }

            double baseCpu = 0.0;
            double baseMemory = 0.0;

            if (cmd.hasOption(BASE_CPU_LOAD_OPT)) {
                try {
                    baseCpu = Double.parseDouble(cmd.getOptionValue(BASE_CPU_LOAD_OPT));
                } catch (final NumberFormatException e) {
                    LOGGER.error("Invalid base percent CPU, which should be a double from 0 to 1.",
                            cmd.getOptionValue(BASE_CPU_LOAD_OPT));
                    System.exit(1);
                }
            }

            if (cmd.hasOption(BASE_MEMORY_LOAD_OPT)) {
                try {
                    baseMemory = Double.parseDouble(cmd.getOptionValue(BASE_MEMORY_LOAD_OPT));
                } catch (final NumberFormatException e) {
                    LOGGER.error("Invalid base Memory '{}', which should be a double representing Memory in Gigabytes.",
                            cmd.getOptionValue(BASE_MEMORY_LOAD_OPT));
                    System.exit(1);
                }
            }

            initializeApplicationManager();

            final FailedRequestWriter failedRequestWriter = new FailedRequestWriter();

            final FakeLoadServer fakeLoadServer = new FakeLoadServer(failedRequestWriter);
            fakeLoadServer.runServer(baseCpu, baseMemory);

        } catch (final IOException e) {
            LOGGER.error("Error initializing the application", e);
            System.exit(1);
        } catch (final ParseException e) {
            LOGGER.error("Error parsing the command line", e);
            printUsage(options);
            System.exit(1);
        }
    }

    /**
     * Runs the server to generate a base load and additional load for each
     * client request.
     * 
     * @param baseCpu
     *            constant amount of CPU load independent of client requests
     * @param baseMemory
     *            constant amount of Memory load independent of client requests
     */
    @SuppressFBWarnings(value = "DM_EXIT", justification = "Application is exiting when the server socket cannot be opened")
    private void runServer(final double baseCpu, final double baseMemory) {
        final ActiveConnectionCountWriter countWriter = new ActiveConnectionCountWriter(numberOfClients);
        countWriter.start();

        final NodeLoadExecutor nodeLoadExecutor = new NodeLoadExecutor();

        // start base load
        LOGGER.info("Base load for {} CPU and {}GB of memory", baseCpu, baseCpu, baseMemory);

        final NodeLoadGeneration baseLoad = new NodeLoadGeneration(null, nodeLoadExecutor, baseCpu, baseMemory,
                Long.MAX_VALUE);
        threadPool.submit(baseLoad);

        // open socket to accept client requests
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            serverChannel.bind(new InetSocketAddress(SimAppUtils.FAKE_LOAD_SERVER_PORT));

            LOGGER.info("Started fake load server on port {}.", serverChannel.getLocalAddress());
            LOGGER.info("Waiting for clients to connect...");

            for (int client = 0; serverChannel.isOpen(); client++) {
                final SocketChannel clientChannel = serverChannel.accept();

                // create a handler to service the client that just
                // connected
                final ClientHandler clientHandler = new ClientHandler(this, latencyLog, numberOfClients, threadPool,
                        clientChannel, client, nodeLoadExecutor);
                threadPool.submit(clientHandler);
            }
        } catch (final IOException e) {
            LOGGER.error("The server is unable to open a socket on port {}.", SimAppUtils.FAKE_LOAD_SERVER_PORT);
            System.exit(1);
        } finally {
            countWriter.stopWriting();
            LOGGER.info("Exiting");
        }

    }

    /**
     * Get the git version information for the build.
     * 
     * @return the version or a string stating what the problem was
     */
    public static String getGitVersionInformation() {
        final URL url = FakeLoadServer.class.getResource("git.properties");
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

    private static final DateTimeFormatter DEPENDENT_DIR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSSS");

    /**
     * Create and execute dependent requests if needed. The dependent requests
     * will be executed in the thread pool.
     * 
     * @param request
     *            the original client request
     */
    /* package */ void executeDependentLoad(final ClientLoad request) {
        for (final Dependency dependency : appSpec.getDependencies()) {
            final ClientLoad dependentRequest = ClientSim.createDependentRequest(request, dependency);

            LOGGER.info("Created dependent demand {} from {}", dependentRequest, request);

            final ApplicationCoordinates dependentService = dependentRequest.getService();
            final String serviceNameForLog = dependentService.getGroup() + "." + dependentService.getArtifact() + "_"
                    + dependentService.getVersion();

            final Path logPath = SimAppUtils.CONTAINER_APP_METRICS_PATH.resolve("dependent-services")
                    .resolve(serviceNameForLog).resolve(LocalDateTime.now().format(DEPENDENT_DIR_FORMAT));
            try {
                Files.createDirectories(logPath);
            } catch (final IOException e) {
                LOGGER.error("Unable to create directory for logging '{}': {}. Cannot execute dependent load.",
                        dependentService, logPath, e);
                return;
            }

            final ApplicationSpecification dependentAppSpec = applicationManager
                    .getApplicationSpecification(dependentRequest.getService());
            final String fullHostname = DnsUtils.getFullyQualifiedServiceHostname(dependentAppSpec);

            // assume client will complete on it's own and exit
            // keep it simple and only execute fake load clients
            try {
                final FakeLoadClient fakeClient = new FakeLoadClient(fullHostname, logPath, dependentRequest,
                        threadPool);
                threadPool.submit(fakeClient);
            } catch (final IOException e) {
                LOGGER.error("Unable to execute fake load client, likely an issue with the latency log path", e);
            }
        }
    }
}
