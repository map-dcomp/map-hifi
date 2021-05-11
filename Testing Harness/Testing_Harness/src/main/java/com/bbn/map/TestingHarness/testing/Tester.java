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
import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.TestingHarness.process_control.LocalProcessController;
import com.bbn.map.TestingHarness.process_control.LocalServiceProcessControllerMonitor;
import com.bbn.map.TestingHarness.process_control.ProcessController;
import com.bbn.map.TestingHarness.process_control.ProcessControllerWithMonitor;
import com.bbn.map.TestingHarness.process_control.UnexpectedTerminationHandler;
import com.bbn.map.TestingHarness.testing.ExperimentConfiguration.TestConfiguration;
import com.bbn.map.TestingHarness.testing.ExperimentResults.TestResultsSummary;



// The Tester class performs the operations necessary to start a service, start clients, and collect data on the service while it is under load created by the clients.

public class Tester
{
	private static Logger log = LogManager.getRootLogger();
	
	private File configFile = new File("tester.config");
	private ConfigurationLoader configurationLoader;
	
	// stores the configuration describing the parameters of each test, which are loaded from the experiment csv file
	private CSVExperimentConfiguration experimentConfiguration;
	private CSVExperimentResults experimentResults;

	
	// TODO: implement a way to define these labels in the configuration file
	// the labels identifying each test parameter
//	private String[] testParameterLabels = {"clients", "images", "images_per_second"};
	private String[] testParameterLabels = {"clients", "client_start_interval", "images", "images_per_second"};
	
	private int delayBetweenTests = 5000;				// time delay between the end of a test repetition and the beginning of the next test repetition
	private int delayBeforeStartingClients = 5000;		// time delay between the service process being started and the client processes being started
//	private int delayBeforeEndingTest = 0;				// the amount of time to wait after all clients have finished executing before terminating service processes and ending the test
	
	private int defaultRepetitionsPerTest = 1;
	private int repetitionsPerExperiment = 1;
	
	// the formats of commands for starting a server, starting a data collection resource monitor, and starting a client
	private String[] serviceCommand = {};
	private String[] dataCollectionCommand = {};
	private String[] clientCommand = {};
	
	
	// current experiment
	private long experimentStartTime;
	private File experimentFolder;
	
	// current test state
	private int currentTestIndex = 0;
	private int currentTestRepetition = 0;
	private long currentTestRepetitionStartTime;
	private volatile boolean currentTestAborted;	// true if the current test has been aborted ad false otherwise
	
	private File testDataFolder = null;				// folder where test results will be stored
	
	
	
	// the  controllers for monitored service processes
	private List<ProcessControllerWithMonitor> serviceProcessControllers = new ArrayList<>();
	
	// the controllers for client processes
	private List<ProcessController> clientProcessControllers = new ArrayList<>();
	private Timer clientStartTimer;
	

	public static void main(String[] args)
	{		
		Tester tester = new Tester();
		tester.runExperiments();
	}
	
	
	public Tester()
	{
		Runtime.getRuntime().addShutdownHook(new TesterShutdownHook());
		
		try
		{
			configurationLoader = new ConfigurationLoader(configFile);
			
			serviceCommand = configurationLoader.getServiceCommand();
			dataCollectionCommand = configurationLoader.getDataCollectionCommand();
			clientCommand = configurationLoader.getClientCommand();
			
			experimentConfiguration = new CSVExperimentConfiguration(configurationLoader.getExperimentFile(), testParameterLabels);
			experimentResults = new CSVExperimentResults();
			
			defaultRepetitionsPerTest = configurationLoader.getDefaultRepetitionsPerTest();
			
			outputInitializationInfo();
		} catch (IOException e)
		{
			System.exit(1);
		}
	}
	
	// outputs information about how the tester was initialized
	private void outputInitializationInfo()
	{
		log.info("Initialized Testing Harness");
		log.info("Experiment file: " + experimentConfiguration.getExperimentFile());
		log.info("Default repetitions per test: " + defaultRepetitionsPerTest + "\n\n\n\n");
	}
	
	// runs all of the tests for the experiment multiple times
	private void runExperiments()
	{		
		for (int n = 0; n < repetitionsPerExperiment; n++)
			runExperiment();
	}
	
