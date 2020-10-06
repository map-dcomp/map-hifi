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
package com.bbn.map.TestingHarness.analysis.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.TestingHarness.analysis.utils.AggregationUtils.Bin;
import com.bbn.map.TestingHarness.analysis.utils.AggregationUtils.SummaryStatistic;
import com.bbn.map.TestingHarness.data.DataTable;
import com.bbn.map.TestingHarness.data.DataTable.Sample;



// Provides functions for creating tables with data for plots such as Histograms

public class PlotUtils
{
	private static Logger log = LogManager.getLogger(PlotUtils.class);
	
	private static final String HISTOGRAM_BIN_INDEX_COLUMN_LABEL = "bin_index";
	
	
	
	
	// Converts inputTable into a histogram by placing Samples in bins and processing each bin, summarizing it to determine the height of its corresponding bar in the histogram.
	// binSelector determines which bin along the histogram's X axis each Sample should be placed in.
	// binProcessor determines how each bin should be summarized (how the height of the bar along the Y axis is calculated).
	public static DataTable generateHistogramDataTable(DataTable inputTable, BinSelector<DataTable.Sample, Object> binSelector, HistogramBinProcessor binProcessor, boolean addEmptyBins)
	{
		// prepare column labels for the summary statistic columns of a new table with histogram data
		String[] statisticColumnLabels = binProcessor.getStatisticColumnLabels();
		String[] columnLabels = new String[statisticColumnLabels.length + 2];
		
		// add standard columns to the table labels
		columnLabels[0] = HISTOGRAM_BIN_INDEX_COLUMN_LABEL;					// bin index column
		columnLabels[1] = binSelector.toString();		// column named according to how Samples are placed into bins
		
		// add summary statistic column labels to the table
		for (int l = 0; l < statisticColumnLabels.length; l++)
			columnLabels[l + 2] = statisticColumnLabels[l];		
		
		
		// construct the new histogram table with the appropriate columns
		DataTable histogramTable = new DataTable(AggregationUtils.addOperationToLabel(inputTable, binSelector), columnLabels);
		binProcessor.setDataTable(histogramTable);		// assign the table to binProcessor so that binProcessor can construct new Samples for the table
		
		// assign Samples from inputTable to bins
		List<Bin<DataTable.Sample>> binnedSamples = new ArrayList<>();
		AggregationUtils.sortAndMergeBins(AggregationUtils.wrapInBins(inputTable.getSamples()), binnedSamples,
				new AggregationUtils.DefaultBinComparator<>(binSelector));
//						new Comparator<DataTable.Sample>()
//				{
//					@Override
//					public int compare(Sample a, Sample b)
//					{
//						return binSelector.selectBin(a) - binSelector.selectBin(b);
//					}
//					
//				}));

		int prevBinIndex = -1;		
		
		// loop through and process each bin
		for (int b = 0; b < binnedSamples.size(); b++)
		{
			if (!binnedSamples.get(b).getElements().isEmpty())
			{
				// determine where along the X axis the bin should be placed (represented as an index)
				int binIndex = binSelector.selectBin(binnedSamples.get(b).getElements().get(0));
				
				if (addEmptyBins)
				{
					// add empty bins, to the histogram, which are excluded from the Bin<DataTable.Sample> list
					while (prevBinIndex + 1 < binIndex)
					{
						prevBinIndex++;
						
						Bin<DataTable.Sample> emptyBin = new Bin<>();
						binProcessor.processBin(emptyBin);
						emptyBin.getElements().get(0).setValue(HISTOGRAM_BIN_INDEX_COLUMN_LABEL, Integer.toString(prevBinIndex));		// set the bin index in the Sample
						emptyBin.getElements().get(0).setValue(binSelector.toString(), binSelector.getBinXValue(prevBinIndex).toString());	// set the bin label, which in the Sample column identified by the binSelector's X axis label
					
						histogramTable.addSample(emptyBin.getElements().get(0));
					}
				}
				
				// process and add the current bin's Sample, which consists of summary statistics, to the table
				Bin<DataTable.Sample> currentBin = binnedSamples.get(b);
				binProcessor.processBin(currentBin);
				currentBin.getElements().get(0).setValue(HISTOGRAM_BIN_INDEX_COLUMN_LABEL, Integer.toString(binIndex));		// set the bin index in the Sample
				currentBin.getElements().get(0).setValue(binSelector.toString(), binSelector.getBinXValue(binIndex).toString());	// set the bin label, which in the Sample column identified by the binSelector's X axis label
				
				histogramTable.addSample(currentBin.getElements().get(0));
				
				prevBinIndex = binIndex;
			}
		}
		
		return histogramTable;
	}
	
