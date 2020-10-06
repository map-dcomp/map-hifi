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
package com.bbn.map.hifi.client;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.google.common.collect.ImmutableMap;

/**
 * Test {@link ClientServiceConfiguration} serialization issues.
 * 
 * @author jschewe
 *
 */
public class ClientServiceConfigurationSerialization {

    private static final ApplicationCoordinates EXPECTED_SERVICE = new ApplicationCoordinates("com.bbn",
            "face-recognition", "1");

    /**
     * Test that reading a file before pre-start was added.
     * 
     * @throws URISyntaxException
     *             This is an internal test error
     * @throws IOException
     *             the test failed
     */
    @Test
    public void testReadBeforePreStart() throws URISyntaxException, IOException {
        final URL url = ClientServiceConfigurationSerialization.class
                .getResource("data/ClientServiceConfiguration_beforePreStart.json");
        assertThat(url, notNullValue());

        final Path path = Paths.get(url.toURI());
        final ImmutableMap<ApplicationCoordinates, ClientServiceConfiguration> actual = ClientServiceConfiguration
                .parseClientServiceConfigurations(path);
        assertThat(actual, notNullValue());

        final ClientServiceConfiguration actualConfig = actual.get(EXPECTED_SERVICE);
        assertThat(actualConfig, notNullValue());
    }

    /**
     * Test that reading a file that has pre-start added.
     * 
     * @throws URISyntaxException
     *             This is an internal test error
     * @throws IOException
     *             the test failed
     */
    @Test
    public void testReadPreStart() throws URISyntaxException, IOException {
        final URL url = ClientServiceConfigurationSerialization.class
                .getResource("data/ClientServiceConfiguration_preStart.json");
        assertThat(url, notNullValue());

        final Path path = Paths.get(url.toURI());
        final ImmutableMap<ApplicationCoordinates, ClientServiceConfiguration> actual = ClientServiceConfiguration
                .parseClientServiceConfigurations(path);
        assertThat(actual, notNullValue());

        final ClientServiceConfiguration actualConfig = actual.get(EXPECTED_SERVICE);
        assertThat(actualConfig, notNullValue());

        assertThat(actualConfig.getPreStartType(), is(ClientServiceConfiguration.ExecutionType.EXTERNAL_PROCESS));
    }

}
