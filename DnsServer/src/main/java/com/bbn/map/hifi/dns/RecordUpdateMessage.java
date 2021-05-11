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
package com.bbn.map.hifi.dns;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message sent from the nodes to the DNS server to update weighted records.
 * 
 * @author jschewe
 *
 */
public class RecordUpdateMessage {

    /**
     * 
     * @param ttl
     *            {@link #getTtl()}
     * @param aliasMessages
     *            {@link #getAliasMessages()}
     */
    public RecordUpdateMessage(@JsonProperty("ttl") final int ttl,
            @JsonProperty("aliasMessages") final Collection<AliasRecordMessage> aliasMessages) {
        this.ttl = ttl;
        this.aliasMessages = Collections.unmodifiableCollection(new LinkedList<>(aliasMessages));
    }

    private final Collection<AliasRecordMessage> aliasMessages;

    /**
     * 
     * @return messages defining aliases for hostnames
     */
    public Collection<AliasRecordMessage> getAliasMessages() {
        return aliasMessages;
    }

    private final int ttl;

    /**
     * 
     * @return the TTL for the DNS records
     */
    public int getTtl() {
        return ttl;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[ ttl: " + ttl + " aliasMessages: " + aliasMessages + "]";
    }

    /**
     * Defines an alias record in the DNS.
     * 
     * @author jschewe
     *
     */
    public static final class AliasRecordMessage {

        /**
         * 
         * @param hostname
         *            {@link #getHostname()}
         * @param resolutionTargets
         *            {@link #getResolutionTargets()}
         */
        public AliasRecordMessage(@JsonProperty("hostname") final String hostname,
                @JsonProperty("resolutionTargets") final Collection<Resolution> resolutionTargets) {
            this.hostname = hostname;
            this.resolutionTargets = Collections.unmodifiableCollection(new LinkedList<>(resolutionTargets));
        }

        private final String hostname;

        /**
         * 
         * @return host to be resolved
         */
        public String getHostname() {
            return hostname;
        }

        private final Collection<Resolution> resolutionTargets;

        /**
         * 
         * @return read-only collection of targets to return for
         *         {@link #getHostname()}
         */
        public Collection<Resolution> getResolutionTargets() {
            return resolutionTargets;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[ hostname: " + hostname + " resolutionTargets: " + resolutionTargets
                    + "]";
        }

    }

    /**
     * A particular resolution of a host. This is a weighted DNS alias record.
     * 
     * @author jschewe
     *
     */
    public static final class Resolution {

        /**
         * 
         * @param target
         *            {@link #getTarget()}
         * @param weight
         *            {@link #getWeight()}
         */
        public Resolution(@JsonProperty("target") final String target, @JsonProperty("weight") final double weight) {
            this.target = target;
            this.weight = weight;
        }

        private final String target;

        /**
         * 
         * @return host to resolve to
         */
        public String getTarget() {
            return target;
        }

        private final double weight;

        /**
         * 
         * @return the weight of this target host
         */
        public double getWeight() {
            return weight;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[ target: " + target + " weight: " + weight + "]";
        }

    }

}
