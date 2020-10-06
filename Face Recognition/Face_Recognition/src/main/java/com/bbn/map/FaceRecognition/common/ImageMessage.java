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
package com.bbn.map.FaceRecognition.common;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;

import javax.imageio.ImageIO;

// a message that contains an Image to be sent from a client to a server

public class ImageMessage implements Serializable
{
	// used to determine if the format of the sent message is compatible with the format of the received message
	private static final long serialVersionUID = 1L;
	
	private String name = "";		// a String that identifies the image in this message
	private byte[] imageData;		// image data to be sent
	private int imageIndex;		    // the index of the image in the sequence of images that are sent
	
	// creates a message to send the given image
	public ImageMessage(BufferedImage image, int index, String name) throws IOException
	{
		this(image, index);
		this.name = name;
	}
	
	// creates a message to send the given image
	public ImageMessage(BufferedImage image, int index) throws IOException
	{
		imageData = bufferedImageToByteArray(image);
		imageIndex = index;
	}
	
	// returns the name of the image
	public String getName()
	{
		return name;
	}
	
	// returns the index of the image
	public int getIndex()
	{
		return imageIndex;
	}	
	
	
	
	// returns the image that is encoded within this message
	public BufferedImage getImage()
	{
		if (imageData == null)
			return null;
		
		try
		{
			return byteArrayToBufferedImage(imageData);
		} 
		catch (IOException e)
		{
			return null;
		}
	}
	
	// encodes a BufferedImage as an array of bytes
	private byte[] bufferedImageToByteArray(BufferedImage image) throws IOException
	{
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", stream);
		return stream.toByteArray(); 
	}
	
	// decodes an array of bytes into a BufferedImage
	private BufferedImage byteArrayToBufferedImage(byte[] data) throws IOException
	{
		ByteArrayInputStream stream = new ByteArrayInputStream(data);
		return ImageIO.read(stream);
	}
}
