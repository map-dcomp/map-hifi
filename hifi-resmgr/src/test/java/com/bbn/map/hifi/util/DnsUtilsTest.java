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
package com.bbn.map.hifi.util;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/**
 * Tests for {@link DnsUtils}.
 * 
 * @author jschewe
 *
 */
public class DnsUtilsTest {

    /**
     * Test valid domain labels against
     * {@link DnsUtils#isValidDomainLabel(String)}.
     * 
     * @author jschewe
     *
     */
    @RunWith(Theories.class)
    public static class ValidDomainLabels {

        /**
         * Valid DNS labels.
         */
        @DataPoints
        public static final String[] VALID_LABELS = { "abc", "A0c", "A-0c", "a", "0--0" };
        /**
         * DNS label that is 1 character short of the maximum.
         */
        @DataPoint
        public static final String ALMOST_TOO_LONG = "o12345670123456701234567012345670123456701234567012345670123456";

        /**
         * Check all of the labels defined as data points.
         * 
         * @param label
         *            the label to check
         */
        @Theory
        public void validDomainLabel(final String label) {
            assertThat(DnsUtils.isValidDomainLabel(label), is(true));
        }

    }

    /**
     * Test invalid domain labels against
     * {@link DnsUtils#isValidDomainLabel(String)}.
     * 
     * @author jschewe
     *
     */
    @RunWith(Theories.class)
    public static class InvalidDomainLabels {

        /**
         * Invalid DNS labels.
         */
        @DataPoints
        public static final String[] INVALID_LABELS = { "01010", "A0c-", "-A0c" };
        /**
         * Label that is past the maximum character length.
         */
        @DataPoint
        public static final String TOO_LONG = "o123456701234567012345670123456701234567012345670123456701234567";
        /**
         * Label that has a dot in it.
         */
        @DataPoint
        public static final String HAS_DOT = "a.b";

        /**
         * Run a unit test for each invalid label.
         * 
         * @param label
         *            the label to check
         */
        @Theory
        public void invalidDomainLabel(final String label) {
            assertThat(DnsUtils.isValidDomainLabel(label), is(false));
        }

    }

}
