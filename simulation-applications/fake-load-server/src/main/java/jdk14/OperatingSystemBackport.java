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
//CHECKSTYLE:OFF - in an odd package to make it clear this is special 
package jdk14;
//CHEcKSTYLE:ON

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.OperatingSystemMXBean;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Methods that we want from JDK14 OperatingSystemMXBean.
 * 
 * @author jschewe
 *
 */
public class OperatingSystemBackport {
    private static final Logger LOGGER = LoggerFactory.getLogger(OperatingSystemBackport.class);

    private final Metrics containerMetrics;

    private static final OperatingSystemMXBean OPERATING_SYSTEM = ManagementFactory
            .getPlatformMXBean(OperatingSystemMXBean.class);

    /**
     * Constructor.
     */
    public OperatingSystemBackport() {
        this.containerMetrics = Metrics.getInstance();
    }

    /**
     * Returns the "recent cpu usage" for the operating environment. This value
     * is a double in the [0.0,1.0] interval. A value of 0.0 means that all CPUs
     * were idle during the recent period of time observed, while a value of 1.0
     * means that all CPUs were actively running 100% of the time during the
     * recent period being observed. All values betweens 0.0 and 1.0 are
     * possible depending of the activities going on. If the recent cpu usage is
     * not available, the method returns a negative value.
     *
     * Copied from JDK 14 source with modifications to execute in JDK 8.
     * 
     * @return the "recent cpu usage" for the whole operating environment; a
     *         negative value if not available.
     */
    public double getCpuLoad() {
        if (containerMetrics != null) {
            long quota = containerMetrics.getCpuQuota();
            if (quota > 0) {
                long periodLength = containerMetrics.getCpuPeriod();
                long numPeriods = containerMetrics.getCpuNumPeriods();
                long usageNanos = containerMetrics.getCpuUsage();
                if (periodLength > 0 && numPeriods > 0 && usageNanos > 0) {
                    long elapsedNanos = TimeUnit.MICROSECONDS.toNanos(periodLength * numPeriods);
                    double systemLoad = (double) usageNanos / elapsedNanos;
                    // Ensure the return value is in the range 0.0 -> 1.0
                    systemLoad = Math.max(0.0, systemLoad);
                    systemLoad = Math.min(1.0, systemLoad);
                    return systemLoad;
                }
                return -1;
            } else {
                // If CPU quotas are not active then find the average system
                // load for
                // all online CPUs that are allowed to run this container.

                // If the cpuset is the same as the host's one there is no need
                // to iterate over each CPU
                if (isCpuSetSameAsHostCpuSet()) {
                    return getCpuLoad0();
                } else {
                    int[] cpuSet = containerMetrics.getEffectiveCpuSetCpus();
                    if (cpuSet != null && cpuSet.length > 0) {
                        LOGGER.error(
                                "There is no implementation for getting the load of a single CPU without added native code");
                        return -1;
                        // throw new RuntimeException("Don't have a way to get
                        // the single CPU load");
                        // double systemLoad = 0.0;
                        // for (int cpu : cpuSet) {
                        // double cpuLoad = getSingleCpuLoad0(cpu);
                        // if (cpuLoad < 0) {
                        // return -1;
                        // }
                        // systemLoad += cpuLoad;
                        // }
                        // return systemLoad / cpuSet.length;
                    }
                    return -1;
                }
            }
        }
        return getCpuLoad0();
    }

    private double getCpuLoad0() {
        LOGGER.trace("getCpuLoad0: delegating to getSystemCpuLoad");
        return OPERATING_SYSTEM.getSystemCpuLoad();
    }

    private boolean isCpuSetSameAsHostCpuSet() {
        if (containerMetrics != null) {
            return containerMetrics.getCpuSetCpus().length == getHostConfiguredCpuCount0();
        }
        return false;
    }

    @SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME", justification = "Need an absolute path to the nproc executable")
    private int getHostConfiguredCpuCount0() {
        // execute /usr/bin/nproc and get the output
        if (Files.exists(Paths.get("/usr/bin/nproc"))) {
            try {
                final ProcessBuilder builder = new ProcessBuilder("/usr/bin/nproc");
                final Process proc = builder.start();
                final int status = proc.waitFor();
                if (0 != status) {
                    final String error = IOUtils.toString(proc.getErrorStream(), Charset.defaultCharset()).trim();
                    LOGGER.error("getHostConfiguredCpuCount0: Got error executing nproc, returning -1: {}", error);
                    return -1;
                } else {
                    final String output = IOUtils.toString(proc.getInputStream(), Charset.defaultCharset()).trim();
                    try {
                        final int numCpus = Integer.parseInt(output);
                        return numCpus;
                    } catch (final NumberFormatException e) {
                        LOGGER.error("getHostConfiguredCpuCount0: Unable to parse output from nproc, returning -1: {}",
                                output, e);
                        return -1;
                    }
                }
            } catch (final InterruptedException e) {
                LOGGER.warn("getHostConfiguredCpuCount0: Interrupted waiting for nproc to exit, returning -1", e);
                return -1;
            } catch (final IOException e) {
                LOGGER.warn("getHostConfiguredCpuCount0: I/O error calling nproc, returning -1", e);
                return -1;
            }
        } else {
            LOGGER.warn("getHostConfiguredCpuCount0: Cannot find /usr/bin/nproc, returning -1");
            return -1;
        }

    }

}
