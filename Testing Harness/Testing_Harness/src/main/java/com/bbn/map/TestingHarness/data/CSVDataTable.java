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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;


// A DataTable that can be read from or written to a CSV file

public class CSVDataTable extends DataTable
{
	public static final String CSV_HEADER_TIMESTAMP = "timestamp";
	
	private File dataFile = null;

	
	public CSVDataTable(File dataFile)
	{
		super(dataFile.getName());
		setDataFile(dataFile);
	}
	
	public void setDataFile(File dataFile)
	{
		this.dataFile = dataFile;
	}
	
	
	
	public void loadFromCSVFile() throws IOException
	{
		loadFromCSVFile(dataFile);
	}
	
	private void loadFromCSVFile(File file) throws IOException
	{
		CSVFormat format = CSVFormat.EXCEL.withHeader();
        CSVParser parser = CSVParser.parse(new InputStreamReader(new FileInputStream(file), Charset.defaultCharset()),
                format);
		
		clear();	// remove any existing Samples
		
		Map<String, Integer> headers = parser.getHeaderMap();
		
		// convert the Map of CSV column headers to an array of column labels for this DataTable
		String[] columnLabels = new String[headers.size() - 1];
		
		int n = 0;
		
		for (String header : headers.keySet())
		{
			if (n > 0)		// assume that the first column is for time stamps
				columnLabels[n - 1] = header;
			
			n++;
		}
		setColumnLabels(columnLabels);		// set the column labels
		
		
		// loop through and create a DataTable.Sample for each record in the CSV file
		for (CSVRecord record : parser.getRecords())
		{
			String timestampStr = record.get(0);		// assume that the time stamp is in the first column
			long timestamp = Long.parseLong(timestampStr);			
			
			String[] values = new String[getNumberOfValues()];
			
			// transfer values from a CSV record to an array starting with the second column
			for (int v = 0; v < values.length; v++)
				values[v] = record.get(getColumnLabels()[v]);

			addSample(timestamp, values);
		}
	}
	
	
	
	public void outputToCSVFile() throws IOException
	{
		outputToCSVFile(dataFile);		
	}
	
	private void outputToCSVFile(File file) throws IOException
	{		
		CSVFormat format = CSVFormat.EXCEL.withHeader(columnLabelsToHeaderLabels(getColumnLabels()));
        CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(new FileOutputStream(file), Charset.defaultCharset()), format);
		
		for (Sample s : getSamples())
		{
			Object[] record = new Object[1 + getNumberOfValues()];
//System.out.println(record.length);
			record[0] = Long.toString(s.getTimestamp());
			
			for (int v = 1; v < record.length; v++)
			{
				String value = s.getValue(v - 1);
						
				if (value != null)
					record[v] = value.toString().trim();
				else
					record[v] = "";
			}
			
			printer.printRecord(record);
		}
		
		printer.flush();
		printer.close();
	}
	
	private String[] columnLabelsToHeaderLabels(String[] columnLabels)
	{
		String[] headers = new String[columnLabels.length + 1];
		headers[0] = CSV_HEADER_TIMESTAMP;
		
		for (int n = 1; n < headers.length; n++)
			headers[n] = columnLabels[n - 1].trim();
		
		return headers;
	}
	
	
	@Override
	public String toString()
	{
		StringBuilder b = new StringBuilder();
		b.append(super.toString() + "=\n");
		
		b.append(CSV_HEADER_TIMESTAMP);
		
		for (int n = 0; n < getColumnLabels().length; n++)
		{
			b.append(",");
			b.append(getColumnLabels()[n]);
		}
		
		for (int s = 0; s < getNumberOfSamples() && s < 10; s++)
		{
//			if (s  || s == getNumberOfSamples() - 1)
			{
				b.append("\n");
				b.append(getSample(s).getTimestamp());
				
				for (int v = 0; v < getNumberOfValues(); v++)
				{
					b.append(",");
					b.append(getSample(s).getValue(v));
				}
			}
//			else if (s == 1)
//			{
//				b.append("\n");
//				b.append("...");
//			}
		}
		
		return b.toString();
	}
}
