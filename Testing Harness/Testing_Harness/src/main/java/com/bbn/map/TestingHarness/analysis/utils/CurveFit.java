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
import java.util.List;

import org.apache.commons.math3.analysis.ParametricUnivariateFunction;
import org.apache.commons.math3.fitting.SimpleCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;

import com.bbn.map.TestingHarness.data.DataTable;

public class CurveFit
{
	public static double[] exponentialDecayFit(DataTable table, String xColumnLabel, String yColumnLabel)
	{
		return exponentialDecayFit(getPointsFromTable(table, xColumnLabel, yColumnLabel));
	}
	
	private static List<WeightedObservedPoint> getPointsFromTable(DataTable table, String xColumnLabel, String yColumnLabel)
	{
		List<WeightedObservedPoint> points = new ArrayList<>();
		
		for (DataTable.Sample s : table.getSamples())
		{
			points.add(new WeightedObservedPoint(1.0, Double.parseDouble(s.getValue(xColumnLabel)),
					Double.parseDouble(s.getValue(yColumnLabel))));
		}
		
		return points;
	}
	

	public static double[] exponentialDecayFit(List<WeightedObservedPoint> data)
	{
		double[] initialValues = {1.0, 1.0, 0.0};
		SimpleCurveFitter curveFitter = SimpleCurveFitter.create(new CurveFit.ExponentialDecay(), initialValues);
		
		return curveFitter.fit(data);		
	}
	
	
	
	static class ExponentialDecay implements ParametricUnivariateFunction
	{

		@Override
		public double value(double x, double... p)
		{
			return (p[0] * (1 - Math.exp(-p[1] * x)) + p[2]);
		}

		@Override
		public double[] gradient(double x, double... p)
		{
			double[] gradient = new double[p.length];
			
			gradient[0] = 1 - Math.exp(-p[1] * x);
			gradient[1] = p[0] * p[1] * Math.exp(-p[1] * x);
			gradient[2] = 1;
			
			return gradient;
		}
		
		@Override
		public String toString()
		{
			return "p[0] * (1 - exp(-p[1] * x)) + p[2]";
		}
		
	}
}
