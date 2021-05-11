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
package com.bbn.map.TestingHarness.testing;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


// provides functionality to load an experiment configuration from a properties file

public class ConfigurationLoader
{
	private static Logger log = LogManager.getRootLogger();
	private Properties properties = new Properties();
	
	public static final String PROPERTY_SERVICE_COMMAND = "service_command";
	public static final String PROPERTY_DATA_COLLECION_COMMAND = "data_collection_command";
	public static final String PROPERTY_CLIENT_COMMAND = "client_command";
	
	public static final String PROPERTY_EXPERIMENT_FILE = "experiment_file";
	public static final String PROPERTY_DEFAULT_REPETITIONS_PER_TEST = "default_repetitions_per_test";
	
	
	public ConfigurationLoader(File configFile) throws IOException
	{
		try
		{
            properties.load(new InputStreamReader(new FileInputStream(configFile), Charset.defaultCharset()));
		} catch (IOException e)
		{
			log.fatal("Unable to load properties file: " + configFile);
			throw e;
		}
	}
	
	public String[] getServiceCommand()
	{
		String commandStr = properties.getProperty(PROPERTY_SERVICE_COMMAND, "");
		return parseCommand(commandStr);
	}
	
	public String[] getDataCollectionCommand()
	{
		String commandStr = properties.getProperty(PROPERTY_DATA_COLLECION_COMMAND, "");
		return parseCommand(commandStr);
	}
	
	public String[] getClientCommand()
	{
		String commandStr = properties.getProperty(PROPERTY_CLIENT_COMMAND, "");
		return parseCommand(commandStr);
	}
	
	
	public File getExperimentFile()
	{
		String path = properties.getProperty(PROPERTY_EXPERIMENT_FILE, "");
		return new File(path);
	}
	
	public int getDefaultRepetitionsPerTest()
	{
		String r = properties.getProperty(PROPERTY_DEFAULT_REPETITIONS_PER_TEST, "");
		int repetitions = 1;
		
		try
		{
			repetitions = Integer.parseInt(r);
		} catch (NumberFormatException e)
		{
			log.error("Unable to parse integer from property '" + PROPERTY_DEFAULT_REPETITIONS_PER_TEST + "'. Defaulting to " + repetitions + ".");
		}
		
		return repetitions;		
	}
	
	
	
	// parses an entire command string into a string array consisting of separate parameters
	public String[] parseCommand(String commandString)
	{
		return commandString.split(",");
	}
}
