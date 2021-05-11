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
/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

// copied from jdk14 to backport container metrics CPU information
// removed references to MemorySubSystem as this isn't needed for MAP
// changed package to avoid any conflicts with system packages
// removed implements as this class doesn't implement an interface known to java 8 or java 11
//CHECKSTYLE:OFF - OpenJDK community doesn't necessarily match our style

package jdk14;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.stream.Stream;

public class Metrics {
    private SubSystem cpu;
    private SubSystem cpuacct;
    private SubSystem cpuset;
    private SubSystem blkio;
    private boolean activeSubSystems;

    // Values returned larger than this number are unlimited.
    static long unlimited_minimum = 0x7FFFFFFFFF000000L;

    private static final Metrics INSTANCE = initContainerSubSystems();

    private static final String PROVIDER_NAME = "cgroupv1";

    private Metrics() {
        activeSubSystems = false;
    }

    public static Metrics getInstance() {
        return INSTANCE;
    }

    private static Metrics initContainerSubSystems() {
        Metrics metrics = new Metrics();

        /**
         * Find the cgroup mount points for subsystems by reading
         * /proc/self/mountinfo
         *
         * Example for docker MemorySubSystem subsystem: 219 214 0:29
         * /docker/7208cebd00fa5f2e342b1094f7bed87fa25661471a4637118e65f1c995be8a34
         * /sys/fs/cgroup/MemorySubSystem ro,nosuid,nodev,noexec,relatime -
         * cgroup cgroup rw,MemorySubSystem
         *
         * Example for host: 34 28 0:29 / /sys/fs/cgroup/MemorySubSystem
         * rw,nosuid,nodev,noexec,relatime shared:16 - cgroup cgroup
         * rw,MemorySubSystem
         */
        try (Stream<String> lines = readFilePrivileged(Paths.get("/proc/self/mountinfo"))) {

            lines.filter(line -> line.contains(" - cgroup ")).map(line -> line.split(" "))
                    .forEach(entry -> createSubSystem(metrics, entry));

        } catch (IOException e) {
            return null;
        }

        /**
         * Read /proc/self/cgroup and map host mount point to local one via
         * /proc/self/mountinfo content above
         *
         * Docker example:
         * 5:memory:/docker/6558aed8fc662b194323ceab5b964f69cf36b3e8af877a14b80256e93aecb044
         *
         * Host example: 5:memory:/user.slice
         *
         * Construct a path to the process specific memory and cpuset cgroup
         * directory.
         *
         * For a container running under Docker from memory example above the
         * paths would be:
         *
         * /sys/fs/cgroup/memory
         *
         * For a Host from memory example above the path would be:
         *
         * /sys/fs/cgroup/memory/user.slice
         *
         */
        try (Stream<String> lines = readFilePrivileged(Paths.get("/proc/self/cgroup"))) {

            lines.map(line -> line.split(":")).filter(line -> (line.length >= 3))
                    .forEach(line -> setSubSystemPath(metrics, line));

        } catch (IOException e) {
            return null;
        }

        // Return Metrics object if we found any subsystems.
        if (metrics.activeSubSystems()) {
            return metrics;
        }

        return null;
    }

    static Stream<String> readFilePrivileged(Path path) throws IOException {
        try {
            PrivilegedExceptionAction<Stream<String>> pea = () -> Files.lines(path);
            return AccessController.doPrivileged(pea);
        } catch (PrivilegedActionException e) {
            unwrapIOExceptionAndRethrow(e);
            throw new InternalError(e.getCause());
        }
    }

    static void unwrapIOExceptionAndRethrow(PrivilegedActionException pae) throws IOException {
        Throwable x = pae.getCause();
        if (x instanceof IOException)
            throw (IOException) x;
        if (x instanceof RuntimeException)
            throw (RuntimeException) x;
        if (x instanceof Error)
            throw (Error) x;
    }

    /**
     * createSubSystem objects and initialize mount points
     */
    private static void createSubSystem(Metrics metric, String[] mountentry) {
        if (mountentry.length < 5)
            return;

        final Path p = Paths.get(mountentry[4]);
        final Path filename = p.getFileName();
        if (null == filename) {
            return;
        }
        final String[] subsystemNames = filename.toString().split(",");

        for (String subsystemName : subsystemNames) {
            switch (subsystemName) {
            case "cpuset":
                metric.setCpuSetSubSystem(new SubSystem(mountentry[3], mountentry[4]));
                break;
            case "cpuacct":
                metric.setCpuAcctSubSystem(new SubSystem(mountentry[3], mountentry[4]));
                break;
            case "cpu":
                metric.setCpuSubSystem(new SubSystem(mountentry[3], mountentry[4]));
                break;
            case "blkio":
                metric.setBlkIOSubSystem(new SubSystem(mountentry[3], mountentry[4]));
                break;
            default:
                // Ignore subsystems that we don't support
                break;
            }
        }
    }

