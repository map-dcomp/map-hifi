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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides utility methods for parsing Linux /proc files.
 * 
 * @author awald
 *
 */
public final class ProcFileParserUtils
{
    private ProcFileParserUtils()
    {
    }
    
    /**
     * Read a file into an array of strings containing one line per String.
     * 
     * @param file
     *            the file to read in
     * @return a list of strings, one for each line of the input file
     * @throws IOException
     *             if there is an error reading from the given file
     */
    public static List<String> readFile(File file) throws IOException
    {
        List<String> result = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), Charset.defaultCharset())))        {
            String line = reader.readLine();

            while (line != null)
            {
                result.add(line);
                line = reader.readLine();
            }
        }

        return result;
    }

    /**
     * Determines the first line of input that starts with the given String.
     * 
     * @param lines
     *            the line Strings to search.
     * @param start
     *            the string to search for at the beginning of each line
     * @return the index of line that starts with the String start if one exists or
     *         -1 if such a line does not exist
     */
    public static int getLineStartingWith(List<String> lines, String start)
    {
        for (int n = 0; n < lines.size(); n++)
        {
            if (lines.get(n).startsWith(start))
                return n;
        }

        return -1;
    }

    /**
     * Count the number of lines that start with a particular substring.
     * 
     * @param lines
     *            lines to search for the given start String
     * @param start
     *            the string to search for at the beginning of each line
     * @return returns the number of lines that start with the string start
     */
    public static int countLinesStartingWith(List<String> lines, String start)
    {
        int n = 0;

        for (int l = 0; l < lines.size(); l++)
        {
            if (lines.get(l).startsWith(start))
                n++;
        }

        return n;
    }

    /**
     * Split a string into substrings with whitespace as the delimiter.
     * 
     * @param str
     *            the string to split
     * @return the resulting array of strings after the split
     */
    public static String[] splitByWhiteSpace(String str)
    {
        return str.split("\\s+");
    }

    /**
     * Search for a string in a list of strings.
     * 
     * @param strs
     *            the list of strings to search
     * @param str
     *            the string to search for
     * @param exactMatch
     *            if true, a string must match str exactly, otherwise the string
     *            must contain str
     * @return the index of the matching string or -1 if the string is not found
     */
    public static int getStringIndex(String[] strs, String str, boolean exactMatch)
    {
        if (exactMatch)
        {
            for (int n = 0; n < strs.length; n++)
                if (strs[n].equals(str))
                    return n;
        } else
        {
            for (int n = 0; n < strs.length; n++)
                if (strs[n].contains(str))
                    return n;
        }

        return -1;
    }
}
