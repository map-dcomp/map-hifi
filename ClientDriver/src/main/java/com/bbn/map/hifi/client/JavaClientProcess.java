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
package com.bbn.map.hifi.client;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.appmgr.util.AppMgrUtils;
import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.common.value.ApplicationSpecification;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.simulator.ClientLoad;

/**
 * A client that executes as a Java class.
 * 
 * @author jschewe
 *
 */
public class JavaClientProcess implements ClientInstance {

    private final Logger logger;
    private final Object lock = new Object();
    private JavaClient client;
    private Future<?> future = null;
    private final ExecutorService threadPool;

    /**
     * 
     * @param threadPool
     *            thread pool for executing clients
     * @param request
     *            the client request, used for naming of the logger
     * @param clientName
     *            name of the client for logging
     */
    JavaClientProcess(@Nonnull final ExecutorService threadPool,
            @Nonnull final String className,
            @Nonnull final ClientLoad request,
            @Nonnull String clientName) {
        this.threadPool = Objects.requireNonNull(threadPool);
        Objects.requireNonNull(className);
        Objects.requireNonNull(request);

        logger = LoggerFactory.getLogger(String.format("%s.%s", JavaClientProcess.class.getName(), clientName));

        final ApplicationCoordinates service = request.getService();
        this.client = instantiateClientClass(className, service, clientName, request, this.threadPool);
    }

    private static JavaClient instantiateClientClass(final String className,
            final ApplicationCoordinates service,
            final String clientName,
            final ClientLoad request,
            final ExecutorService threadPool) {
        final Path latencyLogDirectory = Paths.get(".", "container_data", service.getArtifact().replaceAll("\\s", "_"),
                clientName, "app_metrics_data");
        if (Files.notExists(latencyLogDirectory)) {
            try {
                Files.createDirectories(latencyLogDirectory);
            } catch (IOException e) {
                throw new RuntimeException("Unable to create latency log directory", e);
            }
        }
        final ApplicationSpecification appSpec = AppMgrUtils.getApplicationManager()
                .getApplicationSpecification((ApplicationCoordinates) service);
        Objects.requireNonNull(appSpec, "Could not find application specification for: " + service);

        final String fullHostname = DnsUtils.getFullyQualifiedServiceHostname(appSpec);

        try {
            final Class<JavaClient> clientClass = loadClientClass(className);
            final Constructor<JavaClient> constructor = findConstructor(clientClass);
            final JavaClient client = createInstance(constructor, fullHostname, latencyLogDirectory, request,
                    threadPool);
            return client;
        } catch (final IOException e) {
            throw new RuntimeException("Error opening latency log", e);
        }
    }

    @SuppressWarnings("unchecked") // casting of class type checked with if
                                   // statement
    private static Class<JavaClient> loadClientClass(final String className) {
        try {
            final Class<?> clientClass = Class.forName(className);
            if (!(JavaClient.class.isAssignableFrom(clientClass))) {
                throw new RuntimeException("Class " + className + " does not implement JavaClient.");
            }
            return (Class<JavaClient>) clientClass;
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException("Unable to find class " + className, e);
        }
    }

    private static JavaClient createInstance(final Constructor<JavaClient> constructor,
            final String host,
            final Path latencyLogPath,
            final ClientLoad request,
            final ExecutorService threadPool) throws IOException {
        try {
            final JavaClient client = constructor.newInstance(host, latencyLogPath, request, threadPool);
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
            throw new RuntimeException("Client class is abstract and cannot be instantiated", e);
        } catch (final IllegalArgumentException e) {
            throw new RuntimeException("Internal error, argument types don't match", e);
        }
    }

    private static <T extends JavaClient> Constructor<T> findConstructor(final Class<T> clientClass) {
        try {
            final Constructor<T> constructor = clientClass.getConstructor(String.class, Path.class, ClientLoad.class,
                    ExecutorService.class);
            return constructor;
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException(
                    "Unable to find constructor that takes a String and a Path in " + clientClass.getName(), e);
        }
    }

    @Override
    public void startClient() throws IllegalStateException {
        synchronized (lock) {
            if (null != future) {
                throw new IllegalStateException("Cannot start client instance when it's already running");

            }
            future = threadPool.submit(client);
        }
    }

    @Override
    public boolean isClientRunning() {
        synchronized (lock) {
            return null != future && !future.isDone();
        }
    }

    @Override
    public void stopClient() {
        logger.info("Stopping client");
        synchronized (lock) {
            logger.trace("Calling stop");
            client.stop();
            if (null != future) {
                future.cancel(true);
            }
        }
        logger.info("Stopped");
    }

}
