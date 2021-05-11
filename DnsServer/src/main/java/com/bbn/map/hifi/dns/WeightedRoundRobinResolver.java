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

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.CloseableThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.DNAMERecord;
import org.xbill.DNS.ExtendedFlags;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.NSRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.NameTooLongException;
import org.xbill.DNS.OPTRecord;
import org.xbill.DNS.Opcode;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TSIG;
import org.xbill.DNS.TSIGRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import org.xbill.DNS.Zone;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.hifi.util.DnsUtils;
import com.diffplug.common.base.Errors;

import se.unlogic.eagledns.EagleDNS;
import se.unlogic.eagledns.Request;
import se.unlogic.eagledns.plugins.BasePlugin;
import se.unlogic.eagledns.resolvers.AuthoritativeResolver;
import se.unlogic.eagledns.resolvers.Resolver;
import se.unlogic.standardutils.net.SocketUtils;

/**
 * Resolver that supports weighted round robin. This is based on the
 * {@link AuthoritativeResolver} in EagleDNS. Ideally the class
 * {@link AuthoritativeResolver} would be sub-classed, but the methods aren't
 * available to subclasses.
 * 
 * This resolver will do recursive queries under the following assumptions:
 * <ul>
 * <li>It is assumed that the zone file loaded in only contains A and NS records
 * (no CNAME records)</li>
 * <li>It is assumed that the weighted record list always returns a single
 * record</li>
 * </ul>
 * 
 * @author jschewe
 *
 */
