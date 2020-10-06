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
package com.bbn.map.hifi.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Address;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Type;

import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.simulator.SimulationRunner;

/**
 * Some utilities for working with DNS.
 * 
 * @author jschewe
 *
 */
public final class DnsUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DnsUtils.class);

    /**
     * TLD for names in the map network.
     */
    public static final String MAP_TLD = "map.dcomp";

    private DnsUtils() {
    }

    /**
     * Convert nameOrIp to a name. If the name doesn't resolve, this method will
     * attempt to resolve by adding {@link #MAP_TLD} to the name.
     */
    private static String getName(final String nameOrIp) throws UnknownHostException {
        try {
            final InetAddress addr = Address.getByName(nameOrIp);
            return trimDotFromHostName(Address.getHostName(addr));
        } catch (final UnknownHostException e) {
            if (!nameOrIp.endsWith(MAP_TLD)) {
                final String longName = String.format("%s.%s", nameOrIp, MAP_TLD);

                LOGGER.debug("Hostname {} wasn't found, attempting lookup with MAP tld appended: {}", nameOrIp,
                        longName);

                final InetAddress addr2 = Address.getByName(longName);
                return trimDotFromHostName(Address.getHostName(addr2));
            } else {
                throw e;
            }
        }
    }

    /**
     * DnsJava will put a trailing dot on FQDNs, we don't want this.
     */
    private static String trimDotFromHostName(final String name) {
        if (null != name && name.length() > 0 && name.charAt(name.length() - 1) == '.') {
            return name.substring(0, name.length() - 1);
        } else {
            return name;
        }

    }

    /**
     * Convert a name or IP address to the canonical hostname. This is the name
     * of the node, container, etc.. For objects that are in the topology file
     * this is the name that was specified in the topology file. This method
     * will add {@link #MAP_TLD} if needed to resolve the name.
     * 
     * @param nameOrIp
     *            a hostname or ip address
     * @return the canonical hostname
     * @throws UnknownHostException
     *             if there is a problem doing a lookup in DNS
     */
    public static String getCanonicalName(final String nameOrIp) throws UnknownHostException {
        final String name = getName(nameOrIp);
        final InetAddress addr = Address.getByName(name);
        final String canonicalName = trimDotFromHostName(Address.getHostName(addr));
        return canonicalName;
    }

    /**
     * The primary IP address for an NCP is the one that the canonical name maps
     * to.This method will add {@link #MAP_TLD} if needed to resolve the name.
     * 
     * @param nameOrIp
     *            the name or IP address of an NCP
     * @return the primary IP address for the NCP
     * @throws UnknownHostException
     *             if there is a problem finding the node in DNS
     * @see #getCanonicalName(String)
     */
    public static String getPrimaryIp(final String nameOrIp) throws UnknownHostException {
        final String canonicalName = getCanonicalName(nameOrIp);
        final InetAddress addr = Address.getByName(canonicalName);
        return addr.getHostAddress();
    }

    /**
     * Check if label is a valid part of a domain name. This is any piece
     * between the dots.
     * 
     * @param label
     *            the label to check
     * @return true if the label is valid
     */
    public static boolean isValidDomainLabel(final String label) {
        final Pattern onePattern = Pattern.compile("^\\p{Alnum}[\\p{Alnum}\\-]{0,61}\\p{Alnum}|\\p{Alpha}$");
        final Matcher oneMatcher = onePattern.matcher(label);

        final Pattern excludeAllNumbersPattern = Pattern.compile("^.*[^\\p{Digit}].*$");
        final Matcher excludeMatcher = excludeAllNumbersPattern.matcher(label);

        return oneMatcher.matches() && excludeMatcher.matches();
    }

    /**
     * The DnsJava package caches negative DNS entries to improve performance.
     * Unfortunately this doesn't work well with MAP when things aren't up yet
     * and when we're changing the DNS entries rather quickly. The cache time
     * limit is set to {@link SimulationRunner#TTL}.
     */
    public static void configureDnsCache() {
        LOGGER.info("Configuring DNS caching");

        // these are the types of DNS records that are likely to be changing
        // within MAP
        Lookup.getDefaultCache(Type.A).setMaxNCache(SimulationRunner.TTL);
        Lookup.getDefaultCache(Type.PTR).setMaxNCache(SimulationRunner.TTL);
        Lookup.getDefaultCache(Type.CNAME).setMaxNCache(SimulationRunner.TTL);

        // disable positive cache in dnsjava
        Lookup.getDefaultCache(Type.A).setMaxCache(SimulationRunner.TTL);
        Lookup.getDefaultCache(Type.PTR).setMaxCache(SimulationRunner.TTL);
        Lookup.getDefaultCache(Type.CNAME).setMaxCache(SimulationRunner.TTL);

        LOGGER.info("dnsjava max N cache A: {} PTR: {} CNAME: {}", Lookup.getDefaultCache(Type.A).getMaxNCache(),
                Lookup.getDefaultCache(Type.PTR).getMaxNCache(), Lookup.getDefaultCache(Type.CNAME).getMaxNCache());

        LOGGER.info("dnsjava max cache A: {} PTR: {} CNAME: {}", Lookup.getDefaultCache(Type.A).getMaxCache(),
                Lookup.getDefaultCache(Type.PTR).getMaxCache(), Lookup.getDefaultCache(Type.CNAME).getMaxCache());
    }

    /**
     * 
     * @param appSpec
     *            application specification
     * @return the fully qualified domain name for the service
     */
    public static String getFullyQualifiedServiceHostname(@Nonnull final ApplicationSpecification appSpec) {
        final String fullHostname = String.format("%s.%s", appSpec.getServiceHostname(), DnsUtils.MAP_TLD);
        return fullHostname;
    }
}
