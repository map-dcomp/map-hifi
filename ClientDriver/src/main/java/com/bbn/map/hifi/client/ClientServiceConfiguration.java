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
package com.bbn.map.hifi.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nonnull;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Specifies information about how to start and stop a client for a service.
 * 
 * @author jschewe
 *
 */
public class ClientServiceConfiguration {

    /**
     * Specify how to start a client.
     * 
     * @author jschewe
     *
     */
    public enum ExecutionType {
    /**
     * Execute an external process. The arguments are the command and arguments
     * to execute.
     */
    EXTERNAL_PROCESS,
    /**
     * Execute a Java class.
     */
    JAVA_CLASS;
    }

    /**
     * Specify how to stop a client.
     * 
     * @author jschewe
     *
     */
    public enum StopType {
        /**
         * Kill the running process.
         */
        KILL_PROCESS;
    }

    /**
     * @return check if the configuration object is valid
     */
    @JsonIgnore
    public boolean isValid() {
        return null != service && null != startType;
    }

    private ApplicationCoordinates service = null;

    /**
     * 
     * @return the service identifier
     */
    public ApplicationCoordinates getService() {
        return service;
    }

    @SuppressWarnings("unused") // JSON serialzation
    private void setService(final ApplicationCoordinates v) {
        service = v;
    }

    private ExecutionType preStartType;

    /**
     * 
     * @return specify a command to execute for the service on the client before
     *         the client starts. This may be null, meaning there is nothing to
     *         do.
     */
    public ExecutionType getPreStartType() {
        return preStartType;
    }

    @SuppressWarnings("unused") // JSON serialzation
    private void setPreStartType(final ExecutionType v) {
        preStartType = v;
    }

    private ImmutableList<String> preStartArguments = ImmutableList.of();

    /**
     * The pre-start arguments are interpreted based on the
     * {@Link #getPreStartType()}.
     * 
     * @return the pre-start arguments
     */
    public ImmutableList<String> getPreStartArguments() {
        return preStartArguments;
    }

    @SuppressWarnings("unused") // JSON serialzation
    private void setPreStartArguments(final ImmutableList<String> v) {
        preStartArguments = v;
    }

    private ExecutionType startType;

    /**
     * 
     * @return specify how to start the client
     */
    public ExecutionType getStartType() {
        return startType;
    }

    @SuppressWarnings("unused") // JSON serialzation
    private void setStartType(final ExecutionType v) {
        startType = v;
    }

    private ImmutableList<String> startArguments = ImmutableList.of();

    /**
     * The start arguments are interpreted based on the {@Link #getStartType()}.
     * 
     * @return the start arguments
     */
    public ImmutableList<String> getStartArguments() {
        return startArguments;
    }

    @SuppressWarnings("unused") // JSON serialzation
    private void setStartArguments(final ImmutableList<String> v) {
        startArguments = v;
    }

    /**
     * Read in the client service configurations.
     * 
     * @param path
     *            the path to the file to read
     * @return the data read from the file, empty of the file doesn't exist
     * @throws IOException
     *             when there is an error reading the file
     */
    public static ImmutableMap<ApplicationCoordinates, ClientServiceConfiguration> parseClientServiceConfigurations(
            @Nonnull final Path path) throws IOException {
        if (!Files.exists(path)) {
            // no hardware configs
            return ImmutableMap.of();
        }

        // don't fail on unknown properties to handle version upgrades where
        // there are extra fields in the file that have since been removed
        final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            final ImmutableList<ClientServiceConfiguration> list = mapper.readValue(reader,
                    new TypeReference<ImmutableList<ClientServiceConfiguration>>() {
                    });
            final ImmutableMap.Builder<ApplicationCoordinates, ClientServiceConfiguration> map = ImmutableMap.builder();
            list.forEach(config -> {
                final ApplicationCoordinates service = config.getService();

                map.put(service, config);
            });

            return map.build();
        }

    }

}
