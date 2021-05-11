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
package com.bbn.map.hifi.apps.filestore.protocol;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * Client to FileStore (query request from a client)
 */
final public class FileQueryOperation implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6825711874842829695L;
	
	public enum QueryType {BY_PRODUCER_NAME, BY_TIME_GT, BY_TIME_LT};
	
	QueryType type;
	long time = -1;
	String producerName = null;
	String queryClientName = null;
	
	Pattern p = Pattern.compile("[^a-zA-Z0-9]");
	
	public FileQueryOperation(QueryType type, String queryClientName) {
		this.type = type;
		this.queryClientName = queryClientName;
	}
	
	public QueryType getType() {
		return this.type;
	}
	
	public long getTime() {
		return time;
	}

	public void setTime(long time) throws Exception {
		if (time <= 0)
			throw new Exception("Invalid query time.");
		this.time = time;
	}

	public String getQueryClientName() {
		return queryClientName;
	}
	
	public String getProducerName() {
		return producerName;
	}

	public void setProducerName(String producerName) throws Exception {
		if (producerName == null || producerName.length() < 1 || p.matcher(producerName).find())
			throw new Exception("Invalid producer name.");
		this.producerName = producerName;
	}

	public boolean isValid() {
		if (this.type == QueryType.BY_PRODUCER_NAME) {
			if (producerName == null) {
				return false;
			}
		} else {
			if (time <= 0)
				return false;
		}
		return true;
	}
	
	@Override
	public String toString() {
		return "" + this.type + this.time + "[" + this.producerName + "|" + this.time + "]";
	}
}