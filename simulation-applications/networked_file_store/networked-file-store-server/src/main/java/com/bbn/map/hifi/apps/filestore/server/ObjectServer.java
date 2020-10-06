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
/* Copyright (c) <2016,2019>, <Raytheon BBN Technologies>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.bbn.map.hifi.apps.filestore.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ServerSocketFactory;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.hifi.util.ActiveConnectionCountWriter;
import com.bbn.map.hifi.util.SimAppUtils;

public class ObjectServer implements Runnable {
    private static final Logger LOGGER = LogManager.getLogger(ObjectServer.class.getName());

    private final CSVPrinter latencyLog;

    GenericObjectProtocol callback = null;
    int port = -1;

    boolean keepGoing = true;
    private BlockingQueue<Runnable> workQueue = null;
    ThreadPoolExecutor threadPool = null;

    private final AtomicInteger activeConnectionCount = new AtomicInteger(0);
    final ActiveConnectionCountWriter countWriter = new ActiveConnectionCountWriter(activeConnectionCount);

    /**
     * @param port
     * @throws Exception
     */
    public ObjectServer(int port, int maxClients, GenericObjectProtocol callback) throws Exception {
        this.latencyLog = new CSVPrinter(
                Files.newBufferedWriter(
                        SimAppUtils.CONTAINER_APP_METRICS_PATH.resolve(SimAppUtils.LATENCY_LOG_FILENAME),
                        StandardOpenOption.CREATE_NEW),
                CSVFormat.EXCEL.withHeader(SimAppUtils.SERVER_LATENCY_CSV_HEADER));

        this.port = port;
        this.callback = callback;
        this.workQueue = new LinkedBlockingQueue<Runnable>();
        this.threadPool = new ThreadPoolExecutor(2, maxClients, 3000, TimeUnit.MILLISECONDS, workQueue);
    }

    /**
     * check if this object is still running.
     * 
     * @return boolean is the object running
     */
    public boolean isRunningTrue() {
        return keepGoing;
    }

    @Override
    public void run() {
        countWriter.start();

        try {
            final ServerSocketFactory ssf = ServerSocketFactory.getDefault();
            final ServerSocket ss = ssf.createServerSocket(port, 5);
            while (keepGoing) {
                Worker w = new Worker(ss.accept());
                this.threadPool.submit(w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            countWriter.stopWriting();
        }
    }

    /**
     * stop the network reactor
     */
    public void stop() {
        LOGGER.info("Stop was called on TCPServer on port: " + this.port + ". Shutting down gracefully.");
        keepGoing = false;
    }

    private class Worker implements Runnable {
        Socket socket = null;
        long requestStartTime;
        long requestEndTime;

        public Worker(Socket socket) {
            requestStartTime = System.currentTimeMillis(); // time immediately
                                                           // after connection
                                                           // opened
            LOGGER.info("Accepted connection from client at {}", socket.getInetAddress());
            this.socket = socket;
        }

        @Override
        public void run() {
            activeConnectionCount.incrementAndGet();
            try {
                try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
                    Object obj = ois.readObject();
                    Object ret = callback.handleClientMessage(obj);

                    if (ret != null) {
                        oos.writeObject(ret);

                        requestEndTime = System.currentTimeMillis(); // time ACK
                                                                     // sent
                        writeLatencyLog(socket.getInetAddress(), requestStartTime, requestEndTime);

                        oos.flush();
                    }

                    ois.close();
                    oos.close();
                }

                socket.close();
                socket = null;
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                activeConnectionCount.decrementAndGet();
            }
        }
    }

    private void writeLatencyLog(final InetAddress address, final long requestStart, final long requestEnd)
            throws IOException {
        synchronized (latencyLog) {
            latencyLog.printRecord(System.currentTimeMillis(), "request_processed", address.getHostAddress(),
                    requestStart, requestEnd, requestEnd - requestStart);
            latencyLog.flush();
        }

    }
}