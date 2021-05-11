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

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.TextParseException;

import com.bbn.map.AgentConfiguration;

/**
 * Tests for {@link WeightedCNAMERecord}.
 * 
 * @author jschewe
 *
 */
public class WeightedCNAMEREcordTest {

    /**
     * Test that zero is an invalid weight.
     * 
     * @throws TextParseException
     *             internal error
     */
    @Test(expected = java.lang.IllegalArgumentException.class)
    public void testInvalidWeight() throws TextParseException {
        final long ttl = 1;
        final String alias = "a.google.com";
        final String queryName = "google.com";

        new WeightedCNAMERecord(queryName, alias, ttl, 0);
    }

    /**
     * Test querying a record with varying weights.
     * 
     * @author jschewe
     *
     */
    @RunWith(Theories.class)
    public static final class WeightCount {
        /**
         * @return the weights to use
         */
        @DataPoints
        public static double[] weightsToCheck() {
            // CHECKSTYLE:OFF test values
            return new double[] { 1, 2, 3, 4, 5, 0.25, 0.75 };
            // CHEcKSTYLE:ON
        }

        /**
         * Ensure that we get true back from {@link WeightedCNAMERecord#query()}
         * the right number of times and false once afterward.
         * 
         * @param weight1
         *            the weight to test
         * @throws TextParseException
         *             internal error
         */
        @Theory
        public void test(final double weight1) throws TextParseException {
            final long ttl = 1;
            final String alias1 = "a.google.com";
            final String alias2 = "b.google.com";
            final String query = "google.com";
            final double weight2 = 1;

            final WeightedCNAMERecord record1 = new WeightedCNAMERecord(query, alias1, ttl, weight1);
            final WeightedCNAMERecord record2 = new WeightedCNAMERecord(query, alias2, ttl, weight2);

            final WeightedRecordList recordList = new WeightedRecordList(query, Collections.emptyList());
            recordList.addRecord(record1, record1.getWeight());
            recordList.addRecord(record2, record2.getWeight());

            final int numCycles = 1000000; // enough times to get a good
                                           // sampling
            final int numRecords = 2;
            final int numQueries = numRecords * numCycles;
            final double weightPrecision = 1D / AgentConfiguration.getInstance().getDnsWeightPrecision();

            final Map<String, Integer> counts = new HashMap<>();

            for (int i = 0; i < numQueries; ++i) {
                try {
                    final Record dnsRecord = recordList.query();
                    assertThat(dnsRecord, instanceOf(CNAMERecord.class));

                    final CNAMERecord cRecord = (CNAMERecord) dnsRecord;
                    final String alias = cRecord.getAlias().toString(true);
                    counts.merge(alias, 1, Integer::sum);
                } catch (final TextParseException e) {
                    fail("Internal error, invalid domain name: " + e.getMessage());
                }
            }

            final int countAlias1 = counts.getOrDefault(alias1, 0);
            final double expectedAlias1Weight = weight1 / (weight1 + weight2);
            assertThat(alias1, (double) countAlias1 / numQueries, closeTo(expectedAlias1Weight, weightPrecision));

            final int countAlias2 = counts.getOrDefault(alias2, 0);
            final double expectedAlias2Weight = weight2 / (weight1 + weight2);
            assertThat(alias2, (double) countAlias2 / numQueries, closeTo(expectedAlias2Weight, weightPrecision));

        }

    }

}
