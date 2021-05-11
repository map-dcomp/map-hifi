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
// changed package to avoid any conflicts with system packages

//CHECKSTYLE:OFF - OpenJDK doesn't necessarily follow our style
package jdk14;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class SubSystem {
    String root;
    String mountPoint;
    String path;

    public SubSystem(String root, String mountPoint) {
        this.root = root;
        this.mountPoint = mountPoint;
    }

    public void setPath(String cgroupPath) {
        if (root != null && cgroupPath != null) {
            if (root.equals("/")) {
                if (!cgroupPath.equals("/")) {
                    path = mountPoint + cgroupPath;
                } else {
                    path = mountPoint;
                }
            } else {
                if (root.equals(cgroupPath)) {
                    path = mountPoint;
                } else {
                    if (cgroupPath.startsWith(root)) {
                        if (cgroupPath.length() > root.length()) {
                            String cgroupSubstr = cgroupPath.substring(root.length());
                            path = mountPoint + cgroupSubstr;
                        }
                    }
                }
            }
        }
    }

    public String path() {
        return path;
    }

    /**
     * getSubSystemStringValue
     *
     * Return the first line of the file "parm" argument from the subsystem.
     *
     * TODO: Consider using weak references for caching BufferedReader object.
     *
     * @param subsystem
     * @param parm
     * @return Returns the contents of the file specified by param.
     */
    public static String getStringValue(SubSystem subsystem, String parm) {
        if (subsystem == null)
            return null;

        try {
            return subsystem.readStringValue(parm);
        } catch (IOException e) {
            return null;
        }
    }

    private String readStringValue(String param) throws IOException {
        PrivilegedExceptionAction<BufferedReader> pea = () -> Files.newBufferedReader(Paths.get(path(), param));
        try (BufferedReader bufferedReader = AccessController.doPrivileged(pea)) {
            String line = bufferedReader.readLine();
            return line;
        } catch (PrivilegedActionException e) {
            Metrics.unwrapIOExceptionAndRethrow(e);
            throw new InternalError(e.getCause());
        }
    }

    public static long getLongValueMatchingLine(SubSystem subsystem,
            String param,
            String match,
            Function<String, Long> conversion) {
        long retval = Metrics.unlimited_minimum + 1; // default unlimited
        try {
            List<String> lines = subsystem.readMatchingLines(param);
            for (String line : lines) {
                if (line.startsWith(match)) {
                    retval = conversion.apply(line);
                    break;
                }
            }
        } catch (IOException e) {
            // Ignore. Default is unlimited.
        }
        return retval;
    }

    private List<String> readMatchingLines(String param) throws IOException {
        try {
            PrivilegedExceptionAction<List<String>> pea = () -> Files.readAllLines(Paths.get(path(), param));
            return AccessController.doPrivileged(pea);
        } catch (PrivilegedActionException e) {
            Metrics.unwrapIOExceptionAndRethrow(e);
            throw new InternalError(e.getCause());
        }
    }

    public static long getLongValue(SubSystem subsystem, String parm) {
        String strval = getStringValue(subsystem, parm);
        return convertStringToLong(strval);
    }

    public static long convertStringToLong(String strval) {
        long retval = 0;
        if (strval == null)
            return 0L;

        try {
            retval = Long.parseLong(strval);
        } catch (NumberFormatException e) {
            // For some properties (e.g. memory.limit_in_bytes) we may overflow
            // the range of signed long.
            // In this case, return Long.MAX_VALUE
            BigInteger b = new BigInteger(strval);
            if (b.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                return Long.MAX_VALUE;
            }
        }
        return retval;
    }

    public static double getDoubleValue(SubSystem subsystem, String parm) {
        String strval = getStringValue(subsystem, parm);

        if (strval == null)
            return 0L;

        double retval = Double.parseDouble(strval);

        return retval;
    }

    /**
     * getSubSystemlongEntry
     *
     * Return the long value from the line containing the string "entryname"
     * within file "parm" in the "subsystem".
     *
     * TODO: Consider using weak references for caching BufferedReader object.
     *
     * @param subsystem
     * @param parm
     * @param entryname
     * @return long value
     */
    public static long getLongEntry(SubSystem subsystem, String parm, String entryname) {
        if (subsystem == null)
            return 0L;

        try (Stream<String> lines = Metrics.readFilePrivileged(Paths.get(subsystem.path(), parm))) {

            Optional<String> result = lines.map(line -> line.split(" "))
                    .filter(line -> (line.length == 2 && line[0].equals(entryname))).map(line -> line[1]).findFirst();

            return result.isPresent() ? Long.parseLong(result.get()) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    public static int getIntValue(SubSystem subsystem, String parm) {
        String val = getStringValue(subsystem, parm);

        if (val == null)
            return 0;

        return Integer.parseInt(val);
    }

    /**
     * StringRangeToIntArray
     *
     * Convert a string in the form of 1,3-4,6 to an array of integers
     * containing all the numbers in the range.
     *
     * @param range
     * @return int[] containing a sorted list of processors or memory nodes
     */
    @SuppressFBWarnings(value = "NM_METHOD_NAMING_CONVENTION", justification = "OpenJDK community doesn't always follow good naming practices")
    public static int[] StringRangeToIntArray(String range) {
        int[] ints = new int[0];

        if (range == null)
            return ints;

        ArrayList<Integer> results = new ArrayList<>();
        String strs[] = range.split(",");
        for (String str : strs) {
            if (str.contains("-")) {
                String lohi[] = str.split("-");
                // validate format
                if (lohi.length != 2) {
                    continue;
                }
                int lo = Integer.parseInt(lohi[0]);
                int hi = Integer.parseInt(lohi[1]);
                for (int i = lo; i <= hi; i++) {
                    results.add(i);
                }
            } else {
                results.add(Integer.parseInt(str));
            }
        }

        // sort results
        results.sort(null);

        // convert ArrayList to primitive int array
        ints = new int[results.size()];
        int i = 0;
        for (Integer n : results) {
            ints[i++] = n;
        }

        return ints;
    }

    public static class MemorySubSystem extends SubSystem {

        private boolean hierarchical;

        public MemorySubSystem(String root, String mountPoint) {
            super(root, mountPoint);
        }

        boolean isHierarchical() {
            return hierarchical;
        }

        void setHierarchical(boolean hierarchical) {
            this.hierarchical = hierarchical;
        }

    }

}
// CHECKSTYLE:ON
