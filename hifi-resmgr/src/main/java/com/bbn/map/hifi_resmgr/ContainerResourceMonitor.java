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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Class to monitor Docker container resource usage.
 * 
 * @author awald
 *
 */

public class ContainerResourceMonitor {
    private static Logger log = LogManager.getLogger(ContainerResourceMonitor.class);

    private String baseURL;

    /**
     * The number of CPUs in one nano CPU.
     */
    public static final long CPUS_TO_NANO_CPUS = 1_000_000_000;

    private Map<NodeIdentifier, ContainerMonitorThread> monitorThreads = new HashMap<>();

    /**
     * @param baseURL
     *            The beginning of the URL for making Docker requests with the
     *            protocol, domain or IP address, and port
     */
    public ContainerResourceMonitor(String baseURL) {
        this.baseURL = baseURL;
    }

    /**
     * 
     * @param container
     *            the name of the Docker container to retrieve resource
     *            statistics for
     * @return the current resource statistics for the container
     */
    public ContainerResourceStats getContainerResourceStats(NodeIdentifier container) {
        log.debug("start getContainerResourceStat({})", container);
        if (monitorThreads.containsKey(container))
            return monitorThreads.get(container).getContainerResourceStats();
        else
            return null;

    }

    /**
     * 
     * @return the set of names of the Docker containers that are currently
     *         being monitored
     */
    public Set<NodeIdentifier> getMonitoredContainerIDs() {
        return monitorThreads.keySet();
    }

