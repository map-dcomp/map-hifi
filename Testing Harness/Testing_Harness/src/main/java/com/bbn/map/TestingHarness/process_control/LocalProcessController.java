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
package com.bbn.map.TestingHarness.process_control;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.Semaphore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


// controls a process on the local machine

public class LocalProcessController implements ProcessController
{
	private static Logger log = LogManager.getRootLogger();
	
	private String processControllerLabel = "";
	
	private String[] command;
	private Process process;
	private File outputRedirectionFile = null;
	
	private ProcessTerminationDetector ptd;						// detects if process terminates
	private volatile boolean shouldBeAlive = false;				// true if the process is expected to be alive and false if the process is expected to be dead
	private UnexpectedTerminationHandler uth = null;			// notified if process terminates unexpectedly
	
	private Semaphore waitForProcessSemaphor = new Semaphore(0);
	
	public LocalProcessController(String label)
	{
		processControllerLabel = label;
	}
	

	@Override
	public void setProcessCommand(String[] command)
	{
		this.command = new String[command.length];
		
		for (int n = 0; n < command.length; n++)
			this.command[n] = command[n];
	}

	@Override
	public boolean startProcess()
	{
		ProcessBuilder pb = new ProcessBuilder();
		
		if (outputRedirectionFile != null)
		{
			if (!outputRedirectionFile.exists())
				try
				{			
					outputRedirectionFile.createNewFile();
				} catch (IOException e)
				{
					log.error("Unable to create output redirection file: " + outputRedirectionFile.getAbsolutePath());
				}
			
			if (outputRedirectionFile.exists())
			{
				pb.redirectOutput(outputRedirectionFile);
				pb.redirectError(outputRedirectionFile);
			}
		}
		
		pb.command(command);
		
		try
		{
			process = pb.start();
			if (process == null)
				return false;
			
			ptd = new ProcessTerminationDetector();
			ptd.start();			
						
		} catch (IOException e)
		{
			e.printStackTrace();
			return false;
		}
		
		shouldBeAlive = true;
		waitForProcessSemaphor.release(); 			// allows waiting for process to terminate to begin
		
		return true;
	}

	@Override
	public boolean waitForProcess()
	{		
		try
		{
			waitForProcessSemaphor.acquire();		// acquire permit to wait for process to terminate and cause any other wait to block
			waitForProcessSemaphor.release();       // allow an additional wait for process to continue
			
			if (process != null)
				process.waitFor();
		} catch (InterruptedException e)
		{
//			e.printStackTrace();
			return false;
		}
		
		return true;
	}

	@Override
	public void destroyProcess()
	{
		shouldBeAlive = false;
		
		if (process != null)
			process.destroy();
		
		waitForProcessSemaphor.release(); 			// allow waitForProcess to unblock
	}
	
	@Override
	public boolean isProcessAlive()
	{
		if (process == null)
			return false;
		
		return process.isAlive();
	}

	@Override
	public void setUnexpectedTerminationHandler(UnexpectedTerminationHandler handler)
	{
		uth = handler;		
	}
	
	public boolean getShouldBeAlive()
	{
		return shouldBeAlive;
	}
	
	
	// obtains the PID of process
	public long getPID() throws Exception
	{
		return getPID(process);
	}
	
	// TODO: Update with a platform independent approach. Java 9 may offer this.
	// obtains the PID of the given process
	// This approach only works on some systems
	public static long getPID(Process process)
		throws Exception
	{
		Field pidField = process.getClass().getDeclaredField("pid");
		pidField.setAccessible(true);
		
		long pid = pidField.getLong(process);
		return pid;
	}
	
	
	@Override
	public String getProcessControllerLabel()
	{
		return processControllerLabel;
	}
	


	@Override
	public void setProcessOutputRedirectionFile(File file)
	{
		outputRedirectionFile = file;		
	}
	
	class ProcessTerminationDetector extends Thread
	{		
		@Override
		public void run()
		{
			while (!waitForProcess());
			
			if (shouldBeAlive)			// if the process died but should still be alive
				if (uth != null)
					synchronized (uth)
					{
						uth.handleUnexpectedTermination(LocalProcessController.this);		// notify UnexpectedTerminationHandler of the unexpected termination
					}
		}
	}
}
