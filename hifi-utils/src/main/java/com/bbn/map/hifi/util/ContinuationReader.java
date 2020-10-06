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
package com.bbn.map.hifi.util;

import java.io.IOException;
import java.io.Reader;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reader that allows one to keep reading at the end of a file by sleeping for
 * some time and then trying to read again.
 * 
 * @author jschewe
 *
 */
public class ContinuationReader extends Reader {

    private static final Logger LOGGER = LogManager.getLogger(ContinuationReader.class);

    private final Duration pollInterval;
    private final Reader delegate;
    private final AtomicBoolean cancellationToken;

    /**
     * 
     * @param delegate
     *            where to read data from
     * @param pollInterval
     *            when end of file is reached, how long to sleep before trying
     *            to read again
     * @param cancellationToken
     *            when set to true, stop trying to read. The thread will likely
     *            need to be interrupted to break out of the blocking read.
     */
    public ContinuationReader(final Reader delegate,
            final Duration pollInterval,
            final AtomicBoolean cancellationToken) {
        this.delegate = delegate;
        this.pollInterval = pollInterval;
        this.cancellationToken = cancellationToken;
    }

    /**
     * Read from the delegate. If the end of file has been reached and the
     * cancellationToken is false, sleep for the pool interval and try again. If
     * the cancellation token is true, then return end of file.
     */
    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        do {
            final int result = delegate.read(cbuf, off, len);
            if (-1 == result) {
                if(cancellationToken.get()) {
                    return -1;
                }
                
                try {
                    Thread.sleep(pollInterval.toMillis());
                } catch (final InterruptedException e) {
                    LOGGER.debug("Interrupted waiting for more data. Will check cancellation token", e);
                }
            } else {
                return result;
            }
        } while (!cancellationToken.get());
        return -1;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
