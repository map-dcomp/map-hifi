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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final int UNKNOWN_REGION_CACHE_SIZE = 3000;
    private final UnknownRegionCache unknownRegionCache = new UnknownRegionCache(UNKNOWN_REGION_CACHE_SIZE);

    private static final int REGION_CACHE_SIZE = UNKNOWN_REGION_CACHE_SIZE;
    private final IpRegionCache ipRegionCache = new IpRegionCache(REGION_CACHE_SIZE);

    private final NodeRegionCache nodeRegionCache = new NodeRegionCache(REGION_CACHE_SIZE);

    /**
     * @param subnetToRegion
     *            subnet to region data
     */
    public FileRegionLookupService(final ImmutableMap<SubnetUtils.SubnetInfo, RegionIdentifier> subnetToRegion) {
        this.subnetToRegion = subnetToRegion;
    }

    @Override
    @Nonnull
    public RegionIdentifier getRegionForNode(@Nonnull final NodeIdentifier nodeId) {
        if (NodeIdentifier.UNKNOWN.equals(nodeId)) {
            return RegionIdentifier.UNKNOWN;
        }

        final RegionIdentifier cachedRegionForNode = nodeRegionCache.get(nodeId);
        if (null != cachedRegionForNode) {
            return cachedRegionForNode;
        }

        final NodeIdentifier canonicalId = IdentifierUtils.getCanonicalIdentifier(nodeId);
        final RegionIdentifier cachedRegionForCanonical = nodeRegionCache.get(canonicalId);
        if (null != cachedRegionForCanonical) {
            return cachedRegionForCanonical;
        }

        final String name = canonicalId.getName();
        if (NodeIdentifier.UNKNOWN.equals(canonicalId)) {
            return RegionIdentifier.UNKNOWN;
        } else if (name.startsWith("[")) {
            LOGGER.trace("Ignoring bogus node names from simple docker resource manager network management: " + name);
            final RegionIdentifier region = RegionIdentifier.UNKNOWN;
            nodeRegionCache.put(nodeId, region);
            nodeRegionCache.put(canonicalId, region);
            return region;
        } else {
            try {
                final String ipAddr = DnsUtils.getPrimaryIp(name);
                LOGGER.trace("Converted node id {} to ip {}", name, ipAddr);

                final RegionIdentifier region = getRegionForIp(ipAddr);
                nodeRegionCache.put(nodeId, region);
                nodeRegionCache.put(canonicalId, region);
                return region;
            } catch (final UnknownHostException e) {
                if (unknownRegionCache.add(canonicalId.getName())) {
                    // only log if we haven't logged it recently
                    LOGGER.error("Error looking up address: {}, returning UNKNOWN region", canonicalId.getName(), e);
                }
                // don't cache as this may be a temporary state
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
            final RegionIdentifier cachedRegionForIp = ipRegionCache.get(ipAddr);
            if (null != cachedRegionForIp) {
                return cachedRegionForIp;
            }

            final String primaryIp = getPrimaryIp(ipAddr);
            final RegionIdentifier cachedRegionForPrimary = ipRegionCache.get(primaryIp);
            if (null != cachedRegionForPrimary) {
                return cachedRegionForPrimary;
            }

            final Map.Entry<SubnetUtils.SubnetInfo, RegionIdentifier> entry = subnetToRegion.entrySet().stream()
                    .filter(e -> e.getKey().isInRange(primaryIp)).findFirst().orElse(null);
            final RegionIdentifier region;
            if (null == entry) {
                if (isMulticastAddress(primaryIp)) {
                    // multicast addresses typically don't have regions
                    LOGGER.trace("No region for multicast address {}", ipAddr);
                } else {
                    LOGGER.error("Could not find region for {} or {}", ipAddr, primaryIp);
                }
                region = RegionIdentifier.UNKNOWN;
            } else {
                region = entry.getValue();
            }
            ipRegionCache.put(ipAddr, region);
            ipRegionCache.put(primaryIp, region);
            return region;
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("Invalid IP address {}. Returning UNKNOWN as the region.", ipAddr);
            return RegionIdentifier.UNKNOWN;
        }
    }

    private static final class UnknownRegionCache extends LinkedHashSet<String> {
        private static final long serialVersionUID = 1L;

        private final int cacheSize;

        /**
         * @param cacheSize
         *            size of the cache
         */
        UnknownRegionCache(final int cacheSize) {
            this.cacheSize = cacheSize;
        }

        @Override
        public boolean add(final String e) {
            final Iterator<String> it = this.iterator();
            while (size() >= this.cacheSize) {
                it.next();
                it.remove();
            }
            return super.add(e);
        }

    }

    private static final class NodeRegionCache extends LinkedHashMap<NodeIdentifier, RegionIdentifier> {
        private static final long serialVersionUID = 1L;

        private final int cacheSize;
        private static final float LOAD_FACTOR = 0.75f;

        NodeRegionCache(final int cacheSize) {
            super(cacheSize, LOAD_FACTOR, true);
            this.cacheSize = cacheSize;
        }

        @Override
        protected boolean removeEldestEntry(final Map.Entry<NodeIdentifier, RegionIdentifier> eldest) {
            return size() > this.cacheSize;
        }
    }

    private static final class IpRegionCache extends LinkedHashMap<String, RegionIdentifier> {
        private static final long serialVersionUID = 1L;

        private final int cacheSize;
        private static final float LOAD_FACTOR = 0.75f;

        IpRegionCache(final int cacheSize) {
            super(cacheSize, LOAD_FACTOR, true);
            this.cacheSize = cacheSize;
        }

        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, RegionIdentifier> eldest) {
            return size() > this.cacheSize;
        }
    }
}