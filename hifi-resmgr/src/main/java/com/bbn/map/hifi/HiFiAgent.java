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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.DnsResolutionType;
import com.bbn.map.Controller;
import com.bbn.map.MapOracle;
import com.bbn.map.NetworkServices;
import com.bbn.map.ServiceConfiguration;
import com.bbn.map.ap.MapNetworkFactory;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.dns.DNSUpdateService;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.PlanTranslator;
import com.bbn.map.hifi.dns.DnsServer;
import com.bbn.map.hifi.ta2.TA2Impl;
import com.bbn.map.hifi.util.AbsoluteClock;
import com.bbn.map.hifi.util.ConcurrencyUtils;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.hifi.util.IdentifierUtils;
import com.bbn.map.hifi.util.SimAppUtils;
import com.bbn.map.hifi_resmgr.SimpleDockerResourceManager;
import com.bbn.map.simulator.HardwareConfiguration;
import com.bbn.map.simulator.SimulationRunner;
import com.bbn.map.ta2.TA2Interface;
import com.bbn.map.utils.LogExceptionHandler;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LoadBalancerPlan;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeLookupService;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionServiceState;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.utils.VirtualClock;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Launch the agent.
 * 
 * @author jschewe
 *
 */
public class HiFiAgent implements NetworkServices, MapOracle {

