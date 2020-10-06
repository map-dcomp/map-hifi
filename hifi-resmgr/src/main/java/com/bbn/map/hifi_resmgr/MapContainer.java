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
package com.bbn.map.hifi_resmgr;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.hifi.util.UnitConversions;
import com.bbn.map.simulator.NetworkDemandTracker;
import com.bbn.protelis.networkresourcemanagement.ContainerResourceReport;
import com.bbn.protelis.networkresourcemanagement.InterfaceIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NetworkServer;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceStatus;
import com.bbn.protelis.utils.ImmutableUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;;

/**
 * Class to represent Docker container in the high-fidelity MAP system network.
 * 
 * @author awald
 * @author jschewe
 *
 */
public class MapContainer {

    private static final Logger LOGGER = LogManager.getLogger(MapContainer.class);

    private final Object lock = new Object();
    private ContainerResourceReport shortResourceReport;
    private ContainerResourceReport longResourceReport;
    private final NetworkDemandTracker networkDemandTracker;

    private ContainerResourceStats latestContainerResourceStats;

    private final SimpleAverageRequestProcessTimeRetriever averageRequestProcessTimeRetriever;

    private final ActiveConnectionCountRetriever activeConnectionCountRetriever;

    private final ImmutableMap<String, String> mountMappings;

    private final IftopProcessor iftopProcessor;
    private final InetAddress containerAddress;
    private final Path baseOutputPath;

    private final String nicName;

    /**
     * 
     * @param parent
     *            the resource manager for the parent node
     * @param serviceId
     *            the identifier for the service to run in this container
     * @param containerId
     *            the identifier to use for this container
     * @param networkCapacity
     *            see {@link #getNetworkCapacity()}
     * @param mountMappings
     *            see {@link #getMountMappings()}
     * @param containerAddress
     *            the address of the container, used for computing network load
     * @param containerNic
     *            the network interface of the container, used for computing
     *            network load
     * @param baseOutputPath
     *            see {@link #getBaseOutputPath()}
     * @param hostMountContainerAppMetricsFolder
     *            path to the app metrics folder on the host
     * @param nicName
     *            the name of the network interface for this container
     */
    public MapContainer(@Nonnull final SimpleDockerResourceManager parent,
            @Nonnull final ServiceIdentifier<?> serviceId,
            @Nonnull final NodeIdentifier containerId,
            @Nonnull final String nicName,
            @Nonnull final ImmutableMap<LinkAttribute, Double> networkCapacity,
            @Nonnull final ImmutableMap<String, String> mountMappings,
            @Nonnull final InetAddress containerAddress,
            @Nonnull final NetworkInterface containerNic,
            @Nonnull final Path baseOutputPath,
            @Nonnull final Path hostMountContainerAppMetricsFolder) {
        this.service = serviceId;
        this.identifier = containerId;
        this.nicName = nicName;
        this.parent = parent;
        this.networkCapacity = networkCapacity;
        this.mountMappings = mountMappings;
        this.containerAddress = containerAddress;
        this.baseOutputPath = baseOutputPath;

        this.shortResourceReport = ContainerResourceReport.getNullReport(getIdentifier(),
                ResourceReport.EstimationWindow.SHORT);
        this.longResourceReport = ContainerResourceReport.getNullReport(getIdentifier(),
                ResourceReport.EstimationWindow.LONG);
        this.networkDemandTracker = new NetworkDemandTracker();

        // initialize resource stats to default value
        this.latestContainerResourceStats = new ContainerResourceStats();

        this.averageRequestProcessTimeRetriever = new SimpleAverageRequestProcessTimeRetriever(this.identifier,
                hostMountContainerAppMetricsFolder);

        this.activeConnectionCountRetriever = new ActiveConnectionCountRetriever(this.identifier,
                hostMountContainerAppMetricsFolder);

        this.iftopProcessor = new IftopProcessor(containerNic);

        LOGGER.info("Constructing container with id " + containerId.getName());
    }

