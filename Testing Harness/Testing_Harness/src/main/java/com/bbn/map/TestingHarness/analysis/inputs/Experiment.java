/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
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
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.TestingHarness.analysis.utils.DataUtils;

/**
 * Class for managing analysis and experiment directories.
 * Experiments are defined as a named line-item experiment in the test harness configuration.
 * See the test harness config file documentation for more details.
 * 
 * That experiment name should be 'from' the following format, e.g., this will be the exp0-name below:
 * i. <exp0-name> <10-clients> <100-requests> <10-per-second>
 * 
 *
 */
final public class Experiment {
	static Logger logger = LogManager.getLogger(Experiment.class);
			
	/**
	 * Directory where an experiment data was recorded.
	 */
	private File experimentDirectory = null;
			
	/**
	 * This class will create an analysis directory inside the experiment directory folder.
	 */
	private File analysisDirectory = null;

	/**
	 * Individual runs of the target experiment.
	 */
	private Vector<RepetitionData> repetitions = null;

	/**
	 * printable summary of each server data file
	 */
	private StringBuffer sbIndividualServerSummaries = null;
	
	/**
	 * aggregate of repeption's server data
	 */
	private Map<String, DescriptiveStatistics> lateBoundAggregateServerData = null;

	/**
	 * printable summary of global server data
	 */
	private StringBuffer sbAggregateServerSummary = null;
	
	/**
	 * printable summary of global client data
	 */
	private StringBuffer sbAggregateClientSummary = null;
	
	/**
	 * aggregate of repeption's server data
	 */
	private Map<String, DescriptiveStatistics> lateBoundAggregateClientData = null;
	
	/**
	 * test if the data is valid (or useful) for this experiment.
	 */
	private boolean isValid = true;
	
	/**
	 * common timeframe for all client reps
	 */
	private long firstClientTimeStamp = -1;
	
	/**
	 * out of all the client files, which one has the most data entries
	 */
	private long maxClientRepRecords = -1;
	
	/**
	 * the sum of data entries for all clients.
	 */
	private long totalClientRecords = -1;
	
	/**
	 * Number of clients for this test.
	 */
	private int numberClients = -1;
	
	/**
	 * common timeframe for all server reps
	 */
	private long firstServerTimeStamp = -1;
	
	/**
	 * Given an experiment directory,
	 * 1. create an analysis directory
	 * 2. loop and find repetition folders of the experiment.
	 * 3. create Repetition object for each execution of the experiment.
	 * 
	 * @param experimentDirectory
	 * @throws Exception
	 */
	public Experiment(File experimentDirectory) throws Exception {
		this.experimentDirectory = experimentDirectory;
		if (!this.experimentDirectory.exists())
			throw new Exception ("bad input");
		
		analysisDirectory = new File (experimentDirectory.getAbsolutePath() + File.separator + "analysis" + File.separator);
		if (!analysisDirectory.exists()) {
			analysisDirectory.mkdirs();
		}
		
		lateBoundAggregateServerData = new Hashtable<String, DescriptiveStatistics>();
		lateBoundAggregateClientData = new Hashtable<String, DescriptiveStatistics>();
		
		repetitions = new Vector<RepetitionData>();
		
		logger.info("Examine experiment directory: " + this.experimentDirectory.getName());
		
		for (File test : this.experimentDirectory.listFiles()) {				
			if (test.isDirectory()) {
				String name = test.getName();
				if (name.matches(AnalysisArguments.REP_KEY_REG_EX_SUCCESS)) {
					RepetitionData rep = new RepetitionData(test, this);
					repetitions.add(rep);
				} 
			}
		}
		
		gatherClientStats();
		firstClientTimeStamp = identifyFirstClientTimeStamp(this.repetitions);
		firstServerTimeStamp = identifyFirstServerTimeStamp(this.repetitions);
			
		if (repetitions.size() == 0) {
			logger.warn("The experiment directory: " + this.experimentDirectory.getName() + " is not valid. It contains 0 valid repetitions.");
			this.isValid = false;
		} else if (this.numberClients <= 0) {
			logger.warn("The experiment directory: " + this.experimentDirectory.getName() + " is not valid. It contains 0 clients, or a non-uniform number of client data across repetitions");
			this.isValid = false;
		} else {
			logger.info("Experiment directory contains " + this.repetitions.size() + " repetitions, testing " + this.numberClients + " clients.");
			logger.debug("First server time stamp: " + this.firstServerTimeStamp +  " first client time stamp: " + this.firstClientTimeStamp + " total client records: " + this.totalClientRecords);
			this.isValid = true;
		}
	}
	
