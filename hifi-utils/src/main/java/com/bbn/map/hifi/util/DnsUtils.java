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
package com.bbn.map.hifi.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.Nonnull;

import org.apache.commons.io.LineIterator;
import org.checkerframework.checker.lock.qual.Holding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Address;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.TextParseException;
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
     * TLD for names in the MAP network.
     */
    public static final String MAP_TLD = "map.dcomp";

    /**
     * TLD for names on the MAP control network.
     */
    public static final String MAP_CONTROL_TLD = "map-control.dcomp";

    private DnsUtils() {
    }

    private static final Pattern IP_ADDR_RE = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");

    /**
     * Convert nameOrIp to a name. If the name doesn't resolve, this method will
     * attempt to resolve by adding {@link #MAP_TLD} to the name.
     */
    private static String getName(final String nameOrIp) throws UnknownHostException {
        try {
            final InetAddress addr = getByName(nameOrIp);
            return trimDotFromHostName(Address.getHostName(addr));
        } catch (final UnknownHostException e) {
            // if the name doesn't end with the MAP_TLD and it's not already an
            // IP address, append MAP_TLD
            if (!nameOrIp.endsWith(MAP_TLD) && !IP_ADDR_RE.matcher(nameOrIp).find()) {
                final String longName = String.format("%s.%s", nameOrIp, MAP_TLD);

                LOGGER.debug("Hostname {} wasn't found, attempting lookup with MAP tld appended: {}", nameOrIp,
                        longName);

                final InetAddress addr2 = getByName(longName);
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
        final String wellKnown = getWellKnownName(nameOrIp);
        if (null != wellKnown) {
            return wellKnown;
        } else {
            final String name = getName(nameOrIp);
            return name;
        }
    }

    /**
     * The primary IP address for an NCP is the one that the canonical name maps
     * to. This method will add {@link #MAP_TLD} if needed to resolve the name.
     * 
     * @param nameOrIp
     *            the name or IP address of an NCP
     * @return the primary IP address for the NCP
     * @throws UnknownHostException
     *             if there is a problem finding the node in DNS
     * @see #getCanonicalName(String)
     */
    public static String getPrimaryIp(final String nameOrIp) throws UnknownHostException {
        final String wellKnown = getWellKnownIp(nameOrIp);
        if (null != wellKnown) {
            return wellKnown;
        } else {
            final String canonicalName = getCanonicalName(nameOrIp);
            final InetAddress addr = getByName(canonicalName);
            return addr.getHostAddress();
        }
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

        final int dnsLookupTimeoutSeconds = 30; // default in dnsjava is 5
        Lookup.getDefaultResolver().setTimeout(dnsLookupTimeoutSeconds);

        final int cacheSeconds = 0; // SimulationRunner.TTL;

        // CNAME records are used for services, we don't want these cached for
        // long
        Lookup.getDefaultCache(Type.CNAME).setMaxNCache(cacheSeconds);
        Lookup.getDefaultCache(Type.CNAME).setMaxCache(cacheSeconds);

        // Reduce "A" record negative timeout since a timeout getting any record
        // will be cached
        // as an "A:"record.
        Lookup.getDefaultCache(Type.A).setMaxNCache(cacheSeconds);

        // Reduce "PTR" record negative timeout in case there is a timeout doing
        // a reverse lookup
        Lookup.getDefaultCache(Type.PTR).setMaxNCache(cacheSeconds);

        // don't use the system search domains
        // this is intended to avoid lookups based on the testbed domain, which
        // we don't expect to see in our experiments
        try {
            Lookup.setDefaultSearchPath((String[]) null);
        } catch (final TextParseException e) {
            throw new RuntimeException("Internal error, there are no names to parse", e);
        }
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

    /**
     * Read /etc/map/node_control_names. If the file doesn't exist return an
     * empty map. The node name returned is the base node name without any
     * domain components, ie. nodeA rather than nodeA.map.dcomp.
     * 
     * @return node name -> testbed control network name
     */
    @Nonnull
    public static Map<String, String> parseNodeControlNames() {
        final Path input = Paths.get("/etc/map/node_control_names");
        if (!Files.exists(input)) {
            LOGGER.warn("Cannot find {}. Node access will be across the experiment network.", input);
            return Collections.emptyMap();
        }

        final Pattern hostSectionPattern = Pattern.compile("\\[host_(.*)_map\\]");
        try (BufferedReader reader = Files.newBufferedReader(input)) {
            final Map<String, String> mapping = new HashMap<>();

            String nodeName = null;
            try (LineIterator it = new LineIterator(reader)) {
                while (it.hasNext()) {
                    final String line = it.next();
                    final Matcher match = hostSectionPattern.matcher(line);
                    if (match.find()) {
                        nodeName = match.group(1);
                    } else {
                        if (null != nodeName && line.startsWith(nodeName)) {
                            mapping.put(nodeName, line.trim());

                            // don't overwrite the mapping.
                            nodeName = null;
                        }
                    }

                }
            } catch (final IllegalStateException e) {
                LOGGER.error("Error reading node control names file. Returning partial map.", e);
                return mapping;
            }

            return mapping;
        } catch (final IOException e) {
            LOGGER.error("Error reading node control names file. Returning empty map.", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Filename of hostnames to cache indefinitely.
     */
    public static final String HOSTS_TO_CACHE_FILENAME = "hosts_to_cache.txt";

    private static void parseHostsToCache(final Map<String, InetAddress> nameToIp, final Map<String, String> ipToName) {
        final Path input = Paths.get("/etc/map").resolve(HOSTS_TO_CACHE_FILENAME);
        if (!Files.exists(input)) {
            LOGGER.warn("Cannot find {}. Cache will not have experiment nodes.", input);
        }

        try (BufferedReader reader = Files.newBufferedReader(input)) {
            try (LineIterator it = new LineIterator(reader)) {
                while (it.hasNext()) {
                    final String line = it.next();
                    final String[] tokens = line.split("\\s+");
                    if (tokens.length != 2) {
                        LOGGER.warn("Expected 2 tokens, but found {} on '{}'. Igoring line.", tokens.length, line);
                    } else {
                        final String name = tokens[0];
                        final InetAddress ip = Address.getByAddress(tokens[1]);
                        nameToIp.put(name, ip);
                        ipToName.merge(ip.getHostAddress(), name, DnsUtils::preferExperimentNetwork);
                    }
                }
            } catch (final IllegalStateException e) {
                LOGGER.error("Error reading hosts to cache file. Using partial data.", e);
            }
        } catch (final IOException e) {
            LOGGER.error("Error reading hosts to cache. No data cached.", e);
        }
    }

    private static String preferExperimentNetwork(final String oldValue, final String newValue) {
        if (oldValue.endsWith(MAP_CONTROL_TLD)) {
            return newValue;
        } else {
            return oldValue;
        }
    }

    private static Pattern localhostIpPattern;
    static {
        try {
            localhostIpPattern = Pattern.compile("^127(\\.\\d{1,3}){3}$");
        } catch (final PatternSyntaxException e) {
            LOGGER.error("Localhost IP pattern is invalid", e);
            localhostIpPattern = null;
        }
    }
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final String LOCALHOST_NAME = "localhost";

    /**
     * 
     * @param nameOrIp
     *            name or IP address to lookup
     * @return if a well known name or IP address, the name, otherwise null
     */
    private static String getWellKnownName(final String nameOrIp) {
        ensureCacheBuilt();
        if (null != localhostIpPattern && localhostIpPattern.matcher(nameOrIp).matches()) {
            return LOCALHOST_NAME;
        } else if (LOCALHOST_NAME.equals(nameOrIp)) {
            return LOCALHOST_NAME;
        } else if (cacheNameToIp.containsKey(nameOrIp)) {
            return nameOrIp;
        } else if (cacheIpToName.containsKey(nameOrIp)) {
            return cacheIpToName.get(nameOrIp);
        } else {
            if (!nameOrIp.endsWith(MAP_TLD) && !IP_ADDR_RE.matcher(nameOrIp).find()) {
                final String longName = String.format("%s.%s", nameOrIp, MAP_TLD);
                if (cacheNameToIp.containsKey(longName)) {
                    return longName;
                }
            }
            return null;
        }
    }

    /**
     * 
     * @param nameOrIp
     *            name or IP address to find the IP for
     * @return if a well known IP address or name, return it, otherwise return
     *         null
     */
    private static String getWellKnownIp(final String nameOrIp) {
        ensureCacheBuilt();

        if (null != localhostIpPattern && localhostIpPattern.matcher(nameOrIp).matches()) {
            return LOCALHOST_IP;
        } else if (LOCALHOST_NAME.equals(nameOrIp)) {
            return LOCALHOST_NAME;
        } else if (cacheNameToIp.containsKey(nameOrIp)) {
            return cacheNameToIp.get(nameOrIp).getHostAddress();
        } else if (cacheIpToName.containsKey(nameOrIp)) {
            return nameOrIp;
        } else {
            if (!nameOrIp.endsWith(MAP_TLD) && !IP_ADDR_RE.matcher(nameOrIp).find()) {
                final String longName = String.format("%s.%s", nameOrIp, MAP_TLD);
                if (cacheNameToIp.containsKey(longName)) {
                    return cacheNameToIp.get(longName).getHostAddress();
                }
            }
            return null;
        }
    }

    private static final Object CACHE_LOCK = new Object();
    private static boolean cacheBuilt = false;
    private static Map<String, InetAddress> cacheNameToIp;
    private static Map<String, String> cacheIpToName;

    private static void ensureCacheBuilt() {
        if (!cacheBuilt) {
            // only lock if we don't believe the cache is built

            synchronized (CACHE_LOCK) {
                if (!cacheBuilt) {
                    buildNameCache();
                    cacheBuilt = true;
                }
            }
        }
    }

    @Holding("cacheLock")
    private static void buildNameCache() {
        final Map<String, InetAddress> nameToIp = new HashMap<>();
        final Map<String, String> ipToName = new HashMap<>();

        parseHostsToCache(nameToIp, ipToName);

        cacheNameToIp = Collections.unmodifiableMap(nameToIp);
        cacheIpToName = Collections.unmodifiableMap(ipToName);
    }

    // start end methods from dnsjava. The code is copied and modified enough to
    // provide more information in the unknown host exception
    /**
     * Version of {@link Address#getByName(String)} that provides more
     * information when the lookup fails.
     * 
     * @param name
     *            name to lookup
     * @return the address
     * @throws UnknownHostException
     *             if the lookup failed
     */
    public static InetAddress getByName(final String name) throws UnknownHostException {
        ensureCacheBuilt();

        if (cacheNameToIp.containsKey(name)) {
            return cacheNameToIp.get(name);
        }
        if (!name.endsWith(MAP_TLD) && !IP_ADDR_RE.matcher(name).find()) {
            final String longName = String.format("%s.%s", name, MAP_TLD);
            if (cacheNameToIp.containsKey(longName)) {
                return cacheNameToIp.get(longName);
            }
        }

        try {
            return Address.getByAddress(name);
        } catch (final UnknownHostException e) {
            final Record[] records = lookupHostName(name, false);
            return addrFromRecord(name, records[0]);
        }
    }

    private static Record[] lookupHostName(final String name, final boolean all) throws UnknownHostException {
        try {
            final Lookup lookup = new Lookup(name, Type.A);
            final Record[] a = lookup.run();
            if (a == null) {
                if (lookup.getResult() == Lookup.TYPE_NOT_FOUND) {
                    final Record[] aaaa = new Lookup(name, Type.AAAA).run();
                    if (aaaa != null) {
                        return aaaa;
                    }
                }
                final String message = String.format("Unknown host: %s result: %d error string: %s", name,
                        lookup.getResult(), lookup.getErrorString());
                throw new UnknownHostException(message);
            }
            if (!all) {
                return a;
            }
            final Record[] aaaa = new Lookup(name, Type.AAAA).run();
            if (aaaa == null) {
                return a;
            }
            final Record[] merged = new Record[a.length + aaaa.length];
            System.arraycopy(a, 0, merged, 0, a.length);
            System.arraycopy(aaaa, 0, merged, a.length, aaaa.length);
            return merged;
        } catch (final TextParseException e) {
            throw new UnknownHostException("invalid name: " + name);
        }
    }

    private static InetAddress addrFromRecord(final String name, final Record r) throws UnknownHostException {
        InetAddress addr;
        if (r instanceof ARecord) {
            addr = ((ARecord) r).getAddress();
        } else {
            addr = ((AAAARecord) r).getAddress();
        }
        return InetAddress.getByAddress(name, addr.getAddress());
    }
    // end methods from dnsjava

}