    /**
     * 
     * @return the capacity of this container
     */
    @Nonnull
    public ImmutableMap<NodeAttribute, Double> getComputeCapacity() {
        ImmutableMap.Builder<NodeAttribute, Double> computeCapacity = new ImmutableMap.Builder<>();

        final ContainerResourceStats resourceStats = getLatestContainerResourceStats();
        final Double cpus = resourceStats.getCpuCapacity();
        final Long memory = resourceStats.getMemoryCapacity();

        if (cpus != null) {
            computeCapacity.put(NodeAttribute.CPU, cpus);
            computeCapacity.put(NodeAttribute.TASK_CONTAINERS, cpus);
        }

        if (memory != null) {
            computeCapacity.put(NodeAttribute.MEMORY, UnitConversions.bytesToGigaBytes(memory));
        }

        return computeCapacity.build();
    }

    private final ImmutableMap<LinkAttribute, Double> networkCapacity;

    /**
     * @return the capacity of this container to each neighboring node.
     */
    @Nonnull
    public ImmutableMap<InterfaceIdentifier, ImmutableMap<LinkAttribute, Double>> getNetworkCapacity() {
        return ImmutableMap.of(createInterfaceIdentifier(parent.getNode().getNeighbors()), networkCapacity);
    }

    /**
     * @return the mappings from paths of directories in this container to paths
     *         of directories on the host
     */
    @Nonnull
    public ImmutableMap<String, String> getMountMappings() {
        return mountMappings;
    }

    private final SimpleDockerResourceManager parent;

    /**
     * 
     * @return the node that this container lives in
     */
    @Nonnull
    public NetworkServer getParentNode() {
        return parent.getNode();
    }

    private final NodeIdentifier identifier;

    /**
     * 
     * @return the name of the container
     */
    @Nonnull
    public NodeIdentifier getIdentifier() {
        return identifier;
    }

    private final ServiceIdentifier<?> service;

    /**
     * 
     * @return the current running service, will be null if no service is
     *         running
     */
    public ServiceIdentifier<?> getService() {
        return service;
    }

    private final Map<RegionIdentifier, Integer> numRequestsPerRegion = new HashMap<>();

    /**
     * The number of requests handled by this node for each source region. Key
     * is the source region, value is the number of requests.
     * 
     * @return unmodifiable copy of the internal state
     */
    public Map<RegionIdentifier, Integer> getNumRequestsPerRegion() {
        synchronized (lock) {
            return new HashMap<>(numRequestsPerRegion);
        }
    }

    private ServiceStatus serviceStatus = ServiceStatus.STARTING;

    /**
     * 
     * @return the status of the service
     */
    public ServiceStatus getServiceStatus() {
        synchronized (lock) {
            return serviceStatus;
        }
    }

    /**
     * Sets the status of this container.
     * 
     * @param status
     *            the new status for this container
     */
    public void setServiceStatus(ServiceStatus status) {
        synchronized (lock) {
            serviceStatus = status;
            LOGGER.info("Set service status for container {} to {}.", identifier, serviceStatus);
        }
    }

    // private int requestsCompleted = 0;
    // private long timeToProcessRequests = 0;
    //
    // private void recordRequestFinishedServer(final long timeToProcess) {
    // if (LOGGER.isDebugEnabled()) {
    // LOGGER.debug("Recording server request finished node: {} container: {}
    // time: {}",
    // parent.getNode().getNodeIdentifier(), identifier, timeToProcess);
    // }
    //
    // ++requestsCompleted;
    // timeToProcessRequests += timeToProcess;
    // }

