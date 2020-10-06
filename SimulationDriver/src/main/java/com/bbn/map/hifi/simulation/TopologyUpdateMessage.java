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
package com.bbn.map.hifi.simulation;

import java.util.Collection;
import java.util.LinkedList;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message sent out when the topology has changed. This information is used by
 * the TA2 interface to provide topology information to the algorithms.
 * 
 * @author jschewe
 *
 */
public class TopologyUpdateMessage {

    /**
     * 
     * @param nodes
     *            see {@link #getNodes()}
     * @param links
     *            see {@link #getLinks(){
     */
    public TopologyUpdateMessage(@Nonnull @JsonProperty("nodes") final Collection<String> nodes,
            @Nonnull @JsonProperty("links") final Collection<TopologyUpdateMessage.Link> links) {
        this.nodes = new LinkedList<>(nodes);
        this.links = new LinkedList<>(links);
    }

    private final Collection<String> nodes;

    /**
     * 
     * @return names of the nodes in the topology
     */
    public Collection<String> getNodes() {
        return nodes;
    }

    private final Collection<TopologyUpdateMessage.Link> links;

    /**
     * 
     * @return pairs of nodes that are linked in the topology
     */
    public Collection<TopologyUpdateMessage.Link> getLinks() {
        return links;
    }

    /**
     * Represents a link in the update message. Separate class to help with JSON
     * serialization.
     * 
     * @author jschewe
     *
     */
    public static final class Link {
        /**
         * 
         * @param left
         *            see {@link #getLeft()}
         * @param right
         *            see {@link #getRight()}
         */
        public Link(@JsonProperty("left") @Nonnull final String left,
                @JsonProperty("right") @Nonnull final String right) {
            this.left = left;
            this.right = right;
        }

        private final String left;

        /**
         * @return the left node name
         */
        public String getLeft() {
            return left;
        }

        private final String right;

        /**
         * 
         * @return the right node name
         */
        public String getRight() {
            return right;
        }
    }
}
