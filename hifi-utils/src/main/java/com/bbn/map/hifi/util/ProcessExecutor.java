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

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

/**
 * Execute a process and have the ability to stop it and ensure it gets cleaned
 * up.
 * 
 * @author jschewe
 *
 */
public class ProcessExecutor {

    private final Logger logger;
    private Process process = null;
    private final Object lock = new Object();
    private final ProcessBuilder builder;

    /**
     * 
     * @param builder
     *            the builder for processes
     * @param logger
     *            where to log output
     */
    public ProcessExecutor(@Nonnull final ProcessBuilder builder, @Nonnull final Logger logger) {
        this.builder = Objects.requireNonNull(builder);
        this.logger = Objects.requireNonNull(logger);
    }

    /**
     * Start the process and log it's output.
     * 
     * @throws IllegalStateException
     *             if the process is already running
     */
    public void startProcess() throws IllegalStateException {
        synchronized (lock) {
            if (null != process) {
                throw new IllegalStateException("Cannot start process when it's already running");
            }

            try {
                process = builder.start();
                stop = false;
                logProcessOutput();
                logProcessError();
            } catch (final IOException e) {
                logger.error("Unable to start process: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 
     * @return is the process running
     */
    public boolean isRunning() {
        synchronized (lock) {
            return null != process && process.isAlive();
        }
    }

    /**
     * If the process doesn't die after this much time, force kill.
     */
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Stop the process.
     * 
     * @see Process#destroy()
     * @see Process#destroyForcibly()
     */
    public void stopProcess() {
        logger.info("Stopping");
        synchronized (lock) {
            stop = true;
            if (null != process) {
                process.destroy();
                try {
                    process.waitFor(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                } catch (final InterruptedException e) {
                    logger.debug("Interrupted waiting for client process to exit", e);
                }
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
                process = null;
            }
        }
    }

    private volatile boolean stop = false;

    private void logProcessOutput() {
        new Thread(() -> ProcessUtils.logProcessStream(logger, process.getInputStream(), "standard output",
                logger::info, () -> stop), logger.getName() + " standard output handler").start();
    }

    private void logProcessError() {
        new Thread(() -> ProcessUtils.logProcessStream(logger, process.getErrorStream(), "standard error", logger::info,
                () -> stop), logger.getName() + " error output handler").start();
    }

}
