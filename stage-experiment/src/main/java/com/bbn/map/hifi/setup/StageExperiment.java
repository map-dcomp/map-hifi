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
package com.bbn.map.hifi.setup;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xbill.DNS.Address;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.Controller;
import com.bbn.map.ControllerProperties;
import com.bbn.map.ServiceConfiguration;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.hifi.HiFiAgent;
import com.bbn.map.hifi.MapAgentLoggingConfig;
import com.bbn.map.hifi.client.ClientDriver;
import com.bbn.map.hifi.client.ClientServiceConfiguration;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.hifi_resmgr.SimpleDockerResourceManager;
import com.bbn.map.simulator.BackgroundNetworkLoad;
import com.bbn.map.simulator.ClientLoad;
import com.bbn.map.simulator.HardwareConfiguration;
import com.bbn.map.simulator.NodeFailure;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.simulator.SimulationRunner;
import com.bbn.map.utils.JsonUtils;
import com.bbn.map.utils.LogExceptionHandler;
import com.bbn.map.utils.MapUtils;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkServerProperties;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ns2.Link;
import com.bbn.protelis.networkresourcemanagement.ns2.NS2Parser;
import com.bbn.protelis.networkresourcemanagement.ns2.NetworkDevice;
import com.bbn.protelis.networkresourcemanagement.ns2.Node;
import com.bbn.protelis.networkresourcemanagement.ns2.Switch;
import com.bbn.protelis.networkresourcemanagement.ns2.Topology;
import com.diffplug.common.base.Errors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Create directory for use with ansible to stage an experiment.
 *
 */
public final class StageExperiment {
    // put this first to ensure that the correct logging configuration is used
    static {
        System.setProperty("log4j.configurationFactory", MapAgentLoggingConfig.class.getName());
    }

    private static final Logger LOGGER = LogManager.getLogger(StageExperiment.class);

    /**
     * Used for the static entries.
     */
    private static final int DEFAULT_TTL = 86400;

    private final Topology topology;
    private final ConfigOptions configOptions;
    private final Map<Node, String> nodeToRegion;
    private final Map<Node, InetAddress> nodeToPrimaryIp;
    private final Map<Node, Set<InetAddress>> nodeToAllAddresses;
    /**
     * Region name -> DNS servers in the region.
     */
    private final Map<String, Set<Node>> regionToDns;
    /**
     * The class C subnets found in the topology.
     */
    private final Set<Subnet> subnets;

    /**
     * Region name -> nodes in the region.
     */
    private final Map<String, Set<Node>> regionNodes;

    /**
     * node -> container IP -> container name.
     * 
     * This is computed in {@link #assignIpAddresses()} or
     * {@link #computeContainerNames(Topology, Path)} depending on if auto-ip
     * addresses are used.
     */
    private final Map<Node, Map<InetAddress, String>> nodeToContainerAddresses = new HashMap<>();

    private final Map<Subnet, String> subnetToRegion;

    /**
     * Make sure that no nodes have a null region.
     */
    private static void validateRegionInformation(@Nonnull final Topology topology) {
        topology.getNodes().forEach((name, node) -> {
            if (!MapUtils.isUnderlay(node)) {
                final String region = NetworkServerProperties.parseRegionName(node.getExtraData());
                Objects.requireNonNull(region, String.format("%s has no region", name));
            }
        });
    }

    /**
     * Check that all NCPs have hardware specified.
     */
    private void validateHardwareInformation() {
        topology.getNodes().forEach((name, node) -> {
            if (MapUtils.isNcp(node)) {
                final String configName = node.getHardware();

                Objects.requireNonNull(configName, String.format("%s has no hardware specified", name));

                if (!hardwareConfigs.containsKey(configName)) {
                    throw new RuntimeException(String
                            .format("%s uses hardware config %s that is not know to the scenario", name, configName));
                }
            }
        });
    }

    /**
     * If true, then write out the IP addresses. This is true when auto-ip
     * assignment is done.
     */
    private final boolean autoAssignIpAddresses;

    private final ImmutableMap<String, HardwareConfiguration> hardwareConfigs;

    private final ImmutableList<BackgroundNetworkLoad> backgroundRequests;

    private StageExperiment(@Nonnull final ConfigOptions configOptions, @Nonnull final Topology topology) {
        this.topology = topology;
        this.configOptions = configOptions;

        try {
            this.hardwareConfigs = HardwareConfiguration.parseHardwareConfigurations(
                    configOptions.scenarioPath.resolve(HardwareConfiguration.HARDWARE_CONFIG_FILENAME));
        } catch (final IOException e) {
            throw new RuntimeException("Error parsing hardware configurations", e);
        }
        validateHardwareInformation();

        validateRegionInformation(topology);

        this.nodeToRegion = this.topology.getNodes().entrySet().stream().map(Map.Entry::getValue)
                .filter(n -> !MapUtils.isUnderlay(n))
                .collect(Collectors.toMap(n -> n, n -> NetworkServerProperties.parseRegionName(n.getExtraData())));
        regionNodes = computeRegionNodes(this.topology, this.nodeToRegion);
        assertNodeNamesValid(topology, regionNodes);

        autoAssignIpAddresses = assignIpAddresses();

        nodeToAllAddresses = determineAllAddressesForNodes(topology);
        LOGGER.debug("Addresses {}", nodeToAllAddresses);

        if (!autoAssignIpAddresses) {
            computeContainerNames(this.topology, this.configOptions.containerNamesDirectory);
        }

        nodeToPrimaryIp = determinePrimaryIpForLinks(this.topology, this.nodeToRegion, this.nodeToContainerAddresses);

        regionToDns = determineDnsServers(this.topology, this.nodeToRegion);
        subnets = computeSubnets(this.topology);

        subnetToRegion = buildSubnetToRegionMap(this.regionNodes, this.nodeToPrimaryIp, this.subnets);

        assertRegionNamesValid(this.subnetToRegion);
        assertContainerNamesValid(this.nodeToContainerAddresses, this.nodeToAllAddresses);

        this.backgroundRequests = loadBackgroundTraffic();
        assignBackgroundTrafficPorts(backgroundRequests);
        verifyBackgroundTraffic(this.backgroundRequests);

        verifyClientDemandServices();

        verifyAgentConfiguration();

        verifyServiceConfiguration();
    }

    private void verifyServiceConfiguration() {
        final Path configFile;
        if (null == configOptions.serviceConfigFile) {
            configFile = configOptions.scenarioPath.resolve(Simulation.SERVICE_CONFIGURATIONS_FILENAME);
        } else {
            configFile = Paths.get(configOptions.serviceConfigFile);
            if (!Files.exists(configFile)) {
                throw new RuntimeException("Specified service configurations file doesn't exist: " + configFile);
            }
        }

        try {
            final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> serviceConfigs = ServiceConfiguration
                    .parseServiceConfigurations(configFile);
            serviceConfigs.forEach((service, config) -> {
                final RegionIdentifier defaultRegion = config.getDefaultNodeRegion();
                final Set<Node> nodes = regionNodes.get(defaultRegion.getName());
                if (null == nodes) {
                    throw new RuntimeException(
                            "Default region for " + service + " is the unknown region " + defaultRegion);
                }

                final Set<String> defaultNodeNames = config.getDefaultNodes().entrySet().stream().map(Map.Entry::getKey)
                        .map(NodeIdentifier::getName).collect(Collectors.toSet());

                final Optional<Node> found = nodes.stream().filter(n -> defaultNodeNames.contains(n.getName()))
                        .findAny();
                if (!found.isPresent()) {
                    throw new RuntimeException("None of the default nodes for " + service
                            + " are in the default region of " + defaultRegion);
                }
            });
        } catch (final IOException e) {
            throw new RuntimeException("Error parsing service configurations", e);
        }
    }

    /**
     * Check that there aren't any parse errors.
     */
    private void verifyAgentConfiguration() {
        if (null != configOptions.agentConfiguration) {
            try {
                final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper();
                try (BufferedReader reader = Files.newBufferedReader(configOptions.agentConfiguration)) {
                    mapper.readValue(reader, AgentConfiguration.class);
                }
            } catch (final IOException e) {
                throw new RuntimeException("Error parsing agent configuration", e);
            }
        }
    }

    private Set<ApplicationCoordinates> getServicesExecuted(final Node node) {
        if (Files.exists(configOptions.demandDir)) {
            final Path clientDemand = configOptions.demandDir.resolve(node.getName() + ".json");
            if (Files.exists(clientDemand)) {
                final ImmutableList<ClientLoad> clientRequests = ClientLoad.parseClientDemand(clientDemand);
                final Set<ApplicationCoordinates> servicesToExecute = clientRequests.stream().map(r -> r.getService())
                        .collect(Collectors.toSet());
                return servicesToExecute;
            } else {
                return Collections.emptySet();
            }
        } else {
            return Collections.emptySet();
        }
    }

    private void verifyClientDemandServices() {
        try {
            final ImmutableMap<ApplicationCoordinates, ClientServiceConfiguration> clientServiceConfigs = ClientServiceConfiguration
                    .parseClientServiceConfigurations(this.configOptions.clientServiceConfigFile);

            final Path serviceConfigPath;
            if (null == configOptions.serviceConfigFile) {
                serviceConfigPath = configOptions.scenarioPath.resolve(Simulation.SERVICE_CONFIGURATIONS_FILENAME);
            } else {
                serviceConfigPath = Paths.get(configOptions.serviceConfigFile);
            }
            final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> serviceConfigurations = ServiceConfiguration
                    .parseServiceConfigurations(serviceConfigPath);

            final Set<Node> clientNodes = topology.getNodes().entrySet().stream().map(Map.Entry::getValue)
                    .collect(Collectors.toSet());

            clientNodes.forEach(node -> {
                final Set<ApplicationCoordinates> servicesExecuted = getServicesExecuted(node);
                servicesExecuted.forEach(service -> {
                    if (!clientServiceConfigs.containsKey(service)) {
                        throw new RuntimeException("Client " + node.getName() + " executes service " + service
                                + " which is not in the client service configurations");
                    }

                    if (!serviceConfigurations.containsKey(service)) {
                        throw new RuntimeException("Client " + node.getName() + " executes service " + service
                                + " which is not in the service configurations");
                    }

                });
            });

        } catch (final IOException e) {
            throw new RuntimeException("Error reading client service configurations", e);
        }
    }

    /**
     * Maximum amount of time that can be passed to iperf for the time to run.
     * This is found by passing a large number to iperf and reading the error
     * message.
     */
    private static final Duration MAXIMUM_IPERF_TIME = Duration.ofSeconds(86400);

    private static void verifyBackgroundTraffic(final ImmutableList<BackgroundNetworkLoad> backgroundRequests) {
        for (final BackgroundNetworkLoad req : backgroundRequests) {
            if (req.getRxPort() < 0) {
                throw new RuntimeException("Background traffic rx port must be greater than or equal to zero");
            }
            if (req.getTxPort() < 0) {
                throw new RuntimeException("Background traffic rx port must be greater than or equal to zero");
            }
            if (!req.getNetworkLoad().containsKey(LinkAttribute.DATARATE_RX)
                    && !req.getNetworkLoad().containsKey(LinkAttribute.DATARATE_TX)) {
                throw new RuntimeException("Background traffic must have rx or tx value");
            }

            final Duration time = Duration.ofMillis(req.getNetworkDuration());
            if (time.compareTo(MAXIMUM_IPERF_TIME) > 0) {
                throw new RuntimeException("Duration of background traffic request cannot exceed "
                        + MAXIMUM_IPERF_TIME.getSeconds() + " seconds");
            }
        }
    }

    private ImmutableList<BackgroundNetworkLoad> loadBackgroundTraffic() {
        final Path backgroundTrafficPath;
        if (null != configOptions.demandDir) {
            backgroundTrafficPath = configOptions.demandDir.resolve(Simulation.BACKGROUND_TRAFFIC_FILENAME);
        } else {
            backgroundTrafficPath = null;
        }
        if (null == backgroundTrafficPath) {
            return ImmutableList.of();
        } else {
            if (!Files.exists(backgroundTrafficPath)) {
                return ImmutableList.of();
            } else {
                return BackgroundNetworkLoad.parseBackgroundTraffic(backgroundTrafficPath);
            }
        }
    }

    /**
     * Check that the node failures are sane.
     */
    private boolean verifyNodeFailures(final Topology topology, final Node globalLeader) {
        try {
            final Path nodeFailuresPath = configOptions.scenarioPath.resolve(Simulation.NODE_FAILURES_FILENAME);
            final List<NodeFailure> nodeFailures = NodeFailure.loadNodeFailures(nodeFailuresPath);

            boolean retval = true;
            for (final NodeFailure failure : nodeFailures) {
                if (failure.nodes.size() > 1 && (null == failure.chooseAlgorithm || null == failure.nodeMetric)) {
                    LOGGER.error(
                            "The failure at {} with nodes {} has a null choose algorithm or node metric. The node to fail cannot be determined in this case",
                            failure.time, failure.nodes);
                    retval = false;
                }

                final ImmutableMap<String, Node> topologyNodes = topology.getNodes();

                for (final String nodeName : failure.nodes) {
                    final Node node = topologyNodes.get(nodeName);
                    if (null == node) {
                        LOGGER.error("The node {} is not known", nodeName);
                        retval = false;
                    } else {
                        final Map<String, Object> extraData = node.getExtraData();

                        final boolean runningDcop = ControllerProperties.isRunningDcop(extraData);
                        final boolean runningRLG = ControllerProperties.isRunningRlg(extraData);
                        final boolean runningDns = ControllerProperties.isHandlingDnsChanges(extraData);

                        if (runningDcop) {
                            // can be removed later on when we have a way to
                            // handle
                            // failure
                            // of our leaders
                            LOGGER.error("The node {} is running DCOP, it cannot be marked as a failure.", node);
                            retval = false;
                        } else if (runningRLG) {
                            // can be removed later on when we have a way to
                            // handle
                            // failure
                            // of our leaders
                            LOGGER.error("The node {} is running RLG, it cannot be marked as a failure.", node);
                            retval = false;
                        } else if (runningDns) {
                            // can be removed later on when we have a way to
                            // handle
                            // failure
                            // of our leaders
                            LOGGER.error("The node {} is running DNS, it cannot be marked as a failure.", node);
                            retval = false;
                        } else if (!AgentConfiguration.getInstance().isUseLeaderElection()
                                && globalLeader.getName().equals(node.getName())) {
                            LOGGER.error(
                                    "The node {} is running the global leader and leader election is not enabled. Therefore it cannot be marked as a failure.",
                                    node);
                            retval = false;
                        }
                    }
                } // foreach node
            } // foreach failure

            return retval;
        } catch (final IOException e) {
            throw new RuntimeException("Error reading in node failures", e);
        }
    }

    /**
     * Make sure that all node names are valid DNS labels. Also check that no
     * node has the name of a region.
     */
    private static void assertNodeNamesValid(final Topology topology, final Map<String, Set<Node>> regionNodes) {
        final Set<String> nodeNamesLower = new HashSet<>();
        final Set<String> invalid = new HashSet<>();
        topology.getNodes().forEach((name, node) -> {
            if (!DnsUtils.isValidDomainLabel(name)) {
                invalid.add(name);
            }
            nodeNamesLower.add(name.toLowerCase());
        });
        if (!invalid.isEmpty()) {
            throw new RuntimeException("The following node names are not valid: " + invalid);
        }

        // make sure no node has the name of a region
        final Set<String> nodeRegionMatches = regionNodes.entrySet().stream().map(Map.Entry::getKey)
                .filter(region -> nodeNamesLower.contains(region.toLowerCase())).collect(Collectors.toSet());
        if (!nodeRegionMatches.isEmpty()) {
            throw new RuntimeException(
                    "The following names are both region and node names. This causes problems with DNS."
                            + nodeRegionMatches);
        }
    }

    /**
     * Make sure that all region names are valid DNS labels.
     */
    private static void assertRegionNamesValid(final Map<Subnet, String> regionToSubnet) {
        final Set<String> invalidRegionNames = regionToSubnet.entrySet().stream().map(Map.Entry::getValue)
                .filter(region -> !DnsUtils.isValidDomainLabel(region)).collect(Collectors.toSet());
        if (!invalidRegionNames.isEmpty()) {
            throw new RuntimeException("The following region names are not valid: " + invalidRegionNames);
        }
    }

