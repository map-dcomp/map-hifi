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

import java.net.NetworkInterface;

/**
 * Stores information related to a single communication between a remote IP and
 * a local IP. Sent and received data is from the perspective of the local IP.
 * 
 * @author awald
 *
 */
public class IftopTrafficData {
    private final String localIP;
    private final int localPort;
    private final String remoteIP;
    private final int remotePort;

    private final long last2sBitsSent;
    private final long last2sBitsReceived;

    private final NetworkInterface nic;

    /**
     * 
     * @param localIP
     *            see {@link #getLocalIP()}
     * @param localPort
     *            see {@link #getLocalPort()}
     * @param remoteIP
     *            see {@link #getRemoteIP()}
     * @param remotePort
     *            see {@link #getRemotePort()}
     * @param last2sBitsSent
     *            see {@link #getLast2sBitsSent()}
     * @param last2sBitsReceived
     *            see {@link #getLast2sBitsReceived()}
     * @param nic
     *            see {@link #getInterface()}
     */
    IftopTrafficData(final String localIP,
            final int localPort,
            final String remoteIP,
            final int remotePort,
            final long last2sBitsSent,
            final long last2sBitsReceived,
            final NetworkInterface nic) {
        this.localIP = localIP;
        this.localPort = localPort;
        this.remoteIP = remoteIP;
        this.remotePort = remotePort;

        this.last2sBitsSent = last2sBitsSent;
        this.last2sBitsReceived = last2sBitsReceived;
        this.nic = nic;
    }

    /**
     * 
     * @return the local IP address in the communication
     */
    public String getLocalIP() {
        return localIP;
    }

    /**
     * 
     * @return the port used by the local computer
     */
    public int getLocalPort() {
        return localPort;
    }

    /**
     * 
     * @return the remote IP address in the communication
     */
    public String getRemoteIP() {
        return remoteIP;
    }

    /**
     * 
     * @return the port used by the remote computer
     */
    public int getRemotePort() {
        return remotePort;
    }

    /**
     * 
     * @return the number of bits per second that the local machine sent to the
     *         remote machine in the past 2 seconds
     */
    public long getLast2sBitsSent() {
        return last2sBitsSent;
    }

    /**
     * 
     * @return the number of bits per second that the local machine received
     *         from the remote machine in the past 2 seconds
     */
    public long getLast2sBitsReceived() {
        return last2sBitsReceived;
    }

    /**
     * 
     * @return the interface that the traffic was received on
     */
    public NetworkInterface getInterface() {
        return nic;
    }

    @Override
    public String toString() {
        return nic.getName() + " local: " + localIP + ":" + localPort + " remote: " + remoteIP + ":" + remotePort + " sent: " + last2sBitsSent + " recv: "
                + last2sBitsReceived;
    }
}