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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.TestingHarness.data.DataTable;
import com.bbn.map.TestingHarness.data.DataTable.Sample;


// Provides utility functions for merging multiple DataTables together

public class AggregationUtils
{
	private static Logger log = LogManager.getLogger(AggregationUtils.class);
	
	// the label used to indicates which DataTable a sample originated from in a merged DataTable
	public static final String MERGED_DATATABLE_SOURCE_COLUMN_LABEL = "<source_table>";
	
	// merges multiple DataTables into one DataTable according to the order of time stamps of the Samples in the DataTables
	public static DataTable merge(List<DataTable> dataTables)
	{
		NullBinProcessor<DataTable.Sample> binProcessor = new NullBinProcessor<>();
		DataTable result = merge(dataTables, new SampleTimestampComparator(), binProcessor);
		
		log.debug("merge processed " + binProcessor.getBinsProcessed() + " bins");
		
		return result;
	}
	
	// merges any number of DataTables into one DataTable ordering the Samples across different DataTables according to sampleComparator
	public static DataTable merge(List<DataTable> dataTables, Comparator<DataTable.Sample> sampleComparator, BinProcessor<DataTable.Sample> binProcessor)
	{
		// start an empty merged DataTable
		DataTable result = startMergedTable(dataTables, mergeLabels(dataTables, sampleComparator), false);
		
		// extract the Samples from the DataTables
		List<List<DataTable.Sample>> sampleLists = new ArrayList<>();
		
		for (int dt = 0; dt < dataTables.size(); dt++)
			sampleLists.add(dataTables.get(dt).getSamples());
		

		List<Bin<DataTable.Sample>> binList = new ArrayList<>();		// stores merged bin lists
		
		// create a list of Bins, each of which wrap a DataTable.Sample
		List<List<Bin<DataTable.Sample>>> binLists = new ArrayList<>();
		
		// wrap all DataTable.Sample for each List<DataTable.Sample> in valueLists
		for (int dt = 0; dt < sampleLists.size(); dt++)
			binLists.add(wrapInBins(sampleLists.get(dt)));
		
		// merge the lists of bins into one list of bins
		mergeBins(binLists, binList, new DefaultBinComparator<DataTable.Sample>(sampleComparator));

		processBins(binList, binProcessor); 	// perform an operation on each merged bin
		
		// extract the Samples from the bins
		List<DataTable.Sample> samples = new ArrayList<>();
		int maxBinSize = unwrapValuesInBins(binList, samples);
		log.debug("unwrapValuesInBins max Bin size: " + maxBinSize);
		
		// reassign the samples to the new DataTable result
		reassignSamples(samples, result);
		
		// update column labels in result to show that the binProcessor operation has been performed on each sample
		if (binProcessor != null)
			result.addOperationStringToColumnLabels(binProcessor.getOperationLabel());
		
		return result;		
	}
	
	// scans through the Samples in a DataTable and fills in null values (empty cells) with estimates
	public static void fillNullValues(DataTable dataTable)
	{
		List<DataTable.Sample> samples = dataTable.getSamples();
		
		for (int s = 0; s < samples.size(); s++)
			for (int v = 0; v < samples.get(s).getNumberOfValues(); v++)
				if (samples.get(s).getValue(v) == null)
					fillValueInSample(dataTable, s, v);
	}
	
	// scans through the Samples in a DataTable and removes any Samples with null values
	public static void removeNullValuedSamples(DataTable dataTable)
	{
		List<DataTable.Sample> samples = dataTable.getSamples();
		
		int s = 0;
		
		while (s < samples.size())
		{
			boolean sampleRemoved = false;
			
			for (int v = 0; !sampleRemoved && v < samples.get(s).getNumberOfValues(); v++)
				if (samples.get(s).getValue(v) == null)
				{
					samples.remove(s);
					sampleRemoved = true;
				}
			
			if (!sampleRemoved)
				s++;
		}
	}
	
