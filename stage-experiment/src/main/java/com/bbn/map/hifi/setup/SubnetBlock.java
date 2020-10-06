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
package com.bbn.map.hifi.setup;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Codeify the IP address conventions.
 * 
 * @author jschewe
 *
 */
final class SubnetBlock {

    /**
     * Minimum value for an octet.
     */
    public static final int MIN_OCTET_VALUE = 0;
    /**
     * Maximum value for an octet;
     */
    public static final int MAX_OCTET_VALUE = 254;

    // .0 usually isn't valid
    private static final int INTER_REGION_MIN = MIN_OCTET_VALUE + 1;
    private static final int INTER_REGION_MAX = 10;
    private int nextInterRegion = INTER_REGION_MIN;

    private static final int UNDERLAY_MIN = 179;
    private static final int UNDERLAY_MAX = 199;
    private int nextUnderlay = UNDERLAY_MIN;

    private static final int CLIENT_MIN = UNDERLAY_MAX + 1;
    // less 1 to avoid broadcast address
    private static final int CLIENT_MAX = MAX_OCTET_VALUE - 1;

    /**
     * The maximum number of containers that can be on an NCP.
     */
    public static final int MAX_CONTAINERS_PER_NCP = 4;

    private static final int NCP_MIN = INTER_REGION_MAX + 1;
    private static final int NCP_MAX = UNDERLAY_MIN - MAX_CONTAINERS_PER_NCP - 1;
    private int nextNcp = NCP_MIN;

    private int nextClient = CLIENT_MIN;

    private static final int FIRST_OCTET = 10;

    private static final Object OCTEC_LOCK = new Object();
    private static int nextSecondOctet = MIN_OCTET_VALUE;
    private static int nextThirdOctet = MIN_OCTET_VALUE;

    private final int secondOctet;
    private final int thirdOctet;

    private final Object lock = new Object();

    /**
     * Needed for testing.
     */
    /* package */ static void resetStaticCounters() {
        nextSecondOctet = MIN_OCTET_VALUE;
        nextThirdOctet = MIN_OCTET_VALUE;

    }

    private SubnetBlock(final int secondOctet, final int thirdOctet) {
        this.secondOctet = secondOctet;
        this.thirdOctet = thirdOctet;
    }

    /**
     * @return a new block of addresses to use
     */
    public static SubnetBlock getNextSubnetBlock() {
        final int secondOctet;
        final int thirdOctet;
        synchronized (OCTEC_LOCK) {
            if (nextThirdOctet > MAX_OCTET_VALUE) {
                nextThirdOctet = MIN_OCTET_VALUE;

                ++nextSecondOctet;
                if (nextSecondOctet > MAX_OCTET_VALUE) {
                    throw new RuntimeException("Ran out of values for the second octet");
                }
            }

            secondOctet = nextSecondOctet;

            thirdOctet = nextThirdOctet;
            ++nextThirdOctet;
        }

        return new SubnetBlock(secondOctet, thirdOctet);
    }

    /**
     * @return the next IP address to use for inter-region links
     */
    public InetAddress getNextInterRegionAddress() {
        final int fourthOctet;
        synchronized (lock) {
            if (nextInterRegion > INTER_REGION_MAX) {
                throw new RuntimeException("Ran out of values for inter region connections. Second octet: "
                        + secondOctet + " third octet: " + thirdOctet);
            }

            fourthOctet = nextInterRegion;
            ++nextInterRegion;
        }

        return createAddress(FIRST_OCTET, this.secondOctet, this.thirdOctet, fourthOctet);
    }

    /**
     * @param numContainers
     *            the number of containers for this NCP
     * @return the next IP address to use for NCP links in a region and the list
     *         of possible container addresses. The container addresses are
     *         sorted from low to high
     */
    public Pair<InetAddress, List<InetAddress>> getNcpAddress(final int numContainers) {
        final int fourthOctet;
        if (numContainers > MAX_CONTAINERS_PER_NCP) {
            throw new IllegalArgumentException(
                    "Cannot have more than " + MAX_CONTAINERS_PER_NCP + " containers on each NCP");
        }

        synchronized (lock) {
            if (nextNcp > NCP_MAX) {
                throw new RuntimeException("Ran out of IP addresses for NCP + container connections. Second octet: " + secondOctet
                        + " third octet: " + thirdOctet);
            }

            fourthOctet = nextNcp;
            nextNcp += numContainers + 1;
        }

        final InetAddress ncpAddress = createAddress(FIRST_OCTET, this.secondOctet, this.thirdOctet, fourthOctet);
        final List<InetAddress> containerAddresses = new ArrayList<>(numContainers);
        for (int index = 0; index < numContainers; ++index) {
            final int containerFourthOctet = fourthOctet + index + 1;

            final InetAddress containerAddress = createAddress(FIRST_OCTET, this.secondOctet, this.thirdOctet,
                    containerFourthOctet);
            containerAddresses.add(containerAddress);
        }

        return Pair.of(ncpAddress, containerAddresses);
    }

    /**
     * @return the next IP address to use for client links in a region
     */
    public InetAddress getNextClientAddress() {
        final int fourthOctet;
        synchronized (lock) {
            if (nextClient > CLIENT_MAX) {
                throw new RuntimeException("Ran out of values for client connections. Second octet: " + secondOctet
                        + " third octet: " + thirdOctet);
            }

            fourthOctet = nextClient;
            ++nextClient;
        }

        return createAddress(FIRST_OCTET, this.secondOctet, this.thirdOctet, fourthOctet);
    }

    /**
     * @return the next IP address to use for underlay links in a region
     */
    public InetAddress getNextUnderlayAddress() {
        final int fourthOctet;
        synchronized (lock) {
            if (nextUnderlay > UNDERLAY_MAX) {
                throw new RuntimeException("Ran out of values for underlay connections. Second octet: " + secondOctet
                        + " third octet: " + thirdOctet);
            }

            fourthOctet = nextUnderlay;
            ++nextUnderlay;
        }

        return createAddress(FIRST_OCTET, this.secondOctet, this.thirdOctet, fourthOctet);
    }

    private static void validateOctetValue(final int octetValue, final String label) {
        if (octetValue < MIN_OCTET_VALUE || octetValue > MAX_OCTET_VALUE) {
            throw new IllegalArgumentException(label + " octet is outside valid range: " + MIN_OCTET_VALUE + " <= "
                    + octetValue + " <= " + MAX_OCTET_VALUE);
        }
    }

    private static InetAddress createAddress(final int firstOctet,
            final int secondOctet,
            final int thirdOctet,
            final int fourthOctet) {
        validateOctetValue(firstOctet, "First");
        validateOctetValue(secondOctet, "Second");
        validateOctetValue(thirdOctet, "Third");
        validateOctetValue(fourthOctet, "Fourth");

        final String addrStr = String.format("%d.%d.%d.%d", firstOctet, secondOctet, thirdOctet, fourthOctet);
        try {
            return InetAddress.getByName(addrStr);
        } catch (final UnknownHostException e) {
            throw new RuntimeException("Computed invalid IP address " + addrStr + ", this is an internal error", e);
        }
    }

    @Override
    public String toString() {
        return FIRST_OCTET + "." + secondOctet + "." + thirdOctet + ".0/24";
    }
}
