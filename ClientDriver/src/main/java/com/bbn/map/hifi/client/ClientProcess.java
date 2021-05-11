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

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.hifi.util.ProcessExecutor;
import com.bbn.map.simulator.ClientLoad;

/**
 * A client that executes as an external process.
 * 
 * @author jschewe
 *
 */
public class ClientProcess implements ClientInstance {

    private final ProcessExecutor executor;
    private final Logger logger;
    private final ClientLoad request;

    /**
     * 
     * @param builder
     *            the builder for processes
     * @param request
     *            the client request, used for naming of the logger and
     *            determining when to stop the client
     * @param clientName
     *            used for logging
     */
    /* package */ ClientProcess(@Nonnull final ProcessBuilder builder,
            @Nonnull final ClientLoad request,
            String clientName) {
        this.request = Objects.requireNonNull(request);
        Objects.requireNonNull(builder);
        Objects.requireNonNull(request);
        logger = LoggerFactory.getLogger(String.format("%s.%s", ClientProcess.class.getName(), clientName));
        this.executor = new ProcessExecutor(builder, this.logger);
    }

    @Override
    public void startClient() throws IllegalStateException {
        this.executor.startProcess();

        final Timer stopTimer = new Timer("Client stop timer for " + logger.getName(), true);

        final long duration = Math.max(request.getNetworkDuration(), request.getServerDuration());
        stopTimer.schedule(new TimerTask() {
            public void run() {
                // stop the client at the end of the duration
                stopClient();
            }
        }, duration);

    }

    @Override
    public boolean isClientRunning() {
        return this.executor.isRunning();
    }

    @Override
    public void stopClient() {
        this.executor.stopProcess();
    }

}
