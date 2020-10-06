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
package com.bbn.map.hifi;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.commons.net.util.SubnetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Address;

import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.hifi.util.IdentifierUtils;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionLookupService;
import com.google.common.collect.ImmutableMap;

/**
 * Region lookup service based on a file of subnet to region mappings.
 * 
 * @author jschewe
 *
 */
public final class FileRegionLookupService implements RegionLookupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileRegionLookupService.class);

    private final ImmutableMap<SubnetUtils.SubnetInfo, RegionIdentifier> subnetToRegion;

    /**
     * @param subnetToRegion
     *            subnet to region data
     */
    public FileRegionLookupService(final ImmutableMap<SubnetUtils.SubnetInfo, RegionIdentifier> subnetToRegion) {
        this.subnetToRegion = subnetToRegion;
    }

    @Override
    public RegionIdentifier getRegionForNode(@Nonnull final NodeIdentifier nodeId) {
        final NodeIdentifier canonicalId = IdentifierUtils.getCanonicalIdentifier(nodeId);

        final String name = canonicalId.getName();
        if (NodeIdentifier.UNKNOWN.equals(canonicalId)) {
            return RegionIdentifier.UNKNOWN;
        } else if (name.startsWith("[")) {
            LOGGER.trace("Ignoring bogus node names from simple docker resource manager network management: " + name);
            return RegionIdentifier.UNKNOWN;
        } else {
            try {
                final String ipAddr = DnsUtils.getPrimaryIp(name);
                LOGGER.trace("Converted node id {} to ip {}", name, ipAddr);

                return getRegionForIp(ipAddr);

            } catch (final UnknownHostException e) {
                LOGGER.error("Error looking up address: {}, returning null region", canonicalId.getName(), e);
                return RegionIdentifier.UNKNOWN;
            }
        }
    }

    private static String getPrimaryIp(final String ipAddr) {
        try {
            return DnsUtils.getPrimaryIp(ipAddr);
        } catch (final UnknownHostException e) {
            LOGGER.error("Unable to find primary IP for {}", ipAddr);
            return ipAddr;
        }
    }

    private static boolean isMulticastAddress(final String ipAddr) {
        try {
            final InetAddress addr = Address.getByAddress(ipAddr);
            return addr.isMulticastAddress();
        } catch (final UnknownHostException e) {
            LOGGER.debug("Got error checking for multicast address", e);
            return false;
        }
    }

    /**
     * Get the region for an IP address.
     * 
     * @param ipAddr
     *            the IP address to check
     * @return the region or null if no region can be found for the IP, or the
     *         string is not a valid IP address
     */
    public RegionIdentifier getRegionForIp(final String ipAddr) {
        try {
            final String primaryIp = getPrimaryIp(ipAddr);

            final Map.Entry<SubnetUtils.SubnetInfo, RegionIdentifier> entry = subnetToRegion.entrySet().stream()
                    .filter(e -> e.getKey().isInRange(primaryIp)).findFirst().orElse(null);
            if (null == entry) {
                if (isMulticastAddress(primaryIp)) {
                    // multicast addresses typically don't have regions
                    LOGGER.trace("No region for multicast address {}", ipAddr);
                } else {
                    LOGGER.error("Could not find region for {}", ipAddr);
                }
                return null;
            } else {
                return entry.getValue();
            }
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("Invalid IP address {}. Returning null as the region.", ipAddr);
            return null;
        }
    }
}