    // put this first to ensure that the correct logging configuration is used
    static {
        System.setProperty("log4j.configurationFactory", MapAgentLoggingConfig.class.getName());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(HiFiAgent.class);

    // CHECKSTYLE:OFF value class for storing parameters
    private static final class Parameters {
        public String hostname;
        public Path neighborsFile;
        public Path configurationFile;
        public int apPort;
        public Path serviceConfigurationFile;
        public Path serviceDependencyFile;
        public Path containerNamesFile;

        public Path regionSubnetFile;
        public String dockerRegistryHostname;
        public String globalLeader;

        // dump options
        public Duration dumpInterval = Duration.ofSeconds(SimulationRunner.DEFAULT_DUMP_INTERVAL_SECONDS);
        public Path dumpDirectory = null;
        public boolean dumpEnabled = false;
        // end dump options

        public String hardwareConfigName;

        public Path testbedControlSubnetsFile;

        public String imageFetcherClassname = IMAGE_FETCHER_CLASSNAME_DEFAULT;
        public int dcopPort;
        public Path dcopLeadersFile;

    }
    // CHECKSTYLE:ON

    /**
     * Name of property to read from {@link #LOCAL_PROPERTIES_FILENAME} to get
     * the node name. This is used to identify the node to other components in
     * the network.
     */
    public static final String HOSTNAME_PROPERTY_KEY = "HOSTNAME";

    /**
     * Name of property to read from {@link #GLOBAL_PROPERTIES_FILENAME} to get
     * the port number that AP will use to communicate.
     */
    public static final String AP_PORT_PROPERTY_KEY = "AP_PORT";

    /**
     * Name of property to read from {@link #GLOBAL_PROPERTIES_FILENAME} to get
     * the hostname of the docker registry.
     */
    public static final String DOCKER_REGISTRY_HOST_KEY = "DOCKER_REGISTRY_HOSTNAME";

    /**
     * File to read local properties from.
     */
    public static final String LOCAL_PROPERTIES_FILENAME = "local.properties";

    /**
     * File to read global properties from.
     */
    public static final String GLOBAL_PROPERTIES_FILENAME = "global.properties";

    /**
     * Name of property to read from {@link #GLOBAL_PROPERTIES_FILENAME} to get
     * the dump interval. This value is specified in seconds or parsed by
     * {@link java.time.Duration#parse(CharSequence)}.
     * 
     * @see #DUMP_ENABLED_GLOBAL_KEY
     */
    public static final String DUMP_INTERVAL_GLOBAL_KEY = "DUMP_INTERVAL";

    /**
     * Name of a property to read from {@link #GLOBAL_PROPERTIES_FILENAME} to
     * get the directory to write state information to.
     * 
     * @see #DUMP_ENABLED_GLOBAL_KEY
     */
    public static final String DUMP_DIRECTORY_GLOBAL_KEY = "DUMP_DIRECTORY";

    /**
     * Name of a property to read from {@link #GLOBAL_PROPERTIES_FILENAME} to
     * see if dumping of state information is enabled. No information is written
     * unless this property is true. This value is parsed by
     * {@link Boolean#parseBoolean(String)}.
     */
    public static final String DUMP_ENABLED_GLOBAL_KEY = "DUMP_ENABLED";

    /**
     * Name of a property to read from {@link #GLOBAL_PROPERTIES_FILENAME} to
     * see the name of the global leader.
     */
    public static final String GLOBAL_LEADER_KEY = "GLOBAL_LEADER";

    /**
     * The filename that the service configurations are read from. Needs to
     * match stage experiment ansible 10-setup.yml.
     */
    public static final String SERVICE_CONFIGURATION_FILENAME = "service-configurations.json";

    /**
     * The filename that the service dependencies are read from. Needs to match
     * stage experiment ansible 10-setup.yml.
     */
    public static final String SERVICE_DEPENDENCIES_FILENAME = "service-dependencies.json";

    /**
     * The filename that region subnet information is read from.
     */
    public static final String REGION_SUBNET_FILENAME = "region_subnet.txt";

    /**
     * The filename that DCOP leader information is read from.
     */
    public static final String DCOP_LEADERS_FILENAME = "dcop_leaders.txt";

    /**
     * Name of property to read from {@link #LOCAL_PROPERTIES_FILENAME} to get
     * the hardware configuration information.
     */
    public static final String HARDWARE_PROPERTY_KEY = "HARDWARE";

    /**
     * Default class to use for fetching images.
     */
    public static final String IMAGE_FETCHER_CLASSNAME_DEFAULT = "com.bbn.map.hifi_resmgr.DockerFetcher";

    /**
     * Name of property to read from {@link #GLOBAL_PROPERTIES_FILENAME} to get
     * the classname to use for fetching docker images.
     */
    public static final String IMAGE_FETCHER_CLASSNAME_KEY = "IMAGE_FETCHER_CLASSNAME";

    /**
     * Name of property to read from {@link #GLOBAL_PROPERTIES_FILENAME} to get
     * the port number that DCOP will use to communicate.
     */
    public static final String DCOP_PORT_PROPERTY_KEY = "DCOP_PORT";

    /**
     * @param configurationDirectory
     *            the directory read the configuration from
     * @return the parameters or null on error (an error will have been logged)
     * @throws IOException
     *             if there is an error reading from a configuration file
     */
    private static Parameters parseConfigurationDirectory(@Nonnull final Path configurationDirectory)
            throws IOException {
        final Parameters parameters = new Parameters();

        final Path localPropsFile = configurationDirectory.resolve(LOCAL_PROPERTIES_FILENAME);
        if (!Files.exists(localPropsFile)) {
            LOGGER.error("{} does not exist", localPropsFile);
            return null;
        }

        final Path agentConfigFile = configurationDirectory.resolve(DnsServer.AGENT_CONFIGURATION_FILENAME);
        if (Files.exists(agentConfigFile)) {
            AgentConfiguration.readFromFile(agentConfigFile);
        }

        final Properties localProps = new Properties();
        try (Reader reader = Files.newBufferedReader(localPropsFile)) {
            localProps.load(reader);
        }
        parameters.hostname = localProps.getProperty(HOSTNAME_PROPERTY_KEY, null);
        if (null == parameters.hostname) {
            LOGGER.error("{} does not contain property {}", localPropsFile, HOSTNAME_PROPERTY_KEY);
            return null;
        }

        parameters.hardwareConfigName = localProps.getProperty(HARDWARE_PROPERTY_KEY, null);
        if (null == parameters.hardwareConfigName) {
            LOGGER.error("{} does not contain property {}", localPropsFile, HARDWARE_PROPERTY_KEY);
            return null;
        }

        final Path globalPropsFile = configurationDirectory.resolve(GLOBAL_PROPERTIES_FILENAME);
        if (!Files.exists(globalPropsFile)) {
            LOGGER.error("{} does not exist", globalPropsFile);
            return null;
        }

        final Properties globalProps = new Properties();
        try (Reader reader = Files.newBufferedReader(globalPropsFile)) {
            globalProps.load(reader);
        }

        parameters.apPort = Integer.parseInt(globalProps.getProperty(AP_PORT_PROPERTY_KEY, "-1"));
        if (parameters.apPort < 1) {
            LOGGER.error("{} does not contain property {}", globalPropsFile, AP_PORT_PROPERTY_KEY);
            return null;
        }

        parameters.dockerRegistryHostname = globalProps.getProperty(DOCKER_REGISTRY_HOST_KEY, null);
        if (null == parameters.dockerRegistryHostname) {
            LOGGER.error("{} does not contain property {}", globalPropsFile, DOCKER_REGISTRY_HOST_KEY);
            return null;
        }

        parameters.globalLeader = globalProps.getProperty(GLOBAL_LEADER_KEY, null);
        if (null == parameters.globalLeader) {
            LOGGER.error("{} does not contain property {}", globalPropsFile, GLOBAL_LEADER_KEY);
            return null;
        }

        parameters.neighborsFile = configurationDirectory.resolve("neighbors.txt");
        if (!Files.exists(parameters.neighborsFile)) {
            LOGGER.error("{} does not exist", parameters.neighborsFile);
            return null;
        }

        parameters.testbedControlSubnetsFile = configurationDirectory.resolve("testbed-control-subnets.txt");
        if (!Files.exists(parameters.testbedControlSubnetsFile)) {
            LOGGER.error("{} does not exist", parameters.testbedControlSubnetsFile);
            return null;
        }

        parameters.configurationFile = configurationDirectory.resolve("config.json");
        if (!Files.exists(parameters.configurationFile)) {
            LOGGER.error("{} does not exist", parameters.configurationFile);
            return null;
        }

        parameters.serviceConfigurationFile = configurationDirectory.resolve(SERVICE_CONFIGURATION_FILENAME);
        if (!Files.exists(parameters.serviceConfigurationFile)) {
            LOGGER.error("{} does not exist", parameters.serviceConfigurationFile);
            return null;
        }

        // this file can be missing and the system will still run properly
        parameters.serviceDependencyFile = configurationDirectory.resolve(SERVICE_DEPENDENCIES_FILENAME);

        parameters.containerNamesFile = configurationDirectory.resolve("container-names.txt");
        if (!Files.exists(parameters.containerNamesFile)) {
            LOGGER.error("{} does not exist", parameters.containerNamesFile);
            return null;
        }

        parameters.regionSubnetFile = configurationDirectory.resolve(REGION_SUBNET_FILENAME);
        if (!Files.exists(parameters.regionSubnetFile)) {
            LOGGER.error("{} does not exist", parameters.regionSubnetFile);
            return null;
        }

        // dump options
        String propValue = globalProps.getProperty(DUMP_ENABLED_GLOBAL_KEY, null);
        if (null != propValue) {
            parameters.dumpEnabled = Boolean.parseBoolean(propValue);
        }

        propValue = globalProps.getProperty(DUMP_DIRECTORY_GLOBAL_KEY, null);
        if (null != propValue) {
            parameters.dumpDirectory = Paths.get(propValue);
        }

        propValue = globalProps.getProperty(DUMP_INTERVAL_GLOBAL_KEY, null);
        if (null != propValue) {
            parameters.dumpInterval = SimulationRunner.parseDuration(propValue);
        }

        // end dump options

        propValue = globalProps.getProperty(IMAGE_FETCHER_CLASSNAME_KEY, null);
        if (null != propValue) {
            parameters.imageFetcherClassname = propValue;
        }

        parameters.dcopPort = Integer.parseInt(globalProps.getProperty(DCOP_PORT_PROPERTY_KEY, "-1"));
        if (parameters.apPort < 1) {
            LOGGER.error("{} does not contain property {}", globalPropsFile, DCOP_PORT_PROPERTY_KEY);
            return null;
        }

        parameters.dcopLeadersFile = configurationDirectory.resolve(DCOP_LEADERS_FILENAME);
        if (!Files.exists(parameters.dcopLeadersFile)) {
            LOGGER.error("{} does not exist", parameters.dcopLeadersFile);
            return null;
        }

        return parameters;
    }

    private static final int CONTAINER_ADDRESS_LOOKUP_MAX_ATTEMPTS = 10;

    /**
     * Parse the container names from the specified file and get their address
     * information.
     * 
     * @param containerNamesFile
     *            where to read the container names from
     * @return the list or null on error (an error will be logged)
     * @throws IOException
     *             if there is an error reading the file
     */
    private static ImmutableMap<NodeIdentifier, InetAddress> parseContainerNames(@Nonnull final Path containerNamesFile)
            throws IOException {
        if (!Files.exists(containerNamesFile)) {
            LOGGER.error("{} does not exist", containerNamesFile);
            return null;
        }

        try (Stream<String> stream = Files.lines(containerNamesFile)) {
            final ImmutableMap<NodeIdentifier, InetAddress> containerInformation = stream
                    .filter(line -> !(null == line || line.trim().isEmpty()))//
                    .map(line -> {
                        final NodeIdentifier id = IdentifierUtils.getNodeIdentifier(line);
                        return id;
                    }) //
                    .collect(Collectors.collectingAndThen(//
                            Collectors.toMap(Function.identity(), HiFiAgent::addressForIdentifier), //
                            ImmutableMap::copyOf));

            if (containerInformation.isEmpty()) {
                LOGGER.warn("No container names found in {}", containerNamesFile);
            }

            return containerInformation;
        }
    }

    /**
     * Find the address for a name. If
     * {@link #CONTAINER_ADDRESS_LOOKUP_MAX_ATTEMPTS} lookup attempts fail, this
     * throws a {@link RuntimeException}.
     */
    private static InetAddress addressForIdentifier(final NodeIdentifier id) {
        final String name = id.getName();
        for (int attempt = 0; attempt < CONTAINER_ADDRESS_LOOKUP_MAX_ATTEMPTS; ++attempt) {
            try {
                final InetAddress addr = DnsUtils.getByName(name);
                return addr;
            } catch (final UnknownHostException e) {
                if (attempt + 1 < CONTAINER_ADDRESS_LOOKUP_MAX_ATTEMPTS) {
                    final long retryDelay = SimAppUtils.getClientRetryDelay();
                    LOGGER.warn("Attempt {} to get address for {} failed. Trying again in {} ms", attempt, name,
                            retryDelay, e);
                    try {
                        Thread.sleep(retryDelay);
                    } catch (final InterruptedException e1) {
                        LOGGER.error("Problem waiting retry delay of {} ms.", retryDelay, e1);
                    }
                } // if this isn't the last attempt
            } // unknown host exception
        } // foreach attempt

        throw new RuntimeException(
                "Failed to lookup " + id + " after " + CONTAINER_ADDRESS_LOOKUP_MAX_ATTEMPTS + " attempts");
    }

    /**
     * Parse the region subnet information from the specified file.
     * 
     * @param path
     *            where to read the data from
     * @return the map or null on error (an error will be logged)
     * @throws IOException
     *             if there is an error reading the file
     */
    private static ImmutableMap<SubnetUtils.SubnetInfo, RegionIdentifier> parseRegionSubnetInfo(
            @Nonnull final Path path) throws IOException {
        if (!Files.exists(path)) {
            LOGGER.error("{} does not exist", path);
            return null;
        }

        final ImmutableMap.Builder<SubnetUtils.SubnetInfo, RegionIdentifier> info = ImmutableMap.builder();
        try (Stream<String> stream = Files.lines(path)) {
            stream.filter(line -> !(null == line || line.trim().isEmpty())).forEach(line -> {
                final String[] tokens = line.split("\\s+");
                if (tokens.length != 2) {
                    throw new RuntimeException("Invalid region subnet line (expecting 2 tokens): '" + line + "'");
                }
                final RegionIdentifier region = new StringRegionIdentifier(tokens[0]);
                final SubnetUtils.SubnetInfo subnet = new SubnetUtils(tokens[1]).getInfo();

                info.put(subnet, region);
            });
        }

        try {
            final ImmutableMap<SubnetUtils.SubnetInfo, RegionIdentifier> infoMap = info.build();

            if (infoMap.isEmpty()) {
                LOGGER.error("No region information found in {}", path);
                return null;
            }

            return infoMap;
        } catch (final IllegalArgumentException e) {
            LOGGER.error(
                    "Building the subnet region map failed, it is likely that the file is invalid and contains multiple regions for the same subnet: {}",
                    e.getMessage(), e);
            return null;
        }
    }

    /**
     * @param args
     *            see the help output
     * @throws InterruptedException
     *             when the wait forever lock is interrupted
     */
    public static void main(final String[] args) throws InterruptedException {
        LogExceptionHandler.registerExceptionHandler();

        DnsUtils.configureDnsCache();

        final Options options = new Options();
        options.addOption(null, CONFIGURATION_DIRECTORY_OPT, true,
                "The directory to read the configuration from (default: " + DnsServer.DEFAULT_CONFIGURATION_DIRECTORY
                        + ")");
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

            outputVersionInformation();

            final Path configurationDirectory;
            if (cmd.hasOption(CONFIGURATION_DIRECTORY_OPT)) {
                final String configDirectoryStr = cmd.getOptionValue(CONFIGURATION_DIRECTORY_OPT);
                configurationDirectory = Paths.get(configDirectoryStr);
            } else {
                configurationDirectory = Paths.get(DnsServer.DEFAULT_CONFIGURATION_DIRECTORY);
            }

            final Parameters parameters = parseConfigurationDirectory(configurationDirectory);
            if (null == parameters) {
                System.exit(1);
            }

            final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> serviceConfigurations = AppMgrUtils
                    .loadApplicationManager(parameters.serviceConfigurationFile, parameters.serviceDependencyFile);

            final Map<String, Object> extraData = parseExtraData(parameters.configurationFile);

            final ImmutableMap<NodeIdentifier, InetAddress> containerNames = parseContainerNames(
                    parameters.containerNamesFile);
            if (null == containerNames) {
                System.exit(1);
            }

            final ImmutableMap<SubnetUtils.SubnetInfo, RegionIdentifier> subnetToRegion = parseRegionSubnetInfo(
                    parameters.regionSubnetFile);
            if (null == subnetToRegion) {
                // error was logged inside parseRegionSubnetInfo
                System.exit(1);
            }

            final ImmutableMap<RegionIdentifier, NodeIdentifier> dcopLeaders = parseDcopLeaders(
                    parameters.dcopLeadersFile);
            if (null == dcopLeaders) {
                System.exit(1);
            }

            final Pair<ImmutableMap<String, Double>, ImmutableMap<String, Double>> ipSpeedResult = readIpSpeedFile(
                    configurationDirectory);
            final ImmutableMap<String, Double> ipToSpeed = ipSpeedResult.getLeft();
            LOGGER.debug("Found IP to speed: {}", ipToSpeed);
            final ImmutableMap<String, Double> ipToDelay = ipSpeedResult.getRight();
            LOGGER.debug("Found IP to delay: {}", ipToDelay);

            final NodeIdentifier id = IdentifierUtils.getNodeIdentifier(parameters.hostname);

            final ImmutableMap<String, HardwareConfiguration> hardwareConfigs = HardwareConfiguration
                    .parseHardwareConfigurations(
                            configurationDirectory.resolve(HardwareConfiguration.HARDWARE_CONFIG_FILENAME));
            final HardwareConfiguration hardwareConfig = hardwareConfigs.get(parameters.hardwareConfigName);
            Objects.requireNonNull(hardwareConfig,
                    String.format("%s uses hardware config %s that is not know to the scenario", parameters.hostname,
                            parameters.hardwareConfigName));

            final ImmutableCollection<SubnetUtils.SubnetInfo> testbedControlSubnets = loadExcludedSubnets(
                    parameters.testbedControlSubnetsFile);

            final HiFiAgent agent = new HiFiAgent(id, extraData, TTL, serviceConfigurations, containerNames,
                    new FileRegionLookupService(subnetToRegion), ipToSpeed, ipToDelay, hardwareConfig,
                    testbedControlSubnets, dcopLeaders, parameters);
            outputVersionInformation();
            agent.start();

            // run until shutdown
            try {
                ConcurrencyUtils.waitForever();
            } catch (final InterruptedException e) {
                LOGGER.info("Interrupted waiting for the process to die, probably time to exit", e);
            }
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
        new HelpFormatter().printHelp("HiFiAgent", options);
    }

    /**
     * Option to specify the configuration directory.
     */
    public static final String CONFIGURATION_DIRECTORY_OPT = "configuration-directory";
    /**
     * Option to ask for help.
     */
    public static final String HELP_OPT = "help";
    /**
     * Option to ask for version.
     */
    public static final String VERSION_OPT = "version";

    /**
     * DNS TTL in seconds.
     */
    public static final int TTL = SimulationRunner.TTL;

    private final NodeIdentifier name;
    private final Controller controller;
    private final NodeLookupService lookupService;
    private final VirtualClock clock;
    private final MapNetworkFactory networkFactory;
    private final SimpleDockerResourceManagerFactory managerFactory;
    private final TA2Impl ta2;
    private final NodeLookupService dcopLookup;

    /**
     * Construct an agent.
     * 
     * @param hostname
     *            the name of the agent
     * @param extraData
     *            the configuration data about DCOP, RLG, DNS, ...
     * @param dnsTtlSeconds
     *            the ttl to use for DNS entries
     * @param serviceConfigurations
     *            the service configuration information
     * @param containerNames
     *            the file that contains the list of containers to use
     * @param regionLookupService
     *            how to convert nodes to regions
     * @param ipToSpeed
     *            mapping of IP address to speed, passed to
     *            {@link SimpleDockerResourceManager}
     * @param ipToDelay
     *            mapping of IP address to delay, passed to
     *            {@link SimpleDockerResourceManager}
     * @param hardwareConfig
     *            the simulated hardware for this agent that can limit the
     *            capacity of the real hardware, passed to
     *            {@link SimpleDockerResourceManager}
     * @param testbedControlSubnets
     *            passed to {@link SimpleDockerResourceManagerFactory}
     * @param parameters
     *            parameters passed through from the commandline
     * @param dcopLeaders
     *            {@link #getDcopForRegion(RegionIdentifier)}
     */
    public HiFiAgent(@Nonnull final NodeIdentifier hostname,
            @Nonnull final Map<String, Object> extraData,
            final int dnsTtlSeconds,
            @Nonnull final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> serviceConfigurations,
            @Nonnull final ImmutableMap<NodeIdentifier, InetAddress> containerNames,
            @Nonnull final FileRegionLookupService regionLookupService,
            @Nonnull final ImmutableMap<String, Double> ipToSpeed,
            @Nonnull final ImmutableMap<String, Double> ipToDelay,
            @Nonnull final HardwareConfiguration hardwareConfig,
            @Nonnull final ImmutableCollection<SubnetInfo> testbedControlSubnets,
            @Nonnull final ImmutableMap<RegionIdentifier, NodeIdentifier> dcopLeaders,
            @Nonnull final Parameters parameters) {
        this.name = hostname;
        this.dcopLeaders = dcopLeaders;
        if (AgentConfiguration.getInstance().getApUsesControlNetwork()) {
            this.lookupService = new ControlNetworkLookupService(parameters.apPort);
            this.dcopLookup = new ControlNetworkLookupService(parameters.dcopPort);
        } else {
            this.lookupService = new DnsNodeLookupService(parameters.apPort);
            this.dcopLookup = new DnsNodeLookupService(parameters.dcopPort);
        }

        this.clock = new AbsoluteClock();

        final SimpleDockerResourceManager.Parameters rmParams = new SimpleDockerResourceManager.Parameters();
        rmParams.clock = clock;
        rmParams.pollingInterval = AgentConfiguration.getInstance().getResourceReportInterval().toMillis();
        rmParams.containerNames = containerNames;
        rmParams.dockerRegistryHostname = Objects.requireNonNull(parameters.dockerRegistryHostname);
        rmParams.regionLookupService = regionLookupService;
        rmParams.apPort = parameters.apPort;
        rmParams.ipToSpeed = ipToSpeed;
        rmParams.ipToDelay = ipToDelay;
        rmParams.hardwareConfig = hardwareConfig;
        rmParams.testbedControlSubnets = testbedControlSubnets;
        rmParams.imageFetcherClassname = Objects.requireNonNull(parameters.imageFetcherClassname);
        rmParams.serviceConfigurationFile = parameters.serviceConfigurationFile;
        rmParams.serviceDependencyFile = parameters.serviceDependencyFile;

        this.managerFactory = new SimpleDockerResourceManagerFactory(rmParams);

        final DnsResolutionType dnsResolutionType = AgentConfiguration.getInstance().getDnsResolutionType();
        planTranslator = PlanTranslator.constructPlanTranslator(dnsResolutionType, dnsTtlSeconds);

        networkFactory = new MapNetworkFactory(lookupService, dcopLookup, regionLookupService, managerFactory,
                AgentConfiguration.getInstance().getApProgram(),
                AgentConfiguration.getInstance().isApProgramAnonymous(), this, true, true, true,
                IdentifierUtils::getNodeIdentifier);

        this.controller = networkFactory.createServer(name, extraData);
        this.controller.setDumpInterval(Objects.requireNonNull(parameters.dumpInterval));
        this.controller.setBaseOutputDirectory(parameters.dumpDirectory);
        this.controller.setDumpState(parameters.dumpEnabled);

        LOGGER.info("Dump interval {} output directory {} dump state enabled {}", parameters.dumpInterval,
                parameters.dumpDirectory, parameters.dumpEnabled);

        LOGGER.info("This node has hostname '{}'", hostname);

        if (this.controller.isHandleDnsChanges()) {
            // NOTE: this assumes that the node handling DNS changes is the DNS
            // server
            switch (dnsResolutionType) {
            case RECURSIVE:
                this.dnsUpdateService = new WeightedDnsUpdateServiceRecursive(this.controller.getRegionIdentifier(),
                        InetAddress.getLoopbackAddress());
                break;
            case NON_RECURSIVE:
                this.dnsUpdateService = new WeightedDnsUpdateServiceNonRecursive(this.controller.getRegionIdentifier(),
                        InetAddress.getLoopbackAddress());
                break;
            case RECURSIVE_TWO_LAYER:
                this.dnsUpdateService = new WeightedDnsUpdateServiceRecursive2Layer(
                        this.controller.getRegionIdentifier(), InetAddress.getLoopbackAddress());
                break;
            default:
                throw new RuntimeException("Unexpected DNS resolution type: " + dnsResolutionType);
            }

        } else {
            this.dnsUpdateService = null;
        }

        addNeighbors(Objects.requireNonNull(parameters.neighborsFile));

        final NodeIdentifier dockerRegistryId = IdentifierUtils
                .getNodeIdentifier(Objects.requireNonNull(parameters.dockerRegistryHostname));
        if (hostname.equals(dockerRegistryId)) {
            LOGGER.info("The Docker registry should be running on this node.");
            // startDockerRegistry();
        }

        if (!AgentConfiguration.getInstance().isUseLeaderElection()) {
            final NodeIdentifier globalLeaderId = IdentifierUtils
                    .getNodeIdentifier(Objects.requireNonNull(parameters.globalLeader));
            if (hostname.equals(globalLeaderId)) {
                LOGGER.info("This node is the global leader.");
                this.controller.setGlobalLeader(true);
            }
        }

        ta2 = new TA2Impl(regionLookupService);

        initialDnsConfiguration();

        startInitialServices(serviceConfigurations);
    }

    /**
     * Setup the initial DNS configuration so that services are properly
     * delegated to their default region.
     * 
     * If this node is not handling dns changes, then this method is a nop.
     */
    private void initialDnsConfiguration() {
        if (this.controller.isHandleDnsChanges()) {
            final RegionIdentifier region = this.controller.getRegionIdentifier();

            final LoadBalancerPlan newLoadBalancerPlan = LoadBalancerPlan.getNullLoadBalancerPlan(region);
            final RegionServiceState newRegionServiceState = new RegionServiceState(region, ImmutableSet.of());

            final ImmutableCollection<Pair<DnsRecord, Double>> newDnsEntries = getPlanTranslator()
                    .convertToDns(newLoadBalancerPlan, newRegionServiceState);

            LOGGER.info("Initial DNS configuration for region {}: {}", region, newDnsEntries);

            getDnsUpdateService(region).replaceAllRecords(newDnsEntries);

            LOGGER.info("Finished applying initial DNS configuration");
        }
    }

    /**
     * Start the initial services for this node.
     */
    private void startInitialServices(
            @Nonnull final ImmutableMap<ApplicationCoordinates, ServiceConfiguration> serviceConfigurations) {
        LOGGER.info("Starting initial services");

        serviceConfigurations.forEach((service, config) -> {
            final Map.Entry<NodeIdentifier, Integer> serviceEntry = config.getDefaultNodes().entrySet().stream().filter(
                    e -> IdentifierUtils.getCanonicalIdentifier(e.getKey()).equals(this.controller.getNodeIdentifier()))
                    .findFirst().orElse(null);
            if (null != serviceEntry) {
                final ContainerParameters parameters = AppMgrUtils.getContainerParameters(service);

                LOGGER.info("Starting service {} on {} params: {}", service, this.controller.getNodeIdentifier(),
                        parameters);

                final ResourceManager<?> mgr = controller.getResourceManager();
                for (int i = 0; i < serviceEntry.getValue(); ++i) {

                    final NodeIdentifier id = mgr.startService(service, parameters);
                    if (null == id) {
                        LOGGER.warn("Unable to start initial service {} instance number {} on {}", config.getService(),
                                (i + 1), serviceEntry.getKey());
                    } else {
                        LOGGER.info("Started service {} instance {} on {}", service, (i + 1), id);
                    }
                }
            } else {
                LOGGER.debug("Service {} is not defaulting to node {} default is {}", service,
                        this.controller.getNodeIdentifier(), config.getDefaultNodes());
            }
        });
    }

    private void addNeighbors(@Nonnull final Path neighborFile) {
        if (!Files.exists(neighborFile)) {
            LOGGER.warn("Neighbors file '{}' does not exist, not adding any neighbors", neighborFile);
            return;
        }

        try {
            Files.lines(neighborFile).forEach(line -> {
                final int index = line.indexOf("\t");
                if (index < 0) {
                    throw new RuntimeException("Improperly formatted neighbors line. Cannot find tab: '" + line + "'");
                } else {
                    final String name = line.substring(0, index);
                    final NodeIdentifier id = IdentifierUtils.getNodeIdentifier(name);

                    final String bandwidthStr = line.substring(index + 1);
                    final double bandwidth = Double.parseDouble(bandwidthStr);
                    this.controller.addApNeighbor(id, bandwidth);
                }
            });
        } catch (final IOException e) {
            throw new RuntimeException("Error reading the neighbors file", e);
        }

        LOGGER.debug("Neighbors: {}", this.controller.getNeighbors());
    }

    private static Map<String, Object> parseExtraData(@Nonnull final Path path) {
        try {
            if (Files.exists(path)) {
                try (InputStream stream = Files.newInputStream(path)) {
                    @SuppressWarnings("unchecked")
                    final JsonObject<String, Object> obj = (JsonObject<String, Object>) JsonReader.jsonToJava(stream,
                            Collections.singletonMap(JsonReader.USE_MAPS, true));
                    return obj;
                }
            } else {
                LOGGER.warn("File {} doesn't exist, not parsing any configuration data for node", path);
                return Collections.emptyMap();
            }
        } catch (final IOException e) {
            LOGGER.error("Error parsing " + path + ", no configuration data for node will be parsed", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Start the agent.
     */
    public void start() {
        LOGGER.info("Top of start");

        LOGGER.info("Starting simulation driver listener");
        final Thread simThread = new Thread(() -> SimServer.runServer(ta2, controller), "Simulation Driver Listener");
        simThread.setDaemon(true);
        simThread.start();

        LOGGER.info("Starting controller");
        controller.startExecuting();
        LOGGER.info("after controller start executing");
        clock.startClock();

        LOGGER.info("Agent is up and running on node {}", controller.getNodeIdentifier());
    }

    /**
     * Stop the agent.
     */
    public void stop() {
        controller.stopExecuting();
        clock.stopClock();
    }

    // NetworkServices interface
    private final PlanTranslator planTranslator;

    @Override
    @Nonnull
    public PlanTranslator getPlanTranslator() {
        return planTranslator;
    }

    @Override
    @Nonnull
    public DNSUpdateService getDnsUpdateService(@Nonnull final RegionIdentifier region) {
        if (!region.equals(this.controller.getRegionIdentifier())) {
            throw new IllegalArgumentException(
                    "Can only get the DNS update service for the region that the controller is in");
        }

        return dnsUpdateService;
    }

    private final DNSUpdateService dnsUpdateService;

    @Override
    @Nonnull
    public TA2Interface getTA2Interface() {
        return ta2;
    }

    // end NetworkServices interface

    /**
     * Get the git version information for the build.
     * 
     * @return the version or a string stating what the problem was
     */
    public static String getGitVersionInformation() {
        final URL url = HiFiAgent.class.getResource("git.properties");
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

    private static Pair<ImmutableMap<String, Double>, ImmutableMap<String, Double>> readIpSpeedFile(
            @Nonnull final Path configurationDirectory) {
        final ImmutableMap.Builder<String, Double> ipToSpeed = ImmutableMap.builder();
        final ImmutableMap.Builder<String, Double> ipToDelay = ImmutableMap.builder();

        // filename matches 12-setup-ncps.yml
        final Path ipToSpeedFile = configurationDirectory.resolve("ip-to-speed.txt");

        if (Files.exists(ipToSpeedFile)) {
            try (BufferedReader reader = Files.newBufferedReader(ipToSpeedFile)) {
                try (LineIterator it = new LineIterator(reader)) {
                    while (it.hasNext()) {
                        final String line = it.next();
                        final String[] tokens = line.split("\\s+");
                        if (tokens.length != 3) {
                            LOGGER.warn("Invalid line parsing {}. Expecting 3 tokens, but got {} in '{}'. Skipping.",
                                    ipToSpeedFile, tokens.length, line);
                        } else {
                            final String ip = tokens[0];
                            final String speedStr = tokens[1];
                            final String delayStr = tokens[2];
                            try {
                                final double speed = Double.valueOf(speedStr);
                                ipToSpeed.put(ip, speed);
                            } catch (final NumberFormatException e) {
                                LOGGER.warn(
                                        "Invalid line parsing {}. Cannot parse speed '{}' as a double in '{}'. Skipping.",
                                        ipToSpeedFile, speedStr, line, e);
                            }
                            try {
                                final double delay = Double.valueOf(delayStr);
                                ipToDelay.put(ip, delay);
                            } catch (final NumberFormatException e) {
                                LOGGER.warn(
                                        "Invalid line parsing {}. Cannot parse delay '{}' as a double in '{}'. Skipping.",
                                        ipToSpeedFile, delayStr, line, e);
                            }
                        }
                    }
                } // LineIterator allocation
            } catch (final IOException e) {
                LOGGER.warn("Error reading {}. Skipping", ipToSpeedFile, e);
            }
        }

        return Pair.of(ipToSpeed.build(), ipToDelay.build());
    }

    private static ImmutableCollection<SubnetUtils.SubnetInfo> loadExcludedSubnets(final Path excludedSubnetsFile) {
        if (!Files.exists(excludedSubnetsFile)) {
            LOGGER.warn("Excluded subnets file '{}' does not exist, not excluding any subnets", excludedSubnetsFile);
            return ImmutableList.of();
        }

        try {
            final ImmutableCollection<SubnetUtils.SubnetInfo> excludedSubnets = Files.lines(excludedSubnetsFile)
                    .map(line -> {
                        try {
                            return new SubnetUtils(line).getInfo();
                        } catch (final IllegalArgumentException e) {
                            LOGGER.error("Unable to parse excluded subnet from '{}', skipping", line);
                            return null;
                        }
                    }).filter(x -> null != x).collect(ImmutableList.toImmutableList());

            return excludedSubnets;
        } catch (final IOException e) {
            throw new RuntimeException("Error reading the excluded subnets file", e);
        }
    }

    /**
     * Parse the dcop leaders information from the specified file.
     * 
     * @param path
     *            where to read the data from
     * @return the map or null on error (an error will be logged)
     * @throws IOException
     *             if there is an error reading the file
     */
    private static ImmutableMap<RegionIdentifier, NodeIdentifier> parseDcopLeaders(@Nonnull final Path path)
            throws IOException {
        if (!Files.exists(path)) {
            LOGGER.error("{} does not exist", path);
            return null;
        }

        final ImmutableMap.Builder<RegionIdentifier, NodeIdentifier> info = ImmutableMap.builder();
        try (Stream<String> stream = Files.lines(path)) {
            stream.filter(line -> !(null == line || line.trim().isEmpty())).forEach(line -> {
                final String[] tokens = line.split("\\s+");
                if (tokens.length != 2) {
                    throw new RuntimeException("Invalid DCOP leaders line (expecting 2 tokens): '" + line + "'");
                }
                final RegionIdentifier region = new StringRegionIdentifier(tokens[0]);
                final NodeIdentifier leader = new DnsNameIdentifier(tokens[1]);

                info.put(region, leader);
            });
        }

        try {
            final ImmutableMap<RegionIdentifier, NodeIdentifier> infoMap = info.build();

            if (infoMap.isEmpty()) {
                LOGGER.error("No DCOP leader information found in {}", path);
                return null;
            }

            return infoMap;
        } catch (final IllegalArgumentException e) {
            LOGGER.error(
                    "Building the DCOP leader map failed, it is likely that the file is invalid and contains multiple leaders for the same region: {}",
                    e.getMessage(), e);
            return null;
        }
    }

    private final ImmutableMap<RegionIdentifier, NodeIdentifier> dcopLeaders;

    /**
     * Second octet of MAP control network. Network is a slash 16.
     */
    public static final int MAP_CONTROL_SECOND_OCTET = 20;

    // the MAP control network is 172.20.0.0/16
    /**
     * First octet of MAP control network. Network is a slash 16.
     */
    public static final int MAP_CONTROL_FIRST_OCTET = 172;

    @Override
    @Nonnull
    public NodeIdentifier getDcopForRegion(@Nonnull RegionIdentifier region) {
        if (!dcopLeaders.containsKey(region)) {
            throw new IllegalArgumentException("No DCOP leader for " + region);
        } else {
            return dcopLeaders.get(region);
        }
    }

    @Override
    @Nonnull
    public MapOracle getMapOracle() {
        return this;
    }

}
