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
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.TestingHarness.analysis.utils.PlotUtils.BinSelector;
import com.bbn.map.TestingHarness.analysis.utils.PlotUtils.ByValueLabelBinSelector;
import com.bbn.map.TestingHarness.analysis.utils.PlotUtils.HistogramBinAverager;
import com.bbn.map.TestingHarness.analysis.utils.PlotUtils.HistogramBinElementCounter;
import com.bbn.map.TestingHarness.analysis.utils.PlotUtils.HistogramBinProcessor;
import com.bbn.map.TestingHarness.data.CSVDataTable;
import com.bbn.map.TestingHarness.data.DataTable;
import com.bbn.map.TestingHarness.data.DataTable.Sample;

public class PlotTester
{
	private static Logger log = LogManager.getRootLogger();
	
	public static void main(String[] args)
	{
		File inputFileFolder = new File("merge_test");
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
					table.upscaleTimestamps(1000);
				
				dataTables.add(table);
				log.info("Loaded table: " + table.getLabel());
			} catch (IOException e)
			{
				log.error("Failed to load table: " + table.getLabel());
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// merge input DataTables together
		DataTable aggregateDataTable = AggregationUtils.merge(dataTables);
		AggregationUtils.fillNullValues(aggregateDataTable);
		AggregationUtils.removeNullValuedSamples(aggregateDataTable);
		
		aggregateDataTable.processTable(new DataTable.ColumnAppender() {

			@Override
			public List<String> getNewColumnLabels()
			{
				List<String> labels = new ArrayList<>();
				labels.add("requests/second");
				return labels;
			}

			@Override
			public Object getValueForLabel(Sample sample, String columnLabel)
			{
				switch (columnLabel)
				{
					case "requests/second":
						return (1000 / Float.parseFloat(sample.getValue("latency")));
				}
				
				return null;
			}
			
		});
		
		// create histogram
		String[] yValueLabels = {" %CPU", " %MEM"};		
		HistogramBinProcessor binProcessor = new HistogramBinProcessor(yValueLabels);
		binProcessor.addSummaryStatistic(new HistogramBinAverager());
		binProcessor.addSummaryStatistic(new HistogramBinElementCounter());
		
//		ByValueLabelBinSelector binSelector = new ByValueLabelBinSelector("latency", 5000);
		ByValueLabelBinSelector binSelector = new ByValueLabelBinSelector("requests/second", 0.0001F);

		DataTable histogram = PlotUtils.generateHistogramDataTable(aggregateDataTable, binSelector, binProcessor, false);		
		
		
		// output result
		CSVDataTable histogram2 = new CSVDataTable(new File("histogram_table.csv"));
		histogram2.linkDataFrom(histogram);
		
		CSVDataTable aggregateDataTable2 = new CSVDataTable(new File("aggregateDataTable.csv"));
		aggregateDataTable2.linkDataFrom(aggregateDataTable);
//		System.out.println(aggregateDataTable2.toString());
		
		try
		{
			histogram2.outputToCSVFile();
			aggregateDataTable2.outputToCSVFile();
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
