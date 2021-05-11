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
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.TestingHarness.analysis.utils.DataUtils;

/**
 * A repetition is a single execution of some experiment configuration.
 * 
 * For each repetition directory, look for service and client logging files based
 * upon the experiment harness structure.
 *
 */
final public class RepetitionData {
	static Logger logger = LogManager.getLogger(RepetitionData.class);
			
	/**
	 * directory location for the run
	 */
	public File repetitionDirectory = null;
	
	/**
	 * parsed server data in the Math3 format
	 */
	public Map<String, DescriptiveStatistics> serverData = null;
	
	/**
	 * Summary string for server data.
	 */
	private StringBuffer sbServerSummary = null;
	
	/**
	 * parsed client data in the Math3 format
	 */
	public Map<Integer, Map<String, DescriptiveStatistics>> clientData = null;
	
	/**
	 * Parent experiment
	 */
	public Experiment parent;
	
	/**
	 * out of all the client files, which one has the most data entries
	 */
	private long maxSingleRepetitionClientRecords = -1;

	/**
	 * the sum of data entries for all clients.
	 */
	private long totalClientRecords = -1;
	
	
	/**
	 * Given an experiment directory, parse and store server and client data.
	 * 
	 * @param repetitionDirectory
	 * @param parent
	 * @throws Exception
	 */
	public RepetitionData(File repetitionDirectory, Experiment parent) throws Exception {
		this.repetitionDirectory = repetitionDirectory;
		this.parent = parent;
		if (!this.repetitionDirectory.exists() || this.parent == null)
			throw new Exception("bad input data.");
		
		parseService();
		
		parseClients();
		
		gatherClientStats();
		
		logger.info(this.toString() + " server records: " + getServerRecordCount() + " total client records: " + this.totalClientRecords);
	}

	/**
	 * Utility to collect some stats about the clients in this rep.
	 */
	private void gatherClientStats() {
		this.maxSingleRepetitionClientRecords = Long.MIN_VALUE;
		this.totalClientRecords = 0;
		
		for (Integer client : this.clientData.keySet()) {
			Map<String, DescriptiveStatistics> cData = this.clientData.get(client);
			DescriptiveStatistics ds = cData.get(AnalysisArguments.KEY_CLI_LATENCY);
			
			// check if the clients data set has
			if (ds.getN() > this.maxSingleRepetitionClientRecords) {
				this.maxSingleRepetitionClientRecords = ds.getN();
			}
			
			this.totalClientRecords += ds.getN();
		}
	}
	
	/**
	 * helper to get server record count
	 * 
	 * @return
	 */
	private long getServerRecordCount() {
		long ret = 0;
		
		if (serverData != null) {
			ret = getNCount(serverData.values());
		}
		
		return ret;
	}


	private long getNCount(Collection<DescriptiveStatistics> vals) {
		long ret = 0;
		if (vals != null && vals.size() > 0) {
			DescriptiveStatistics val = vals.iterator().next();
			ret = val.getN();
		}
		
		return ret;
	}

	/**
	 * Locate each client file name and generate Math3 DS data
	 * 
	 * @throws Exception
	 */
	private void parseClients() throws Exception {
		clientData = new Hashtable<Integer, Map<String, DescriptiveStatistics>>();
		
		for (File file : repetitionDirectory.listFiles()) {
			if (file.isFile() && file.getName().matches(AnalysisArguments.CLIENT_STAT_FILE_REG_EX)) {
				Map<String, DescriptiveStatistics> data = DataUtils.summarizeUTF8CSVFile(file, AnalysisArguments.KEYSET_CLI, CSVFormat.DEFAULT.withHeader());
				String id = file.getName();
				id = id.replaceAll(AnalysisArguments.CLIENT_STAT_FILE_PREFIX, "");
				id = id.replaceAll(AnalysisArguments.CLIENT_STAT_FILE_POSTFIX, "");
				clientData.put(Integer.parseInt(id), data);
			}
		}
	}

	/**
	 * Locate server file name and generate Math3 DS data
	 * 
	 * @throws Exception
	 */
	private void parseService() throws Exception {
		String file = repetitionDirectory.getAbsolutePath() + File.separator + AnalysisArguments.SERVER_STAT_FILE;
		File f = new File (file);
		this.serverData = DataUtils.summarizeUTF8CSVFile(f, AnalysisArguments.KEYSET_RESMON, CSVFormat.DEFAULT.withHeader()); 
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer("Repetition: '" + repetitionDirectory.getName() + "', #Clients: '" + clientData.size() + "'");
		return sb.toString();
	}

	/**
	 * 
	 * @return
	 */
	synchronized public String printServerSummarizes() {
		if (sbServerSummary == null) {
			sbServerSummary = new StringBuffer("Server Summary for Repetition: " + repetitionDirectory + System.lineSeparator());
			
			try {
				
				for (String key : AnalysisArguments.KEYSET_RESMON) {
					DescriptiveStatistics data = serverData.get(key);
					sbServerSummary.append(key + System.lineSeparator());
					sbServerSummary.append("N: \t" + data.getN() + System.lineSeparator());
					sbServerSummary.append(DataUtils.getMinFormatted(data) + System.lineSeparator());
					sbServerSummary.append(DataUtils.getMeanFormatted(data) + System.lineSeparator());
					sbServerSummary.append(DataUtils.getMaxFormatted(data) + System.lineSeparator());
					sbServerSummary.append(DataUtils.getStdDevFormatted(data) + System.lineSeparator());
					sbServerSummary.append(DataUtils.getSkewFormatted(data) + System.lineSeparator());
				}
				
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		
		return sbServerSummary.toString();
	}
	
	/**
	 * return max client records across clients
	 * 
	 * @return
	 */
	public long getMaxClientRepRecords() {
		return maxSingleRepetitionClientRecords;
	}

	/**
	 * return total client records for this rep.
	 * 
	 * @return
	 */
	public long getTotalClientRecords() {
		return totalClientRecords;
	}
	
	public int getNumberOfClients() {
		int ret = 0;
		
		try {
			ret = this.clientData.size();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		return ret;
	}
	
	/**
	 * run a name for this rep.
	 * 
	 * @return
	 */
	public String getName() {
		String ret = "null";
		
		if (this.repetitionDirectory != null) {
			ret = this.repetitionDirectory.getName();
		}
		
		return ret;
	}
}