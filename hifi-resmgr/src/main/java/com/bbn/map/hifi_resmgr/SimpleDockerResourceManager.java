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
package com.bbn.map.hifi_resmgr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.io.IOUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.hifi.FileRegionLookupService;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.hifi.util.SimAppUtils;
import com.bbn.map.simulator.HardwareConfiguration;
import com.bbn.map.simulator.NetworkDemandTracker;
import com.bbn.map.simulator.Simulation;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.InterfaceIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceState;
import com.bbn.protelis.utils.ImmutableUtils;
import com.bbn.protelis.utils.VirtualClock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Class to start, stop, and monitor Docker containers on a node in MAP.
 * 
 * @author awald
 * @author jschewe
 *
 */
public class SimpleDockerResourceManager implements ResourceManager<Controller> {
    private static final Logger LOGGER = LogManager.getLogger(SimpleDockerResourceManager.class);

    private static final String DOCKER_REST_API_BASE_URL_VERSION_SUFFIX = "/v1.32";
    private static final String DOCKER_REST_API_BASE_URL = "http://localhost:5010"
            + DOCKER_REST_API_BASE_URL_VERSION_SUFFIX;

    private static final int DOCKER_STOP_TIMEOUT_SECONDS = 120;

    // Hard coded service container defaults
    /* package */ static final String DEFAULT_DOCKER_IMAGE_TAG = "latest";
    private static final double DEFAULT_DOCKER_CONTAINER_CPUS = 1.0F;
    private static final double DEFAULT_DOCKER_CONTAINER_MEMORY_GB = 4;
    private static final String DEFAULT_DOCKER_SHARED_NETWORK_NAME = "shared_nw";
    private static final String DEFAULT_DOCKER_CONTAINER_NIC = "eth0";

    // Host bind mount source paths
    private static final String HOST_CONTAINER_SHARED_BASE_FOLDER_PATH = "/var/lib/map/agent/container_data";
    private static final String HOST_SERVICE_SHARED_BASE_FOLDER_PATH = "/var/lib/map/agent/service_data";
    // Container bind mount target paths
    private static final String CONTAINER_DATA_TARGET_LOCATION = "/" + SimAppUtils.HOST_CONTAINER_DATA_LOCATION;
    private static final String CONTAINER_SERVICE_DATA_LOCATION = "/service_data";

    // Unit conversions
    private static final double KB_TO_BYTES = 1024.0;
    private static final int CPUS_TO_NANOCPUS = 1_000_000_000;

    // Docker response codes
    private static final int DOCKER_RESPONSE_CODE_CREATE_IMAGE_NO_ERROR = 200;
    private static final int DOCKER_RESPONSE_CODE_REMOVE_IMAGE_NO_ERROR = 200;
    private static final int DOCKER_RESPONSE_CODE_CREATED_CONTAINER_SUCCESSFULLY = 201;
    private static final int DOCKER_RESPONSE_CODE_START_CONTAINER_NO_ERROR = 204;
    private static final int DOCKER_RESPONSE_CODE_STOP_CONTAINER_NO_ERROR = 204;
    private static final int DOCKER_RESPONSE_CODE_REMOVE_CONTAINER_NO_ERROR = 204;
    private static final int DOCKER_RESPONSE_CODE_CREATE_EXEC_INSTANCE_NO_ERROR = 201;
    private static final int DOCKER_RESPONSE_CODE_GET_LOGS_NO_ERROR = 200;

    private static final int DOCKER_START_CONTAINER_ATTEMPTS = 3;
    private static final int DOCKER_START_CONTAINER_MAX_RETRY_DELAY = 100; // milliseconds
    private static final int DOCKER_START_CONTAINER_MIN_RETRY_DELAY = 20; // milliseconds
    private static final int DOCKER_RESPONSE_CODE_LIST_IMAGES = 200;

    private static final String HTTP_GET = "GET";
    private static final String HTTP_POST = "POST";
    private static final String HTTP_DELETE = "DELETE";

    // resource monitoring
    private ContainerResourceMonitor containerResourceMonitor = new ContainerResourceMonitor(DOCKER_REST_API_BASE_URL);

    // null until init() is called
    private NCPResourceMonitor ncpResourceMonitor;

    private final boolean useFailedRequestsInDemand;

    private Thread updateResourceReportsThread = null;
    private final Object resourceReportLock = new Object();
    private ResourceReport shortResourceReport;
    private ResourceReport longResourceReport;
    private final long pollingInterval;

    /**
     * 
     * @return the interval to check for new data at
     */
    public long getPollingInterval() {
        return pollingInterval;
    }

    private final Map<NodeIdentifier, MapContainer> runningContainers = new ConcurrentHashMap<>();
    private Controller node;
    private final VirtualClock clock;
    private final ImmutableMap<NodeIdentifier, InetAddress> containerNames;

    private String dockerRegistryHostname;
    /**
     * Port that the docker registry runs on.
     */
    public static final int DOCKER_REGISTRY_PORT = 5000;

    private final NetworkDemandTracker networkDemandTracker;

    private final FileRegionLookupService regionLookupService;

    private final int apPort;

    private final ImmutableMap<String, Double> ipToSpeed;
    private final ImmutableMap<String, Double> ipToDelay;

    private final HardwareConfiguration hardwareConfig;

    private final ImmutableCollection<SubnetUtils.SubnetInfo> testbedControlSubnets;

    private final DockerImageManager imageManager;

    private final Path serviceConfigurationFile;
    private final Path serviceDependencyFile;

    /**
     * @return the port that AP communicates on
     */
    public int getApPort() {
        return apPort;
    }

    /**
     * 
     * @param nic
     *            the network interface to find neighbors on
     * @return the neighbors attached to the specified network interface
     * @see #getNeighborsAttachedToNic(String)
     * @see NetworkInterface#getName()
     */
    /* package */ static Collection<NodeIdentifier> getNeighborsAttachedToNic(
            final Map<NodeIdentifier, String> neighborToNICMap,
            final NetworkInterface nic) {
        return getNeighborsAttachedToNic(neighborToNICMap, nic.getName());
    }

    /**
     * 
     * @param nicName
     *            the name of the network interface to find neighbors on
     * @return the neighbors attached to the specified network interface
     */
    private static Collection<NodeIdentifier> getNeighborsAttachedToNic(
            final Map<NodeIdentifier, String> neighborToNICMap,
            final String nicName) {
        // if there are a lot of neighbors this can get expensive. If this
        // becomes the case, then we should store the inverse of
        // neighborToNICMap
        return neighborToNICMap.entrySet().stream().filter(e -> nicName.equals(e.getValue())).map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static ImmutableMap<NodeIdentifier, InetAddress> limitContainersToMatchHardwareConfig(
            final ImmutableMap<NodeIdentifier, InetAddress> origContainerNames,
            final HardwareConfiguration hardwareConfig) {
        final int hardwareContainers = hardwareConfig.getMaximumServiceContainers();
        final int numContainers = Math.min(origContainerNames.size(), hardwareContainers);

        final ImmutableMap<NodeIdentifier, InetAddress> retval;
        if (numContainers < origContainerNames.size()) {
            retval = origContainerNames.entrySet().stream() //
                    .limit(numContainers) //
                    .collect(Collectors.collectingAndThen(//
                            Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue), //
                            ImmutableMap::copyOf));
        } else {
            // can't be larger, so must be just right
            retval = origContainerNames;
        }
        LOGGER.info("limiting containers orig: {} hw: {} return: {}", origContainerNames.size(), hardwareContainers,
                retval.size());
        return retval;
    }

    // CHECKSTYLE:OFF - data class
    /**
     * Parameters for SimpleDockerResourceManager.
     */
    public static final class Parameters {
        /**
         * keeps track of time for this resource manager.
         */
        public VirtualClock clock;
        /**
         * interval for polling resource usage and creating
         * {@link ResourceReport} objects.
         */
        public long pollingInterval;
        /**
         * the available names to use for new containers.
         */
        public ImmutableMap<NodeIdentifier, InetAddress> containerNames;
        /**
         * the host that is running the docker registry.
         */
        public String dockerRegistryHostname;
        /**
         * used to find the region of network traffic.
         */
        public FileRegionLookupService regionLookupService;
        /**
         * the port that AP communicates on.
         */
        public int apPort;
        /**
         * used to map IP addresses to speeds specified in the topology file for
         * handling simulated speeds.
         */
        public ImmutableMap<String, Double> ipToSpeed;
        /**
         * used to map IP addresses to delays specified in the topology file for
         * handling simulated link delays.
         */
        public ImmutableMap<String, Double> ipToDelay;
        /**
         * the hardware configuration that overrides what is found on the real
         * hardware for simulation testing.
         */
        public HardwareConfiguration hardwareConfig;
        /**
         * passed to
         * {@link NCPResourceMonitor#NCPResourceMonitor(long, RegionIdentifier, FileRegionLookupService, int, ImmutableCollection)}.
         */
        public ImmutableCollection<SubnetUtils.SubnetInfo> testbedControlSubnets;
        /**
         * passed to {@link DockerImageManager#DockerImageManager(String)}.
         */
        public String imageFetcherClassname;

