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
package com.bbn.map.TestingHarness.analysis;

import java.io.File;
import java.io.IOException;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.TestingHarness.analysis.inputs.AnalysisArguments;
import com.bbn.map.TestingHarness.analysis.inputs.Experiment;
import com.bbn.map.TestingHarness.analysis.outputs.BoxPlotEntry;
import com.bbn.map.TestingHarness.analysis.outputs.GnuPlot;
import com.bbn.map.TestingHarness.analysis.utils.IOUtils;
import com.bbn.map.TestingHarness.testing.CSVExperimentConfiguration;

/**
 * A very static, analysis entry point, for extracting useful aggregate data summaries and plots.
 * 
 *  These routines are designed to parse an experiment config and data set based upon some
 *  type of analysis argument.
 *
 */
final public class Control {
	static Logger logger = LogManager.getLogger(Control.class);

	/**
	 * For given inputs, return a data set based upon the Math3 DS library
	 * 
	 * @param experimentConfigFile
	 * @param experimentResultDir
	 * @param arguments
	 * @return
	 * @throws Exception
	 */
	public static Vector<Experiment> runSimpleAggregateAnalysis(File experimentConfigFile, File experimentResultDir, AnalysisArguments arguments,  File fileResultsDir) throws Exception {		
		Vector<Experiment> experimentData = identifyExperiments(experimentConfigFile, experimentResultDir);
		generateAggregateSummary(experimentData, arguments, fileResultsDir);
		generateAggregatePlots(experimentData, arguments, fileResultsDir);
		return experimentData;
	}
	
