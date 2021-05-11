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
/* Copyright (c) <2017>, <Raytheon BBN Technologies>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.bbn.map.TestingHarness.analysis.utils;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Hashtable;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper utilities dealing with MAP's usage of Math3
 *
 */
final public class DataUtils {
	static Logger logger = LogManager.getLogger(DataUtils.class);
	
	/**
	 * parse UTF-8 CSV File into map of descriptive stats
	 * 
	 * @param fileToParse
	 * @param expectedHeaders
	 * @param format
	 * @return
	 * @throws Exception
	 */
	public static Map<String, DescriptiveStatistics> summarizeUTF8CSVFile(File fileToParse, String [] expectedHeaders, CSVFormat format) throws Exception {
		return DataUtils.summarizeCSVFile(fileToParse, expectedHeaders, format, Charset.forName("UTF-8"));
	}
	
	/**
	 * Parse CSV File of some character set into map of descriptive stats
	 * 
	 * @param fileToParse
	 * @param expectedHeaders
	 * @param format
	 * @param Charset charset
	 * @return
	 * @throws Exception
	 */
	public static Map<String, DescriptiveStatistics> summarizeCSVFile(File fileToParse, String [] expectedHeaders, CSVFormat format, Charset charset) throws Exception {
		
		// basic parse for tab files.
		CSVParser parser = CSVParser.parse(fileToParse, charset, format);
		
		// setup return(s)
		Map<String, DescriptiveStatistics> ret = new Hashtable<String, DescriptiveStatistics>();
				
		// get headers
		Map<String, Integer> headerMap = parser.getHeaderMap();
		for (String key : expectedHeaders) {
			if (!headerMap.containsKey(key)) {
				logger.warn("CSV file '" +fileToParse.getAbsolutePath() + "' doest not contain header: '" + key + "'");
			} else {
				ret.put(key, new DescriptiveStatistics());
			}
		}
		
		int ct = 0;
		
		// create data sets
		for (CSVRecord a : parser) {
			ct++;
			for (String key : expectedHeaders) {
				DescriptiveStatistics ds = ret.get(key);
				if (ds == null) { 
					ds = new DescriptiveStatistics();
					ret.put(key, ds);
				} 
				
				try {
					String val = a.get(key);
					try {
						double d = Double.parseDouble(val);
						ds.addValue(d);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
		
		logger.debug("Parsed: " + fileToParse.getAbsolutePath() + " contained: " + ct + " records.");
		
		return ret;
	}
	
	
	/**
	 * return number of data items
	 * 
	 * @param in
	 * @return
	 */
	public static long getRecordCount(DescriptiveStatistics in) {
		return in.getN();
	}
	
	public static String getMeanFormatted(DescriptiveStatistics in) {
		return "Mean:\t" + String.format("%.2f", in.getMean());
	}

	public static String getMinFormatted(DescriptiveStatistics in) {
		return "Min:\t" + String.format("%.2f", in.getMin());
	}
	
	public static String getMaxFormatted(DescriptiveStatistics in) {
		return "Max:\t" + String.format("%.2f", in.getMax());
	}
	
	public static String getStdDevFormatted(DescriptiveStatistics in) {
		return "StdDev:\t" + String.format("%.2f", in.getStandardDeviation());
	}
	
	public static String getSkewFormatted(DescriptiveStatistics in) {
		return "Skew:\t" + String.format("%.2f", in.getSkewness());
	}
}