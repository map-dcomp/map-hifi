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
package com.bbn.map.hifi.util.network;

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

import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.hifi.util.UnitConversions;

/**
 * Generate network traffic by writing data at the specified rate to a stream.
 * Also read any data from the channel that is sent from the other end.
 * 
 * Expected order of operations is:
 * <li>
 * <ol>
 * construction
 * </ol>
 * <ol>
 * {@link #startGenerating()}
 * </ol>
 * <ol>
 * {@link #sendSuccess()} or {@link #sendFailure()} and/or
 * {@link #stopWriting()}
 * </ol>
 * <ol>
 * {@link #shutdown()}
 * </ol>
 * </li>
 * 
 * @author jschewe
 *
 */
public class TrafficGenerator implements Runnable {

    /**
     * How many bytes to read at a time.
     */
    private static final int BYTES_TO_READ = 1024;

    private final Logger logger;

    private long duration;

    private final double bytesPerSecond;
    private final SocketChannel channel;
    private boolean done = false;
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
     * @param duration
     *            time to run in milliseconds, a value less than or equal to
     *            zero runs forever
     * 
     * @param threadPool
     *            used to create threads, must not have a limit otherwise
     *            traffic generation won't happen on time
     */
    public TrafficGenerator(@Nonnull final ExecutorService threadPool,
            final double mbps,
            final long duration,
            @Nonnull final SocketChannel channel) {
        this.threadPool = Objects.requireNonNull(threadPool);
        this.logger = getLogger(channel);

        this.bytesPerSecond = UnitConversions.megabitsToBytes(mbps);
        this.duration = duration;
        this.channel = Objects.requireNonNull(channel);

        logger.debug("{} mega bits per second -> {} bytes per second", mbps, this.bytesPerSecond);
    }

    private boolean sendSuccess = false;
    private boolean doneWriting = false;
    /**
     * Byte to signify success.
     */
    public static final byte SUCCESS = 1;

    /**
     * Byte to signify failure.
     */
    public static final byte FAILURE = 2;

    private static final int MESSAGE_SIZE = 2 * BYTES_TO_READ;
    /**
     * Message to send for success. The size of this message larger than
     * BYTES_TO_READ to ensure that it's read after being sent and there aren't
     * buffering issues.
     */
    private static final ByteBuffer SUCCESS_MESSAGE = ByteBuffer.allocate(MESSAGE_SIZE);

    /**
     * Message to send for failure. The size of this message larger than
     * BYTES_TO_READ to ensure that it's read after being sent and there aren't
     * buffering issues.
     */
    private static final ByteBuffer FAILURE_MESSAGE = ByteBuffer.allocate(MESSAGE_SIZE);

    static {
        // populate the messages
        for (int i = 0; i < MESSAGE_SIZE; ++i) {
            SUCCESS_MESSAGE.put(SUCCESS);
            FAILURE_MESSAGE.put(FAILURE);
        }
    }

    private final Object shutdown = new Object();
    private boolean shutdownComplete = false;

    /**
     * Wait for shutdown and the channel to be closed.
     * 
     * @throws InterruptedException
     *             if something interrupts the wait
     */
    public void waitForShutdown() throws InterruptedException {
        synchronized (shutdown) {
            while (!shutdownComplete) {
                shutdown.wait();
            }
        }
    }

    private boolean isDoneWriting() {
        synchronized (lock) {
            return doneWriting;
        }
    }

    /**
     * Send a success message.
     */
    public void sendSuccess() {
        logger.debug("sendSuccess: before synchronized (lock)");
        synchronized (lock) {
            logger.debug("sendSuccess: in synchronized (lock)");
            sendSuccess = true;
        }
        logger.debug("sendSuccess: before synchronized (writeSleep)");
        synchronized (writeSleep) {
            logger.debug("sendSuccess: in synchronized (writeSleep)");
            writeSleep.notifyAll();
        }
        logger.debug("sendSuccess: end");
    }

