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
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.objdetect.CascadeClassifier;

import nu.pattern.OpenCV;




// The ImageClassifier provides functionality to take an image and provide Integer results for
// when multiple computer vision classifiers are applied to the image

public class ImageMultiClassifier
{
	private static Logger log = LogManager.getLogger(ImageMultiClassifier.class); //Logger.getRootLogger();
	private Map<CascadeClassifier, File> classifierToFileMap = new HashMap<>();
	
	// determines which classifiers will be used by specifying their parent directory
	public static final String CLASSIFIER_FOLDER = "classifiers";
	
	
	static
	{
		OpenCV.loadShared();
		log.info("Using OpenCV version: " + Core.VERSION);
//		System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
	}

	
	
	public ImageMultiClassifier()
	{
		int amountLoaded;
		
		amountLoaded = loadClassifiersFromFolder(new File(CLASSIFIER_FOLDER));
		
		log.info("Loaded " + amountLoaded + " classifiers.");
	}
		
	
	// removes all classifiers that will be used on future images
	private void clearClassifiers()
	{
		classifierToFileMap.clear();
	}
	
	// loads classifiers contained at any level of depth within classifierFolder
	// returns the number of classifiers that were loaded and added to the map
	private int loadClassifiersFromFolder(File classifierFolder)
	{
		int amountLoaded = 0;
		
		Stack<File> foldersAndFiles = new Stack<File>();
		foldersAndFiles.push(classifierFolder);
		
		while (!foldersAndFiles.isEmpty())
		{
			File currentFolderOrFile = foldersAndFiles.pop();
			
			if (currentFolderOrFile.isDirectory())		// if currentFolderOrFile is folder
			{
				// push all sub folders and files onto the stack
				for (File f : currentFolderOrFile.listFiles())
					foldersAndFiles.push(f);
			}
			else if (currentFolderOrFile.isFile()) // if currentFolderOrFile is a file
			{
				// attempt to load the classifier
				CascadeClassifier classifier = createClassifier(currentFolderOrFile.getAbsolutePath());
				
				// add the classifier to the map of classifiers if it successfully loaded
				if (classifier != null)
				{
					classifierToFileMap.put(classifier, currentFolderOrFile);
					amountLoaded++;
					
					log.debug("Loaded classifier from file: " + currentFolderOrFile);
				}
			}
		}
		
		return amountLoaded;		
	}
	
	
	// returns a map containing the results of each classifier (identified by its File) on the given image
	public Map<File, Integer> classifyImage(BufferedImage image)
	{		
		Map<File, Integer> classifierFileToResultMap = new HashMap<>();
		
		Mat imageMat = convertBufferedImageToCVMat(image);
		
		for (CascadeClassifier classifier : classifierToFileMap.keySet())
		{
			// attempt to run classifier on image
			try
			{
				Integer result = countOccurrencesInImage(imageMat, classifier);

				// add an entry to the map will the classifier File as the key and result as the value
				classifierFileToResultMap.put(classifierToFileMap.get(classifier), result);
			}
			catch (Exception e)
			{
				log.error("Error applying classifier " + classifierToFileMap.get(classifier).getName());
				e.printStackTrace();
				
				// add a null mapping to the result map for this classifier
				classifierFileToResultMap.put(classifierToFileMap.get(classifier), null);
			}
		}

log.debug("start imageMat.release");
		imageMat.release();		// free the memory used for imageMat
log.debug("end imageMat.release");
		
		return classifierFileToResultMap;
	}
	
	
	// converts a standard BufferedImage into an OpenCV image (Mat)
	private static Mat convertBufferedImageToCVMat(BufferedImage image)
	{
log.debug("start new Mat");
		Mat cvMatImage = new Mat(image.getWidth(), image.getHeight(), CvType.CV_8UC3);
log.debug("end new Mat");

//log.debug("start cast to DataBufferByte");
		DataBuffer buffer = image.getRaster().getDataBuffer();
		
//String bufferType = "";
//switch (buffer.getDataType())
//{
//	case DataBuffer.TYPE_BYTE:
//		bufferType = "TYPE_BYTE";
//		break;
//	case DataBuffer.TYPE_DOUBLE:
//		bufferType = "TYPE_DOUBLE";
//		break;
//	case DataBuffer.TYPE_FLOAT:
//		bufferType = "TYPE_FLOAT";
//		break;
//	case DataBuffer.TYPE_INT:
//		bufferType = "TYPE_INT";
//		break;
//	case DataBuffer.TYPE_SHORT:
//		bufferType = "TYPE_SHORT";
//		break;
//	case DataBuffer.TYPE_UNDEFINED:
//		bufferType = "TYPE_UNDEFINED";
//		break;
//	case DataBuffer.TYPE_USHORT:
//		bufferType = "TYPE_USHORT";
//		break;
//}
//
//if (!bufferType.equals(""))
//{
//	log.debug(" **** Image Data Buffer Type: " + bufferType);
//}
		if (buffer instanceof DataBufferByte)
		{
			byte[] imageData = ((DataBufferByte) buffer).getData();
//log.debug("end cast to DataBufferByte");
	
log.debug("start cvMatImage.put");
			cvMatImage.put(0, 0, imageData);
log.debug("end cvMatImage.put: H:" + cvMatImage.height() + " W:" + cvMatImage.width());
		}
		else
		{
			log.error("Warning: Unable to convert BufferedImage into a cv::Mat. The cv::Mat will be left blank.");
		}
		
		return cvMatImage;
	}
	
	// returns a classifier created from the given classifier file
	private static CascadeClassifier createClassifier(String filename)
	{
		CascadeClassifier classifier = new CascadeClassifier();
		classifier.load(filename);

		return classifier;
	}
	
	// uses the classifier to count the number of occurrences in the BufferedImage
	private int countOccurrencesInImage(Mat imageMat, CascadeClassifier classifier)
		throws CvException, Exception
	{
log.debug("start new MatOfRect");
		MatOfRect detections = new MatOfRect();
log.debug("end new MatOfRect");
log.debug("start detectMultiScale");
		classifier.detectMultiScale(imageMat, detections);
log.debug("end detectMultiScale");

		int count = detections.toArray().length;
log.debug("start detections.release");
		detections.release();							// free memory for detections MatOfRect
log.debug("end detections.release");
		return count;
	}
	
	// returns the number of classifiers that are loaded and used to to classify each image
	public int getNumberOfClassifiers()
	{
		return classifierToFileMap.size();
	}
}
