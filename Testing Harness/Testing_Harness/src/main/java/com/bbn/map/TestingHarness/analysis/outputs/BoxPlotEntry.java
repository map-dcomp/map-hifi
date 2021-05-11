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
/* Copyright (c) <2017>, <Raytheon BBN Technologies>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.bbn.map.TestingHarness.analysis.outputs;

import java.util.Map;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Min/Max box plot data
 *
 */
final public class BoxPlotEntry {
	String name = null;
	double min; 
	double max;
	double r25;
	double r50;
	double r75;
	
	public BoxPlotEntry(String name, double min, double r25, double r50, double r75, double max) {
		this.name = name;
		this.min = min;
		this.max = max;
		this.r25 = r25;
		this.r50 = r50;
		this.r75 = r75;
	}
	
	public static BoxPlotEntry getBoxPlot(Map<String, DescriptiveStatistics> dataSet, String keyColumn, String boxPlotName) throws Exception {
		DescriptiveStatistics ds = dataSet.get(keyColumn);
		
		if (ds != null) {
			return new BoxPlotEntry(boxPlotName, ds.getMin(), ds.getPercentile(25.0D), ds.getPercentile(50.0D), ds.getPercentile(75.0D), ds.getMax());
		}

		return null;
	}
	
	public String toRow() {
		StringBuffer sb = new StringBuffer ("");
		sb.append(getMin() + "\t");
		sb.append(get25() + "\t");
		sb.append(get50() + "\t");
		sb.append(get75() + "\t");
		sb.append(getMax());
		return sb.toString();
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer ("");
		sb.append(name + System.lineSeparator());
		sb.append("min\tfirst\tmedian\tthrid\tmax" + System.lineSeparator());
		sb.append(getMin() + "\t");
		sb.append(get25() + "\t");
		sb.append(get50() + "\t");
		sb.append(get75() + "\t");
		sb.append(getMax() + System.lineSeparator());
		return sb.toString();
	}

	public double getMin() {
		return min;
	}
	
	public double getMax() {
		return max;
	}
	
	public double get25() {
		return r25;
	}
	
	public double get50() {
		return r50;
	}
	
	public double get75() {
		return r75;
	}
	
	public String getName() {
		return name;
	}
}