        /**
         * Path to the service configurations file. This is copied into service
         * containers.
         */
        public Path serviceConfigurationFile;
        /**
         * Path to the service dependency file. This is copied into service
         * containers.
         */
        public Path serviceDependencyFile;

    }
    // CHECKSTYLE:ON

    /**
     * @param params
     *            all parameters
     */
    public SimpleDockerResourceManager(@Nonnull final Parameters params) {
        // cache value so that it isn't checked every time
        this.useFailedRequestsInDemand = AgentConfiguration.getInstance().getUseFailedRequestsInDemand();

        this.clock = Objects.requireNonNull(params.clock);
        this.pollingInterval = params.pollingInterval;
        this.dockerRegistryHostname = Objects.requireNonNull(params.dockerRegistryHostname);
        this.networkDemandTracker = new NetworkDemandTracker();
        this.regionLookupService = Objects.requireNonNull(params.regionLookupService);
        this.apPort = params.apPort;
        this.ipToSpeed = Objects.requireNonNull(params.ipToSpeed);
        this.ipToDelay = Objects.requireNonNull(params.ipToDelay);
        this.hardwareConfig = Objects.requireNonNull(params.hardwareConfig);
        this.testbedControlSubnets = Objects.requireNonNull(params.testbedControlSubnets);
        this.containerNames = limitContainersToMatchHardwareConfig(params.containerNames, params.hardwareConfig);
        this.imageManager = new DockerImageManager(Objects.requireNonNull(params.imageFetcherClassname));
        this.serviceConfigurationFile = Objects.requireNonNull(params.serviceConfigurationFile);
        this.serviceDependencyFile = Objects.requireNonNull(params.serviceDependencyFile);

        LOGGER.info("Using Docker registry at node '" + dockerRegistryHostname + "'.");

        LOGGER.info("--------------------------------- Adding ContainerShutdownHook ---------------------------------");
        Runtime.getRuntime().addShutdownHook(new ContainerShutdownHook());
    }

    @Override
    public void init(@Nonnull final Controller node, @Nonnull final Map<String, Object> ignored) {
        LOGGER.info("Initializing SimpleDockerResourceManager for NetworkServer '" + node + "' with polling interval "
                + pollingInterval + " ms.");

        this.node = node;

        this.shortResourceReport = ResourceReport.getNullReport(node.getNodeIdentifier(), EstimationWindow.SHORT);
        this.longResourceReport = ResourceReport.getNullReport(node.getNodeIdentifier(), EstimationWindow.LONG);

        ncpResourceMonitor = new NCPResourceMonitor(pollingInterval, node, regionLookupService, apPort,
                testbedControlSubnets, this);

        // start polling and updating resource reports
        start();
    }

    /**
     * Start generation of {@link ResourceReport} objects.
     * 
     * @throws IllegalStateException
     *             if the simulation is already started
     */
    private void start() {

        LOGGER.info("Starting SimpleDockerResourceManager monitoring and polling");

        if (null != updateResourceReportsThread) {
            throw new IllegalStateException("Cannot start when it is already running");
        }

        updateResourceReportsThread = new Thread(() -> runUpdateResourceReports());
        updateResourceReportsThread.setName("updateResourceReports");
        updateResourceReportsThread.start();

        runningContainers.forEach((id, sim) -> {
            sim.start();
        });
    }

    private void runUpdateResourceReports() {
        while (true) {
            try {
                final long start = System.currentTimeMillis();

                // update the resource reports
                updateResourceReports();

                final long executionTime = System.currentTimeMillis() - start;
                LOGGER.debug(
                        "ResourceReport update starting at time {} took {} ms to execute, and the polling interval is {} ms.",
                        start, executionTime, pollingInterval);

                final long remaining = pollingInterval - executionTime;
                if (remaining > 0) {
                    LOGGER.debug("sleeping for {} ms before next ResourceReport update", remaining);
                    Thread.sleep(remaining);
                }
            } catch (final InterruptedException e) {
                LOGGER.debug("ResourceReport generation thread got interrupted", e);
            }
        }
    }

    @Override
    @Nonnull
    public ResourceReport getCurrentResourceReport(@Nonnull final ResourceReport.EstimationWindow estimationWindow) {
        synchronized (resourceReportLock) {
            switch (estimationWindow) {
            case LONG:
                return longResourceReport;
            case SHORT:
                return shortResourceReport;
            default:
                throw new IllegalArgumentException("Unknown estimation window type: " + estimationWindow);
            }
        }
    }

