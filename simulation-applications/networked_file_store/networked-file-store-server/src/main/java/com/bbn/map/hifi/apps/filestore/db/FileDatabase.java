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
package com.bbn.map.hifi.apps.filestore.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedList;

import com.bbn.map.hifi.apps.filestore.protocol.Acknowledgement;
import com.bbn.map.hifi.apps.filestore.protocol.FileQueryOperation;
import com.bbn.map.hifi.apps.filestore.protocol.FileQueryResultOperation;
import com.bbn.map.hifi.apps.filestore.protocol.FileStoreOperation;

public class FileDatabase {

    String urlBase = null;
    Connection conn = null;
    PreparedStatement psInsert = null;
    PreparedStatement psQueryName = null;
    PreparedStatement psQueryTimeGT = null;
    PreparedStatement psQueryTimeLT = null;

    public FileDatabase(String dbLocation) throws Exception {
        createConnection(dbLocation);
        initStore();
    }

    private void createConnection(String dbLocation) {
        try {
            urlBase = "jdbc:derby:" + dbLocation;
            String url = urlBase + ";create=true;";
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver").newInstance();
            conn = DriverManager.getConnection(url);
        } catch (Exception except) {
            except.printStackTrace();
        }
    }

    private void initStore() throws Exception {
        boolean gotit = false;
        DatabaseMetaData md = conn.getMetaData();
        ResultSet rs = md.getTables(null, null, "%", null);
        while (rs.next()) {
            if (((String) rs.getString(3)).equalsIgnoreCase("filestore")) {
                gotit = true;
                break;
            }
        }

        if (!gotit) {

            // Note: this does not actually store the real file.
            Statement st = conn.createStatement();
            st.executeUpdate("CREATE TABLE filestore ("
                    + " index bigint not null GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1) primary key, "
                    + " publisher varchar(256), " + " timestamp integer, " + " name varchar(256), "
                    + " metadata varchar(1024), " + " size integer" + ")");
        }

        psInsert = conn.prepareStatement(
                "insert into filestore(publisher, timestamp, name, metadata, size) values(?,?,?,?,?)");

        String pre = "filestore.publisher, filestore.timestamp, filestore.name, filestore.metadata, filestore.size";

        // some example queries
        psQueryName = conn.prepareStatement("select " + pre + " from filestore where filestore.publisher = (?)");
        psQueryTimeGT = conn.prepareStatement("select " + pre + " from filestore where filestore.timestamp > (?)");
        psQueryTimeLT = conn.prepareStatement("select " + pre + " from filestore where filestore.timestamp <= (?)");
    }

    public void storeFileStoreObject(FileStoreOperation obj) {
        String producerName = obj.getProducerName();
        long ts = obj.getTimestamp();
        String filename = obj.getFilename();
        String filemetadata = obj.getFileMetadata();
        byte[] d = obj.getFiledata();
        int size = 0;
        if (d != null) {
            size = d.length;
        }
        try {

            psInsert.setString(1, producerName);
            psInsert.setInt(2, (int) ts);
            psInsert.setString(3, filename);
            psInsert.setString(4, filemetadata);
            psInsert.setInt(5, size);

            psInsert.executeUpdate();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public Object queryFileStoreObject(FileQueryOperation fqo) {
        int matchCount = 0;
        int matchSize = 0;

        if (!fqo.isValid()) {
            return new Acknowledgement(Acknowledgement.Type.MAPNACK, System.currentTimeMillis());
        }

        try {
            ResultSet rs = null;

            if (fqo.getType() == FileQueryOperation.QueryType.BY_PRODUCER_NAME) {
                psQueryName.setString(1, fqo.getProducerName());
                rs = psQueryName.executeQuery();
            } else if (fqo.getType() == FileQueryOperation.QueryType.BY_TIME_GT) {
                psQueryTimeGT.setInt(1, (int) fqo.getTime());
                rs = psQueryTimeGT.executeQuery();
            } else if (fqo.getType() == FileQueryOperation.QueryType.BY_TIME_LT) {
                psQueryTimeLT.setInt(1, (int) fqo.getTime());
                rs = psQueryTimeLT.executeQuery();
            }

            LinkedList<FileStoreOperation> retObjs = new LinkedList<FileStoreOperation>();

            if (rs != null) {
                while (rs.next()) {
                    int t = rs.getInt(5);

                    matchSize += rs.getInt(5);
                    ++matchCount;

                    if (t < 0)
                        t = 1; // for non-null allocations.

                    FileStoreOperation aObj = new FileStoreOperation(rs.getString(1), rs.getInt(2), rs.getString(3),
                            new byte[t], rs.getString(4));
                    retObjs.add(aObj);
                }
            }

            FileQueryResultOperation fqro = new FileQueryResultOperation(matchCount, matchSize, retObjs);
            return fqro;
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return new Acknowledgement(Acknowledgement.Type.MAPNACK, System.currentTimeMillis());
    }

    public void close() {
        try {
            if (conn != null) {
                DriverManager.getConnection(urlBase + ";shutdown=true");
                conn.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        conn = null;
    }
}