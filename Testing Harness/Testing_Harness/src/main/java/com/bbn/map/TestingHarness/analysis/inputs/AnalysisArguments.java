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
package com.bbn.map.TestingHarness.analysis.inputs;

import java.io.File;

/**
 * Ideally, this class would specify the types of data analysis that should be performed. However, right now, this class is used house string for parsing
 * the resource monitoring format and the data file names that were developed in the TestHarness code. 
 * 
 * Alex, can you deduplicate and refactor the static Strings below (i.e., those that identify outputs of the test harness) to either:
 * 	1. be used by the harness, or
 *  2. replace the keys in this class with however you are define the names in the harness.
 *  
 *  for example, please look at at: TEST_KEY, REP_KEY_REG_EX, SERVER_STAT_FILE, CLIENT_STAT_FILE_[POST|PRE]FIX
 */
public class AnalysisArguments {
	// top-level directory for tests
	public static final String TEST_KEY = File.separator + "test_";
	
	// how Alex is labeling experiment repetitions
	public static final String REP_KEY_REG_EX_SUCCESS = "rep_.*-finished";
	public static final String REP_KEY_REG_EX_ABORTED = "rep_.*-aborted";
	
	// the names of the stat files
	public static final String SERVER_STAT_FILE = "server_resource_stats.csv";
	public static final String CLIENT_STAT_FILE_PREFIX = "client_";
	public static final String CLIENT_STAT_FILE_POSTFIX = "_application_data.csv";
	public static final String CLIENT_STAT_FILE_REG_EX = CLIENT_STAT_FILE_PREFIX + ".*" + CLIENT_STAT_FILE_POSTFIX;
	
	// client stat file headers
	// timestamp,event,time_image_sent,time_ack_received,latency
	public static final String KEY_CLI_TIMESTAMP = "timestamp";
	public static final String KEY_CLI_EVENT = "event";
	public static final String KEY_CLI_TIME_IMAGE_SENT = "time_image_sent";
	public static final String KEY_CLI_TIME_ACK_RECEIVED = "time_ack_received";
	public static final String KEY_CLI_LATENCY = "latency";
	
	public static final String [] KEYSET_CLI = {KEY_CLI_TIMESTAMP,
			KEY_CLI_LATENCY, 
			KEY_CLI_TIME_IMAGE_SENT,
			KEY_CLI_TIME_ACK_RECEIVED,
			KEY_CLI_LATENCY
	};
	
	// server stat file headers, note the space b/c of resmon
	public static final String KEY_RESMON_TIMESTAMP = "Timestamp";
	public static final String KEY_RESMON_CPU = " %CPU";
	public static final String KEY_RESMON_MEM = " %MEM";
	public static final String KEY_RESMON_DISK_R_CT = " io.read";
	public static final String KEY_RESMON_DISK_R_KB = " io.read.KB";
	public static final String KEY_RESMON_DISK_W_CT = " io.write";
	public static final String KEY_RESMON_DISK_W_KB = " io.write.KB";
	public static final String KEY_RESMON_MEM_RESIDENT= " mem.rss.KB";
	public static final String KEY_RESMON_CTX = " nctxsw";
	public static final String KEY_RESMON_THREADS = " nthreads";
	
	public static final String [] KEYSET_RESMON = {KEY_RESMON_TIMESTAMP,
			KEY_RESMON_CPU,
			KEY_RESMON_MEM,
			KEY_RESMON_DISK_R_CT,
			KEY_RESMON_DISK_R_KB,
			KEY_RESMON_DISK_W_CT,
			KEY_RESMON_DISK_W_KB,
			KEY_RESMON_MEM_RESIDENT,
			KEY_RESMON_CTX,
			KEY_RESMON_THREADS
			};
	
	/**
	 * quick way to specify how an analysis should run.
	 */
	public static enum ANALYSIS_TARGET {INDIVIDUAL_SERVERS, GROUPED_SERVERS,
									INDIVIDUAL_CLIENTS, GROUPED_CLIENTS};
	
	public AnalysisArguments() {
		//I am empty.
		// I shall be mighty.
	}
	
	public static AnalysisArguments getDefaultAnalysisArguments() {
		return new AnalysisArguments();
	}
}