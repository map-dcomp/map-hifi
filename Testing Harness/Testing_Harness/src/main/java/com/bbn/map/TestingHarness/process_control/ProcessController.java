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


// interface for a class that starts and stops a process for tests

public interface ProcessController
{
	// sets the command to be used to start a process
	public void setProcessCommand(String[] command);
	
	// starts a process, which may be located on a remote system
	// returns true if the process was started successfully and false otherwise
	public boolean startProcess();
	
	// waits for the process to finish executing
	// returns true if waiting for the process to terminate was successful and false if the waiting was interrupted
	public boolean waitForProcess();
	
	// destroys the process
	public void destroyProcess();
	
	// checks if the process is alive
	public boolean isProcessAlive();
	
	// returns the label used to identify this ProcessController
	public String getProcessControllerLabel();

	// sets the UnexpectedTerminationHandler, which is called when the process of this ProcessController terminates unexpectedly
	public void setUnexpectedTerminationHandler(UnexpectedTerminationHandler handler);
	
	// sets the file to redirect the output of this process to
	public void setProcessOutputRedirectionFile(File file);
}
