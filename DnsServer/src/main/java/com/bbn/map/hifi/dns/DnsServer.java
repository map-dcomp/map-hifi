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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AgentConfiguration;
import com.bbn.map.hifi.util.DnsUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DNS server for MAP that supports weighted round robin of servers.
 * 
 * @author jschewe
 *
 */
public final class DnsServer {
    // put this first to ensure that the correct logging configuration is used
    static {
        System.setProperty("log4j.configurationFactory", MapDnsLoggingConfig.class.getName());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DnsServer.class);
    /**
     * File to read the agent configuration from.
     */
    public static final String AGENT_CONFIGURATION_FILENAME = "agent-configuration.json";
    /**
     * Default directory to find configuration files in.
     */
    public static final String DEFAULT_CONFIGURATION_DIRECTORY = "/etc/map";

    private DnsServer() {
    }

    private static final String SHUTDOWN_OPT = "shutdown";
    private static final String CONFIG_OPT = "config";
    private static final String FLUSH_LOGS_OPT = "flush-logs";

    /**
     * Start the DNS server using "conf/config.xml" for the configuration.
     * 
     * @param args
     *            --shutdown to stop the current running service, --config to
     *            specify the configuration file
     */
    public static void main(final String[] args) {
        LOGGER.info("DNS server built from git version {}", getGitVersionInformation());

        DnsUtils.configureDnsCache();

        final Options options = new Options();
        options.addOption(null, SHUTDOWN_OPT, false, "Shutdown the server running on localhost");
        options.addOption(null, CONFIG_OPT, true, "Configuration file (default: conf/config.xml)");
        options.addOption(null, FLUSH_LOGS_OPT, false, "Flush the weighted DNS logs to disk");

        // DnsServer runs on a node that has an agent, so the agent
        // configuration file will be present
        final Path configurationDirectory = Paths.get(DEFAULT_CONFIGURATION_DIRECTORY);
        final Path agentConfigFile = configurationDirectory.resolve(DnsServer.AGENT_CONFIGURATION_FILENAME);
        if (Files.exists(agentConfigFile)) {
            try {
                AgentConfiguration.readFromFile(agentConfigFile);
            } catch (final IOException e) {
                LOGGER.error("Got error reading agent configuration, using defaults", e);
            }
        } else {
            LOGGER.warn("Cannot find agent configuration in {}, using defaults", agentConfigFile);
        }

        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine cmd = parser.parse(options, args);

            String configFile = "conf/config.xml";

            if (cmd.hasOption(CONFIG_OPT)) {
                configFile = cmd.getOptionValue(CONFIG_OPT);
            }

            if (cmd.hasOption(SHUTDOWN_OPT)) {
                se.unlogic.eagledns.plugins.remotemanagement.EagleManagerClient
                        .main(new String[] { configFile, "localhost", "shutdown" });
            } else if (cmd.hasOption(FLUSH_LOGS_OPT)) {
                flushLogs();
            } else {
                System.err
                        .println("Standard out and standard error will be closed, watch the log file for all messages");
                se.unlogic.eagledns.EagleDNS.main(new String[] { configFile });
                LOGGER.debug("EagleDNS.main has exited");
            }

        } catch (final ParseException e) {
            LOGGER.error("Error parsing the command line: {}", e.getMessage());
            new HelpFormatter().printHelp("SimulationRunner", options);
            System.exit(1);
        }
    }

    private static void flushLogs() {
        try (Socket socket = new Socket("localhost", WeightedRecordMessageServer.PORT);
                Writer writer = new OutputStreamWriter(socket.getOutputStream(), Charset.defaultCharset())) {

            final ObjectMapper jsonMapper = new ObjectMapper().disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)
                    .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

            jsonMapper.writeValue(writer, WeightedRecordMessageServer.FLUSH_COMMAND);
        } catch (final IOException e) {
            LOGGER.error("Error flushing logs", e);
        }

    }

    /**
     * Get the git version information for the build.
     * 
     * @return the version or a string stating what the problem was
     */
    public static String getGitVersionInformation() {
        final URL url = DnsServer.class.getResource("git.properties");
        if (null == url) {
            return "UNKNOWN";
        }
        try (InputStream is = url.openStream()) {
            final Properties props = new Properties();
            props.load(is);
            return props.getProperty("git.commit.id", "MISSING-PROPERTY");
        } catch (final IOException e) {
            LOGGER.error("Unable to read version properties", e);
            return "ERROR-READING-VERSION";
        }
    }

}
