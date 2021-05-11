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

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.Controller;
import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.hifi.FileRegionLookupService;
import com.bbn.map.hifi.HiFiAgent;
import com.bbn.map.hifi.dns.WeightedRecordMessageServer;
import com.bbn.map.hifi.simulation.SimDriver;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.hifi.util.IdentifierUtils;
import com.bbn.map.hifi.util.UnitConversions;
import com.bbn.map.utils.MAPServices;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Class that retrieves the resource usage and capacity information from /proc
 * on a single NCP.
 * 
 * @author awald
 *
 */
@SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME", justification = "Need absolute path to find sys and proc files on Linux")
public class NCPResourceMonitor {
    private static Logger log = LogManager.getLogger(NCPResourceMonitor.class);

    private static final int DNS_QUERY_PORT = 53;

    // Files for capacity information
    private static final File FILE_CPU_CAPACITY = new File("/proc/cpuinfo");

    private static final File FILE_NETWORK_CAPACITY = new File("/sys/class/net/");

    private static final String FILE_NETWORK_CAPACITY_2 = "/speed";

    // Files for network routes information
    private static final File FILE_NETWORK_ROUTE_INFORMATION = new File("/proc/net/route");

    // Files for usage information
    private static final File FILE_CPU_USAGE = new File("/proc/stat");

    private static final File FILE_MEMORY_USAGE = new File("/proc/meminfo");

    // unit conversion constants
    private static final int KILOBYTES_TO_BYTES = 1024;

    private static final ImmutableSet<String> OSPF_ADDRESSES = ImmutableSet.of("224.0.0.5", "224.0.0.6");

    private static final ImmutableSet<String> PIM_ADDRESSES = ImmutableSet.of("224.0.0.13");

    private static final ImmutableSet<String> MULTICAST_MANAGEMENT_ADDRESSES = ImmutableSet.of("224.0.0.1",
            "224.0.0.22");

    private final ImmutableCollection<SubnetUtils.SubnetInfo> testbedControlSubnets;

    // Polling
    private final long pollingInterval;
    private Timer pollingTimer = new Timer();
    private long[] time = new long[2]; // the time of the current and previous
                                       // polling

    // CPU and Memory Information
    private int processorCount; // number of processors in the machine
    private long totalMemory; // memory capacity of the machine in KB

    // CPU Usage
    private long[] totalCPURunning = new long[2]; // current and previous values
    private long[] totalCPUIdle = new long[2]; // current and previous values

    // Memory Usage
    private long usedMemory; // memory usage in KB

    // Network information
    private RoutingTable routingTable = new RoutingTable();
    private final Object routingTableLock = new Object();

    // (bridge -> [members], member -> bridge)
    private Pair<Map<NetworkInterface, Collection<NetworkInterface>>, Map<NetworkInterface, NetworkInterface>> bridgeInfo;

    /**
     * The bandwidth capability of each NIC in Megabits/sec.
     */
    private final Map<String, Double> networkCapacity = new HashMap<>();

    private final FileRegionLookupService regionLookupService;

    private final RegionIdentifier region;

    private final int apPort;

    /**
     * Stores the current and previous usage for a certain NIC.
     * 
     * @author awald
     *
     */
    class NICUsage {
        private long[] rxBytes = new long[2]; // current and previous values
        private long[] txBytes = new long[2]; // current and previous values

        /**
         * Add a new amount of data received to the history array.
         * 
         * @param bytes
         *            the amount of data received since the previous update in
         *            bytes
         */
        public void shiftInNewRxBytes(long bytes) {
            shiftValueIntoArray(rxBytes, bytes);
        }

        /**
         * Add a new amount of data sent to the history array.
         * 
         * @param bytes
         *            the amount of data sent since the previous update in bytes
         */
        public void shiftInNewTxBytes(long bytes) {
            shiftValueIntoArray(txBytes, bytes);
        }

        /**
         * Returns the amount of data received at a certain point in the
         * history.
         * 
         * @param n
         *            the amount of values back in history to retrieve: 0 -
         *            current value 1 - previous value
         * @return the amount of data received in bytes
         */
        public long getRxBytes(int n) {
            return rxBytes[n];
        }

        /**
         * Returns the amount of data sent at a certain point in the history.
         * 
         * @param n
         *            the amount of values back in history to retrieve: 0 -
         *            current value 1 - previous value
         * @return the amount of data sent in bytes
         */
        public long getTxBytes(int n) {
            return txBytes[n];
        }
    }