    /**
     * 
     * @return the current state of the container
     * @param demandEstimationWindow
     *            the window over which to compute the demand
     */
    public ContainerResourceReport getContainerResourceReport(
            final ResourceReport.EstimationWindow demandEstimationWindow) {
        synchronized (lock) {
            switch (demandEstimationWindow) {
            case LONG:
                return longResourceReport;
            case SHORT:
                return shortResourceReport;
            default:
                throw new IllegalArgumentException("Unknown estimation window type: " + demandEstimationWindow);
            }
        }
    }

    /**
     * Start generation of {@link ResourceReport} objects.
     * 
     * @throws IllegalStateException
     *             if report generation is already started
     */
    public void start() {
        setServiceStatus(ServiceStatus.STARTING);

        averageRequestProcessTimeRetriever.start();
        activeConnectionCountRetriever.start();

        iftopProcessor.start();
    }

    /**
     * Update the current resource reports. Called from the tests and from
     * {@link SimpleDockerResourceManager#updateResourceReports()}. This method
     * depends on
     * {@link SimpleDockerResourceManager#getNeighborsAttachedToNic(String)}, so
     * that map needs to be up to date before this method is called.
     */
    /* package */ void updateResourceReports(final Map<NodeAttribute, Double> allocatedComputeCapacity) {

        try {
            LOGGER.debug("Update Container Resource Reports");

            try (CloseableThreadContext.Instance ctc = CloseableThreadContext.push(getIdentifier().getName())) {
                synchronized (lock) {
                    final long now = parent.getClock().getCurrentTime();

                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Updating resource reports time: {}", now);
                    }

                    // retrieve the latest ContainerResourceStats object for
                    // this MapContainer
                    updateContainerResourceStats();

                    final ImmutableMap<NodeAttribute, Double> computeCapacity = getComputeCapacity();

                    // Obtain average processing time
                    final double serverAverageProcessTime = averageRequestProcessTimeRetriever
                            .getAverageProcessingTime();

                    // Obtain measured compute load
                    final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> reportComputeLoad = getComputeLoad(
                            allocatedComputeCapacity);

                    final ImmutableMap<InterfaceIdentifier, ImmutableMap<LinkAttribute, Double>> reportNetworkCapacity = getNetworkCapacity();

                    // Obtain measured network load
                    final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> reportNetworkLoad = getNetworkLoad();

                    networkDemandTracker.updateDemandValues(now, reportNetworkLoad);

                    updateComputeDemandValues(now, reportComputeLoad);

                    final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> reportLongNetworkDemand = networkDemandTracker
                            .computeNetworkDemand(now, ResourceReport.EstimationWindow.LONG);

                    final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> reportLongServerDemand = computeComputeDemand(
                            now, ResourceReport.EstimationWindow.LONG);

                    final boolean skipNetworkData = AgentConfiguration.getInstance().getSkipNetworkData();

                    longResourceReport = new ContainerResourceReport(getIdentifier(), now, getService(),
                            getServiceStatus(), ResourceReport.EstimationWindow.LONG, computeCapacity,
                            reportComputeLoad, reportLongServerDemand, serverAverageProcessTime, //
                            skipNetworkData ? ImmutableMap.of() : reportNetworkCapacity,
                            skipNetworkData ? ImmutableMap.of() : reportNetworkLoad,
                            skipNetworkData ? ImmutableMap.of() : reportLongNetworkDemand);

                    final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> reportShortServerDemand = computeComputeDemand(
                            now, ResourceReport.EstimationWindow.SHORT);

                    final ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> reportShortNetworkDemand = networkDemandTracker
                            .computeNetworkDemand(now, ResourceReport.EstimationWindow.SHORT);

                    shortResourceReport = new ContainerResourceReport(getIdentifier(), now, getService(),
                            getServiceStatus(), ResourceReport.EstimationWindow.SHORT, computeCapacity,
                            reportComputeLoad, reportShortServerDemand, serverAverageProcessTime, //
                            skipNetworkData ? ImmutableMap.of() : reportNetworkCapacity,
                            skipNetworkData ? ImmutableMap.of() : reportNetworkLoad,
                            skipNetworkData ? ImmutableMap.of() : reportShortNetworkDemand);
                } // end lock
            } // logging thread context

        } catch (RuntimeException e) {
            LOGGER.error("updateResourceReports Exception: ", e);
        }
    }

    /**
     * uses the NodeIdentifier for this MapContainer to obtain the
     * ContainerResourceStats. Expects the lock to be held.
     */
    private void updateContainerResourceStats() {
        LOGGER.debug("Updating latest container resource stats for container with id: {}", identifier);

        ContainerResourceStats stats = parent.getCurrentContainerResourceStats(identifier);
        LOGGER.debug("updateContainerResourceStats: new stats: {}", stats);

        if (stats != null) {
            latestContainerResourceStats = stats;
        }

        if (latestContainerResourceStats == null)
            latestContainerResourceStats = new ContainerResourceStats();

        String status = latestContainerResourceStats.getStatus();

        if (status != null) {
            switch (status) {
            case "running":
                if (serviceStatus == ServiceStatus.STOPPED || serviceStatus == ServiceStatus.STARTING
                        || serviceStatus == ServiceStatus.UNKNOWN) {
                    setServiceStatus(ServiceStatus.RUNNING);
                }
                break;

            case "created":
            case "exited":
            case "removing":
                if (serviceStatus == ServiceStatus.RUNNING || serviceStatus == ServiceStatus.STOPPING
                        || serviceStatus == ServiceStatus.UNKNOWN) {
                    setServiceStatus(ServiceStatus.STOPPED);
                }
                break;

            default:
                LOGGER.error(
                        "updateContainerResourceStats: Found unknown container status string '{}'. Treating it as UNKOWN.",
                        status);
                setServiceStatus(ServiceStatus.UNKNOWN);
                break;
            }
        }

        LOGGER.debug("Container status of {}: {}, serviceStatus = {}", identifier, status, serviceStatus);
    }

    // returns the current ContainerResourceStats for this container
    private ContainerResourceStats getLatestContainerResourceStats() {
        synchronized (lock) {
            LOGGER.debug("Get latest container resource stats for container with id: {}", identifier);
            return latestContainerResourceStats;
        }
    }

    private ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> computeComputeDemand(final long now,
            @Nonnull final ResourceReport.EstimationWindow estimationWindow) {
        final long duration;
        switch (estimationWindow) {
        case LONG:
            duration = AgentConfiguration.getInstance().getDcopEstimationWindow().toMillis();
            break;
        case SHORT:
            duration = AgentConfiguration.getInstance().getRlgEstimationWindow().toMillis();
            break;
        default:
            throw new IllegalArgumentException("Unknown estimation window: " + estimationWindow);
        }

        final long cutoff = now - duration;
        final Map<NodeIdentifier, Map<NodeAttribute, Double>> sums = new HashMap<>();
        final Map<NodeIdentifier, Map<NodeAttribute, Integer>> counts = new HashMap<>();

        NetworkLoadTracker.twoLevelHistoryMapCountSum(computeLoadHistory, cutoff, sums, counts);

        final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> reportDemand = NetworkLoadTracker
                .twoLevelMapAverage(sums, counts);

        return reportDemand;
    }

    private final Map<Long, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>>> computeLoadHistory = new HashMap<>();

    private void updateComputeDemandValues(final long timestamp,
            @Nonnull final ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> computeLoad) {
        computeLoadHistory.put(timestamp, computeLoad);

        // clean out old entries from server load and network load
        final long historyCutoff = timestamp
                - Math.max(AgentConfiguration.getInstance().getDcopEstimationWindow().toMillis(),
                        AgentConfiguration.getInstance().getRlgEstimationWindow().toMillis());
        final Iterator<Map.Entry<Long, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>>>> serverIter = computeLoadHistory
                .entrySet().iterator();
        while (serverIter.hasNext()) {
            final Map.Entry<Long, ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>>> entry = serverIter
                    .next();
            if (entry.getKey() < historyCutoff) {

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Removing compute demand value {} because it's time {} is before {}", entry.getValue(),
                            entry.getKey(), historyCutoff);
                }

                serverIter.remove();
            }
        }
    }

    private ImmutableMap<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> getComputeLoad(
            final Map<NodeAttribute, Double> allocatedComputeCapacity) {
        ImmutableMap.Builder<NodeIdentifier, ImmutableMap<NodeAttribute, Double>> nodeComputeLoad = new ImmutableMap.Builder<>();
        ImmutableMap.Builder<NodeAttribute, Double> load = ImmutableMap.builder();

        final ContainerResourceStats resourceStats = getLatestContainerResourceStats();

        final double rawContainerCpuUsage = resourceStats.getCpuUsage();
        if (!Double.isNaN(rawContainerCpuUsage)) {
            final double containerCpuUsage = parent.getCpuOverloadFactor(allocatedComputeCapacity)
                    * rawContainerCpuUsage;
            load.put(NodeAttribute.CPU, containerCpuUsage);
            load.put(NodeAttribute.TASK_CONTAINERS, containerCpuUsage);
        }

        final double rawMemoryUsage = UnitConversions.bytesToGigaBytes(resourceStats.getMemoryUsage());
        if (!Double.isNaN(rawMemoryUsage)) {
            final double memoryUsage = parent.getMemoryOverloadFactor(allocatedComputeCapacity) * rawMemoryUsage;
            load.put(NodeAttribute.MEMORY, memoryUsage);
        }

        final double activeConnectionCount = activeConnectionCountRetriever.getCurrentCount();
        load.put(NodeAttribute.QUEUE_LENGTH, activeConnectionCount);

        // TODO: ticket:86 needs to know which client created the load
        nodeComputeLoad.put(NodeIdentifier.UNKNOWN, load.build());

        return nodeComputeLoad.build();
    }

    private InterfaceIdentifier createInterfaceIdentifier(final Set<NodeIdentifier> neighbors) {
        return new InterfaceIdentifier(nicName, ImmutableSet.copyOf(neighbors));
    }

    /**
     * 
     * @return see {@link ContainerResourceReport#getNetworkLoad()}
     */
    private ImmutableMap<InterfaceIdentifier, ImmutableMap<NodeNetworkFlow, ImmutableMap<ServiceIdentifier<?>, ImmutableMap<LinkAttribute, Double>>>> getNetworkLoad() {
        final Map<InterfaceIdentifier, Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>> networkLoad = new HashMap<>();

        // always create the nic load map as this is expected downstream
        final Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>> baseNetworkLoad = new HashMap<>();

        parent.getNCPResourceMonitor().gatherNetworkInformation(parent.getNode(), containerAddress, iftopProcessor,
                baseNetworkLoad);

        // we don't know which neighbor interface the traffic came through on
        // the NCP, so make it appear as if it came from all of them
        final Set<NodeIdentifier> neighbors = parent.getNode().getNeighbors();
        networkLoad.put(createInterfaceIdentifier(neighbors), baseNetworkLoad);

        return ImmutableUtils.makeImmutableMap4(networkLoad);
    }

    @Override
    public String toString() {
        return identifier.getName();
    }

    /**
     * Changes the status of this MapContainer to STOPPING.
     */
    public void stop() {
        LOGGER.debug("stop: start");
        synchronized (lock) {
            if (serviceStatus == ServiceStatus.STARTING || serviceStatus == ServiceStatus.RUNNING)
                setServiceStatus(ServiceStatus.STOPPING);
        }

        averageRequestProcessTimeRetriever.stopReading();
        activeConnectionCountRetriever.stopReading();

        LOGGER.debug("stop: end");
    }

    /**
     * 
     * @return the location on the host where outputs from the container go
     */
    public Path getBaseOutputPath() {
        return baseOutputPath;
    }
}