	// Fills a value with the given valueIndex in the Sample with the given sampleIndex in dataTable
	// by estimating its current value based on surrounding values.
	// Returns true if the null value can be filled and false otherwise
	private static boolean fillValueInSample(DataTable dataTable, int sampleIndex, int valueIndex)
	{
		// fill the value from the previous value if it is available
		if (sampleIndex > 0)
		{
			String newValue = dataTable.getSample(sampleIndex - 1).getValue(valueIndex);
			
			if (newValue == null)
				return false;
			
			dataTable.getSample(sampleIndex).setValue(valueIndex, newValue);
		}
		
		return true;
	}
	
	// Takes a list of lists of values of type T, wraps the value in Bin<T>,
	// and merges the lists into one large list of Bin<T> according to the given comparator.
	public static <T> void wrapAndMerge(List<List<T>> valueLists, List<Bin<T>> mergedBinnedValues, Comparator<Bin<T>> binComparator)
	{
		// create a list of Bins, each of which wrap a T
		List<List<Bin<T>>> binLists = new ArrayList<>();
		
		// wrap all T for each List<T> in valueLists
		for (int dt = 0; dt < valueLists.size(); dt++)
			binLists.add(wrapInBins(valueLists.get(dt)));
		
		// merge the lists of bins into one list of bins
		mergeBins(binLists, mergedBinnedValues, binComparator);
	}
	
	// constructs a new DataTable with column labels obtained from all of the given DataTables and with the given label
	public static DataTable startMergedTable(List<DataTable> dataTables, String label, boolean byColumnOrder)
	{
		List<String> columnLabels = mergeColumnLabels(dataTables, byColumnOrder);
		columnLabels.add(MERGED_DATATABLE_SOURCE_COLUMN_LABEL);		// add column indicating where Sample originated from to resulting DataTable
		
		// start the resulting DataTable
		DataTable result = new DataTable(label, columnLabels);
		
		return result;
	}
	
	// Copies and assigned each Sample in a List to the given dataTable by associating the source and destination column labels
	public static void reassignSamples(List<DataTable.Sample> samples, DataTable dataTable)
	{
		for (int n = 0; n < samples.size(); n++)
			reassignSample(samples.get(n), dataTable);
	}
	
	// Takes the values of a given Sample and assigns them to a new Sample according to the association of the column labels of the given dataTable
	// and the column labels of the DataTable containing oldSample. The result is added to the given dataTable as a new Sample.
	public static void reassignSample(DataTable.Sample sample, DataTable dataTable)
	{
		DataTable.Sample oldSample = sample; // dataTables.get(currentBestIndex).getSample(indices[currentBestIndex]);
		String[] newSampleValues = new String[dataTable.getNumberOfColumns()];
		
		// try to copy each value from old Sample to newSampleValues
		for (int v = 0; v < oldSample.getNumberOfValues(); v++)
		{
			try
			{
				newSampleValues[dataTable.getColumnIndex(oldSample.getValueLabel(v))] = oldSample.getValue(v);
			} catch (IllegalArgumentException e) {}
		}
		
		// try to add label of source DataTable to values
		try
		{
			newSampleValues[dataTable.getColumnIndex(MERGED_DATATABLE_SOURCE_COLUMN_LABEL)] = oldSample.getTableLabel();
		} catch (IllegalArgumentException e) {}
		
		dataTable.addSample(oldSample.getTimestamp(), newSampleValues);
	}
	