    /**
     * Stop writing data, but keep the socket open for possibly
     * {@link #sendFailure()} and {@link #sendSuccess()}.
     */
    public void stopWriting() {
        synchronized (lock) {
            doneWriting = true;
        }
    }

    private boolean sendFailure = false;

    /**
     * Send a failure message. This causes the traffic generation to stop after
     * the message is sent.
     */
    public void sendFailure() {
        logger.debug("sendFailure: before synchronized (lock)");
        synchronized (lock) {
            logger.debug("sendFailure: in synchronized (lock)");
            sendFailure = true;
            // no sense in writing anymore data
            doneWriting = true;
        }
        logger.debug("sendFailure: before synchronized (writeSleep)");
        synchronized (writeSleep) {
            logger.debug("sendFailure: in synchronized (writeSleep)");
            writeSleep.notifyAll();
        }
        logger.debug("sendFailure: end");
    }

    /**
     * Shutdown traffic generation.
     */
    public void shutdown() {
        logger.debug("shutdown: before synchronized (lock)");
        synchronized (lock) {
            logger.debug("shutdown: in synchronized (lock)");
            done = true;
        }
        logger.debug("shutdown: before synchronized (runDurationLock)");
        synchronized (runDurationLock) {
            logger.debug("shutdown: in synchronized (runDurationLock)");
            runDurationLock.notifyAll();
        }
        logger.debug("shutdown: before synchronized (writeSleep)");
        synchronized (writeSleep) {
            logger.debug("shutdown: in synchronized (writeSleep)");
            writeSleep.notifyAll();
        }
        logger.debug("shutdown: end");
    }

    private static final double ZERO_TOLERANCE = 1E-6;

    /**
     * Used to handle the sleep for writing traffic;
     */
    private final Object writeSleep = new Object();

