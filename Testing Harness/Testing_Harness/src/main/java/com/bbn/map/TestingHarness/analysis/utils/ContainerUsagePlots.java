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
package com.bbn.map.TestingHarness.analysis.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.fitting.SimpleCurveFitter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.TestingHarness.analysis.utils.PlotUtils.BinSelector;
import com.bbn.map.TestingHarness.analysis.utils.PlotUtils.ByValueLabelBinSelector;
import com.bbn.map.TestingHarness.analysis.utils.PlotUtils.HistogramBinAverager;
import com.bbn.map.TestingHarness.analysis.utils.PlotUtils.HistogramBinElementCounter;
import com.bbn.map.TestingHarness.analysis.utils.PlotUtils.HistogramBinPercentile;
import com.bbn.map.TestingHarness.analysis.utils.PlotUtils.HistogramBinProcessor;
import com.bbn.map.TestingHarness.data.CSVDataTable;
import com.bbn.map.TestingHarness.data.DataTable;
import com.bbn.map.TestingHarness.data.DataTable.Sample;
import com.bbn.map.TestingHarness.data.DataTable.TableSampleProcessor;

public class ContainerUsagePlots
{
	private static Logger log = LogManager.getRootLogger();
	
	public static void main(String[] args)
	{
		File inputFileFolder = new File("merge_test");
		List<DataTable> clientApplicationDataTables = new ArrayList<>();
		List<DataTable> serverApplicationDataTables = new ArrayList<>();
		List<DataTable> serverResourceUsageDataTables = new ArrayList<>();
		List<DataTable> dataTables = new ArrayList<>();
		
		// read in data tables
		for (File f : inputFileFolder.listFiles())
		{
			CSVDataTable table = new CSVDataTable(f);
			
			try
			{
				table.loadFromCSVFile();
				
				// handle the difference in time units for the resmon stats
				if (table.getLabel().contains("server_resource_stats"))
				{
					table.upscaleTimestamps(1000);
					serverResourceUsageDataTables.add(table);
				} else if (table.getLabel().contains("server_application_data"))
				{
					serverApplicationDataTables.add(table);
				} else if (table.getLabel().contains("client_"))
				{
					clientApplicationDataTables.add(table);
				}

				dataTables.add(table);
				
				log.info("Loaded table: " + table.getLabel());
			} catch (IOException e)
			{
				log.error("Failed to load table: " + table.getLabel());
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
//		// create histogram for server resource usage data
//		String[] yValueLabels = {" %CPU", " %MEM"};
//		HistogramBinProcessor binProcessor = new HistogramBinProcessor(yValueLabels);
//		binProcessor.addSummaryStatistic(new HistogramBinAverager());
//		binProcessor.addSummaryStatistic(new HistogramBinElementCounter());		
//		BinSelector<DataTable.Sample, Object> binSelector = new PlotUtils.TimestampBinSelector(60_000);
//
//		DataTable serverResourceUsage = serverResourceUsageDataTables.get(0);
//		serverResourceUsage.timeNormalize();
//		DataTable serverResourceUsageHistogram = PlotUtils.generateHistogramDataTable(serverResourceUsage, binSelector, binProcessor, false);		
		
		// update the timestamps of the server latency samples to reflect the the time that requests were received
		// rather than the time that ACKs were sent
		for (DataTable dt : serverApplicationDataTables)
		{
			dt.processTable(new TableSampleProcessor()
			{
				
				@Override
				public void updateSample(Sample sample)
				{
					sample.setTimestamp(Long.parseLong(sample.getValue("time_image_received")));					
				}
				
				@Override
				public String[] updateColumnLabels(String[] columnLabels)
				{
					// TODO Auto-generated method stub
					return null;
				}
			});
			
			dt.sortSamplesByTimestamp();
			log.info("Updated timestamps of server application data table: " + dt.getLabel());
		}
		

		// merge input DataTables together
		DataTable aggregateDataTable = AggregationUtils.merge(dataTables);
		aggregateDataTable.timeNormalize();
				
		
		// create histogram
		String[] yValueLabels = {" %CPU", " %MEM", "latency"};
		HistogramBinProcessor binProcessor = new HistogramBinProcessor(yValueLabels);
		binProcessor.addSummaryStatistic(new HistogramBinAverager());
		binProcessor.addSummaryStatistic(new HistogramBinElementCounter());
		
		BinSelector<DataTable.Sample, Object> binSelector = new PlotUtils.TimestampBinSelector(60_000);		// bin by time period

		DataTable timePeriodHistogram = PlotUtils.generateHistogramDataTable(aggregateDataTable, binSelector, binProcessor, false);		
		
		
		// create histogram
		String[] yValueLabels2 = {"bin_average( %CPU)","bin_average( %MEM)"};
		
		HistogramBinProcessor binProcessor2 = new HistogramBinProcessor(yValueLabels2);
		binProcessor2.addSummaryStatistic(new HistogramBinAverager());
		binProcessor2.addSummaryStatistic(new HistogramBinElementCounter());
		binProcessor2.addSummaryStatistic(new HistogramBinPercentile(HistogramBinPercentile.MINIMUM));
		binProcessor2.addSummaryStatistic(new HistogramBinPercentile(HistogramBinPercentile.MEDIAN));
		binProcessor2.addSummaryStatistic(new HistogramBinPercentile(HistogramBinPercentile.MAXIMUM));
		
		BinSelector<DataTable.Sample, Object> binSelector2 = new PlotUtils.ByValueLabelBinSelector("bin_value_count(latency)", 1);		// bin by time period
		
		DataTable requestsMeanCPUHistogram = PlotUtils.generateHistogramDataTable(timePeriodHistogram, binSelector2, binProcessor2, false);		

		
		// perform a least squares curve fit on resource usage values
//		String[] curveFitColumnLabels = {};
//		
//		for (int cl = 0; cl < curveFitColumnLabels.length; cl++)
//		{
//			
//		}
		
		for (String cl : requestsMeanCPUHistogram.getColumnLabels())
		{
			if (cl.contains(" %CPU"))
			{
				double[] parameters = CurveFit.exponentialDecayFit(requestsMeanCPUHistogram, "bin_bin_value_count(latency)", cl);
				
				StringBuilder b = new StringBuilder();
				
				for (int p = 0; p < parameters.length; p++)
				{
//					if (p > 0)
						b.append("\n");
					
					b.append(parameters[p]);
				}
				
				log.info("Exponential Decay Curve fit: " + cl + ": " + b.toString());
			}
		}
		
		
		
		
		
		
		// output result
		CSVDataTable timePeriodHistogram2 = new CSVDataTable(new File("timePeriodHistogram.csv"));
		timePeriodHistogram2.linkDataFrom(timePeriodHistogram);
		
		CSVDataTable requestsMeanCPUHistogram2 = new CSVDataTable(new File("requestsCPUMemoryHistogram.csv"));
		requestsMeanCPUHistogram2.linkDataFrom(requestsMeanCPUHistogram);
		
		CSVDataTable aggregateDataTable2 = new CSVDataTable(new File("aggregateDataTable.csv"));
		aggregateDataTable2.linkDataFrom(aggregateDataTable);
		
		try
		{
			timePeriodHistogram2.outputToCSVFile();
			requestsMeanCPUHistogram2.outputToCSVFile();
			aggregateDataTable2.outputToCSVFile();
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
