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
package com.bbn.map.hifi_resmgr;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.AgentConfiguration;

/**
 * Fetch docker images using docker pull.
 * 
 * @author jschewe
 *
 */
public class DockerFetcher implements ImageFetcher {
    private static final Logger LOGGER = LogManager.getLogger(DockerFetcher.class);

    // Can use multiple threads if we want, but for now we only pull 1 image at
    // a time
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @Override
    public void fetchImage(@Nonnull final String image, @Nonnull final ImageFetcher.Callback callback) {
        pool.execute(() -> fetchWithRetry(image, callback));
    }

    private final Random pullBackoffRandom = new Random();

    private void fetchWithRetry(final String image, final ImageFetcher.Callback callback) {
        // attempt to pull image to ensure that it is on the local machine
        final Duration minPullBackoff = Duration.ofSeconds(AgentConfiguration.getInstance().getPullMinBackoffSeconds());
        final Duration maxPullBackoff = Duration.ofSeconds(AgentConfiguration.getInstance().getPullMaxBackoffSeconds());
        final long pullBackoffInterval = maxPullBackoff.toMillis() - minPullBackoff.toMillis();

        boolean pullResult = false;
        int attempt = 0;
        while (!pullResult && attempt < AgentConfiguration.getInstance().getMaxPullAttemps()) {
            pullResult = SimpleDockerResourceManager.pullDockerImage(image,
                    SimpleDockerResourceManager.DEFAULT_DOCKER_IMAGE_TAG);
            if (!pullResult) {
                boolean lastAttempt = (attempt >= AgentConfiguration.getInstance().getMaxPullAttemps() - 1);
                        
                if (!lastAttempt) {
                    final long positiveRandom = Math.abs(pullBackoffRandom.nextLong() / 2);
                    final long sleepMs = positiveRandom % pullBackoffInterval + minPullBackoff.toMillis();
                    
                    LOGGER.warn("Pull attempt {} failed. Waiting {} ms before next attempt.", attempt, sleepMs);
                    
                    try {
                        Thread.sleep(sleepMs);
                    } catch (final InterruptedException e) {
                        LOGGER.warn("Got interrupted sleeping between pull attempts", e);
                    }
                } else {
                    LOGGER.warn("Last pull attempt ({}) failed.", attempt);                    
                }
            }
        }
        LOGGER.info("Pulling docker container result: " + pullResult);
        callback.apply(image, pullResult);

    }

}
