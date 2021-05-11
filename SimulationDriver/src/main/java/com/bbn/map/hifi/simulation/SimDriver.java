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
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.hifi.util.AbsoluteClock;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.hifi.util.IdentifierUtils;
import com.bbn.map.simulator.NodeFailure;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.ta2.OverlayLink;
import com.bbn.map.utils.LogExceptionHandler;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ns2.Link;
import com.bbn.protelis.networkresourcemanagement.ns2.NS2Parser;
import com.bbn.protelis.networkresourcemanagement.ns2.Node;
import com.bbn.protelis.networkresourcemanagement.ns2.Topology;
import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;

import edu.uci.ics.jung.graph.Graph;
import java8.util.Objects;

/**
 * Drive a simulation in the hi-fi environment.
 * 
 * @author jschewe
 *
 */
public class SimDriver {
    // put this first to ensure that the correct logging configuration is used
    static {
        System.setProperty("log4j.configurationFactory", MapSimLoggingConfig.class.getName());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SimDriver.class);

    private static final String SCENARIO_DIRECTORY_OPT = "configuration-directory";
    private static final String HELP_OPT = "help";
    private static final String VERSION_OPT = "version";
    private static final String AGENT_CONFIGURATION_OPT = "agentConfiguration";

    private final Graph<NodeIdentifier, OverlayLink> graph;

