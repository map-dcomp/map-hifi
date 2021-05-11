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
package com.bbn.map.FaceRecognition.image_sending_client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.FaceRecognition.Version;
import com.bbn.map.FaceRecognition.common.DataRecorder;
import com.bbn.map.FaceRecognition.common.ImageMessage;
import com.bbn.map.FaceRecognition.common.ImageProcessedAckMessage;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.utils.LogExceptionHandler;




// The ImageSendingClient sends in sequence images from a specified folder to the server at the given address and port for analysis.

public class ImageSendingClient
{
	private static Logger LOGGER = LogManager.getLogger(ImageSendingClient.class);
	
	private Timer imageSendingTimer = new Timer();
	
	// the folder that contains the images to send
	private File imageFolder;
	private String serverAddress;			// the host name or address of the server to connect to
	private int serverPort;					// the server port to connect to
	
	// whether the client should wait for a response from the server acknowledging that the image was processed before sending the next image
	private boolean waitForAcknowledgments = false;
	
	// whether the client should create a new connection with the server for each image
	private boolean newConnectionPerImage = true;
	
	private File applicationSpecificDataFile = null;		// optionally specifies a csv file for recording application specific data to
	private DataRecorder dataRecorder = null;


	private static final String[] EVENTS_CSV_HEADER = {"timestamp", "event", "server", "time_image_sent", "time_ack_received", "latency"};
	
	
	public static void main(String[] args)
	{
        LogExceptionHandler.registerExceptionHandler();

        DnsUtils.configureDnsCache();

	    LOGGER.info("Running version built from git commit: " + Version.getGitVersionInformation());
	    
		String serverAddress;	  // IP address of server
		int serverPort = 0;		  // server port
		String imageFolderPath;	  // the path of the folder containing the images to be sent to the server
		
		// optional parameters
		float imagesPerSecond = Float.POSITIVE_INFINITY;
		int imageTotal = -1;
		
		
		// check for correct number of arguments
		if (args.length < 3)
		{
			LOGGER.fatal("The image client expected arguments in the following format:\n" 
								+ "[server address]" + " " + "[server port]" + " " + "[image folder]");
				
			System.exit(1);
		}
		
		// read command line arguments
		serverAddress = args[0];
		
		try
		{
			serverPort = Integer.parseInt(args[1]);
		} catch (NumberFormatException e)
		{
			LOGGER.fatal("The specified port is not a valid integer: " + args[1]);
			System.exit(1);
		}
		
		imageFolderPath = args[2];
		
		if (args.length >= 4)
			try
			{
				imagesPerSecond = Float.parseFloat(args[3]);
			}
			catch (NumberFormatException e)
			{
				LOGGER.fatal("The specified number of images per second to send is not a valid floating point number: " + args[3]);
				System.exit(1);
			}
		
		if (args.length >= 5)
			try
			{
				imageTotal = Integer.parseInt(args[4]);
			}
			catch (NumberFormatException e)
			{
				LOGGER.fatal("The specified number of images to send is not a valid integer: " + args[4]);
				System.exit(1);
			}
		
		
		
		ImageSendingClient client = new ImageSendingClient(new File(imageFolderPath), serverAddress, serverPort);
		
		// treat remaining arguments as switches
		for (int arg = 5; arg < args.length; arg++)
		{
			switch (args[arg])
			{
    			case "--one-connection":    // use same connection for each image
    			    client.newConnectionPerImage = false;
    			    break;
			
				case "--ack":	// enable wait for acknowledgment
					client.waitForAcknowledgments = true;
					break;
					
				case "--csv":	// optionally specify a csv file for recording application specific data
					arg++;
					
					if (arg < args.length)
					{
						client.applicationSpecificDataFile = new File(args[arg]);
						client.dataRecorder = new DataRecorder(client.applicationSpecificDataFile, EVENTS_CSV_HEADER);
					}
					else
					{
						LOGGER.fatal("Switch --csv requires a parameter, the path of the file for storing application specific data.");
						System.exit(1);
					}
					
					break;
					
				default:
					LOGGER.fatal("Undefined switch: " + args[arg]);
					System.exit(1);
					break;
			}
		}
		
		client.runClient(imagesPerSecond, imageTotal);
	}
	
	
	
	// construct a client, which, when started, will attempt to connect to the server at the given address and port
	// and then send the images that are found in the given imageFolder
	public ImageSendingClient(File imageFolder, String serverAddress, int serverPort)
	{
		this.imageFolder = imageFolder;
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
	}
	
	
	public void runClient()
	{
		runClient(Float.POSITIVE_INFINITY, -1);
		return;
	}

