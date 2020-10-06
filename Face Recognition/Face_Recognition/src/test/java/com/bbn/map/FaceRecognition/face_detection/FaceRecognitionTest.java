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
package com.bbn.map.FaceRecognition.face_detection;
import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.bbn.map.FaceRecognition.face_detection_server.FaceDetectionServer;
import com.bbn.map.FaceRecognition.face_detection_server.ImageAnalysisResultsFileOutput;
import com.bbn.map.FaceRecognition.image_sending_client.ImageSendingClient;

//import nu.pattern.OpenCV;


public class FaceRecognitionTest
{
	private static File testFolder = new File("TEST");
	private static File testInputFolder = new File(testFolder.getAbsolutePath() + File.separator + "input");
	private static File testOutputFolder = new File(testFolder.getAbsolutePath() + File.separator + "output");
	private static int testServerPort = 13924;
	
	private static int testNumberOfImages = 10;
	
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		testInputFolder.mkdirs();
		
		if (!testInputFolder.exists())
			throw new Exception("Unable to make test input directory: " + testInputFolder);
		
		for (int n = 0; n < testNumberOfImages; n++)
		{
			BufferedImage testImage = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
			File file = new File(testInputFolder.getAbsolutePath() + File.separator + "image_" + n + ".jpg");
			ImageIO.write(testImage, "jpg", file);
		}
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
		delete(testInputFolder);
		testFolder.delete();
	}

	@Before
	public void setUp() throws Exception
	{
		testOutputFolder.mkdirs();
		
		if (!testInputFolder.exists())
			throw new Exception("Unable to make test input directory: " + testOutputFolder);
	}

	@After
	public void tearDown() throws Exception
	{
		delete(testOutputFolder);
	}

	// deletes a file or a folder and its contents
	private static boolean delete(File folder)
	{
		// delete folder's contents
		if (folder.isDirectory())
		{			
			for (File a : folder.listFiles())
				delete(a);
		}
		
		return folder.delete();
	}
	
	
	@Test
	public void test()
	{
		FaceDetectionServer server = new FaceDetectionServer(testServerPort, new ImageAnalysisResultsFileOutput(testOutputFolder));
		ServerTestThread serverTestThread = new ServerTestThread(server);
		serverTestThread.start();
		
		ImageSendingClient client = new ImageSendingClient(testInputFolder, "localHost", testServerPort);
		client.setWaitForAcknowledgments(true);
		client.runClient();
		
		while(server.getNumberOfClients() == 0);		// wait for the server to start servicing client
		while(server.getNumberOfClients() > 0);			// wait for the server to finish servicing client
		
		int numberOfClassifiers = server.getImageMultiClassifier().getNumberOfClassifiers();
		int numberOfClassifierOutputFolders = testOutputFolder.listFiles().length;
		
		// ensure that each classifier has its own output folder
		assertTrue(numberOfClassifierOutputFolders == numberOfClassifiers);
		
		// loop through each classifier's folder
		for (int a = 0; a < testOutputFolder.listFiles().length; a++)
		{
			int images = 0;
			
			if (testOutputFolder.listFiles()[a].isDirectory())
			{
				// loop through each result folder within a a classifier's folder
				for (int b = 0; b < testOutputFolder.listFiles()[a].listFiles().length; b++)
				{
					if (testOutputFolder.listFiles()[a].listFiles()[b].isDirectory())
					{
						// loop through each image within a a classifier's result folder
						for (int i = 0; i < testOutputFolder.listFiles()[a].listFiles()[b].listFiles().length; i++)
						{
							File imageFile = testOutputFolder.listFiles()[a].listFiles()[b].listFiles()[i];
							
							if (imageFile.isFile())
								images++;
							else
								// ensure that only files are in the classifier's result folder
								fail("Found a stray directory in a results folder where only images should be: " + imageFile);
						}
					}
					else
					{
						// Ensure that there are no images that were placed directly within a classifier's output folder.
						// Each image should be placed in a subfolder within the classfier's folder indicating the result of the classification.
						fail("Found a file that was not placed in a results folder: " + testOutputFolder.listFiles()[a].listFiles()[b]);
					}
				}
			}
			else
			{
				// Ensure that there are no images that were placed directly within the output folder.
				// Each image should be placed in a subfolder within a classfier's folder indicating the result of the classification.
				fail("Found a file that was not placed in a results folder: " + testOutputFolder.listFiles()[a]);
			}
			
			// ensure that each image was classified for each of the classifiers
			assertTrue(testNumberOfImages == images);
		}
	}
	
	

	
	class ServerTestThread extends Thread
	{
		private FaceDetectionServer server;
		
		public ServerTestThread(FaceDetectionServer server)
		{
			this.server = server;
			this.server.setSendAcknowledgments(true);
		}
		
		@Override
		public void run()
		{
			super.run();
			server.runServer();
		}
	}

}
