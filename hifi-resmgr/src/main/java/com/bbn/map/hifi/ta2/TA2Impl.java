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
package com.bbn.map.hifi.ta2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.bbn.map.hifi.simulation.TopologyUpdateMessage;
import com.bbn.map.hifi.util.IdentifierUtils;
import com.bbn.map.ta2.OverlayTopology;
import com.bbn.map.ta2.TA2Interface;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionLookupService;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;

/**
 * Hi-fi implementation of {@link TA2Interface}.
 * 
 * @author jschewe
 *
 */
public class TA2Impl implements TA2Interface {

    private final RegionLookupService regionLookup;

    private final Object lock = new Object();

    /**
     * 
     * @param regionLookup
     *            lookup service for regions of nodes received from TA2
     */
    public TA2Impl(@Nonnull final RegionLookupService regionLookup) {
        this.regionLookup = regionLookup;
    }

    private Graph<Node, Link> cachedGraph = null;

    @Override
    public OverlayTopology getOverlay(final RegionIdentifier region) {
        final Set<ImmutablePair<NodeIdentifier, NodeIdentifier>> ta2Links = new HashSet<>();
        final Set<NodeIdentifier> ta2Nodes = new HashSet<>();

        synchronized (lock) {
            if (null == cachedGraph) {
                cachedGraph = convertMessageToGraph(topologyMessage, regionLookup);
            }

            cachedGraph.getVertices().stream().filter(v -> region.equals(v.region)).forEach(v -> {
                ta2Nodes.add(v.name);

                // should be able to get get in or out edges, but getting
                // both
                // to be
                // sure that we get all edges
                Stream.concat(cachedGraph.getInEdges(v).stream(), cachedGraph.getOutEdges(v).stream()).forEach(edge -> {
                    ta2Nodes.add(edge.left.name);
                    ta2Nodes.add(edge.right.name);

                    ta2Links.add(ImmutablePair.of(edge.left.name, edge.right.name));
                });
            });
        }

        return new OverlayTopology(ta2Nodes, ta2Links);
    }

    private TopologyUpdateMessage topologyMessage = null;

    /**
     * 
     * @param update
     *            update to note the new topology
     */
    public void updateTopologyInformation(final TopologyUpdateMessage update) {
        synchronized (lock) {
            topologyMessage = update;
        }
    }

    private static Graph<Node, Link> convertMessageToGraph(final TopologyUpdateMessage msg,
            final RegionLookupService regionLookup) {
        final Map<String, Node> nodes = new HashMap<>();

        final Graph<Node, Link> graph = new SparseMultigraph<>();
        msg.getNodes().stream().forEach(name -> {
            final Node node = createNode(name, regionLookup);
            if (!graph.containsVertex(node)) {
                graph.addVertex(node);
            }
            nodes.put(name, node);
        });

        msg.getLinks().stream().forEach(pair -> {
            final Link link = new Link();
            if (!nodes.containsKey(pair.getLeft())) {
                nodes.put(pair.getLeft(), createNode(pair.getLeft(), regionLookup));
            }
            if (!nodes.containsKey(pair.getRight())) {
                nodes.put(pair.getRight(), createNode(pair.getRight(), regionLookup));
            }

            link.left = nodes.get(pair.getLeft());
            link.right = nodes.get(pair.getRight());

            if (!graph.containsEdge(link)) {
                graph.addEdge(link, link.left, link.right);
            }
        });

        return graph;
    }

    private static Node createNode(final String name, final RegionLookupService regionLookup) {
        final Node node = new Node();
        node.name = IdentifierUtils.getNodeIdentifier(name);
        node.region = regionLookup.getRegionForNode(node.name);
        return node;
    }

    // CHECKSTYLE:OFF internal data class
    private static final class Node {
        public NodeIdentifier name;
        public RegionIdentifier region;
    }

    private static final class Link {
        public Node left;
        public Node right;
    }
    // CHECKSTYLE:ON

}
