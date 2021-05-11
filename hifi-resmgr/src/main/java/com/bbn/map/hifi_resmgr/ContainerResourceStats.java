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

/**
 * Contains resource usage data of a container for one point in time.
 * 
 * @author awald
 *
 */
public class ContainerResourceStats
{
    // Compute capacity
    private Double cpuCapacity = null;
    private Long memoryCapacity = null;

    // Compute usage
    private double cpuUsage = 0;
    private long memoryUsage = 0;

    // Network usage
    private String nic = null;
    private long deltaRxBytes = 0;
    private long deltaTxBytes = 0;
    
    
    private String status = null;
    
    

    /**
     * Constructs a default ContainerResourceStats object.
     */
    public ContainerResourceStats()
    {
    }

    /**
     * 
     * @return the CPU usage of the container in CPUs
     */
    public double getCpuUsage()
    {
        return cpuUsage;
    }

    /**
     * 
     * @return the memory usage of the container in bytes
     */
    public long getMemoryUsage()
    {
        return memoryUsage;
    }

    /**
     * Store the compute usage of the container.
     * 
     * @param cpuUsage
     *            the CPU usage of the container in CPUs
     * @param memoryUsage
     *            the memory usage of the container in bytes
     */
    public void setComputeUsage(double cpuUsage, long memoryUsage)
    {
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
    }

    /**
     * Store the network usage of the container.
     * 
     * @param nic
     *            the name of the NIC in the container
     * @param deltaRxBytes
     *            the amount of data that the container received since the last
     *            update in bytes
     * @param deltaTxBytes
     *            the amount of data that the container sent since the last update
     *            in bytes
     */
    public void setNetworkUsage(String nic, long deltaRxBytes, long deltaTxBytes)
    {
        this.nic = nic;
        this.deltaRxBytes = deltaRxBytes;
        this.deltaTxBytes = deltaTxBytes;
    }

    /**
     * Store the measured compute capacity of the container.
     * 
     * @param cpus
     *            the amount of CPU available to the container in CPUs
     * @param memory
     *            the amount of memory available to the container in bytes
     */
    public void setComputeCapacity(double cpus, long memory)
    {
        cpuCapacity = cpus;
        memoryCapacity = memory;
    }

    /**
     * 
     * @return the amount of CPU available to the container in CPUs
     */
    public Double getCpuCapacity()
    {
        return cpuCapacity;
    }

    /**
     * 
     * @return the amount of memory available to the container in bytes
     */
    public Long getMemoryCapacity()
    {
        return memoryCapacity;
    }

    /**
     * 
     * @return the name of the NIC that is being monitored for this container
     */
    public String getNIC()
    {
        return nic;
    }

    /**
     * 
     * @return the amount of data that the container received since the last update
     *         in bytes
     */
    public long getDeltaRxBytes()
    {
        return deltaRxBytes;
    }

    /**
     * 
     * @return the amount of data that the container sent since the last update in
     *         bytes
     */
    public long getDeltaTxBytes()
    {
        return deltaTxBytes;
    }
    
    /**
     * 
     * @return the Docker status string for the container
     */
    public String getStatus()
    {
        return status;
    }

    /**
     * 
     * @param status
     *          the Docker status string for the container
     */
    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String toString()
    {
        return "CPU: " + cpuUsage + "\nMemory: " + memoryUsage + "\nRX Bytes: " + deltaRxBytes + " TX Bytes: "
                + deltaTxBytes + "\nStatus: " + status;
    }
}
