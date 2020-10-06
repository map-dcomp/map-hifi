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
package com.bbn.map.TestingHarness.testing;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


// provides functionality to load an experiment from a CSV file

public class CSVExperimentConfiguration	extends ExperimentConfiguration
{
	Logger log = LogManager.getRootLogger();
	
	private static final String TEST_LABEL_HEADER = "test_id";			// the header for the column containing an identifier for each test
	
	private File experimentFile;
	
	
	public CSVExperimentConfiguration(File csvExperimentFile, String... parameterLabels) throws IOException
	{
		super(parameterLabels);
		this.experimentFile = csvExperimentFile;
		
		try
		{
            loadExperimentFromCSV(
                    new InputStreamReader(new FileInputStream(csvExperimentFile), Charset.defaultCharset()));
		} catch (IOException e)
		{
			log.error("Unable to load experiment from CSV file: " + csvExperimentFile);
			throw e;
		}
	}
	
	private boolean loadExperimentFromCSV(Reader csvExperimentFile) throws IOException
	{
		CSVFormat format = CSVFormat.EXCEL.withHeader();
		CSVParser parser = CSVParser.parse(csvExperimentFile, format);
		
		Map<String, Integer> headers = parser.getHeaderMap();
		
		if (!headers.containsKey(TEST_LABEL_HEADER))
		{
			log.error("The test label header '" + TEST_LABEL_HEADER + "' was not found in the csv file.");
			return false;
		}
		
		// loop through each record in the CSV file and create a TestConfiguration with an ID and parameter values
		for (CSVRecord record : parser.getRecords())
		{
			String label = record.get(TEST_LABEL_HEADER);
			String[] values = new String[getNumberOfValues()];
			
			for (int v = 0; v < getNumberOfValues(); v++)
				values[v] = record.get(getParameterLabels()[v].toString());
	
			addTestConfiguration(label, values);
		}
		
		return true;
	}
	
	public File getExperimentFile()
	{
		return experimentFile;
	}
	
//	public String[] getParameterLabels()
//	{
//		return super.getParameterLabels();
//	}
}
