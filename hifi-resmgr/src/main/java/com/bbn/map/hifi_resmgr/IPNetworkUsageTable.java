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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Stores information about traffic between a container and a region at a
 * certain point in time.
 * 
 * @author awald
 *
 */
public class IPNetworkUsageTable {
    private static Logger log = LogManager.getLogger(IPNetworkUsageTable.class);

    // Container -> Region -> regionTraffic
    private Map<String, Map<String, RegionTraffic>> ipNetworkUsage = new HashMap<>();

    /**
     */
    IPNetworkUsageTable() {
    }

    /**
     * Add network traffic information for a communication between a client and
     * a container.
     * 
     * @param container
     *            the name of the local container receiving and sending data
     * @param region
     *            the name of the region or group to which the machine that is
     *            communicating with the container belongs
     * @param client
     *            the name or IP address of the remote machine that is
     *            communicating with the container
     * @param bitsPerSecondSent
     *            the amount of data that this container is currently sending to
     *            the remote machine in bits per second
     * @param bitsPerSecondReceived
     *            the amount of data that this container is currently receiving
     *            from the remote machine in bits per second
     */
    public void addToRegionTraffic(String container,
            String region,
            String client,
            long bitsPerSecondSent,
            long bitsPerSecondReceived) {
        if (!ipNetworkUsage.containsKey(container))
            ipNetworkUsage.put(container, new HashMap<>());

        if (!ipNetworkUsage.get(container).containsKey(region))
            ipNetworkUsage.get(container).put(region, new RegionTraffic());

        log.debug("addToRegionTraffic container=" + container + ", region=" + region + " for client '" + client
                + "' sent: " + bitsPerSecondSent + ", received: " + bitsPerSecondReceived);

        ipNetworkUsage.get(container).get(region).addClientTraffic(client, bitsPerSecondSent, bitsPerSecondReceived);
    }

    /**
     * Return the rate of data being sent from a container to a region.
     * 
     * @param container
     *            the name of the container
     * @param region
     *            the name of the region
     * @return the rate of data transfer in bits per second
     */
    public long getBitsPerSecondSent(String container, String region) {
        return ipNetworkUsage.get(container).get(region).getTotalBitsPerSecondSent();
    }

    /**
     * Return the rate of data being received by a container from a region.
     * 
     * @param container
     *            the name of the container
     * @param region
     *            the name of the region
     * @return the rate of data transfer in bits per second
     */
    public long getBitsPerSecondReceived(String container, String region) {
        return ipNetworkUsage.get(container).get(region).getTotalBitsPerSecondReceived();
    }

    /**
     * Return the rate of data being sent from a container to a particular
     * client in a region.
     * 
     * @param container
     *            the name of the container
     * @param region
     *            the name of the region
     * @param client
     *            the name of the client in the region
     * @return the rate of data transfer in bits per second
     */
    public long getBitsPerSecondSent(String container, String region, String client) {
        return ipNetworkUsage.get(container).get(region).getClientTraffic(client).getBitsPerSecondSent();
    }

    /**
     * Return the rate of data being received by a container from a particular
     * client in a region.
     * 
     * @param container
     *            the name of the container
     * @param region
     *            the name of the region
     * @param client
     *            the name of the client in the region
     * @return the rate of data transfer in bits per second
     */
    public long getBitsPerSecondReceived(String container, String region, String client) {
        return ipNetworkUsage.get(container).get(region).getClientTraffic(client).getBitsPerSecondReceived();
    }

    /**
     * Obtain the set of names of regions that are communicating with a
     * particular container.
     * 
     * @param container
     *            the name of the container
     * @return the set of names of regions that are communicating with the
     *         container
     */
    public Set<String> getRegionsCommunicatingWithContainer(String container) {
        if (ipNetworkUsage.containsKey(container))
            return ipNetworkUsage.get(container).keySet();
        else
            return new HashSet<>();
    }

    static final class RegionTraffic {
        private long totalBitsPerSecondSent = 0;
        private long totalBitsPerSecondReceived = 0;

        private Map<String, ClientTraffic> clientTraffic = new HashMap<>();

        public void addClientTraffic(String clientIP, long bitsPerSecondSent, long bitsPerSecondReceived) {
            totalBitsPerSecondReceived += bitsPerSecondReceived;
            totalBitsPerSecondSent += bitsPerSecondSent;

            clientTraffic.put(clientIP, new ClientTraffic(bitsPerSecondSent, bitsPerSecondReceived));
        }

        public Set<String> getClientIPs() {
            return clientTraffic.keySet();
        }

        public ClientTraffic getClientTraffic(String clientIP) {
            return clientTraffic.get(clientIP);
        }

        public long getTotalBitsPerSecondSent() {
            return totalBitsPerSecondSent;
        }

        public long getTotalBitsPerSecondReceived() {
            return totalBitsPerSecondReceived;
        }

        @Override
        public String toString() {
            return ("total: (sent=" + totalBitsPerSecondSent + "bps" + ", received=" + totalBitsPerSecondReceived
                    + "bps)" + "  clients: " + clientTraffic.toString());
        }
    }

    static final class ClientTraffic {
        private long bitsPerSecondSent;
        private long bitsPerSecondReceived;

        ClientTraffic(long bitsPerSecondSent, long bitsPerSecondReceived) {
            this.bitsPerSecondSent = bitsPerSecondSent;
            this.bitsPerSecondReceived = bitsPerSecondReceived;
        }

        public long getBitsPerSecondSent() {
            return bitsPerSecondSent;
        }

        public long getBitsPerSecondReceived() {
            return bitsPerSecondReceived;
        }

        @Override
        public String toString() {
            return "(sent=" + bitsPerSecondSent + "bps" + ", received=" + bitsPerSecondReceived + "bps)";
        }
    }

    @Override
    public String toString() {
        return ipNetworkUsage.toString();
    }
}