	/**
	 * Utility to walk the server datasets and find the first timestamp (i.e., lowest value)
	 *
	 * @param repetitions
	 * 
	 * @return
	 */
	public static long identifyFirstServerTimeStamp(Vector<RepetitionData> repetitions) {
		long ret = Long.MAX_VALUE;
		
		for (RepetitionData rep : repetitions) {
			Map<String, DescriptiveStatistics> cData = rep.serverData;
			DescriptiveStatistics ds = cData.get(AnalysisArguments.KEY_RESMON_TIMESTAMP);
			long ts = (long) ds.getMin();
			if (ts < ret) {
				ret = ts;
			}
		}
		
		if (ret == Long.MAX_VALUE)
			return -1;
		
		return ret;
	}

	/**
	 * Utility to walk the client datasets and find the first timestamp (i.e., lowest value)
	 * @param repetitions
	 * 
	 * @return
	 */
	public static long identifyFirstClientTimeStamp(Vector<RepetitionData> repetitions) {
		long ret = Long.MAX_VALUE;
		
		for (RepetitionData rep : repetitions) {
			Map<Integer, Map<String, DescriptiveStatistics>> clientData = rep.clientData;
			for (Integer client : clientData.keySet()) {
				Map<String, DescriptiveStatistics> cData = clientData.get(client);
				DescriptiveStatistics ds = cData.get(AnalysisArguments.KEY_CLI_LATENCY);
				long ts = (long) ds.getMin();
				if (ts < ret) {
					ret = ts;
				}
			}
		}
		
		if (ret == Long.MAX_VALUE)
			return -1;
		
		return ret;
	}
	
	/**
	 * Utility to collect some stats about the clients under each repetion.
	 */
	private void gatherClientStats() {		
		this.maxClientRepRecords = Long.MIN_VALUE;
		this.totalClientRecords = 0;
		this.numberClients = -1;
		
		for (RepetitionData rep : this.repetitions) {
			if (this.maxClientRepRecords < rep.getMaxClientRepRecords()) {
				this.maxClientRepRecords = rep.getMaxClientRepRecords();
			}
			
			this.totalClientRecords += rep.getTotalClientRecords();
			
			if (this.numberClients == -1) {
				this.numberClients = rep.getNumberOfClients();
			} else if (this.numberClients != rep.getNumberOfClients()) {
				logger.warn("Rep " + rep.getName() + " client count did not match earlier execution. Possibly a failed test? Expecting: " + this.numberClients + " Observed: " + rep.getNumberOfClients());
				this.numberClients = -2; // just loop out of this. There is some non-uniform sized test. Possibly a failed client?
			}
		}
	}

	/**
	 * TBD. Add some better validation code here.
	 */
	public boolean isValid() {
		return isValid;
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer("Experiment: '" + experimentDirectory.getName() + "' ");
		sb.append(", Repetitions: '" + repetitions.size() + "'");
		return sb.toString();
	}

