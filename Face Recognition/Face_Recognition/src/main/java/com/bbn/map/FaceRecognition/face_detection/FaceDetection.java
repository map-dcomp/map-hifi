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
package com.bbn.map.FaceRecognition.face_detection;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;

//use the classifier to detect presence or absence of faces
import org.opencv.objdetect.CascadeClassifier;





// The following class provides functionality that reads images from a specified input folder (first command line parameter)
// and copies each image to one of two output folders (second command line parameter specifies the parent of these two folders)
// depending on whether or not the image contains any faces.

public class FaceDetection
{
	//TODO: See if there is a better way to reference this library resource
	// determines which face detection classifier will be used by specifying its path
	public static final String FACE_DETECTION_CLASSIFIER = "C:\\Users\\awald\\Documents\\Development\\Libraries\\opencv\\sources\\data\\haarcascades\\"
//														 + "haarcascade_frontalface_alt.xml";
	 													 + "haarcascade_frontalface_default.xml";
	
	// specifies the sub output folders for photos containing faces and photos not containing faces
	public static final String FACES_FOLDER = "faces";
	public static final String NON_FACES_FOLDER = "non_faces";

	
	
	
	public static void main(String[] args)
	{
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		//FaceDetection fr = new FaceDetection();
		
		if (args.length != 2)
		{
			System.out.println("Expected the following parameters:\n" + "[input folder]" + " " + "[output folder]");
			System.exit(1);
		}
		
		// store command line parameters
		String inputFolderPath = args[0];
		String outputFolderPath = args[1];
		
		// create input and output folder File objects
		File inputFolder = new File(inputFolderPath);
		File outputFolderFaces = new File(outputFolderPath + "\\" + FACES_FOLDER);
		File outputFolderNonFaces = new File(outputFolderPath + "\\" + NON_FACES_FOLDER);
		
		
		// create output directories if they do not yet exist
		if (!outputFolderFaces.exists())
			outputFolderFaces.mkdirs();
		
		if (!outputFolderNonFaces.exists())
			outputFolderNonFaces.mkdirs();
		
		
		CascadeClassifier faceDetector = createFaceDetectionClassifier();
		
		// loop through and count the faces in each image that is directly within the output folder
		for (File imageFile : inputFolder.listFiles())
		{
			try
			{
				BufferedImage image = ImageIO.read(imageFile);
				
				// check if the image contains at least one face and store the image in the appropriate folder
				if (countFacesInImage(image, faceDetector) > 0)
					ImageIO.write(image, "jpg", new File(outputFolderFaces.getAbsolutePath() + "\\" + imageFile.getName()));
				else
					ImageIO.write(image, "jpg", new File(outputFolderNonFaces.getAbsolutePath() + "\\" + imageFile.getName()));
			}
			catch (IOException e)
			{
				System.out.println("Unable to load image file: " + imageFile.getName());
			}
			catch (CvException e)
			{
				System.out.println("The image file " + imageFile.getName() + " could not be classified possibly because the specified classifier path is not valid.");
			}
		}
	}
	

	
	// uses the classifier to count the number of faces in the BufferedImage
	private static int countFacesInImage(BufferedImage image, CascadeClassifier faceDetector)
		throws CvException
	{
		MatOfRect faceDetections = new MatOfRect();
		faceDetector.detectMultiScale(convertBufferedImageToCVMat(image), faceDetections);
		
		return faceDetections.toArray().length;
	}

	// returns a classifier that is used to detect faces in images
	private static CascadeClassifier createFaceDetectionClassifier()
	{
		CascadeClassifier faceDetector = new CascadeClassifier();
		faceDetector.load(FACE_DETECTION_CLASSIFIER);

		return faceDetector;
	}
	
	// converts a standard BufferedImage into an OpenCV image (Mat)
	private static Mat convertBufferedImageToCVMat(BufferedImage image)
	{
		Mat cvMatImage = new Mat(image.getWidth(), image.getHeight(), CvType.CV_8UC3);
		byte[] imageData = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
		cvMatImage.put(0, 0, imageData);
		
		return cvMatImage;
	}
}