    private void generateTraffic() {
        try (CloseableThreadContext.Instance ignored = CloseableThreadContext.push("generateTraffic")) {
            logger.info("Traffic generation thread is starting");

            if (bytesPerSecond < ZERO_TOLERANCE) {
                logger.info("Requested rate is effectively zero, not generating any traffic.");
                stopWriting();
            }

            double runningRemainder = 0;

            // start sleep at 100 ms
            long sleep = 100;

            ByteBuffer buffer = null;

            logger.debug("generateTraffic: before while done: {}, doneWriting: {}, sendSuccess: {}, sendFailure: {}", done, doneWriting, sendSuccess, sendFailure);
            while (!isDone()) {
                try {
                    final long start = System.currentTimeMillis();

                    if (!isDoneWriting()) {
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

                        int bytesToSend = Math.max(1, (int) Math.floor(bytesToSendFraction));

                        // keep track of the remainder so that we catch up
                        // eventually
                        runningRemainder += bytesToSendFraction - bytesToSend;
                        if (runningRemainder >= 1) {
                            ++bytesToSend;
                            runningRemainder -= 1;
                        }

                        // get ready to send
                        buffer.rewind();

                        logger.trace("Sending {} bytes", bytesToSend);
                        buffer.limit(bytesToSend);
                        sendBuffer(buffer);
                    } else {
                        logger.trace("generateTraffic: done writing but still in loop done: {}, doneWriting: {}", done, doneWriting);
                    }

                    possiblyWriteFailure();
                    possiblyWriteSuccess();

                    final long end = System.currentTimeMillis();
                    final long computeTime = end - start;
                    if (computeTime > sleep) {
                        sleep += SLEEP_INCREASE_INCREMENT;

                        logger.debug("Compute and send took too long, increased sleep time to {}. Increment is {}.",
                                sleep, SLEEP_INCREASE_INCREMENT);
                    } else {
                        final long remainingSleep = sleep - computeTime;
                        logger.trace("Sleeping for {} ms", remainingSleep);
                        try {
                            synchronized (writeSleep) {
                                writeSleep.wait(remainingSleep);
                            }
                        } catch (final InterruptedException e) {
                            logger.debug("Got interrupted waiting for next network burst", e);
                        }
                    }
                } catch (final ClosedByInterruptException e) {
                    logger.info("Channel was closed by an interrupt. Stopping generate.", e);
                    break;
                } catch (final AsynchronousCloseException e) {
                    logger.info("Channel was closed by another thread. Stopping generate.", e);
                    break;
                } catch (final IOException e) {
                    logger.error("Unexpected I/O error on channel, stopping traffic generation", e);
                    break;
                }
            } // ! done
            logger.debug("generateTraffic: after while done: {}, doneWriting: {}, sendSuccess: {}, sendFailure: {}", done, doneWriting, sendSuccess, sendFailure);
        } catch (final RuntimeException e) {
            logger.error("Got error in [generateTraffic]", e);

            for (final Throwable suppressed : e.getSuppressed()) {
                logger.error("Got suppressed error in [generateTraffic]:", suppressed);
            }
            throw e;
        } finally {
            logger.debug("generateTraffic: start finally done: {}, doneWriting: {}, sendSuccess: {}, sendFailure: {}", done, doneWriting, sendSuccess, sendFailure);

            try {
                possiblyWriteFailure();
            } catch (final IOException e) {
                logger.error("Got error writing final failure", e);
            }
            try {
                possiblyWriteSuccess();
            } catch (final IOException e) {
                logger.error("Got error writing final success", e);
            }

            logger.debug("generateTraffic: after final possiblyWriteFailure and possiblyWriteSuccess done: {}, doneWriting: {}, sendSuccess: {}, sendFailure: {}", done, doneWriting, sendSuccess, sendFailure);

            // clean up the read side
            if (null != readThread) {
                readThread.cancel(true);
                readThread = null;
            }

            // close the socket
            logger.debug("Closing channel");
            try {
                channel.close();
            } catch (final IOException e) {
                logger.debug("Exception closing channel at the end of traffic generation", e);
            }

            logger.info("Traffic generation thread is exiting");
            logger.debug("generateTraffic: Traffic generation thread is exiting done: {}, doneWriting: {}", done, doneWriting);
            
            synchronized (shutdown) {
                shutdownComplete = true;
                logger.info("Notifying of shutdown");
                shutdown.notifyAll();
            }

            logger.debug("generateTraffic: end finally");
        }
    }

    private void possiblyWriteSuccess() throws IOException {
        logger.trace("possiblyWriteSuccess: before synchronized (lock)");
        synchronized (lock) {
            logger.trace("possiblyWriteSuccess: in synchronized (lock) done: {}, sendSuccess: {}, doneWriting: {}", done, sendSuccess, doneWriting);
            if (sendSuccess) {
                logger.info("Sending success message");
                SUCCESS_MESSAGE.rewind();
                channel.write(SUCCESS_MESSAGE);
                // note already sent
                sendSuccess = false;
            }
        }
        logger.trace("possiblyWriteSuccess: after synchronized (lock)");
    }

    private void possiblyWriteFailure() throws IOException {
        logger.trace("possiblyWriteFailure: before synchronized (lock)");
        synchronized (lock) {
            logger.trace("possiblyWriteFailure: in synchronized (lock) done: {}, sendFailure: {}, doneWriting: {}", done, sendFailure, doneWriting);
            if (sendFailure) {
                logger.info("Sending failure message");
                FAILURE_MESSAGE.rewind();
                channel.write(FAILURE_MESSAGE);
                done = true;
                // note already sent
                sendFailure = false;
            }
        }
        logger.trace("possiblyWriteFailure: after synchronized (lock)");
    }

    private void sendBuffer(final ByteBuffer buffer) throws IOException {
        if (null != buffer) {
            channel.write(buffer);
        }
    }

    private boolean failure = false;