	// Merges the columns labels from a list of DataTables into one large list of column labels
	// If byColumnOrder is true then columns with the same index but in different tables will be grouped next to each other.
	// If byColumnOrder is false then columns in the same table will be grouped next to each other
	private static List<String> mergeColumnLabels(List<DataTable> dataTables, boolean byColumnOrder)
	{
		List<String> columnLabels = new ArrayList<>();
		
		if (!byColumnOrder)
		{
			List<String> newColumnLabels = new ArrayList<>();
			
			// loop through all of the DataTables
			for (int dt = 0; dt < dataTables.size(); dt++)
			{
				// loop through all of the columns in the current DataTable
				for (int c = 0; c < dataTables.get(dt).getNumberOfColumns(); c++)
				{
					String label = dataTables.get(dt).getColumnLabel(c);
					
					if (!columnLabels.contains(label))
						newColumnLabels.add(label);
				}
				
				// transfer the new column labels to columnLabels
				columnLabels.addAll(newColumnLabels);
				newColumnLabels.clear();
			}
		}
		else
		{
			int c = 0;
			boolean newColumnFound;
			
			do
			{
				newColumnFound = false;

				
				for (int dt = 0; dt < dataTables.size(); dt++)
				{
					if (c < dataTables.get(dt).getNumberOfColumns())
					{
						newColumnFound = true;
						
						String label = dataTables.get(dt).getColumnLabel(c);
						
						if (!columnLabels.contains(label))
							columnLabels.add(label);
					}
				}
				c++;
			} while (newColumnFound);
		}
		
		return columnLabels;
	}
	
	
	// adds an operation description string from sampleComparator to the label of the given DataTable
	// and returns the result
	public static String addOperationToLabel(DataTable dataTable, Comparator<DataTable.Sample> sampleComparator)
	{
		StringBuilder b = new StringBuilder();
		b.append(sampleComparator.toString());
		b.append("(");
		b.append(dataTable.getLabel());
		b.append(")");
		
		return b.toString();
	}
	
	// merges the labels of the given DataTables for the given Comparator<DataTable.Sample>
	public static String mergeLabels(List<DataTable> dataTables, Comparator<DataTable.Sample> sampleComparator)
	{
		StringBuilder b = new StringBuilder();
		b.append("merge");
		b.append("_");
		b.append(sampleComparator.toString());
		b.append("(");
		
		for (int dt = 0; dt < dataTables.size(); dt++)
		{
			if (dt > 0)
				b.append(",");
			
			b.append(dataTables.get(dt).getLabel());
		}
		
		b.append(")");
		
		return b.toString();
	}
	
	// Applies the operation of a BinProcessor<T> to a each Bin<T> in a list
	public static <T> void processBins(List<Bin<T>> binList, BinProcessor<T> binProcessor)
	{
		if (binProcessor != null)
		{
			for (Bin<T> b : binList)
				binProcessor.processBin(b);
		}
	}
	
	// Maps each Bin<T> in a List to a Bin<U> according to a BinMapper<T,U>
	public static <T,U> void mapBins(List<Bin<T>> binInputList, List<Bin<U>> binOutputList, BinToBinMapper<T,U> binMapper)
	{
		map(binInputList, binOutputList, binMapper);
	}
	
	// Maps each Bin<T> in a List to a U according to a BinMapper<T,U>
	public static <T,U>  List<U> mapBins(List<Bin<T>> binInputList, BinMapper<T,U> binMapper)
	{
		List<U> binOutputList = new ArrayList<>();
		
		for (Bin<T> b : binInputList)
			binOutputList.add(binMapper.map(b));
		
		return binOutputList;
	}
	
	// Maps each Bin<T> in a List to a U according to a BinMapper<T,U>
	public static <T,U> void mapBins(List<Bin<T>> binInputList, List<U> binOutputList, BinMapper<T,U> binMapper)
	{
		map(binInputList, binOutputList, binMapper);
	}
	
	// Maps eachT in a List to a U according to a Mapper<T,U>
	public static <T,U> void map(List<T> input, List<U> output, Mapper<T,U> mapper)
	{
		for (T b : input)
			output.add(mapper.map(b));
	}
	
	// Takes a List<Bin<T>> and produces an output list with the Bin<T> in ascending order according to the given binComparator.
	// Equivalent Bin<T> are merged together
	public static <T> void sortAndMergeBins(List<Bin<T>> binListIn, List<Bin<T>> binListOut, Comparator<Bin<T>> binComparator)
	{
		if (binListIn.isEmpty())
			return;
		
		List<Bin<T>> sortedBins = new ArrayList<>();
		sortedBins.addAll(binListIn);
		sortedBins.sort(binComparator);		// sort bins
		
		// add ascending bins, and combine consecutive equal bins
		binListOut.add(sortedBins.get(0));

		for (int n = 1; n < sortedBins.size(); n++)
		{
			Bin<T> currentBin = sortedBins.get(n);
			Bin<T> previousBin = binListOut.get(binListOut.size() - 1);
			
			// check if the current Bin is equal to the most recently added bin
			if (binComparator.compare(currentBin, previousBin) == 0)
				previousBin.addElementsFrom(currentBin);			// merge the current bin with the previous bin if they are equal
			else
				binListOut.add(currentBin);		// add the current bin to the end of the ascending output list otherwise
		}
	}
	