public class WeightedRoundRobinResolver extends BasePlugin implements Resolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeightedRoundRobinResolver.class);
    private final Object lock = new Object();

    // default matches that in SimpleResolver
    private int delegationTimeout = 10;

    /**
     * 
     * @param v
     *            timeout as a string, used by EagleDNS property configuration
     */
    public void setDelegationTimeout(final String v) {
        setDelegationTimeout(Integer.parseInt(v));
    }

    /**
     * The amount of time to wait for a response from a delegate DNS server.
     * 
     * @param v
     *            new timeout value in seconds
     */
    public void setDelegationTimeout(final int v) {
        delegationTimeout = v;
    }

    private String logFilePathName = null;
    private BufferedWriter logFileWriter = null;

    /**
     * @param logFilePath
     *            where to write the log file, if null no log file is written
     */
    public void setLogFilePath(final String logFilePath) {
        this.logFilePathName = logFilePath;
    }

    private Map<Name, WeightedRecordList> weightedRecords = new HashMap<>();

    /**
     * Get the client name
     * 
     * @param request
     *            the DNS request
     * @return the client IP address or a string that the socket type is unknown
     */
    private String getClientAddress(final Request request) {
        final SocketAddress sockaddr = request.getSocketAddress();
        if (sockaddr instanceof InetSocketAddress) {
            final InetSocketAddress isockaddr = (InetSocketAddress) sockaddr;
            final InetAddress clientAddr = isockaddr.getAddress();
            final String client = clientAddr.getHostAddress();
            return client;
        } else {
            return "Unknown socket address type";
        }
    }

    @Override
    public Message generateReply(final Request request) throws Exception {
        final long timestamp = System.currentTimeMillis();
        final String clientAddress = getClientAddress(request);

        Message query = request.getQuery();
        Record queryRecord = query.getQuestion();

        try (CloseableThreadContext.Instance ctc = CloseableThreadContext.push(String.format("client: %s lookup: %s",
                clientAddress, null == queryRecord ? "null" : queryRecord.getName()))) {

            if (queryRecord == null) {
                LOGGER.debug("generateReply: No query, returning null");
                return null;
            }

            Name name = queryRecord.getName();
            Zone zone = findBestZone(name);

            LOGGER.debug("Resolver {} processing request for {} from {} with zone {}", this.name, name, clientAddress,
                    null == zone ? null : zone.getSOA());

            if (zone != null) {
                LOGGER.debug("matching zone found");

                Header header;
                // boolean badversion;
                int flags = 0;

                header = query.getHeader();
                if (header.getFlag(Flags.QR)) {
                    LOGGER.trace("generateReply: QR in header - returning null");
                    return null;
                }
                if (header.getRcode() != Rcode.NOERROR) {
                    LOGGER.trace("generateReply: rcode error {}", header.getRcode());
                    return null;
                }
                if (header.getOpcode() != Opcode.QUERY) {
                    LOGGER.trace("generateReply: not a query {} - returning null", header.getOpcode());
                    return null;
                }

                TSIGRecord queryTSIG = query.getTSIG();
                TSIG tsig = null;
                if (queryTSIG != null) {
                    tsig = systemInterface.getTSIG(queryTSIG.getName());
                    if (tsig == null || tsig.verify(query, request.getRawQuery(), request.getRawQueryLength(),
                            null) != Rcode.NOERROR) {
                        LOGGER.trace("generateReply: invalid TSIG - returning null");
                        return null;
                    }
                }

                OPTRecord queryOPT = query.getOPT();
                // if (queryOPT != null && queryOPT.getVersion() > 0) {
                // // badversion = true;
                // }

                if (queryOPT != null && (queryOPT.getFlags() & ExtendedFlags.DO) != 0) {
                    flags = EagleDNS.FLAG_DNSSECOK;
                }

                Message response = new Message(query.getHeader().getID());
                response.getHeader().setFlag(Flags.QR);
                if (query.getHeader().getFlag(Flags.RD)) {
                    response.getHeader().setFlag(Flags.RD);
                }

                // this servers supports recursion
                response.getHeader().setFlag(Flags.RA);

                response.addRecord(queryRecord, Section.QUESTION);

                int type = queryRecord.getType();
                int dclass = queryRecord.getDClass();
                if (type == Type.AXFR && request.getSocket() != null) {
                    LOGGER.trace("Returning from AXFR");
                    return doAXFR(name, query, tsig, queryTSIG, request.getSocket());
                }
                if (!Type.isRR(type) && type != Type.ANY) {
                    LOGGER.trace("generateReply: Not RR and not ANY {} - returning null", type);
                    return null;
                }

                final byte rcode = addAnswer(timestamp, clientAddress, response, name, type, dclass, 0, flags, zone);

                if (rcode != Rcode.NOERROR && rcode != Rcode.NXDOMAIN) {
                    LOGGER.trace("generateReply: rcode error: {} - returning error", rcode);
                    return EagleDNS.errorMessage(query, rcode);
                }

                addAdditional(response, flags);

                if (queryOPT != null) {
                    final int optflags = (flags == EagleDNS.FLAG_DNSSECOK) ? ExtendedFlags.DO : 0;
                    final OPTRecord opt = new OPTRecord((short) 4096, rcode, (byte) 0, optflags);
                    response.addRecord(opt, Section.ADDITIONAL);
                }

                response.setTSIG(tsig, Rcode.NOERROR, queryTSIG);

                if (rcode == Rcode.NXDOMAIN) {
                    LOGGER.trace("generateReply: returning no name for {} due to NXDOMAIN", name);
                }

                LOGGER.debug("Response is: {}", response);

                return response;

            } else {

                LOGGER.trace("Resolver " + this.name + " ignoring request for " + name + ", no matching zone found");

                return null;
            }
        } // logging context
    }

    private void addAdditional(Message response, int flags) {

        addAdditional2(response, Section.ANSWER, flags);
        addAdditional2(response, Section.AUTHORITY, flags);
    }

    private static final int MAX_ITERATIONS = 6;

    private byte addAnswer(final long timestamp,
            final String clientAddress,
            final Message response,
            final Name name,
            int type,
            final int dclass,
            final int iterations,
            int flags,
            Zone zone) {
        LOGGER.trace("{} Looking up {} with type {} zone {}", clientAddress, name, type,
                null == zone ? null : zone.getSOA());

        SetResponse sr;
        byte rcode = Rcode.NOERROR;

        if (iterations > MAX_ITERATIONS) {
            LOGGER.warn("Hit {} iterations resolving {}", MAX_ITERATIONS, name);
            return Rcode.NOERROR;
        }

        if (type == Type.SIG || type == Type.RRSIG) {
            type = Type.ANY;
            flags |= EagleDNS.FLAG_SIGONLY;
        }

        if (zone == null) {
            zone = findBestZone(name);
            LOGGER.trace("findBestZone returned {}", zone.getSOA());
        }

        if (zone != null) {
            sr = zone.findRecords(name, type);
            LOGGER.trace("Found records: {} type: {}, if NXDOMAIN and type is 1, will look in weightedRecords", sr,
                    type);

            if (sr.isNXDOMAIN() && type == Type.A) {
                // only use weighting for records that aren't in the
                // standard
                // zone

                final WeightedRecordList weightedList;
                synchronized (lock) {
                    LOGGER.trace("Looking for {} in {}", name, weightedRecords);

                    weightedList = weightedRecords.get(name);
                }
                LOGGER.trace("weightedList: {}", weightedList);

                if (null == weightedList) {
                    // not found
                    respondWithNxdomain(response, iterations, zone);
                    rcode = Rcode.NXDOMAIN;
                } else {
                    try {
                        final Record record = weightedList.query();
                        if (null == record) {
                            LOGGER.debug("Got an empty weighted list for {}, returning NXDOMAIN", name);

                            respondWithNxdomain(response, iterations, zone);
                            rcode = Rcode.NXDOMAIN;
                        } else {
                            LOGGER.trace("Found weighted record: {}", record);

                            // if we have a response, then we are
                            // authoritative
                            response.getHeader().setFlag(Flags.AA);

                            final RRset rrset = new RRset(record);

                            addRRset(name, response, rrset, Section.ANSWER, flags);

                            addNS(response, zone, flags);

                            writeLogEntry(timestamp, clientAddress, name, record);

                            if (record instanceof CNAMERecord) {
                                rcode = addAnswer(timestamp, clientAddress, response,
                                        ((CNAMERecord) record).getTarget(), type, dclass, iterations + 1, flags, null);
                            } else {
                                rcode = Rcode.NOERROR;
                            }

                        }

                    } catch (final TextParseException e) {
                        LOGGER.error("Found invalid name in weighted records, returning NXDOMAIN", e);

                        respondWithNxdomain(response, iterations, zone);
                        rcode = Rcode.NXDOMAIN;
                    }
                }

            } else if (sr.isNXRRSET()) {
                LOGGER.trace("Got NXRRSET looking for address {} with type {} from client {}", name, type,
                        clientAddress);
                addSOA(response, zone);
                if (iterations == 0) {
                    response.getHeader().setFlag(Flags.AA);
                }
            } else if (sr.isDelegation()) {
                final RRset nsRecords = sr.getNS();

                LOGGER.trace("Inside delegation. NS records: {}", nsRecords);

                addRRset(nsRecords.getName(), response, nsRecords, Section.AUTHORITY, flags);

                final List<Name> nameServers = new LinkedList<>();
                final Iterator<?> it = nsRecords.rrs();
                while (it.hasNext()) {
                    final NSRecord r = (NSRecord) it.next();
                    nameServers.add(r.getTarget());
                }

                // send a message to the other server(s) here
                rcode = resolveDelegation(name, type, dclass, nameServers, response);

            } else if (sr.isCNAME()) {
                CNAMERecord cname = sr.getCNAME();
                RRset rrset = new RRset(cname);
                addRRset(name, response, rrset, Section.ANSWER, flags);
                if (iterations == 0) {
                    response.getHeader().setFlag(Flags.AA);
                }
                rcode = addAnswer(timestamp, clientAddress, response, cname.getTarget(), type, dclass, iterations + 1,
                        flags, null);
            } else if (sr.isDNAME()) {
                DNAMERecord dname = sr.getDNAME();
                RRset rrset = new RRset(dname);
                addRRset(name, response, rrset, Section.ANSWER, flags);
                Name newname;
                try {
                    newname = name.fromDNAME(dname);
                } catch (NameTooLongException e) {
                    return Rcode.YXDOMAIN;
                }
                rrset = new RRset(new CNAMERecord(name, dclass, 0, newname));
                addRRset(name, response, rrset, Section.ANSWER, flags);
                if (iterations == 0) {
                    response.getHeader().setFlag(Flags.AA);
                }
                rcode = addAnswer(timestamp, clientAddress, response, newname, type, dclass, iterations + 1, flags,
                        null);
            } else if (sr.isSuccessful()) {
                RRset[] rrsets = sr.answers();
                for (RRset rrset : rrsets) {
                    addRRset(name, response, rrset, Section.ANSWER, flags);
                }
                addNS(response, zone, flags);
                if (iterations == 0) {
                    response.getHeader().setFlag(Flags.AA);
                }
            }
        }

        return rcode;
    }

    private void writeLogEntry(final long timestamp, final String clientAddress, final Name name, final Record record) {
        if (null != logFileWriter) {
            final String alias;
            if (record instanceof CNAMERecord) {
                final CNAMERecord crecord = (CNAMERecord) record;
                alias = crecord.getAlias().toString(true);
            } else {
                alias = "Unexpected record class: " + record.getClass();
            }

            final String row = String.format("%d,%s,%s,%s", timestamp, clientAddress, name.toString(true), alias);
            try {
                synchronized (logFileWriter) {
                    logFileWriter.write(row);
                    logFileWriter.newLine();
                } // lock
            } catch (final IOException e) {
                LOGGER.error("Error writing row '{}' to logfile: {}", row, e.getMessage(), e);
            }
        }
    }

    private byte resolveDelegation(final Name name,
            final int type,
            final int dclass,
            final List<Name> nameServers,
            final Message response) {
        byte rcode = -1;
        for (final Name serverName : nameServers) {
            final String serverHostName = serverName.toString(true);

            long resolveStartTime = Long.MAX_VALUE;
            long resolveDuration;

            try {
                // cache the resolvers for performance, not thread safe so using
                // ThreadLocal
                if (!resolverCache.get().containsKey(serverHostName)) {
                    final SimpleResolver resolver = new SimpleResolver(serverHostName);
                    resolver.setTimeout(delegationTimeout);
                    if (AgentConfiguration.getInstance().getDnsDelegationUseTcp()) {
                        resolver.setTCP(true);
                    }

                    resolverCache.get().put(serverHostName, resolver);
                }
                final SimpleResolver resolver = resolverCache.get().get(serverHostName);

                final Record queryRecord = Record.newRecord(name, type, dclass);
                final Message query = Message.newQuery(queryRecord);

                resolveStartTime = System.currentTimeMillis();
                final Message delegateResponse = resolver.send(query);
                resolveDuration = System.currentTimeMillis() - resolveStartTime;

                LOGGER.debug("duration of successful resolver.send for destination {}: {} ms", resolver.getAddress(),
                        resolveDuration);

                if (rcode == -1) {
                    // just use it
                    rcode = (byte) delegateResponse.getRcode();
                }

                if (Rcode.NOERROR == rcode && Rcode.NOERROR != delegateResponse.getRcode()) {
                    LOGGER.warn(
                            "Got error response from {} for {}, have a positive response from another server so this is ignored",
                            serverName, name);
                } else {
                    rcode = (byte) delegateResponse.getRcode();
                }

                if (Rcode.NOERROR == delegateResponse.getRcode()) {
                    if (delegateResponse.getHeader().getFlag(Flags.AA)) {
                        // found the authorative answer
                        response.getHeader().setFlag(Flags.AA);
                    }

                    // process the result
                    for (int section : new int[] { Section.ANSWER, Section.AUTHORITY, Section.ADDITIONAL }) {
                        final RRset[] answers = delegateResponse.getSectionRRsets(section);
                        for (RRset rrset : answers) {
                            addRRset(name, response, rrset, section, 0);
                        }

                    }
                }

            } catch (final UnknownHostException e) {
                LOGGER.error("Error resolving nameserver address '{}' in {}: {}", serverName.toString(true),
                        nameServers, e.getMessage(), e);
            } catch (final IOException e) {
                resolveDuration = System.currentTimeMillis() - resolveStartTime;

                LOGGER.error("Error querying server '{}' in list {} after {} ms: {}", serverName.toString(true),
                        nameServers, resolveDuration, e.getMessage(), e);
            }
        }
        if (-1 == rcode) {
            return Rcode.SERVFAIL;
        } else {
            return rcode;
        }
    }

    private void respondWithNxdomain(final Message response, final int iterations, Zone zone) {
        response.getHeader().setRcode(Rcode.NXDOMAIN);
        if (zone != null) {
            addSOA(response, zone);
            if (iterations == 0) {
                response.getHeader().setFlag(Flags.AA);
            }
        }
    }

    private Message doAXFR(Name name, Message query, TSIG tsig, TSIGRecord qtsig, Socket socket) {

        boolean first = true;

        Zone zone = this.findBestZone(name);

        if (zone == null) {

            return EagleDNS.errorMessage(query, Rcode.REFUSED);

        }

        // Check that the IP requesting the AXFR is present as a NS in this zone
        boolean axfrAllowed = false;

        Iterator<?> nsIterator = zone.getNS().rrs();

        while (nsIterator.hasNext()) {

            NSRecord record = (NSRecord) nsIterator.next();

            try {
                String nsIP = DnsUtils.getByName(record.getTarget().toString()).getHostAddress();

                if (socket.getInetAddress().getHostAddress().equals(nsIP)) {

                    axfrAllowed = true;
                    break;
                }

            } catch (UnknownHostException e) {

                LOGGER.warn("Unable to resolve hostname of nameserver " + record.getTarget() + " in zone "
                        + zone.getOrigin() + " while processing AXFR request from " + socket.getRemoteSocketAddress());
            }
        }

        if (!axfrAllowed) {
            LOGGER.warn("AXFR request of zone " + zone.getOrigin() + " from " + socket.getRemoteSocketAddress()
                    + " refused!");
            return EagleDNS.errorMessage(query, Rcode.REFUSED);
        }

        Iterator<?> it = zone.AXFR();

        try {
            DataOutputStream dataOut;
            dataOut = new DataOutputStream(socket.getOutputStream());
            int id = query.getHeader().getID();
            while (it.hasNext()) {
                RRset rrset = (RRset) it.next();
                Message response = new Message(id);
                Header header = response.getHeader();
                header.setFlag(Flags.QR);
                header.setFlag(Flags.AA);
                addRRset(rrset.getName(), response, rrset, Section.ANSWER, EagleDNS.FLAG_DNSSECOK);
                if (tsig != null) {
                    tsig.applyStream(response, qtsig, first);
                    qtsig = response.getTSIG();
                }
                first = false;
                byte[] out = response.toWire();
                dataOut.writeShort(out.length);
                dataOut.write(out);
            }
        } catch (IOException ex) {
            LOGGER.warn("AXFR failed", ex);
        } finally {
            SocketUtils.closeSocket(socket);
        }

        return null;
    }

    private void addSOA(Message response, Zone zone) {

        response.addRecord(zone.getSOA(), Section.AUTHORITY);
    }

    private void addNS(Message response, Zone zone, int flags) {

        RRset nsRecords = zone.getNS();
        addRRset(nsRecords.getName(), response, nsRecords, Section.AUTHORITY, flags);
    }

    private void addGlue(Message response, Name name, int flags) {

        RRset a = findExactMatch(name, Type.A, DClass.IN, true);
        if (a == null) {
            return;
        }
        addRRset(name, response, a, Section.ADDITIONAL, flags);
    }

    private void addAdditional2(Message response, int section, int flags) {

        Record[] records = response.getSectionArray(section);
        for (Record r : records) {
            Name glueName = r.getAdditionalName();
            if (glueName != null) {
                addGlue(response, glueName, flags);
            }
        }
    }

    private RRset findExactMatch(Name name, int type, int dclass, boolean glue) {

        Zone zone = findBestZone(name);

        if (zone != null) {
            return zone.findExactMatch(name, type);
        }

        return null;
    }

    private void addRRset(Name name, Message response, RRset rrset, int section, int flags) {

        for (int s = 1; s <= section; s++) {
            if (response.findRRset(name, rrset.getType(), s)) {
                return;
            }
        }
        if ((flags & EagleDNS.FLAG_SIGONLY) == 0) {
            Iterator<?> it = rrset.rrs();
            while (it.hasNext()) {
                Record r = (Record) it.next();
                if (r.getName().isWild() && !name.isWild()) {
                    r = r.withName(name);
                }
                response.addRecord(r, section);
            }
        }
        if ((flags & (EagleDNS.FLAG_SIGONLY | EagleDNS.FLAG_DNSSECOK)) != 0) {
            Iterator<?> it = rrset.sigs();
            while (it.hasNext()) {
                Record r = (Record) it.next();
                if (r.getName().isWild() && !name.isWild()) {
                    r = r.withName(name);
                }
                response.addRecord(r, section);
            }
        }
    }

    private Zone findBestZone(Name name) {
        Zone foundzone = systemInterface.getZone(name);

        if (foundzone != null) {
            return foundzone;
        }

        int labels = name.labels();

        for (int i = 1; i < labels; i++) {

            Name tname = new Name(name, i);
            foundzone = systemInterface.getZone(tname);

            if (foundzone != null) {
                return foundzone;
            }
        }

        return null;
    }

    @Override
    public void init(final String ignored) throws Exception {
        super.init(ignored);

        LOGGER.trace("Initializing the round robin resolver");

        if (null != logFilePathName) {
            final Path logFilePath = Paths.get(logFilePathName);
            try {
                this.logFileWriter = Files.newBufferedWriter(logFilePath, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);

                // writer header
                this.logFileWriter.write("timestamp,clientAddress,name_to_resolve,resolved_name");
                this.logFileWriter.newLine();
            } catch (final IOException e) {
                LOGGER.error("The specified log file '{}' cannot be written to: {}", this.logFilePathName,
                        e.getMessage(), e);
                this.logFileWriter = null;
            }
        }

        startWeightedRecordListener();
    }

    @Override
    public void shutdown() {
        stopWeightedRecordListener();

        if (null != this.logFileWriter) {
            try {
                flushLogs();
                this.logFileWriter.close();
            } catch (final IOException e) {
                LOGGER.error("Error closing logfile '{}': {}", this.logFilePathName, e.getMessage(), e);
            }
        }
    }

    /**
     * Flush the log file to disk.
     * 
     * @throws IOException
     *             if there is a problem flushing the log writer
     */
    public void flushLogs() throws IOException {
        if (null != this.logFileWriter) {
            this.logFileWriter.flush();
        }
    }

    private WeightedRecordMessageServer messageServer = null;

    private void startWeightedRecordListener() {
        stopWeightedRecordListener();

        LOGGER.trace("Starting the message server");

        messageServer = new WeightedRecordMessageServer(this);
        messageServer.start();
    }

    private void stopWeightedRecordListener() {
        if (null != messageServer) {
            messageServer.shutdown();
            messageServer = null;
        }
    }

    /**
     * Set the list of weighted records. This merges changes in the current
     * records list.
     * 
     * @param message
     *            the new weighted record information
     * @throws RuntimeException
     *             if one of the CNAME names is not a valid DNS name
     */
    public void setWeightedRecords(final RecordUpdateMessage message) throws RuntimeException {
        final Map<Name, WeightedRecordList> newWeightedRecords = new HashMap<>();
        toWeightedRecordList(message).forEach(Errors.rethrow().wrap(r -> {
            final String nameStr = r.getName();
            // add '.' to make absolute
            final Name name = Name.fromString(nameStr + ".");

            newWeightedRecords.merge(name, r, WeightedRoundRobinResolver::mergeWeightedRecordList);
        }));

        synchronized (lock) {
            // remove names no longer in DNS
            final List<Name> toRemove = weightedRecords.entrySet().stream().map(Map.Entry::getKey)
                    .filter(newWeightedRecords::containsKey).collect(Collectors.toList());
            toRemove.forEach(weightedRecords::remove);

            newWeightedRecords.forEach((n, list) -> {
                final WeightedRecordList existing = weightedRecords.get(n);
                if (null != existing) {
                    // update
                    existing.updateRecords(list);
                } else {
                    // add
                    weightedRecords.put(n, list);
                }
            });
            LOGGER.trace("updated records: {}", weightedRecords);
        }
    }

    private static Collection<WeightedRecordList> toWeightedRecordList(final RecordUpdateMessage message) {

        final Collection<WeightedRecordList> recordLists = message.getAliasMessages().stream().map(aliasMessage -> {
            final String name = aliasMessage.getHostname();
            final int ttl = message.getTtl();

            final List<WeightedCNAMERecord> records = aliasMessage.getResolutionTargets().stream()
                    .map(resolutionTarget -> {
                        return new WeightedCNAMERecord(name, resolutionTarget.getTarget(), ttl,
                                resolutionTarget.getWeight());
                    }).collect(Collectors.toList());

            return new WeightedRecordList(name, records);
        }).collect(Collectors.toList());
        return recordLists;
    }

    private static WeightedRecordList mergeWeightedRecordList(final WeightedRecordList one,
            final WeightedRecordList two) {
        if (!one.getName().equals(two.getName())) {
            throw new IllegalArgumentException(
                    "When merging record lists the names must match " + one.getName() + " != " + two.getName());
        }

        final List<WeightedCNAMERecord> mergedRecords = new LinkedList<>();
        one.foreachRecord((record, weight) -> {
            mergedRecords.add(record);
        });
        two.foreachRecord((record, weight) -> {
            mergedRecords.add(record);
        });

        final WeightedRecordList retval = new WeightedRecordList(one.getName(), mergedRecords);
        return retval;
    }

    private static final class ResolverCache extends ThreadLocal<Map<String, SimpleResolver>> {
        private static final ResolverCache INSTANCE = new ResolverCache();

        @Override
        protected Map<String, SimpleResolver> initialValue() {
            return new HashMap<>();
        }
    }

    private ThreadLocal<Map<String, SimpleResolver>> resolverCache = ResolverCache.INSTANCE;

}
