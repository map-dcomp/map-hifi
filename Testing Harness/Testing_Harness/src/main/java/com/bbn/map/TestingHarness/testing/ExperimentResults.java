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

import java.util.ArrayList;
import java.util.List;


import com.bbn.map.TestingHarness.testing.ExperimentConfiguration.TestConfiguration;



// contains the results for an entire set of tests

public class ExperimentResults
{
//	private Map<DataLogKeyType, DataLog> dataLogs = new HashMap<>();
//	private Map<MonitorResultsIdentifier, MonitorResults> monitorResults = new HashMap<>();
	private List<TestResultsSummary> testResultsSummaries = new ArrayList<>();
	private TestResultsSummary currentSummary = null;
	
	public static final int TEST_STATUS_RUNNING = 0;
	public static final int TEST_STATUS_ABORTED = 1;
	public static final int TEST_STATUS_FINISHED = 2;
	
	
	
	public ExperimentResults()
	{

	}
	
	
	public void startSummary(TestConfiguration testConfiguration, int repetition)
	{
		currentSummary = new TestResultsSummary(testConfiguration, repetition);
		testResultsSummaries.add(currentSummary);
	}
	
	public void finishSummary(long duration, int status)
	{
		currentSummary.finishSummary(duration, status);
		currentSummary = null;
	}
	
	
	public List<TestResultsSummary> getTestSummaries()
	{
		return testResultsSummaries;
	}
	
	public void outputSummary()
	{
		
	}
	
	public String statusToString(int status)
	{
		String str = "";
		
		switch (status)
		{
			case TEST_STATUS_RUNNING:
				str = "running";
				break;
				
			case TEST_STATUS_ABORTED:
				str = "aborted";
				break;
				
			case TEST_STATUS_FINISHED:
				str = "finished";
				break;
		}
		
		return str;
	}
	
	class TestResultsSummary
	{		
		private TestConfiguration testConfiguration;
		private int repetition;
		private long duration = -1;
		private int status = TEST_STATUS_RUNNING;
		
		
		
		public TestResultsSummary(TestConfiguration testConfiguration, int repetition)
		{
			this.testConfiguration = testConfiguration;
			this.repetition = repetition;
		}
		
		public void finishSummary(long duration, int status)
		{
			this.duration = duration;
			this.status = status;
		}
		
		public String getTestLabel()
		{
			return testConfiguration.getLabel();
		}

		public int getRepetition()
		{
			return repetition;
		}

		public long getDuration()
		{
			return duration;
		}

		public int getStatus()
		{
			return status;
		}
		
		public String getStatusString()
		{
			return statusToString(status);
		}
	}
}
