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
package com.bbn.map.FaceRecognition.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class DataRecorder
{
	private static Logger log = LogManager.getLogger(DataRecorder.class);
	
	private File dataOutputFile;
	private CSVPrinter printer = null;
	
	
	public DataRecorder(File dataOutputFile, String[] csvHeader)
	{
		this.dataOutputFile = dataOutputFile;
		
		try
		{
            printer = new CSVPrinter(
                    new OutputStreamWriter(new FileOutputStream(this.dataOutputFile), Charset.defaultCharset()),
                    CSVFormat.EXCEL.withHeader(csvHeader));
            

            log.info("Configured to output data to CSV file: {}", dataOutputFile);
		} catch (IOException e)
		{
			log.error("Unable to create a printer for writing to CSV file: {}", dataOutputFile);
		}
	}
	
	public void recordLatencyEvent(String eventLabel, String remoteId, long startTime, long endTime)
	{
		recordEvent(eventLabel, remoteId, startTime, endTime, endTime - startTime);
	}
	
	public void recordEvent(Object... data)
	{
		if (printer != null)
		{
			Object[] data2 = new Object[data.length + 1];
			data2[0] = Long.toString(System.currentTimeMillis());	// store recording time;
			
			for (int n = 0; n < data.length; n++)
				data2[n + 1] = data[n];
			
			try
			{
				printer.printRecord(data2);
				printer.flush();
			} catch (IOException e)
			{
				log.error("Unable to record data to CSV file: {}", dataOutputFile);
			}
		}
	}
}