    @Override
    @Nonnull
    public ServiceReport getServiceReport() {
        final long now = getClock().getCurrentTime();

        final ServiceReport report = new ServiceReport(node.getNodeIdentifier(), now, computeServiceState());
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("getServiceReport report: {} node: {} region: {}", report, getNode(),
                    getNode().getRegionIdentifier());
        }
        return report;
    }

    private ImmutableMap<NodeIdentifier, ServiceState> computeServiceState() {

        final ImmutableMap.Builder<NodeIdentifier, ServiceState> builder = ImmutableMap.builder();

        runningContainers.forEach((id, container) -> {
            final ServiceIdentifier<?> service = container.getService();
            if (null != service) {
                final ServiceState s = new ServiceState(service, container.getServiceStatus());
                builder.put(id, s);
            }
        });

        final ImmutableMap<NodeIdentifier, ServiceState> serviceState = builder.build();
        LOGGER.debug("Service state: " + serviceState);
        return serviceState;
    }

    private String getImageForService(final ServiceIdentifier<?> service) {
        final ApplicationSpecification appSpec = AppMgrUtils.getApplicationManager()
                .getApplicationSpecification((ApplicationCoordinates) service);
        Objects.requireNonNull(appSpec, "Could not find application specification for: " + service);

        final String image = appSpec.getImageName();
        if (null == image || "null".equals(image) || image.isEmpty()) {
            throw new IllegalArgumentException("Could not find image name for service: " + service);
        }

        // determine the full image name of the image to pull
        final String dockerImage = dockerRegistryHostname + ":" + DOCKER_REGISTRY_PORT + image;

        return dockerImage;
    }

    @Override
    public NodeIdentifier startService(@Nonnull final ServiceIdentifier<?> service,
            @Nonnull final ContainerParameters parameters) {
        Objects.requireNonNull(service);
        Objects.requireNonNull(parameters);

        LOGGER.info("**** Start service: start service {} with compute capacity {} and network capacity {}", service,
                parameters.getComputeCapacity(), parameters.getNetworkCapacity());

        if (!waitForImage(service)) {
            LOGGER.warn("Image fetch failed for service {}", service);
            return null;
        }

        // get next available container name
        final Map.Entry<NodeIdentifier, InetAddress> availableContainer = getAvailableContainerName();

        if (availableContainer != null) {
            final NodeIdentifier containerId = availableContainer.getKey();
            final String containerName = containerId.getName();

            final InetAddress containerAddress = availableContainer.getValue();

            LOGGER.info("**** Start service: Obtained container name '{}' and IP '{}'.", containerName,
                    containerAddress);

            // get parameter values
            final Double cpus = parameters.getComputeCapacity().getOrDefault(NodeAttribute.CPU,
                    DEFAULT_DOCKER_CONTAINER_CPUS);
            final Long memory = gigaBytesToBytes(parameters.getComputeCapacity().getOrDefault(NodeAttribute.MEMORY,
                    DEFAULT_DOCKER_CONTAINER_MEMORY_GB));

            // create host paths for container paths to mount to
            // final String imageNameForMountPath =
            // dockerImage.replaceFirst("\\A.*(/|\\\\)", "");
            String serviceNameForMountPath;

            if (service instanceof ApplicationCoordinates) {
                ApplicationCoordinates serviceAppCoordinates = ((ApplicationCoordinates) service);
                serviceNameForMountPath = serviceAppCoordinates.getGroup() + "." + serviceAppCoordinates.getArtifact()
                        + "_" + serviceAppCoordinates.getVersion();
            } else {
                serviceNameForMountPath = service.toString();
            }

            final String hostServiceMountBasePath = HOST_SERVICE_SHARED_BASE_FOLDER_PATH.replaceAll("\\|/",
                    File.separator);

            final Path hostServiceDataFolder = Paths.get(hostServiceMountBasePath).resolve(serviceNameForMountPath);

            final Path hostMountBaseFolder = Paths
                    .get(HOST_CONTAINER_SHARED_BASE_FOLDER_PATH.replaceAll("\\|/", File.separator));

            final Path hostMountServiceFolder = hostMountBaseFolder.resolve(serviceNameForMountPath);

            final Path hostMountContainerNameFolder = hostMountServiceFolder.resolve(containerName);

            final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmssSSSS");
            final Path hostMountTimeFolder = hostMountContainerNameFolder.resolve(dateFormat.format(new Date()));

            final Path hostMountContainerAppMetricsFolder = hostMountTimeFolder.resolve(SimAppUtils.APP_METRICS_FOLDER);

            final Path hostMountContainerInstanceDataFolder = hostMountTimeFolder
                    .resolve(SimAppUtils.HOST_CONTAINER_DATA_LOCATION);

            // add mount mappings

            // mount mappings: host source path -> container target path
            final Map<String, String> mountMappings = new HashMap<>();

            try {
                Files.createDirectories(hostServiceDataFolder);
            } catch (final IOException e) {
                LOGGER.error("Unable to create mounted host directory for service '{}': {}", service,
                        hostServiceDataFolder, e);
                return null;
            }
            mountMappings.put(hostServiceDataFolder.toAbsolutePath().toString(), CONTAINER_SERVICE_DATA_LOCATION);

            LOGGER.debug("Adding mounted host directory for service '{}' to container '{}': {}", service, containerId,
                    hostServiceDataFolder);

            try {
                Files.createDirectories(hostMountContainerAppMetricsFolder);
            } catch (final IOException e) {
                LOGGER.error("Unable to create mounted host directory for service '{}': {}", service,
                        hostMountContainerAppMetricsFolder, e);
                return null;
            }
            mountMappings.put(hostMountContainerAppMetricsFolder.toAbsolutePath().toString(),
                    SimAppUtils.CONTAINER_APP_METRICS_PATH.toAbsolutePath().toString());
            LOGGER.debug("Adding mounted host directory for service '{}' to container '{}': {}", service, containerId,
                    hostMountContainerAppMetricsFolder);

            try {
                Files.createDirectories(hostMountContainerInstanceDataFolder);
            } catch (final IOException e) {
                LOGGER.error("Unable to create mounted host directory for service '{}': {}", service,
                        hostMountContainerInstanceDataFolder, e);
                return null;
            }
            mountMappings.put(hostMountContainerInstanceDataFolder.toAbsolutePath().toString(),
                    CONTAINER_DATA_TARGET_LOCATION);
            LOGGER.debug("Adding mounted host directory for service '{}' to container '{}': {}", service, containerId,
                    hostMountContainerInstanceDataFolder);

            writeContainerInstanceData(hostMountContainerInstanceDataFolder, service);

            writeContainerMetadata(hostMountTimeFolder, service, parameters);

            // attempt to run Docker container
            final String dockerImage = getImageForService(service);
            final boolean runResult = runDockerContainer(containerName, dockerImage, cpus, memory,
                    DEFAULT_DOCKER_SHARED_NETWORK_NAME, containerAddress.getHostAddress(), mountMappings);

            // keep track of how many CPUs we've allocated
            allocatedCpus.put(containerId, cpus);

            LOGGER.info("Attempting to run container with name " + containerName + " success result: " + runResult);

            // return null if there was an issue running the container
            if (!runResult) {
                return null;
            }

            // starts monitoring of the container
            LOGGER.info("Started container resource monitor for {}", containerName);

            // container isn't running, add to running
            final ImmutableMap<LinkAttribute, Double> genericNetworkCapacity = parameters.getNetworkCapacity();

            final String nicName = getContainerVirtualNicName(containerName);
            if (null == nicName) {
                LOGGER.error("Container likely didn't start if we can't get the virtual NIC name");
                stopDockerContainer(containerName);
                return null;
            }
            LOGGER.info("Found virtual interface {} for container {}", nicName, containerName);

            final NetworkInterface containerInterface;
            try {
                containerInterface = NetworkInterface.getByName(nicName);
                if (null == containerInterface) {
                    LOGGER.error("Unable to get network interface object for container {} interface {}", containerName,
                            nicName);
                    stopDockerContainer(containerName);
                    return null;
                }
            } catch (final SocketException se) {
                LOGGER.error("Error finding network interface object for container {} interface {}", containerName,
                        nicName, se);
                stopDockerContainer(containerName);
                return null;
            }

            final MapContainer container = new MapContainer(this, service, containerId, nicName, genericNetworkCapacity,
                    ImmutableMap.copyOf(mountMappings), containerInterface, hostMountTimeFolder,
                    hostMountContainerAppMetricsFolder);

            runningContainers.put(containerId, container);

            if (updateResourceReportsThread != null) {
                container.start();
            }

            // starts monitoring of the container
            containerResourceMonitor.startMonitorForContainer(containerId, DEFAULT_DOCKER_CONTAINER_NIC);

            LOGGER.info("Started container for service '{}' with id '{}'.", service, containerId);

            return containerId;
        } else {
            LOGGER.error("Failed to start container because there are no more available container IDs.");
            return null;
        }
    }

    private void writeContainerInstanceData(final Path hostMountContainerInstanceDataFolder,
            final ServiceIdentifier<?> service) {
        final ObjectMapper jsonWriter = JsonUtils.getStandardMapObjectMapper();

        try (Writer writer = Files
                .newBufferedWriter(hostMountContainerInstanceDataFolder.resolve(SimAppUtils.SERVICE_FILENAME))) {
            jsonWriter.writeValue(writer, service);
        } catch (final IOException e) {
            LOGGER.error("Error writing container service information", e);
        }

        if (Files.exists(this.serviceConfigurationFile)) {
            try {
                Files.copy(this.serviceConfigurationFile,
                        hostMountContainerInstanceDataFolder.resolve(Simulation.SERVICE_CONFIGURATIONS_FILENAME));
            } catch (final IOException e) {
                LOGGER.error("Error writing service configurations file", e);
            }
        } else {
            LOGGER.warn("Service configuration file {} does not exist, not copying to container",
                    this.serviceConfigurationFile);
        }

        if (Files.exists(this.serviceDependencyFile)) {
            try {
                Files.copy(this.serviceDependencyFile,
                        hostMountContainerInstanceDataFolder.resolve(Simulation.SERVICE_DEPENDENCIES_FILENAME));
            } catch (final IOException e) {
                LOGGER.error("Error writing service dependencies file", e);
            }
        } else {
            LOGGER.warn("Service dependency file {} does not exist, not copying to container",
                    this.serviceDependencyFile);
        }

    }

    private static final int VIRTUAL_INTERFACE_RETRY_LIMIT = 10;
    private static final Duration VIRTUAL_INTERFACE_WAIT = Duration.ofSeconds(1);

    /**
     * 
     * @param containerName
     * @return the interface name or null on an error
     */
    private static String getContainerVirtualNicName(final String containerName) {
        for (int attempt = 0; attempt < VIRTUAL_INTERFACE_RETRY_LIMIT; ++attempt) {
            try {
                final ProcessBuilder builder = new ProcessBuilder("/etc/map/get_container_veth.sh", containerName);
                builder.redirectErrorStream(true);

                final Process process = builder.start();
                process.waitFor();

                try (StringWriter writer = new StringWriter()) {
                    IOUtils.copy(process.getInputStream(), writer, Charset.defaultCharset());
                    final String output = writer.toString().trim();

                    if (0 != process.exitValue()) {
                        LOGGER.warn("Unable to get the virtual interface for container {}: {}. Attempt {}",
                                containerName, output, attempt);
                        Thread.sleep(VIRTUAL_INTERFACE_WAIT.toMillis());
                    } else {
                        return output;
                    }
                }
            } catch (final IOException ioe) {
                LOGGER.error("Error getting the virtual interface for container " + containerName, ioe);
                return null;
            } catch (final InterruptedException ie) {
                LOGGER.error("Interrupted waiting for script to get virtual interface for container " + containerName,
                        ie);
                return null;
            }
        } // retry loop

        LOGGER.error("All attempts to get the virtual interface for container {} have failed", containerName);
        return null;
    }

    private void writeContainerMetadata(final Path hostMountTimeFolder,
            final ServiceIdentifier<?> service,
            final ContainerParameters parameters) {
        final ObjectMapper jsonWriter = JsonUtils.getStandardMapObjectMapper();

        try (Writer writer = Files.newBufferedWriter(hostMountTimeFolder.resolve(SimAppUtils.SERVICE_FILENAME))) {
            jsonWriter.writeValue(writer, service);
        } catch (final IOException e) {
            LOGGER.error("Error writing container service information", e);
        }

        try (Writer writer = Files.newBufferedWriter(hostMountTimeFolder.resolve("parameters.json"))) {
            jsonWriter.writeValue(writer, parameters);
        } catch (final IOException e) {
            LOGGER.error("Error writing container parameter information", e);
        }
    }

    // converts gigabytes to bytes
    private static long gigaBytesToBytes(double gigaBytes) {
        return Math.round(gigaBytes * KB_TO_BYTES * KB_TO_BYTES * KB_TO_BYTES);
    }

    // returns an available name for a new container
    private Map.Entry<NodeIdentifier, InetAddress> getAvailableContainerName() {

        final Set<NodeIdentifier> runningContainerNames = getRunningContainerIDs();
        final Map.Entry<NodeIdentifier, InetAddress> nextContainerName = containerNames.entrySet().stream() //
                .filter(entry -> !runningContainerNames.contains(entry.getKey())) //
                .findFirst().orElse(null);

        LOGGER.info("Available container names: {} Running container names: {} Next container name: {}", containerNames,
                runningContainerNames, nextContainerName);

        return nextContainerName;
    }

    private Map<NodeIdentifier, Double> allocatedCpus = new ConcurrentHashMap<>();

    /* package */ Set<NodeIdentifier> getRunningContainerIDs() {
        return containerResourceMonitor.getMonitoredContainerIDs();
    }

    @Override
    public boolean stopService(NodeIdentifier containerId) {
        final MapContainer container = runningContainers.get(containerId);
        if (null == container) {
            LOGGER.warn("Attempting to stop {} and the container is null. Someone else must have stopped it",
                    containerId);
            return true;
        }
        container.stop();

        // TODO: might need to change this later
        // perform shutdown tasks on container
        // File destinationFolder = new File("container_app_metrics_data");

        // if (destinationFolder.exists() || destinationFolder.mkdir()) {
        // File destinationFile = new File(
        // destinationFolder + File.separator + containerId +
        // "-processing_latency.csv");
        // retrieveContainerFile(containerId.getName(),
        // "/app_metrics_data/processing_latency.csv", destinationFile);
        // }

        // stop the container
        boolean stopResult = stopDockerContainer(containerId.getName());
        LOGGER.debug("Stopping container {} success result: {}", containerId.getName(), stopResult);

        // remove the container if it was stopped successfully
        if (stopResult) {
            // grab the logs
            final Path logOutput = container.getBaseOutputPath().resolve("logs.txt");
            final boolean getLogsResult = getDockerContainerLogs(containerId.getName(), logOutput);
            if (!getLogsResult) {
                LOGGER.warn("Trouble getting logs from docker container {}. Continuing with remove.",
                        containerId.getName());
            }

            if (removeDockerContainer(containerId.getName())) {

                // stop monitoring of the container
                containerResourceMonitor.stopMonitorForContainer(containerId);
                LOGGER.debug("Stopping resource monitor for container {}", containerId.getName());

                runningContainers.remove(containerId);

                // keep track of how many CPUs we've allocated
                allocatedCpus.remove(containerId);

                return true;
            } else {
                LOGGER.error("Error removing container {}", containerId.getName());
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public ImmutableMap<NodeAttribute, Double> getComputeCapacity() {
        final ImmutableMap.Builder<NodeAttribute, Double> capacity = ImmutableMap.builder();

        final double hardwareCpuCount = ncpResourceMonitor.getProcessorCount();
        final double simCpuCount = hardwareConfig.getCapacity().getOrDefault(NodeAttribute.CPU,
                Double.POSITIVE_INFINITY);
        final double cpuCount = Math.min(hardwareCpuCount, simCpuCount);

        // add number of processors
        capacity.put(NodeAttribute.CPU, cpuCount);

        // TASK_CONTAINERS is just CPU
        capacity.put(NodeAttribute.TASK_CONTAINERS, cpuCount);

        // add total memory in GB
        final double hardwareMemory = ncpResourceMonitor.getMemoryCapacity();
        final double simMemory = hardwareConfig.getCapacity().getOrDefault(NodeAttribute.MEMORY,
                Double.POSITIVE_INFINITY);
        final double memory = Math.min(hardwareMemory, simMemory);
        capacity.put(NodeAttribute.MEMORY, memory);

        ImmutableMap<NodeAttribute, Double> result = capacity.build();
        return result;
    }

    /**
     * Attempt to pull the Docker image with the given image name and tag
     * returns true if the pull was successful or if the current image is
     * already up to date and false otherwise.
     */
    /* package */ static boolean pullDockerImage(final String image, final String tag) {
        try {
            // request to create an image (by pulling)
            Response response = request("/images/create?fromImage=" + image + "&tag=" + tag, HTTP_POST, null);
            LOGGER.debug("Response " + response.getCode() + ": " + response.getResponseString());

            if (response.getCode() == DOCKER_RESPONSE_CODE_CREATE_IMAGE_NO_ERROR) {
                LOGGER.info("Pulled image (response code " + response.getCode() + "): " + image + ":" + tag);
                return true;
            } else {
                LOGGER.error("Failed to pull image (response code " + response.getCode() + "): " + image + ":" + tag);
                return false;
            }
        } catch (IOException e) {
            LOGGER.warn("Error pulling docker image {}", image, e);
            return false;
        }
    }

    /** attempts to remove a Docker image with the given name */
    /* package */ static boolean removeDockerImage(String image) {
        try {
            Response response = request("/images/" + image, HTTP_DELETE, null);
            LOGGER.debug("Response " + response.getCode() + ": " + response.getResponseString());

            if (response.getCode() == DOCKER_RESPONSE_CODE_REMOVE_IMAGE_NO_ERROR) {
                LOGGER.info("Removed image (response code {}): {}", response.getCode(), image);
                return true;
            } else {
                LOGGER.error("Failed to remove image (response code {}): {}", response.getCode(), image);
                return false;
            }
        } catch (IOException e) {
            LOGGER.warn("Error removing docker image {}", image, e);
            return false;
        }
    }

    // CHECKSTYLE:OFF data classes running commands in container
    @SuppressWarnings("unused") // JSON serialzation
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    private static final class CreateContainerData {
        public boolean AttachStdin = false;
        public boolean AttachStdout = true;
        public boolean AttachStderr = true;
        public String DetachKeys = "ctrl-p,ctrl-q";
        public boolean Tty = false;
        public List<String> Cmd = new LinkedList<>();
        public List<String> environment = new LinkedList<>();

        public void populateEnvironment(final Map<String, String> e) {
            if (null != e) {
                e.forEach((k, v) -> {
                    environment.add(String.format("%s=%s", k, v));
                });
            }
        }
    }

    @SuppressWarnings("unused") // JSON serialzation
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    private static final class StartContainerData {
        public boolean Tty = false;
        public boolean Detach = false;
    }
    // CHECKSTYLE:ON

    /**
     * Sends requests to create and run an Exec instance in a container.
     * 
     * @param containerId
     *            the name of the container
     * @param command
     *            the command to and its parameters
     * @param environment
     *            an environment to for the command
     * @return the response string of the command
     */
    /* package */ static String runCommandInContainer(String containerId,
            String[] command,
            Map<String, String> environment) {
        final CreateContainerData createData = new CreateContainerData();
        createData.Cmd.addAll(Arrays.asList(command));
        createData.populateEnvironment(environment);

        LOGGER.info("Run command in container '{}': {}", containerId, Arrays.toString(command));

        try {
            final ObjectMapper jsonParser = JsonUtils.getStandardMapObjectMapper();

            Response createExecResponse = request("/containers/" + containerId + "/exec", HTTP_POST,
                    jsonParser.writeValueAsString(createData));

            if (createExecResponse.getCode() == DOCKER_RESPONSE_CODE_CREATE_EXEC_INSTANCE_NO_ERROR) {
                try {
                    final JsonNode jsonCreateExecResponse = jsonParser.readTree(createExecResponse.getResponseString());
                    final String execInstanceId = jsonCreateExecResponse.get("Id").asText();

                    final StartContainerData startData = new StartContainerData();
                    Response startExecInstanceResponse = request("/exec/" + execInstanceId + "/start", HTTP_POST,
                            jsonParser.writeValueAsString(startData));
                    LOGGER.debug("Start exec for container '{}' had response code {} and returned: {}", containerId,
                            startExecInstanceResponse.getCode(), startExecInstanceResponse.getResponseString());

                    return startExecInstanceResponse.getResponseString();
                } catch (final JsonProcessingException e) {
                    LOGGER.error("Failed to parse create exec response for container '{}': {}", containerId,
                            createExecResponse.getResponseString(), e);
                }
            } else {
                LOGGER.error("Failed to create exec instance for container '{}' (response {}).", containerId,
                        createExecResponse.getCode());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to send JSON request for container '{}': {}", containerId, e.getMessage(), e);
        }

        return null;
    }

    // CHECKSTYLE:OFF data for running a container
    @SuppressWarnings("unused") // JSON serialzation
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    private static final class RunContainerData {
        public String Image;
        public NetworkingConfigData NetworkingConfig = null;
        public final HostConfigData HostConfig = new HostConfigData();
        public final Map<String, Object> ExposedPorts = new HashMap<>();

        public void addMount(String hostSourcePath, String containerTargetPath) {
            HostConfig.addMount(hostSourcePath, containerTargetPath);
        }

        public void addExposedPort(final int port) {
            ExposedPorts.put(port + "/tcp", Collections.emptyMap());
        }
    }

    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    private static final class NetworkingConfigData {
        public final Map<String, NetworkConfigData> EndpointsConfig = new HashMap<>();
    }

    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    private static final class NetworkConfigData {
        public final IPAMConfigData IPAMConfig = new IPAMConfigData();
    }

    @SuppressWarnings("unused") // JSON serialization
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    private static final class IPAMConfigData {
        public String IPv4Address;
    }

    @SuppressWarnings("unused") // JSON serialization
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    private static final class HostConfigData {
        public long NanoCPUs;
        public long Memory;
        public String NetworkMode;
        public final List<Mount> Mounts = new LinkedList<>();

        public void addMount(String hostSourcePath, String containerTargetPath) {
            Mount mount = new Mount();
            mount.Source = hostSourcePath;
            mount.Target = containerTargetPath;
            Mounts.add(mount);
        }
    }

    @SuppressWarnings("unused") // JSON serialization
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    private static final class Mount {
        public String Target;
        public String Source;
        public String Type = "bind";
        public boolean ReadOnly = false;
        public String Consistency = "default";
    }

    // CHECKSTYLE:ON

    // attempts to create and run a Docker containers with the given image
    /* package */ static boolean runDockerContainer(String name,
            String image,
            double cpus,
            long memoryBytes,
            String dockerNetwork,
            String containerIP,
            Map<String, String> mountMappings) {
        final RunContainerData runContainerData = new RunContainerData();
        runContainerData.Image = image;

        if (mountMappings != null) {
            // Add mounts from target container path to host source path
            mountMappings.forEach((hostSourcePath, containerTargetPath) -> {
                runContainerData.addMount(hostSourcePath, containerTargetPath);
            });
        }

        if (dockerNetwork != null) {
            runContainerData.HostConfig.NetworkMode = dockerNetwork;

            if (containerIP != null) {
                runContainerData.NetworkingConfig = new NetworkingConfigData();

                final NetworkConfigData networkConfigData = new NetworkConfigData();
                networkConfigData.IPAMConfig.IPv4Address = containerIP;
                runContainerData.NetworkingConfig.EndpointsConfig.put(dockerNetwork, networkConfigData);
            }

            LOGGER.info("Set custom IP address for container to '" + containerIP + "'. " + "Skipping port mappings.");
        }

        // Add resource limits
        runContainerData.HostConfig.NanoCPUs = (long) Math.ceil(cpus * CPUS_TO_NANOCPUS);
        runContainerData.HostConfig.Memory = memoryBytes;

        try {
            final ObjectMapper jsonParser = JsonUtils.getStandardMapObjectMapper();

            LOGGER.info("JSON for request to create container: {}", jsonParser.writeValueAsString(runContainerData));

            Response containerCreateResponse = request("/containers/create" + "?name=" + name, HTTP_POST,
                    jsonParser.writeValueAsString(runContainerData));
            LOGGER.debug("Create container response " + containerCreateResponse.getCode() + ": "
                    + containerCreateResponse.getResponseString());

            try {
                if (containerCreateResponse.getCode() == DOCKER_RESPONSE_CODE_CREATED_CONTAINER_SUCCESSFULLY) {
                    final JsonNode jsonCreateExecResponse = jsonParser
                            .readTree(containerCreateResponse.getResponseString());
                    final String containerID = jsonCreateExecResponse.get("Id").asText();

                    for (int attemptN = 0; attemptN < DOCKER_START_CONTAINER_ATTEMPTS; attemptN++) {
                        final Response containerStartResponse = request("/containers/" + containerID + "/start",
                                HTTP_POST, null);
                        LOGGER.debug("Start container attempt {} response {}: {}", attemptN,
                                containerStartResponse.getCode(), containerStartResponse.getResponseString());

                        if (containerStartResponse.getCode() == DOCKER_RESPONSE_CODE_START_CONTAINER_NO_ERROR) {
                            LOGGER.info("Started container with name '{}' and Id {} (response {}). Attempt {}", name,
                                    containerID, containerStartResponse.getCode(), attemptN);
                            return true;
                        }

                        final long retryDelay = SimAppUtils.getRetryDelay(DOCKER_START_CONTAINER_MIN_RETRY_DELAY,
                                DOCKER_START_CONTAINER_MAX_RETRY_DELAY);

                        try {
                            Thread.sleep(retryDelay);
                        } catch (InterruptedException e) {
                            LOGGER.debug("Failed to sleep between retries:", e);
                        }
                    }

                    LOGGER.error("Failed to start container with id {} after {} attempts.", containerID,
                            DOCKER_START_CONTAINER_ATTEMPTS);
                    return false;

                } else {
                    return false;
                }
            } catch (final JsonProcessingException e) {
                LOGGER.error(
                        "Error parsing JSON response for the attempt to create a container. "
                                + "Response had error code {} and JSON: {}",
                        containerCreateResponse.getCode(), containerCreateResponse.getResponseString(), e);
                return false;
            }
        } catch (final IOException e) {
            LOGGER.error("Error sending container create response", e);
            return false;
        }
    }

    // stops the Docker container with the given name
    /* package */ static boolean stopDockerContainer(String name) {
        try {
            Response response = request("/containers/" + name + "/stop?t=" + DOCKER_STOP_TIMEOUT_SECONDS, HTTP_POST,
                    null);
            LOGGER.debug("Response " + response.getCode() + ": " + response.getResponseString());

            if (response.getCode() == DOCKER_RESPONSE_CODE_STOP_CONTAINER_NO_ERROR) {
                LOGGER.info("Stopped container (response code " + response.getCode() + "): " + name);
                return true;
            } else {
                LOGGER.error("Failed to stop container (response code " + response.getCode() + "): " + name);
                return false;
            }
        } catch (IOException e) {
            LOGGER.warn("Error stopping container {}", name, e);
            return false;
        }
    }

    // removes the Docker container with the given name
    /* package */ boolean removeDockerContainer(String name) {

        try {
            Response response = request("/containers/" + name, HTTP_DELETE, null);
            LOGGER.debug("Response " + response.getCode() + ": " + response.getResponseString());

            if (response.getCode() == DOCKER_RESPONSE_CODE_REMOVE_CONTAINER_NO_ERROR) {
                LOGGER.info("Removed container (response code " + response.getCode() + "): " + name);
                return true;
            } else {
                LOGGER.error("Failed to remove container (response code " + response.getCode() + "): " + name);
                return false;
            }
        } catch (IOException e) {
            LOGGER.warn("Error removing docker container {}", name, e);
            return false;
        }
    }

    private boolean getDockerContainerLogs(final String name, final Path outputPath) {
        // don't use request method so that one can write directly to the output
        // file
        try {
            final URL url = new URL(DOCKER_REST_API_BASE_URL + "/containers/" + name + "/logs?stdout=true&stderr=true");
            final HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
            httpConnection.setRequestMethod("GET");
            try (InputStream response = httpConnection.getInputStream()) {
                Files.copy(response, outputPath);
            }
            LOGGER.info("Wrote logs for {} to {}", name, outputPath);

            if (httpConnection.getResponseCode() != DOCKER_RESPONSE_CODE_GET_LOGS_NO_ERROR) {
                LOGGER.error("Failed get logs (response code " + httpConnection.getResponseCode() + "): " + name);
                return false;
            } else {
                return true;
            }
        } catch (MalformedURLException e) {
            LOGGER.warn("Invalid URL getting logs", e);
            return false;
        } catch (IOException e) {
            LOGGER.warn("Error getting container logs from {}", name, e);
            return false;
        }
    }

    // performs a Docker Rest API call
    private static Response request(final String url, final String requestMethod, final String jsonContent)
            throws IOException {
        try {
            final URL url2 = new URL(DOCKER_REST_API_BASE_URL + url);

            LOGGER.info("Making HTTP {} request with URL '{}'", requestMethod, url2);

            final HttpURLConnection httpConnection = (HttpURLConnection) url2.openConnection();
            httpConnection.setRequestMethod(requestMethod);

            // optionally write content to the request
            if (jsonContent != null) {
                if (HTTP_GET.equals(requestMethod)) {
                    throw new IllegalArgumentException("Cannot send a request body with GET request method");
                }

                httpConnection.setDoOutput(true);
                httpConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                httpConnection.setFixedLengthStreamingMode(jsonContent.length());

                try (OutputStream out = httpConnection.getOutputStream()) {
                    try (BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(out, Charset.defaultCharset()))) {
                        writer.write(jsonContent);
                    }
                }
            }

            final StringBuilder response = new StringBuilder();

            // read http response
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(httpConnection.getInputStream(), Charset.defaultCharset()))) {
                String line = reader.readLine();

                while (line != null) {
                    LOGGER.debug("request: read line: " + line);
                    response.append(line + System.lineSeparator());
                    line = reader.readLine();
                }

                reader.close();
            } catch (IOException e) {
                LOGGER.error("Unable to open input stream to read response. Error response message '{}': ",
                        httpConnection.getResponseMessage(), e);
            }

            return new Response(httpConnection.getResponseCode(), response.toString());

        } catch (MalformedURLException e) {
            LOGGER.warn("Invalid URL {}", url, e);
            return null;
        }
    }

    private static final class Response {
        private int code;
        private String responseString;

        private Response(int code, String json) {
            this.code = code;
            this.responseString = json;
        }

        private int getCode() {
            return code;
        }

        private String getResponseString() {
            return responseString;
        }
    }

    private final Map<String, InterfaceIdentifier> interfaceIdentifiers = new HashMap<>();

    /**
     * Package visibility for testing. This allows me to force the creation of
     * the latest ResourceReports.
     */
    @SuppressFBWarnings(value = "UC_USELESS_OBJECT", justification = "runningContainersCopy is in fact used with forEach")
    /* package */ void updateResourceReports() {
        try {
            try (CloseableThreadContext.Instance ctc = CloseableThreadContext
                    .push(node.getNodeIdentifier().getName())) {

                // make a copy so that things don't change while we are creating
                // the resource report
                final Map<NodeIdentifier, MapContainer> runningContainersCopy = new HashMap<>(runningContainers);

                final long now = getClock().getCurrentTime();

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Updating resource reports time: {}", now);
                }

                synchronized (interfaceIdentifiers) {
                    interfaceIdentifiers.clear();
                    interfaceIdentifiers.putAll(createInterfaceIdentifiers());
                }

                final Map<NodeAttribute, Double> allocatedComputeCapacity = getAllocatedComputeCapacity();

                // update all containers after the neighbor map is updated
                // since it's used by the container to compute it's data
                runningContainersCopy.forEach((id, container) -> {
                    container.updateResourceReports(allocatedComputeCapacity);
                });

                // initialize long and short NodeIdentifier to
                // ContainerResourceReport maps
                final ImmutableMap.Builder<NodeIdentifier, ContainerResourceReport> longContainerReports = ImmutableMap
                        .builder();
                final ImmutableMap.Builder<NodeIdentifier, ContainerResourceReport> shortContainerReports = ImmutableMap
                        .builder();

                // add container reports
                runningContainersCopy.forEach((id, sim) -> {
                    final ContainerResourceReport longReport = sim
                            .getContainerResourceReport(ResourceReport.EstimationWindow.LONG);
                    longContainerReports.put(id, longReport);

                    final ContainerResourceReport shortReport = sim
                            .getContainerResourceReport(ResourceReport.EstimationWindow.SHORT);
                    shortContainerReports.put(id, shortReport);
                });

                final ImmutableMap<NodeAttribute, Double> nodeComputeCapacity = getComputeCapacity();

                // compute network information
                final Set<NodeIdentifier> connectedNeighbors = node.getConnectedNeighbors();
                final ImmutableMap<InterfaceIdentifier, ImmutableMap<LinkAttribute, Double>> reportNetworkCapacity = getNetworkCapacities(
                        interfaceIdentifiers, connectedNeighbors);
                LOGGER.trace("Network capacities: {}", reportNetworkCapacity);

                final Map<String, Map<RegionNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>> networkLoadPerNic = ncpResourceMonitor
                        .computeNetworkLoadPerNic(getNode());
                final Map<InterfaceIdentifier, Map<RegionNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>> networkLoadPerInterface = new HashMap<>();
                networkLoadPerNic.forEach((nic, data) -> {
                    final InterfaceIdentifier ii = interfaceIdentifiers.get(nic);
                    if (null == ii) {
                        LOGGER.warn("Interface {} found in network load NICs, but not found in interfaces map {}", nic,
                                interfaceIdentifiers);
                    } else {
                        networkLoadPerInterface.put(ii, data);
                    }
                });
                final ImmutableMap<InterfaceIdentifier, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> reportNetworkLoad = ImmutableUtils
                        .makeImmutableMap4(networkLoadPerInterface);

                networkDemandTracker.updateDemandValues(now, reportNetworkLoad);

                final ImmutableMap<InterfaceIdentifier, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> reportShortNetworkDemand = networkDemandTracker
                        .computeNetworkDemand(ResourceReport.EstimationWindow.SHORT);

                final ImmutableMap<InterfaceIdentifier, ImmutableMap<RegionNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> reportLongNetworkDemand = networkDemandTracker
                        .computeNetworkDemand(ResourceReport.EstimationWindow.LONG);

                final boolean skipNetworkData = AgentConfiguration.getInstance().getSkipNetworkData();

                synchronized (resourceReportLock) {

                    LOGGER.trace("updateResourceReports: reportNetworkLoad: {}", reportNetworkLoad);

                    longResourceReport = new ResourceReport(node.getNodeIdentifier(), now,
                            ResourceReport.EstimationWindow.LONG, nodeComputeCapacity, //
                            skipNetworkData ? ImmutableMap.of() : reportNetworkCapacity,
                            skipNetworkData ? ImmutableMap.of() : reportNetworkLoad,
                            skipNetworkData ? ImmutableMap.of() : reportLongNetworkDemand, //
                            longContainerReports.build(), containerNames.size(), runningContainersCopy.size());

                    LOGGER.trace("updateResourceReports: longResourceReport.getNetworkLoad: {}",
                            longResourceReport.getNetworkLoad());

                    shortResourceReport = new ResourceReport(node.getNodeIdentifier(), now,
                            ResourceReport.EstimationWindow.SHORT, nodeComputeCapacity, //
                            skipNetworkData ? ImmutableMap.of() : reportNetworkCapacity,
                            skipNetworkData ? ImmutableMap.of() : reportNetworkLoad,
                            skipNetworkData ? ImmutableMap.of() : reportShortNetworkDemand, //
                            shortContainerReports.build(), containerNames.size(), runningContainersCopy.size());

                    if (LOGGER.isTraceEnabled()) {
                        // output resource reports to logs
                        LOGGER.trace("Long Resource Report: " + resourceReportToString(longResourceReport));
                        LOGGER.trace("Short Resource Report: " + resourceReportToString(shortResourceReport));
                    }

                } // end lock
            } // logging thread context
        } catch (RuntimeException e) {
            LOGGER.error("updateResourceReports exception: {}", e);
        }
    }

    // outputs a ResourceReport to the log
    private String resourceReportToString(final ResourceReport resourceReport) {
        try (StringWriter writer = new StringWriter()) {
            final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper();
            mapper.writeValue(writer, resourceReport);
            return writer.toString();
        } catch (final IOException e) {
            LOGGER.error("Error converting resource report to string", e);
            return "ERROR";
        }
    }

    @Override
    public VirtualClock getClock() {
        return clock;
    }

    /**
     * 
     * @return the node that this resource manager is for, null until
     *         {@link #init(Controller, Map)} is called.
     */
    public Controller getNode() {
        return node;
    }

    /**
     * Obtains resource usage information for a container.
     * 
     * @param containerId
     *            the identifier for the container to get resource usage
     *            information for
     * @return the resource usage information for the container
     */
    public ContainerResourceStats getCurrentContainerResourceStats(NodeIdentifier containerId) {
        return containerResourceMonitor.getContainerResourceStats(containerId);
    }

    /**
     * The CPU overload factor. If we have allocated more virtual CPUs than
     * there are physical CPUs, then this will return a number greater than 1,
     * otherwise 1 is returned.
     *
     * @param allocatedComputeCapacity
     *            the result of {@link #getAllocatedComputeCapacity()}
     * @return the CPU overload factor
     */
    /* package */ double getCpuOverloadFactor(final Map<NodeAttribute, Double> allocatedComputeCapacity) {
        final int numPhysicalCpus = ncpResourceMonitor.getProcessorCount();
        final double allocatedCpus = allocatedComputeCapacity.getOrDefault(NodeAttribute.CPU, 0D);

        if (allocatedCpus <= numPhysicalCpus) {
            // no overload factor
            return 1.0;
        } else {
            // need to scale up loads as it's impossible to keep all virtual
            // CPUs busy when there are more virtual CPUs than physical CPUs
            return allocatedCpus / numPhysicalCpus;
        }
    }

    /**
     * The memory overload factor. If we have allocated more virtual memory than
     * there is physical memory, then this will return a number greater than 1,
     * otherwise 1 is returned.
     * 
     * @param allocatedComputeCapacity
     *            the result of {@link #getAllocatedComputeCapacity()}
     * @return the memory overload factor
     */
    /* package */ double getMemoryOverloadFactor(final Map<NodeAttribute, Double> allocatedComputeCapacity) {
        final double physicalMemory = ncpResourceMonitor.getMemoryCapacity();
        final double allocatedMemory = allocatedComputeCapacity.getOrDefault(NodeAttribute.MEMORY, 0D);

        if (allocatedMemory <= physicalMemory) {
            // no overload factor
            return 1.0;
        } else {
            // need to scale up loads as it's impossible to keep all virtual
            // CPUs busy when there are more virtual CPUs than physical CPUs
            return allocatedMemory / physicalMemory;
        }
    }

    private Map<NodeAttribute, Double> getAllocatedComputeCapacity() {
        final Map<NodeAttribute, Double> allocatedComputeCapacity = new HashMap<>();
        runningContainers.forEach((id, container) -> {
            final ImmutableMap<NodeAttribute, Double> containerComputeCapacity = container.getComputeCapacity();
            containerComputeCapacity.forEach((attr, value) -> {
                allocatedComputeCapacity.merge(attr, value, Double::sum);
            });
        });
        return allocatedComputeCapacity;
    }

    /**
     * 
     * @param nic
     *            the network interface to query
     * @return the speed or NaN if not found in {@link #ipToSpeed}
     */
    private double getTopologyBandwidthForNetworkInterface(final NetworkInterface nic) {
        for (final InterfaceAddress addr : nic.getInterfaceAddresses()) {
            final InetAddress a = addr.getAddress();
            final String nicIp = a.getHostAddress();
            if (ipToSpeed.containsKey(nicIp)) {
                return ipToSpeed.get(nicIp);
            }
        }
        return Double.NaN;
    }

    /**
     * 
     * @param nic
     *            the network interface to query
     * @return the speed or 0 if not found in {@link #ipToDelay}
     */
    private double getTopologyLinkDelayForNetworkInterface(final NetworkInterface nic) {
        for (final InterfaceAddress addr : nic.getInterfaceAddresses()) {
            final InetAddress a = addr.getAddress();
            final String nicIp = a.getHostAddress();
            if (ipToDelay.containsKey(nicIp)) {
                return ipToDelay.get(nicIp);
            }
        }
        return 0;
    }

    /**
     * Network bandwidth for the specified NIC from the topology.
     * 
     * @param nic
     *            the network interface to check
     * @return the value from the topology or {@link Double#POSITIVE_INFINITY}
     *         if nothing is specified in the topology
     */
    private double getTopologyBandwidth(final String nic) {
        try {
            final Enumeration<NetworkInterface> nicEnum = NetworkInterface.getNetworkInterfaces();
            while (nicEnum.hasMoreElements()) {
                final NetworkInterface n = nicEnum.nextElement();
                if (n.getName().equals(nic)) {
                    final double bw = getTopologyBandwidthForNetworkInterface(n);
                    if (!Double.isNaN(bw)) {
                        return bw;
                    }

                    // check bridge
                    final NetworkInterface bridge = ncpResourceMonitor.getBridgeNameforNIC(n);
                    if (null != bridge) {
                        final double bbw = getTopologyBandwidthForNetworkInterface(bridge);
                        if (!Double.isNaN(bbw)) {
                            return bbw;
                        }
                    }

                }
            }
            return Double.POSITIVE_INFINITY;
        } catch (final SocketException e) {
            LOGGER.warn("Unable to enumerate network interfaces when getting topology bandwidth, returning Infinity",
                    e);
            return Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Link delay for the specified NIC from the topology.
     * 
     * @param nic
     *            the network interface to check
     * @return the value from the topology or {@link Double#POSITIVE_INFINITY}
     *         if nothing is specified in the topology
     */
    private double getTopologyLinkDelay(final String nic) {
        try {
            final Enumeration<NetworkInterface> nicEnum = NetworkInterface.getNetworkInterfaces();
            while (nicEnum.hasMoreElements()) {
                final NetworkInterface n = nicEnum.nextElement();
                if (n.getName().equals(nic)) {
                    final double bw = getTopologyLinkDelayForNetworkInterface(n);
                    if (!Double.isNaN(bw)) {
                        return bw;
                    }

                    // check bridge
                    final NetworkInterface bridge = ncpResourceMonitor.getBridgeNameforNIC(n);
                    if (null != bridge) {
                        final double bbw = getTopologyLinkDelayForNetworkInterface(bridge);
                        if (!Double.isNaN(bbw)) {
                            return bbw;
                        }
                    }

                }
            }
            return 0;
        } catch (final SocketException e) {
            LOGGER.warn("Unable to enumerate network interfaces when getting topology link delay, returning 0", e);
            return 0;
        }
    }

    private ImmutableMap<InterfaceIdentifier, ImmutableMap<LinkAttribute, Double>> getNetworkCapacities(
            final Map<String, InterfaceIdentifier> nicToInterfaceId,
            final Set<NodeIdentifier> connectedNeighbors) {
        LOGGER.debug("getNetworkCapacities: Obtaining network capacity for node with ID {}, interfaces: {}",
                node.getNodeIdentifier(), nicToInterfaceId);

        final ImmutableMap.Builder<InterfaceIdentifier, ImmutableMap<LinkAttribute, Double>> networkCapacity = ImmutableMap
                .builder();

        nicToInterfaceId.forEach((nic, ii) -> {
            final double hardwareBandwidth = ncpResourceMonitor.getNetworkBandwidth(nic);
            final double topologyBandwidth = getTopologyBandwidth(nic);
            final double bandwidth = Math.min(hardwareBandwidth, topologyBandwidth);
            final double linkDelay = getTopologyLinkDelay(nic);

            LOGGER.debug("getNetworkCapacities: hw bandwidth: {} topology bandwidth: {} bandwidth: {} delay: {}",
                    hardwareBandwidth, topologyBandwidth, bandwidth, linkDelay);

            final ImmutableMap.Builder<LinkAttribute, Double> linkCapacity = ImmutableMap.builder();

            // add nic bandwidth
            linkCapacity.put(LinkAttribute.DATARATE_TX, bandwidth);
            linkCapacity.put(LinkAttribute.DATARATE_RX, bandwidth);

            linkCapacity.put(LinkAttribute.DELAY, linkDelay);

            LOGGER.debug("getNetworkCapacities: Add capacity for NIC {} with bandwidth {}", nic, bandwidth);
            networkCapacity.put(ii, linkCapacity.build());
        }); // foreach nic

        return networkCapacity.build();
    }

    /**
     * Null until {@link #init(Controller, Map)} is called.
     * 
     * @return the {@code NCPResourceMonitor}
     */
    public NCPResourceMonitor getNCPResourceMonitor() {
        return ncpResourceMonitor;
    }

    /**
     * Given a network interface name, convert it to the network interface name
     * that will show up as a key in {@link #interfaceIdentifiers}.
     * 
     * @param nic
     *            the network interface to lookup
     * @return the network interface to use, not null
     */
    private String getPhysicalNic(final String nic) {

        // if nicName refers to a bridge, replace nicName with the the
        // first physical interface name
        final Set<String> physicalNicNames = ncpResourceMonitor.getPysicalNICNamesForBridge(nic);

        final String physicalNicName;
        if (physicalNicNames == null || physicalNicNames.isEmpty()) {
            physicalNicName = nic;
            LOGGER.debug("{} is a physical interface.", nic);
        } else {
            if (physicalNicNames.size() > 1)
                LOGGER.warn("There is more than one physical interface ({}) for bridge {}.", physicalNicNames, nic);

            physicalNicName = physicalNicNames.iterator().next();
            LOGGER.debug("Selected physical interface {} from set {} for bridge {}.", physicalNicName, physicalNicNames,
                    nic);
        }

        return physicalNicName;
    }

    /**
     * Determine which neighbors are attached to which network interfaces.
     */
    private Map<String, InterfaceIdentifier> createInterfaceIdentifiers() {
        final Collection<NodeIdentifier> neighbors = node.getNeighbors();
        LOGGER.trace("createInterfaceIdentifiers neighbors: {}", neighbors);

        final Collection<String> networkInterfaceNames = ncpResourceMonitor.getMonitoredNetworkInterfaceNames();
        LOGGER.trace("createInterfaceIdentifiers interfaces: {}", networkInterfaceNames);

        final Map<String, Set<NodeIdentifier>> nicToNeighbor = new HashMap<>();
        // initialize with all monitored interfaces
        networkInterfaceNames.forEach(n -> {
            nicToNeighbor.put(n, new HashSet<>());
        });

        final RoutingTable table = ncpResourceMonitor.getRoutingTable();

        neighbors.forEach(neighbor -> {
            try {
                // reverse DNS lookup of neighbor's domain name
                final InetAddress neighborAddr = DnsUtils.getByName(neighbor.getName());
                final String nicName = table.route(neighborAddr);

                final String physicalNicName = getPhysicalNic(nicName);

                if (physicalNicName != null) {
                    nicToNeighbor.computeIfAbsent(physicalNicName, k -> new HashSet<>()).add(neighbor);
                    LOGGER.debug("createNeighborToNICMap: Mapped neighbor to NIC: {} -> {}", neighbor, physicalNicName);
                } else {
                    LOGGER.warn("createNeighborToNICMap: Failed to map neighbor '{}' to NIC.", neighbor);
                }
            } catch (final UnknownHostException e) {
                LOGGER.error("Failed to obtain the InetAddress for neighbor '{}'.", neighbor.getName(), e);
            }
        });

        LOGGER.trace("Computed new NIC to neighbor map: {}", nicToNeighbor);

        final Map<String, InterfaceIdentifier> interfaces = new HashMap<>();
        nicToNeighbor.forEach((name, n) -> {
            final InterfaceIdentifier ii = new InterfaceIdentifier(name, ImmutableSet.copyOf(n));
            interfaces.put(name, ii);
        });
        return interfaces;
    }

    private class ContainerShutdownHook extends Thread {
        @Override
        public void run() {
            // copyAllCsvFiles();

            LOGGER.info(
                    "--------------------------------- Running container shutdown hook. -----------------------------------");

            containerNames.forEach((id, addr) -> {
                LOGGER.info("--------- Shutdown hook: running stopService({}) ---------", id);
                stopService(id);
            });

            LOGGER.info(
                    "--------------------------------- Finished container shutdown hook. ---------------------------------");
            super.run();
        }
    }

    @Override
    public void fetchImage(@Nonnull final ServiceIdentifier<?> service) {
        final String image = getImageForService(service);

        imageManager.fetchImage(image);
    }

    @Override
    public boolean waitForImage(@Nonnull final ServiceIdentifier<?> service) {
        try {
            final String image = getImageForService(service);
            return imageManager.waitForImage(image);
        } catch (final InterruptedException e) {
            LOGGER.warn("Got interrupted waiting for image for service {}", service, e);
            return false;
        }
    }

    /**
     * 
     * @return the set of images currently loaded on this node
     */
    @Nonnull
    /* package */ static Set<String> getCurrentImages() {

        final Set<String> images = new HashSet<>();

        try {
            // request to create an image (by pulling)
            final Response response = request("/images/json", HTTP_GET, null);
            LOGGER.debug("List images response " + response.getCode() + ": " + response.getResponseString());

            if (response.getCode() == DOCKER_RESPONSE_CODE_LIST_IMAGES) {
                final ObjectMapper jsonParser = JsonUtils.getStandardMapObjectMapper();
                try {
                    final JsonNode parsed = jsonParser.readTree(response.getResponseString());
                    for (final JsonNode imageEntry : parsed) {
                        final JsonNode repoTags = imageEntry.get("RepoTags");
                        for (final JsonNode imageNode : repoTags) {
                            final String image = imageNode.asText();
                            images.add(image);

                            // strip off the tag
                            final int colonIndex = image.lastIndexOf(':');
                            if (colonIndex > 0) {
                                final String imageNoTag = image.substring(0, colonIndex);
                                images.add(imageNoTag);
                            }
                        }
                    }
                } catch (final JsonProcessingException e) {
                    LOGGER.error("Failed to parse response from image listing: {}", response.getResponseString(), e);
                }
            } else {
                LOGGER.error("Failed to list images (response code " + response.getCode() + ")");
            }
        } catch (final IOException e) {
            LOGGER.warn("Error listing docker images", e);
        }

        return images;
    }

    @Override
    public void addFailedRequest(final NodeIdentifier client,
            final NodeIdentifier containerId,
            final long serverEndTime,
            final Map<NodeAttribute, Double> serverLoad,
            final long networkEndTime,
            final Map<LinkAttribute, Double> networkLoad) {

        LOGGER.debug(
                "addFailedRequest: client: {}, containerId: {}, serverEndTime: {}, serverLoad: {}, "
                        + "networkEndTime: {}, networkLoad: {}, useFailedRequestsInDemand = {}",
                client, containerId, serverEndTime, serverLoad, networkEndTime, networkLoad, useFailedRequestsInDemand);

        if (useFailedRequestsInDemand) {
            final MapContainer container = runningContainers.get(containerId);
            if (null == container) {
                LOGGER.warn("Looking for container {} to notify of failed request, but it's not running", containerId);
                return;
            }

            final RegionIdentifier containerRegion = container.getParentNode().getRegionIdentifier();
            final RegionIdentifier clientRegion = regionLookupService.getRegionForNode(client);

            try {
                container.addFailedRequest(client, serverEndTime, serverLoad, networkEndTime, networkLoad);

                final InetAddress clientAddress = DnsUtils.getByName(client.getName());
                final String nic = ncpResourceMonitor.getRoutingTable().route(clientAddress.getHostAddress());
                final String physicalNic = getPhysicalNic(nic);

                if (null == physicalNic) {
                    LOGGER.error("Cannot find network interface for connection to {}, ignoring failed request", client);
                    return;
                }

                final InterfaceIdentifier ifce;
                synchronized (interfaceIdentifiers) {
                    if (interfaceIdentifiers.isEmpty()) {
                        // ensure the interface identifiers have been updated
                        interfaceIdentifiers.clear();
                        interfaceIdentifiers.putAll(createInterfaceIdentifiers());
                    }

                    if (!interfaceIdentifiers.containsKey(physicalNic)) {
                        LOGGER.error("Unable to find interface for nic {}, ignoring failed request", physicalNic);
                        return;
                    }
                    ifce = interfaceIdentifiers.get(physicalNic);
                }

                networkDemandTracker.addFailedRequest(ifce, clientRegion, containerRegion, container.getService(),
                        networkEndTime, networkLoad);
            } catch (UnknownHostException e) {
                LOGGER.error("Cannot find address for client {}, ignoring failed request", client, e);
            }
        }
    }
}
