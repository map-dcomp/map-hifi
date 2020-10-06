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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import org.apache.commons.io.LineIterator;
import org.slf4j.Logger;

/**
 * Some methods for dealing with external processes.
 * 
 * @author jschewe
 *
 */
public final class ProcessUtils {

    private ProcessUtils() {
    }

    /**
     * Log a stream to a logger.
     * 
     * @param logger
     *            used to report errors
     * @param stream
     *            the stream to read
     * @param streamName
     *            the name of the stream
     * @param logFunc
     *            the function to call with each line of the stream
     * @param isStoppedFunc
     *            function to check if the process is stopped, if so then
     *            exceptions from the reader will goto trace logging rather than
     *            error.
     */
    public static void logProcessStream(@Nonnull final Logger logger,
            @Nonnull final InputStream stream,
            @Nonnull final String streamName,
            @Nonnull final Consumer<String> logFunc,
            @Nonnull final BooleanSupplier isStoppedFunc) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()))) {
            try (LineIterator it = new LineIterator(reader)) {
                while (it.hasNext()) {
                    final String line = it.next();
                    logFunc.accept(line);
                }
            } // LineIterator allocation
        } catch (IOException | IllegalStateException e) {
            if (isStoppedFunc.getAsBoolean()) {
                logger.trace("During shutdown - Error handling process {}: {}", streamName, e.getMessage(), e);
            } else {
                logger.error("Error handling process {}: {}", streamName, e.getMessage(), e);
            }
        }
    }

}