    /**
     * @throws RuntimeException
     *             if the subnet is mapped to multiple regions
     * @return subnet -> region name
     */
    private static Map<Subnet, String> buildSubnetToRegionMap(final Map<String, Set<Node>> regionNodes,
            final Map<Node, InetAddress> nodeToPrimaryIp,
            final Set<Subnet> subnets) {
        final Map<Subnet, String> subnetToRegion = new HashMap<>();

        regionNodes.forEach((region, nodes) -> {
            nodes.forEach(node -> {
                final InetAddress nodePrimaryIp = nodeToPrimaryIp.get(node);
                Objects.requireNonNull(nodePrimaryIp, "No primary IP for: " + node.getName());

                final InetAddress nodeSubnetPrefix = getSubnetPrefix(nodePrimaryIp);

                final Subnet regionSubnet = subnets.stream().filter(s -> nodeSubnetPrefix.equals(s.getPrefix()))
                        .findFirst().orElse(null);
                if (subnetToRegion.containsKey(regionSubnet)) {
                    final String prevRegion = subnetToRegion.get(regionSubnet);
                    if (!Objects.equals(prevRegion, region)) {
                        final String message = String.format("Found multiple regions (%s, %s) for the same subnet: %s",
                                prevRegion, region, regionSubnet.getPrefix());
                        LOGGER.error(message);
                        throw new RuntimeException(message);
                    }
                } else {
                    subnetToRegion.put(regionSubnet, region);
                }
            });
        }); // foreach region

        return subnetToRegion;
    }

    /**
     * Compute the region to set of nodes in the region. Excludes underlay
     * nodes.
     */
    private static Map<String, Set<Node>> computeRegionNodes(final Topology topology,
            final Map<Node, String> nodeToRegion) {
        final Map<String, Set<Node>> lRegionNodes = new HashMap<>();
        topology.getNodes().forEach((name, node) -> {
            // underlay nodes don't have a region
            if (!MapUtils.isUnderlay(node)) {
                final String region = nodeToRegion.get(node);
                lRegionNodes.computeIfAbsent(region, k -> new HashSet<>()).add(node);
            }
        });
        return lRegionNodes;
    }

    private static Map<String, Set<Node>> determineDnsServers(final Topology topology,
            final Map<Node, String> nodeToRegion) {
        final Map<String, Set<Node>> lRegionToDns = new HashMap<>();
        topology.getNodes().forEach((name, node) -> {
            if (ControllerProperties.isHandlingDnsChanges(node.getExtraData())) {
                final String region = nodeToRegion.get(node);
                lRegionToDns.computeIfAbsent(region, k -> new HashSet<>()).add(node);
            }
        });
        return lRegionToDns;
    }

    /** the ending of a file that lists the neighbors of a node */
    private static final String NODE_NEIGHBOR_FILE_SUFFIX = ".neighbors";

    private static final String SCENARIO_NAME_OPT = "scenario-name";

    private static final String SCENARIO_OPT = "scenario";
    private static final String OUTPUT_OPT = "output";
    private static final String HELP_OPT = "help";
    private static final String AGENT_JAR_OPT = "agent-jar";
    private static final String EMULAB_EXPERIMENT_OPT = "emulab-experiment";
    private static final String EMULAB_GROUP_OPT = "emulab-group";
    private static final String EMULAB_GROUP_DEFAULT = "a3";
    private static final String CONTAINER_NAMES_OPT = "container-names-directory";
    private static final String DOCKER_REGISTRY_HOSTNAME_OPT = "docker-registry-hostname";

    private static final String DUMP_DIRECTORY_OPT = "dumpDirectory";
    private static final String DUMP_INTERVAL_OPT = "dumpInterval";
    private static final String DUMP_ENABLED_OPT = "dumpEnabled";

    private static final String DNS_JAR_OPT = "dns-jar";

    private static final String CLIENT_JAR_OPT = "client-jar";
    private static final String CLIENT_PRE_START_JAR_OPT = "client-pre-start-jar";
    private static final String DEMAND_OPT = "demand";
    private static final String CLIENT_SERVICE_CONFIG_OPT = "client-service-config";

    private static final String SERVICE_CONFIG_OPT = "service-config";

    private static final String BASTION_HOST_OPT = "bastion";

    private static final String FIREWALL_OPT = "enableFirewall";

    private static final String AGENT_CONFIGURATION_OPT = "agentConfiguration";

    private static final String SIM_DRIVER_JAR_OPT = "sim-driver-jar";

    private static final String DCOMP_PROJECT_OPT = "dcomp-project";
    private static final String DCOMP_PROJECT_DEFAULT = "map";

    private static final String BACKGROUND_TRAFFIC_DRIVER_JAR_OPT = "background-driver-jar";

    /**
     * Print out the usage information. Does not exit, that is up to the caller.
     * 
     * @param options
     *            the options for the command line parser
     */
    private static void printUsage(final Options options) {
        new HelpFormatter().printHelp(StageExperiment.class.getSimpleName(), options);
    }

    // CHECKSTYLE:OFF value class
    private static final class ConfigOptions {
        String scenarioName = null;

        Path scenarioPath;
        Path outputFolder;
        Path agentJarFile;
        Path containerNamesDirectory;

        String emulabGroup;
        String experiment;
        String dockerRegistryHostname = null;

        // dump options
        public Duration dumpInterval = Duration.ofSeconds(SimulationRunner.DEFAULT_DUMP_INTERVAL_SECONDS);
        public Path dumpDirectory = null;
        public boolean dumpEnabled = false;

        Path dnsJarFile;

        Path clientJarFile;
        Path clientPreStartJarFile;
        Path clientServiceConfigFile;
        Path demandDir;

        String bastionHostname = null;

        boolean enableFirewall = false;

        Path agentConfiguration = null;

        Path simDriverJarFile;

        String dcompProject;

        Path backgroundTrafficDriverJarFile;

        String serviceConfigFile = null;
    }
    // CHECKSTYLE:ON