	// Merges multiple List<<Bin<T>> into one big list of List<Bin<T>> according to binComparator
	// The bins inside each List<Bin<T>> are assumed to already be sorted according to binComparator.
	// The binComparator is used to create a new list with bins in ascending order.
	// If two bins from different List<Bin<T>> are considered equal then the bins are combined into one bin when added to binList
	public static <T> void mergeBins(List<List<Bin<T>>> binLists, List<Bin<T>> binList, Comparator<Bin<T>> binComparator)
	{
		// keeps track of the index of the next Bin to be inserted from each List<Bin<T>> in binLists
		int[] indices = new int[binLists.size()];

		int currentBestIndex;
		Bin<T> previousBestBin = null;
		
		do
		{
			currentBestIndex = -1;
			
			// find the index of the List<Bin<T>> containing the next smallest Bin<T>
			for (int dt = 0; dt < binLists.size(); dt++)
			{
				if (indices[dt] < binLists.get(dt).size())		// if there are remaining Bin<T> in the list with index dt
				{
					if (currentBestIndex >= 0)
					{
						int comparison = binComparator.compare(binLists.get(dt).get(indices[dt]),
								binLists.get(currentBestIndex).get(indices[currentBestIndex]));
						
						if (currentBestIndex == -1 || comparison < 0)
							currentBestIndex = dt;
					}
					else
					{
						currentBestIndex = dt;
					}
				}
			}
			
			// if there exists a next smallest element to be added to binList next
			if (currentBestIndex >= 0)
			{
				Bin<T> currentBestBin = binLists.get(currentBestIndex).get(indices[currentBestIndex]);

				// check if the current smallest bin to be added next is equivalent to the previously added bin
				if (previousBestBin != null && binComparator.compare(currentBestBin, previousBestBin) == 0)
				{
					previousBestBin.addElementsFrom(currentBestBin); 		// merge the current Bin with the previous Bin, which is equivalent
				}
				else
				{
					binList.add(currentBestBin);		// add the current Bin after the previous Bin
					previousBestBin = currentBestBin;
				}
				
				indices[currentBestIndex]++;	// increment the index for the DataTable whose sample was added
			}
		} while (currentBestIndex != -1);
	}
	
	// contains a collection of elements that are considered equal according to some Comparator<T>
	static class Bin<T>
	{
		List<T> elements = new LinkedList<>();
		
		public Bin()
		{
			
		}
		
		public void clear()
		{
			elements.clear();
		}
		
		public void add(T element)
		{
			elements.add(element);
		}
		
		public void addAll(Collection<T> elements)
		{
			elements.addAll(elements);
		}
		
		public List<T> getElements()
		{
			return elements;
		}
		
		// returns a new Bin containing the elements of a and b
		public static <T> Bin<T> combine(Bin<T> a, Bin<T> b)
		{
			Bin<T> bin = new Bin<>();
			bin.addAll(a.getElements());
			bin.addAll(b.getElements());
			return bin;
		}
		
		// adds the elements of the given Bin to this Bin
		public void addElementsFrom(Bin<T> bin)
		{
			elements.addAll(bin.getElements());
		}
	}
	
	
	// turns a List<T> into List<Bin<T>> by wrapping each element in the given List<T> inside of a List<T> of size 1
	public static <T> List<Bin<T>> wrapInBins(List<T> list)
	{
		List<Bin<T>> newList = new ArrayList<>();
		AggregationUtils.<T>wrapInBins(list, newList);
		return newList;
	}
	
