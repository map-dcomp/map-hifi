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
package com.bbn.map.hifi_resmgr;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/* package*/ class DockerImageManager {

    private static final Logger LOGGER = LogManager.getLogger(DockerImageManager.class);

    private final Object imagesLock = new Object();
    private final Set<String> localImages = new HashSet<>();
    private final Set<String> pendingImages = new HashSet<>();
    private final ImageFetcher fetcher;

    /**
     * 
     * @param fetcherClassname
     *            a class that implements {@link ImageFetcher}
     */
    /* package */ DockerImageManager(@Nonnull final String fetcherClassname) {
        fetcher = createFetcher(fetcherClassname);

        final Set<String> images = SimpleDockerResourceManager.getCurrentImages();
        localImages.addAll(images);
        LOGGER.info("Initial list of images: {}", localImages);
    }

    private static ImageFetcher createFetcher(String fetcherClassname) {
        try {
            final Class<ImageFetcher> clientClass = loadClientClass(fetcherClassname);
            final Constructor<ImageFetcher> constructor = findConstructor(clientClass);
            final ImageFetcher fetcher = createInstance(constructor);
            return fetcher;
        } catch (final IOException e) {
            throw new RuntimeException("Error opening latency log", e);
        }

    }

    @SuppressWarnings("unchecked") // casting of class type checked with if
                                   // statement
    private static Class<ImageFetcher> loadClientClass(final String className) {
        try {
            final Class<?> clientClass = Class.forName(className);
            if (!(ImageFetcher.class.isAssignableFrom(clientClass))) {
                throw new RuntimeException("Class " + className + " does not implement ImageFetcher.");
            }
            return (Class<ImageFetcher>) clientClass;
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException("Unable to find class " + className, e);
        }
    }

    private static ImageFetcher createInstance(final Constructor<ImageFetcher> constructor) throws IOException {
        try {
            final ImageFetcher client = constructor.newInstance();
            return client;
        } catch (final InvocationTargetException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw new RuntimeException("Error creating client instance", e);
            }
        } catch (final IllegalAccessException e) {
            throw new RuntimeException("Constructor is not public", e);
        } catch (final InstantiationException e) {
            throw new RuntimeException("ImageFetcher class is abstract and cannot be instantiated", e);
        } catch (final IllegalArgumentException e) {
            throw new RuntimeException("Internal error, argument types don't match", e);
        }
    }

    private static <T extends ImageFetcher> Constructor<T> findConstructor(final Class<T> clientClass) {
        try {
            final Constructor<T> constructor = clientClass.getConstructor();
            return constructor;
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("Unable to find a no argument constructor in " + clientClass.getName(), e);
        }
    }

    /**
     * Schedule an image to be fetched. If the image is already local or pending
     * this method does nothing.
     * 
     * @param image
     *            the image to fetch
     */
    public void fetchImage(@Nonnull final String image) {
        synchronized (imagesLock) {
            internalFetchImage(image);
        }
    }

    private void internalFetchImage(final String image) {
        if (localImages.contains(image)) {
            return;
        }
        if (pendingImages.contains(image)) {
            return;
        }
        pendingImages.add(image);
        fetcher.fetchImage(image, this::imageCallback);
    }

    private void imageCallback(final String image, final boolean success) {
        synchronized (imagesLock) {
            pendingImages.remove(image);
            if (success) {
                localImages.add(image);
            }
            imagesLock.notifyAll();
        }
    }

    /**
     * Block until the specified image is local or a failure fetching has
     * occurred. This will fetch the image if it's not already requested.
     * 
     * @param image
     *            the image to wait on
     * @return true if the image is now local, false if an error occurred
     *         fetching.
     * @throws InterruptedException
     *             see {@link Object#wait()}
     */
    public boolean waitForImage(@Nonnull final String image) throws InterruptedException {
        synchronized (imagesLock) {
            internalFetchImage(image);
            while (!localImages.contains(image) && pendingImages.contains(image)) {
                imagesLock.wait();
            }

            return localImages.contains(image);
        }

    }

}
