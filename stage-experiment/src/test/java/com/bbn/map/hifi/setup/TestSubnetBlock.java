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
package com.bbn.map.hifi.setup;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for {@link SubnetBlock}.
 * 
 * @author jschewe
 *
 */
public class TestSubnetBlock {

    private static final Logger LOGGER = LogManager.getLogger(TestSubnetBlock.class);

    private void createAllValidBlocks() {
        for (int second = SubnetBlock.MIN_OCTET_VALUE; second <= SubnetBlock.MAX_OCTET_VALUE; ++second) {
            for (int third = SubnetBlock.MIN_OCTET_VALUE; third <= SubnetBlock.MAX_OCTET_VALUE; ++third) {
                LOGGER.info("Checking second {} third {}", second, third);
                SubnetBlock.getNextSubnetBlock();
            }
        }
    }

    /**
     * Reset the {@link SubnetBlock} object to default values. This is done
     * before and after all tests to ensure that there is no overflow between
     * tests.
     */
    @Before
    @After
    public void resetCounters() {
        SubnetBlock.resetStaticCounters();
    }

    /**
     * Check that we get an exception when we ask for more subnet blocks than we
     * can create.
     */
    @Test(expected = RuntimeException.class)
    public void testExhaustSecondOctet() {
        createAllValidBlocks();

        // this should fail
        SubnetBlock.getNextSubnetBlock();
    }

    /**
     * Make sure that we can create all of the expected blocks.
     */
    @Test
    public void testAllValidBlocks() {
        createAllValidBlocks();
    }

    /**
     * Get all addresses for a {@link SubnetBlock} and ensure that none of them
     * overlap.
     */
    @SuppressFBWarnings(value = "IL_INFINITE_LOOP", justification = "The loop should run until there are no more addresses available")
    @Test
    public void testNoAddressOverlap() {
        final SubnetBlock block = SubnetBlock.getNextSubnetBlock();

        // get all client IPs
        final Set<InetAddress> seenClients = new HashSet<>();
        boolean done = false;
        while (!done) {
            try {
                final InetAddress addr = block.getNextClientAddress();
                final boolean added = seenClients.add(addr);
                assertThat("Found client address " + addr + " that overlaps a client address previously seen", added,
                        is(true));
            } catch (final RuntimeException e) {
                done = true;
            }
        }

        // get all inter-region addresses
        final Set<InetAddress> seenInterRegion = new HashSet<>();
        done = false;
        while (!done) {
            try {
                final InetAddress addr = block.getNextClientAddress();
                final boolean added = seenInterRegion.add(addr);
                assertThat(
                        "Found inter-region address " + addr + " that overlaps an inter-region address previously seen",
                        added, is(true));
            } catch (final RuntimeException e) {
                done = true;
            }
        }

        final int numContainers = 4;
        final Set<InetAddress> seenNcp = new HashSet<>();
        final Set<InetAddress> seenContainer = new HashSet<>();
        done = false;
        while (!done) {
            try {
                final Pair<InetAddress, List<InetAddress>> pair = block.getNcpAddress(numContainers);
                final InetAddress ncpAddr = pair.getLeft();
                final boolean ncpAdded = seenNcp.add(ncpAddr);
                assertThat("Found NCP address " + ncpAddr + " that overlaps an NCP address previously seen", true,
                        is(ncpAdded));

                for (final InetAddress containerAddr : pair.getRight()) {
                    final boolean added = seenContainer.add(containerAddr);
                    assertThat("Found container address " + containerAddr
                            + " that overlaps a container address previously seen", added, is(true));

                }
            } catch (final RuntimeException e) {
                done = true;
            }
        }

        // check all intersections
        final Set<InetAddress> clientInterRegionIntersection = new HashSet<>(seenClients);
        clientInterRegionIntersection.retainAll(seenInterRegion);
        assertThat("Overlap between client and inter-region addresses", clientInterRegionIntersection, empty());

        final Set<InetAddress> clientNcpIntersection = new HashSet<>(seenClients);
        clientNcpIntersection.retainAll(seenNcp);
        assertThat("Overlap between client and NCP addresses", clientNcpIntersection, empty());

        final Set<InetAddress> clientContainerIntersection = new HashSet<>(seenClients);
        clientContainerIntersection.retainAll(seenContainer);
        assertThat("Overlap between client and container addresses", clientContainerIntersection, empty());

        final Set<InetAddress> interRegionNcpIntersection = new HashSet<>(seenInterRegion);
        interRegionNcpIntersection.retainAll(seenNcp);
        assertThat("Overlap between inter-region and NCP addresses", interRegionNcpIntersection, empty());

        final Set<InetAddress> interRegionContainerIntersection = new HashSet<>(seenInterRegion);
        interRegionContainerIntersection.retainAll(seenContainer);
        assertThat("Overlap between inter-region and container addresses", interRegionContainerIntersection, empty());

        final Set<InetAddress> ncpContainerIntersection = new HashSet<>(seenNcp);
        ncpContainerIntersection.retainAll(seenContainer);
        assertThat("Overlap between NCP and container addresses", ncpContainerIntersection, empty());

    }
}
