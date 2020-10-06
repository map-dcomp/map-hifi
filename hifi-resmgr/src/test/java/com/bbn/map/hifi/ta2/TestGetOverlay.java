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

import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.junit.Test;

import com.bbn.map.hifi.simulation.SimDriver;
import com.bbn.map.hifi.simulation.TopologyUpdateMessage;
import com.bbn.map.ta2.OverlayLink;
import com.bbn.map.ta2.OverlayTopology;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.NetworkServerProperties;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionLookupService;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.ns2.NS2Parser;
import com.bbn.protelis.networkresourcemanagement.ns2.Topology;

import edu.uci.ics.jung.graph.Graph;

/**
 * See that getting the overlay topology for a region contains the right nodes.
 * 
 * @author jschewe
 *
 */
public class TestGetOverlay {

    /**
     * Check that the nodes in a region are in the resulting topology.
     * 
     * @throws URISyntaxException
     *             test failure
     * @throws IOException
     *             test failure
     */
    @Test
    public void testNodesInRegion() throws URISyntaxException, IOException {
        final RegionIdentifier regionA = new StringRegionIdentifier("A");

        //FIXME need to get proper reference to files in other project
        final URL baseu = TestGetOverlay.class.getResource("multinode");
        final Path baseDirectory = Paths.get(baseu.toURI());
        final Topology topology = NS2Parser.parse("test", baseDirectory);

        final TestRegionLookup regionLookup = new TestRegionLookup();
        // populate region lookup
        topology.getNodes().entrySet().stream().map(Map.Entry::getValue).forEach(node -> {
            regionLookup.addMapping(new DnsNameIdentifier(node.getName()),
                    new StringRegionIdentifier(NetworkServerProperties.parseRegionName(node.getExtraData())));
        });

        final Stream<NodeIdentifier> expectedServers = regionLookup.getData().entrySet().stream()
                .filter(entry -> regionA.equals(entry.getValue())).map(Map.Entry::getKey);
        final Set<NodeIdentifier> expectedNodes = expectedServers.collect(Collectors.toSet());

        final TA2Impl ta2 = new TA2Impl(regionLookup);
        final TopologyUpdateMessage msg = createMessage(topology);
        ta2.updateTopologyInformation(msg);

        final OverlayTopology overlay = ta2.getOverlay(regionA);

        final Set<NodeIdentifier> allOverlayNodes = new HashSet<>(overlay.getAllNodes());
        assertThat(allOverlayNodes, hasItems(expectedNodes.toArray(new NodeIdentifier[0])));
    }

    private static TopologyUpdateMessage createMessage(final Topology topology) {
        final Graph<NodeIdentifier, OverlayLink> graph = SimDriver.parseGraph(topology);
        final TopologyUpdateMessage msg = SimDriver.createTopologyUpdate(graph);
        return msg;
    }

    private static final class TestRegionLookup implements RegionLookupService {

        private final Map<NodeIdentifier, RegionIdentifier> data = new HashMap<>();

        public Map<NodeIdentifier, RegionIdentifier> getData() {
            return data;
        }

        public void addMapping(@Nonnull final NodeIdentifier node, @Nonnull final RegionIdentifier region) {
            data.put(node, region);
        }

        @Override
        public RegionIdentifier getRegionForNode(final NodeIdentifier nodeId) {
            return data.get(nodeId);
        }

    }

}
