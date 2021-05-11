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
package com.bbn.map.hifi.dns;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Record;
import org.xbill.DNS.TextParseException;

import com.bbn.map.utils.WeightedRoundRobin;

/**
 * A list of weighted DNS records. All records are assumed to be for the same
 * query host. This class is thread safe.
 * 
 * @author jschewe
 *
 */
public final class WeightedRecordList extends WeightedRoundRobin<WeightedCNAMERecord> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeightedRecordList.class);

    private final String name;

    /**
     * Create an empty list.
     * 
     * @param name
     *            see {@link #getName()}
     * @param records
     *            passed to {@link #addRecord(WeightedCNAMERecord, double)} to
     *            create the object
     */
    public WeightedRecordList(final String name, final List<WeightedCNAMERecord> records) {
        super();

        records.forEach(r -> addRecord(r, r.getWeight()));

        this.name = name;
    }

    /**
     * 
     * @return the name that points to the value of the contained records
     */
    public String getName() {
        return name;
    }

    /**
     * This method exists for serialization.
     * 
     * @return unmodifiable copy of the current records
     */
    public List<WeightedCNAMERecord> getRecords() {
        final List<WeightedCNAMERecord> records = new LinkedList<>();
        foreachRecord((record, weight) -> {
            records.add(record);
        });
        return Collections.unmodifiableList(records);
    }

    /**
     * @return the DNS record for this weighted record, will return null if the
     *         list is empty
     * @throws TextParseException
     *             If there is an error calling
     *             {@link WeightedCNAMERecord#query()}
     */
    public Record query() throws TextParseException {
        LOGGER.trace("Top of query");

        final WeightedCNAMERecord record = getNextRecord();
        if (null == record) {
            return null;
        } else {
            final Record dnsRecord = record.query();
            LOGGER.trace("Returning DNS record {}", dnsRecord);
            return dnsRecord;
        }
    }

}
