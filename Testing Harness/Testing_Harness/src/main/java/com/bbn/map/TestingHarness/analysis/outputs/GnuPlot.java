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

import java.io.File;
import java.util.Vector;

import com.bbn.map.TestingHarness.analysis.utils.IOUtils;

/**
 * Methods to generate plots and data input files for gnuplot
 *
 */
final public class GnuPlot {
	
	/**
	 * name for a plot file.
	 */
	public static final String GNU_PLOT_EXTENSION = ".gnu";
	
	/**
	 * name for a data file.
	 */
	public static final String GNU_PLOT_DATA_EXTENSION = ".dat";
	
	/**
	 * Generate a box plot and data file.
	 * 
	 * @param plotTitle
	 * @param plotYAxis
	 * @param data
	 * @param filePlot
	 * @param fileData
	 * @throws Exception
	 */
	public static void plotBoxPlot(String plotTitle, String plotYAxis, Vector<BoxPlotEntry> data, File filePlot, File fileData) throws Exception {
		if (data == null || data.size() == 0) {
			throw new Exception ("data is incomplete");
		}
		
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;		

		for (BoxPlotEntry in : data) {			
			if (in.getMin() < min) {
				min = (int) in.getMin();
			}
			
			if (in.getMax() > max) {
				max = (int) in.getMax();
			}
		}
		
		if (min >= max || min == Integer.MAX_VALUE || max == Integer.MIN_VALUE) {
			throw new Exception("Could not identify min or max range for dataset" + min + " " + max);
		}

		try {
			int ct = writeBoxPlotData(fileData, data);
			
			if (ct <= 0 || !writePlot(filePlot, ct, min, max, plotTitle, plotYAxis, fileData.getAbsolutePath())) {
				deleteFile(filePlot);
				deleteFile(fileData);
				throw new Exception ("Failed to write plot or data file");
			}
		} catch (Exception ex) {
			deleteFile(filePlot);
			deleteFile(fileData);
			throw ex;
		}
	}

	private static void deleteFile(File file) {
		if (file != null && file.exists()) {
			file.delete();
		}
	}

	private static boolean writePlot(File plotFile, int xCount, int minY, int maxY, 
			String plotTitle, String plotYAxis, String dataFileName) {
		String name = plotFile.getName().replaceAll("\\.gnu", "\\.png");
		if (plotTitle == null || plotTitle.length() == 0) {
			plotTitle = "Plot Title";
		}
		plotYAxis = plotYAxis.trim();
		if (plotYAxis == null || plotYAxis.length() == 0) {
			plotYAxis = "Plot Axis";
		}
		
		StringBuffer sb = new StringBuffer("");
		sb.append("set terminal png" + System.lineSeparator());
		sb.append("set output \"" + name + "\""+ System.lineSeparator());
		sb.append("set title \"" + plotTitle + "\""+ System.lineSeparator());
		sb.append("set ylabel \"" + plotYAxis + "\"" + System.lineSeparator());
		sb.append("set xlabel \"test\"" + System.lineSeparator());
		sb.append("set boxwidth .3"+ System.lineSeparator());
		sb.append("set grid y"+ System.lineSeparator());
		sb.append("set xrange[-1:" + xCount + "]"+ System.lineSeparator());
		sb.append("set yrange[" + (minY-10) + ":" + (maxY+10) + "]"+ System.lineSeparator());
		sb.append("set xtics noenhanced nomirror rotate by -45 offset -.1,-.1 font \",12\""+ System.lineSeparator());
		sb.append("plot '" + dataFileName + "' using 1:3:2:6:5:xticlabels(7) with candlesticks lt 3 lw 3 title 'Quartiles' whiskerbars, '' using 1:4:4:4:4 with candlesticks lt -1 lw 3 title 'Median'"+ System.lineSeparator());
		
		try {
			return IOUtils.zeroAndWriteFile(plotFile, sb);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Write out a box plot file
	 * 
	 * @param dataFile
	 * @param data
	 * @return
	 */
	public static int writeBoxPlotData(File dataFile, Vector<BoxPlotEntry> data) {
		StringBuffer sb = new StringBuffer("");
		
		int ct = 0;
		for (BoxPlotEntry in : data) {
			sb.append(ct++ + "\t" + in.toRow() + "\t" + in.getName() + System.lineSeparator());
		}
		
		if (ct == 0) { 
			return -1;
		}
		
		try {
			if (!IOUtils.zeroAndWriteFile(dataFile, sb)) {
				return -1;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return ct;
	}
	
	/**
	 * write a bash script to generate and open a set of gnu plots
	 * 
	 * @param fileBashFile
	 * @param gnuPlotList
	 */
	public static void writeBashHelperFile(File fileBashFile, String [] gnuPlotList) {
		StringBuffer sb = new StringBuffer("#!/bin/bash" + System.lineSeparator());
		
		sb.append(System.lineSeparator());
		sb.append("#Do you have bash, gnuplot and some open file routine?" + System.lineSeparator());
		sb.append("#chmod 755 " + fileBashFile.getName() + " && ./" + fileBashFile.getName() + System.lineSeparator());
		
		sb.append(System.lineSeparator());
		sb.append("#Choose your OS open utility:" + System.lineSeparator());
		sb.append("pngopen=xdg-open" + System.lineSeparator());
		sb.append("pngopen=open" + System.lineSeparator());
		
		sb.append(System.lineSeparator());
		for (String plot : gnuPlotList) {
			sb.append("gnuplot ." + File.separator + plot.trim() + GnuPlot.GNU_PLOT_EXTENSION + System.lineSeparator());
		}
		
		sb.append(System.lineSeparator());
		sb.append("${pngopen} *.png" + System.lineSeparator());
		
		sb.append(System.lineSeparator());
		sb.append("exit 0" + System.lineSeparator());
		
		try {
			IOUtils.zeroAndWriteFile(fileBashFile, sb);
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
	
}