	// turns a List<T> into List<Bin<T>> by wrapping each element in the given List<T> inside of a Bin<T> of size 1
	public static <T> void wrapInBins(List<T> list, List<Bin<T>> binList)
	{		
		for (int n = 0; n < list.size(); n++)
		{
			Bin<T> newSubList = new Bin<>();
			newSubList.add(list.get(n));
			
			// add sub list of size 1 to listOfLists
			binList.add(newSubList);
		}
	}
	
	// Extracts all of the T elements from each Bin<T> and adds them to a new List<T>
	// Returns the new List<T>
	public static <T> List<T> unwrapValuesInBins(List<Bin<T>> binList)
	{
		List<T> list = new ArrayList<>();
		unwrapValuesInBins(binList, list);
		return list;
	}
	
	// Extracts all of the T elements from each Bin<T> and adds them to a List<T>
	public static <T> int unwrapValuesInBins(List<Bin<T>> binList, List<T> list)
	{
		int maxSize = 0;
		
		for (int n = 0; n < binList.size(); n++)
		{
			maxSize = Math.max(binList.get(n).getElements().size(), maxSize);
			list.addAll(binList.get(n).getElements());
		}
		
		return maxSize;
	}

	
	
	// Compares two Samples according to their time stamps so that they can be ordered chronologically
	static class SampleTimestampComparator implements Comparator<DataTable.Sample>
	{
		@Override
		public int compare(Sample a, Sample b)
		{
			return (int) (a.getTimestamp() - b.getTimestamp());
		}
		
		@Override
		public String toString()
		{
			return "timestamp-order";
		}		
	}
	
	// Comparator that compares Bin<T> according to a Comparator<T>
	public static class DefaultBinComparator<T> implements Comparator<Bin<T>>
	{
		private Comparator<T> elementComparator;
		
		public DefaultBinComparator(Comparator<T> elementComparator)
		{
			this.elementComparator = elementComparator;
		}
		
		@Override
		public int compare(Bin<T> a, Bin<T> b)
		{
			// if one of the bins is empty, keep a before b
			if (a.getElements().isEmpty() || b.getElements().isEmpty())
				return -1;
			
			// compare the bins according to arbitrarily chosen elements since all elements in a bin are assumed to be equal
			return elementComparator.compare(a.getElements().get(0), b.getElements().get(0));
		}
	}
	
	// Interface for objects that can perform operations on the set elements of a bin such as a summary statistic for the Bin
	static interface BinProcessor<T>
	{
		// performs some operation on the given bin
		void processBin(Bin<T> bin);
		
		// returns a label identifying the operation that processBin performs
		String getOperationLabel();
	}
	
	// Interface for objects that can perform an operation that converts an object of type T to some other object of type U
	static interface Mapper<T, U>
	{
		// maps a T to a U
		U map(T bin);
		
		// maps T to a U with knowledge of an index for the bin
		U map(T bin, int index);
	}
	
	// Interface for objects that can perform an operation that converts a Bin<T> to some other object of type U
	static interface BinMapper<T, U> extends Mapper<Bin<T>, U>
	{
	}
	
	// Interface for objects that can perform an operation that converts a Bin<T> to some other type of bin Bin<U>
	static interface BinToBinMapper<T, U> extends BinMapper<T, Bin<U>>
	{
	}
	
	
	// a BinProcessor that does nothing to a Bin<T>
	// counts the number of Bin<T> processed
	static class NullBinProcessor<T> implements BinProcessor<T>
	{
		private int binsProcessed = 0;
		
		@Override
		public void processBin(Bin<T> bin)
		{
			// do nothing to the given bin but count how many bins have been processed
			binsProcessed++;
		}
		
		public int getBinsProcessed()
		{
			return binsProcessed;
		}

		@Override
		public String getOperationLabel()
		{
			return null;
		}		
	}
	
	// Interface for objects that can produce a summary statistic object for an array of data
	interface SummaryStatistic<T, U>
	{
		// takes in an array of data of type T and returns a summary statistic of type U
		public U calculate(List<T> data);
		
		// returns a String that represents the that this summary statistic performs
		public String getOperationLabel();
	}
}