	// runs all of the tests for the experiment
	private void runExperiment()
	{
		experimentStartTime = System.currentTimeMillis();
		experimentFolder = getExperimentFolder();
		
		log.info("Starting experiment at time " + new Date(experimentStartTime).toString() + " with data folder " + experimentFolder + ".\n\n\n");
		experimentResults.setSummaryOutputFile(new File(experimentFolder + File.separator + "summary_" + experimentFolder.getName() + ".csv"));
		
		for (setTest(0); getCurrentTestIndex() < experimentConfiguration.getNumberOfTestConfigurations(); nextTest())
		{			
			while (getCurrentTestRepetition() < defaultRepetitionsPerTest)
			{
				runTest();
				
				try
				{
					Thread.sleep(delayBetweenTests);
				} catch (InterruptedException e)
				{
					log.error("Unable to sleep between tests.");
				}

				try
				{
					ProcessBuilder pb = new ProcessBuilder();
					pb.command("ps");
					pb.inheritIO();
					pb.start();
				} catch (IOException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				try
				{
					Thread.sleep(1000);
				} catch (InterruptedException e)
				{
					log.error("Unable to sleep after ps.");
				}
				
				nextTestRepetition();
			}
		}
	}
	
	
	
	// runs the test defined in the ExperimentConfiguration with the given index
	// returns false if the test could not be performed successfully
	private boolean runTest()
	{
		if (currentTestAborted)
		{
			log.debug("The current test was aborted before being run.");
			return false;
		}
		
		clientStartTimer = new Timer();
		
		int testIndex = getCurrentTestIndex();
		
		currentTestRepetitionStartTime = System.currentTimeMillis();
		
//		if (currentTestRepetition == 0)
//			currentTestStartTime = currentTestRepetitionStartTime;
		
		
		TestConfiguration testConfiguration = experimentConfiguration.getTestConfiguration(testIndex);
		log.info("Starting test " + testIndex + "  --  " + testConfiguration + " -- repetition " + currentTestRepetition + ".");
		experimentResults.startSummary(testConfiguration, currentTestRepetition);
		
		createTestRepetitionFolder();

		startService();
		
		try
		{
			Thread.sleep(1000);
		} catch (InterruptedException e1)
		{
			log.error("Unable to sleep between starting service and starting data collection.");
		}
		
		startDataCollection();

		try
		{
			Thread.sleep(delayBeforeStartingClients);
		} catch (InterruptedException e1)
		{
			log.error("Unable to sleep before starting clients.");
		}
		
		if (!currentTestAborted)
		{
//			startClients();
			initializeClients();
		
			// wait for clients to finish executing
			log.info("Waiting for client processes to end...");
			for (ProcessController p : clientProcessControllers)
				if (!p.waitForProcess())
					log.info("Waiting for client process to end was interrupted.");

			terminateMonitors();
			terminateService();	
		}
		
//		terminateMonitors();
//		terminateService();		

		long testRepetitionDuration = getCurrentTestRepetitionTimeMS();
		log.info("Finished test " + testIndex + "  --  " + experimentConfiguration.getTestConfiguration(testIndex) + " -- repetition " + currentTestRepetition + ".");
		log.info("Test repetition took " + (testRepetitionDuration / 1000.0F) + " seconds to run.\n\n\n");
		
		clientProcessControllers.clear();
		serviceProcessControllers.clear();
		
		// rename test repetition folder to have "finished" suffix
		if (!currentTestAborted)
			renameCurrentRepetitionFolder("finished");
		
		experimentResults.finishSummary(testRepetitionDuration, (currentTestAborted ? ExperimentResults.TEST_STATUS_ABORTED : ExperimentResults.TEST_STATUS_FINISHED));
		clientStartTimer.cancel();
		
		return true;
	}
	
	private long getCurrentTestRepetitionTimeMS()
	{
		return (System.currentTimeMillis() - currentTestRepetitionStartTime);
	}
	private float getCurrentTestRepetitionTimeSec()
	{
		return (getCurrentTestRepetitionTimeMS() / 1000.0F);
	}
	
	// starts the processes that make up the service
	private void startService()
	{	
		int serversStarted = 0;
		
		ProcessControllerWithMonitor pc = new LocalServiceProcessControllerMonitor("Service controller", dataCollectionCommand);
		
//		String[] command = serviceCommand;		
		
//		pc.setProcessCommand(command);
		pc.setProcessCommand(createServiceProcessCommand(serviceCommand));
		pc.setProcessOutputRedirectionFile(new File(getCurrentTestRepetitionProcessOutputFolder() + File.separator + "server_output.txt"));

		pc.setUnexpectedTerminationHandler(serviceUnexpectedTerminationHandler);
		
		if (pc.startProcess())
		{
			serversStarted++;
			serviceProcessControllers.add(pc);
		}
		else
		{
			log.error("Unable to start process for service.");
		}
		
		log.info("Started " + serversStarted + " service processes.");
	}
	
	public String[] createServiceProcessCommand(String[] commandFormat)
	{
		String[] command = new String[commandFormat.length];		// create a new command for substituting the correct parameter values in
		
		// replace each parameter value identifier in the command with the appropriate value
		// and leave everything else the same
		for (int n = 0; n < command.length; n++)
		{
			// if a parameter specified in the server command configuration format is of the form {*}
			// then attempt to substitute a parameter value for the current test as specified in the experiment configuration
			if (commandFormat[n].matches("\\{.*\\}"))
			{				
				String parameterLabel = commandFormat[n].substring(1, commandFormat[n].length() - 1);
				String parameterValue = experimentConfiguration.getTestConfigurationValue(getCurrentTestIndex(), parameterLabel);
				command[n] = parameterValue;
			}
			else
			{
				switch (commandFormat[n])
				{				
					case "[application_data_file]":
						command[n] = getCurrentTestRepetitionDataFolder().getAbsolutePath() + File.separator + "server_application_data.csv";
						break;	
				
					// leave element n unchanged from the command template
					default:
						command[n] = commandFormat[n];
						break;
				}
			}
		}
		
		return command;
	}
	
	// starts the data collection processes, which will collect data on the server processes
	private boolean startDataCollection()
	{
		// prepare monitor processes to be started
		for (ProcessControllerWithMonitor pc : serviceProcessControllers)
		{
			String processOutFilePath = testDataFolder + File.separator + "server_resource_stats.csv";
			String nicOutFilePath = testDataFolder + File.separator + "nic_stats.{nic}.csv";
			
			
			pc.setMonitorParameters(processOutFilePath, "lo", nicOutFilePath);
		}
		
		// start monitor processes
		for (ProcessControllerWithMonitor pc : serviceProcessControllers)
		{
			if (!pc.startProcessMonitor())		// attempt to start the process monitor and check if it succeeds
			{	
				log.error("Unable to start monitor process.");
				return false;
			}
		}
		
		return true;
	}
	
	
	private void initializeClients()
	{
		int clients = Integer.parseInt(experimentConfiguration.getTestConfigurationValue(getCurrentTestIndex(), "clients"));
		long clientStartInterval = Integer.parseInt(experimentConfiguration.getTestConfigurationValue(getCurrentTestIndex(), "client_start_interval"));
		
		for (int n = 0; n < clients; n++)
		{
			ProcessController pc = new LocalProcessController("Client " + n + " controller");
			pc.setProcessCommand(createClientProcessCommand(n, clientCommand));
			pc.setProcessOutputRedirectionFile(new File(getCurrentTestRepetitionProcessOutputFolder() + File.separator + "client_" + n + "_output.txt"));
			pc.setUnexpectedTerminationHandler(clientUnexpectedTerminationHandler);
			clientProcessControllers.add(pc);
		}
		
		if (clientStartInterval > 0)
		{		
			clientStartTimer.schedule(new TimerTask()
			{
				int startClientIndex = 0;
				
				@Override
				public void run()
				{
					if (startClientIndex < clientProcessControllers.size())
					{
						clientProcessControllers.get(startClientIndex).startProcess();
						log.info("Started client " + startClientIndex + " at time " + getCurrentTestRepetitionTimeSec() + " seconds.");
						startClientIndex++;
					}
					
					if (startClientIndex >= clientProcessControllers.size())
						cancel();
				}
			}, 0, clientStartInterval);
		}
		else
		{
			int startClientIndex = 0;
			
			while (startClientIndex < clientProcessControllers.size())
			{
				clientProcessControllers.get(startClientIndex).startProcess();
				log.info("Started client " + startClientIndex + " at time " + getCurrentTestRepetitionTimeSec() + " seconds.");
				startClientIndex++;
			}
		}
	}
	
//	// starts the clients that will communicate with the service
//	private void startClients()
//	{		
//		int clients = Integer.parseInt(experimentConfiguration.getTestConfigurationValue(getCurrentTestIndex(), "clients"));
//		int clientsStarted = 0;
//		
//		for (int n = 0; n < clients; n++)
//		{
//			ProcessController pc = new LocalProcessController("Client " + n + " controller");
//			pc.setProcessCommand(createClientProcessCommand(n, clientCommand));
//			pc.setProcessOutputRedirectionFile(new File(getCurrentTestRepetitionProcessOutputFolder() + File.separator + "client_" + n + "_output.txt"));
//			pc.setUnexpectedTerminationHandler(clientUnexpectedTerminationHandler);
//			
//			if (pc.startProcess())
//			{
//				clientProcessControllers.add(pc);
//				clientsStarted++;
//			}
//			else
//			{
//				log.error("Unable to start client process with index " + n);
//			}
//		}
//		
//		log.info("Started " + clientsStarted + " out of " + clients + " clients.");
//	}
	
	// creates a command for starting a client process according to the given command format array
	private String[] createClientProcessCommand(int clientIndex, String[] commandFormat)
	{
		String[] command = new String[commandFormat.length];		// create a new command for substituting the correct parameter values in
		
		// replace each parameter value identifier in the command with the appropriate value
		// and leave everything else the same
		for (int n = 0; n < command.length; n++)
		{
			// if a parameter specified in the client command configuration format is of the form {*}
			// then attempt to substitute a parameter value for the current test as specified in the experiment configuration
			if (commandFormat[n].matches("\\{.*\\}"))
			{				
				String parameterLabel = commandFormat[n].substring(1, commandFormat[n].length() - 1);
				String parameterValue = experimentConfiguration.getTestConfigurationValue(getCurrentTestIndex(), parameterLabel);
				command[n] = parameterValue;

//				log.debug("replaced parameter " + commandFormat[n] + " with " + command[n]);
			}
			else
			{
				switch (commandFormat[n])
				{				
					case "[application_data_file]":
						command[n] = getCurrentTestRepetitionDataFolder().getAbsolutePath() + File.separator + "client_" + clientIndex + "_application_data.csv";
//log.debug("replaced parameter " + commandFormat[n] + " with " + command[n]);
						break;	
				
					// leave element n unchanged from the command template
					default:
						command[n] = commandFormat[n];
						break;
				}
			}
		}
		
		return command;
	}
	
	// terminates the client processes for the current test and waits for them to end
	private void terminateClients()
	{
		log.info("Terminating client processes...");
		
		// kill all server processes
		for (ProcessController p : clientProcessControllers)
			p.destroyProcess();
		
		// wait for server processes to end
		for (ProcessController p : clientProcessControllers)
			if (!p.waitForProcess())
				log.info("Waiting for client processes to end was interrupted.");
	}
	
	// terminates the service processes for the current test and waits for them to end
	private void terminateService()
	{
		log.info("Terminating service processes...");
		
		// kill all server processes
		for (ProcessController p : serviceProcessControllers)
			p.destroyProcess();
		
		// wait for server processes to end
		for (ProcessController p : serviceProcessControllers)
			if (!p.waitForProcess())
				log.info("Waiting for server processes to end was interrupted.");
	}
	
	// terminates the monitor processes for the current test and waits for them to end
	private void terminateMonitors()
	{
		// kill all monitor processes
		for (ProcessControllerWithMonitor p : serviceProcessControllers)
			p.stopProcessMonitor();
		
		// wait for monitor processes to end
		log.info("Waiting for monitor processes to end...");
		for (ProcessControllerWithMonitor p : serviceProcessControllers)
			if (!p.waitForMonitorStop())
				log.info("Waiting for monitor process to end was interrupted.");
	}
	
	
	// creates a folder for the current test repetition's files
	private void createTestRepetitionFolder()
	{
		testDataFolder = getCurrentTestRepetitionDataFolder();
		testDataFolder.mkdirs();
		
		if (!testDataFolder.exists())
			log.error("Unable to create folder " + testDataFolder.getAbsolutePath() + " for test with index " + getCurrentTestIndex() + " and repetition " + getCurrentTestRepetition() + ".");
	}
	
//	// creates a folder for the current test's files
//	private void createTestFolder()
//	{
//		testDataFolder = new File(getCurrentTestDataFolderPath());
//		
//		if (!testDataFolder.mkdirs())
//			log.error("Unable to create folder " + testDataFolder + " for test with index " + getCurrentTestIndex());
//	}
	
	// creates (if needed) and returns the folder where process output for the current test will be stored
	private File getCurrentTestRepetitionProcessOutputFolder()
	{
		File file = new File(getCurrentTestRepetitionDataFolder() + File.separator + "process_output");
		
		if (!file.exists())
			file.mkdirs();
		
		return file;
	}

	// creates (if needed) and returns a folder where the data for the current test and repetition should be stored
	private File getCurrentTestRepetitionDataFolder()
	{
		File file = new File(getCurrentTestDataFolderPath() + File.separator + getCurrentTestRepetitionDataFolderBaseName());
		
		if (!file.exists())
			file.mkdirs();
		
		return file;
	}
	
	// returns the base folder name of the current test repetition
	private String getCurrentTestRepetitionDataFolderBaseName()
	{
		return "rep_" + getCurrentTestRepetition();
	}
	
	// renames the current repetition folder to have the specified status suffix such as "started", "finished", "or "aborted"
	private boolean renameCurrentRepetitionFolder(String suffix)
	{
		File newFolder = new File(testDataFolder.getParent() + File.separator
				+ getCurrentTestRepetitionDataFolderBaseName() + (suffix != null ? "-" : "") + suffix);
		
log.debug("rename " + testDataFolder + " to " + newFolder);
		boolean result = testDataFolder.renameTo(newFolder);
		
		testDataFolder = newFolder;
		return result;		
	}
	

	
	// returns a path where the data for the current test should be stored
	private String getCurrentTestDataFolderPath()
	{
		String testLabel = experimentConfiguration.getTestConfigurationLabel(getCurrentTestIndex());
		return experimentFolder + File.separator + "test_" + testLabel + "-" + getCurrentTestIndex();
	}
	
	// creates and returns the folder path for the current experiment
	private File getExperimentFolder()
	{
		Date date = new Date(experimentStartTime);
		DateFormat formatter = new SimpleDateFormat("MMddyyyy_HHmmss_SSS");		
		
		String folderPath = "experiments" + File.separator + formatter.format(date);
		
		File folder = new File(folderPath);
		folder.mkdirs();
		
		return folder;
	}	
	
	
	// returns the index of the test currently being performed
	private int getCurrentTestIndex()
	{
		return currentTestIndex;
	}
	
	// increments the test index to prepare for the next test
	private void nextTest()
	{
		currentTestIndex++;
		currentTestRepetition = 0;
		currentTestAborted = false;
	}
	
	// sets the current test index to prepare for the test with the given index
	private void setTest(int index)
	{
		currentTestIndex = index;
		currentTestRepetition = 0;
		currentTestAborted = false;
	}
	
	// increments the test repetition to prepare for the next repetition of the current test
	private void nextTestRepetition()
	{
		currentTestRepetition++;
		currentTestAborted = false;
	}
	
	// returns the current repetition of the test currently being performed
	private int getCurrentTestRepetition()
	{
		return currentTestRepetition;
	}

	
	
	
	
	private UnexpectedTerminationHandler serviceUnexpectedTerminationHandler = new UnexpectedTerminationHandler()
	{
		@Override
		public synchronized void handleUnexpectedTermination(ProcessController pc)
		{
			log.fatal("A service process controlled by '" + pc.getProcessControllerLabel() + "' terminated unexpectedly.");
			abortCurrentTest();
		}
	};
	
	private UnexpectedTerminationHandler clientUnexpectedTerminationHandler = new UnexpectedTerminationHandler()
	{
		@Override
		public synchronized void handleUnexpectedTermination(ProcessController pc)
		{
			if (!currentTestAborted)
				log.info("The client process controlled by '" + pc.getProcessControllerLabel() + "' finished executing.");
		}
	};
	
	// immediately terminates the experiment and all of its tests
	private void abortExperiment()
	{
		abortCurrentTest();
		log.info("Experiment aborted.");
		System.exit(2);
	}
	
	private void abortCurrentTest()
	{
		currentTestAborted = true;
		log.info("Current test aborted.");
		terminateClients();
		
// TODO: Confirm that commenting this out prevents concurrent modification error as it is called in normally at the end of test
//		terminateService();
		
		// rename test repetition folder to have "aborted" suffix
		renameCurrentRepetitionFolder("aborted");
	}
	
	
	class TesterShutdownHook extends Thread
	{
		@Override
		public void run()
		{
			log.info("Running tester shutdown hook to kill any lingering processes.");
			
			int lingeringServerProcesses = 0;
			int lingeringMonitorProcesses = 0;
			int lingeringClientProcesses = 0;
			
			for (ProcessController p : clientProcessControllers)
			{
				if (p.isProcessAlive())
				{
					lingeringClientProcesses++;
					p.destroyProcess();					
				}
			}
			
			for (ProcessControllerWithMonitor p : serviceProcessControllers)
			{
				if (p.isProcessAlive())
				{
					lingeringServerProcesses++;
					p.destroyProcess();					
				}
				
				if (p.isMonitorAlive())
				{
					lingeringMonitorProcesses++;
					p.stopProcessMonitor();			
				}
			}

			log.info("Found " + lingeringServerProcesses + " lingering server processes.");
			log.info("Found " + lingeringMonitorProcesses + " lingering monitor processes.");
			log.info("Found " + lingeringClientProcesses + " lingering client processes.");
			
			// output a summary of the experiment before terminating
			if (experimentResults != null)
				experimentResults.outputSummary();
		}
	}
}
