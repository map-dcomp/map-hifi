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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Contains routing information for a Linux machine.
 * 
 * @author awald
 *
 */
public class RoutingTable {
    private static Logger log = LogManager.getLogger(RoutingTable.class);

    private List<RoutingTableRow> routingTableRows = new ArrayList<>();

    /**
     * Adds a row to the routing table.
     * 
     * @param nic
     *            the name of the network interface
     * @param destination
     *            the IP address of the destination
     * @param gateway
     *            the IP address of the gateway
     * @param metric
     *            the priority metric
     * @param mask
     *            the mask of the destination subnet
     */
    public void addRow(String nic, String destination, String gateway, int metric, String mask) {
        RoutingTableRow row = new RoutingTableRow(nic, destination, gateway, metric, mask);
        routingTableRows.add(row);
        log.debug("add row: {}", row);
    }

    private Comparator<RoutingTableRow> routingComparator = new Comparator<RoutingTableRow>() {
        /**
         * Compares rows a and b of the RoutingTable. Prioritizes the row with a
         * more specific (larger) mask. If the mask sizes are the same,
         * prioritizes the row with the smaller metric value.
         */
        @Override
        public int compare(RoutingTableRow a, RoutingTableRow b) {
            int relativeSpecificity = a.getMaskSize() - b.getMaskSize();

            if (relativeSpecificity != 0)
                return relativeSpecificity;
            else
                return b.getMetric() - a.getMetric();
        }
    };

    /**
     * 
     * @param address
     *            the address to find a matching NIC for
     * @return the name of the NIC that packets destined for the given
     *         InetAddress address are routed to or null
     */
    public String route(InetAddress address) {
        return route(address.getHostAddress());
    }

    /**
     * 
     * @param address
     *            the IP address to find a matching NIC for
     * @return the name of the NIC that packets destined for the given
     *         InetAddress address are routed to or null
     */
    public String route(final String address) {
        List<RoutingTableRow> routingTableRowsForAddress = routingTableRows.stream()
                .filter(row -> row.matchesSubnet(address)).collect(Collectors.toList());
        Optional<RoutingTableRow> selectedRoutingTableRowForAddress = routingTableRowsForAddress.stream()
                .max(routingComparator);

        log.debug(
                "route: address = {}, routingTableRows = {}, routingTableRowsForAddress: {}, "
                        + "selectedRoutingTableRowForAddress: {}",
                address, routingTableRows, routingTableRowsForAddress, selectedRoutingTableRowForAddress);

        if (!selectedRoutingTableRowForAddress.isPresent()) {
            log.error("route: No NIC found for address {} in routing table with entries {}", address, routingTableRows);
            return null;
        } else {
            return selectedRoutingTableRowForAddress.get().getNic();
        }
    }

    static final class RoutingTableRow {
        private String nic;

        private String destination;
        private String gateway;
        private int metric;
        private String mask;
        private int maskSize;
        private SubnetUtils subnetUtils;

        RoutingTableRow(String nic, String destination, String gateway, int metric, String mask) {
            this.nic = nic;
            this.destination = destination;
            this.gateway = gateway;
            this.metric = metric;
            this.mask = mask;
            maskSize = maskToMaskSize(mask);
            subnetUtils = new SubnetUtils(destination, mask);
            // needed otherwise matching an address in a /32 doesn't work due to
            // broadcast and network address
            // https://issues.apache.org/jira/browse/NET-675
            subnetUtils.setInclusiveHostCount(true);
        }

        boolean matchesSubnet(final String hostAddress) {
            final boolean result = subnetUtils.getInfo().isInRange(hostAddress);
            log.trace("Checking if {} is in {} -> {}", hostAddress, subnetUtils.getInfo(), result);
            return result;
        }

        private int maskToMaskSize(String mask) {
            String[] parts = mask.split(Pattern.quote("."));

            int bits = 0;

            for (String part : parts)
                bits = (bits << 8) | Integer.parseInt(part);

            int size = 0;

            while (bits != 0) {
                bits <<= 1;
                size++;
            }

            return size;
        }

        public String getNic() {
            return nic;
        }

        public String getDestination() {
            return destination;
        }

        public String getGateway() {
            return gateway;
        }

        public int getMetric() {
            return metric;
        }

        public String getMask() {
            return mask;
        }

        public int getMaskSize() {
            return maskSize;
        }

        public SubnetUtils getSubnetUtils() {
            return subnetUtils;
        }

        @Override
        public String toString() {
            return (nic + ", " + destination + ", " + gateway + ", " + metric + ", " + mask + " (size: " + getMaskSize()
                    + ")");
        }
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();

        b.append("NIC, Destination, Gateway, Metric, Mask");

        for (RoutingTableRow row : routingTableRows) {
            b.append("\n");
            b.append(row);
        }

        return b.toString();
    }

}
