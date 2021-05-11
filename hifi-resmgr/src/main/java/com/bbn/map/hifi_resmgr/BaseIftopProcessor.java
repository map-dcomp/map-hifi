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

import java.util.List;

/**
 * Common functionality between the custom parser and the original parser.
 * 
 * @author jschewe
 *
 */
public abstract class BaseIftopProcessor extends Thread {

    private final Object lock = new Object();

    private List<IftopTrafficData> lastIftopFrame = null;

    /**
     * 
     * @param name name of the thread
     */
    public BaseIftopProcessor(final String name) {
        super(name);
    }

    /**
     * Store the frame to return next from {@link #getLastIftopFrames()}.
     * 
     * @param data
     *            the new frame
     */
    protected final void setLastIftopFrames(final List<IftopTrafficData> data) {
        synchronized (lock) {
            lastIftopFrame = data;
        }
    }

    /**
     * @return the most recently produced frames for this NIC, may be null
     */
    public final List<IftopTrafficData> getLastIftopFrames() {
        synchronized (lock) {
            return lastIftopFrame;
        }
    }

    /**
     * Stop reading data from iftop.
     */
    public abstract void stopProcessing();

}