    /**
     * Gets JSON text by sending a request to a certain URL.
     * 
     * @param url
     *            the URL to send the request to
     * @return the JSON response
     * 
     * @throws IOException
     *             if there was an issue establishing a connection or reading
     *             data
     */
    public String getJSON(URL url) throws IOException {
        try {
            HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(httpConnection.getInputStream(), Charset.defaultCharset()))) {
                StringBuilder response = new StringBuilder();
                String line = reader.readLine();

                while (line != null) {
                    log.debug("getJSON: read line: {}", line);
                    response.append(line);

                    line = reader.readLine();
                }

                return response.toString();
            }
        } catch (MalformedURLException e) {
            log.error("Invalid URL", e);
            return null;
        }
    }

    /**
     * Start a monitor thread for a Docker container.
     * 
     * @param containerID
     *            the name that Docker has assigned to the container
     * @param nic
     *            the name of the NIC in the Docker container to monitor
     */
    public void startMonitorForContainer(NodeIdentifier containerID, String nic) {
        startMonitorForContainer(containerID, nic, null);
    }

    // starts a monitor thread for a container
    private void startMonitorForContainer(NodeIdentifier containerID,
            String nic,
            ContainerResourceStatsHandler handler) {
        // stops a monitor thread for the given container if one was already
        // started
        if (monitorThreads.containsKey(containerID)) {
            log.info("Attempting to stop container with ID: " + containerID);

            monitorThreads.get(containerID).stopMonitor();
            monitorThreads.remove(containerID);
        }

        // creates and starts a new monitor thread
        ContainerMonitorThread cmt = new ContainerMonitorThread(containerID, nic);
        cmt.setStatsHandler(handler);
        monitorThreads.put(containerID, cmt);
        cmt.start();
    }

    /**
     * Stops a monitor thread for a container.
     * 
     * @param containerID
     *            the name that Docker has assigned to the container to stop
     *            monitoring
     */
    public void stopMonitorForContainer(NodeIdentifier containerID) {
        if (monitorThreads.containsKey(containerID)) {
            log.info("Attempting to stop container with ID: " + containerID);

            monitorThreads.get(containerID).stopMonitor();
            monitorThreads.remove(containerID);
        } else {
            log.error("The container with name '" + containerID + "' cannot be stopped because it does not exist.");
        }
    }

    // Thread for monitoring one container
    private class ContainerMonitorThread extends Thread {
        private volatile boolean running = false;

        private ContainerResourceStatsHandler handler = null;
        private ContainerResourceStats newestResourceStats = null;
        private NodeIdentifier container;
        private String nic;

        private long prevTxBytes = 0; // -1;
        private long prevRxBytes = 0; // -1;

        private long[] time = new long[2];

        ContainerMonitorThread(NodeIdentifier container, String nic) {
            this.container = container;
            this.nic = nic;
        }

        public void setStatsHandler(ContainerResourceStatsHandler handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            super.run();

            running = true;

            try {
                monitorContainerStats(container, nic);
            } catch (IOException e) {
                log.error("Could not monitor stats for container: {}", container, e);
            }
        }

        // returns relevant container resource usage stats given the name or id
        // of a
        // container
        public void monitorContainerStats(NodeIdentifier container, String nic) throws IOException {
            URL usageStatsURL = new URL(baseURL + "/containers/" + container.getName() + "/stats");
            URL inspectURL = new URL(baseURL + "/containers/" + container.getName() + "/json");

            String inspectJSON = "";
            inspectJSON = getJSON(inspectURL);

            try {
                HttpURLConnection httpConnection = (HttpURLConnection) usageStatsURL.openConnection();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(httpConnection.getInputStream(), Charset.defaultCharset()))) {
                    String line = reader.readLine();

                    while (running && line != null) {
                        inspectJSON = getJSON(inspectURL);
                        newestResourceStats = getContainerResourceStatsFromJSON(line, inspectJSON, nic);
                        log.debug("monitorContainerStats: New stats for container '{}': {}", container,
                                newestResourceStats);

                        if (handler != null)
                            handler.handleStats(container, newestResourceStats);

                        line = reader.readLine();
                    }
                }

            } catch (MalformedURLException e) {
                log.error("Invalid URL", e);
            }
        }

        // parses the JSON that stores container stats and stores the results in
        // a
        // ContainerResourceStats object
        private ContainerResourceStats getContainerResourceStatsFromJSON(String usageJSON,
                String inspectJSON,
                String nic) {
            try {
                final ObjectMapper jsonParser = JsonUtils.getStandardMapObjectMapper();
                final JsonNode usageJsonObj = jsonParser.readTree(usageJSON);

                // Capacity
                Long memoryCapacity = null;
                Long nanoCpusCapacity = null;

                String status = null;

                if (inspectJSON != null) {
                    final JsonNode inspectJSONObj = jsonParser.readTree(inspectJSON);

                    if (inspectJSONObj != null) {
                        JsonNode hostConfig = inspectJSONObj.get("HostConfig");

                        if (hostConfig != null) {
                            memoryCapacity = hostConfig.get("Memory").asLong();
                            nanoCpusCapacity = hostConfig.get("NanoCpus").asLong();

                            log.debug("getContainerResourceStatsFromJSON: Memory: " + memoryCapacity
                                    + ",   CPU Capacity: " + nanoCpusCapacity);
                        }

                        JsonNode state = inspectJSONObj.get("State");

                        if (state != null) {
                            status = state.get("Status").asText();
                            log.debug("getContainerResourceStatsFromJSON: Read status for container '{}': {}",
                                    container, status);
                        }
                    }
                }

                // CPU Usage
                Long totalUsage = null;
                Long systemCPUUsage = null;
                Long onlineCPUs = null;
                Long preTotalUsage = null;
                Long preSystemCPUUsage = null;

                JsonNode cpuStats = usageJsonObj.get("cpu_stats");
                JsonNode precpuStats = usageJsonObj.get("precpu_stats");

                if (cpuStats != null) {
                    final JsonNode cpuUsage = cpuStats.get("cpu_usage");

                    if (cpuUsage != null) {
                        final JsonNode temp = cpuUsage.get("total_usage");
                        if (null != temp) {
                            totalUsage = temp.asLong();
                        }
                    }

                    final JsonNode systemCpuUsageJson = cpuStats.get("system_cpu_usage");
                    if (null != systemCpuUsageJson) {
                        systemCPUUsage = systemCpuUsageJson.asLong();
                    }
                    final JsonNode onlineCpusJson = cpuStats.get("online_cpus");
                    if (null != onlineCpusJson) {
                        onlineCPUs = onlineCpusJson.asLong();
                    }
                }

                if (precpuStats != null) {
                    JsonNode precpuUsage = precpuStats.get("cpu_usage");

                    if (precpuUsage != null) {
                        final JsonNode temp = precpuUsage.get("total_usage");
                        if (null != temp) {
                            preTotalUsage = temp.asLong();
                        }
                    }

                    final JsonNode systemCpuUsageJson = precpuStats.get("system_cpu_usage");
                    if (null != systemCpuUsageJson) {
                        preSystemCPUUsage = systemCpuUsageJson.asLong();
                    }
                }

                // Compute CPU usage in CPUs
                double cpus;

                if (totalUsage == null || preTotalUsage == null || systemCPUUsage == null || preSystemCPUUsage == null
                        || onlineCPUs == null) {
                    log.warn("Missing information, unable to compute CPU statistics JSON: {}", usageJSON);
                    cpus = Double.NaN;
                } else {
                    // docker computes CPU usage percentage using the code here
                    // https://github.com/moby/moby/blob/eb131c5383db8cac633919f82abad86c99bffbe5/cli/command/container/stats_helpers.go#L175-L188

                    final double cpuDelta = totalUsage - preTotalUsage;
                    final double systemDelta = systemCPUUsage - preSystemCPUUsage;
                    // This is the value output by "docker stats" / 100.
                    // Based on experimentation this value is the number of host CPUs that are busy.
                    // So if a container is given 0.5 CPUs and it is fully busy, this value is 0.5.
                    // This is consistent with the CPU capacity, so no more math is needed.
                    final double percentageOfOneHostCPU = (cpuDelta / systemDelta) * onlineCPUs;

                    cpus = percentageOfOneHostCPU;
                    if (Double.isInfinite(cpus)) {
                        // NaN or Infinity
                        cpus = 0;
                        log.warn(
                                "Found infinite CPU load. totalUsage: {} preTotalUsage: {} systemCPUUsage: {} preSystemCPUUsage: {} json: {}",
                                totalUsage, preTotalUsage, systemCPUUsage, preSystemCPUUsage, usageJsonObj);
                    } else if (Double.isNaN(cpus)) {
                        log.error(
                                "Found NaN CPU load. totalUsage: {} preTotalUsage: {} systemCPUUsage: {} preSystemCPUUsage: {} json: {}",
                                totalUsage, preTotalUsage, systemCPUUsage, preSystemCPUUsage, usageJsonObj);
                    }
                }

                // Memory Usage
                Long memoryUsage = null;
                final JsonNode memoryStats = usageJsonObj.get("memory_stats");
                if (memoryStats != null) {
                    final JsonNode usage = memoryStats.get("usage");
                    if(null != usage) {
                        memoryUsage = usage.asLong();
                    }
                }

                // Network usage
                Long rxBytes = null, txBytes = null;
                final JsonNode networks = usageJsonObj.get("networks");

                if (networks != null) {
                    JsonNode network = networks.get(nic);
                    if(null != network) {
                        final JsonNode rx = network.get("rx_bytes");
                        if(null != rx) {
                        rxBytes = rx.asLong();
                        }
                        final JsonNode tx = network.get("rx_bytes");
                        if(null != tx) {                        
                            txBytes = tx.asLong();
                        }
                    }
                }

                // // initialize prevRxBytes and prevTxBytes on the first sample
                // if (prevRxBytes < 0)
                // prevRxBytes = (rxBytes != null ? rxBytes : 0);
                //
                // if (prevTxBytes < 0)
                // prevTxBytes = (txBytes != null ? txBytes : 0);

                // construct ContainerResourceStats object
                // record the current time
                shiftValueIntoArray(time, System.currentTimeMillis());

                ContainerResourceStats crs = new ContainerResourceStats();

                crs.setComputeUsage(cpus, (memoryUsage != null ? memoryUsage : 0));

                if (rxBytes != null && txBytes != null) {
                    crs.setNetworkUsage(nic, rxBytes - prevRxBytes, txBytes - prevTxBytes);
                    prevRxBytes = rxBytes;
                    prevTxBytes = txBytes;
                }

                if (nanoCpusCapacity != null && memoryCapacity != null) {
                    crs.setComputeCapacity(nanoCpusCapacity * 1.0 / CPUS_TO_NANO_CPUS, memoryCapacity);
                }

                if (status != null)
                    crs.setStatus(status);

                log.debug("getContainerResourceStatsFromJSON: return crs: {}", crs);
                return crs;
            } catch (final IOException e) {
                log.error("getContainerResourceStatsFromJSON: Error", e);
            }

            return null;
        }

        public void stopMonitor() {
            running = false;
        }

        public ContainerResourceStats getContainerResourceStats() {
            return newestResourceStats;
        }
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
     * Interface for objects that should be periodically notified to handle a
     * resource usage updates for a Docker container.
     * 
     * @author awald
     *
     */
    public interface ContainerResourceStatsHandler {
        /**
         * Process a ContainerResourceStats object for a certain container.
         * 
         * @param containerID
         *            the name that Docker has assigned to the container
         * @param stats
         *            the most recent resource usage update for the container
         */
        void handleStats(NodeIdentifier containerID, ContainerResourceStats stats);
    }
}
