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
package com.bbn.map.TestingHarness.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


// stores a table of data similar to a CSV file

public class DataTable
{
	private String label = "";								// identifier for the entire table
	private String[] columnLabels;
	private List<Sample> samples = new ArrayList<>();

	
	public DataTable()
	{
		this("");
	}
	
	public DataTable(String label)
	{
		this(label, new String[0]);
	}
	
	public DataTable(String[] columnLabels)
	{
		this("", columnLabels);
	}
	
	public DataTable(List<String> columnLabels)
	{
		this("", columnLabels);
	}
	
	public DataTable(String label, String[] columnLabels)
	{
		this.label = label;
		setColumnLabels(columnLabels);
	}
	
	public DataTable(String label, List<String> columnLabels)
	{
		this.label = label;
		setColumnLabels(columnLabels);
	}
	
	public void setColumnLabels(String... columnLabels)
	{
		this.columnLabels = new String[columnLabels.length];
		
		for (int ml = 0; ml < columnLabels.length; ml++)
			this.columnLabels[ml] = columnLabels[ml];
	}
	
	public void setColumnLabels(List<String> columnLabels)
	{
		this.columnLabels = new String[columnLabels.size()];
		
		for (int ml = 0; ml < columnLabels.size(); ml++)
			this.columnLabels[ml] = columnLabels.get(ml);
	}
	
	// adds the specified string to each column label to indicate that an operation has been performed on each Sample's values
	public void addOperationStringToColumnLabels(String operationString)
	{
		if (operationString != null)
			for (int l = 0; l < columnLabels.length; l++)
				columnLabels[l] = operationString + "(" + columnLabels[l] + ")";
	}
	
	// performs a timeShift so that the time stamp of the first sample is 0
	public void timeNormalize()
	{
		if (getNumberOfSamples() > 0)
			timeShift(getSample(0).getTimestamp());
	}
	
	// performs a timeShift so that the time stamp of the first sample is startTime
	public void timeNormalize(long startTime)
	{
		if (getNumberOfSamples() > 0)
			timeShift(getSample(0).getTimestamp() - startTime);
	}
	
	// subtracts a reference time from all Samples in this DataTable
	public void timeShift(long referenceTime)
	{
		for (DataTable.Sample s : getSamples())
			s.timestamp -= referenceTime;
	}
	
	public String[] getColumnLabels()
	{
		return columnLabels;
	}
	
	public String getColumnLabel(int n)
	{
		return columnLabels[n];
	}
	
	public int getColumnIndex(String label)
	{
		int n = 0;
		
		while (n < columnLabels.length && !columnLabels[n].equals(label))
			n++;
		
		if (n < columnLabels.length)
			return n;
		else
			throw new IllegalArgumentException("The column label '" + label + "' does not exist in the DataTable.");
	}
	
	public int getNumberOfColumns()
	{
		return columnLabels.length;
	}
	
	public int getNumberOfValues()
	{
		return columnLabels.length;
	}
	
	// clears the list of samples
	public void clear()
	{
		samples.clear();
	}
	
	public void addSample(long timestamp, String... values)
	{
		samples.add(new Sample(timestamp, values));
	}
	
	public void addSample(Sample sample)
	{
		samples.add(sample);
	}
	
	public int getNumberOfSamples()
	{
		return samples.size();
	}
	
	public List<Sample> getSamples()
	{
		return samples;
	}
	
	public Sample getSample(int n)
	{
		return samples.get(n);
	}
	
	// sets the column headers and data to be the same as another DataTable
	public void linkDataFrom(DataTable other)
	{
		this.columnLabels = other.columnLabels;
		this.samples = other.samples;
	}
	
	// returns the label for this DataTable
	public String getLabel()
	{
		return label;
	}

	// sets the label for this DataTable
	public void setLabel(String label)
	{
		this.label = label;
	}
	
	@Override
	public String toString()
	{
		return getLabel();
	}	
	
	// returns a new samples for this DataTable
	public Sample getNewSample(long timestamp, String... values)
	{
		return new Sample(timestamp, values);
	}
	
	// multiplies all time stamps in this table by a factor to account for a different in time units
	public void upscaleTimestamps(int factor)
	{
		for (Sample s : getSamples())
			s.upscaleTimestamp(factor);
	}
	
	// divides all time stamps in this table by a factor to account for a different in time units
	public void downscaleTimestamps(int factor)
	{
		for (Sample s : getSamples())
			s.downscaleTimestamp(factor);
	}

	public class Sample
	{
		private long timestamp;
		private String[] dataValues = new String[columnLabels.length];
		
		public Sample(long timestamp, String... values)
		{
			setValues(timestamp, values);
		}
		
