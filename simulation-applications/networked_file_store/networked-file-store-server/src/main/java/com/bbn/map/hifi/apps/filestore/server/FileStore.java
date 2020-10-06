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
package com.bbn.map.hifi.apps.filestore.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bbn.map.hifi.apps.filestore.db.FileDatabase;
import com.bbn.map.hifi.apps.filestore.protocol.Acknowledgement;
import com.bbn.map.hifi.apps.filestore.protocol.FileQueryOperation;
import com.bbn.map.hifi.apps.filestore.protocol.FileStoreOperation;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.utils.LogExceptionHandler;

/**
 * TCP Server receives and stores FileStoreObject(s)
 * 
 * Sends small after each storage operation.
 *
 */
final public class FileStore implements GenericObjectProtocol {
	private static final Logger LOGGER = LogManager.getLogger(FileStore.class.getName());
	
	public static final String DEFAULT_DB_LOCATION = "/tmp/test.db";
    public static final int PORT = 59112;

	private final int maxClients = 20;
	private FileDatabase db = null;
	
	public FileStore(String dbLocation) throws Exception {
		LOGGER.info("Creating file store.");
		db = new FileDatabase(dbLocation);
		ObjectServer server = new ObjectServer(PORT, maxClients, this);
		Thread ioThread = new Thread(server);
		LOGGER.info("Waiting for connections.");
		ioThread.start();
		ioThread.join();
		db.close();
	}

	@Override
	public Object handleClientMessage(Object obj) {
		if (obj instanceof FileStoreOperation) {
	        LOGGER.info("Handling received FileStoreOperation message: {}", obj);
			db.storeFileStoreObject((FileStoreOperation) obj);
			return new Acknowledgement(Acknowledgement.Type.MAPACK, System.currentTimeMillis());
		} else if (obj instanceof FileQueryOperation) {
            LOGGER.info("Handling received FileQueryOperation message: {}", obj);
			return db.queryFileStoreObject((FileQueryOperation) obj);
		} else
		{		
            LOGGER.info("Handling received message: {}", obj);
    		return null;
		}
	}
	
	public static void main(String [] args) {
        LogExceptionHandler.registerExceptionHandler();

        DnsUtils.configureDnsCache();

		try {
			String dbLocation = FileStore.DEFAULT_DB_LOCATION;
			if (args.length >=1) {
				dbLocation = args[0];
				LOGGER.info("use set db location to: " + dbLocation);
			}
			new FileStore(dbLocation);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}