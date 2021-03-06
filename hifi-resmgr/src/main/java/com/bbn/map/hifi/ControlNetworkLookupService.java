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
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.hifi.util.IdentifierUtils;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeLookupService;

/**
 * Lookup nodes using DNS on the control network.
 * 
 * @author jschewe
 *
 */
public class ControlNetworkLookupService implements NodeLookupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ControlNetworkLookupService.class);

    private final int apPort;

    /**
     * 
     * @param apPort
     *            the port returned in
     *            {@link #getInetAddressForNode(NodeIdentifier)}
     */
    public ControlNetworkLookupService(final int apPort) {
        this.apPort = apPort;
    }

    private String getNodeHostname(final NodeIdentifier node) {
        final String name = IdentifierUtils.getSimpleNodeName(node);
        final String controlNetworkName = String.format("%s.%s", name, DnsUtils.MAP_CONTROL_TLD);
        return controlNetworkName;
    }

    @Override
    public InetSocketAddress getInetAddressForNode(@Nonnull final NodeIdentifier id) {
        final String hostname = getNodeHostname(id);
        try {
            final InetAddress address = DnsUtils.getByName(hostname);

            final InetSocketAddress addr = new InetSocketAddress(address, apPort);

            if (null == addr.getAddress()) {
                LOGGER.error("Host {} cannot be found in DNS, returning null from getInetAddressForNode()", hostname);
                return null;
            } else {
                return addr;
            }
        } catch (final UnknownHostException e) {
            LOGGER.error("Host {} cannot be found in DNS, returning null from getInetAddressForNode()", hostname, e);
            return null;
        }
    }

}