	/**
	 * generate statistics
	 * 
	 * @param experimentData
	 * @param arguments
	 */
	private static void generateAggregateSummary(Vector<Experiment> experiments, AnalysisArguments arguments, File fileResultsDir) {
		StringBuffer sbResults = new StringBuffer("");
		for (Experiment experiment : experiments) {
			sbResults.append(experiment.generateAggregateServerSummary(new String[] {AnalysisArguments.KEY_RESMON_CPU, AnalysisArguments.KEY_RESMON_MEM, AnalysisArguments.KEY_RESMON_MEM_RESIDENT}));
			sbResults.append(experiment.generateAggregateClientSummary(new String[] {AnalysisArguments.KEY_CLI_LATENCY}));
		}
		
		String t = sbResults.toString();
		File fileSummary = new File(fileResultsDir.getAbsoluteFile() + File.separator + "aggregate.txt");
		try {
			IOUtils.zeroAndWriteFile(fileSummary, t);
		} catch (Exception e) {
			e.printStackTrace();
		}
		logger.info(t);
		try {
			logger.info("Wrote aggreate analysis summary to file: " + fileSummary.getCanonicalPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * generate global plots plots
	 * 
	 * @param experimentData
	 * @param arguments
	 * @throws Exception
	 */
	private static void generateAggregatePlots(Vector<Experiment> experimentData, AnalysisArguments arguments, File fileResultsDir) throws Exception {
		logger.info("Generating aggregate plots...");
		Vector<BoxPlotEntry> bpClientLatency = new Vector<BoxPlotEntry>();
		
		Vector<BoxPlotEntry> bpServerCPU = new Vector<BoxPlotEntry>();
		Vector<BoxPlotEntry> bpServerMEM = new Vector<BoxPlotEntry>();
		Vector<BoxPlotEntry> bpServerMEMKB = new Vector<BoxPlotEntry>();

		for (Experiment experiment : experimentData) {
			if (experiment.isValid()) {
				bpClientLatency.add(BoxPlotEntry.getBoxPlot(experiment.getLateBoundAggregateClientData(), AnalysisArguments.KEY_CLI_LATENCY, experiment.getExperimentName()));
				
				bpServerCPU.add(BoxPlotEntry.getBoxPlot(experiment.getLateBoundAggregateServerData(),AnalysisArguments.KEY_RESMON_CPU, experiment.getExperimentName()));
				bpServerMEM.add(BoxPlotEntry.getBoxPlot(experiment.getLateBoundAggregateServerData(),AnalysisArguments.KEY_RESMON_MEM, experiment.getExperimentName()));
				bpServerMEMKB.add(BoxPlotEntry.getBoxPlot(experiment.getLateBoundAggregateServerData(),AnalysisArguments.KEY_RESMON_MEM_RESIDENT, experiment.getExperimentName()));
			}
		}
	
		String path = fileResultsDir.getAbsolutePath() + File.separator;
		
		File fileClientLatencyPlot = new File (path + AnalysisArguments.KEY_CLI_LATENCY.trim() + GnuPlot.GNU_PLOT_EXTENSION);
		File fileClientLatencyData = new File (path + AnalysisArguments.KEY_CLI_LATENCY.trim() + GnuPlot.GNU_PLOT_DATA_EXTENSION);
		GnuPlot.plotBoxPlot("Client Latencies", AnalysisArguments.KEY_CLI_LATENCY, bpClientLatency, fileClientLatencyPlot, fileClientLatencyData);
		
		File fileServerCPUPlot = new File (path + AnalysisArguments.KEY_RESMON_CPU.trim() + GnuPlot.GNU_PLOT_EXTENSION);
		File fileServerCPUData = new File (path + AnalysisArguments.KEY_RESMON_CPU.trim() + GnuPlot.GNU_PLOT_DATA_EXTENSION);
		GnuPlot.plotBoxPlot("Server CPU% Use", AnalysisArguments.KEY_RESMON_CPU, bpServerCPU, fileServerCPUPlot, fileServerCPUData);
		
		File fileServerMEMPlot = new File (path + AnalysisArguments.KEY_RESMON_MEM.trim() + GnuPlot.GNU_PLOT_EXTENSION);
		File fileServerMEMData = new File (path + AnalysisArguments.KEY_RESMON_MEM.trim() + GnuPlot.GNU_PLOT_DATA_EXTENSION);
		GnuPlot.plotBoxPlot("Server MEM% Use", AnalysisArguments.KEY_RESMON_MEM, bpServerMEM, fileServerMEMPlot, fileServerMEMData);
		
		File fileServerMEMResPlot = new File (path + AnalysisArguments.KEY_RESMON_MEM_RESIDENT.trim() + GnuPlot.GNU_PLOT_EXTENSION);
		File fileServerMEMResData = new File (path + AnalysisArguments.KEY_RESMON_MEM_RESIDENT.trim() + GnuPlot.GNU_PLOT_DATA_EXTENSION);
		GnuPlot.plotBoxPlot("Server MEM Resident", AnalysisArguments.KEY_RESMON_MEM_RESIDENT, bpServerMEMKB, fileServerMEMResPlot, fileServerMEMResData);
		
		// for quick use...
		GnuPlot.writeBashHelperFile(new File(path + File.separator + "open-all-plots.sh"), new String [] {AnalysisArguments.KEY_CLI_LATENCY, 
				AnalysisArguments.KEY_RESMON_MEM, AnalysisArguments.KEY_RESMON_CPU, AnalysisArguments.KEY_RESMON_MEM_RESIDENT});
		
		logger.info("Four aggregate plots (over " + experimentData.size() + " experiment(s)) were written to directory: " + path);
	}
	
	/**
	 * Locate and collect experiment data.
	 * 
	 * @param experimentConfigFile
	 * @param experimentResultDir
	 * @return
	 */
	public static Vector<Experiment> identifyExperiments(File experimentConfigFile, File experimentResultDir) {
		logger.info("Identify experiment data...");
		
		// copied from com.bbn.map.TestingHarness.testing.Tester.java
		String[] testParameterLabels = {"clients", "images", "images_per_second"};
		CSVExperimentConfiguration experimentConfiguration = null;	
		try
		{
			experimentConfiguration = new CSVExperimentConfiguration(experimentConfigFile, testParameterLabels);
		} catch (IOException e2)
		{
			System.exit(1);
		}
		
		// print some debugging info
		int testCfgs = experimentConfiguration.getNumberOfTestConfigurations();
		logger.info("Experiment file: '" + experimentConfiguration.getExperimentFile() + "' contains " + testCfgs + " test config(s).");
		
		// walk initial labels and determine FS paths for each test config.
		Vector<Experiment> experimentData = new Vector<Experiment>();
		
		logger.info("Discover tests on the file system...");
		
		for (int i = 0; i < testCfgs; i++) {
			// test harness prepends 'test_' to the test configuration label and postpends '-[0-9]' 
			// this might look like /<path>/<to>/<exp>/test_a3-0
			File e = new File (experimentResultDir.getAbsolutePath() + AnalysisArguments.TEST_KEY + experimentConfiguration.getTestConfigurationLabel(i) + "-" + i);
			
			if (e.exists() && e.isDirectory()) {
				Experiment exp;
				try {
					exp = new Experiment(e);
					experimentData.add(exp);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			} else {
				logger.warn("Experiment results dir ("+e.getAbsolutePath()+") for label: " + experimentConfiguration.getTestConfigurationLabel(i) + " did not exist, or was not a directory.");
			}
		}
		
		
		return experimentData;
	}
	
	// update to resource streams
	public static final String DIR_RESOURCE_TEST = "." + File.separator + "resources" + File.separator + "analysis_test" + File.separator;
	public static final String FILE_TEST_EXPERIMENT = DIR_RESOURCE_TEST + "face_recognition_experiment.csv";
	public static final String FILE_TEST_DATA = DIR_RESOURCE_TEST + "09292017_180311_162" + File.separator;
	
	public static final String FILE_SIMPLE_AGGREGATE_RESULTS_DIR = DIR_RESOURCE_TEST + "aggregate_results" + File.separator;

	
	public static void main(String [] args) {
		try {
			// Experimental config file for the test harness.
			String config = null; 
			
			// Experiment results directory, as a convince, assume it can be saved somewhere other than what was specified in the config file
			String results = null; 
			
			// Analysis results directory
			String analysisDir = null;
			
			if (args.length == 3) {
				config = args[0];
				results = args[1];
				analysisDir = args[2];
			} else {
				// update to something on the classpath
				config = Control.FILE_TEST_EXPERIMENT;
				results = Control.FILE_TEST_DATA;
				analysisDir = Control.FILE_SIMPLE_AGGREGATE_RESULTS_DIR;
			}
			File fileResultsDir = new File(results);
			File fileExpConfigFile = new File(config);
			File fileAnalysisDir = new File(analysisDir);
			
			AnalysisArguments analysisArgs = AnalysisArguments.getDefaultAnalysisArguments();
			
			Vector<Experiment> data = Control.runSimpleAggregateAnalysis(fileExpConfigFile, fileResultsDir, analysisArgs, fileAnalysisDir);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}