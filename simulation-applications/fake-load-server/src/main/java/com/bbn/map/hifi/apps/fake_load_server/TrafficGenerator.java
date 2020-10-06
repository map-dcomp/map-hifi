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
package com.bbn.map.hifi.apps.fake_load_server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.hifi.util.UnitConversions;

/**
 * Generate network traffic by writing data at the specified rate to a stream.
 * Also read any data from the channel that is sent from the other end.
 * 
 * @author jschewe
 *
 */
public class TrafficGenerator {

    private final Logger logger;

    private final double bytesPerSecond;
    private final SocketChannel channel;
    private boolean done;
    private Future<?> writeThread;
    private Future<?> readThread;
    private final Object lock = new Object();
    private final ExecutorService threadPool;

    /**
     * When the sleep is too short, how much to increase it by.
     */
    private static final long SLEEP_INCREASE_INCREMENT = 10;

    @FunctionalInterface
    private interface SocketAddressSupplier {
        SocketAddress get() throws IOException;
    }

    private static SocketAddress getAddressOrNull(final SocketAddressSupplier f) {
        try {
            return f.get();
        } catch (final Throwable e) {
            return null;
        }
    }

    private static Logger getLogger(final SocketChannel channel) {
        final SocketAddress localAddr = getAddressOrNull(channel::getLocalAddress);
        final String local;
        if (localAddr instanceof InetSocketAddress) {
            final InetSocketAddress addr = (InetSocketAddress) localAddr;
            local = String.format("%s_%d", addr.getHostString(), addr.getPort());
        } else {
            local = "unknown";
        }

        final SocketAddress remoteAddr = getAddressOrNull(channel::getRemoteAddress);
        final String remote;
        if (remoteAddr instanceof InetSocketAddress) {
            final InetSocketAddress addr = (InetSocketAddress) remoteAddr;
            remote = String.format("%s_%d", addr.getHostString(), addr.getPort());
        } else {
            remote = "unknown";
        }

        return LoggerFactory.getLogger(String.format("%s.%s.%s", TrafficGenerator.class.getName(), local, remote));
    }

    /**
     * 
     * @param mbps
     *            desired rate - megabits per second
     * @param channel
     *            where to write the traffic and read traffic
     * @param threadPool
     *            used to create threads, must not have a limit otherwise
     *            traffic generation won't happen on time
     */
    public TrafficGenerator(@Nonnull final ExecutorService threadPool,
            final double mbps,
            @Nonnull final SocketChannel channel) {
        this.threadPool = Objects.requireNonNull(threadPool);
        this.logger = getLogger(channel);

        this.bytesPerSecond = UnitConversions.megabitsToBytes(mbps);
        this.channel = Objects.requireNonNull(channel);

        logger.debug("{} mega bits per second -> {} bytes per second", mbps, this.bytesPerSecond);
    }

    private void generateTraffic() {
        logger.info("Traffic generation thread is starting");

        double runningRemainder = 0;

        // start sleep at 100 ms
        long sleep = 100;

        // initial allocation is 100 bytes
        ByteBuffer buffer = null;

        while (!isDone()) {
            try {
                final long start = System.currentTimeMillis();

                final double sleepFractionOfSecond = sleep / 1000.0;
                logger.trace("Sleep fraction {}", sleepFractionOfSecond);

                final double bytesToSendFraction = bytesPerSecond * sleepFractionOfSecond;
                logger.trace("To send fraction {}", bytesToSendFraction);

                final int bytesToSendMax = (int) Math.ceil(bytesToSendFraction);

                if (null == buffer || buffer.capacity() < bytesToSendMax) {
                    final ByteBuffer newBuffer = ByteBuffer.allocate(bytesToSendMax);
                    if (null != buffer) {
                        newBuffer.put(buffer);
                    }
                    buffer = newBuffer;
                }

                // get ready to send
                buffer.rewind();

                int bytesToSend = (int) Math.floor(bytesToSendFraction);

                // keep track of the remainder so that we catch up eventually
                runningRemainder += bytesToSendFraction - bytesToSend;
                if (runningRemainder >= 1) {
                    ++bytesToSend;
                    runningRemainder -= 1;
                }

                logger.trace("Sending {} bytes", bytesToSend);
                buffer.limit(bytesToSend);
                channel.write(buffer);

                final long end = System.currentTimeMillis();
                final long computeTime = end - start;
                if (computeTime > sleep) {
                    sleep += SLEEP_INCREASE_INCREMENT;

                    logger.debug("Compute and send took too long, increased sleep time to {}. Increment is {}.", sleep,
                            SLEEP_INCREASE_INCREMENT);
                } else {
                    final long remainingSleep = sleep - computeTime;
                    logger.trace("Sleeping for {} ms", remainingSleep);
                    try {
                        Thread.sleep(remainingSleep);
                    } catch (final InterruptedException e) {
                        logger.debug("Got interrupted waiting for next network burst", e);
                    }
                }
            } catch (final ClosedByInterruptException e) {
                logger.debug(
                        "Channel was closed by an interrupt. The done status will get checked at the top of the loop.",
                        e);
            } catch (final AsynchronousCloseException e) {
                logger.debug(
                        "Channel was closed by another thread. The done status will get checked at the top of the loop.",
                        e);
            } catch (final IOException e) {
                logger.error("Unexpected I/O error on channel, stopping traffic generation", e);
                break;
            }
        } // ! done

        logger.info("Traffic generation thread is exiting");
    }

    private static final int BYTES_TO_READ = 1024;

    private void readTraffic() {
        logger.info("Traffic read thread is starting");

        final ByteBuffer buffer = ByteBuffer.allocate(BYTES_TO_READ);

        while (!isDone()) {
            try {
                final int bytesRead = channel.read(buffer);
                logger.trace("Read {} bytes", bytesRead);
                buffer.clear();
            } catch (final ClosedByInterruptException e) {
                logger.debug(
                        "Channel was closed by an interrupt. The done status will get checked at the top of the loop.",
                        e);
            } catch (final AsynchronousCloseException e) {
                logger.debug(
                        "Channel was closed by another thread. The done status will get checked at the top of the loop.",
                        e);
            } catch (final IOException e) {
                logger.error("Unexpected I/O error on channel, stopping read of traffic", e);
                break;
            }
        } // ! done

        logger.info("Traffic read thread is exiting");
    }

    private boolean isDone() {
        synchronized (lock) {
            return done;
        }
    }

    /**
     * Stop generating and reading traffic.
     */
    public void stopGenerating() {
        logger.info("Stopping traffic generation");
        synchronized (lock) {
            done = true;
            if (null != writeThread) {
                writeThread.cancel(true);
                writeThread = null;
            }
            if (null != readThread) {
                readThread.cancel(true);
                readThread = null;
            }
        }
    }

    /**
     * Start generating and reading traffic.
     */
    public void startGenerating() {
        logger.info("Starting traffic generation");
        synchronized (lock) {
            if (null != writeThread && !writeThread.isDone()) {
                logger.warn("Write thread already running, ignoring additional start");
            } else {
                writeThread = threadPool.submit(this::generateTraffic);
            }

            if (null != readThread && !readThread.isDone()) {
                logger.warn("Read thread already running, ignoring additional start");
            } else {
                readThread = threadPool.submit(this::readTraffic);
            }

            done = false;
        }
    }
}
