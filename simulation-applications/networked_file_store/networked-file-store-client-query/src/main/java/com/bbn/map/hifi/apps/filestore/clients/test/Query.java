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
/* Copyright (c) <2019>, <Raytheon BBN Technologies>
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
package com.bbn.map.hifi.apps.filestore.clients.test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.hifi.apps.filestore.protocol.Acknowledgement;
import com.bbn.map.hifi.apps.filestore.protocol.FileQueryOperation;
import com.bbn.map.hifi.apps.filestore.protocol.FileQueryResultOperation;
import com.bbn.map.hifi.apps.filestore.server.FileStore;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.hifi.util.SimAppUtils;
import com.bbn.map.utils.LogExceptionHandler;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;



public class Query
{
    private static final Logger LOGGER = LogManager.getLogger(Query.class.getName());

    private Random r = new Random();

    public static final String MY_NAME = "TestQuery";
    public static final String PUBLISHER_MY_NAME = "TestPublisher";
    
    private static final String[] CSV_HEADER = { "timestamp", "event", "client", "time_sent", "time_ack_received",
    "latency" };
    
    private final CSVPrinter latencyLog;
    private final CSVPrinter requestStatus;
    
    private void writeRequestStatus(final long timestamp,
            final InetAddress addr,
            final boolean success,
            final String message) {
        try {
            requestStatus.printRecord(timestamp, null == addr ? null : addr.getHostAddress(), success, message);
            requestStatus.flush();
        } catch (final IOException e) {
            LOGGER.error("Error writing request status {} {} {}", timestamp, success, message, e);
        }
    }
    
    /**
     * Constructs a new Query client that periodically queries the database.
     * 
     * @param host
     *          the hostname of the database to connect to
     * @param numberOfDataPackets
     *          number of packets to send or -1 to send indefinitely
     * @throws Exception
     *          thrown if there was a problem performing a query
     */
    @SuppressFBWarnings(value="IL_INFINITE_LOOP", justification="We want to have the option of querying until stopped in an infinite loop.")
    public Query(String host, int numberOfDataPackets) throws Exception
    {
        this.latencyLog = new CSVPrinter(Files.newBufferedWriter(
                SimAppUtils.CONTAINER_APP_METRICS_PATH.resolve(SimAppUtils.LATENCY_LOG_FILENAME),
                StandardOpenOption.CREATE_NEW), CSVFormat.EXCEL.withHeader(CSV_HEADER));

        final Path requestStatusPath = SimAppUtils.CONTAINER_APP_METRICS_PATH
                .resolve(SimAppUtils.REQUEST_STATUS_FILENAME);
        this.requestStatus = new CSVPrinter(Files.newBufferedWriter(requestStatusPath, StandardOpenOption.CREATE_NEW),
                CSVFormat.EXCEL.withHeader(SimAppUtils.CLIENT_REQUEST_STATUS_HEADER));

        LOGGER.debug("Query client starting.");

        for (int i = 0; i < numberOfDataPackets || numberOfDataPackets == -1; i++)
        {
            doSleep(1000);
            doQuery(host);
        }

        LOGGER.debug("Query client finished.");
    }

    private void doQuery(String host) throws Exception
    {
        FileQueryOperation fqo = new FileQueryOperation(FileQueryOperation.QueryType.BY_PRODUCER_NAME, Query.MY_NAME);
        fqo.setProducerName(MY_NAME);

        LOGGER.info("Sending FQO: " + fqo.toString());
        
        long requestStartTime = System.currentTimeMillis();

        try (Socket s = new Socket(host, FileStore.PORT))
        {
            try (ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
                    ObjectInputStream ois = new ObjectInputStream(s.getInputStream()))
            {
                oos.writeObject(fqo);
                oos.flush();

                Object ret = ois.readObject();
                
                long requestEndTime = System.currentTimeMillis();
                
                final InetAddress addr = s.getInetAddress();
                writeLatencyLog(addr, requestStartTime, requestEndTime);

                if (ret instanceof FileQueryResultOperation)
                {
                    FileQueryResultOperation fqro = (FileQueryResultOperation) ret;
                    LOGGER.debug(fqro.toString());
                    writeRequestStatus(requestStartTime, addr, true, null);
                } else if (ret instanceof Acknowledgement)
                {
                    LOGGER.debug("Received file store message: " + ((Acknowledgement) ret).getType());
                    writeRequestStatus(requestStartTime, addr, true, null);
                } else
                {
                    writeRequestStatus(requestStartTime, addr, false, "Unknown return object");
                    LOGGER.warn("Unknown return object from FileStore");
                }
            }
        }


    }

    private void doSleep(int min)
    {
        long sleepTime = r.nextInt(5000) + min;
        try
        {
            Thread.sleep(sleepTime);
        } catch (Exception ex)
        {

        }
    }

    public static void main(String[] args)
    {
        LogExceptionHandler.registerExceptionHandler();

        DnsUtils.configureDnsCache();

        if (args.length < 1)
        {
            LOGGER.fatal("Expected parameters: [database hostname]");
            System.exit(1);
        }

        String databaseAddress = args[0];
        int numberOfDataPackets = -1;
        
        if (args.length >= 2)
        {
            String numberOfDataPacketsStr = args[1];
            
            try
            {
                numberOfDataPackets = Integer.parseInt(numberOfDataPacketsStr);
                numberOfDataPackets = Math.max(numberOfDataPackets, 0);
            } catch (NumberFormatException e)
            {
                LOGGER.fatal("Specified number of data packets to receive is not a valid Integer: {}", numberOfDataPacketsStr);
                System.exit(1);
            }
        }
        
        if (numberOfDataPackets >= 0)
        {
            LOGGER.info("Configured to receive {} data packets.", numberOfDataPackets);
        }
        else
        {
            LOGGER.info("Configured to receive data packets until stopped.");
        }

        try
        {
            new Query(databaseAddress, numberOfDataPackets);
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        System.exit(0);
    }
    
    
    private void writeLatencyLog(final InetAddress address, final long requestStart, final long requestEnd)
            throws IOException {
        latencyLog.printRecord(System.currentTimeMillis(), "request_processed", address.getHostAddress(), requestStart,
                requestEnd, requestEnd - requestStart);
        latencyLog.flush();

    }
}
