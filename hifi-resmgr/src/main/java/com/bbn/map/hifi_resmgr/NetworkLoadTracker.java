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
package com.bbn.map.hifi_resmgr;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nonnull;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.simulator.ContainerSim;
import com.bbn.map.simulator.SimResourceManager;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceReport;
import com.google.common.collect.ImmutableMap;




// TODO: Possibly rename this class for hifi-resmgr

/**
 * Track network load. This is used inside {@link ContainerSim} and
 * {@link SimResourceManager}.
 * 
 * @author jschewe
 *
 */
/* package */ class NetworkLoadTracker {
    /**
     * The value specifies which linked node the load goes on.
     */
//    private List<LinkLoadEntry> linkLoad = new LinkedList<>();

//    /**
//     * Specify some load on a link on the node.
//     * 
//     * @param req
//     *            the client request, only networkLoad is used
//     * @param otherNode
//     *            The node on the other end of the link from the node being
//     *            managed by the resource manager
//     * @param now
//     *            the current time according to the clock currently being used
//     * @param networkCapacity
//     *            the capacity of the node that the load is being computed for
//     * @return status of the request, if the status is
//     *         {@link ClientSim.RequestResult#FAIL}, then the link load is not
//     *         modified. The {@link LinkLoadEntry} can be used to remove the
//     *         link load with {@link #removeLinkLoad(LinkLoadEntry)}
//     * @see #removeLinkLoad(ClientRequest, NodeIdentifier)
//     */
//    public Pair<ClientSim.RequestResult, LinkLoadEntry> addLinkLoad(final long now,
//            @Nonnull final ClientRequest req,
//            @Nonnull final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute, Double>> networkCapacity,
//            @Nonnull final NodeIdentifier otherNode) {
//        // need to add, then check the thresholds and then possibly
//        // remove
//        // TODO: ticket:29 will make the duration variable based on network
//        // path state
//        final LinkLoadEntry entry = new LinkLoadEntry(req, otherNode, req.getNetworkDuration());
//        linkLoad.add(entry);
//
//        final ClientSim.RequestResult result = computeCurrentLinkLoad(now, networkCapacity, otherNode);
//
//        if (result == ClientSim.RequestResult.FAIL) {
//            linkLoad.remove(entry);
//        }
//
//        return Pair.of(result, entry);
//    }
//
//    /**
//     * Compute the current link load for the link between this node and the
//     * specified node. The lock must be held for this method to be called.
//     * 
//     * @param now
//     *            the current time from the clock used
//     * @param otherNode
//     *            the node on the other end of the link
//     * @param networkCapacity
//     *            the capacity of the node that the load is being computed for
//     * @return the result at the current point in time
//     */
//    public ClientSim.RequestResult computeCurrentLinkLoad(final long now,
//            @Nonnull final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute, Double>> networkCapacity,
//            @Nonnull final NodeIdentifier otherNode) {
//        final Map<LinkAttribute, Double> load = new HashMap<>();
//
//        final Iterator<LinkLoadEntry> linkDemandIter = linkLoad.iterator();
//        while (linkDemandIter.hasNext()) {
//            final LinkLoadEntry entry = linkDemandIter.next();
//            final ClientRequest req = entry.getRequest();
//            final NodeIdentifier neighborId = entry.getOtherNode();
//            final long duration = entry.getDuration();
//            final long end = req.getStartTime() + duration;
//
//            if (neighborId.equals(otherNode) && req.getStartTime() <= now && now < end) {
//                final ImmutableMap<LinkAttribute, Double> clientNetworkLoad = req.getNetworkLoadAsAttribute();
//                clientNetworkLoad.forEach((k, v) -> load.merge(k, v, Double::sum));
//            } else if (end > now) {
//                // already happened, drop it
//                linkDemandIter.remove();
//            }
//        }
//
//        final ImmutableMap<LinkAttribute, Double> linkCapacity = networkCapacity.getOrDefault(otherNode,
//                ImmutableMap.of());
//
//        for (final Map.Entry<LinkAttribute, Double> entry : load.entrySet()) {
//            final LinkAttribute attribute = entry.getKey();
//            final double attributeValue = entry.getValue();
//            final double attributeCapacity = linkCapacity.getOrDefault(attribute, 0D);
//
//            final double percentageOfCapacity = attributeValue / attributeCapacity;
//            if (percentageOfCapacity > 1) {
//                return ClientSim.RequestResult.FAIL;
//            } else if (percentageOfCapacity > SimulationConfiguration.getInstance().getSlowNetworkThreshold()) {
//                return ClientSim.RequestResult.SLOW;
//            }
//        }
//
//        return ClientSim.RequestResult.SUCCESS;
//    }

//    /**
//     * Undoes the changes done by {@Link #addLinkLoad(ClientRequest,
//     * NodeIdentifier)}.
//     *
//     * @param entry
//     *            the return value from
//     *            {@link #addLinkLoad(ClientRequest, NodeIdentifier)}
//     * @return true if the load was removed
//     * @see #addLinkLoad(ClientRequest, NodeIdentifier)
//     */
//    public boolean removeLinkLoad(final LinkLoadEntry entry) {
//        return linkLoad.remove(entry);
//    }
//
//    /**
//     * 
//     * @param now
//     *            the current time
//     * @return the current network load
//     */
//    public ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute, Double>> computeCurrentNetworkLoad(
//            final long now) {
//        final Map<NodeIdentifier, Map<LinkAttribute, Double>> networkLoad = new HashMap<>();
//
//        final Iterator<LinkLoadEntry> linkLoadIter = linkLoad.iterator();
//        while (linkLoadIter.hasNext()) {
//            final LinkLoadEntry entry = linkLoadIter.next();
//            final ClientRequest req = entry.getRequest();
//            final NodeIdentifier neighborId = entry.getOtherNode();
//            final long end = req.getStartTime() + entry.getDuration();
//
//            if (req.getStartTime() <= now && now < end) {
//                final Map<LinkAttribute, Double> nodeNetworkLoad = networkLoad.computeIfAbsent(neighborId,
//                        k -> new HashMap<>());
//
//                final ImmutableMap<LinkAttribute, Double> clientNetworkLoad = req.getNetworkLoadAsAttribute();
//                clientNetworkLoad.forEach((attr, value) -> {
//                    nodeNetworkLoad.merge(attr, value, Double::sum);
//                });
//            } else if (end > now) {
//                // already happened, drop it
//                linkLoadIter.remove();
//            }
//        }
//
//        final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute, Double>> reportNetworkLoad = ImmutableUtils
//                .makeImmutableMap2(networkLoad);
//
//        return reportNetworkLoad;
//    }

    private final Map<Long, ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute, Double>>> networkLoadHistory = new HashMap<>();

    /**
     * Update the current demand state. Used for
     * {@link #computeNetworkDemand(long, com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow)}.
     * 
     * @param timestamp
     * @param networkLoad
     */
    public void updateDemandValues(final long timestamp,
            @Nonnull final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute, Double>> networkLoad) {
        // add new entry
        networkLoadHistory.put(timestamp, networkLoad);

        // clean out old entries
        final long historyCutoff = timestamp
                - Math.max(AgentConfiguration.getInstance().getDcopEstimationWindow().toMillis(),
                        AgentConfiguration.getInstance().getRlgEstimationWindow().toMillis());

        final Iterator<Map.Entry<Long, ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute, Double>>>> networkIter = networkLoadHistory
                .entrySet().iterator();
        while (networkIter.hasNext()) {
            final Map.Entry<Long, ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute, Double>>> entry = networkIter
                    .next();
            if (entry.getKey() < historyCutoff) {
                networkIter.remove();
            }
        }
    }

    /**
     * Compute the current network demand.
     * 
     * @param now
     *            the current time
     * @param estimationWindow
     *            the window over which to compute the demand
     * @return the demand value
     */
    @Nonnull
    public ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute, Double>> computeNetworkDemand(final long now,
            @Nonnull final ResourceReport.EstimationWindow estimationWindow) {
        final long duration;
        switch (estimationWindow) {
        case LONG:
            duration = AgentConfiguration.getInstance().getDcopEstimationWindow().toMillis();
            break;
        case SHORT:
            duration = AgentConfiguration.getInstance().getRlgEstimationWindow().toMillis();
            break;
        default:
            throw new IllegalArgumentException("Unknown estimation window: " + estimationWindow);
        }

        final long cutoff = now - duration;
        final Map<NodeIdentifier, Map<LinkAttribute, Double>> sums = new HashMap<>();
        final Map<NodeIdentifier, Map<LinkAttribute, Integer>> counts = new HashMap<>();
        twoLevelHistoryMapCountSum(networkLoadHistory, cutoff, sums, counts);

        final ImmutableMap<NodeIdentifier, ImmutableMap<LinkAttribute, Double>> reportDemand = twoLevelMapAverage(
                sums, counts);

        return reportDemand;
    }

    private static <K1, K2> void twoLevelMapCountSum(final ImmutableMap<K1, ImmutableMap<K2, Double>> map,
            final Map<K1, Map<K2, Double>> sums,
            final Map<K1, Map<K2, Integer>> counts) {
        for (final Map.Entry<K1, ImmutableMap<K2, Double>> entry1 : map.entrySet()) {
            final K1 key1 = entry1.getKey();
            final Map<K2, Double> sums1 = sums.getOrDefault(key1, new HashMap<>());
            final Map<K2, Integer> counts1 = counts.getOrDefault(key1, new HashMap<>());

            final ImmutableMap<K2, Double> map1 = entry1.getValue();
            for (final Map.Entry<K2, Double> valueEntry : map1.entrySet()) {
                final K2 key2 = valueEntry.getKey();
                final double value = valueEntry.getValue();

                final double newSum = value + sums1.getOrDefault(key2, 0D);
                final int newCount = 1 + counts1.getOrDefault(key2, 0);

                sums1.put(key2, newSum);
                counts1.put(key2, newCount);
            } // level 2

            sums.put(key1, sums1);
            counts.put(key1, counts1);
        } // level 1
    }

    public static <K1, K2> void twoLevelHistoryMapCountSum(
            final Map<Long, ImmutableMap<K1, ImmutableMap<K2, Double>>> history,
            final long historyCutoff,
            final Map<K1, Map<K2, Double>> sums,
            final Map<K1, Map<K2, Integer>> counts) {
        for (final Map.Entry<Long, ImmutableMap<K1, ImmutableMap<K2, Double>>> historyEntry : history.entrySet()) {
            final long timestamp = historyEntry.getKey();
            if (timestamp >= historyCutoff) {
                twoLevelMapCountSum(historyEntry.getValue(), sums, counts);
            } // inside window
        } // foreach history entry
    }

    public static <K1, K2> ImmutableMap<K1, ImmutableMap<K2, Double>> twoLevelMapAverage(
            final Map<K1, Map<K2, Double>> sums, final Map<K1, Map<K2, Integer>> counts) {
        final ImmutableMap.Builder<K1, ImmutableMap<K2, Double>> average = ImmutableMap.builder();

        for (final Map.Entry<K1, Map<K2, Double>> sumEntry : sums.entrySet()) {
            final K1 key1 = sumEntry.getKey();
            final Map<K2, Double> sums1 = sumEntry.getValue();
            final Map<K2, Integer> counts1 = counts.get(key1);

            final ImmutableMap.Builder<K2, Double> average1 = ImmutableMap.builder();

            for (final Map.Entry<K2, Double> sumEntry1 : sums1.entrySet()) {
                final K2 key2 = sumEntry1.getKey();
                final double value = sumEntry1.getValue();
                final int count = counts1.get(key2);

                final double demand = value / count;
                average1.put(key2, demand);
            } // level 2

            average.put(key1, average1.build());
        } // level 1

        return average.build();
    }

//    /**
//     * Class for storing information about the link load.
//     *
//     * @author jschewe
//     *
//     */
//    public static final class LinkLoadEntry {
//        /**
//         *
//         * @param request
//         *            see {@link #getRequest()}
//         * @param otherNode
//         *            see {@link #getOtherNode()}
//         */
//        /* package */ LinkLoadEntry(@Nonnull final ClientRequest request,
//                @Nonnull final NodeIdentifier otherNode,
//                final long duration) {
//            this.request = request;
//            this.otherNode = otherNode;
//            this.duration = duration;
//        }
//
//        private final NodeIdentifier otherNode;
//
//        /**
//         * @return the node on the other end of the link
//         */
//        @Nonnull
//        public NodeIdentifier getOtherNode() {
//            return otherNode;
//        }
//
//        private final ClientRequest request;
//
//        /**
//         *
//         * @return the request that is creating the load
//         */
//        @Nonnull
//        public ClientRequest getRequest() {
//            return request;
//        }
//
//        private final long duration;
//
//        /**
//         * @return The duration that the load takes, this is based on the load
//         *         of the network at the time that the client requested the
//         *         service
//         */
//        public long getDuration() {
//            return duration;
//        }
//    }

}
