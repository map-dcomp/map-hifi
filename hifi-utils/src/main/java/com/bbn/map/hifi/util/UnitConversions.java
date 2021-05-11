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

/**
 * Some utilities for doing unit conversions.
 * 
 * @author jschewe
 *
 */
public final class UnitConversions {

    private UnitConversions() {
    }

    private static final double KB_TO_BYTES = 1024.0;

    /**
     * Converts bits per second to Megabits per second.
     * 
     * @param bitsPerSecond
     *            bits per second
     * @return megabits per second
     */
    public static double bitsPerSecondToMegabitsPerSecond(final long bitsPerSecond) {
        return (bitsPerSecond / KB_TO_BYTES / KB_TO_BYTES);
    }

    /**
     * Converts bytes to gigabytes.
     * 
     * @param bytes
     *            bytes
     * @return gigabytes
     */
    public static double bytesToGigaBytes(final long bytes) {
        return (bytes / KB_TO_BYTES / KB_TO_BYTES / KB_TO_BYTES);
    }

    /**
     * @param gigaBytes
     *            gigabytes
     * @return bytes
     */
    public static double gigabytesToBytes(final double gigaBytes) {
        return gigaBytes * KB_TO_BYTES * KB_TO_BYTES * KB_TO_BYTES;
    }

    /**
     * @param megaBytes
     *            megaBytes
     * @return bytes
     */
    public static double megabytesToBytes(final double megaBytes) {
        return megaBytes * KB_TO_BYTES * KB_TO_BYTES;
    }

    private static final double BITS_PER_BYTE = 8;

    /**
     * 
     * @param bits
     *            bits
     * @return bytes
     */
    public static double bitsToBytes(final double bits) {
        return bits / BITS_PER_BYTE;
    }

    /**
     * @param megabits
     *            megabits
     * @return bytes
     */
    public static double megabitsToBytes(final double megabits) {
        final double megabytes = bitsToBytes(megabits);
        return megabytesToBytes(megabytes);
    }

}