    /**
     * Note that it is possible, although unlikely, that both success and
     * failure can be read.
     * 
     * @return true if a failure message was read
     */
    public boolean readFailureMessage() {
        synchronized (lock) {
            return failure;
        }
    }

    private boolean success = false;

    /**
     * 
     * @return true if a success message was read
     */
    public boolean readSuccessMessage() {
        synchronized (lock) {
            return success;
        }
    }

    private void readTraffic() {
        try (CloseableThreadContext.Instance ignored = CloseableThreadContext.push("readTraffic")) {
            logger.info("Traffic read thread is starting");

            final ByteBuffer buffer = ByteBuffer.allocate(BYTES_TO_READ);
            while (!isDone()) {
                try {
                    final int bytesRead = channel.read(buffer);
                    if (bytesRead == -1) {
                        logger.warn("Got end of stream, exiting readTraffic");
                        // don't shutdown in case we can still write to the
                        // other side
                        break;
                    } else {
                        logger.trace("Read {} bytes", bytesRead);
                    }

                    // scan buffer for failure or success
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        final byte b = buffer.get();
                        if (TrafficGenerator.FAILURE == b) {
                            logger.info("Read failure message");
                            synchronized (lock) {
                                failure = true;
                            }
                            shutdown();
                            break;
                        } else if (TrafficGenerator.SUCCESS == b) {
                            logger.info("Read success message");
                            synchronized (lock) {
                                success = true;
                            }
                            if (isDoneWriting()) {
                                // we are completely done
                                shutdown();
                            }
                            break;
                        }
                    }

                    buffer.clear();
                } catch (final ClosedByInterruptException e) {
                    logger.info("Channel was closed by an interrupt. Stopping read.", e);
                    break;
                } catch (final AsynchronousCloseException e) {
                    logger.info("Channel was closed by another thread. Stopping read.", e);
                    break;
                } catch (final IOException e) {
                    logger.error("Unexpected I/O error on channel, stopping read of traffic", e);
                    break;
                }
            } // ! done

            logger.info("Traffic read thread is exiting");
        } // logger context
    }

    private boolean isDone() {
        synchronized (lock) {
            return done;
        }
    }

    /**
     * Start generating and reading traffic.
     */
    public void startGenerating() {
        logger.info("Starting traffic generation");
        synchronized (lock) {
            logger.debug("startGenerating: in synchronized (lock)");
            done = false;
            
            if (null != writeThread && !writeThread.isDone()) {
                logger.warn("Write thread already running, ignoring additional start");
            } else {
                writeThread = threadPool.submit(this::generateTraffic);
                logger.debug("startGenerating: submit(this::generateTraffic)");
            }

            if (null != readThread && !readThread.isDone()) {
                logger.warn("Read thread already running, ignoring additional start");
            } else {
                readThread = threadPool.submit(this::readTraffic);
                logger.debug("startGenerating: submit(this::readTraffic)");
            }
        }

        logger.debug("startGenerating: after synchronized (lock)");
    }

    /**
     * Used to track how long the traffic should be generated for.
     */
    private final Object runDurationLock = new Object();

    /**
     * Execute the traffic generator for the specified duration. At the end of
     * the duration writing stops, but reading is still done to watch for a
     * failure or success message until {@link #shutdown()} is called.
     */
    @Override
    public void run() {
        try {
            startGenerating();
            logger.debug("run: before synchronized (runDurationLock)");
            synchronized (runDurationLock) {
                if (duration > 0) {
                    logger.debug("run: runDurationLock.wait({})", duration);
                    runDurationLock.wait(duration);
                } else {
                    logger.debug("run: runDurationLock.wait()");
                    runDurationLock.wait();
                }
            }
            logger.debug("run: after synchronized (runDurationLock)");
            stopWriting();
        } catch (final InterruptedException e) {
            logger.warn("Got interrupted", e);
        }

        logger.debug("run: end");
    }
}
