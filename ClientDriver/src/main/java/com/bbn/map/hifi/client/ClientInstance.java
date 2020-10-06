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

import javax.annotation.concurrent.ThreadSafe;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.simulator.ClientLoad;

/**
 * Common interface for all types of clients. All implementations must be thread
 * safe.
 * 
 * @author jschewe
 *
 */
@ThreadSafe
public interface ClientInstance {

    /**
     * Start the client instance. Well-behaved clients should stop themselves.
     * The {@link #stopClient()} method is used by {@link ClientDriver} to force
     * the client to stop if it's not behaving.
     * 
     * @throws IllegalStateException
     *             if the instance is already started
     */
    void startClient() throws IllegalStateException;

    /**
     * @return is the process running
     */
    boolean isClientRunning();

    /**
     * Stop the instance. If it's already stopped, this is a nop. This may be
     * called from the instance or from {@link ClientDriver} to force the client
     * to stop after the end of the request duration.
     */
    void stopClient();

    /**
     * Get the name of the client for logging purposes.
     * 
     * @param request
     *            the request
     * @param clientIndex
     *            the index of the request
     * @return a string suitable for a logger name
     */
    default String getClientName(final ClientLoad request, final int clientIndex) {
        final ApplicationCoordinates serviceIdentifier = request.getService();
        return String.format("%s.%s.%s_%d_%d", serviceIdentifier.getGroup(), serviceIdentifier.getArtifact(),
                serviceIdentifier.getVersion(), request.getStartTime(), clientIndex);
    }

}