    /**
     * 
     * @param pollingInterval
     *            the interval at which to poll the /proc files for resource
     *            usage information in milliseconds
     * @param regionLookupService
     *            used to determine the region of network traffic
     * @param region
     *            the region of the node being monitored, used to determine
     *            local vs. remote traffic
     * @param apPort
     *            the port number that AP communicates on
     * @param testbedControlSubnets
     *            the subnets used by the testbed for control traffic
     */
    public NCPResourceMonitor(long pollingInterval,
            @Nonnull final RegionIdentifier region,
            @Nonnull final FileRegionLookupService regionLookupService,
            final int apPort,
            @Nonnull final ImmutableCollection<SubnetUtils.SubnetInfo> testbedControlSubnets) {
        this.pollingInterval = pollingInterval;
        this.regionLookupService = regionLookupService;
        this.region = region;
        this.apPort = apPort;
        this.testbedControlSubnets = testbedControlSubnets;

        updateCPUCount(FILE_CPU_CAPACITY);
        updateNetworkBandwidth(FILE_NETWORK_CAPACITY);
        updateNetworkRouteInformation(FILE_NETWORK_ROUTE_INFORMATION);

        startPolling();
    }

    private static boolean isDockerContainer(final NetworkInterface nic) {
        // TODO: find a better way to determine the docker interfaces other than
        // checking the name
        return nic.getName().startsWith("veth");
    }

    private static boolean isDockerBridge(final NetworkInterface nic) {
        // TODO: find a better way to determine the docker interfaces other than
        // checking the name
        return nic.getName().startsWith("docker");
    }

    private static Set<NetworkInterface> getAllNics() throws SocketException {
        final Set<NetworkInterface> allNics = new HashSet<>();
        final Enumeration<NetworkInterface> nicEnum = NetworkInterface.getNetworkInterfaces();

        while (nicEnum.hasMoreElements()) {
            final NetworkInterface nic = nicEnum.nextElement();
            allNics.add(nic);
        }
        return allNics;
    }

    /**
     * @return (bridge -> [members], member -> bridge)
     */
    @SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME", justification = "Need absolute path to find files on Linux")
    private static Pair<Map<NetworkInterface, Collection<NetworkInterface>>, Map<NetworkInterface, NetworkInterface>> findBridgeMembers(
            final Set<NetworkInterface> allNics) {
        final Map<NetworkInterface, Collection<NetworkInterface>> bridgeToMembers = new HashMap<>();
        final Map<NetworkInterface, NetworkInterface> memberToBridge = new HashMap<>();

        final Path sysNet = Paths.get("/sys/class/net");

        for (final NetworkInterface nic : allNics) {
            final Path bridgeDir = sysNet.resolve(nic.getName()).resolve("brif");
            if (Files.exists(bridgeDir)) {
                try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(bridgeDir)) {
                    for (Path file : dirStream) {
                        final Path fileName = file.getFileName();
                        if (null != fileName) {
                            final String memberName = fileName.toString();
                            final NetworkInterface member = allNics.stream().filter(m -> memberName.equals(m.getName()))
                                    .findFirst().orElse(null);
                            if (null != member) {
                                bridgeToMembers.computeIfAbsent(nic, k -> new LinkedList<>()).add(member);

                                memberToBridge.put(member, nic);
                            }
                        } // non-null fileName
                    } // foreach file
                } catch (IOException | DirectoryIteratorException e) {
                    log.warn("Unable to enumerate the interfaces for bridge {}: {}", nic.getName(), e.getMessage(), e);
                }
            }
        }

