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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.PlanTranslatorRecurse;
import com.bbn.map.hifi.dns.RecordUpdateMessage;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.simulator.SimulationRunner;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.google.common.collect.ImmutableCollection;

/**
 * Implementation of {@link WeightedDnsUpdateService} used with
 * {@link PlanTranslatorRecurse}.
 * 
 * @author jschewe
 *
 */
public class WeightedDnsUpdateServiceRecursive extends WeightedDnsUpdateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeightedDnsUpdateServiceRecursive.class);

    /**
     * 
     * @param serverAddress
     *            see
     *            {@link WeightedDnsUpdateService#WeightedDnsUpdateService(RegionIdentifier, InetAddress)}
     * @param region
     *            see
     *            {@link WeightedDnsUpdateService#WeightedDnsUpdateService(RegionIdentifier, InetAddress)}
     */
    public WeightedDnsUpdateServiceRecursive(@Nonnull final RegionIdentifier region, final InetAddress serverAddress) {
        super(region, serverAddress);
    }

    @Override
    public boolean replaceAllRecords(ImmutableCollection<Pair<DnsRecord, Double>> records) {
        if (null == records) {
            LOGGER.warn("Given a null set of DNS records, ignoring.");
            return true;
        }

        synchronized (getLock()) {
            final Map<ServiceIdentifier<?>, Map<String, Double>> serviceRecordWeights = new HashMap<>();

            // Use the minimum TTL across all of the DNS records. In MAP all of
            // the TTLs should be the same anyway.
            final int minTtl = records.stream() //
                    .mapToInt(pair -> {
                        // this mapToInt is a little abuse of the streams API as
                        // it's creating side-effects in addition to the mapping

                        final DnsRecord r = pair.getLeft();
                        final double recordWeight = pair.getRight();
                        final ServiceIdentifier<?> service = r.getService();
                        final Map<String, Double> recordWeights = serviceRecordWeights.computeIfAbsent(service,
                                k -> new HashMap<>());

                        final String alias = getDestinationForRecord(r);
                        recordWeights.merge(alias, recordWeight, Double::sum);

                        return r.getTtl();
                    }) //
                    .min().orElse(SimulationRunner.TTL);

            final List<RecordUpdateMessage.AliasRecordMessage> aliasMessages = new LinkedList<>();
            serviceRecordWeights.forEach((service, recordWeights) -> {
                addServiceToRegion(aliasMessages, service);

                final String thisRegionHostname = getServiceRegionName(service);

                final Collection<RecordUpdateMessage.Resolution> targets = recordWeights.entrySet().stream()//
                        .map(entry -> {
                            return new RecordUpdateMessage.Resolution(entry.getKey(), entry.getValue());
                        })//
                        .collect(Collectors.toList());

                final RecordUpdateMessage.AliasRecordMessage aliasMessage = new RecordUpdateMessage.AliasRecordMessage(
                        thisRegionHostname, targets);

                aliasMessages.add(aliasMessage);
            });

            final RecordUpdateMessage message = new RecordUpdateMessage(minTtl, aliasMessages);
            return sendMessage(message);
        }

    }

    /**
     * create service.map.dcomp -> service.region.map.dcomp.
     * 
     * @param messages
     *            the current list of messages to be sent
     * @param service
     *            the service to add the record for
     */
    protected void addServiceToRegion(final List<RecordUpdateMessage.AliasRecordMessage> messages,
            final ServiceIdentifier<?> service) {
        final ApplicationSpecification appSpec = AppMgrUtils.getApplicationManager()
                .getApplicationSpecification((ApplicationCoordinates) service);
        Objects.requireNonNull(appSpec, "Could not find application specification for: " + service);

        final String fullHostname = DnsUtils.getFullyQualifiedServiceHostname(appSpec);

        final String regionHostname = getServiceRegionName(service);

        final Collection<RecordUpdateMessage.Resolution> targets = Collections
                .singleton(new RecordUpdateMessage.Resolution(regionHostname, 1));

        final RecordUpdateMessage.AliasRecordMessage message = new RecordUpdateMessage.AliasRecordMessage(fullHostname,
                targets);

        messages.add(message);
    }

}
