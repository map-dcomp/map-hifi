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


// Provides a way to store the configuration of an experiment, which consists of a series of test configurations

public class ExperimentConfiguration
{
	private String[] parameterLabels;
	private List<TestConfiguration> tests = new ArrayList<>();
	
	
	public ExperimentConfiguration(String... parameterLabels)
	{
		this.parameterLabels = new String[parameterLabels.length];
		for (int pl = 0; pl < parameterLabels.length; pl++)
			this.parameterLabels[pl] = parameterLabels[pl];
	}

	public void addTestConfiguration(String label, String... values)
	{
		tests.add(new TestConfiguration(label, values));
	}
	
	public String[] getParameterLabels()
	{
		return parameterLabels;
	}
	
	public int getNumberOfValues()
	{
		return parameterLabels.length;
	}
	
	public TestConfiguration getTestConfiguration(int testIndex)
	{
		return tests.get(testIndex);
	}
	
	public int getNumberOfTestConfigurations()
	{
		return tests.size();
	}
	
	// returns the parameter value that corresponds with the specified parameter label for the test configuration with the given index
	public String getTestConfigurationValue(int testIndex, String parameterLabel)
	{
		return tests.get(testIndex).getValue(parameterLabel);
	}
	
	// returns the label for the test configuration with the given index
	public String getTestConfigurationLabel(int testIndex)
	{
		return tests.get(testIndex).getLabel();
	}
	
	
	class TestConfiguration
	{
		private String label;
		private String[] parameterValues = new String[parameterLabels.length];
		
		public TestConfiguration(String label, String... values)
		{
			setValues(label, values);
		}
		
		public void setValues(String label, String[] values)
		{	
			this.label = label;
			
			if (values.length != parameterValues.length)
				throw new IllegalArgumentException("There are " + parameterLabels.length + " parameter labels but " + values.length + " values were given.");
			
			for (int v = 0; v < values.length && v < parameterValues.length; v++)
				parameterValues[v] = values[v];
		}
		
		// search for the specified label and obtain its value if found
		// if the parameter label is not found, throw an IllegalArgumentException
		public String getValue(String parameterLabel)
		{	
			for (int i = 0; i < parameterLabels.length; i++)
			{
				if (parameterLabels[i].equals(parameterLabel))
					return parameterValues[i];
			}
			
			throw new IllegalArgumentException("Could not find value for label '" + parameterLabel.toString() + "' in test '" + label + "'.");
		}
		
		// returns the label for this test configuration
		public String getLabel()
		{
			return label;
		}
		
		@Override
		public String toString()
		{
			StringBuilder b = new StringBuilder();
			b.append(label + ": ");
			
			for (int n = 0; n < parameterValues.length; n++)
			{
				if (n > 0)
					b.append(", ");
				
				b.append(parameterValues[n]);
			}
			
			return b.toString();
		}
	}	
}