	/**
	 * Build up individual summarizes of each server
	 */
	synchronized public String printIndividualServerSummarizes() {
		if (sbIndividualServerSummaries == null) {
			sbIndividualServerSummaries = new StringBuffer("Individual Server Summaries:" + System.lineSeparator());
			try {
				for (RepetitionData rep : repetitions) {
					sbIndividualServerSummaries.append(rep.printServerSummarizes());
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				sbIndividualServerSummaries = new StringBuffer("Error processing individual server stats.");
			}
		
		}
		return sbIndividualServerSummaries.toString();
	}
	
	/**
	 * Build up aggregate server summary.
	 */
	synchronized public String printAggregateServerSummary() {
		
		buildAggregateServerData();
		
		if (sbAggregateServerSummary == null) {
			sbAggregateServerSummary = new StringBuffer("Aggregate Server Summary for Experiment " + experimentDirectory.getName() + System.lineSeparator());
			
			try {
				
				for (String key : AnalysisArguments.KEYSET_RESMON) {
					DescriptiveStatistics data = lateBoundAggregateServerData.get(key);
					sbAggregateServerSummary.append(key + System.lineSeparator());
					sbAggregateServerSummary.append("N: \t" + data.getN() + System.lineSeparator());
					sbAggregateServerSummary.append(DataUtils.getMinFormatted(data) + System.lineSeparator());
					sbAggregateServerSummary.append(DataUtils.getMeanFormatted(data) + System.lineSeparator());
					sbAggregateServerSummary.append(DataUtils.getMaxFormatted(data) + System.lineSeparator());
					sbAggregateServerSummary.append(DataUtils.getStdDevFormatted(data) + System.lineSeparator());
					sbAggregateServerSummary.append(DataUtils.getSkewFormatted(data) + System.lineSeparator());
				}
				
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		
		}
		return sbAggregateServerSummary.toString();
	}
	
	/**
	 * Build up an aggregate server summary for some keys.
	 */
	public String generateAggregateServerSummary(String [] forKeySet) {
		
		buildAggregateServerData();
		
		StringBuffer sbTemp = new StringBuffer("Aggregate Server Summary for Experiment " + experimentDirectory.getName() + System.lineSeparator());
			
		try {
			
			for (String key : forKeySet) {
				DescriptiveStatistics data = lateBoundAggregateServerData.get(key);
				if (data == null) {
					sbTemp.append(key + " was not found in server's data." + System.lineSeparator());
				} else {
					sbTemp.append(key + System.lineSeparator());
					sbTemp.append("N: \t" + data.getN() + System.lineSeparator());
					sbTemp.append(DataUtils.getMinFormatted(data) + System.lineSeparator());
					sbTemp.append(DataUtils.getMeanFormatted(data) + System.lineSeparator());
					sbTemp.append(DataUtils.getMaxFormatted(data) + System.lineSeparator());
					sbTemp.append(DataUtils.getStdDevFormatted(data) + System.lineSeparator());
					sbTemp.append(DataUtils.getSkewFormatted(data) + System.lineSeparator());
				}
			}
			
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		

		return sbTemp.toString();
	}
	
	/**
	 * Build up an aggregate client summary for some keys
	 * 
	 * @param forKeySet
	 * @return
	 */
	public String generateAggregateClientSummary(String [] forKeySet) {
		
		buildAggregateClientData();
		
		StringBuffer sbTemp = new StringBuffer("Aggregate Client Summary for Experiment " + experimentDirectory.getName() + System.lineSeparator());
			
		try {
			
			for (String key : forKeySet) {
				DescriptiveStatistics data = lateBoundAggregateClientData.get(key);
				if (data == null) {
					sbTemp.append(key + " was not found in server's data." + System.lineSeparator());
				} else {
					sbTemp.append(key + System.lineSeparator());
					sbTemp.append("N: \t" + data.getN() + System.lineSeparator());
					sbTemp.append(DataUtils.getMinFormatted(data) + System.lineSeparator());
					sbTemp.append(DataUtils.getMeanFormatted(data) + System.lineSeparator());
					sbTemp.append(DataUtils.getMaxFormatted(data) + System.lineSeparator());
					sbTemp.append(DataUtils.getStdDevFormatted(data) + System.lineSeparator());
					sbTemp.append(DataUtils.getSkewFormatted(data) + System.lineSeparator());
				}
			}
			
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		

		return sbTemp.toString();
		
	}

	/**
	 * build global data set for servers...
	 * 
	 * NOTE this is unsorted data that is not aligned by timestamp
	 */
	private boolean buildAggregateServerData() {
		synchronized(lateBoundAggregateServerData) {
			if (lateBoundAggregateServerData.size() == 0) {
				AtomicBoolean isFirst = new AtomicBoolean(true);

				for (RepetitionData rep : repetitions) {
					Map<String, DescriptiveStatistics> repData = rep.serverData;
					// maybe replace with the merge function here...
					for (String key : repData.keySet()) {
						
						// check if the key exits and create it if not.
						DescriptiveStatistics ds = lateBoundAggregateServerData.get(key);
						if (ds == null) {
							ds = new DescriptiveStatistics();
							lateBoundAggregateServerData.put(key, ds);
						}
						
						// copy the data in.
						DescriptiveStatistics dsToMerge = repData.get(key);
//						for (long i = 0; i < dsToMerge.getN(); i++) {
//							ds.addValue(dsToMerge.getElement((int) i));
//						}
						
						doTimeNormalizedCopy(isFirst.getAndSet(false), this.firstServerTimeStamp, ds, dsToMerge);
					}
				}
			}
		}
		
		if (lateBoundAggregateServerData != null && lateBoundAggregateServerData.size() > 0) {
			return true;
		}
		return false;
	}
	
	/**
	 * build global data set for clients ...
	 * 
	 * NOTE this is unsorted data that is not aligned by timestamp
	 */
	private boolean buildAggregateClientData() {
		synchronized(lateBoundAggregateClientData) {
			if (lateBoundAggregateClientData.size() == 0) {
				AtomicBoolean isFirst = new AtomicBoolean(true);

				for (RepetitionData rep : repetitions) {
					Map<Integer, Map<String, DescriptiveStatistics>> repClientMap = rep.clientData;
					
					for (Integer id : repClientMap.keySet()) {
						Map<String, DescriptiveStatistics> repData = repClientMap.get(id);
						
						// maybe replace with the merge function here...
						for (String key : repData.keySet()) {
							
							// check if the key exits and create it if not.
							DescriptiveStatistics ds = lateBoundAggregateClientData.get(key);
							if (ds == null) {
								ds = new DescriptiveStatistics();
								lateBoundAggregateClientData.put(key, ds);
							}
							
							// copy the data in.
							DescriptiveStatistics dsToMerge = repData.get(key);
//							for (long i = 0; i < dsToMerge.getN(); i++) {
//								ds.addValue(dsToMerge.getElement((int) i));
//							}
							
							doTimeNormalizedCopy(isFirst.getAndSet(false), this.firstClientTimeStamp, ds, dsToMerge);
						}
					}
				}
			}
		}
		
		if (lateBoundAggregateClientData != null && lateBoundAggregateClientData.size() > 0) {
			return true;
		}
		return false;
	}

	private void doTimeNormalizedCopy(boolean isFirstSet, long baseTimestamp, DescriptiveStatistics ds, DescriptiveStatistics dsToMerge) {
		//if (isFirstSet) {
			for (long i = 0; i < dsToMerge.getN(); i++) {
				ds.addValue(dsToMerge.getElement((int) i));
			} 
//		} else {
//			long firsttimestamp = ds.
//		}
	}

	/**
	 * Build up aggregate client summary.
	 */
	synchronized public String printAggregateClientSummary() {
		if (sbAggregateClientSummary == null) {
			sbAggregateClientSummary = new StringBuffer("TBD - Aggregate Client Summary");
		}
		return sbAggregateClientSummary.toString();
	}
	
	/**
	 * get the analysis directory
	 * 
	 * @return
	 */
	public File getAnalysisDirectory() {
		return analysisDirectory;
	}
	
	/**
	 * get the experiment directory
	 * 
	 * @return
	 */
	public File getExperimentDirectory() {
		return experimentDirectory;
	}
	
	/**
	 * get aggregate server data
	 * 
	 * @return
	 */
	public Map<String, DescriptiveStatistics> getLateBoundAggregateServerData() {
		buildAggregateServerData();

		return lateBoundAggregateServerData;
	}

	/**
	 * get aggregate client data
	 * 
	 * @return
	 */
	public Map<String, DescriptiveStatistics> getLateBoundAggregateClientData() {
		buildAggregateClientData();

		return lateBoundAggregateClientData;
	}
	
	/**
	 * get repetition of this experiment.
	 * 
	 * @return
	 */
	public Vector<RepetitionData> getRepetitions() {
		return repetitions;
	}
	
	/**
	 * Utility to generate a labeled name for the exp
	 * 
	 * @return
	 */
	public String getExperimentName() {
		String ret = this.experimentDirectory.getName();
		
		return ret;
	}
}