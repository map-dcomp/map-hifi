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
package com.bbn.map.hifi;

import javax.annotation.Nonnull;

import org.apache.commons.net.util.SubnetUtils;

import com.bbn.map.Controller;
import com.bbn.map.hifi_resmgr.SimpleDockerResourceManager;
import com.bbn.map.simulator.HardwareConfiguration;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.ResourceManager;
import com.bbn.protelis.networkresourcemanagement.ResourceManagerFactory;
import com.bbn.protelis.utils.VirtualClock;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Creates instances of {@link SimpleDockerResourceManager}. It is expected that
 * each instance of this class will only create a {@link ResourceManager} for a
 * single node since all resource managers will be given the same list of
 * container names to use.
 * 
 * @author jschewe
 *
 */
public class SimpleDockerResourceManagerFactory implements ResourceManagerFactory<Controller> {

    private final VirtualClock clock;
    private final long pollingInterval;
    private final ImmutableList<NodeIdentifier> containerNames;
    private final String dockerRegistryHostname;
    private final FileRegionLookupService regionLookupService;
    private final int apPort;
    private final ImmutableMap<String, Double> ipToSpeed;
    private final HardwareConfiguration hardwareConfig;
    private final ImmutableCollection<SubnetUtils.SubnetInfo> excludedSubnets;
    private final String imageFetcherClassname;

    /**
     * 
     * @param clock
     *            the clock to pass along
     * @param pollingInterval
     *            the interval to poll for resource reports
     * @param containerNames
     *            the names of containers to be used
     * @param dockerRegistryHostname
     *            the host name of the Docker registry
     * @param regionLookupService
     *            passed to the constructor for
     *            {@link SimpleDockerResourceManager}
     * @param apPort
     *            the port that AP communicates on
     * @param ipToSpeed
     *            passed to {@link SimpleDockerResourceManager}
     * @param hardwareConfig
     *            passed to {@link SimpleDockerResourceManager}
     * @param excludedSubnets
     *            passed to {@link SimpleDockerResourceManager}
     * @param imageFetcherClassname
     *            passed to {@link SimpleDockerResourceManager}
     */
    public SimpleDockerResourceManagerFactory(@Nonnull final VirtualClock clock,
            final long pollingInterval,
            @Nonnull final ImmutableList<NodeIdentifier> containerNames,
            @Nonnull final String dockerRegistryHostname,
            @Nonnull final FileRegionLookupService regionLookupService,
            final int apPort,
            @Nonnull final ImmutableMap<String, Double> ipToSpeed,
            @Nonnull final HardwareConfiguration hardwareConfig,
            @Nonnull final ImmutableCollection<SubnetUtils.SubnetInfo> excludedSubnets,
            @Nonnull final String imageFetcherClassname) {
        this.clock = clock;
        this.pollingInterval = pollingInterval;
        this.containerNames = containerNames;
        this.dockerRegistryHostname = dockerRegistryHostname;
        this.regionLookupService = regionLookupService;
        this.apPort = apPort;
        this.ipToSpeed = ipToSpeed;
        this.hardwareConfig = hardwareConfig;
        this.excludedSubnets = excludedSubnets;
        this.imageFetcherClassname = imageFetcherClassname;
    }

    @Override
    @Nonnull
    public ResourceManager<Controller> createResourceManager() {

        final SimpleDockerResourceManager manager = new SimpleDockerResourceManager(clock, pollingInterval,
                containerNames, dockerRegistryHostname, regionLookupService, apPort, ipToSpeed, hardwareConfig,
                excludedSubnets, imageFetcherClassname);

        return manager;
    }

}
