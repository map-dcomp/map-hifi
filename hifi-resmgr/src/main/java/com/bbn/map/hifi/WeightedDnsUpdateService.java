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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.MutableApplicationManagerApi;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.dns.DNSUpdateService;
import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.map.hifi.dns.WeightedCNAMERecord;
import com.bbn.map.hifi.dns.WeightedRecordList;
import com.bbn.map.hifi.dns.WeightedRecordMessageServer;
import com.bbn.map.hifi.dns.WeightedRoundRobinResolver;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.ContainerParameters;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AtomicDouble;

/**
 * DNS update service that talks to {@link WeightedRoundRobinResolver}.
 * 
 * @author jschewe
 *
 */
public class WeightedDnsUpdateService implements DNSUpdateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeightedDnsUpdateService.class);

    private final RegionIdentifier region;
    private final InetAddress serverAddress;
    private final Object lock = new Object();

    /**
     * 
     * @param serverAddress
     *            the DNS server to modify
     * @param region
     *            the region that this dns updater is for
     */
    public WeightedDnsUpdateService(@Nonnull final RegionIdentifier region, final InetAddress serverAddress) {
        this.serverAddress = serverAddress;
        this.region = region;
    }

    /** create service.map.dcomp -> service.region.map.dcomp */
    private void addServiceToRegion(final List<WeightedRecordList> newRecords,
            final ServiceIdentifier<?> service,
            final int ttl) {
        final ApplicationSpecification appSpec = AppMgrUtils.getApplicationManager()
                .getApplicationSpecification((ApplicationCoordinates) service);
        Objects.requireNonNull(appSpec, "Could not find application specification for: " + service);

        final String fullHostname = DnsUtils.getFullyQualifiedServiceHostname(appSpec);

        final String regionHostname = getServiceRegionName(service);

        final WeightedCNAMERecord weightedRecord = new WeightedCNAMERecord(fullHostname, regionHostname, ttl, 1);
        final WeightedRecordList weightedRecordList = new WeightedRecordList(fullHostname,
                Collections.singletonList(weightedRecord));

        newRecords.add(weightedRecordList);
    }

    @Override
    public boolean replaceAllRecords(final ImmutableCollection<Pair<DnsRecord, Double>> records) {
        if (null == records) {
            LOGGER.warn("Given a null set of DNS records, ignoring.");
            return true;
        }

        synchronized (lock) {
            final Map<ServiceIdentifier<?>, Map<String, AtomicDouble>> serviceRecordWeights = new HashMap<>();

            // Use the minimum TTL across all of the DNS records. In MAP all of
            // the
            // TTLs should be the same anyway.
            final AtomicInteger minTtl = new AtomicInteger(Integer.MAX_VALUE);

            records.forEach(pair -> {
                final DnsRecord r = pair.getLeft();
                final double recordWeight = pair.getRight();
                final ServiceIdentifier<?> service = r.getService();
                final Map<String, AtomicDouble> recordWeights = serviceRecordWeights.computeIfAbsent(service,
                        k -> new HashMap<>());

                final String alias = getDestinationForRecord(r);
                recordWeights.computeIfAbsent(alias, k -> new AtomicDouble()).addAndGet(recordWeight);

                minTtl.getAndUpdate(prev -> Math.min(prev, r.getTtl()));
            });

            final List<WeightedRecordList> newRecords = new LinkedList<>();
            serviceRecordWeights.forEach((service, recordWeights) -> {
                addServiceToRegion(newRecords, service, minTtl.get());

                final String thisRegionHostname = getServiceRegionName(service);

                final WeightedRecordList weightedRecordList = new WeightedRecordList(thisRegionHostname,
                        Collections.emptyList());

                recordWeights.forEach((alias, weight) -> {
                    final WeightedCNAMERecord weightedRecord = new WeightedCNAMERecord(thisRegionHostname, alias,
                            minTtl.get(), weight.get());
                    weightedRecordList.addRecord(weightedRecord, weightedRecord.getWeight());
                });

                newRecords.add(weightedRecordList);
            });

            return sendRecords(newRecords);
        }
    }

    /**
     * Send the records to the configured DNS server.
     */
    private boolean sendRecords(final Collection<WeightedRecordList> records) {
        LOGGER.trace("Top of send records");
        try (Socket socket = new Socket(serverAddress, WeightedRecordMessageServer.PORT);
                Reader reader = new InputStreamReader(socket.getInputStream(), Charset.defaultCharset());
                Writer writer = new OutputStreamWriter(socket.getOutputStream(), Charset.defaultCharset())) {

            LOGGER.trace("Finished connecting to server");

            final ObjectMapper jsonMapper = JsonUtils.getStandardMapObjectMapper()
                    .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE).disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

            jsonMapper.writeValue(writer, WeightedRecordMessageServer.UPDATE_COMMAND);
            jsonMapper.writeValue(writer, records);
            writer.flush();

            final WeightedRecordMessageServer.Reply reply = jsonMapper.readValue(reader,
                    WeightedRecordMessageServer.Reply.class);

            if (!reply.isSuccess()) {
                LOGGER.error("Got error response from DNS server: {}", reply.getMessage());
                return false;
            } else {
                LOGGER.trace("Got reply {}", reply);
                return true;
            }

        } catch (final IOException e) {
            LOGGER.error("Got error sending records to the server", e);
            return false;
        }
    }

    private String getDestinationForRecord(final DnsRecord record) {
        if (record instanceof NameRecord) {
            final NameRecord nameRecord = (NameRecord) record;
            return getAliasForNameRecord(nameRecord);
        } else if (record instanceof DelegateRecord) {
            final DelegateRecord delegateRecord = (DelegateRecord) record;
            return getAliasForDelegateRecord(delegateRecord);
        } else {
            throw new RuntimeException("Unknown DnsRecord type: " + record.getClass());
        }
    }

    private String getAliasForNameRecord(final NameRecord record) {
        final NodeIdentifier serviceNode = record.getNode();

        return serviceNode.getName();
    }

    private String getAliasForDelegateRecord(final DelegateRecord record) {
        final ServiceIdentifier<?> service = record.getService();
        final String delegateRegionHostname = getServiceRegionName(service, record.getDelegateRegion());

        return delegateRegionHostname;
    }

    private String getServiceRegionName(final ServiceIdentifier<?> service) {
        return getServiceRegionName(service, region);
    }

    /**
     * Get the FQDN of the service in the specified region.
     */
    private static String getServiceRegionName(final ServiceIdentifier<?> service, final RegionIdentifier region) {
        final ApplicationSpecification appSpec = AppMgrUtils.getApplicationManager()
                .getApplicationSpecification((ApplicationCoordinates) service);
        Objects.requireNonNull(appSpec, "Could not find application specification for: " + service);

        final String baseHostname = appSpec.getServiceHostname(); // no dot

        final String regionHostname = String.format("%s.%s", baseHostname, getRegionZone(region));
        return regionHostname;
    }

    /**
     * Get the name of the zone for the specified region.
     */
    private static String getRegionZone(final RegionIdentifier region) {
        final String regionZone = String.format("%s.%s", region.getName(), DnsUtils.MAP_TLD);
        return regionZone;
    }

}