	public void runClient(float imagesPerSecond, int imageTotal)
	{
		// output error message if the given image folder path does not exist,
		// but does not terminate the program so that more errors can be outputted if appropriate
		if (!imageFolder.exists())
			LOGGER.fatal("The folder \"" + imageFolder.getAbsolutePath() + "\" does not exist");
		
		if (imagesPerSecond != Float.POSITIVE_INFINITY)
			LOGGER.info("Configured to send images at a rate of " + imagesPerSecond + " images per second.");
		
		if (imageTotal > -1)
			LOGGER.info("Configured to send " + imageTotal + " images.");
		
		if (waitForAcknowledgments)
			LOGGER.info("Configured to wait for acknowledgments.");
		
		if (newConnectionPerImage)
		    LOGGER.info("Configured to create a new connection per image.");
		
		LOGGER.info("Begin sending images...\n\n");
		
		if (applicationSpecificDataFile != null)
		{
			try
			{
				if (!applicationSpecificDataFile.exists())
					applicationSpecificDataFile.createNewFile();
				
			} catch (IOException e)
			{
			    LOGGER.error("Unable to create file for recording application specific data: " + applicationSpecificDataFile);
			}
			
			if (applicationSpecificDataFile.exists() && applicationSpecificDataFile.isFile())
				LOGGER.info("Configured to use file \"" + applicationSpecificDataFile + "\" for recording application specific data.");
			else
				LOGGER.error("Unable to create file for recording application specific data: " + applicationSpecificDataFile);
		}
		
		try
		{
		    int imagesSent = 0;
		    Stack<File> foldersAndFiles = new Stack<>();
		    foldersAndFiles.push(imageFolder);
		    
		    while ((imageTotal == -1 || imagesSent < imageTotal) && !foldersAndFiles.isEmpty())
		    {                
    			InetAddress address = InetAddress.getByName(serverAddress);  // perform a DNS lookup for each new connection
    			
    			try (Socket clientSocket = new Socket(address, serverPort)) 
    			{
    				LOGGER.info("Connected to server at " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
    				
    				try (OutputStream out = clientSocket.getOutputStream(); InputStream in = clientSocket.getInputStream())
    				{
    	                File currentFolderOrFile = foldersAndFiles.pop();
    	                
    	                // if there should be a new connection per image, loop until the next image (rather than an entire folder) is found
    	                while (newConnectionPerImage && currentFolderOrFile.isDirectory())
    	                {
    	                    // push all sub folders and files onto the stack
    	                    for (File f : currentFolderOrFile.listFiles())
    	                        foldersAndFiles.push(f);
    	                    
    	                    currentFolderOrFile = foldersAndFiles.pop();
    	                }
    				    
    				    // send images for this connection
					    File[] imageFolderAndFiles = new File[1];
					    imageFolderAndFiles[0] = currentFolderOrFile;
						imagesSent += sendImages(imageFolderAndFiles, out, in, imagesPerSecond, imageTotal, address);
						LOGGER.info("Total images sent: {}\n", imagesSent);
    				}
    				catch (IOException e)
    				{
    					LOGGER.fatal("Unable to obtain an output stream to communicate with the server.");
    				}
    			}
    			catch (IOException e)
    			{
    				LOGGER.fatal("Unable to create a socket to communicate with the server at " + address + " on port " + serverPort);
    				
    				if (!newConnectionPerImage)
    				    break;
    			}
		    }
		}
		catch (UnknownHostException e)
		{
			LOGGER.fatal("The given host name or address (" + serverAddress + ") could not be found.");
			return;
		}
		
		imageSendingTimer.cancel();
	}
	
	
	// writes images found in the specified folder to the given output stream
	private int sendImages(File[] imageFolderAndFiles, OutputStream out, InputStream in, float imagesPerSecond, int imageTotal, InetAddress serverAddress)
	{		
		long msPeriod = (long) (1000.0/imagesPerSecond);
		
		if (msPeriod < 1)
			msPeriod = 1;
		
		try
		{
		    SendImageTask imageSendingTask = new SendImageTask(imageFolderAndFiles, out, in, imageTotal, serverAddress);
			

			synchronized (imageSendingTask)		// ensure that this thread has the lock on imageSendingTask before imageSendingTask can claim it
			{
				imageSendingTimer.schedule(imageSendingTask, msPeriod, msPeriod);
				
				try
				{
					// release the lock so that the imageSendingTask can execute and wait for imageSendingTask to finish
					imageSendingTask.wait();
				} catch (InterruptedException e)
				{
					LOGGER.fatal("Unable to wait for image sending to finish.");
					e.printStackTrace();
				}
			}
			
		     return imageSendingTask.getImagesSent();
		} catch (IOException e)
		{
			LOGGER.fatal("Unable to schedule the sending of images.");
		}
		
		return 0;
	}
	
	class SendImageTask extends TimerTask
	{
		private ObjectOutputStream messageOut;		
		private ObjectInputStream messageIn;
		
        private InetAddress serverAddress;
		
		private int imageTotal = -1;		// maximum number of images to send before this task and timer are cancelled or -1 to send as many images as possible
		private int imagesSent = 0;
		private int imageIndex = 1;
		
		private Stack<File> foldersAndFiles = new Stack<File>();
		
		public SendImageTask(File[] imageFoldersAndFiles, OutputStream out, InputStream in, InetAddress serverAddress)
			throws IOException
		{
			this(imageFoldersAndFiles, out, in, -1, serverAddress);
		}
		
		public SendImageTask(File[] imageFoldersAndFiles, OutputStream out, InputStream in, int imageTotal, InetAddress serverAddress)
			throws IOException
		{
		    for (File imageFolderOrFile : imageFoldersAndFiles)
		        foldersAndFiles.push(imageFolderOrFile);
			
			this.imageTotal = imageTotal;
			this.serverAddress = serverAddress;
			
			initializeSendingStream(out);
			initializeReceivingStream(in);
		}
		
		private void initializeSendingStream(OutputStream out)
			throws IOException
		{
			try
			{
				messageOut = new ObjectOutputStream(out);
			} catch (IOException e)
			{
				LOGGER.fatal("Unable to obtain ObjectOutputStream from OutputStream.");
				throw e;
			}
		}
		
		private void initializeReceivingStream(InputStream in)
				throws IOException
			{
				try
				{
					messageIn = new ObjectInputStream(in);
				} catch (IOException e)
				{
					LOGGER.error("Unable to obtain ObjectInputStream from InputStream.");
					throw e;
				}
			}
		
		// sends an image
		@Override
		public synchronized void run()
		{
			if (!foldersAndFiles.isEmpty() && (imageTotal == -1 || imagesSent < imageTotal))
			{
				File currentFolderOrFile = foldersAndFiles.pop();
				
				// while currentFolderOrFile is a folder
				while (currentFolderOrFile.isDirectory())
				{
					// push all sub folders and files onto the stack
					for (File f : currentFolderOrFile.listFiles())
						foldersAndFiles.push(f);
					
					currentFolderOrFile = foldersAndFiles.pop();
				}
				
				if (currentFolderOrFile.isFile())
				{
					// treat the file as an image
					File imageFile = currentFolderOrFile;
					
					try
					{
						BufferedImage image = ImageIO.read(imageFile);
						
						if (image != null)		//  if the file is a valid image
						{
							// try to create and send the image message
							try
							{
								ImageMessage im = new ImageMessage(image, imageIndex, createImageName(imageFile));
								messageOut.writeObject(im);
								long timeImageSent = System.currentTimeMillis();								
								imagesSent++;
								
								LOGGER.info("Sent connection image " + im.getIndex() + " (" + im.getName() + ") from " + imageFile.getAbsolutePath() + " to server.");
							
								// wait for image acknowledgment
								if (waitForAcknowledgments)
								{
									try
									{
										ImageProcessedAckMessage ack = (ImageProcessedAckMessage) messageIn.readObject();
										long timeAckReceived = System.currentTimeMillis();
										LOGGER.info("Received acknowledgment for image " + ack.getIndex() +  " (" + im.getName() + ").");
										
										if (ack.getIndex() != im.getIndex())
											LOGGER.error("The image index in the acknowledgment (" + ack.getIndex() + ") does not match the index of the image that was sent (" + im.getIndex() + ").");
										
										if (!ack.getName().equals(im.getName()))
											LOGGER.error("The image name in the acknowledgment (" + ack.getName() + ") does not match the name of the image that was sent (" + im.getName() + ").");
										
										if (dataRecorder != null)
											dataRecorder.recordLatencyEvent("image_ack_received", serverAddress.getHostAddress(), timeImageSent, timeAckReceived);
									} catch (ClassNotFoundException | IOException e)
									{
										LOGGER.error("Unable to receive image acknowledgment for image " + imageIndex + " (" + createImageName(imageFile) + ") from server.");
									}
								}
							}
							catch (IOException e)
							{
                                long timeImageSendingFailed = System.currentTimeMillis();
								LOGGER.error("Unable to send image " + imageIndex + " (" + createImageName(imageFile) + ") from " + imageFile.getAbsolutePath());
								
                                if (dataRecorder != null)
                                    dataRecorder.recordLatencyEvent("image_sending_failed", serverAddress.getHostAddress(), timeImageSendingFailed, -1);
							}
						}
					}
					catch (IOException e)
					{
						LOGGER.error("Unable to load image file " + imageIndex + ": " + imageFile.getAbsolutePath());
					}
					
					imageIndex++;	
				}
			}
			
			// cancel this task if the last image was just sent or if no images should be sent
			if (!(!foldersAndFiles.isEmpty() && (imageTotal == -1 || imagesSent < imageTotal)))
			{
				this.cancel();
				notifyAll();					// causes the main thread to proceed and close the Socket
			}
		}
		
		public int getImagesSent()
		{
		    return imagesSent;
		}
	};
	
	
	// creates a name to label an image based on its file
	public String createImageName(File imageFile)
	{
		String name = imageFile.getAbsolutePath();
		
		// remove the initial part of the path that is common among all images so that only the relative path within the image folder remains
		// also remove the file's extension
		name = name.substring(imageFolder.getAbsolutePath().length() + 1, name.lastIndexOf('.'));		
		return name;
	}
	
	public void setWaitForAcknowledgments(boolean wait)
	{
		waitForAcknowledgments = wait;
	}
}
