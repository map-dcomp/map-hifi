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
package com.bbn.map.FaceRecognition.face_detection_server;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.FaceRecognition.Version;
import com.bbn.map.FaceRecognition.common.DataRecorder;
import com.bbn.map.FaceRecognition.common.ImageMessage;
import com.bbn.map.FaceRecognition.common.ImageProcessedAckMessage;
import com.bbn.map.hifi.util.ActiveConnectionCountWriter;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.utils.LogExceptionHandler;

//import nu.pattern.OpenCV;




// The FaceRecognitionServer accepts requested connections from clients, receives images over the network,
// and sorts each image into a folder that depends on whether the image contains at least one face.

public class FaceDetectionServer
{
	private static Logger log = LogManager.getLogger(FaceDetectionServer.class);
	public static final String OUTPUT_FILE_FORMAT = "jpg";		// the format of the image files to output
	private static final String[] EVENTS_CSV_HEADER = {"timestamp", "event", "client", "time_image_received", "time_image_processed", "latency"};
	
	private int port;
	private ImageAnalysisResultsOuput<Map<File, Integer>> imageAnalsisResultsOutput;
	private AtomicInteger numberOfClients = new AtomicInteger(0);		// the number of clients currently being serviced
	
	private boolean sendAcknowledgments = false;
	
	private File applicationSpecificDataFile = null;		// optionally specifies a csv file for recording application specific data to
	private DataRecorder dataRecorder = null;
	
	
	// classifier for classifying images
	private ImageMultiClassifier multiClassifier = new ImageMultiClassifier();
	
	

	public static void main(String[] args)
	{
        LogExceptionHandler.registerExceptionHandler();

        DnsUtils.configureDnsCache();

        log.info("Running version built from git commit: " + Version.getGitVersionInformation());

		int serverPort = 0;					// argument 1 - server port
		String outputFolderPath = null;		// argument 2 - image output folder path
		
		// check for correct number of arguments
		if (1 > args.length || args.length > 5)
		{
			log.fatal("The server expected arguments in the following format:\n" + "[port]" + " " + "[output folder]");
			System.exit(1);
		}
		
		// store command line arguments
		try
		{
			serverPort = Integer.parseInt(args[0]);
		} catch (NumberFormatException e)
		{
			log.fatal("The specified port is not a valid integer: " + args[0]);
			e.printStackTrace();
				
			System.exit(1);
		}
		
		if (args.length >= 2)
			outputFolderPath = args[1];
		
		FaceDetectionServer frs;
		
		if (outputFolderPath != null)
		{
			File outputFolder = new File(outputFolderPath);
			
			if (!outputFolder.exists())
			{
				log.fatal("The specified output folder does not exist: " + outputFolder.getAbsolutePath());
				System.exit(1);
			}
		
			frs = new FaceDetectionServer(serverPort, new ImageAnalysisResultsFileOutput(outputFolder));
		}
		else
		{
			frs = new FaceDetectionServer(serverPort, null);
		}
		
		
		// treat remaining arguments as switches
		for (int arg = 2; arg < args.length; arg++)
		{
			switch (args[arg])
			{
				case "--ack":	// enable wait for acknowledgment
					frs.setSendAcknowledgments(true);
					break;
					
				case "--csv":	// optionally specify a csv file for recording application specific data
					arg++;
					
					if (arg < args.length)
					{
						frs.applicationSpecificDataFile = new File(args[arg]);
						frs.dataRecorder = new DataRecorder(frs.applicationSpecificDataFile, EVENTS_CSV_HEADER);
					}
					else
					{
						log.fatal("Switch --csv requires a parameter, the path of the file for storing application specific data.");
						System.exit(1);
					}
					
					break;
					
				default:
					log.fatal("Undefined switch: " + args[arg]);
					System.exit(1);
					break;
			}
		}
		
		
		
		frs.runServer();
	}
	
	
	
