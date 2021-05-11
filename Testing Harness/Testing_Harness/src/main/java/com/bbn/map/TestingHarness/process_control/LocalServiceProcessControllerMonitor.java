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
package com.bbn.map.TestingHarness.process_control;

import java.io.IOException;
import java.io.InputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


//controls a process and a resource monitor for the process on the local machine

public class LocalServiceProcessControllerMonitor extends LocalProcessController implements ProcessControllerWithMonitor
{
	private static Logger log = LogManager.getRootLogger();
	
	private Process monitorProcess;
	private String[] dataCollectionCommandFormat;
	private ResmonOutConsumptionThread monitorOutConsumer;
	
	private String processOutFilePath;
	private String nic;
	private String nicOutFilePath;
	
	
	
	public LocalServiceProcessControllerMonitor(String label, String[] dataCollectionCommandFormat)
	{
		super(label);
		this.dataCollectionCommandFormat = dataCollectionCommandFormat;
	}
	

	@Override
	public void setMonitorParameters(String processOutFilePath, String nic, String nicOutFilePath)
	{
		this.processOutFilePath = processOutFilePath;
		this.nic = nic;
		this.nicOutFilePath = nicOutFilePath;		
	}


	@Override
	public boolean startProcessMonitor()
	{
		ProcessBuilder pb = new ProcessBuilder();
		
		try
		{
			pb.command(createDataCollectionCommand(getPID(), processOutFilePath, nic, nicOutFilePath));
			
//			File redirectionFile = new File("resmon_out");
//log.debug("redirect io:" + redirectionFile.createNewFile());
//			pb.redirectOutput(redirectionFile);
//			pb.redirectError(redirectionFile);
			
			
			try
			{
				monitorProcess = pb.start();
				
				if (monitorProcess == null)
					return false;
				
				monitorOutConsumer = new ResmonOutConsumptionThread();
				monitorOutConsumer.start();
				
				log.info("Started data collection for service process.");	
			} catch (IOException e)
			{
				e.printStackTrace();
				return false;
			}
		} catch (Exception e)
		{
			return false;
		}
		
		return true;
	}

	@Override
	public void stopProcessMonitor()
	{
		if (monitorProcess != null)
		{
			if (monitorOutConsumer != null)
				monitorOutConsumer.end();
			
//			try
//			{
//				ProcessBuilder pb = new ProcessBuilder();
//				String[] command = {"kill", "-SIGINT", Long.toString(getMonitorPID())};
//				
//log.debug("Execute kill command: " + command);
//				pb.command(command);
//				pb.inheritIO();
//				pb.start();			// kill resmon so that it closes the monitor properly
//			} catch (Exception e)
//			{
				monitorProcess.destroy();
//				e.printStackTrace();
//			}
		}
	}
	
	private long getMonitorPID() throws Exception
	{
		return getPID(monitorProcess);
	}
	
	@Override
	public boolean waitForMonitorStop()
	{
		if (monitorProcess != null)
			try
			{
				monitorProcess.waitFor();
			} catch (InterruptedException e)
			{
				e.printStackTrace();
				return false;
			}
		
		return true;
	}
	
	@Override
	public boolean isMonitorAlive()
	{
		if (monitorProcess == null)
			return false;
		
		return monitorProcess.isAlive();
	}

	@Override
	public MonitorResults getMonitorResults()
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	
	public void setProcessOutFilePath(String processOutFilePath)
	{
		this.processOutFilePath = processOutFilePath;
	}

	public void setNic(String nic)
	{
		this.nic = nic;
	}

	public void setNicOutFilePath(String nicOutFilePath)
	{
		this.nicOutFilePath = nicOutFilePath;
	}
	
	// creates a command for starting a data collection process that will collect data on the process with the given PID
	private String[] createDataCollectionCommand(long pid, String processOutFilePath, String nic, String nicOutFilePath)
	{
		String[] command = new String[dataCollectionCommandFormat.length];		// create a new command for substituting the correct parameter values in
		
		// replace each parameter value identifier in the command with the appropriate value
		// and leave everything else the same
		for (int n = 0; n < command.length; n++)
		{
			switch (dataCollectionCommandFormat[n])
			{
				case "[pid]":
					command[n] = Long.toString(pid);
					break;
					
				case "[ps_outfile]":
					command[n] = processOutFilePath;
					break;
					
				case "[nic]":
					command[n] = nic;
					break;
					
				case "[nic_outfile]":
					command[n] = nicOutFilePath;
					break;
				
				// leave element n unchanged from the command template
				default:
					command[n] = dataCollectionCommandFormat[n];
					break;
			}
		}
		
		return command;
	}
	
	
	class ResmonOutConsumptionThread extends Thread
	{
		private InputStream out;
		private volatile boolean running = true;
		
		public ResmonOutConsumptionThread()
		{
			out = monitorProcess.getInputStream();
		}
		
		@Override
		public void run()
		{
			log.debug("Start monitor output consumer.");
			
			byte[] data = new byte[1024];
			
			try
			{
				while (running)
					out.read(data);
			} catch (IOException e)
			{
				running = false;
			}
			
			log.debug("End monitor output consumer.");
		}
		
		public void end()
		{
			running = false;
		}
	}
}
