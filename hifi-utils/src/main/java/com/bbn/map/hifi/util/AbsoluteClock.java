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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.protelis.utils.SimpleClock;
import com.bbn.protelis.utils.VirtualClock;

/**
 * Implementation of {@link VirtualClock} that is like {@link SimpleClock},
 * except that the current time is the absolute time of the system.
 * 
 * @author jschewe
 *
 */
public class AbsoluteClock implements VirtualClock {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbsoluteClock.class);

    /**
     * The time reported when stopped. This is initially 0, but will change to
     * the time that stop is called. This allows one to keep querying the clock
     * after it's stopped and time not go backwards. Note that this time already
     * has <code>offset</code> applied.
     */
    private long stoppedTime = 0;

    private boolean clockRunning = false;

    @Override
    public boolean isStarted() {
        synchronized (lock) {
            return clockRunning;
        }
    }

    private boolean shutdown = false;

    @Override
    public void shutdown() {
        synchronized (lock) {
            shutdown = true;
            clockRunning = false;
            stoppedTime = -1;
            lock.notifyAll();
        }
    }

    private final Object lock = new Object();

    @Override
    public void startClock() {
        synchronized (lock) {
            if (clockRunning) {
                throw new IllegalStateException("Clock is already running, cannot be started again until stopped");
            }
            if (shutdown) {
                throw new IllegalStateException("Clock has been shutdown, it can no longer be used.");
            }

            clockRunning = true;
            lock.notifyAll();
        }
    }

    @Override
    public void stopClock() {
        synchronized (lock) {
            if (shutdown) {
                throw new IllegalStateException("Clock has been shutdown, it can no longer be used.");
            }
            if (!clockRunning) {
                throw new IllegalStateException("Clock is not running, cannot be stopped");
            }

            stoppedTime = System.currentTimeMillis();
            clockRunning = false;

            // wake everyone up
            lock.notifyAll();
        }
    }

    @Override
    public long getCurrentTime() {
        synchronized (lock) {
            return internalGetCurrentTime();
        }
    }

    /**
     * Must hold the lock when calling this method.
     */
    private long internalGetCurrentTime() {
        if (clockRunning) {
            return System.currentTimeMillis();
        } else {
            return stoppedTime;
        }
    }

    @Override
    public void waitForClockStart() {
        synchronized (lock) {
            if (shutdown) {
                return;
            }

            while (!clockRunning && !shutdown) {
                try {
                    lock.wait();
                } catch (final InterruptedException e) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Interrupted waiting for clock to start", e);
                    }
                }
            }
        }
    }

    @Override
    public void waitForDuration(final long duration) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Enter waitForDuration: " + duration);
        }
        synchronized (lock) {
            if (shutdown) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Returning because shutdown");
                }
                return;
            }
            if (!clockRunning) {
                throw new IllegalStateException("Cannot wait on stopped clock");
            }

            try {
                final long initialNow = internalGetCurrentTime();
                final long wakeTime = initialNow + duration;
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("wakeTime: " + wakeTime + " initialNow: " + initialNow);
                }

                while (clockRunning) {
                    final long now = internalGetCurrentTime();
                    final long remainingSleep = wakeTime - now;
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("remainingSleep: " + remainingSleep + " now: " + now);
                    }
                    if (remainingSleep <= 0) {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Finished with sleep");
                        }

                        break;
                    } else {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Starting wait for: {} ms", remainingSleep);
                        }
                        try {
                            lock.wait(remainingSleep);
                        } catch (final InterruptedException e) {
                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace("Interrupted during sleep", e);
                            }
                        }
                    }
                }
            } catch (final IllegalStateException e) {
                if (!shutdown) {
                    throw e;
                }
            }
        } // lock
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Exiting waitForDuration");
        }
    }

    @Override
    public void waitUntilTime(final long time) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("waitUntilTime: waiting for clock to start");
        }

        waitForClockStart();

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("waitUntilTime: clock is started");
        }

        synchronized (lock) {
            if (shutdown) {
                return;
            }

            while (clockRunning) {
                final long remainingSleep;
                try {
                    final long now = internalGetCurrentTime();
                    remainingSleep = time - now;

                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("remainingSleep: " + remainingSleep + " now: " + now);
                    }

                } catch (final IllegalStateException e) {
                    if (shutdown) {
                        return;
                    } else {
                        throw e;
                    }
                }
                if (remainingSleep > 0) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("waitUntilTime: waiting for " + remainingSleep);
                    }

                    try {
                        lock.wait(remainingSleep);
                    } catch (final InterruptedException e) {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Interrupted during sleep", e);
                        }
                    }
                } else {
                    return;
                }
            }

        }
    }

    @Override
    public boolean isShutdown() {
        synchronized (lock) {
            return shutdown;
        }
    }
}
