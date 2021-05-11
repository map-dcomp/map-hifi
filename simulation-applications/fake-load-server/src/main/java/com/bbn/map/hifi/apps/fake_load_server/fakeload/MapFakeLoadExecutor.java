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
package com.bbn.map.hifi.apps.fake_load_server.fakeload;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martensigwart.fakeload.DefaultFakeLoadExecutor;
import com.martensigwart.fakeload.FakeLoad;
import com.martensigwart.fakeload.FakeLoadExecutor;
import com.martensigwart.fakeload.FakeLoadScheduler;

/**
 * MAP implementation of {@link FakeLoadExecutor}. This is based on
 * {@link DefaultFakeLoadExecutor}, however it exposes the failure to generate
 * load rather than just printing a stack trace.
 *
 * @see FakeLoadExecutor
 * @see FakeLoadScheduler
 * @see FakeLoad
 */
public class MapFakeLoadExecutor implements FakeLoadExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapFakeLoadExecutor.class);

    /**
     * The scheduler used for scheduling {@link FakeLoad} objects for execution.
     */
    private final FakeLoadScheduler scheduler;

    /**
     * Creates a new {@code DefaultFakeLoadExecutor} instance.
     * 
     * @param scheduler
     *            the scheduler to be used by the newly created executor
     */
    public MapFakeLoadExecutor(final FakeLoadScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * @throws LoadFailedException
     *             when the load generation fails to complete
     */
    @Override
    public void execute(final FakeLoad load) throws LoadFailedException {
        try {
            LOGGER.info("Starting FakeLoad execution...");
            LOGGER.debug("Executing {}", load);
            Future<Void> future = scheduler.schedule(load);
            future.get();
            LOGGER.info("Finished FakeLoad execution.");
        } catch (InterruptedException | ExecutionException e) {
            throw new LoadFailedException("Load generation failed", e);
        }
    }

}
