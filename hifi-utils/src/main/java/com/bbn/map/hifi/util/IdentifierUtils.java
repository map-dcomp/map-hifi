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

import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;

/**
 * Functions for working with {@link NodeIdentifier}.
 * 
 * @author jschewe
 *
 */
public final class IdentifierUtils {

    private IdentifierUtils() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentifierUtils.class);

    /**
     * Given the name or IP of a node, get the canonical identifier. If the name
     * cannot be found in DNS a warning is logged and the parameter is used
     * directly.
     * 
     * @param nameOrIp
     *            name or IP of a node
     * @return node identifier
     * @see DnsUtils#getCanonicalName(String)
     * 
     */
    public static NodeIdentifier getNodeIdentifier(final String nameOrIp) {
        try {
            final String name = DnsUtils.getCanonicalName(nameOrIp);
            final NodeIdentifier id = new DnsNameIdentifier(name);
            return id;
        } catch (final UnknownHostException e) {
            LOGGER.warn("Unable to find canonical name for {} in DNS, using bare name for node identifier", nameOrIp);
            return new DnsNameIdentifier(nameOrIp);
        }
    }

    /**
     * Convert a node identifier to a canonical name through DNS.
     * 
     * @param ni
     *            the node identifier to get the canonical value for
     * @return the canonical identifier
     */
    public static NodeIdentifier getCanonicalIdentifier(final NodeIdentifier ni) {
        if (NodeIdentifier.UNKNOWN.equals(ni)) {
            // don't try and lookup unknown
            return ni;
        } else {
            return getNodeIdentifier(ni.getName());
        }
    }
}