    /**
     * setSubSystemPath based on the contents of /proc/self/cgroup
     */
    private static void setSubSystemPath(Metrics metric, String[] entry) {
        String controller;
        String base;
        SubSystem subsystem = null;
        SubSystem subsystem2 = null;

        controller = entry[1];
        base = entry[2];
        if (controller != null && base != null) {
            switch (controller) {
            case "cpuset":
                subsystem = metric.CpuSetSubSystem();
                break;
            case "cpu,cpuacct":
            case "cpuacct,cpu":
                subsystem = metric.CpuSubSystem();
                subsystem2 = metric.CpuAcctSubSystem();
                break;
            case "cpuacct":
                subsystem = metric.CpuAcctSubSystem();
                break;
            case "cpu":
                subsystem = metric.CpuSubSystem();
                break;
            case "blkio":
                subsystem = metric.BlkIOSubSystem();
                break;
            // Ignore subsystems that we don't support
            default:
                break;
            }
        }

        if (subsystem != null) {
            subsystem.setPath(base);
            metric.setActiveSubSystems();
        }
        if (subsystem2 != null) {
            subsystem2.setPath(base);
        }
    }

    private void setActiveSubSystems() {
        activeSubSystems = true;
    }

    private boolean activeSubSystems() {
        return activeSubSystems;
    }

    private void setCpuSubSystem(SubSystem cpu) {
        this.cpu = cpu;
    }

    private void setCpuAcctSubSystem(SubSystem cpuacct) {
        this.cpuacct = cpuacct;
    }

    private void setCpuSetSubSystem(SubSystem cpuset) {
        this.cpuset = cpuset;
    }

    private void setBlkIOSubSystem(SubSystem blkio) {
        this.blkio = blkio;
    }

    private SubSystem CpuSubSystem() {
        return cpu;
    }

    private SubSystem CpuAcctSubSystem() {
        return cpuacct;
    }

    private SubSystem CpuSetSubSystem() {
        return cpuset;
    }

    private SubSystem BlkIOSubSystem() {
        return blkio;
    }

    public String getProvider() {
        return PROVIDER_NAME;
    }

    /*****************************************************************
     * CPU Accounting Subsystem
     ****************************************************************/

    public long getCpuUsage() {
        return SubSystem.getLongValue(cpuacct, "cpuacct.usage");
    }

    public long[] getPerCpuUsage() {
        String usagelist = SubSystem.getStringValue(cpuacct, "cpuacct.usage_percpu");
        if (usagelist == null) {
            return new long[0];
        }

        String list[] = usagelist.split(" ");
        long percpu[] = new long[list.length];
        for (int i = 0; i < list.length; i++) {
            percpu[i] = Long.parseLong(list[i]);
        }
        return percpu;
    }

    public long getCpuUserUsage() {
        return SubSystem.getLongEntry(cpuacct, "cpuacct.stat", "user");
    }

    public long getCpuSystemUsage() {
        return SubSystem.getLongEntry(cpuacct, "cpuacct.stat", "system");
    }

    /*****************************************************************
     * CPU Subsystem
     ****************************************************************/

    public long getCpuPeriod() {
        return SubSystem.getLongValue(cpu, "cpu.cfs_period_us");
    }

    public long getCpuQuota() {
        return SubSystem.getLongValue(cpu, "cpu.cfs_quota_us");
    }

    public long getCpuShares() {
        long retval = SubSystem.getLongValue(cpu, "cpu.shares");
        if (retval == 0 || retval == 1024)
            return -1;
        else
            return retval;
    }

    public long getCpuNumPeriods() {
        return SubSystem.getLongEntry(cpu, "cpu.stat", "nr_periods");
    }

    public long getCpuNumThrottled() {
        return SubSystem.getLongEntry(cpu, "cpu.stat", "nr_throttled");
    }

    public long getCpuThrottledTime() {
        return SubSystem.getLongEntry(cpu, "cpu.stat", "throttled_time");
    }

    public long getEffectiveCpuCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    /*****************************************************************
     * CPUSet Subsystem
     ****************************************************************/

    public int[] getCpuSetCpus() {
        return SubSystem.StringRangeToIntArray(SubSystem.getStringValue(cpuset, "cpuset.cpus"));
    }

    public int[] getEffectiveCpuSetCpus() {
        return SubSystem.StringRangeToIntArray(SubSystem.getStringValue(cpuset, "cpuset.effective_cpus"));
    }

    public int[] getCpuSetMems() {
        return SubSystem.StringRangeToIntArray(SubSystem.getStringValue(cpuset, "cpuset.mems"));
    }

    public int[] getEffectiveCpuSetMems() {
        return SubSystem.StringRangeToIntArray(SubSystem.getStringValue(cpuset, "cpuset.effective_mems"));
    }

    public double getCpuSetMemoryPressure() {
        return SubSystem.getDoubleValue(cpuset, "cpuset.memory_pressure");
    }

    public boolean isCpuSetMemoryPressureEnabled() {
        long val = SubSystem.getLongValue(cpuset, "cpuset.memory_pressure_enabled");
        return (val == 1);
    }

    /*****************************************************************
     * BlKIO Subsystem
     ****************************************************************/

    public long getBlkIOServiceCount() {
        return SubSystem.getLongEntry(blkio, "blkio.throttle.io_service_bytes", "Total");
    }

    public long getBlkIOServiced() {
        return SubSystem.getLongEntry(blkio, "blkio.throttle.io_serviced", "Total");
    }

}
// CHECKSTYLE:ON