    /**
     * 
     * @param args
     *            run without arguments to see all options
     */
    public static void main(String[] args) {
        LogExceptionHandler.registerExceptionHandler();

        final Options options = new Options();
        options.addOption(null, SCENARIO_NAME_OPT, true,
                "The name of the scenario (defaults to the name of the scenario directory)");
        options.addRequiredOption("s", SCENARIO_OPT, true, "The directory where the scenario is stored (required)");
        options.addRequiredOption("o", OUTPUT_OPT, true,
                "Directory to write the output to, if not specified no output is written (required)");
        options.addRequiredOption(null, AGENT_JAR_OPT, true,
                "The path to the map agent executable jar file to copy to each node (required)");
        options.addRequiredOption(null, EMULAB_EXPERIMENT_OPT, true, "The emulab or DCOMP experiment name (required)");

        options.addOption(null, EMULAB_GROUP_OPT, true,
                "The emulab group name (default: " + EMULAB_GROUP_DEFAULT + ")");

        options.addOption(null, CONTAINER_NAMES_OPT, true,
                "The directory containing the container names files (required IP addresses are specified in the topology)");

        options.addOption(null, DOCKER_REGISTRY_HOSTNAME_OPT, true,
                "Name of host to run the docker registry (Default is a randomaly chosen node)");

        options.addOption(null, DUMP_DIRECTORY_OPT, true,
                "Directory to write state to if dumping is enabled (required if " + DUMP_ENABLED_OPT + " is present)");
        options.addOption(null, DUMP_INTERVAL_OPT, true,
                "Interval to write state out at if dumping is enabled. Number of seconds or parsable duration (Default: "
                        + SimulationRunner.DEFAULT_DUMP_INTERVAL_SECONDS + " seconds)");
        options.addOption(null, DUMP_ENABLED_OPT, false,
                "If specified, then the nodes will write out their state at regular intervals");

        options.addRequiredOption(null, DNS_JAR_OPT, true,
                "The path to the map DNS server executable jar file to copy to each node (required)");

        options.addRequiredOption(null, CLIENT_JAR_OPT, true,
                "The path to the map client driver executable jar file to copy to each node (required)");
        options.addRequiredOption(null, CLIENT_PRE_START_JAR_OPT, true,
                "The path to the map client pre starrt driver executable jar file to copy to each node (required)");
        options.addRequiredOption(null, CLIENT_SERVICE_CONFIG_OPT, true,
                "The path to the client service configurations file (required)");
        options.addRequiredOption(null, DEMAND_OPT, true,
                "The path to the directory containing the client demand files (required)");

        options.addOption(null, BASTION_HOST_OPT, true,
                "The host controlling the experiment (Default is a randomaly chosen node)");

        options.addOption(null, FIREWALL_OPT, false, "Enable the firewall in the experiment. Defaults to off.");

        options.addOption(null, AGENT_CONFIGURATION_OPT, true,
                "Use the specified agent configuration. If not specified, all defaults will be used.");

        options.addRequiredOption(null, SIM_DRIVER_JAR_OPT, true,
                "The path to the simulation driver driver executable jar file to copy to the registry node (required)");

        options.addOption(null, DCOMP_PROJECT_OPT, true,
                "The dcomp project name (default: " + DCOMP_PROJECT_DEFAULT + ")");

        options.addRequiredOption(null, BACKGROUND_TRAFFIC_DRIVER_JAR_OPT, true,
                "The path to the background traffic driver executable jar file to copy to each node (required)");

        options.addOption(null, SERVICE_CONFIG_OPT, true,
                "The service configuration file to use, defaults to scenario/service-configurations.json");

        options.addOption("h", HELP_OPT, false, "Show the help");

        final CommandLineParser parser = new DefaultParser();

        try {
            final CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption(HELP_OPT)) {
                printUsage(options);
                System.exit(0);
            }

            final ConfigOptions configOptions = new ConfigOptions();

            if (cmd.hasOption(SCENARIO_NAME_OPT)) {
                configOptions.scenarioName = cmd.getOptionValue(SCENARIO_NAME_OPT);
            }

            configOptions.scenarioPath = Paths.get(cmd.getOptionValue(SCENARIO_OPT));
            configOptions.outputFolder = Paths.get(cmd.getOptionValue(OUTPUT_OPT));
            configOptions.agentJarFile = Paths.get(cmd.getOptionValue(AGENT_JAR_OPT));
            if (cmd.hasOption(CONTAINER_NAMES_OPT)) {
                configOptions.containerNamesDirectory = Paths.get(cmd.getOptionValue(CONTAINER_NAMES_OPT));
            }

            configOptions.dnsJarFile = Paths.get(cmd.getOptionValue(DNS_JAR_OPT));

            configOptions.clientJarFile = Paths.get(cmd.getOptionValue(CLIENT_JAR_OPT));
            configOptions.clientPreStartJarFile = Paths.get(cmd.getOptionValue(CLIENT_PRE_START_JAR_OPT));
            configOptions.clientServiceConfigFile = Paths.get(cmd.getOptionValue(CLIENT_SERVICE_CONFIG_OPT));
            configOptions.demandDir = Paths.get(cmd.getOptionValue(DEMAND_OPT));

            configOptions.simDriverJarFile = Paths.get(cmd.getOptionValue(SIM_DRIVER_JAR_OPT));

            configOptions.backgroundTrafficDriverJarFile = Paths
                    .get(cmd.getOptionValue(BACKGROUND_TRAFFIC_DRIVER_JAR_OPT));

            if (cmd.hasOption(EMULAB_GROUP_OPT)) {
                configOptions.emulabGroup = cmd.getOptionValue(EMULAB_GROUP_OPT);
            } else {
                configOptions.emulabGroup = EMULAB_GROUP_DEFAULT;
            }
            configOptions.experiment = cmd.getOptionValue(EMULAB_EXPERIMENT_OPT);

            if (cmd.hasOption(DOCKER_REGISTRY_HOSTNAME_OPT)) {
                configOptions.dockerRegistryHostname = cmd.getOptionValue(DOCKER_REGISTRY_HOSTNAME_OPT);
            }

            if (cmd.hasOption(DUMP_ENABLED_OPT)) {
                configOptions.dumpEnabled = true;
            }
            if (cmd.hasOption(DUMP_INTERVAL_OPT)) {
                final String str = cmd.getOptionValue(DUMP_INTERVAL_OPT);
                configOptions.dumpInterval = SimulationRunner.parseDuration(str);
                if (null == configOptions.dumpInterval) {
                    LOGGER.error("The dump interval {} cannot be parsed", str);
                    printUsage(options);
                    System.exit(1);
                }
            }
            if (cmd.hasOption(DUMP_DIRECTORY_OPT)) {
                configOptions.dumpDirectory = Paths.get(cmd.getOptionValue(DUMP_DIRECTORY_OPT));
            }
            if (configOptions.dumpEnabled) {
                // sanity check if dumping
                if (null == configOptions.dumpDirectory) {
                    LOGGER.error("The dump directory must be specified when dumping state");
                    printUsage(options);
                    System.exit(1);
                }
            }

            if (cmd.hasOption(BASTION_HOST_OPT)) {
                configOptions.bastionHostname = cmd.getOptionValue(BASTION_HOST_OPT);
            }

            if (cmd.hasOption(FIREWALL_OPT)) {
                configOptions.enableFirewall = true;
            }

            if (cmd.hasOption(AGENT_CONFIGURATION_OPT)) {
                configOptions.agentConfiguration = Paths.get(cmd.getOptionValue(AGENT_CONFIGURATION_OPT));
            }

            if (cmd.hasOption(DCOMP_PROJECT_OPT)) {
                configOptions.dcompProject = cmd.getOptionValue(DCOMP_PROJECT_OPT);
            } else {
                configOptions.dcompProject = DCOMP_PROJECT_DEFAULT;
            }

            if (cmd.hasOption(SERVICE_CONFIG_OPT)) {
                configOptions.serviceConfigFile = cmd.getOptionValue(SERVICE_CONFIG_OPT);
            }

            final Topology topology = NS2Parser.parse("dummy", configOptions.scenarioPath);
            final StageExperiment stager = new StageExperiment(configOptions, topology);
            stager.writeExperiment();

            LOGGER.info("Done");
        } catch (final ParseException e) {
            LOGGER.error("Error parsing the command line: {}", e.getMessage());
            printUsage(options);
            System.exit(1);
        } catch (final IOException e) {
            LOGGER.error("Error parsing files", e);
            System.exit(1);
        }
    }

    @SuppressFBWarnings(value = "DM_EXIT", justification = "The application shoulod exit if settings are not correct")
    private void writeExperiment() throws IOException {
        if (!Files.exists(configOptions.outputFolder)) {
            Files.createDirectory(configOptions.outputFolder);
        }

        // write local and global properties first to ensure that all properties
        // are in a sane state. These methods may assign default values to some
        // properties.
        writeLocalProperties();
        final Node globalLeader = writeGlobalProperties();

        // TODO make this a case-insensitive comparison
        // need to check after writing global properties as the hostname may
        // change
        if (null == topology.getNodes().get(configOptions.dockerRegistryHostname)) {
            LOGGER.error("The host to run the docker registry, {}, is not defined in the scenario",
                    configOptions.dockerRegistryHostname);
            System.exit(-1);
        }

        if (null == topology.getNodes().get(configOptions.bastionHostname)) {
            LOGGER.error("The host to be the bastion host, {}, is not defined in the scenario",
                    configOptions.dockerRegistryHostname);
            System.exit(-1);
        }

        writeNeighbors();

        writeAnsibleConfiguration();
        writeAnsiblePlaybook();

        final Node dockerRegistryNode = findDockerRegistry();
        createAnsibleHostsForEmulab(dockerRegistryNode);
        createAnsibleHostsForDcomp(dockerRegistryNode);

        copyServiceConfigurations();
        copyNodeConfigFiles();
        copyTopology();

        if (!verifyNodeFailures(topology, globalLeader)) {
            // cannot execute, failures were logged by the verify function
            throw new IllegalArgumentException(
                    "There were errors with the node failures. See previous log messages for the details.");
        }

        writeAgentConfiguration();

        writeRegistryInformation();

        writeEmulabScriptInformation();

        copyLoggingConfig();
        copyAgentJarFile();

        copySystemdService();

        copyResource("ansible/setup-docker-networking.py", "setup-docker-networking.py", true);
        copyResource("ansible/map_common.py", "map_common.py");
        copyResource("ansible/shutdown_network_interfaces.py", "shutdown_network_interfaces.py", true);

        copyContainerNames();

        writeDnsInformation();
        writeDnsMapping();

        writeDockerNetworkingScript();

        writeRegionSubnetInformation();

        writeClientInformation();

        writeRunScripts();

        writeSimDriverInformation();

        writeQuaggaConfig();

        writeIpToSpeed();

        writeDcompPython();
        writeEmulabPython();

        writePimdInformation();

        writeVariables(dockerRegistryNode);

        writeBackgroundTraffic();
        copyBackgroundTrafficDriverJarFile();

        copySysinfo();
    }

    private void writeBackgroundTraffic() throws IOException {
        final Path output = configOptions.outputFolder.resolve("client").resolve("demand")
                .resolve(Simulation.BACKGROUND_TRAFFIC_FILENAME);

        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            final ObjectWriter mapper = JsonUtils.getStandardMapObjectMapper().writer().withDefaultPrettyPrinter();
            mapper.writeValue(writer, this.backgroundRequests);
        }
    }

    private void writeVariables(final Node dockerRegistryNode) throws IOException {
        final Path output = configOptions.outputFolder.resolve("vars.yml");

        try (BufferedWriter writer = Files.newBufferedWriter(output, Charset.defaultCharset())) {
            writer.write(String.format("---%n"));

            final InetAddress registryIp = nodeToPrimaryIp.get(dockerRegistryNode);
            writer.write(String.format("pim_rendezvous_ip: %s%n", registryIp.getHostAddress()));
        }
    }

    private void writePimdInformation() throws IOException {
        Stream.of(new String[] { "pimd.standard.default", //
                "pimd.yml", //
        }).forEach(filename -> {
            copyResource("ansible/" + filename, filename);
        });

        Stream.of(new String[] { "configure_pimd.py", //
        }).forEach(filename -> {
            copyResource("ansible/" + filename, filename, true);
        });
    }

    private void writeAgentConfiguration() throws IOException {
        final Path output = configOptions.outputFolder.resolve(HiFiAgent.AGENT_CONFIGURATION_FILENAME);

        if (null == configOptions.agentConfiguration) {
            // write the defaults out
            final ObjectWriter mapper = Controller.createDumpWriter();
            try (BufferedWriter writer = Files.newBufferedWriter(output, Charset.defaultCharset())) {
                mapper.writeValue(writer, AgentConfiguration.getInstance());
            }
        } else {
            // copy the specified file
            Files.copy(configOptions.agentConfiguration, output);
        }

    }

    /**
     * 
     * @return true if addresses need to be assigned
     * @throws RuntimeException
     *             if some IP addresses are assigned, but not all
     */
    private boolean checkIfAutomaticIpAssignmentNeeded() {
        boolean allIpAddressesAssigned = true;
        boolean someAddressesAssigned = false;
        for (final Map.Entry<String, Node> nEntry : topology.getNodes().entrySet()) {
            final Node node = nEntry.getValue();
            for (final Link link : node.getLinks()) {
                final InetAddress addr = node.getIpAddress(link);
                if (null == addr) {
                    allIpAddressesAssigned = false;
                } else {
                    someAddressesAssigned = true;
                }
            }
        }

        if (!allIpAddressesAssigned && someAddressesAssigned) {
            throw new RuntimeException(
                    "All IP addresses may be assigned OR no IP addresses may be assigned, partial assignment is not supported");
        }

        return !allIpAddressesAssigned && !someAddressesAssigned;
    }

    private InetAddress computeIpAddress(final Node node,
            final Link link,
            final boolean interRegion,
            final Link primaryLink,
            final SubnetBlock subnet) {
        if (node.isClient()) {
            return subnet.getNextClientAddress();
        } else if (MapUtils.isUnderlay(node)) {
            return subnet.getNextUnderlayAddress();
        } else if (interRegion && !link.equals(primaryLink)) {
            return subnet.getNextInterRegionAddress();
        } else {
            Objects.requireNonNull(primaryLink, "Cannot determine the primary link for " + node.getName()
                    + ", cannot properly assign IP addresses");

            if (primaryLink.equals(link)) {

                final HardwareConfiguration hardware = hardwareConfigs.get(node.getHardware());
                final int maxContainers = hardware == null ? SubnetBlock.MAX_CONTAINERS_PER_NCP
                        : hardware.getMaximumServiceContainers();

                final Pair<InetAddress, List<InetAddress>> pair = subnet.getNcpAddress(maxContainers);

                // store the container addresses
                final Map<InetAddress, String> containerAddresses = nodeToContainerAddresses.computeIfAbsent(node,
                        k -> new HashMap<>());
                final AtomicInteger index = new AtomicInteger();
                pair.getRight().stream().forEach(containerAddress -> {
                    final String containerName = containerName(node, index.getAndIncrement());

                    if (containerAddresses.containsKey(containerAddress)) {
                        throw new RuntimeException("Container IP '" + containerAddress + "' on node '" + node.getName()
                                + "' is a duplicate");
                    }

                    containerAddresses.put(containerAddress, containerName);
                });

                return pair.getLeft();
            } else {
                return subnet.getNextInterRegionAddress();
            }
        }
    }

    private static NetworkDevice getOtherEndOfLink(final Node node, final Link link) {
        final NetworkDevice other;
        if (link.getLeft().equals(node)) {
            other = link.getRight();
        } else {
            other = link.getLeft();
        }
        return other;
    }

    /**
     * Find the link on an NCP that is primary. This is the one that links to
     * it's region and the address associated with this link is the one that the
     * containers will be based upon.
     * 
     * If the node has multiple links to it's region and more than links to
     * non-clients, then the primary link cannot be determined.
     * 
     * @param node
     *            the node to search the links of
     * @return the primary link, null if one cannot be found
     * 
     * @throws RuntimeException
     *             if the primary link cannot be determined
     */
    private static Link findPrimaryLink(final Node node) {
        final Collection<Link> links = node.getLinks();
        if (node.isClient()) {
            // primary link doesn't matter, just pick one
            return links.stream().findAny().orElse(null);
        }

        if (links.isEmpty()) {
            LOGGER.warn("Node {} doesn't have any links, this is odd", node.getName());
        } else if (1 == links.size()) {
            // just use the link
            return links.stream().findAny().orElse(null);
        }

        final String region = NetworkServerProperties.parseRegionName(node.getExtraData());

        // links that connect to nodes in the same region
        final Set<Link> linksToRegion = new HashSet<>();

        // links to the same region that do not connect to a client
        final Set<Link> linksToNonClients = new HashSet<>();
        for (final Link link : links) {
            final NetworkDevice other = getOtherEndOfLink(node, link);
            if (other instanceof Node) {
                final Node otherNode = (Node) other;
                final String otherRegion = NetworkServerProperties.parseRegionName(otherNode.getExtraData());

                if (Objects.equals(otherRegion, region)) {
                    linksToRegion.add(link);
                    if (MapUtils.isNcp(otherNode)) {
                        linksToNonClients.add(link);
                    }
                }
            } else if (other instanceof Switch) {
                final Switch sw = (Switch) other;

                // if all nodes on the switch are in the same region, then
                // this is link is to the node's region.
                boolean regionLink = true;
                boolean linkHasClients = false;
                for (final Node otherNode : sw.getNodes()) {
                    if (!node.equals(otherNode)) {
                        final String otherRegion = NetworkServerProperties.parseRegionName(otherNode.getExtraData());
                        if (!Objects.equals(region, otherRegion)) {
                            regionLink = false;
                        } else {
                            linksToRegion.add(link);
                            if (MapUtils.isNcp(otherNode)) {
                                linksToNonClients.add(link);
                            }
                        }
                        if (otherNode.isClient()) {
                            linkHasClients = true;
                        }
                    } // filter self
                }
                if (regionLink) {
                    linksToRegion.add(link);
                    if (!linkHasClients) {
                        linksToNonClients.add(link);
                    }
                }
            } else {
                throw new RuntimeException("Unexpected NetworkDevice class " + other.getClass());
            }
        } // foreach link

        if (linksToRegion.size() > 1) {
            if (linksToNonClients.size() >= 1) {
                // pick the first non-client link
                return linksToNonClients.stream().findAny().orElse(null);
            } else {
                // pick the first link
                return linksToRegion.stream().findAny().orElse(null);
            }

        } else {
            return linksToRegion.stream().findAny().orElse(null);
        }

    }

    /**
     * @return true if IP assignment was needed
     */
    private boolean assignIpAddresses() {
        final boolean needsAssignment = checkIfAutomaticIpAssignmentNeeded();
        if (!needsAssignment) {
            return false;
        }

        LOGGER.debug("Computing IP addresses");

        final Map<Link, SubnetBlock> linkToSubnet = new HashMap<>();
        final Map<Switch, SubnetBlock> switchToSubnet = new HashMap<>();
        for (final Map.Entry<String, Node> nEntry : topology.getNodes().entrySet()) {
            final Node node = nEntry.getValue();

            final String region = NetworkServerProperties.parseRegionName(node.getExtraData());
            try (CloseableThreadContext.Instance nodeLoggerContext = CloseableThreadContext.push(node.getName())) {

                LOGGER.trace("Region is {}", region);

                final Link primaryLink = findPrimaryLink(node);
                LOGGER.trace("Primary link is {}", primaryLink);

                for (final Link link : node.getLinks()) {
                    try (CloseableThreadContext.Instance linkLoggerContext = CloseableThreadContext
                            .push(node.getName())) {

                        final NetworkDevice other = getOtherEndOfLink(node, link);

                        final InetAddress addr;
                        if (other instanceof Node) {
                            final Node otherNode = (Node) other;
                            final String otherRegion = NetworkServerProperties
                                    .parseRegionName(otherNode.getExtraData());

                            final SubnetBlock subnet = linkToSubnet.computeIfAbsent(link,
                                    k -> SubnetBlock.getNextSubnetBlock());

                            LOGGER.trace("Other node {} is in region {} subnet {}", otherNode, otherRegion, subnet);

                            addr = computeIpAddress(node, link, !Objects.equals(region, otherRegion), primaryLink,
                                    subnet);
                        } else if (other instanceof Switch) {
                            final Switch sw = (Switch) other;

                            final SubnetBlock subnet = switchToSubnet.computeIfAbsent(sw,
                                    k -> SubnetBlock.getNextSubnetBlock());

                            // if all nodes on the switch are in the same
                            // region, then this is not an inter-region link
                            final boolean interRegion = sw.getNodes().stream()
                                    .map(n -> NetworkServerProperties.parseRegionName(n.getExtraData())).distinct()
                                    .limit(2).count() > 1;

                            LOGGER.trace("Other is a switch {} subnet {} interRegion {}", sw, subnet, interRegion);

                            addr = computeIpAddress(node, link, interRegion, primaryLink, subnet);

                        } else {
                            throw new RuntimeException("Unexpected NetworkDevice class " + other.getClass());
                        }

                        // set the address
                        node.setIpAddress(link, addr);
                        LOGGER.trace("Set address on link {} to {}", link, addr);
                    } // link logging context
                } // foreach link
            } // node logging context
        } // foreach node

        return true;
    }

    private void writeRunScripts() throws IOException {
        writeEmulabRunScenarioScript();
        writeEmulabRunScenarioBatchScript();
        writeExecuteScenarioScript();
        writeExecuteScenarioBackgroundScript();
        writeWaitForDynamicRoutingScript();

        writeDcompRunScenarioScript();

        writeFetchImagesScript();

        Stream.of(new String[] { //
                "wait_for_clients.py", //
                "collect-outputs.sh" }).forEach(filename -> {
                    copyResource("ansible/" + filename, filename, true);
                });

    }

    private void writeDcompRunScenarioScript() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ansible/run-scenario_dcomp.sh.template")) {
            Objects.requireNonNull(stream, "Cannot find ansible/run-scenario_dcomp.sh.template");

            final Path destination = configOptions.outputFolder.resolve("run-scenario_dcomp.sh");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()));
                    BufferedWriter writer = Files.newBufferedWriter(destination)) {
                try (LineIterator it = new LineIterator(reader)) {
                    while (it.hasNext()) {
                        final String line = it.next();
                        final String newLine = line.replaceAll("SCENARIO_NAME", getScenarioName()) //
                                .replaceAll("EXPERIMENT", configOptions.experiment) //
                                .replaceAll("DCOMP_PROJECT", configOptions.dcompProject) //
                        ;
                        writer.write(newLine);
                        writer.newLine();
                    }
                } // LineIterator allocation
            } // reader/writer allocation

            final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(destination);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(destination, permissions);
        } // stream allocation
    }

    private String getScenarioName() {
        final String name;
        if (null != configOptions.scenarioName) {
            name = configOptions.scenarioName;
        } else {
            name = String.valueOf(configOptions.scenarioPath.getFileName());
        }
        return name;
    }

    private void writeExecuteScenarioScript() throws IOException {
        final StringBuilder waitClientsArgsBuilderEmulab = new StringBuilder();
        final StringBuilder waitClientsArgsBuilderDcomp = new StringBuilder();
        final StringBuilder ncpsBuilderEmulab = new StringBuilder();
        final StringBuilder ncpsBuilderDcomp = new StringBuilder();

        ncpsBuilderDcomp.append('"');
        ncpsBuilderEmulab.append('"');
        waitClientsArgsBuilderDcomp.append('"');
        waitClientsArgsBuilderEmulab.append('"');
        for (final Map.Entry<?, Node> sEntry : topology.getNodes().entrySet()) {
            final Node node = sEntry.getValue();
            if (node.isClient()) {
                waitClientsArgsBuilderEmulab.append(String.format(" -c %s", getEmulabHostnameFor(node)));
                waitClientsArgsBuilderDcomp.append(String.format(" -c %s", getDcompHostnameFor(node)));
            } else if (MapUtils.isNcp(node)) {
                ncpsBuilderEmulab.append(String.format(" %s", getEmulabHostnameFor(node)));
                ncpsBuilderDcomp.append(String.format(" %s", getDcompHostnameFor(node)));
            }
        }
        ncpsBuilderDcomp.append('"');
        ncpsBuilderEmulab.append('"');
        waitClientsArgsBuilderDcomp.append('"');
        waitClientsArgsBuilderEmulab.append('"');

        final String waitClientEmulab = waitClientsArgsBuilderEmulab.toString();
        final String waitClientDcomp = waitClientsArgsBuilderDcomp.toString();
        final String ncpsEmulab = ncpsBuilderEmulab.toString();
        final String ncpsDcomp = ncpsBuilderDcomp.toString();

        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ansible/execute-scenario.sh.template")) {
            Objects.requireNonNull(stream, "Cannot find ansible/execute-scenario.sh.template");

            final Path destination = configOptions.outputFolder.resolve("execute-scenario.sh");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()));
                    BufferedWriter writer = Files.newBufferedWriter(destination)) {
                try (LineIterator it = new LineIterator(reader)) {
                    while (it.hasNext()) {
                        final String line = it.next();
                        final String newLine = line.replaceAll("WAIT_FOR_CLIENTS_ARGS_EMULAB", waitClientEmulab) //
                                .replaceAll("WAIT_FOR_CLIENTS_ARGS_DCOMP", waitClientDcomp) //
                                .replaceAll("NCPS_EMULAB", ncpsEmulab)//
                                .replaceAll("NCPS_DCOMP", ncpsDcomp)//
                                .replaceAll("GROUP", configOptions.emulabGroup) //
                                .replaceAll("EXPERIMENT", configOptions.experiment) //
                                .replaceAll("SCENARIO_NAME", getScenarioName()) //
                                .replaceAll("DCOMP_PROJECT", configOptions.dcompProject) //
                        ;
                        writer.write(newLine);
                        writer.newLine();
                    }
                } // LineIterator allocation
            } // reader/writer allocation

            final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(destination);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(destination, permissions);
        } // stream allocation
    }

    private void writeFetchImagesScript() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ansible/fetch_service_images.py.template")) {
            Objects.requireNonNull(stream, "Cannot find ansible/fetch_service_images.py.template");

            final Path destination = configOptions.outputFolder.resolve("fetch_service_images.py");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()));
                    BufferedWriter writer = Files.newBufferedWriter(destination)) {
                try (LineIterator it = new LineIterator(reader)) {
                    while (it.hasNext()) {
                        final String line = it.next();
                        final String newLine = line //
                                .replaceAll("DOCKER_REGISTRY_HOST", configOptions.dockerRegistryHostname) //
                                .replaceAll("DOCKER_REGISTRY_PORT",
                                        String.valueOf(SimpleDockerResourceManager.DOCKER_REGISTRY_PORT)) //
                        ;
                        writer.write(newLine);
                        writer.newLine();
                    }
                } // LineIterator allocation
            } // reader/writer allocation

            final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(destination);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(destination, permissions);
        } // stream allocation
    }

    private void writeExecuteScenarioBackgroundScript() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ansible/execute-scenario-background.sh.template")) {
            Objects.requireNonNull(stream, "Cannot find ansible/execute-scenario-background.sh.template");

            final Path destination = configOptions.outputFolder.resolve("execute-scenario-background.sh");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()));
                    BufferedWriter writer = Files.newBufferedWriter(destination)) {
                try (LineIterator it = new LineIterator(reader)) {
                    while (it.hasNext()) {
                        final String line = it.next();
                        final String newLine = line.replaceAll("SCENARIO_NAME", getScenarioName()) //
                                .replaceAll("EXPERIMENT", configOptions.experiment) //
                        ;
                        writer.write(newLine);
                        writer.newLine();
                    }
                } // LineIterator allocation
            } // reader/writer allocation

            final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(destination);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(destination, permissions);
        } // stream allocation
    }

    private void writeWaitForDynamicRoutingScript() throws IOException {
        final Set<String> allIps = new HashSet<>();
        for (final Map.Entry<?, Node> sEntry : topology.getNodes().entrySet()) {
            final Node node = sEntry.getValue();
            final Set<String> nodeIps = node.getAllIpAddresses().entrySet().stream().map(Map.Entry::getValue)
                    .map(addr -> addr.getHostAddress()).collect(Collectors.toSet());
            allIps.addAll(nodeIps);
        }

        final String allIpsStr = allIps.stream().map(ip -> ("'" + ip + "'")).collect(Collectors.joining(" "));

        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ansible/wait_for_dynamic_routing.sh.template")) {
            Objects.requireNonNull(stream, "Cannot find ansible/wait_for_dynamic_routing.sh.template");

            final Path destination = configOptions.outputFolder.resolve("wait_for_dynamic_routing.sh");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()));
                    BufferedWriter writer = Files.newBufferedWriter(destination)) {
                try (LineIterator it = new LineIterator(reader)) {
                    while (it.hasNext()) {
                        final String line = it.next();
                        final String newLine = line.replaceAll("ALL_IP_ADDRESSES", allIpsStr);
                        writer.write(newLine);
                        writer.newLine();
                    }
                } // LineIterator allocation
            } // reader/writer allocation

            final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(destination);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(destination, permissions);
        } // stream allocation
    }

    private void writeEmulabRunScenarioScript() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ansible/run-scenario_emulab.sh.template")) {
            Objects.requireNonNull(stream, "Cannot find ansible/run-scenario_emulab.sh.template");
            final Path destination = configOptions.outputFolder.resolve("run-scenario_emulab.sh");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()));
                    BufferedWriter writer = Files.newBufferedWriter(destination)) {
                try (LineIterator it = new LineIterator(reader)) {
                    while (it.hasNext()) {
                        final String line = it.next();
                        final String newLine = line
                                .replaceAll("BASTION", getEmulabHostnameFor(configOptions.bastionHostname))
                                .replaceAll("SCENARIO_NAME", getScenarioName());
                        writer.write(newLine);
                        writer.newLine();
                    }
                } // LineIterator allocation
            } // reader/writer allocation

            final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(destination);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(destination, permissions);
        } // stream allocation
    }

    private void writeEmulabRunScenarioBatchScript() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ansible/run-scenario-batch_emulab.sh.template")) {
            Objects.requireNonNull(stream, "Cannot find ansible/run-scenario-batch_emulab.sh.template");
            final Path destination = configOptions.outputFolder.resolve("run-scenario-batch_emulab.sh");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()));
                    BufferedWriter writer = Files.newBufferedWriter(destination)) {
                try (LineIterator it = new LineIterator(reader)) {
                    while (it.hasNext()) {
                        final String line = it.next();
                        final String newLine = line
                                .replaceAll("BASTION", getEmulabHostnameFor(configOptions.bastionHostname))
                                .replaceAll("SCENARIO_NAME", getScenarioName());
                        writer.write(newLine);
                        writer.newLine();
                    }
                } // LineIterator allocation
            } // reader/writer allocation

            final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(destination);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(destination, permissions);
        } // stream allocation
    }

    private void writeClientInformation() throws IOException {
        final Path clientDir = configOptions.outputFolder.resolve(String.format("client/"));
        if (!Files.exists(clientDir)) {
            Files.createDirectories(clientDir);
        }

        copyClientJarFile();

        Stream.of(new String[] { "map-client.logging.xml", "map-client.service", "map-client_pre_start.logging.xml" })
                .forEach(filename -> {
                    copyResource("ansible/client/" + filename, "client/" + filename);
                });

        // client start scripts
        Stream.of(new String[] { "start_database_publish.sh", "load_database_publish.sh", //
                "start_database_query.sh", "load_database_query.sh", //
                "start_frs_client.sh", "load_frs_client.sh", //
        }).forEach(filename -> {
            copyResource("ansible/client/" + filename, "client/" + filename, true);
        });

        copyClientServiceConfiguration();
        copyClientDemand();

        Stream.of(new String[] { "52-start_clients.yml", "stop_clients.yml", "update_client_jar.yml" })
                .forEach(filename -> {
                    copyResource("ansible/" + filename, filename);
                });

    }

    private void writeSimDriverInformation() throws IOException {
        final Path simDriverDir = configOptions.outputFolder.resolve(String.format("sim-driver/"));
        if (!Files.exists(simDriverDir)) {
            Files.createDirectories(simDriverDir);
        }

        copySimDriverJarFile(simDriverDir);

        Stream.of(new String[] { "14-setup-sim-driver.yml", "55-start_sim-driver.yml", "stop_sim-driver.yml" })
                .forEach(filename -> {
                    copyResource("ansible/" + filename, filename);
                });

        Stream.of(new String[] { "sim-driver.service", "map-sim.logging.xml" }).forEach(filename -> {
            copyResource("ansible/" + filename, "sim-driver/" + filename);
        });

        // create a complete copy of the scenario directory for the simulation
        // driver to use
        final Path scenarioDest = simDriverDir.resolve("scenario");
        if (!Files.exists(scenarioDest)) {
            Files.createDirectories(scenarioDest);
        }
        FileUtils.copyDirectory(configOptions.scenarioPath.toFile(), scenarioDest.toFile());

    }

    private void copySysinfo() throws IOException {
        final Path dest = configOptions.outputFolder.resolve("sysinfo");
        if (!Files.exists(dest)) {
            Files.createDirectories(dest);
        }
        // copy stuff from jar file...

        Stream.of(new String[] { "install.yml", "map_system_stats.service", "start.yml", "stop.yml" })
                .forEach(filename -> {
                    copyResource("ansible/sysinfo/" + filename, "sysinfo/" + filename, false);
                });

        Stream.of(new String[] { "map_system_stats.py" }).forEach(filename -> {
            copyResource("ansible/sysinfo/" + filename, "sysinfo/" + filename, true);
        });
    }

    private void copySimDriverJarFile(final Path simDriverDir) {
        try {
            if (Files.exists(configOptions.simDriverJarFile)) {
                final Path destination = simDriverDir.resolve(SIM_DRIVER_JAR_FILENAME);
                Files.copy(configOptions.simDriverJarFile, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new RuntimeException(configOptions.simDriverJarFile + " does not exist");
            }
        } catch (final IOException e) {
            throw new RuntimeException("Error copying simulation driver jar file", e);
        }
    }

    private void copyClientDemand() throws IOException {
        if (Files.exists(configOptions.demandDir)) {

            final Path demandDir = configOptions.outputFolder.resolve(String.format("client/")).resolve("demand/");
            if (!Files.exists(demandDir)) {
                Files.createDirectories(demandDir);
            }

            FileUtils.copyDirectory(configOptions.demandDir.toFile(), demandDir.toFile());

        } else {
            throw new RuntimeException(configOptions.demandDir + " does not exist");
        }
    }

    private void copyClientServiceConfiguration() {
        try {
            if (Files.exists(configOptions.clientServiceConfigFile)) {
                final Path destination = configOptions.outputFolder.resolve("client")
                        .resolve(ClientDriver.CLIENT_SERVICE_CONFIGURATION_FILENAME);
                Files.copy(configOptions.clientServiceConfigFile, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new RuntimeException(configOptions.clientServiceConfigFile + " does not exist");
            }
        } catch (final IOException e) {
            throw new RuntimeException("Error copying client service configurations file", e);
        }
    }

    /**
     * The file that ansible will push out to the driver node to run the
     * simulation driver. Must match sim-driver.service.
     */
    public static final String SIM_DRIVER_JAR_FILENAME = "sim-driver.jar";

    /**
     * The file that ansible will push out to the nodes to run the client. Must
     * match 10-setup.yml.
     */
    public static final String CLIENT_JAR_FILENAME = "map-client.jar";

    /**
     * The file that ansible will push out to the nodes to run the client. Must
     * match 10-setup.yml.
     */
    public static final String CLIENT_PRE_START_JAR_FILENAME = "map-client-pre-start.jar";

    private void copyClientJarFile() {
        try {
            if (Files.exists(configOptions.clientJarFile)) {
                final Path destination = configOptions.outputFolder.resolve("client").resolve(CLIENT_JAR_FILENAME);
                Files.copy(configOptions.clientJarFile, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new RuntimeException(configOptions.clientJarFile + " does not exist");
            }
        } catch (final IOException e) {
            throw new RuntimeException("Error copying client jar file", e);
        }

        try {
            if (Files.exists(configOptions.clientPreStartJarFile)) {
                final Path destination = configOptions.outputFolder.resolve("client")
                        .resolve(CLIENT_PRE_START_JAR_FILENAME);
                Files.copy(configOptions.clientPreStartJarFile, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new RuntimeException(configOptions.clientPreStartJarFile + " does not exist");
            }
        } catch (final IOException e) {
            throw new RuntimeException("Error copying client pre-start jar file", e);
        }
    }

    private void writeRegionSubnetInformation() throws IOException {
        final Path destination = configOptions.outputFolder.resolve("region_subnet.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(destination)) {
            for (final Map.Entry<Subnet, String> entry : subnetToRegion.entrySet()) {
                // assumes class C
                writer.write(String.format("%s %s/%d%n", entry.getValue(), entry.getKey().getPrefix().getHostAddress(),
                        NETMASK_LENGTH));
            }
        }
    }

    private void writeDockerNetworkingScript() throws IOException {
        final Path destination = configOptions.outputFolder.resolve("15-docker-networking.yml");
        try (BufferedWriter writer = Files.newBufferedWriter(destination)) {
            writer.write(String.format("---%n"));

            writer.write(String.format("- hosts: ncps%n"));
            writer.write(String.format("  become: yes%n"));
            writer.write(String.format("  tasks:%n"));
            writer.write(String.format("    - name: \"Copy setup docker networking script\"%n"));
            writer.write(String.format("      copy:%n"));
            writer.write(String.format("        src: setup-docker-networking.py%n"));
            writer.write(String.format("        dest: /etc/map%n"));
            writer.write(String.format("        owner: root%n"));
            writer.write(String.format("        mode: 0755%n"));
            writer.newLine();

            for (final Map.Entry<?, Node> nEntry : topology.getNodes().entrySet()) {
                final Node node = nEntry.getValue();
                if (MapUtils.isNcp(node)) {
                    final InetAddress primaryIp = nodeToPrimaryIp.get(node);
                    if (null != primaryIp) {
                        writer.write(String.format("- hosts: %s%n", getAnsibleMapAlias(node)));
                        writer.write(String.format("  become: yes%n"));
                        writer.write(String.format("  tasks:%n"));
                        writer.write(String.format("    - shell: /etc/map/setup-docker-networking.py --ip %s%n",
                                primaryIp.getHostAddress()));
                        writer.newLine();
                    } // have primary IP
                } // not client
            } // foreach node
        } // writer
    }

    private void writeDnsInformation() throws IOException {
        writeResolveConfHead();

        final Path dnsDir = configOptions.outputFolder.resolve(String.format("dns/"));
        if (!Files.exists(dnsDir)) {
            Files.createDirectories(dnsDir);
        }

        copyResource("ansible/dns/map-dns.logging.xml", "dns/map-dns.logging.xml");
        copyResource("ansible/dns/map-dns.service", "dns/map-dns.service");

        final Path dnsConf = dnsDir.resolve("conf");
        if (!Files.exists(dnsConf)) {
            Files.createDirectories(dnsConf);
        }

        writeAnsibleDnsPlaybook();

        copyDnsJarFile();

        for (final Map.Entry<String, ?> entry : regionToDns.entrySet()) {
            writeDnsForRegion(entry.getKey());
        }

    }

    private void writeAnsibleDnsPlaybook() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ansible/20-dns.yml")) {
            Objects.requireNonNull(stream, "Cannot find ansible/20-dns.yml");

            final Path destination = configOptions.outputFolder.resolve("20-dns.yml");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()));
                    BufferedWriter writer = Files.newBufferedWriter(destination)) {
                try (LineIterator it = new LineIterator(reader)) {
                    // copy base file
                    while (it.hasNext()) {
                        final String line = it.next();
                        writer.write(line);
                        writer.newLine();
                    }
                } // LineIterator allocation

                // add region information
                writer.newLine();
                for (final Map.Entry<String, Set<Node>> dnsEntry : regionToDns.entrySet()) {
                    final String regionName = dnsEntry.getKey();

                    writer.write(String.format("- hosts: region_%s%n", regionName));
                    writer.write(String.format("  become: yes%n"));
                    writer.write(String.format("  tasks:%n"));

                    // make sure [Resolve] is in file
                    writer.write(String.format("    - name: resolved.conf format (%s)%n", regionName));
                    writer.write(String.format("      lineinfile:%n"));
                    writer.write(String.format("        path: /etc/systemd/resolved.conf%n"));
                    writer.write(String.format("        regexp: '^[Resolve]'%n"));
                    writer.write(String.format("        line: '[Resolve]'%n"));
                    writer.write(String.format("      %n"));

                    // specify DNS server
                    writer.write(String.format("    - name: Use regional DNS (%s)%n", regionName));
                    writer.write(String.format("      lineinfile:%n"));
                    writer.write(String.format("        path: /etc/systemd/resolved.conf%n"));
                    writer.write(String.format("        insertafter: '^[Resolve]'%n"));
                    writer.write(String.format("        regexp: '^DNS='%n"));

                    // entry needs to be an IP address
                    final String servers = dnsEntry.getValue().stream()
                            .map(node -> nodeToPrimaryIp.get(node).getHostAddress()).collect(Collectors.joining(", "));
                    writer.write(String.format("        line: 'DNS=%s'%n", servers));

                    // specify search domains
                    writer.write(String.format("    - name: Search map.dcomp for hosts%n"));
                    writer.write(String.format("      lineinfile:%n"));
                    writer.write(String.format("        path: /etc/systemd/resolved.conf%n"));
                    writer.write(String.format("        insertafter: '^[Resolve]'%n"));
                    writer.write(String.format("        regexp: '^Domains='%n"));
                    writer.write(String.format("        line: 'Domains=map.dcomp'%n", servers));
                    writer.write(String.format("%n"));

                    writer.write(String.format("    - name: Ensure /etc/resolv.conf is a symlink%n"));
                    writer.write(String.format("      file:%n"));
                    writer.write(String.format("        src: /run/systemd/resolve/resolv.conf%n"));
                    writer.write(String.format("        dest: /etc/resolv.conf%n"));
                    writer.write(String.format("        state: link%n"));
                    writer.write(String.format("        force: yes%n"));
                    writer.write(String.format("%n"));

                    writer.write(String.format("    - name: reload systemd-resolved%n"));
                    writer.write(String.format("      systemd:%n"));
                    writer.write(String.format("        name: systemd-resolved%n"));
                    writer.write(String.format("        state: restarted%n"));
                    writer.newLine();
                }

                // add DNS for underlay, just pick a regional DNS
                {
                    final Optional<Map.Entry<String, Set<Node>>> optionalDnsEntry = regionToDns.entrySet().stream()
                            .findFirst();
                    if (!optionalDnsEntry.isPresent()) {
                        throw new RuntimeException("No regions");
                    }
                    final Map.Entry<String, Set<Node>> dnsEntry = optionalDnsEntry.get();

                    writer.write(String.format("- hosts: underlay%n"));
                    writer.write(String.format("  become: yes%n"));
                    writer.write(String.format("  tasks:%n"));

                    // make sure [Resolve] is in file
                    writer.write(String.format("    - name: resolved.conf format (underlay)%n"));
                    writer.write(String.format("      lineinfile:%n"));
                    writer.write(String.format("        path: /etc/systemd/resolved.conf%n"));
                    writer.write(String.format("        regexp: '^[Resolve]'%n"));
                    writer.write(String.format("        line: '[Resolve]'%n"));
                    writer.write(String.format("      %n"));

                    // specify DNS server
                    writer.write(String.format("    - name: Use regional DNS (underlay)%n"));
                    writer.write(String.format("      lineinfile:%n"));
                    writer.write(String.format("        path: /etc/systemd/resolved.conf%n"));
                    writer.write(String.format("        insertafter: '^[Resolve]'%n"));
                    writer.write(String.format("        regexp: '^DNS='%n"));

                    // entry needs to be an IP address
                    final String servers = dnsEntry.getValue().stream()
                            .map(node -> nodeToPrimaryIp.get(node).getHostAddress()).collect(Collectors.joining(", "));
                    writer.write(String.format("        line: 'DNS=%s'%n", servers));

                    // specify search domains
                    writer.write(String.format("    - name: Search map.dcomp for hosts%n"));
                    writer.write(String.format("      lineinfile:%n"));
                    writer.write(String.format("        path: /etc/systemd/resolved.conf%n"));
                    writer.write(String.format("        insertafter: '^[Resolve]'%n"));
                    writer.write(String.format("        regexp: '^Domains='%n"));
                    writer.write(String.format("        line: 'Domains=map.dcomp'%n", servers));
                    writer.write(String.format("%n"));

                    writer.write(String.format("    - name: Ensure /etc/resolv.conf is a symlink%n"));
                    writer.write(String.format("      file:%n"));
                    writer.write(String.format("        src: /run/systemd/resolve/resolv.conf%n"));
                    writer.write(String.format("        dest: /etc/resolv.conf%n"));
                    writer.write(String.format("        state: link%n"));
                    writer.write(String.format("        force: yes%n"));
                    writer.write(String.format("%n"));

                    writer.write(String.format("    - name: reload systemd-resolved%n"));
                    writer.write(String.format("      systemd:%n"));
                    writer.write(String.format("        name: systemd-resolved%n"));
                    writer.write(String.format("        state: restarted%n"));
                    writer.newLine();
                } // underlay

            } // reader/writer allocation

            final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(destination);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(destination, permissions);
        } // stream allocation
    }

    private static Map<Node, InetAddress> determinePrimaryIpForLinks(final Topology topology,
            final Map<Node, String> nodeToRegion,
            final Map<Node, Map<InetAddress, String>> nodeToContainerAddresses) {
        final Map<Node, InetAddress> lNodeToPrimaryIp = new HashMap<>();
        topology.getNodes().forEach((name, node) -> {
            final InetAddress primaryIp = getPrimaryIpForNode(node, nodeToRegion, nodeToContainerAddresses);
            if (null != primaryIp) {
                lNodeToPrimaryIp.put(node, primaryIp);
            } else {
                throw new NullPointerException("Cannot determine primary IP for " + node);
            }
        });
        return lNodeToPrimaryIp;
    }

    private static Map<Node, Set<InetAddress>> determineAllAddressesForNodes(final Topology topology) {
        final Map<Node, Set<InetAddress>> lNodeToAddresses = new HashMap<>();
        topology.getNodes().forEach((name, node) -> {
            final Set<InetAddress> addresses = node.getLinks().stream().map(link -> node.getIpAddress(link))
                    .collect(Collectors.toSet());
            lNodeToAddresses.put(node, addresses);
        });
        return lNodeToAddresses;
    }

    /**
     * Get the primary IP address for a node.
     * 
     * @param node
     *            the node to find the primary IP for
     * @return the primary IP address, null if it cannot be determined
     */
    private static InetAddress getPrimaryIpForNode(final Node node,
            final Map<Node, String> nodeToRegion,
            final Map<Node, Map<InetAddress, String>> nodeToContainerAddresses) {
        final Set<Link> links = node.getLinks();
        if (links.isEmpty()) {
            // no links, dead end
            LOGGER.warn("Node {} doesn't have any links, cannot determine it's primary IP", node.getName());
            return null;
        } else if (links.size() == 1) {
            // single link, this is the primary IP
            final Link link = links.iterator().next();
            final InetAddress primaryIp = node.getIpAddress(link);
            return primaryIp;
        } else if (MapUtils.isUnderlay(node)) {
            // pick the first link and be done with it
            final Link link = links.iterator().next();
            final InetAddress primaryIp = node.getIpAddress(link);
            return primaryIp;
        } else {
            final String nodeRegion = nodeToRegion.get(node);
            if (null == nodeRegion) {
                LOGGER.warn("Node {} doesn't have a region, cannot determine it's primary IP address", node.getName());
                return null;
            }

            // find all neighbors in the same region
            final Map<Node, Link> neighborNodesInRegion = new HashMap<>();

            for (final Link link : links) {
                final NetworkDevice neighborDevice = link.getLeft().equals(node) ? link.getRight() : link.getLeft();
                if (neighborDevice instanceof Node) {
                    final Node neighborNode = (Node) neighborDevice;
                    final String neighborRegion = nodeToRegion.get(neighborNode);
                    if (nodeRegion.equals(neighborRegion)) {
                        neighborNodesInRegion.put(neighborNode, link);
                    }
                } else if (neighborDevice instanceof Switch) {
                    final Switch sw = (Switch) neighborDevice;

                    // if 1 node on the switch is in the same region, assume
                    // this is the primary interface
                    final Node neighborNode = sw.getNodes().stream()
                            .filter(n -> !n.equals(node) && nodeRegion.equals(nodeToRegion.get(n))).findAny()
                            .orElse(null);
                    if (null != neighborNode) {
                        neighborNodesInRegion.put(neighborNode, link);
                    } else {
                        // switch only has nodes in other regions on it
                        LOGGER.debug("Node {} is connected to a switch with no other nodes in the same region.", node);
                    }
                }
            } // foreach link

            if (1 == neighborNodesInRegion.size()) {
                // single neighbor in the same region, the address of node on
                // the link is the primary IP
                final Link link = neighborNodesInRegion.entrySet().stream().map(Map.Entry::getValue).findFirst()
                        .orElse(null);
                final InetAddress primaryIp = node.getIpAddress(link);
                return primaryIp;
            } else {
                // First check if all of the neighbors are in the same subnet.
                final Set<InetAddress> neighborSubnets = neighborNodesInRegion.entrySet().stream()
                        .map(Map.Entry::getValue).map(l -> node.getIpAddress(l)).map(a -> getSubnetPrefix(a))
                        .collect(Collectors.toSet());
                if (1 == neighborSubnets.size()) {
                    // get the address to the first neighbor and just use it
                    final Link neighborLink = neighborNodesInRegion.entrySet().iterator().next().getValue();
                    final InetAddress primaryIp = node.getIpAddress(neighborLink);
                    return primaryIp;
                }

                // Next find the link that has an address in the same
                // subnet one of the containers is the one to choose.
                final Map<InetAddress, String> containerAddresses = nodeToContainerAddresses.get(node);
                if (null != containerAddresses && !containerAddresses.isEmpty()) {
                    final Set<InetAddress> containerSubnets = containerAddresses.entrySet().stream()
                            .map(Map.Entry::getKey).map(a -> getSubnetPrefix(a)).collect(Collectors.toSet());
                    for (final Map.Entry<Node, Link> neighborEntry : neighborNodesInRegion.entrySet()) {
                        final InetAddress nodeAddress = node.getIpAddress(neighborEntry.getValue());
                        final InetAddress nodeSubnet = getSubnetPrefix(nodeAddress);
                        if (containerSubnets.contains(nodeSubnet)) {
                            return nodeAddress;
                        }
                    }
                } else {
                    // if all region neighbors are clients and only 1 is a node,
                    // then use the link with the node
                    final Map<Node, Link> regionalNeighborNodes = neighborNodesInRegion.entrySet().stream()
                            .filter(e -> MapUtils.isNcp(e.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    if (1 == regionalNeighborNodes.size()) {
                        final Link link = regionalNeighborNodes.entrySet().stream().map(Map.Entry::getValue).findFirst()
                                .orElse(null);
                        Objects.requireNonNull(link, "Cannot find neighbor in the region");
                        final InetAddress primaryIp = node.getIpAddress(link);
                        return primaryIp;
                    } else {
                        LOGGER.warn(
                                "Node {} has multiple neighbors in the same region that are in different subnets ({}) and the node doesn't have any containers to determine the primary IP address",
                                node.getName(),
                                neighborSubnets.stream().map(s -> s.getHostAddress()).collect(Collectors.toList()));
                    }
                }
            }

            LOGGER.warn(
                    "Standard rules for finding primary IP for for node {} were not able to determine the primary IP address. Falling back to just using the first address. Neighbors in region: {}",
                    node.getName(), neighborNodesInRegion.keySet());
            return node.getIpAddress(links.iterator().next());
        }
    }

    /**
     * Get the container names file for a node. This is a relative path.
     */
    private static String containerNamesFileFor(final Node node) {
        final String filename = getBaseFilenameForNode(node) + ".container-names.txt";
        return filename;
    }

    private static String ipToSpeedFileFor(final Node node) {
        final String filename = getBaseFilenameForNode(node) + ".ip-to-speed.txt";
        return filename;
    }

    private static String containerName(final Node node, final int index) {
        return String.format("%s_c%d", node.getName(), index);
    }

    /**
     * Make sure that the container names files contain IP addresses and compute
     * all of the container names.
     * 
     * @return node -> container IP -> base container name
     */
    private Map<Node, Map<InetAddress, String>> computeContainerNames(final Topology topology,
            final Path containerNamesDirectory) {
        Objects.requireNonNull(containerNamesDirectory,
                "When specifying IP addresses, one must also specify the container names");

        for (final Map.Entry<?, Node> sentry : topology.getNodes().entrySet()) {
            final Node node = sentry.getValue();
            if (MapUtils.isNcp(node)) {
                final Map<InetAddress, String> containerAddresses = nodeToContainerAddresses.computeIfAbsent(node,
                        k -> new HashMap<>());

                final String filename = containerNamesFileFor(node);
                final Path source = containerNamesDirectory.resolve(filename);
                if (!Files.exists(source)) {
                    throw new RuntimeException("Cannot find container names file " + filename);
                }

                try (Stream<String> stream = Files.lines(source)) {
                    final AtomicInteger index = new AtomicInteger();
                    stream.filter(line -> !(null == line || line.trim().isEmpty())).forEach(line -> {
                        try {
                            final InetAddress containerAddress = Address.getByAddress(line);

                            final String containerName = containerName(node, index.getAndIncrement());

                            if (containerAddresses.containsKey(containerAddress)) {
                                throw new RuntimeException("Container IP '" + containerAddress + "' on node '"
                                        + node.getName() + "' is a duplicate");
                            }

                            containerAddresses.put(containerAddress, containerName);
                        } catch (final UnknownHostException e) {
                            throw new RuntimeException(
                                    "'" + line + "' in '" + filename + "' is not a valid IP address.");
                        }

                    });
                } catch (final IOException e) {
                    throw new RuntimeException("Error reading " + filename, e);
                }

            }
        }

        return nodeToContainerAddresses;
    }

    /**
     * Make sure that the container names files contain IP addresses and compute
     * all of the container names.
     */
    private static void assertContainerNamesValid(final Map<Node, Map<InetAddress, String>> nodeToContainerAddresses,
            final Map<Node, Set<InetAddress>> nodeToAllAddresses) {
        for (final Map.Entry<Node, Map<InetAddress, String>> topEntry : nodeToContainerAddresses.entrySet()) {
            final Node node = topEntry.getKey();

            final Set<InetAddress> nodeAddresses = nodeToAllAddresses.get(node);
            Objects.requireNonNull(nodeAddresses, "Cannot find addresses for " + node);
            final Set<InetAddress> nodeSubnets = nodeAddresses.stream().map(a -> getSubnetPrefix(a))
                    .collect(Collectors.toSet());

            for (final Map.Entry<InetAddress, String> nodeContainerEntry : topEntry.getValue().entrySet()) {
                final InetAddress containerAddress = nodeContainerEntry.getKey();

                final InetAddress containerSubnet = getSubnetPrefix(containerAddress);

                final boolean found = nodeSubnets.stream().anyMatch(s -> containerSubnet.equals(s));
                if (!found) {
                    throw new RuntimeException("Node '" + node.getName() + "' is in subnet(s) '"
                            + nodeSubnets.stream().map(s -> s.getHostAddress()).collect(Collectors.toList())
                            + "', but one of it's containers is in subnet '" + containerSubnet.getHostAddress() + "'");
                }
            }
        }
    }

    private void copyContainerNames() {
        for (final Map.Entry<?, Node> sentry : topology.getNodes().entrySet()) {
            final Node node = sentry.getValue();
            if (MapUtils.isNcp(node)) {
                final String filename = containerNamesFileFor(node);
                final Path destination = configOptions.outputFolder.resolve(filename);

                if (autoAssignIpAddresses) {
                    try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardOpenOption.CREATE)) {
                        final Map<InetAddress, String> containerNames = nodeToContainerAddresses.get(node);
                        Objects.requireNonNull(containerNames, "Cannot find any container names for: " + node);

                        for (final Map.Entry<InetAddress, String> entry : containerNames.entrySet()) {
                            writer.write(entry.getKey().getHostAddress());
                            writer.newLine();
                        }
                    } catch (final IOException e) {
                        throw new RuntimeException("Error writing " + filename, e);
                    }
                } else {
                    Objects.requireNonNull(configOptions.containerNamesDirectory,
                            "If not using auto-ip assignment, then the container names must be specified");
                    try {
                        final Path source = configOptions.containerNamesDirectory.resolve(filename);
                        if (Files.exists(source)) {
                            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            throw new RuntimeException(
                                    "No container-names.txt file found for node: " + sentry.getValue().getName());
                        }
                    } catch (final IOException e) {
                        throw new RuntimeException("Error copying " + filename, e);
                    }
                }
            }
        }
    }

    private void writeIpToSpeed() {
        for (final Map.Entry<?, Node> sentry : topology.getNodes().entrySet()) {
            final Node node = sentry.getValue();
            if (MapUtils.isNcp(node)) {
                final String filename = ipToSpeedFileFor(node);
                final Path destination = configOptions.outputFolder.resolve(filename);
                try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardOpenOption.CREATE)) {
                    for (final Map.Entry<Link, InetAddress> addrEntry : node.getAllIpAddresses().entrySet()) {
                        final Link link = addrEntry.getKey();
                        final InetAddress addr = addrEntry.getValue();
                        writer.write(String.format("%s %f%n", addr.getHostAddress(), link.getBandwidth()));
                    }
                } catch (final IOException e) {
                    throw new RuntimeException("Error writing " + filename, e);
                }
            } // NCP
        } // foreach node
    }

    /**
     * The file that ansible will push out to the nodes to run the background
     * traffic generation. Must match 10-setup.yml.
     */
    public static final String BACKGROUND_TRAFFIC_DRIVER_JAR_FILENAME = "background-traffic-driver.jar";

    private void copyBackgroundTrafficDriverJarFile() {
        try {
            if (Files.exists(configOptions.backgroundTrafficDriverJarFile)) {
                final Path destination = configOptions.outputFolder.resolve(BACKGROUND_TRAFFIC_DRIVER_JAR_FILENAME);
                Files.copy(configOptions.backgroundTrafficDriverJarFile, destination,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new RuntimeException(configOptions.backgroundTrafficDriverJarFile + " does not exist");
            }
        } catch (final IOException e) {
            throw new RuntimeException("Error copying background traffic driver jar file", e);
        }
    }

    /**
     * The file that ansible will push out to the nodes to run the agent. Must
     * match 12-setup-ncps.yml.
     */
    public static final String AGENT_JAR_FILENAME = "map-agent.jar";

    private void copyAgentJarFile() {
        try {
            if (Files.exists(configOptions.agentJarFile)) {
                final Path destination = configOptions.outputFolder.resolve(AGENT_JAR_FILENAME);
                Files.copy(configOptions.agentJarFile, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new RuntimeException(configOptions.agentJarFile + " does not exist");
            }
        } catch (final IOException e) {
            throw new RuntimeException("Error copying agent jar file", e);
        }
    }

    /**
     * The file that ansible will push out to the nodes to run the DNS server.
     * Must match dns.yml.
     */
    public static final String DNS_JAR_FILENAME = "map-dns.jar";

    private void copyDnsJarFile() {
        try {
            if (Files.exists(configOptions.dnsJarFile)) {
                final Path destination = configOptions.outputFolder.resolve("dns").resolve(DNS_JAR_FILENAME);
                Files.copy(configOptions.dnsJarFile, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new RuntimeException(configOptions.dnsJarFile + " does not exist");
            }
        } catch (final IOException e) {
            throw new RuntimeException("Error copying DNS jar file", e);
        }
    }

    @Nonnull
    private Node findDockerRegistry() {
        Node dockerRegistryNode = null;
        for (final Map.Entry<?, Node> sEntry : topology.getNodes().entrySet()) {
            final Node node = sEntry.getValue();
            if (node.getName().equals(configOptions.dockerRegistryHostname)) {
                if (null != dockerRegistryNode) {
                    throw new RuntimeException(
                            "Found 2 nodes with the docker registry name: " + configOptions.dockerRegistryHostname);
                } else {
                    dockerRegistryNode = node;
                }
            }
        }

        if (null == dockerRegistryNode) {
            throw new RuntimeException(
                    "Could not find node with name '" + configOptions.dockerRegistryHostname + "' for docker registry");
        }

        return dockerRegistryNode;
    }

    private void copyLoggingConfig() {
        copyResource("ansible/map-hifi.logging.xml", "map-hifi.logging.xml");
        copyResource("ansible/map-background-traffic.logging.xml", "map-background-traffic.logging.xml");
    }

    private void createAnsibleHostsForEmulab(Node dockerRegistryNode) {
        createAnsibleHosts(dockerRegistryNode, "emulab", this::getEmulabHostnameFor);
    }

    private void createAnsibleHostsForDcomp(Node dockerRegistryNode) {
        createAnsibleHosts(dockerRegistryNode, "dcomp", this::getDcompHostnameFor);
    }

    private void createAnsibleHosts(final Node dockerRegistryNode,
            final String testbed,
            final Function<Node, String> hostnameFunction) {
        final Path destination = configOptions.outputFolder.resolve("hosts." + testbed);
        try (BufferedWriter writer = Files.newBufferedWriter(destination)) {
            writer.write("[ncps]");
            writer.newLine();
            for (final Map.Entry<?, Node> sEntry : topology.getNodes().entrySet()) {
                final Node node = sEntry.getValue();
                if (MapUtils.isNcp(node)) {
                    writer.write(hostnameFunction.apply(node));
                    writer.newLine();
                }
            }
            writer.newLine();

            writer.write("[registry]");
            writer.newLine();
            writer.write(hostnameFunction.apply(dockerRegistryNode));
            writer.newLine();
            writer.newLine();

            writer.write("[clients]");
            writer.newLine();
            for (final Map.Entry<?, Node> cEntry : topology.getNodes().entrySet()) {
                final Node node = cEntry.getValue();
                if (node.isClient()) {
                    writer.write(String.format("%s%n", hostnameFunction.apply(node)));
                }
            }
            writer.newLine();

            writer.write("[dns_servers]");
            writer.newLine();
            for (final Map.Entry<?, Set<Node>> rEntry : regionToDns.entrySet()) {
                for (final Node node : rEntry.getValue()) {
                    writer.write(String.format("%s%n", hostnameFunction.apply(node)));
                }
            }
            writer.newLine();

            for (final Map.Entry<String, Set<Node>> entry : regionNodes.entrySet()) {
                writer.write(String.format("[region_%s]%n", entry.getKey()));
                for (final Node node : entry.getValue()) {
                    writer.write(String.format("%s%n", hostnameFunction.apply(node)));
                }
                writer.newLine();
            }

            // create an alias for each host to make handling multiple testbeds
            // easier
            writer.newLine();
            for (final Map.Entry<?, Node> sEntry : topology.getNodes().entrySet()) {
                final Node node = sEntry.getValue();
                writer.write(String.format("[%s]%n", getAnsibleMapAlias(node)));
                writer.write(String.format("%s%n", hostnameFunction.apply(node)));
                writer.newLine();
            }

            writer.newLine();
            writer.write("[underlay]");
            writer.newLine();
            topology.getNodes().entrySet().stream().map(Map.Entry::getValue).filter(MapUtils::isUnderlay)
                    .forEach(Errors.rethrow().wrap(node -> {
                        writer.write(String.format("%s%n", hostnameFunction.apply(node)));
                    }));

            writer.newLine();

        } catch (final IOException e) {
            throw new RuntimeException("Error writing ansible hosts file", e);
        }
    }

    private Object getAnsibleMapAlias(Node node) {
        return String.format("host_%s_map", node.getName());
    }

    /**
     * Port that AP will use to communicate.
     */
    public static final int AP_PORT = 50042;

    /**
     * Also sets the global leader.
     */
    private Node writeGlobalProperties() {
        // always pick a global leader, it will be used if leader election is
        // turned off
        final Node globalLeader = this.topology.getNodes().entrySet().stream().map(Map.Entry::getValue)
                .filter(MapUtils::isNcp).findFirst().orElse(null);
        Objects.requireNonNull(globalLeader);
        final String globalLeaderHostname = globalLeader.getName();

        // default hostnames to the global leader
        if (null == configOptions.dockerRegistryHostname) {
            configOptions.dockerRegistryHostname = globalLeaderHostname;
        }
        if (null == configOptions.bastionHostname) {
            configOptions.bastionHostname = globalLeaderHostname;
        }

        final Path destination = configOptions.outputFolder.resolve(HiFiAgent.GLOBAL_PROPERTIES_FILENAME);
        final Properties globalProperties = new Properties();
        globalProperties.setProperty(HiFiAgent.AP_PORT_PROPERTY_KEY, String.valueOf(AP_PORT));

        globalProperties.setProperty(HiFiAgent.DOCKER_REGISTRY_HOST_KEY, configOptions.dockerRegistryHostname);

        globalProperties.setProperty(HiFiAgent.GLOBAL_LEADER_KEY, globalLeaderHostname);

        if (configOptions.dumpEnabled) {
            globalProperties.setProperty(HiFiAgent.DUMP_ENABLED_GLOBAL_KEY, String.valueOf(configOptions.dumpEnabled));
            globalProperties.setProperty(HiFiAgent.DUMP_DIRECTORY_GLOBAL_KEY, configOptions.dumpDirectory.toString());
            globalProperties.setProperty(HiFiAgent.DUMP_INTERVAL_GLOBAL_KEY, configOptions.dumpInterval.toString());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(destination)) {
            globalProperties.store(writer, null);
        } catch (final IOException e) {
            throw new RuntimeException("Error writing global properties", e);
        }

        return globalLeader;
    }

    private void copyTopology() {
        final Path source = configOptions.scenarioPath.resolve(NS2Parser.TOPOLOGY_FILENAME);
        final Path destination = configOptions.outputFolder.resolve(NS2Parser.TOPOLOGY_FILENAME);
        final Pattern routePattern = Pattern.compile("^\\$(\\S+)\\s+rtproto\\s+\\S+$");

        try (BufferedWriter writer = Files.newBufferedWriter(destination)) {

            Files.lines(source).forEach(line -> {
                try {
                    final Matcher routeMatcher = routePattern.matcher(line);

                    if (line.matches("^\\$.*\\s+run$")) {
                        if (autoAssignIpAddresses) {
                            writer.newLine();

                            for (final Map.Entry<String, Node> nEntry : topology.getNodes().entrySet()) {
                                final Node node = nEntry.getValue();

                                writer.write(String.format("tb-set-node-os $%s UBUNTU18-64-MAP%n", node.getName()));

                                final String hardwareConfigName = node.getHardware();
                                if (null != hardwareConfigName) {
                                    final HardwareConfiguration hwConfig = hardwareConfigs.get(hardwareConfigName);
                                    if (null == hwConfig) {
                                        throw new RuntimeException("Node '" + node.getName() + "' specifies hardware '"
                                                + hardwareConfigName
                                                + "', but it is not specified in the hardware configurations file");
                                    }
                                    final String emulabConfig = hwConfig.getEmulabConfig();
                                    if (null != emulabConfig) {
                                        writer.write(String.format("tb-set-hardware $%s %s%n", node.getName(),
                                                emulabConfig));
                                    }
                                }

                                for (final Link link : node.getLinks()) {
                                    final NetworkDevice other;
                                    if (link.getLeft().equals(node)) {
                                        other = link.getRight();
                                    } else {
                                        other = link.getLeft();
                                    }

                                    final String addr = node.getIpAddress(link).getHostAddress();
                                    if (other instanceof Node) {
                                        writer.write(String.format("tb-set-ip-link $%s $%s %s", node.getName(),
                                                link.getName(), addr));
                                    } else if (other instanceof Switch) {
                                        final Switch sw = (Switch) other;
                                        writer.write(String.format("tb-set-ip-lan $%s $%s %s", node.getName(),
                                                sw.getName(), addr));
                                    } else {
                                        throw new RuntimeException(
                                                "Unexpected NetworkDevice class " + other.getClass());
                                    }
                                    writer.newLine();
                                } // foreach link
                            } // foreach node

                            writer.newLine();
                        } // write addresses

                        if (configOptions.enableFirewall) {
                            writer.write("# ---- firewall config");
                            writer.newLine();

                            writer.write("# create a firewall node to protect the experiment");
                            writer.newLine();
                            writer.write("set fw [new Firewall $ns]");
                            writer.newLine();
                            // writer.write("$fw set-type ipfw2-vlan");
                            writer.write("$fw set-type iptables-vlan");
                            writer.newLine();
                            writer.write("$fw set-style basic");
                            writer.newLine();

                            writer.write("# allow traceroute through so that emulab loading works");
                            writer.newLine();
                            writer.write("$fw add-rule \"allow udp from EMULAB_CNET to any 33434-33524\"");
                            writer.newLine();
                            writer.write("$fw add-rule \"allow udp from any 33434-33524 to EMULAB_CNET\"");
                            writer.newLine();

                            writer.write("# --- end firewall config");
                            writer.newLine();
                        }
                    } else if (line.matches("^tb-set-hardware\\s+\\S+\\s+\\S+$")) {
                        // skip hardware lines, these are from the lo-fi
                        // simulation
                        return;
                    } else if (line.startsWith("tb-set-node-os")) {
                        // skip node os lines, will be written out later
                        return; // don't write out the original line
                    } else if (routeMatcher.find()) {
                        // always use Manual routing and therefore use quagga
                        final String nsName = routeMatcher.group(1);
                        writer.write(String.format("$%s rtproto Manual%n", nsName));
                        return; // don't write out the original line
                    }

                    writer.write(line);
                    writer.newLine();
                } catch (final IOException e) {
                    throw new RuntimeException("Error writing topology file", e);
                }
            });
        } catch (final IOException e) {
            throw new RuntimeException("Error reading topology file", e);
        }
    }

    private void copyServiceConfigurations() {
        try {
            final Path source;
            if (null == configOptions.serviceConfigFile) {
                source = configOptions.scenarioPath.resolve(Simulation.SERVICE_CONFIGURATIONS_FILENAME);
            } else {
                source = Paths.get(configOptions.serviceConfigFile);
                if (!Files.exists(source)) {
                    throw new RuntimeException("Specified service configurations file doesn't exist: " + source);
                }
            }
            final Path destination = configOptions.outputFolder.resolve(Simulation.SERVICE_CONFIGURATIONS_FILENAME);
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            throw new RuntimeException("Error copying service-configurations.json", e);
        }
    }

    private void copyNodeConfigFiles() {
        for (final Map.Entry<?, Node> sentry : topology.getNodes().entrySet()) {
            final String filename = getBaseFilenameForNode(sentry.getValue()) + ".json";
            try {
                final Path source = configOptions.scenarioPath.resolve(filename);
                if (Files.exists(source)) {
                    final Path destination = configOptions.outputFolder.resolve(filename);
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    throw new RuntimeException(
                            "Cannot find the configuration file for node " + sentry.getValue() + " -> " + filename);
                }
            } catch (final IOException e) {
                throw new RuntimeException("Error copying " + filename, e);
            }
        }
    }

    private void writeAnsibleConfiguration() {
        copyResource("ansible/ansible.cfg", "ansible.cfg");
    }

    private void writeAnsiblePlaybook() {
        Stream.of(new String[] { //
                "create-swap.sh", //
                "00-setup_dcomp.yml", //
                "01-ansible-packages.yml", //
                "02-failsafe-dns.yml", //
                "start_network_interfaces.py", //
                "04-install-services.yml", //
                "05-cleanup.yml", //
                "10-setup.yml", //
                "11-registry.yml", //
                "11-copy-registry-images_emulab.yml", //
                "11-copy-registry-images_dcomp.yml", //
                "12-setup-ncps.yml", //
                "get_container_veth.sh", //
                "13-setup-clients.yml", //
                "40-start_registry.yml", //
                "45-client-pre-start.yml", //
                "50-start_map_agent.yml", //
                "90-gather-node-data.yml", //
                "99-ip_forwarding.conf", //
                "docker-map.conf", //
                "stop_map_agent.yml", //
                "stop_map_agent.yml", //
                "wait_for_dynamic_routing.yml", //
                "flow-install.yml", //
                "flow-start.yml", //
                "flow-stop.yml", //
                "excluded-subnets.txt", //
                "fetch_images.yml", //
                "clear-docker.yml", //
                "start_background_traffic.yml", //
                "stop_background_traffic.yml", //
                "daemon.json", //
        }).forEach(filename -> {
            copyResource("ansible/" + filename, filename);
        });
    }

    private static String localPropertiesFileFor(final Node node) {
        final String filename = getBaseFilenameForNode(node) + ".local.properties";
        return filename;
    }

    private void writeLocalProperties() {
        for (final Map.Entry<?, Node> sentry : topology.getNodes().entrySet()) {
            final Node node = sentry.getValue();
            final String filename = localPropertiesFileFor(node);
            final Path destination = configOptions.outputFolder.resolve(filename);

            final Properties properties = new Properties();
            properties.setProperty(HiFiAgent.HOSTNAME_PROPERTY_KEY, node.getName());

            if (MapUtils.isNcp(node)) {
                // only need hardware information on NCPs
                properties.setProperty(HiFiAgent.HARDWARE_PROPERTY_KEY, node.getHardware());
            }

            try (BufferedWriter writer = Files.newBufferedWriter(destination)) {
                properties.store(writer, null);
            } catch (final IOException e) {
                throw new RuntimeException("Error writing local properties for " + node.getName(), e);
            }
        } // foreach node
    }

    private void copySystemdService() {
        copyResource("ansible/map-agent.service", "map-agent.service");
        copyResource("ansible/background-traffic-driver.service", "background-traffic-driver.service");
    }

    private void copyResource(final String resourcePath, final String destPath) {
        copyResource(resourcePath, destPath, false);
    }

    /**
     * 
     * @param resourcePath
     *            relative to the root of the classpath
     * @param destPath
     *            relative to configOptions.outputFolder
     * @param executable
     *            true if the file should be executable
     */
    private void copyResource(final String resourcePath, final String destPath, final boolean executable) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(stream, "Cannot find resource " + resourcePath);
            final Path destination = configOptions.outputFolder.resolve(destPath);
            Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);

            if (executable) {
                final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(destination);
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
                try {
                    Files.setPosixFilePermissions(destination, permissions);
                } catch (final UnsupportedOperationException use) {
                    LOGGER.debug("No on a POSIX host, skipping setting of filesystem permissions");
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException("Error writing " + destPath, e);
        }
    }

    /**
     * Writes out the files necessary for creating a registry certificate and
     * running the registry.
     */
    private void writeRegistryInformation() {
        if (configOptions.outputFolder.resolve("registry").toFile().mkdirs()) {
            writeOpenSSLConfigFile("ansible/registry/openssl_map.cnf.template", "registry/openssl_map.cnf");
            copyResource("ansible/registry/gen_cert_map.sh", "registry/gen_cert_map.sh", true);
            copyResource("ansible/registry/config2.yml", "registry/config2.yml");
            copyResource("ansible/registry/run_docker_registry_map.sh", "registry/run_docker_registry_map.sh", true);
        }
    }

    /**
     * Writes out the files necessary for running emulab scripts.
     */
    private void writeEmulabScriptInformation() {
        if (configOptions.outputFolder.resolve("emulab-scripts").toFile().mkdirs()) {
            copyResource("ansible/emulab-scripts/emulabclient.py", "emulab-scripts/emulabclient.py", true);
            copyResource("ansible/emulab-scripts/script_wrapper.py", "emulab-scripts/script_wrapper.py", true);
            copyResource("ansible/emulab-scripts/sslxmlrpc_client.py", "emulab-scripts/sslxmlrpc_client.py", true);
        }
    }

    /**
     * Creates a custom version of the openssl_map.cnf certificate configuration
     * file for this experiment.
     * 
     * @param resourcePath
     *            the path of the template certificate configuration file
     * @param destPath
     *            the destination path of the custom certificate file
     */
    private void writeOpenSSLConfigFile(final String resourcePath, final String destPath) {
        try {
            InputStream source = Thread.currentThread().getContextClassLoader().getResource(resourcePath).openStream();
            Path destination = configOptions.outputFolder.resolve(destPath);

            try (BufferedWriter writer = Files.newBufferedWriter(destination)) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(source, Charset.defaultCharset()))) {
                    reader.lines().forEach((line) -> {
                        try {
                            writer.write(line);
                            writer.newLine();

                            if (line.matches(Pattern.quote("[alt_names]"))) {
                                List<Node> nodes = new ArrayList<>();
                                nodes.addAll(topology.getNodes().values());

                                writer.newLine();

                                for (int n = 0; n < nodes.size(); n++) {
                                    writer.write("DNS." + (n + 1) + " = " + nodes.get(n).getName());
                                    writer.newLine();
                                }

                                writer.newLine();

                                for (int n = 0; n < nodes.size(); n++) {
                                    writer.write("DNS." + (nodes.size() + n + 1) + " = "
                                            + getEmulabHostnameFor(nodes.get(n)));
                                    writer.write("DNS." + (nodes.size() + n + 1) + " = "
                                            + getDcompHostnameFor(nodes.get(n)));
                                    writer.newLine();
                                }

                                writer.newLine();

                                for (int n = 0; n < nodes.size(); n++) {
                                    writer.write("DNS." + (2 * nodes.size() + n + 1) + " = " + nodes.get(n).getName()
                                            + "." + DnsUtils.MAP_TLD);
                                    writer.newLine();
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Error writing openssl configuration file", e);
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException("Error reading openssl configuration file", e);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error writing openssl configuration file", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error writing openssl configuration file", e);
        }
    }

    /**
     * Have a consistent way to get from a Node to a base filename. This is
     * needed to match how the way that ansible uses hostnames in the yaml
     * files.
     */
    private static String getBaseFilenameForNode(final Node node) {
        return node.getName();
    }

    private void writeNeighbors() throws IOException {
        final Map<?, Node> nodes = topology.getNodes();
        for (final Map.Entry<?, Node> sEntry : nodes.entrySet()) {
            final Node node = sEntry.getValue();
            if (MapUtils.isNcp(node)) {
                final Path outputFile = configOptions.outputFolder
                        .resolve(getBaseFilenameForNode(node) + NODE_NEIGHBOR_FILE_SUFFIX);
                try (BufferedWriter writer = Files.newBufferedWriter(outputFile, Charset.defaultCharset())) {

                    for (final Map.Entry<Node, Double> nEntry : getNeighborNodes(node).entrySet()) {
                        final Node other = nEntry.getKey();
                        if (MapUtils.isNcp(other)) {
                            // only write out servers
                            writer.write(other.getName() + "\t" + nEntry.getValue());
                            writer.newLine();
                        }
                    }
                } // use BufferedWriter
            } // non-client
        } // foreach node
    }

    /**
     * Get all neighboring nodes. This passes through switches and includes all
     * neighbors connected to the switch.
     * 
     * @param node
     *            the node to find the neighbors for
     * @return the neighbor nodes and the bandwidth to them
     */
    @Nonnull
    private static Map<Node, Double> getNeighborNodes(@Nonnull final Node source) {
        final Map<Node, Double> neighbors = new HashMap<>();

        source.getLinks().forEach(l -> {
            final NetworkDevice leftDev = l.getLeft();
            final NetworkDevice rightDev = l.getRight();

            // using the bandwidth of the first link. Ideally should use the
            // minimum across all links traversed.
            final double bandwidth = l.getBandwidth();

            final NetworkDevice otherDev = source.equals(leftDev) ? rightDev : leftDev;
            final Set<Node> neighborNodes = MapUtils.resolveOverlayNeighbors(otherDev, Collections.singleton(source));
            neighborNodes.forEach(n -> neighbors.put(n, bandwidth));
        });

        // make sure the source isn't in the list of neighbors
        neighbors.remove(source);

        return neighbors;
    }

    private void writeDnsForRegion(final String region) throws IOException {
        final Set<Node> dnsServers = regionToDns.get(region);
        for (final Node dnsNode : dnsServers) {
            writeDnsForRegionServer(region, dnsNode);
        }
    }

    /**
     * Write out the information for a DNS server in a region.
     * 
     * @param region
     *            the region that the DNS is for
     * @param dnsNode
     *            the node that is the DNS server
     * @throws IOException
     *             if there is a problem writing to the output files
     */
    private void writeDnsForRegionServer(final String region, final Node dnsNode) throws IOException {
        final Path nodeDnsZonesDir = configOptions.outputFolder
                .resolve(String.format("dns/%s/zones", getBaseFilenameForNode(dnsNode)));
        if (!Files.exists(nodeDnsZonesDir)) {
            Files.createDirectories(nodeDnsZonesDir);
        }

        writeMapZone(region, dnsNode);
        writeRegionZone(region, dnsNode);
        writeReverseZones(dnsNode);
        writeConfigTemplate(dnsNode);
    }

    private void writeConfigTemplate(final Node dnsNode) throws IOException {
        final Path configDir = configOptions.outputFolder
                .resolve(String.format("dns/%s/conf", getBaseFilenameForNode(dnsNode)));
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
        }

        final Path destination = configDir.resolve("config.xml");
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ansible/dns/conf/config.template.xml")) {
            Objects.requireNonNull(stream, "Cannot find resource ansible/dns/conf/config.template.xml");

            final StringWriter swriter = new StringWriter();
            IOUtils.copy(stream, swriter, Charset.defaultCharset());
            final String rawTemplate = swriter.toString();

            final InetAddress primaryIp = nodeToPrimaryIp.get(dnsNode);

            final String nodeTemplate = rawTemplate.replaceAll("PRIMARY_IP_ADDRESS", primaryIp.getHostAddress());

            try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write(nodeTemplate);
            }

        } catch (final IOException e) {
            throw new RuntimeException("Error writing " + destination.toString(), e);
        }

        copyResource("ansible/dns/conf/config.template.xml", "dns/conf/config.template.xml");

    }

    private void writeReverseZones(final Node dnsNode) throws IOException {
        for (final Subnet subnet : subnets) {
            final Path zonePath = configOptions.outputFolder.resolve(String.format("dns/%s/zones/%s",
                    getBaseFilenameForNode(dnsNode), getSubnetReverseFilename(subnet)));
            try (BufferedWriter writer = Files.newBufferedWriter(zonePath)) {
                writer.write(String.format("$TTL    %d%n", DEFAULT_TTL));
                writer.write(String.format("@   IN  SOA localhost. root.localhost. (%n"));
                writer.write(String.format("\t1     ; Serial%n"));
                writer.write(String.format("\t604800     ; Refresh%n"));
                writer.write(String.format("\t86400     ; Retry%n"));
                writer.write(String.format("\t2419200     ; Expire%n"));
                writer.write(String.format("\t604800 )   ; Negative Cache TTL%n"));
                writer.write(String.format(";%n"));
                writer.write(String.format("@   IN  NS  ns.%n"));

                for (final Map.Entry<InetAddress, Node> sentry : subnet.getAddressesInSubnet().entrySet()) {
                    final InetAddress nodeAddress = sentry.getKey();
                    final Node node = sentry.getValue();
                    final int[] nodeOctets = Address.toArray(nodeAddress.getHostAddress());

                    writer.write(String.format("%d IN PTR %s.%s.%n", nodeOctets[3], node.getName(), DnsUtils.MAP_TLD));

                    // only write containers if this is an NCP and this is the
                    // primary IP for the node
                    final InetAddress nodePrimaryIp = nodeToPrimaryIp.get(node);
                    if (MapUtils.isNcp(node) && nodeAddress.equals(nodePrimaryIp)) {
                        final Map<InetAddress, String> containerAddresses = nodeToContainerAddresses.getOrDefault(node,
                                Collections.emptyMap());
                        for (final Map.Entry<InetAddress, String> centry : containerAddresses.entrySet()) {
                            final InetAddress containerAddress = centry.getKey();
                            final String containerName = centry.getValue();

                            final int[] containerOctets = Address.toArray(containerAddress.getHostAddress());

                            writer.write(String.format("%d IN PTR %s.%s.%n", containerOctets[3], containerName,
                                    DnsUtils.MAP_TLD));

                        } // foreach container
                    } // node is server
                } // foreach node in subnet
            } // writer
        } // foreach subnet
    }

    private static String getRegionZoneFilename(final String region) {
        return String.format("%s.%s", region, DnsUtils.MAP_TLD);
    }

    private void writeRegionZone(final String region, final Node dnsNode) throws IOException {
        final Path regionZonePath = configOptions.outputFolder.resolve(
                String.format("dns/%s/zones/%s", getBaseFilenameForNode(dnsNode), getRegionZoneFilename(region)));

        try (BufferedWriter writer = Files.newBufferedWriter(regionZonePath)) {
            writer.write(String.format("$ORIGIN .%n"));
            writer.write(String.format("$TTL %d%n", DEFAULT_TTL));
            writer.write(String.format("%s.%s         IN SOA  %s.%s. hostmaster.%s. (%n", region, DnsUtils.MAP_TLD,
                    dnsNode.getName(), DnsUtils.MAP_TLD, DnsUtils.MAP_TLD));
            writer.write(String.format("\t2018020506 ; serial%n"));
            writer.write(String.format("\t28800      ; refresh (8 hours)%n"));
            writer.write(String.format("\t7200       ; retry (2 hours)%n"));
            writer.write(String.format("\t2419200    ; expire (4 weeks)%n"));
            writer.write(String.format("\t86400      ; minimum (1 day)%n"));
            writer.write(String.format(")%n"));
            writer.write(String.format("\tNS  %s.%s.%n", dnsNode.getName(), DnsUtils.MAP_TLD));
            writer.write(String.format("$ORIGIN %s.%s.%n", region, DnsUtils.MAP_TLD));
            writer.write(String.format("@       IN      NS      %s.%s.%n", dnsNode.getName(), DnsUtils.MAP_TLD));
        }

    }

    private void writeDnsMapping() throws IOException {
        final Path csvPath = configOptions.outputFolder.resolve(String.format("dns/host-ip.csv"));

        try (BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
            writer.write(String.format("host,ip%n"));
            
            for (final Map.Entry<Node, InetAddress> entry : nodeToPrimaryIp.entrySet()) {
                final Node node = entry.getKey();
                final String nodeName = node.getName();
                final InetAddress addr = entry.getValue();
                writer.write(String.format("%s,%s%n", nodeName, addr.getHostAddress()));

                if (MapUtils.isNcp(node)) {
                    final Map<InetAddress, String> containerNames = nodeToContainerAddresses.getOrDefault(node,
                            Collections.emptyMap());
                    for (final Map.Entry<InetAddress, String> centry : containerNames.entrySet()) {
                        final InetAddress containerAddress = centry.getKey();
                        final String baseContainerName = centry.getValue();

                        writer.write(
                                String.format("%s,%s%n", baseContainerName, containerAddress.getHostAddress()));
                    } // foreach container
                } // if server

            } // foreach node
        } // writer
    }

    private void writeMapZone(final String thisRegion, final Node dnsNode) throws IOException {
        final Path mapZonePath = configOptions.outputFolder
                .resolve(String.format("dns/%s/zones/%s", getBaseFilenameForNode(dnsNode), DnsUtils.MAP_TLD));

        try (BufferedWriter writer = Files.newBufferedWriter(mapZonePath)) {
            writer.write(String.format("$ORIGIN .%n"));
            writer.write(String.format("$TTL %d%n", DEFAULT_TTL));
            writer.write(String.format("%s IN SOA  ns.%s. hostmaster.%s. (%n", DnsUtils.MAP_TLD, DnsUtils.MAP_TLD,
                    DnsUtils.MAP_TLD));
            writer.write(String.format("\t2018020504 ; serial%n"));
            writer.write(String.format("\t28800      ; refresh (8 hours)%n"));
            writer.write(String.format("\t7200       ; retry (2 hours)%n"));
            writer.write(String.format("\t2419200    ; expire (4 weeks)%n"));
            writer.write(String.format("\t86400      ; minimum (1 day)%n"));
            writer.write(String.format(")%n"));
            writer.write(String.format("\tNS  ns.%s.%n", DnsUtils.MAP_TLD));
            writer.write(String.format("$ORIGIN %s.%n", DnsUtils.MAP_TLD));

            final InetAddress dnsPrimaryIp = nodeToPrimaryIp.get(dnsNode);
            writer.write(String.format("ns\t\tA %s ; node running dns handler for the region%n",
                    dnsPrimaryIp.getHostAddress()));

            for (final Map.Entry<Node, InetAddress> entry : nodeToPrimaryIp.entrySet()) {
                final Node node = entry.getKey();
                final String nodeName = node.getName();
                final InetAddress addr = entry.getValue();
                writer.write(String.format("%s\tA\t%s%n", nodeName, addr.getHostAddress()));

                if (MapUtils.isNcp(node)) {
                    final Map<InetAddress, String> containerNames = nodeToContainerAddresses.getOrDefault(node,
                            Collections.emptyMap());
                    for (final Map.Entry<InetAddress, String> centry : containerNames.entrySet()) {
                        final InetAddress containerAddress = centry.getKey();
                        final String baseContainerName = centry.getValue();

                        writer.write(
                                String.format("%s\tA\t%s%n", baseContainerName, containerAddress.getHostAddress()));
                    } // foreach container
                } // if server

            } // foreach node

            writer.write(String.format("; delegate records for each regional subdomain%n"));

            for (final Map.Entry<String, Set<Node>> entry : regionToDns.entrySet()) {
                final String regionName = entry.getKey();
                if (!thisRegion.equals(regionName)) {

                    // TODO ticket:99 verify that writing multiple NS entries is
                    // valid

                    writer.write(String.format("$ORIGIN %s.%s.%n", regionName, DnsUtils.MAP_TLD));

                    for (final Node regionDnsNode : entry.getValue()) {
                        writer.write(String.format("@       IN      NS      %s.%s.%n", regionDnsNode.getName(),
                                DnsUtils.MAP_TLD));
                    }
                    writer.newLine();
                }
            } // foreach region
        } // writer

    }

    private String getEmulabHostnameFor(final Node node) {
        return getEmulabHostnameFor(node.getName());
    }

    private String getEmulabHostnameFor(final String name) {
        return String.format("%s.%s.%s.emulab.net", name, configOptions.experiment, configOptions.emulabGroup);
    }

    private String getDcompHostnameFor(final Node node) {
        return getDcompHostnameFor(node.getName());
    }

    private String getDcompHostnameFor(final String name) {
        return String.format("%s.%s.%s.%s", name, getScenarioName(), configOptions.experiment,
                configOptions.dcompProject);
    }

    /**
     * 
     * @param subnet
     *            the subnet to get the filename for
     * @return the base filename
     */
    private String getSubnetReverseFilename(final Subnet subnet) {
        // Note: assumes class C subnets, see getSubnetPrefix
        final int[] octets = Address.toArray(subnet.getPrefix().getHostAddress());

        final String filename = String.format("%d.%d.%d.in-addr.arpa", octets[2], octets[1], octets[0]);
        return filename;
    }

    private void writeResolveConfHead() throws IOException {
        final Path resolveConfHeadDir = configOptions.outputFolder.resolve("dns/resolv.conf.d-head");
        if (!Files.exists(resolveConfHeadDir)) {
            Files.createDirectories(resolveConfHeadDir);
        }

        for (final Map.Entry<String, Node> entry : topology.getNodes().entrySet()) {
            final Node node = entry.getValue();
            if (!MapUtils.isUnderlay(node)) {
                final Path nodePath = resolveConfHeadDir.resolve(getBaseFilenameForNode(node));
                final String region = nodeToRegion.get(node);
                final Set<Node> dnsServers = regionToDns.get(region);
                Objects.requireNonNull(dnsServers, String.format("Region %s doesn't have any DNS servers", region));

                try (BufferedWriter writer = Files.newBufferedWriter(nodePath)) {
                    for (final Node dnsNode : dnsServers) {
                        final InetAddress primaryIp = nodeToPrimaryIp.get(dnsNode);
                        writer.write(String.format("nameserver %s%n", primaryIp.getHostAddress()));
                    } // foreach DNS server

                    writer.write(String.format("search %s%n", DnsUtils.MAP_TLD));
                } // using writer
            } // not underlay
        } // foreach node
    }

    private static final class RouterIdGenerator {
        private short octet1 = 0;
        private short octet2 = 0;
        private short octet3 = 0;
        private short octet4 = 0;
        private static final short MAX_OCTET_VALUE = 255;

        private void increment() {
            ++octet4;
            if (octet4 > MAX_OCTET_VALUE) {
                octet4 = 0;
                ++octet3;
            }
            if (octet3 > MAX_OCTET_VALUE) {
                octet3 = 0;
                ++octet2;
            }
            if (octet2 > MAX_OCTET_VALUE) {
                octet2 = 0;
                ++octet1;
            }
            if (octet1 > MAX_OCTET_VALUE) {
                throw new RuntimeException("Too many router IDs generated");
            }
        }

        /**
         * @return the next router ID
         */
        public String getNextId() {
            final String id = String.format("%d.%d.%d.%d", octet1, octet2, octet3, octet4);
            increment();
            return id;
        }
    }

    private void writeQuaggaConfig() throws IOException {
        final Path baseDir = configOptions.outputFolder.resolve("quagga");
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }

        copyResource("ansible/16-setup-quagga.yml", "16-setup-quagga.yml");
        copyResource("ansible/stop-quagga.yml", "stop-quagga.yml");

        final RouterIdGenerator idGen = new RouterIdGenerator();
        for (final Map.Entry<String, Node> entry : topology.getNodes().entrySet()) {
            final Node node = entry.getValue();
            final String basename = getBaseFilenameForNode(node);

            final Path ospfConfig = baseDir.resolve("ospfd.conf." + basename);
            try (BufferedWriter writer = Files.newBufferedWriter(ospfConfig)) {
                writer.write(String.format("hostname %s%n", basename));
                writer.write(String.format("password zebra%n"));
                writer.write(String.format("log file /var/log/quagga/ospfd.log%n"));
                writer.write(String.format("router ospf%n"));

                writer.write(String.format("  ospf router-id %s%n", idGen.getNextId()));

                for (final InetAddress addr : nodeToAllAddresses.get(node)) {
                    final InetAddress subnetPrefix = getSubnetPrefix(addr);
                    writer.write(String.format("  network %s/%d area 0.0.0.0%n", subnetPrefix.getHostAddress(),
                            NETMASK_LENGTH));
                } // foreach network

            } // using writer

            final Path zebraConfig = baseDir.resolve("zebra.conf." + basename);
            try (BufferedWriter writer = Files.newBufferedWriter(zebraConfig)) {
                writer.write(String.format("hostname %s%n", basename));
                writer.write(String.format("password zebra%n"));
                writer.write(String.format("enable password zebra%n"));
                writer.write(String.format("log file /var/log/quagga/zebra.log%n"));
            } // using writer

        } // foreach node

    }

    private void writeDcompPython() throws IOException {
        final Path output = configOptions.outputFolder.resolve("dcomp-topology.py");

        final Set<Link> pointToPoint = new HashSet<>();
        final Set<Switch> lans = new HashSet<>();

        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write(String.format("import mergexp%n"));
            writer.write(String.format("import mergexp.net%n"));
            writer.write(String.format("import mergexp.unit%n%n"));
            writer.write(String.format("import mergexp.machine%n%n"));
            writer.write(String.format("import mergexp.meta%n%n"));

            writer.write(String.format("topology = mergexp.Topology('%s')%n%n", getScenarioName()));

            writer.write(String.format("# nodes%n"));
            topology.getNodes().entrySet().stream().forEach(Errors.rethrow().wrap(entry -> {
                final Node node = entry.getValue();

                writer.write(String.format("%s = topology.device('%s'", node.getName(), node.getName()));
                final String hardwareConfigName = node.getHardware();
                if (null != hardwareConfigName) {
                    final HardwareConfiguration hwConfig = hardwareConfigs.get(hardwareConfigName);
                    if (null == hwConfig) {
                        throw new RuntimeException(
                                "Node '" + node.getName() + "' specifies hardware '" + hardwareConfigName
                                        + "', but it is not specified in the hardware configurations file");
                    }
                    final String dcompConfig = hwConfig.getDcompConfig();
                    if (null != dcompConfig) {
                        writer.write(String.format(", %s", dcompConfig));
                    }
                }
                writer.write(String.format(")%n"));

                // group the links and switches
                node.getLinks().forEach(link -> {
                    if (link.getLeft() instanceof Switch) {
                        lans.add((Switch) link.getLeft());
                    } else if (link.getRight() instanceof Switch) {
                        lans.add((Switch) link.getRight());
                    } else {
                        pointToPoint.add(link);
                    }
                });
            }));

            writer.write(String.format("%n# network links%n"));
            pointToPoint.stream().forEach(Errors.rethrow().wrap(link -> {
                writer.write(String.format(
                        "%s = topology.connect([%s, %s], mergexp.net.capacity == mergexp.unit.mbps(%f))%n",
                        link.getName(), link.getLeft().getName(), link.getRight().getName(), link.getBandwidth()));

                // given that these are point to point links we know that left
                // and right are both Node objects
                writer.write(String.format("%s[%s].ip.addrs = ['%s/%d']%n", link.getName(), link.getLeft().getName(),
                        ((Node) link.getLeft()).getIpAddress(link).getHostAddress(), NETMASK_LENGTH));
                writer.write(String.format("%s[%s].ip.addrs = ['%s/%d']%n", link.getName(), link.getRight().getName(),
                        ((Node) link.getRight()).getIpAddress(link).getHostAddress(), NETMASK_LENGTH));

                writer.write(String.format("%n"));
            }));

            lans.stream().forEach(Errors.rethrow().wrap(lan -> {
                final String nodes = lan.getNodes().stream().map(Node::getName).collect(Collectors.joining(","));
                writer.write(
                        String.format("%s = topology.connect([%s], mergexp.net.capacity == mergexp.unit.mbps(%f))%n",
                                lan.getName(), nodes, lan.getBandwidth()));

                lan.getLinks().entrySet().stream().forEach(Errors.rethrow().wrap(entry -> {
                    final Node node = entry.getKey();
                    final Link link = entry.getValue();
                    final InetAddress addr = node.getIpAddress(link);
                    writer.write(String.format("%s[%s].ip.addrs = ['%s/%d']%n", link.getName(), node.getName(),
                            addr.getHostAddress(), NETMASK_LENGTH));
                }));

                writer.write(String.format("%n"));
            }));

            writer.write(String.format("%nmergexp.experiment(topology)%n"));
        } // allocate writer

    }

    private static final String EMULAB_MAP_DISK_IMAGE_URN = "urn:publicid:IDN+emulab.net+image+a3//UBUNTU18-64-MAP";

    private static double mbpsToEmulabPython(final double mbps) {
        return mbps * 1000;
    }

    private void writeEmulabPython() throws IOException {
        final Path output = configOptions.outputFolder.resolve("emulab-topology.py");

        final Set<Link> pointToPoint = new HashSet<>();
        final Set<Switch> lans = new HashSet<>();

        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write(String.format("\"\"\"%s\"\"\"%n", getScenarioName()));
            writer.write(String.format("import geni.portal as portal%n"));
            writer.write(String.format("import geni.rspec.pg as pg%n"));
            writer.write(String.format("import geni.rspec.emulab as emulab%n"));
            writer.write(String.format("pc = portal.Context()%n"));
            writer.write(String.format("request = pc.makeRequestRSpec()%n%n"));

            writer.write(String.format("# nodes%n"));
            topology.getNodes().entrySet().stream().forEach(Errors.rethrow().wrap(entry -> {
                final Node node = entry.getValue();
                writer.write(String.format("%s = request.RawPC('%s')%n", node.getName(), node.getName()));
                writer.write(String.format("%s.disk_image = '%s'%n", node.getName(), EMULAB_MAP_DISK_IMAGE_URN));

                final String hardwareConfigName = node.getHardware();
                if (null != hardwareConfigName) {
                    final HardwareConfiguration hwConfig = hardwareConfigs.get(hardwareConfigName);
                    if (null == hwConfig) {
                        throw new RuntimeException(
                                "Node '" + node.getName() + "' specifies hardware '" + hardwareConfigName
                                        + "', but it is not specified in the hardware configurations file");
                    }
                    final String emulabConfig = hwConfig.getEmulabConfig();
                    if (null != emulabConfig) {
                        writer.write(String.format("%s.hardware_type = \"%s\"%n", node.getName(), emulabConfig));
                    }
                }

                // group the links and switches
                node.getLinks().forEach(link -> {
                    if (link.getLeft() instanceof Switch) {
                        lans.add((Switch) link.getLeft());
                    } else if (link.getRight() instanceof Switch) {
                        lans.add((Switch) link.getRight());
                    } else {
                        pointToPoint.add(link);
                    }
                });
            }));

            writer.write(String.format("%n# network links%n"));
            pointToPoint.stream().forEach(Errors.rethrow().wrap(link -> {
                writer.write(String.format("%s = request.Link(name='%s')%n", link.getName(), link.getName()));

                final Node leftNode = (Node) link.getLeft();
                final InetAddress leftAddr = leftNode.getIpAddress(link);

                final String leftInterface = createEmulabPythonInterface(writer, leftNode, leftAddr, link.getName(),
                        link.getBandwidth());

                writer.write(String.format("%s.addInterface(%s)%n", link.getName(), leftInterface));

                final Node rightNode = (Node) link.getRight();
                final InetAddress rightAddr = rightNode.getIpAddress(link);

                final String rightInterface = createEmulabPythonInterface(writer, rightNode, rightAddr, link.getName(),
                        link.getBandwidth());

                writer.write(String.format("%s.addInterface(%s)%n", link.getName(), rightInterface));

                writer.write(String.format("%n"));
            }));

            lans.stream().forEach(Errors.rethrow().wrap(lan -> {
                writer.write(String.format("%s = request.LAN(name='%s')%n", lan.getName(), lan.getName()));

                lan.getLinks().entrySet().stream().forEach(Errors.rethrow().wrap(entry -> {
                    final Node node = entry.getKey();
                    final Link link = entry.getValue();
                    final InetAddress addr = node.getIpAddress(link);

                    final String ifce = createEmulabPythonInterface(writer, node, addr, lan.getName(),
                            lan.getBandwidth());

                    writer.write(String.format("%s.addInterface(%s)%n", lan.getName(), ifce));
                }));

                writer.write(String.format("%n"));
            }));

            writer.write(String.format("%nportal.context.printRequestRSpec()%n"));
        } // allocate writer
    }

    /**
     * 
     * @return interface name
     */
    private static String createEmulabPythonInterface(final BufferedWriter writer,
            final Node node,
            final InetAddress addr,
            final String lanOrLinkName,
            final double mbps) throws IOException {
        final String interfaceName = String.format("%s_%s", node.getName(), lanOrLinkName);

        writer.write(String.format("%s = %s.addInterface(name='%s', address=pg.IPv4Address('%s', '%s'))%n",
                interfaceName, node.getName(), lanOrLinkName, addr.getHostAddress(), NETMASK));
        writer.write(String.format("%s.bandwidth = %f%n", interfaceName, mbpsToEmulabPython(mbps)));
        return interfaceName;
    }

    private static Set<Subnet> computeSubnets(final Topology topology) {
        final Map<InetAddress, Subnet> subnets = new HashMap<>();
        topology.getNodes().forEach((nodeName, node) -> {
            node.getAllIpAddresses().forEach((link, addr) -> {
                final InetAddress subnetPrefix = getSubnetPrefix(addr);
                final Subnet subnet = subnets.computeIfAbsent(subnetPrefix, k -> new Subnet(k));
                subnet.addAddress(addr, node);
            });
        });

        return subnets.entrySet().stream().map(Map.Entry::getValue).collect(Collectors.toSet());
    }

    /**
     * Convert an IP address to a class C subnet address.
     * 
     * @param addr
     *            the ip address
     * @return the subnet prefix as an address
     */
    private static InetAddress getSubnetPrefix(final InetAddress addr) {
        final InetAddress subnetAddress = Address.truncate(addr, NETMASK_LENGTH);
        return subnetAddress;
    }

    /**
     * Size of the subnet mask used for all networks.
     */
    private static final int NETMASK_LENGTH = 24;
    /**
     * Needs to match {@link #NETMASK_LENGTH}.
     */
    private static final String NETMASK = "255.255.255.0";

    /**
     * Track the addresses used in a subnet.
     * 
     * @author jschewe
     *
     */
    private static final class Subnet {
        /**
         * @param prefix
         *            the address of the subnet
         */
        Subnet(final InetAddress prefix) {
            this.prefix = prefix;
        }

        private final InetAddress prefix;

        /**
         * 
         * @return the prefix of the subnet.
         */
        public InetAddress getPrefix() {
            return prefix;
        }

        private final Map<InetAddress, Node> addressesInSubnet = new HashMap<>();

        /**
         * Addresses in the subnet.
         * 
         * @return Address -> Node (unmodifiable)
         */
        public Map<InetAddress, Node> getAddressesInSubnet() {
            return Collections.unmodifiableMap(addressesInSubnet);
        }

        /**
         * Add an address to the subnet.
         * 
         * @param addr
         *            the address to add
         * @param node
         *            the node associated with the address
         */
        public void addAddress(final InetAddress addr, final Node node) {
            if (addressesInSubnet.containsKey(addr)) {
                throw new IllegalArgumentException(addr + " already in subnet " + prefix + " with node "
                        + addressesInSubnet.get(addr).getName() + " while trying to add node: " + node.getName());
            }

            addressesInSubnet.put(addr, node);
        }

        @Override
        public int hashCode() {
            return prefix.hashCode();
        }

        @Override
        public boolean equals(final Object o) {
            if (null == o) {
                return false;
            } else if (this == o) {
                return true;
            } else if (this.getClass().equals(o.getClass())) {
                final Subnet other = (Subnet) o;
                return getPrefix().equals(other.getPrefix());
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return String.format("prefix: %s", prefix.getHostAddress());
        }
    } // class Subnet

    private static final int BACKGROUND_TRAFFIC_MIN_PORT = 50050; // after AP
    private static final int BACKGROUND_TRAFFIC_MAX_PORT = 60000;

    /**
     * 
     * @param requests
     *            rxPort and txProperties will be set by this method
     * @throws RuntimeException
     *             if all of the port numbers have been exhausted
     */
    private static void assignBackgroundTrafficPorts(final ImmutableList<BackgroundNetworkLoad> requests) {
        final Map<String, Integer> nextPort = new HashMap<>();
        requests.stream().forEach(bt -> {
            bt.setRxPort(nextPort.computeIfAbsent(bt.getServer(), k -> BACKGROUND_TRAFFIC_MIN_PORT));
            bt.setTxPort(bt.getRxPort() + 1);
            nextPort.put(bt.getServer(), bt.getTxPort() + 1);

            if (bt.getRxPort() > BACKGROUND_TRAFFIC_MAX_PORT || bt.getTxPort() > BACKGROUND_TRAFFIC_MAX_PORT) {
                throw new RuntimeException("Too many background traffic requests for " + bt.getServer());
            }
        });
    }

}
