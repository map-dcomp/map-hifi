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

import com.bbn.map.TestingHarness.data.CSVDataTable;
import com.bbn.map.TestingHarness.data.DataTable;

public class MergeTester
{
	private static Logger log = LogManager.getRootLogger();

	public static void main(String[] args)
	{
		File inputFileFolder = new File("merge_test");
		List<DataTable> dataTables = new ArrayList<>();
		
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
				log.error("Failes to load table: " + table.getLabel());
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		log.info("Loaded " + dataTables.size() + " DataTables");
		
		DataTable result1 =	AggregationUtils.merge(dataTables);
		AggregationUtils.fillNullValues(result1);
		AggregationUtils.removeNullValuedSamples(result1);
		
		log.info("Produced DataTable\n" + result1.getLabel());

		CSVDataTable result = new CSVDataTable(new File("result.csv"));
		result.linkDataFrom(result1);
		log.debug("Number of samples after: " + result.getNumberOfSamples());
		
		try
		{
			result.outputToCSVFile();
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
