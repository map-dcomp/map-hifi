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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.dns.DNSUpdateService;
import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.map.hifi.dns.RecordUpdateMessage;
import com.bbn.map.hifi.dns.WeightedRecordMessageServer;
import com.bbn.map.hifi.dns.WeightedRoundRobinResolver;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableCollection;

/**
 * DNS update service that talks to {@link WeightedRoundRobinResolver}.
 * 
 * @author jschewe
 *
 */
public abstract class WeightedDnsUpdateService implements DNSUpdateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeightedDnsUpdateService.class);

    private final RegionIdentifier region;

    /**
     * @return the region that this service is in
     */
    public RegionIdentifier getRegion() {
        return region;
    }

    private final InetAddress serverAddress;
    private final Object lock = new Object();

    /**
     * 
     * @return lock for thread-safe access to variables
     */
    protected final Object getLock() {
        return lock;
    }

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

    @Override
    public abstract boolean replaceAllRecords(ImmutableCollection<Pair<DnsRecord, Double>> records);

    /**
     * Send the records to the configured DNS server.
     * 
     * @param message
     *            the message to send
     * @return true if there are no sending the records
     */
    protected final boolean sendMessage(final RecordUpdateMessage message) {
        LOGGER.trace("Top of send records");
        try (Socket socket = new Socket(serverAddress, WeightedRecordMessageServer.PORT);
                Reader reader = new InputStreamReader(socket.getInputStream(), Charset.defaultCharset());
                Writer writer = new OutputStreamWriter(socket.getOutputStream(), Charset.defaultCharset())) {

            LOGGER.trace("Finished connecting to server");

            final ObjectMapper jsonMapper = JsonUtils.getStandardMapObjectMapper()
                    .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE).disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

            jsonMapper.writeValue(writer, WeightedRecordMessageServer.UPDATE_COMMAND);
            jsonMapper.writeValue(writer, message);
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

    /**
     * 
     * @param record
     *            the record to get the destination for
     * @return the destination for the record
     */
    protected final String getDestinationForRecord(final DnsRecord record) {
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

    /**
     * 
     * @param record
     *            record to get the alias for
     * @return the name for the node referenced
     */
    protected String getAliasForNameRecord(final NameRecord record) {
        final NodeIdentifier serviceNode = record.getNode();

        return serviceNode.getName();
    }

    /**
     * 
     * @param record
     *            the record to get the alias for
     * @return the appropriate region delegate string
     */
    protected String getAliasForDelegateRecord(final DelegateRecord record) {
        final ServiceIdentifier<?> service = record.getService();
        final String delegateRegionHostname = getServiceRegionName(service, record.getDelegateRegion());

        return delegateRegionHostname;
    }

    /**
     * 
     * @param service
     *            the service
     * @return the hostname for the service in this region
     */
    protected final String getServiceRegionName(final ServiceIdentifier<?> service) {
        return getServiceRegionName(service, region);
    }

    /**
     * Get the FQDN of the service in the specified region.
     * 
     * @param service
     *            the service to find the name for
     * @param region
     *            the region to get the name for
     * @return the hostname for the service in the specified region
     */
    protected static String getServiceRegionName(final ServiceIdentifier<?> service, final RegionIdentifier region) {
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