    /**
     * @param args
     *            see the help output
     */
    public static void main(final String[] args) {

        LogExceptionHandler.registerExceptionHandler();

        final Options options = new Options();
        options.addRequiredOption(null, SCENARIO_DIRECTORY_OPT, true,
                "The directory to read the scenario from (required)");

        options.addOption(null, AGENT_CONFIGURATION_OPT, true, "Read the agent configuration from the specified file.");

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

            if (cmd.hasOption(AGENT_CONFIGURATION_OPT)) {
                final Path path = Paths.get(cmd.getOptionValue(AGENT_CONFIGURATION_OPT));
                try {
                    AgentConfiguration.readFromFile(path);
                } catch (final IOException e) {
                    LOGGER.error("Error reading agent configuration from {}: {}", path, e.getMessage(), e);
                }
            }

            outputVersionInformation();

            final Map<String, String> nodeControlNames = DnsUtils.parseNodeControlNames();

            final Path scenarioDirectory = Paths.get(cmd.getOptionValue(SCENARIO_DIRECTORY_OPT));
            final SimDriver driver = new SimDriver(scenarioDirectory, nodeControlNames);

            driver.execute();
        } catch (final ParseException e) {
            LOGGER.error("Error parsing the command line: {}", e.getMessage());
            printUsage(options);
            System.exit(1);
        } catch (IOException e) {
            LOGGER.error("Error reading a configuration files", e);
            System.exit(1);
        }
    }

    /**
     * Print out the usage information. Does not exit, that is up to the caller.
     * 
     * @param options
     *            the options for the command line parser
     */
    private static void printUsage(final Options options) {
        new HelpFormatter().printHelp("SimDriver", options);
    }

    /**
     * Get the git version information for the build.
     * 
     * @return the version or a string stating what the problem was
     */
    public static String getGitVersionInformation() {
        final URL url = SimDriver.class.getResource("git.properties");
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

    private static void outputVersionInformation() {
        LOGGER.info("Git version: {}", getGitVersionInformation());
    }

    private final Map<NodeIdentifier, SimConnection> clientConnections = new HashMap<>();
    private final Map<NodeIdentifier, SimConnection> ncpConnections = new HashMap<>();
    private final Map<NodeIdentifier, SimConnection> backgroundTrafficConnections = new HashMap<>();

    private final List<NodeFailure> nodeFailures;

    private final Map<String, String> nodeControlNames;

    /**
     * 
     * @param scenarioDirectory
     *            the directory containing the scenario to execute.
     * @throws IOException
     *             if there is an error loading the node failures
     * @param nodeControlNames
     *            mapping from node name to hostname on the control network
     */
    public SimDriver(@Nonnull final Path scenarioDirectory, @Nonnull final Map<String, String> nodeControlNames)
            throws IOException {
        final Topology topology = NS2Parser.parse("hifi-Sim", scenarioDirectory);

        graph = parseGraph(topology);

        final Path nodeFailuresPath = scenarioDirectory.resolve(Simulation.NODE_FAILURES_FILENAME);
        nodeFailures = NodeFailure.loadNodeFailures(nodeFailuresPath);
        // assuming node failures were validated by StageExperiment

        this.nodeControlNames = Collections.unmodifiableMap(Objects.requireNonNull(nodeControlNames));

        createConnections(topology);

        // make sure all NCPs have a current topology
        broadcastTopologyUpdate();
    }

    private static final class DriverGraphFactory implements MapUtils.GraphFactory<NodeIdentifier, OverlayLink> {

        @Override
        public NodeIdentifier createVertex(@Nonnull final Node node) {
            return IdentifierUtils.getNodeIdentifier(node.getName());
        }

        @Override
        public OverlayLink createEdge(@Nonnull final Link link,
                @Nonnull final NodeIdentifier left,
                @Nonnull final NodeIdentifier right) {
            return new OverlayLink(left, right);
        }

    }

    /**
     * Convert a topology into a graph of neighbors. This walks through switches
     * to create direct neighbor links.
     * 
     * @param topology
     *            the topology to process
     * @return the resulting graph
     */
    @Nonnull
    public static Graph<NodeIdentifier, OverlayLink> parseGraph(@Nonnull final Topology topology) {
        final DriverGraphFactory factory = new DriverGraphFactory();
        final Graph<NodeIdentifier, OverlayLink> graph = MapUtils.parseTopology(topology, factory);
        return graph;
    }

    /**
     * Create connections to all nodes in the topology. Throws an exception if
     * it's unable to connect to a node.
     */
    private void createConnections(final Topology topology) {
        // separate loops so that the connections can be collected into the
        // proper maps
        // in parallel
        createClientConnections(topology);
        createNcpConnections(topology);
        createBackgroundTrafficConnections(topology);
    }

    private void createBackgroundTrafficConnections(final Topology topology) {
        final Map<NodeIdentifier, SimConnection> localNcpConnections = topology.getNodes().entrySet().parallelStream()
                .map(Map.Entry::getValue).map(node -> {
                    final NodeIdentifier id = IdentifierUtils.getNodeIdentifier(node.getName());
                    LOGGER.trace("Creating connection to background traffic {}", id);

                    final SimConnection connection = new SimConnection(id, BACKGROUND_TRAFFIC_PORT, nodeControlNames);
                    if (!connection.isConnected()) {
                        throw new RuntimeException("Unable to connect to " + node);
                    }

                    return connection;
                }).collect(Collectors.toMap(SimConnection::getNodeId, c -> c));
        backgroundTrafficConnections.putAll(localNcpConnections);
    }

    private void createNcpConnections(final Topology topology) {
        final Map<NodeIdentifier, SimConnection> localNcpConnections = topology.getNodes().entrySet().parallelStream()
                .map(Map.Entry::getValue).filter(MapUtils::isNcp).map(node -> {
                    final NodeIdentifier id = IdentifierUtils.getNodeIdentifier(node.getName());
                    LOGGER.trace("Creating connection to NCP {}", id);

                    final SimConnection connection = new SimConnection(id, PORT, nodeControlNames);
                    if (!connection.isConnected()) {
                        throw new RuntimeException("Unable to connect to " + node);
                    }

                    return connection;
                }).collect(Collectors.toMap(SimConnection::getNodeId, c -> c));
        ncpConnections.putAll(localNcpConnections);
    }

    private void createClientConnections(final Topology topology) {
        final Map<NodeIdentifier, SimConnection> localClientConnections = topology.getNodes().entrySet()
                .parallelStream().map(Map.Entry::getValue).filter(Node::isClient).map(node -> {
                    final NodeIdentifier id = IdentifierUtils.getNodeIdentifier(node.getName());
                    LOGGER.trace("Creating connection to client {}", id);

                    final SimConnection connection = new SimConnection(id, PORT, nodeControlNames);
                    if (!connection.isConnected()) {
                        throw new RuntimeException("Unable to connect to " + node);
                    }

                    return connection;
                }).collect(Collectors.toMap(SimConnection::getNodeId, c -> c));
        clientConnections.putAll(localClientConnections);
    }

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Port that the clients and NCPs are listening on for connections.
     */
    public static final int PORT = 64000;
    /**
     * Port that the background traffic drivers listen on for connections.
     */
    public static final int BACKGROUND_TRAFFIC_PORT = PORT + 1;

    private static final int MAX_START_ATTEMPTS = 10;
    private static final Duration SLEEP_WAIT_DURATION = Duration.ofSeconds(1);

    /**
     * Time to wait before starting the agent algorithms.
     */
    private static final Duration ALGORITHM_START_WAIT = Duration.ofMinutes(10);
    /**
     * Time to wait after starting the agent algorithms before starting the
     * clients.
     */
    private static final Duration CLIENT_START_WAIT = Duration.ofMinutes(5);

    private void execute() {
        LOGGER.info("Top of run for simulation driver");

        running.set(true);

        final VirtualClock globalClock = new AbsoluteClock();
        globalClock.startClock();

        final long globalNow = globalClock.getCurrentTime();

        // start the algorithms and clients
        // this is a global time so that we can rely on NTP in clients and
        // agents to resolve this time
        final long algorithmStartTime = globalNow + ALGORITHM_START_WAIT.toMillis();
        final long clientStartTime = algorithmStartTime + CLIENT_START_WAIT.toMillis();

        final boolean allAgentsStarted = ncpConnections.entrySet().parallelStream() //
                .allMatch(e -> {
                    final NodeIdentifier nodeId = e.getKey();
                    final SimConnection connection = e.getValue();

                    LOGGER.trace("Algorithms: Sending start to {}:{}", nodeId, connection.getPort());
                    boolean success = false;
                    int attempt = 1;
                    while (!success && attempt < MAX_START_ATTEMPTS) {
                        success = connection.sendStart(clientStartTime);
                        if (!success) {
                            LOGGER.warn("Got error sending start to {}:{}, trying again (attempt {})", nodeId,
                                    connection.getPort(), attempt);
                            try {
                                Thread.sleep(SLEEP_WAIT_DURATION.toMillis());
                            } catch (final InterruptedException ie) {
                                LOGGER.warn("Got interrupted in sleep between sending start events", ie);
                            }
                        } else {
                            return true;
                        }

                        ++attempt;
                    }
                    LOGGER.error("All attempts to send start to {}:{} failed", nodeId, connection.getPort());
                    return false;
                });

        if (!allAgentsStarted) {
            LOGGER.error("Some agents failed to get the start message, aborting.");
            return;
        }

        final boolean allClientsStarted = Stream.concat(clientConnections.entrySet().parallelStream(),
                backgroundTrafficConnections.entrySet().parallelStream()).allMatch(e -> {
                    final NodeIdentifier nodeId = e.getKey();
                    final SimConnection connection = e.getValue();

                    LOGGER.trace("Client or Background Traffic: Sending start to {}:{}", nodeId, connection.getPort());
                    boolean success = false;
                    int attempt = 1;
                    while (!success && attempt < MAX_START_ATTEMPTS) {
                        success = connection.sendStart(clientStartTime);
                        if (!success) {
                            LOGGER.warn("Got error sending start to {}:{}, trying again (attempt {})", nodeId,
                                    connection.getPort(), attempt);
                            try {
                                Thread.sleep(SLEEP_WAIT_DURATION.toMillis());
                            } catch (final InterruptedException ie) {
                                LOGGER.warn("Got interrupted in sleep between sending start events", ie);
                            }
                        } else {
                            return true;
                        }

                        ++attempt;
                    }
                    LOGGER.error("All attempts to send start to {}:{} failed", nodeId, connection.getPort());
                    return false;
                });

        if (!allClientsStarted) {
            LOGGER.error("Some clients or background traffic generators failed to get the start message, aborting.");
            return;
        }

        // wait until the clients are to start
        LOGGER.info("Waiting until the client start time to start the simulation");
        globalClock.waitUntilTime(clientStartTime);

        LOGGER.info("Starting simulation driver");

        // start the simulation clock
        final VirtualClock simulationClock = new SimpleClock();
        simulationClock.startClock();

        simulateFailures(simulationClock);

        LOGGER.info("Finished simulation driver");
    }

    private NodeIdentifier chooseNodeToFail(final NodeFailure failure) {
        if (null == failure.nodes || failure.nodes.isEmpty()) {
            LOGGER.error("Node failure object has null or empty nodes list");
            return null;
        } else if (failure.nodes.size() == 1) {
            return IdentifierUtils.getNodeIdentifier(failure.nodes.stream().findFirst().get());
        } else {
            LOGGER.warn(
                    "Choosing a node based on an algorithm is currently unsupported, the first node in the list will be shutdown");
            return IdentifierUtils.getNodeIdentifier(failure.nodes.stream().findFirst().get());
        }
    }

    private void simulateFailures(final VirtualClock simClock) {
        final ExecutorService threadPool = Executors.newCachedThreadPool();

        for (final NodeFailure failure : nodeFailures) {
            LOGGER.trace("Waiting for failure time {}", failure.time);
            simClock.waitUntilTime(failure.time);

            if (!running.get()) {
                LOGGER.info("Exiting failure thread due to simulation shutdown (after wait)");
                return;
            }

            if (failure.nodes.isEmpty()) {
                LOGGER.warn("Found node failure at " + failure.time + " with no nodes, skipping");
                continue;
            }

            final NodeIdentifier nodeId = chooseNodeToFail(failure);
            if (null == nodeId) {
                LOGGER.error("Unable to find a node to fail out of {}", failure.nodes);
            } else {
                if (ncpConnections.containsKey(nodeId)) {
                    LOGGER.info("Simulating failure on node {}", nodeId);

                    final SimConnection connection = ncpConnections.get(nodeId);

                    // don't let one slow node kill the rest of the simulated
                    // failures
                    threadPool.submit(() -> {
                        LOGGER.debug("Sending shutdown command to {}", nodeId);
                        final boolean result = connection.sendShutdown();
                        LOGGER.debug("Got shutdown result from {} of {}", nodeId, result);
                    });

                    // don't try and send anything more to the node after it's
                    // been shutdown
                    ncpConnections.remove(nodeId);

                    graph.removeVertex(nodeId);
                    broadcastTopologyUpdate();
                } else {
                    LOGGER.error("Unable to find {} as a running node or a running container", nodeId);
                }

                if (!running.get()) {
                    LOGGER.info("Exiting failure thread due to simulation shutdown (after simulated failure)");
                    return;
                }
            }
        }
        LOGGER.info("All simulated failures executed. Exiting simulate failures thread");
    }

    private void broadcastTopologyUpdate() {
        final TopologyUpdateMessage msg = createTopologyUpdate();

        ncpConnections.entrySet().parallelStream().map(Map.Entry::getValue).forEach(connection -> {
            final boolean result = connection.sendTopologyUpdate(msg);
            LOGGER.debug("Result of sending topology update to {}: {}", connection.getNodeId().getName(), result);
        });
    }

    private TopologyUpdateMessage createTopologyUpdate() {
        synchronized (graph) {
            return createTopologyUpdate(graph);
        }
    }

    /**
     * Convert the graph into a topology update message.
     * 
     * @param graph
     *            the graph to process
     * @return the resulting message
     */
    @Nonnull
    public static TopologyUpdateMessage createTopologyUpdate(@Nonnull final Graph<NodeIdentifier, OverlayLink> graph) {
        final Set<String> nodes = graph.getVertices().stream().map(NodeIdentifier::getName).collect(Collectors.toSet());
        final Set<TopologyUpdateMessage.Link> links = graph.getEdges().stream()
                .map(e -> new TopologyUpdateMessage.Link(e.getLeft().getName(), e.getRight().getName()))
                .collect(Collectors.toSet());

        final TopologyUpdateMessage msg = new TopologyUpdateMessage(nodes, links);
        return msg;
    }
}