	public FaceDetectionServer(int port, ImageAnalysisResultsOuput<Map<File, Integer>> output)
	{
		this.port = port;
		imageAnalsisResultsOutput = output;
		
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
		{	
			@Override
			public void run()
			{
				log.info("The server is shutting down.");				
			}
		}));
	}
	
	// runs the server to accept connections on the given port and store images within the given output folder
	public void runServer()
	{
        final ActiveConnectionCountWriter countWriter = new ActiveConnectionCountWriter(numberOfClients);
        countWriter.start();

		try (ServerSocket serverSocket = new ServerSocket(port))
		{
			serverSocket.setReuseAddress(true);
			
			if (sendAcknowledgments)
				log.info("Configured to send acknowledgements.");
			
			log.info("Started face detection server on port " + serverSocket.getLocalPort() + ".");
			log.info("Waiting for clients to connect...");
			
			// repeatedly wait for a client to connect and create a new thread to service the client after it connects
			for (int clientNumber = 1; serverSocket.isBound(); clientNumber++)
			{
				Socket clientSocket;
				ClientHandlingThread clientHandlingThread;
				
				clientSocket = serverSocket.accept();		// wait for a client to connect
				
				// create a new thread to service the client that just connected
				clientHandlingThread = new ClientHandlingThread(clientSocket, clientNumber);
				clientHandlingThread.start();				
			}		
		}
		catch (IOException e)
		{
			log.fatal("The server is unable to open a socket on port " + port + ".");
			System.exit(1);
		} finally {
		    countWriter.stopWriting();
		}
	}
	
	
	
	
	
	// Thread that communicates with a client concurrently with other ClientHandlingThreads
	class ClientHandlingThread extends Thread
	{	
		private Socket clientSocket;  // the socket used to communicate with the client
		private int clientNumber;     // a number to indicate which client in the sequence of connecting clients this thread handles		
		
		public ClientHandlingThread(Socket socket, int number)
		{
			super("Thread for client " + number + " at " + socket);
			clientSocket = socket;
			clientNumber = number;
		}
		
		public void run()
		{
			numberOfClients.incrementAndGet();
			
			log.info("Started communication with client " + clientNumber + " on socket " + clientSocket + ".");
			
			try (InputStream in = clientSocket.getInputStream(); OutputStream out = clientSocket.getOutputStream())
			{
				receiveImages(in, out, clientSocket.getInetAddress());
			}
			catch (IOException e)
			{
				log.error("Unable to obtain an input stream to communiate with client " + clientNumber + ".");
			}
			
			numberOfClients.decrementAndGet();
		}
		
		
		// receives images on the given InputStream and sends acknowledgment through the given OutputStream
		public void receiveImages(InputStream in, OutputStream out, InetAddress clientAddress)
		{
			try (ObjectInputStream messageIn = new ObjectInputStream(in); ObjectOutputStream messageOut = new ObjectOutputStream(out))
			{
				try
				{
					try
					{
						while (true)
						{
						    log.debug("readObject");
							ImageMessage message = (ImageMessage) messageIn.readObject();
							long timeImageReceived = System.currentTimeMillis();
							log.debug("getImage");
							BufferedImage image = message.getImage();
							int imageIndex = message.getIndex();
							log.info("Received image " + imageIndex + " (" + message.getName() + ") from client connection" + clientNumber + " at " + getClientIdentifier());
							
							if (imageAnalsisResultsOutput != null)
							{
							    log.debug("Classify image");
								Map<File, Integer> imageClassifications = multiClassifier.classifyImage(image);
								log.debug("outputImage");
								imageAnalsisResultsOutput.outputImage(image, message, imageClassifications);
							}
							

                            long timeImageProcessingFinished = System.currentTimeMillis();
                            
                            if (dataRecorder != null)
                                dataRecorder.recordLatencyEvent("image_processed", clientAddress.getHostAddress(), timeImageReceived, timeImageProcessingFinished);
							
							if (sendAcknowledgments)
							{
								try
								{
									messageOut.writeObject(new ImageProcessedAckMessage(message));
									log.info("Sent ackowledgment of processing image " + imageIndex + " (" + message.getName() + ") to client " + clientNumber + " at " + getClientIdentifier());
								}
								catch (IOException e)
								{
									log.error("Unable to send ackowledgment of processing image " + imageIndex + " (" + message.getName() + ") to client " + clientNumber + " at " + getClientIdentifier());
								}
							}
						}					
					}
					catch (IOException e)
					{
						log.info("Stopped receiving images from client connection " + clientNumber + " at " + getClientIdentifier());
					}
				}
				catch (ClassNotFoundException e)
				{
					// error indicating an issue with the way the ImageMessage was defined or used
					e.printStackTrace();
				}
			}
			catch (IOException e)
			{
				log.error("Unable to obtain ObjectInputStream from InputStream.");
				e.printStackTrace();
			}
		}
		
		// returns a String that identifies the client for this thread
		private String getClientIdentifier()
		{
			return clientSocket.getInetAddress() + ":" + clientSocket.getPort();
		}
	}
	
	
	// returns the number of clients currently being serviced
	public int getNumberOfClients()
	{
		return numberOfClients.get();
	}
	
	// returns the classifier object that is used to analyze the images
	public ImageMultiClassifier getImageMultiClassifier()
	{
		return multiClassifier;
	}
	
	public void setSendAcknowledgments(boolean send)
	{
		sendAcknowledgments = send;
	}
}