	// Determines the bin in which each DataTable.Sample should be placed
	// The determination is made according to the timestamp of the Sample
	static class TimestampBinSelector extends BinSelector<DataTable.Sample, Object>
	{
		private long binSize;
		
		public TimestampBinSelector(long binSize)
		{
			this.binSize = binSize;
		}

		@Override
		public int selectBin(Sample sample)
		{
			long time = sample.getTimestamp();
			
			int index = (int) Math.floor(time / binSize);		// determine the index of the bin that contains value
			return index;
		}

		// returns a label to identify the bin
		@Override
		Object getBinXValue(int binIndex)
		{
			float binStart = binSize * binIndex;		// identify a bin by the smallest value that can be placed in the bin
			return binStart;
		}

		// Returns the label of the value that identifies a particular bin along the X axis
		public String getBinXAxisLabel()
		{
			return "bin_" + "timestamp";
		}
		
		@Override
		public String toString()
		{
			return getBinXAxisLabel();
		}
	}
	
	// Determines the bin in which each DataTable.Sample should be placed
	// The determination is made according to the value under the column identified by selectionValueLabel.
	static class ByValueLabelBinSelector extends BinSelector<DataTable.Sample, Object>
	{
		private String selectionValueLabel;
		private float binSize;
		
		public ByValueLabelBinSelector(String selectionValueLabel, float binSize)
		{
			this.selectionValueLabel = selectionValueLabel;
			this.binSize = binSize;
		}

		@Override
		public int selectBin(Sample sample)
		{
			String valueStr = sample.getValue(selectionValueLabel);
			
			if (valueStr == null)
				throw new IllegalArgumentException("The given sample cannot be placed in a bin because its value under '" + selectionValueLabel + "' is null.");
			
			double value = Double.parseDouble(valueStr);
			int index = (int) Math.floor(value / binSize);		// determine the index of the bin that contains value
			return index;
		}

		// returns a label to identify the bin
		@Override
		Object getBinXValue(int binIndex)
		{
			float binStart = binSize * binIndex;		// identify a bin by the smallest value that can be placed in the bin
			return binStart;
		}

		// Returns the label of the value that identifies a particular bin along the X axis
		public String getBinXAxisLabel()
		{
			return "bin_" + selectionValueLabel;
		}
		
		@Override
		public String toString()
		{
			return getBinXAxisLabel();
		}
	}
	
	
	
	// Determines the relative ordering of histogram bins using a BinSelector
	static class HistogramBinComparator<T> implements Comparator<T>
	{
		private BinSelector<T,?> binSelector;
		
		public HistogramBinComparator(BinSelector<T,?> binSelector)
		{
			this.binSelector = binSelector;
		}

		@Override
		public int compare(T a, T b)
		{
			return binSelector.selectBin(a) - binSelector.selectBin(b);
		}
	}
	
	// selects a Bin index for a value of type T
	static abstract class BinSelector<T, U> implements Comparator<T>
	{
		// returns the bin index for value
		abstract int selectBin(T value);
		
		// returns the label for the bin with the given index
		abstract U getBinXValue(int binIndex);

		@Override
		public int compare(T a, T b)
		{
			return selectBin(a) - selectBin(b);
		}
	}
	

	// averages a the values for a column of data from the Samples in a histogram bin
	static class HistogramBinAverager implements SummaryStatistic<Float, Float>
	{			
		@Override
		public Float calculate(List<Float> data)
		{
			float total = 0;
			
			for (int n = 0; n < data.size(); n++)
				total += data.get(n);
			
			return (total / data.size());
		}

		@Override
		public String getOperationLabel()
		{
			return "bin_average";
		}
	}
	
