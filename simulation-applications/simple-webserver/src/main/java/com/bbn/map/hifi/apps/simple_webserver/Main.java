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
package com.bbn.map.hifi.apps.simple_webserver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;

import com.bbn.map.hifi.util.ActiveConnectionCountWriter;
import com.bbn.map.hifi.util.DnsUtils;
import com.bbn.map.utils.LogExceptionHandler;

/**
 * Simple web server that receives a JSON payload of random characters and
 * responds based on the payload.
 * 
 * @author jschewe
 *
 */
public final class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    private Main() {
    }

    private static final String CSV_OPT = "csv";
    private static final String HELP_OPT = "help";
    private static final String RESPONSE_TYPE_OPT = "responseType";

    /**
     * Print out the usage information. Does not exit, that is up to the caller.
     * 
     * @param options
     *            the options for the command line parser
     */
    private static void printUsage(final Options options) {
        new HelpFormatter().printHelp(Main.class.getSimpleName(), options);
    }

    private static final int IDLE_TIMEOUT_MINUTES = 60;
    private static final int HTTP_PORT = 8080;

    private static final AtomicInteger ACTIVE_CONNECTION_COUNT = new AtomicInteger(0);

    /**
     * Run a simple webserver that sends back random strings to requests.
     * 
     * @param args
     *            see "--help"
     * 
     * @throws Exception
     *             if an error occurs in the Jetty server
     */
    public static void main(final String[] args) throws Exception {
        LogExceptionHandler.registerExceptionHandler();

        DnsUtils.configureDnsCache();

        LOGGER.info("Built from git revision {}", getGitVersionInformation());

        final Options options = new Options();
        options.addRequiredOption(null, CSV_OPT, true, "Where to write the latency files (required)");
        options.addRequiredOption(null, RESPONSE_TYPE_OPT, true, "Response type 'small' or 'large' (required)");

        options.addOption("h", HELP_OPT, false, "Show the help");

        final CommandLineParser parser = new DefaultParser();

        try {
            final CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption(HELP_OPT)) {
                printUsage(options);
                System.exit(0);
            }

            final Path csvOutput = Paths.get(cmd.getOptionValue(CSV_OPT));
            final String responseType = cmd.getOptionValue(RESPONSE_TYPE_OPT);

            final LatencyLogWriter latencyWriter = new LatencyLogWriter(csvOutput);
            // don't stop exit based on this thread
            latencyWriter.setDaemon(true);
            latencyWriter.start();

            final Server server = new Server();
            final ServerConnector http = new ServerConnector(server);
            http.setPort(HTTP_PORT);
            http.setIdleTimeout(Duration.ofMinutes(IDLE_TIMEOUT_MINUTES).toMillis());
            server.addConnector(http);

            final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");
            context.setResourceBase(System.getProperty("java.io.tmpdir"));
            server.setHandler(context);

            server.setHandler(context);

            if ("large".equalsIgnoreCase(responseType)) {
                context.addServlet(RandomStringServlet.class, "/*");
            } else if ("small".equalsIgnoreCase(responseType)) {
                context.addServlet(SmallResponseServlet.class, "/*");
            } else if ("database".equalsIgnoreCase(responseType)) {
                context.addServlet(DatabaseServlet.class, "/*");
            } else {
                throw new IllegalArgumentException(
                        "Response type must be 'small' or 'large' or 'database', but was '" + responseType + "'");
            }

            context.getServletContext().setAttribute(RandomStringServlet.LATENCY_LOG_WRITER, latencyWriter);

            final ActiveConnectionCountWriter countWriter = new ActiveConnectionCountWriter(ACTIVE_CONNECTION_COUNT);
            countWriter.start();

            server.start();
            server.join();

            countWriter.stopWriting();

        } catch (final ParseException e) {
            LOGGER.error("Error parsing the command line: {}", e.getMessage());
            printUsage(options);
            System.exit(1);
        }

    }

    /**
     * Increment the count of active connections.
     */
    public static void incrementActiveConnectionCount() {
        ACTIVE_CONNECTION_COUNT.incrementAndGet();
    }

    /**
     * Decrement the count of active connections.
     */
    public static void decrementActiveConnectionCount() {
        ACTIVE_CONNECTION_COUNT.decrementAndGet();
    }

    /**
     * Get the git version information for the build.
     * 
     * @return the version or a string stating what the problem was
     */
    public static String getGitVersionInformation() {
        final URL url = Main.class.getResource("git.properties");
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