		public void setValues(long timestamp, String[] values)
		{
			this.timestamp = timestamp;		
			if(values.length != columnLabels.length)
				throw new IllegalArgumentException("There are " + columnLabels.length + " column labels but " + values.length + " values were given.");
			
			if (values.length != dataValues.length)
				dataValues = new String[values.length];
			
			for (int v = 0; v < values.length && v < dataValues.length; v++)
				dataValues[v] = values[v];
		}
		
		public void upscaleTimestamp(int factor)
		{
			timestamp *= factor;
		}
		
		public void downscaleTimestamp(int factor)
		{
			timestamp /= factor;
		}
		
		public long getTimestamp()
		{
			return timestamp;
		}
		
		public void setTimestamp(long timestamp)
		{
			this.timestamp = timestamp;
		}
		
		public String getValue(int n)
		{
			return dataValues[n];
		}
		
		public String getValue(String label)
		{
			return getValue(getValueIndex(label));
		}
		
		public void setValue(int n, String newValue)
		{
			dataValues[n] = newValue;
		}
		
		public void setValue(String label, String newValue)
		{
			setValue(getColumnIndex(label), newValue);
		}
		
		public int getNumberOfValues()
		{
			return dataValues.length;
		}
		
		public String getValueLabel(int n)
		{
			return DataTable.this.getColumnLabel(n);
		}
		
		public int getValueIndex(String label)
		{
			return DataTable.this.getColumnIndex(label);
		}
		
		// returns the label of the DataTable that is storing this Sample
		public String getTableLabel()
		{
			return DataTable.this.getLabel();
		}
		
		@Override
		public String toString()
		{
			StringBuilder b = new StringBuilder();
			b.append(getTimestamp() + ": ");
			
			for (int v = 0; v < dataValues.length; v++)
			{
				if (v > 0)
					b.append(", ");
				
				b.append(dataValues[v]);
			}
			
			return b.toString();
		}
	}
	
	// puts the samples in order according to their current timestamps
	// useful in cases where timestamps are recalculated and changes in the ordering results
	public void sortSamplesByTimestamp()
	{
		Collections.sort(getSamples(), new Comparator<Sample>()
		{
			@Override
			public int compare(Sample a, Sample b)
			{
				// take the difference between the timestamps and ensure that the result is within the range of an integer
				return (int) Math.max(Math.min(a.getTimestamp() - b.getTimestamp(), Integer.MAX_VALUE), Integer.MIN_VALUE);
			}
		});
	}
	
	
	// updates a table using a TableSampleProcessor
	public void processTable(TableSampleProcessor tsp)
	{
		String[] newLabels = tsp.updateColumnLabels(columnLabels);
		
		if (newLabels != null)
			columnLabels = newLabels;
//System.out.println("labels: "  + columnLabels.length);
		
		for (Sample s : getSamples())
		{
			tsp.updateSample(s);
//System.out.println("v: " + s.getNumberOfValues());
		}
	}
	
	// interface for object that can update the column labels and Samples in the table
	public interface TableSampleProcessor
	{
		// produces a new array of column labels for this table
		String[] updateColumnLabels(String[] columnLabels);
		
		// modifies a sample in accordance with the updated column labels
		void updateSample(Sample sample);
	}
	
	// Provides an easy way to specify new column labels and value calculations to be added to a DataTable
	public static abstract class ColumnAppender implements TableSampleProcessor
	{
		// returns the labels of the columns that will be added to the DataTable
		public abstract List<String> getNewColumnLabels();
		
		// computes a value for one of the new columns for the given sample
		public abstract Object getValueForLabel(Sample sample, String columnLabel);

		@Override
		public String[] updateColumnLabels(String[] columnLabels)
		{
			List<String> newColumnLabels = getNewColumnLabels();
			String[] columnLabels2 = new String[columnLabels.length + newColumnLabels.size()];
			
			// copy old labels
			for (int n = 0; n < columnLabels.length; n++)
				columnLabels2[n] = columnLabels[n];
			
			// copy new labels
			for (int n = 0; n < newColumnLabels.size(); n++)
				columnLabels2[columnLabels.length + n] = newColumnLabels.get(n);
			
			return columnLabels2;
		}

		@Override
		public void updateSample(Sample sample)
		{
			List<String> newColumnLabels = getNewColumnLabels();
			String[] values = new String[sample.getNumberOfValues() + newColumnLabels.size()];
			
			// copy old values
			for (int n = 0; n < sample.getNumberOfValues(); n++)
				values[n] = sample.getValue(n);
			
			// calculate and store new values
			for (int n = 0; n < newColumnLabels.size(); n++)
				values[sample.getNumberOfValues() + n] = getValueForLabel(sample, newColumnLabels.get(n)).toString();
			
			// update the values in the Sample
			sample.setValues(sample.getTimestamp(), values);
		}
	}
}
