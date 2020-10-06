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
package com.bbn.map.FaceRecognition.face_detection_server;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.FaceRecognition.common.ImageMessage;


// Outputs analyzed images to files according to the results of the analysis and metadata for each image

public class ImageAnalysisResultsFileOutput implements ImageAnalysisResultsOuput<Map<File, Integer>>
{
	private static Logger log = LogManager.getRootLogger();
	public static final String OUTPUT_FILE_FORMAT = "jpg";		// the format of the image files to output
	private File outputFolder;	// folder where classified images will be stored
	
	private AtomicInteger fileOutputCounter = new AtomicInteger(0);
	
	
	public ImageAnalysisResultsFileOutput(File outputFolder)
	{
		this.outputFolder = outputFolder;
	}
	
	@Override
	public void outputImage(BufferedImage image, ImageMessage imageData, Map<File, Integer> imageClassifications)
	{
		// output the image to a File for each classification
		for (File f : imageClassifications.keySet())
		{
			File imageDestinationFolder = determineImageDestinationFromClassification(f, imageClassifications.get(f));
			
			if (!imageDestinationFolder.exists())
				imageDestinationFolder.mkdirs();
			
			log.debug("imageData.getName() = " + imageData.getName());
			String imageDestinationName = convertImageNameToFilename(imageData.getName());
			log.debug("imageDestinationName = " + imageDestinationName);
			
			if (imageDestinationFolder.exists())
			{
				// output the image file to the appropriate folder
				File imageOutputPath = new File(imageDestinationFolder.getAbsolutePath() + File.separatorChar + imageDestinationName);
				try
				{
					ImageIO.write(image, OUTPUT_FILE_FORMAT, imageOutputPath);
				} catch (Exception e)
				{
					log.error("Unable to output image to file: " + imageOutputPath);
					e.printStackTrace();
				}
			}
			else
			{
				log.error("Unable to create directory " + imageDestinationFolder + " for image " + imageDestinationName + ".");
			}
		}
	}
	
	// determines where the image should be outputted based on its classification
	// returns the location in the form of a File folder
	private File determineImageDestinationFromClassification(File classifierFile, Integer classificationResult)
	{
		String path = outputFolder.getAbsolutePath();
		path += File.separatorChar;
		path += classifierFile.getName();	// store image within a directory with the same name as the classifier's file
		path += File.separatorChar;
		
		if (classificationResult != null)
		{
			// if there was at least 1 face detected in the image
			if (classificationResult > 0)
				path += "faces";					// folder for images with at one face detected
			else
				path += "non_faces";				// folder for images with no faces detected
		}
		else
		{
			// place image with no result in an error directory
			// used for debugging purposes in case a classification fails on certain images
			path += "ERROR";
		}
		
		return new File(path);
	}
	
	// converts an image name inside an ImageMessage to a valid file name
	private String convertImageNameToFilename(String name)
	{
		// replace each slash with an underscore, append a unique counter value, and append a file extension
		String filename =  name.replaceAll("\\\\|\\/", "_") + "-" + fileOutputCounter.incrementAndGet() + "." + OUTPUT_FILE_FORMAT;
		return filename;
	}

}