	// determines the count or number of values for a column of data from the Samples in a histogram bin
	static class HistogramBinElementCounter implements SummaryStatistic<Float, Integer>
	{		
		@Override
		public Integer calculate(List<Float> data)
		{
			// return the number of data values in a column of the bin samples
			return data.size();
		}

		@Override
		public String getOperationLabel()
		{
			return "bin_value_count";
		}
	}
	
	// determines the largest value in a List of Floats with order rank less than the the list size * percentile
	static class HistogramBinPercentile implements SummaryStatistic<Float, Float>
	{	
		public static float MINIMUM = 0.0F;
		public static float MEDIAN = 0.5F;
		public static float MAXIMUM = 1.0F;
		
		private float percentile;
		
		
		public HistogramBinPercentile(float percentile)
		{
			this.percentile = percentile;
		}		
		
		@Override
		public Float calculate(List<Float> data)
		{
			List<Float> data2 = new ArrayList<>();
			data2.addAll(data);
			
			Collections.sort(data2);
			
			return data2.get((int) ((data2.size() - 1) * percentile));
		}

		@Override
		public String getOperationLabel()
		{
			return "bin_percentile_" + percentile;
		}
	}
	
	// Used to process each bin for a histogram by replacing a List of old Samples in a bin with a new sample to summarize the bin.
	// The result is a new Sample in each bin with a column for each SummaryStatistic applied to
	// each column that is identified by columnLabel[n] in the original data.
	static class HistogramBinProcessor implements AggregationUtils.BinProcessor<DataTable.Sample>
	{
		private DataTable dataTable;		// used for constructing new Samples
		private String[] columnLabels;
		
		private List<SummaryStatistic<Float, ?>> summaryStatistics = new ArrayList<>();
		

		public HistogramBinProcessor(String... columnLabels)
		{
			this.columnLabels = columnLabels;
		}
		
		// sets the dataTable for which the new Samples will be created
		public void setDataTable(DataTable dataTable)
		{
			this.dataTable = dataTable;
		}
		
		// add a new SummaryStatistic for processing data in each historgram bin
		public void addSummaryStatistic(SummaryStatistic<Float, ?> statistic)
		{
			summaryStatistics.add(statistic);
		}
		
		@Override
		public void processBin(Bin<Sample> bin)
		{
			String[] values = new String[columnLabels.length * summaryStatistics.size() + 2];
			DataTable.Sample summarySample = dataTable.getNewSample(0, values);
			
			if (bin != null)
			{
				for (int cl = 0; cl < columnLabels.length; cl++)
				{
					List<Float> columnValues = new ArrayList<>();
					
					for (int s = 0; s < bin.getElements().size(); s++)
					{
						Object value = bin.getElements().get(s).getValue(columnLabels[cl]);
						
						if (value != null)
						{
							if (value instanceof Float)
								columnValues.add((Float) value);
							else
								columnValues.add(Float.parseFloat(value.toString()));
						}
					}
					
					// apply each statistic to the values in a column and add the results to the summary sample
					for (SummaryStatistic<Float, ?> s : summaryStatistics)
					{
						Object stat = s.calculate(columnValues);
						summarySample.setValue(getStatisticColumnLabel(s, columnLabels[cl]), stat.toString());
					}
				}
			}
			
			bin.clear();
			bin.add(summarySample);
		}
		
		// returns the column labels with the operation label added on to them to produce labels for summary statistic columns
		// such as "latency" -> "bin_average(latency)"
		public String[] getStatisticColumnLabels()
		{
			String[] labels = new String[columnLabels.length * summaryStatistics.size()];
			
			for (int s = 0; s < summaryStatistics.size(); s++)
			{
				
				for (int n = 0; n < columnLabels.length; n++)
					labels[s*columnLabels.length + n] = getStatisticColumnLabel(summaryStatistics.get(s), columnLabels[n]); //getOperationLabel() + "(" + columnLabels[n] + ")";
			}
			
			return labels;
		}
		
		// Returns the label for a column with data resulting from the given statistic applied to data in the column with the given column label
		public String getStatisticColumnLabel(SummaryStatistic<?, ?> statistic, String columnLabel)
		{
			return statistic.getOperationLabel() + "(" + columnLabel + ")";
		}

		@Override
		public String getOperationLabel()
		{
			// TODO Auto-generated method stub
			return null;
		}
	}
}
