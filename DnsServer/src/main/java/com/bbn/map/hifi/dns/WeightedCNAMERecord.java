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

import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.TextParseException;

import com.fasterxml.jackson.annotation.JsonProperty;

import java8.util.Objects;

/**
 * The value of a CNAME record that has a weight for how often to use it. This
 * class is thread safe.
 * 
 * @author jschewe
 *
 */
public class WeightedCNAMERecord {

    private final String name;
    private final String value;
    private final long ttl;
    private final double weight;

    /**
     * 
     * @param value
     *            see {@link #getValue()}
     * @param weight
     *            see {@link #getWeight()}, must be greater than zero
     * @param ttl
     *            see {@link #getTtl()}
     * @param name
     *            see {@link #getName()}
     */
    public WeightedCNAMERecord(@JsonProperty("name") final String name,
            @JsonProperty("value") final String value,
            @JsonProperty("ttl") final long ttl,
            @JsonProperty("weight") final double weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be greater than zero");
        }

        this.name = name;
        this.value = value;
        this.ttl = ttl;
        this.weight = weight;
    }

    /**
     * @return the name points to {@link #getValue()}, assumed to be absolute
     */
    public String getName() {
        return name;
    }

    /**
     * 
     * @return the name that this record points to, assumed to be absolute
     */
    public String getValue() {
        return value;
    }

    /**
     * 
     * @return The weight of this record relative to other records in the
     *         {@link WeightedRecordList}
     */
    public double getWeight() {
        return weight;
    }

    /**
     * 
     * @return DNS time to live
     */
    public long getTtl() {
        return ttl;
    }

    /**
     * @return the DNS record
     * @throws TextParseException
     *             if the name or value is not a valid domain name
     */
    public Record query() throws TextParseException {
        return new CNAMERecord(Name.fromString(getName() + "."), DClass.IN, getTtl(),
                Name.fromString(getValue() + "."));
    }

    @Override
    public String toString() {
        return String.format("%s -> %s weight: %f", getName(), getValue(), getWeight());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getValue());
    }

    /**
     * Compares {@link #getName()} and {@link #getValue()}, but not
     * {@link #getWeight()}. This allows the object to be found in the weighted
     * record list when merging values.
     * 
     * @return if this object equals {@code o}
     */
    @Override
    public boolean equals(final Object o) {
        if (null == o) {
            return false;
        } else if (this == o) {
            return true;
        }
        if (this.getClass().equals(o.getClass())) {
            final WeightedCNAMERecord other = (WeightedCNAMERecord) o;
            return getName().equals(other.getName()) //
                    && getValue().equals(other.getValue());
        } else {
            return false;
        }
    }

}