        return Pair.of(bridgeToMembers, memberToBridge);
    }

    private static InetAddress getAddress(final NetworkInterface nic,
            final Map<NetworkInterface, NetworkInterface> bridgeMembers) {
        // nic.getInterfaceAddresses().stream().map(InterfaceAddress::getAddress).forEach(i
        // -> {
        // System.out.println("\t\ta: " + i);
        // System.out.println("\t\t\t anyLocal: " + i.isAnyLocalAddress());
        // System.out.println("\t\t\t linkLocal: " + i.isLinkLocalAddress());
        // System.out.println("\t\t\t siteLocal: " + i.isSiteLocalAddress());
        // });

        final InetAddress addr = nic.getInterfaceAddresses().stream().map(InterfaceAddress::getAddress)
                .filter(i -> !i.isAnyLocalAddress() && !i.isLinkLocalAddress()).findFirst().orElse(null);

        if (null == addr) {
            final NetworkInterface bridgeIf = bridgeMembers.get(nic);
            if (null != bridgeIf) {
                return getAddress(bridgeIf, bridgeMembers);
            } else {
                return null;
            }
        } else {
            return addr;
        }
    }

    private Set<NetworkInterface> determineInterfacesToMonitor(final Set<NetworkInterface> allNics) {
        return allNics.stream()//
                // skip docker containers
                .filter(nic -> !isDockerContainer(nic)) //

                // skip docker bridges
                .filter(nic -> !isDockerBridge(nic))//

                // skip loopback and excluded addresses, unless AP is using the
                // control
                // network
                .filter(nic -> nic.getInterfaceAddresses().stream() //
                        .map(InterfaceAddress::getAddress) //
                        .noneMatch(this::isInterfaceInExcludedSubnets)) //

                // only include interfaces with IPv4 addresses or docker bridge
                // members
                .filter(this::hasIpv4AddressOrDockerBridgeMember) //

                .collect(Collectors.toSet());
    }

    /**
     * Check that the interface has an IPv4 address or is a member of a docker
     * bridge.
     */
    private boolean hasIpv4AddressOrDockerBridgeMember(final NetworkInterface nic) {
        for (final InterfaceAddress ia : nic.getInterfaceAddresses()) {
            if (ia.getAddress() instanceof Inet4Address) {
                log.trace("{} has an IPv4 address", nic);
                return true;
            }
        }

        // check if a member of a docker bridge
        final NetworkInterface bridgeIfce = getBridgeNameforNIC(nic);
        final boolean dockerBridge = null != bridgeIfce && isDockerBridge(bridgeIfce);
        log.trace("{} is a part of docker bridge? {}", nic, dockerBridge);
        return dockerBridge;
    }

    private final Map<NetworkInterface, BaseIftopProcessor> networkMonitors = new HashMap<>();

    /**
     * 
     * @return names of the monitored network interfaces
     */
    public Set<String> getMonitoredNetworkInterfaceNames() {
        return networkMonitors.entrySet().stream().map(Map.Entry::getKey).map(NetworkInterface::getName)
                .collect(Collectors.toSet());
    }

    private final Map<NetworkInterface, InetAddress> nicToIp = new HashMap<>();

    private void startNetworkMonitoring() {
        try {
            final Set<NetworkInterface> allNics = getAllNics();
            log.debug("All network interfaces: {}", allNics);

            // needs to be set before determineInterfacesToMonitor is executed
            bridgeInfo = findBridgeMembers(allNics);

            final Set<NetworkInterface> interfacesToMonitor = determineInterfacesToMonitor(allNics);
            log.debug("Interfaces to monitor: {}", interfacesToMonitor);

            final Map<NetworkInterface, NetworkInterface> memberToBridge = bridgeInfo.getRight();

            interfacesToMonitor.forEach(nic -> {
                final InetAddress addr = getAddress(nic, memberToBridge);
                final BaseIftopProcessor processor;
                if (AgentConfiguration.getInstance().getIftopUseCustom()) {
                    processor = new IftopProcessor(nic);
                } else {
                    processor = new OriginalIftopProcessor(nic);
                }
                processor.start();
                if (networkMonitors.containsKey(nic)) {
                    throw new RuntimeException(
                            "Unexpected situtation, there is already a monitor for the network interface " + nic);
                }

                nicToIp.put(nic, addr);
                networkMonitors.put(nic, processor);
            });

            log.debug("Created network monitors for: {}", networkMonitors);
            log.debug("IP mappings for NICs: {}", nicToIp);

        } catch (final SocketException e) {
            throw new RuntimeException("Error geting network interfaces: " + e.getMessage(), e);
        }
    }

    /**
     * Make sure that the local address in the returned object is nicAddress.
     * 
     * @throws UnknownHostException
     */
    private IftopTrafficData fixLocalRemote(final InetAddress nicAddress, final IftopTrafficData trafficData1)
            throws UnknownHostException {
        final InetAddress address1 = DnsUtils.getByName(trafficData1.getLocalIP());
        final InetAddress address2 = DnsUtils.getByName(trafficData1.getRemoteIP());

        final boolean flip;
        if (address1.isLoopbackAddress()) {
            flip = false;
        } else if (address2.isLoopbackAddress()) {
            flip = true;
        } else if (address1.equals(nicAddress)) {
            flip = false;
        } else if (address2.equals(nicAddress)) {
            // flip the addresses around so that "local"
            // is the address of the host being
            // monitored
            flip = true;
        } else {
            final RegionIdentifier address1Region = regionLookupService.getRegionForIp(address1.getHostAddress());
            final RegionIdentifier address2Region = regionLookupService.getRegionForIp(address2.getHostAddress());

            if (region.equals(address1Region)) {
                flip = false;
                return trafficData1;
            } else if (region.equals(address2Region)) {
                // Since the traffic isn't for this host, make anything in the
                // same region local.
                // This makes sure that the ResourceSummary objects are correct
                // for traffic into and out of the region.
                // Chances are this traffic is actually to one of the containers
                // on this host.
                flip = true;
            } else {
                // this is traffic that is just passing through, so local and
                // remote
                // don't matter
                flip = false;
                return trafficData1;
            }
        }

        if (flip) {
            final IftopTrafficData trafficData2 = new IftopTrafficData(trafficData1.getRemoteIP(),
                    trafficData1.getRemotePort(), trafficData1.getLocalIP(), trafficData1.getLocalPort(),
                    trafficData1.getLast2sBitsReceived(), trafficData1.getLast2sBitsSent(),
                    trafficData1.getInterface());
            return trafficData2;
        } else {
            return trafficData1;
        }
    }

    /**
     * Look at the traffic data and the application profile for the running
     * service to determine which end is the server.
     * 
     * @param apPort
     *            the port that AP uses to communicate on
     * @param trafficData
     *            the data to parse
     * @param controller
     *            used to get the service information for nodes
     * @return the flow and service information needed for a report
     */
    /* package */ static Pair<NodeNetworkFlow, ServiceIdentifier<?>> createNetworkFlow(final int apPort,
            @Nonnull final IftopTrafficData trafficData,
            @Nonnull final Controller controller) {

        // TODO: this may be able to be done more efficiently with some rework
        // of the data flow
        final NodeIdentifier sourceHost = IdentifierUtils.getNodeIdentifier(trafficData.getLocalIP());
        final int sourceHostPort = trafficData.getLocalPort();
        final ServiceIdentifier<?> sourceService = controller.getServiceForNode(sourceHost);

        final NodeIdentifier destHost = IdentifierUtils.getNodeIdentifier(trafficData.getRemoteIP());
        final int destHostPort = trafficData.getRemotePort();
        final ServiceIdentifier<?> destService = controller.getServiceForNode(destHost);

        ServiceIdentifier<?> service = null;
        NodeIdentifier serverHost = null;
        if (null == serverHost && null != sourceService) {
            final ApplicationSpecification spec = AppMgrUtils.getApplicationSpecification(sourceService);
            final int servicePort = spec.getServerPort();
            if (servicePort == sourceHostPort) {
                serverHost = sourceHost;
                service = sourceService;
            } else {
                log.warn("Source service {} port {} doesn't match sourceHostPort {} destHostPort {}", sourceService,
                        servicePort, sourceHostPort, destHostPort);
            }
        }

        if (null == serverHost && null != destService) {
            final ApplicationSpecification spec = AppMgrUtils.getApplicationSpecification(destService);
            final int servicePort = spec.getServerPort();
            if (servicePort == destHostPort) {
                serverHost = destHost;
                service = destService;
            } else {
                log.warn("Dest service {} port {} doesn't match destHostPort {} sourceHostPort {}", destService,
                        servicePort, destHostPort, sourceHostPort);
            }
        }

        // couldn't match to any managed service
        if (null == serverHost) {
            if (DNS_QUERY_PORT == sourceHostPort || DNS_QUERY_PORT == destHostPort) {
                service = MAPServices.DNS_QUERY;

                if (DNS_QUERY_PORT == sourceHostPort) {
                    serverHost = sourceHost;
                } else {
                    serverHost = destHost;
                }
            } else if (WeightedRecordMessageServer.PORT == sourceHostPort
                    || WeightedRecordMessageServer.PORT == destHostPort) {
                service = MAPServices.DNS_UPDATE;
                if (WeightedRecordMessageServer.PORT == sourceHostPort) {
                    serverHost = sourceHost;
                } else {
                    serverHost = destHost;
                }
            } else if (SimpleDockerResourceManager.DOCKER_REGISTRY_PORT == sourceHostPort
                    || SimpleDockerResourceManager.DOCKER_REGISTRY_PORT == destHostPort) {
                service = MAPServices.DOCKER;
                if (SimpleDockerResourceManager.DOCKER_REGISTRY_PORT == sourceHostPort) {
                    serverHost = sourceHost;
                } else {
                    serverHost = destHost;
                }
            } else if (OSPF_ADDRESSES.contains(trafficData.getLocalIP())
                    || OSPF_ADDRESSES.contains(trafficData.getRemoteIP())) {
                service = MAPServices.OSPF;
                serverHost = NodeIdentifier.UNKNOWN;
            } else if (SimDriver.PORT == sourceHostPort || SimDriver.PORT == destHostPort) {
                service = MAPServices.SIMULATION_DRIVER;
                if (SimDriver.PORT == sourceHostPort) {
                    serverHost = sourceHost;
                } else {
                    serverHost = destHost;
                }
            } else if (SimDriver.BACKGROUND_TRAFFIC_PORT == sourceHostPort
                    || SimDriver.BACKGROUND_TRAFFIC_PORT == destHostPort) {
                service = MAPServices.SIMULATION_DRIVER;
                if (SimDriver.BACKGROUND_TRAFFIC_PORT == sourceHostPort) {
                    serverHost = sourceHost;
                } else {
                    serverHost = destHost;
                }
            } else if (PIM_ADDRESSES.contains(trafficData.getLocalIP())
                    || PIM_ADDRESSES.contains(trafficData.getRemoteIP())) {
                service = MAPServices.PIM;
                serverHost = NodeIdentifier.UNKNOWN;
            } else if (MULTICAST_MANAGEMENT_ADDRESSES.contains(trafficData.getLocalIP())
                    || MULTICAST_MANAGEMENT_ADDRESSES.contains(trafficData.getRemoteIP())) {
                service = MAPServices.MULTICAST_MANAGEMENT;
                serverHost = NodeIdentifier.UNKNOWN;
            } else if (apPort == sourceHostPort || apPort == destHostPort) {
                // there isn't a server in AP communication
                serverHost = NodeIdentifier.UNKNOWN;
                service = ApplicationCoordinates.AP;
            } else {
                log.warn(
                        "Unable to associate traffic with a service between {}:{} ({}) and {}:{} ({}). Source service: {} Dest service: {}",
                        sourceHost, sourceHostPort, trafficData.getLocalIP(), destHost, destHostPort,
                        trafficData.getRemoteIP(), sourceService, destService);

                serverHost = NodeIdentifier.UNKNOWN;
                service = ApplicationCoordinates.UNMANAGED;
            }
        }

        return Pair.of(new NodeNetworkFlow(sourceHost, destHost, serverHost), service);
    }

    /**
     * The first key is the {@link NetworkInterface#getName()} of the NIC.
     * 
     * @param controller
     *            used to get the service information for nodes
     * @return network information for {@link ResourceReport#getNetworkLoad()}
     *         that needs to be converted from network interface to neighbor.
     */
    /* package */ Map<String, Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>> computeNetworkLoadPerNic(
            @Nonnull final Controller controller) {
        // NIC name -> flow -> service -> attribute -> value
        final Map<String, Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>>> networkLoad = new HashMap<>();

        networkMonitors.forEach((nic, monitor) -> {
            log.trace("Computing network load for {}", nic.getName());

            // always create the nic load map as this is expected downstream
            final Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>> nicLoad = networkLoad
                    .computeIfAbsent(nic.getName(), k -> new HashMap<>());

            gatherNetworkInformation(controller, nicToIp.get(nic), monitor, nicLoad);
        }); // foreach nic

        log.trace("computeNetworkLoadPerNic: networkLoad: {}", networkLoad);
        return networkLoad;
    }

    /**
     * Collect network information from the specified monitor and store it in
     * nicLoad.
     * 
     * @param controller
     *            used to create the flow
     * @param nicAddress
     *            the address of the interface being monitored
     * @param monitor
     *            the monitor to read information from
     * @param nicLoad
     *            where to store the data
     */
    /* package */ void gatherNetworkInformation(final Controller controller,
            final InetAddress nicAddress,
            final BaseIftopProcessor monitor,
            final Map<NodeNetworkFlow, Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>>> nicLoad) {
        final List<IftopTrafficData> trafficFrame = monitor.getLastIftopFrames();
        if (null != trafficFrame) {

            log.trace("Load for nic with address {} -> {}", nicAddress, trafficFrame);

            for (IftopTrafficData trafficData1 : trafficFrame) {
                log.trace("traffic data {}", trafficData1);

                try {
                    final IftopTrafficData correctedTrafficData = fixLocalRemote(nicAddress, trafficData1);

                    log.trace("corrected traffic data {}", correctedTrafficData);

                    final Pair<NodeNetworkFlow, ServiceIdentifier<?>> flowResult = createNetworkFlow(apPort,
                            correctedTrafficData, controller);

                    log.trace("computeNetworkLoadPerNic: flowResult: {}", flowResult);

                    final NodeNetworkFlow flow = flowResult.getLeft();
                    final ServiceIdentifier<?> service = flowResult.getRight();

                    final Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>> sourceLoad = nicLoad
                            .computeIfAbsent(flow, k -> new HashMap<>());

                    final long bitsSent = correctedTrafficData.getLast2sBitsSent();
                    final double tx = UnitConversions.bitsPerSecondToMegabitsPerSecond(bitsSent);

                    final long bitsReceived = correctedTrafficData.getLast2sBitsReceived();
                    final double rx = UnitConversions.bitsPerSecondToMegabitsPerSecond(bitsReceived);

                    log.trace("Nic{}: Adding network load for remote machine '{}': rx = {}, tx = {}", nicAddress,
                            flow.getDestination().getName(), rx, tx);
                    addNetworkLoad(sourceLoad, tx, rx, service);

                } catch (final UnknownHostException e) {
                    log.error("Unable to lookup host from traffic data: {}", e.getMessage(), e);
                }
            } // foreach data element
        } // if there is a traffic frame
        else {
            log.trace("No network information for {}", nicAddress);
        }
    }

    /**
     * Ensure that the correct link attributes are used and summed properly.
     * 
     * @param sourceLoad
     *            the load to add the values to
     * @param tx
     *            the transmit value
     * @param rx
     *            the receive value
     * @param service
     *            the service
     */
    private static void addNetworkLoad(final Map<ServiceIdentifier<?>, Map<LinkAttribute, Double>> sourceLoad,
            final double tx,
            final double rx,
            final ServiceIdentifier<?> service) {
        final Map<LinkAttribute, Double> serviceLoad = sourceLoad.computeIfAbsent(service, k -> new HashMap<>());

        serviceLoad.merge(LinkAttribute.DATARATE_RX, rx, Double::sum);
        serviceLoad.merge(LinkAttribute.DATARATE_TX, tx, Double::sum);
    }

    private void startPolling() {
        startNetworkMonitoring();

        pollingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                shiftValueIntoArray(time, System.currentTimeMillis());
                log.debug("time[0] = " + time[0] + ", time[1] = " + time[1] + ", delta = " + (time[0] - time[1]));

                updateCPUUsage(FILE_CPU_USAGE);
                updateMemoryUsage(FILE_MEMORY_USAGE);
                updateNetworkRouteInformation(FILE_NETWORK_ROUTE_INFORMATION);
            }
        }, 0, pollingInterval);
    }

    private void updateCPUCount(File file) {
        List<String> lines;
        try {
            lines = ProcFileParserUtils.readFile(file);
            processorCount = ProcFileParserUtils.countLinesStartingWith(lines, "processor");
        } catch (IOException e) {
            log.error("Unable to find cpu count in file: " + file);
            e.printStackTrace();
        }
    }

    private void updateNetworkBandwidth(File file) {
        final File[] files = file.listFiles();
        if (null == files) {
            log.warn("Invalid path {}", file);
            return;
        }

        for (File nicFolder : files) {
            if (nicFolder.isDirectory()) {
                final String nic = nicFolder.getName();

                final File speedFile = new File(nicFolder + FILE_NETWORK_CAPACITY_2);
                if (speedFile.exists()) {
                    try {
                        final List<String> lines = ProcFileParserUtils.readFile(speedFile);

                        if (lines.size() > 0) {
                            double speed = Long.parseLong(lines.get(0));
                            networkCapacity.put(nic, speed);
                        }
                    } catch (final IOException e) {
                        log.debug("Error reading speed file: {} ", speedFile, e);
                    }
                } else {
                    log.debug("Did not find speed file: {}", speedFile);
                }
            } else {
                log.trace("{} is not a directory", nicFolder);
            }
        }
        log.trace("New network capacities: {}", networkCapacity);
    }

    private void updateNetworkRouteInformation(File file) {
        log.debug("updateNetworkRouteInformation: start");

        try {
            List<String> lines = ProcFileParserUtils.readFile(file);
            int headerLineIndex = ProcFileParserUtils.getLineStartingWith(lines, "Iface");

            if (headerLineIndex >= 0) {
                String[] columnHeaders = ProcFileParserUtils.splitByWhiteSpace(lines.get(headerLineIndex));

                int nicColumn = ProcFileParserUtils.getStringIndex(columnHeaders, "Iface", true);
                int destinationColumn = ProcFileParserUtils.getStringIndex(columnHeaders, "Destination", true);
                int gatewayColumn = ProcFileParserUtils.getStringIndex(columnHeaders, "Gateway", true);
                int metricColumn = ProcFileParserUtils.getStringIndex(columnHeaders, "Metric", true);
                int maskColumn = ProcFileParserUtils.getStringIndex(columnHeaders, "Mask", true);

                // create new RoutingTable
                RoutingTable table = new RoutingTable();

                // read each entry from the file, parse it, and add it to the
                // table
                for (int l = 1; l < lines.size(); l++) {
                    String[] lineParts = ProcFileParserUtils.splitByWhiteSpace(lines.get(l));

                    if (nicColumn < lineParts.length && destinationColumn < lineParts.length
                            && gatewayColumn < lineParts.length && maskColumn < lineParts.length) {
                        String nic = lineParts[nicColumn];
                        String destination = lineParts[destinationColumn];
                        String gateway = lineParts[gatewayColumn];
                        String metric = lineParts[metricColumn];
                        String mask = lineParts[maskColumn];

                        table.addRow(nic, hexIPtoStringIP(destination), hexIPtoStringIP(gateway),
                                Integer.parseInt(metric), hexIPtoStringIP(mask));

                    }
                }

                synchronized (routingTableLock) {
                    routingTable = table;
                }

                log.debug("Updated routing table:\n{}", routingTable);
            } else {
                log.error("Could not find network route information in file: {}", file);
            }
        } catch (IOException e) {
            log.error("updateNetworkRouteInformation exception: \n{}", e);
        } catch (Exception e) {
            log.error("updateNetworkRouteInformation exception: \n{}", e);
        }
    }

    private String hexIPtoStringIP(String hexIP) {
        List<Integer> ipByteValues = new ArrayList<>();

        for (int n = 0; n < hexIP.length(); n += 2)
            ipByteValues.add(0, Integer.parseUnsignedInt(hexIP.substring(n, n + 2), 16));

        StringBuilder b = new StringBuilder();

        for (int n = 0; n < ipByteValues.size(); n++) {
            if (n > 0)
                b.append(".");

            b.append(ipByteValues.get(n));
        }

        log.debug("hexIPtoStringIP: Converted '{}' to '{}'.", hexIP, b);
        return b.toString();
    }

    private void updateCPUUsage(File file) {
        try {
            List<String> lines = ProcFileParserUtils.readFile(file);
            int cpuLineIndex = ProcFileParserUtils.getLineStartingWith(lines, "cpu ");

            if (cpuLineIndex >= 0) {
                String[] cpuLineParts = ProcFileParserUtils.splitByWhiteSpace(lines.get(cpuLineIndex));

                if (cpuLineParts.length >= 8) {
                    // CHECKSTYLE:OFF
                    long cpuUser = Long.parseLong(cpuLineParts[1]);
                    long cpuNice = Long.parseLong(cpuLineParts[2]);
                    long cpuSystem = Long.parseLong(cpuLineParts[3]);
                    long cpuIdle = Long.parseLong(cpuLineParts[4]);
                    long cpuIOWait = Long.parseLong(cpuLineParts[5]);
                    long cpuIRQ = Long.parseLong(cpuLineParts[6]);
                    long cpuSoftIRQ = Long.parseLong(cpuLineParts[7]);
                    // CHECKSTYLE:ON

                    // calculate total CPU usage cycles
                    long cpuRunning = cpuUser + cpuNice + cpuSystem + cpuIOWait + cpuIRQ + cpuSoftIRQ;

                    // store current CPU values
                    shiftValueIntoArray(totalCPURunning, cpuRunning);
                    shiftValueIntoArray(totalCPUIdle, cpuIdle);
                } else {
                    log.error("Unable to find cpu usage stats columns in file: {}", file);
                }
            } else {
                log.error("Unable to find cpu usage stats line in file: {}", file);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void updateMemoryUsage(File file) {
        long memTotal = -1;
        long memFree = -1;

        try {
            List<String> lines = ProcFileParserUtils.readFile(file);

            int memTotalIndex = ProcFileParserUtils.getLineStartingWith(lines, "MemTotal");
            int memFreeIndex = ProcFileParserUtils.getLineStartingWith(lines, "MemFree");

            if (memTotalIndex >= 0 && memFreeIndex >= 0) {
                String[] lineParts;

                lineParts = ProcFileParserUtils.splitByWhiteSpace(lines.get(memTotalIndex));

                if (lineParts.length >= 3) {
                    log.debug("Memory total units: {}", lineParts[2]); // kB is
                                                                       // expected

                    if (!lineParts[2].equals("kB")) {
                        log.error("Memory units 'kB' expected");
                        return;
                    }

                    memTotal = Long.parseLong(lineParts[1]);
                }

                lineParts = ProcFileParserUtils.splitByWhiteSpace(lines.get(memFreeIndex));

                if (lineParts.length >= 3) {
                    log.debug("Memory free units: {}", lineParts[2]); // kB is
                                                                      // expected

                    if (!lineParts[2].equals("kB")) {
                        log.error("Memory units 'kB' expected");
                        return;
                    }

                    memFree = Long.parseLong(lineParts[1]);
                }
            } else {
                log.error("Unable to find memory usage data in {}", file);
            }

            if (memTotal >= 0) {
                totalMemory = memTotal;

                if (memFree >= 0)
                    usedMemory = totalMemory - memFree;
            }

        } catch (IOException e) {
            log.error("Failed to read memory usage file: {}", file);
            e.printStackTrace();
        }

    }

    /**
     *
     * @return the number of processors in the system
     */
    public int getProcessorCount() {
        return processorCount;
    }

    /**
     *
     * @return the total memory on the system in GB
     */
    public double getMemoryCapacity() {
        return (kBToGB(totalMemory)); // convert KB to GB
    }

    /**
     * Obtain the bandwidth of an NIC.
     *
     * @param nic
     *            the name of the NIC to return the bandwidth of
     * @return the capacity of the given network interface in megabits/sec
     */
    public double getNetworkBandwidth(String nic) {
        return networkCapacity.getOrDefault(nic, 0D);
    }

    /**
     *
     * @return CPU usage as a decimal value from 0 to 1
     */
    public float getCPUUsage() {
        long deltaCpuRunning = totalCPURunning[0] - totalCPURunning[1];
        long deltaCpuTotal = (totalCPURunning[0] + totalCPUIdle[0]) - (totalCPURunning[1] + totalCPUIdle[1]);

        return deltaCpuRunning * 1.0F / deltaCpuTotal;
    }

    /**
     * 
     * @return the amount of memory used on the system in GB
     */
    public double getMemoryUsage() {
        return (kBToGB(usedMemory)); // convert KB to GB
    }

    /**
     * 
     * @return the RoutingTable for this NCP
     */
    public RoutingTable getRoutingTable() {
        synchronized (routingTableLock) {
            return routingTable;
        }
    }

    private static double kBToGB(long kB) {
        return (kB * 1.0 / KILOBYTES_TO_BYTES / KILOBYTES_TO_BYTES);
    }

    // shifts a value into index 0 of an array after shifting every other
    // element to
    // the next index
    private void shiftValueIntoArray(long[] array, long value) {
        for (int n = array.length - 1; n > 0; n--)
            array[n] = array[n - 1];

        array[0] = value;
    }

    /**
     * Finds the physical NIC names for the bridge with the given name or null
     * if nic is not a bridge.
     * 
     * @param nic
     *            the name of the NIC
     * @return the physical NIC names within nic if nic is a bridge or null
     *         otherwise
     */
    public Set<String> getPysicalNICNamesForBridge(final String nic) {
        Set<String> physicalNICNames = null;

        for (Map.Entry<NetworkInterface, Collection<NetworkInterface>> entry : bridgeInfo.getLeft().entrySet()) {
            if (entry.getKey().getName().equals(nic)) {
                physicalNICNames = new HashSet<>();

                for (NetworkInterface bridgeNic : entry.getValue())
                    physicalNICNames.add(bridgeNic.getName());

                break;
            }
        }

        return physicalNICNames;
    }

    /**
     * If the specified NIC is part of a bridge, return it. Otherwise return
     * null
     * 
     * @param nic
     *            the network interface to see if it is associated with a bridge
     * @return the bridge interface or null
     */
    public NetworkInterface getBridgeNameforNIC(final NetworkInterface nic) {
        return bridgeInfo.getRight().get(nic);
    }

    private static final String MAP_CONTROL_NETWORK = String.format("%d.%d.0.0/16", HiFiAgent.MAP_CONTROL_FIRST_OCTET,
            HiFiAgent.MAP_CONTROL_SECOND_OCTET);
    private static final SubnetInfo MAP_CONTROL_SUBNET = new SubnetUtils(MAP_CONTROL_NETWORK).getInfo();

    /**
     * 
     * @param addr
     *            the address on the interface to check
     * @return true if this address should be ignored (loopback or excluded
     *         subnet)
     */
    private boolean isInterfaceInExcludedSubnets(final InetAddress addr) {
        if (addr.isLoopbackAddress()) {
            return true;
        } else if (addr instanceof Inet6Address) {
            // don't make a decision based on an IPv6 address as we don't know
            // how to check them
            return false;
        } else {
            final String ip = addr.getHostAddress();

            if (!AgentConfiguration.getInstance().getMonitorTestbedControlNetwork()) {
                final boolean excluded = testbedControlSubnets.stream().anyMatch(subnet -> subnet.isInRange(ip));
                if (excluded) {
                    return true;
                }
            }

            if (!AgentConfiguration.getInstance().getMonitorMapControlNetwork()) {
                if (MAP_CONTROL_SUBNET.isInRange(ip)) {
                    return true;
                }
            }
        }

        // not excluded
        return false;
    }

}
