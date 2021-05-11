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
package com.bbn.map.hifi.clientprestart;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.hifi.client.ClientDriver;
import com.bbn.map.hifi.client.ClientServiceConfiguration;
import com.bbn.map.hifi.client.ClientServiceConfiguration.ExecutionType;
import com.bbn.map.hifi.util.ProcessUtils;
import com.bbn.map.simulator.ClientLoad;
import com.bbn.map.utils.LogExceptionHandler;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Execute pre-start commands for clients.
 * 
 * @author jschewe
 *
 */
public final class ExecutePrestartCommands {

    // put this first to ensure that the correct logging configuration is used
    static {
        System.setProperty("log4j.configurationFactory", MapClientPreStartLoggingConfig.class.getName());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutePrestartCommands.class);

    private static final String CONFIGURATION_DIRECTORY_OPT = "configuration-directory";
    private static final String HELP_OPT = "help";
    private static final String VERSION_OPT = "version";

    /**
     * Print out the usage information. Does not exit, that is up to the caller.
     * 
     * @param options
     *            the options for the command line parser
     */
    private static void printUsage(final Options options) {
        new HelpFormatter().printHelp("ExecutePrestartCommands", options);
    }

    /**
     * 
     * @param args
     *            See --help for all options.
     */
    public static void main(final String[] args) {
        LogExceptionHandler.registerExceptionHandler();

        final Options options = new Options();
        options.addOption(null, CONFIGURATION_DIRECTORY_OPT, true,
                "The directory to read the configuration from (default: " + ClientDriver.DEFAULT_CONFIGURATION_DIRECTORY
                        + ")");
        options.addOption("h", HELP_OPT, false, "Show the help");
        options.addOption("v", VERSION_OPT, false, "Display the version");

        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption(HELP_OPT)) {
                printUsage(options);
                System.exit(0);
            } else if (cmd.hasOption(VERSION_OPT)) {
                outputVersionInformation();
                System.exit(0);
            }

            final Path configurationDirectory;
            if (cmd.hasOption(CONFIGURATION_DIRECTORY_OPT)) {
                final String configDirectoryStr = cmd.getOptionValue(CONFIGURATION_DIRECTORY_OPT);
                configurationDirectory = Paths.get(configDirectoryStr);
            } else {
                configurationDirectory = Paths.get(ClientDriver.DEFAULT_CONFIGURATION_DIRECTORY);
            }

            try {
                final ClientDriver.Parameters parameters = ClientDriver
                        .parseConfigurationDirectory(configurationDirectory);
                if (null == parameters) {
                    System.exit(1);
                }

                final ExecutePrestartCommands driver = new ExecutePrestartCommands(parameters);
                outputVersionInformation();

                driver.executePreStartCommands(parameters.dockerRegistryHostname);

                LOGGER.info("Exiting...");
                System.exit(0);
            } catch (final IOException e) {
                LOGGER.error("Error parsing configuration files: {}", e.getMessage(), e);
                System.exit(1);
            }

        } catch (final ParseException e) {
            LOGGER.error("Error parsing the command line: {}", e.getMessage());
            new HelpFormatter().printHelp("SimulationRunner", options);
            System.exit(1);
        }
    }

    private final ImmutableList<ClientLoad> clientRequests;
    /**
     * Services that this client will execute.
     */
    private final Set<ApplicationCoordinates> servicesToExecute;

    private final ImmutableMap<ApplicationCoordinates, ClientServiceConfiguration> clientServiceConfigs;

    /**
     * 
     * @param parameters
     *            information about configuring the driver
     * @throws IOException
     *             if there is an error parsing the configuration files
     */
    private ExecutePrestartCommands(final ClientDriver.Parameters parameters) throws IOException {
        this.clientRequests = ClientLoad.parseClientDemand(parameters.demandFile);
        this.clientServiceConfigs = ClientServiceConfiguration
                .parseClientServiceConfigurations(parameters.clientServiceConfigurationFile);

        this.servicesToExecute = this.clientRequests.stream().map(r -> r.getService()).collect(Collectors.toSet());

        LOGGER.trace("Loaded client load requests: {}", this.clientRequests);
        LOGGER.trace("Loadded client service configurations: {}", this.clientServiceConfigs);
    }

    private void executePreStartCommands(final String registryHostname) {
        clientServiceConfigs.forEach((service, serviceConfig) -> {
            if (servicesToExecute.contains(service)) {
                if (null != serviceConfig.getPreStartType()) {
                    if (ExecutionType.EXTERNAL_PROCESS == serviceConfig.getPreStartType()) {
                        LOGGER.info("Executing pre start command {} for service ", serviceConfig.getPreStartArguments(),
                                service);

                        final ProcessBuilder builder = new ProcessBuilder(serviceConfig.getPreStartArguments());
                        builder.environment().put("REGISTRY_HOSTNAME", registryHostname);

                        try {
                            final Process process = builder.start();

                            new Thread(
                                    () -> ProcessUtils.logProcessStream(LOGGER, process.getInputStream(),
                                            "standard output", LOGGER::info, () -> false),
                                    LOGGER.getName() + " standard output handler").start();

                            new Thread(
                                    () -> ProcessUtils.logProcessStream(LOGGER, process.getErrorStream(),
                                            "standard error", LOGGER::info, () -> false),
                                    LOGGER.getName() + " error output handler").start();

                            process.waitFor();
                        } catch (final IOException e) {
                            LOGGER.error("Unable to execute pre-start command for service {}", service, e);
                        } catch (final InterruptedException e) {
                            LOGGER.error("Got interrupted executing pre-start command for service {}", service, e);
                        }

                        LOGGER.info("Finished executing pre start command for service {}", service);
                    } else if (ExecutionType.JAVA_CLASS == serviceConfig.getPreStartType()) {
                        LOGGER.error(
                                "Executing a Java Class as the pre-start command is not currently supported. If this is needed it can be implemented.");
                    } else {
                        throw new IllegalArgumentException("Unknown pre start type " + serviceConfig.getPreStartType()
                                + " for service " + service);
                    }
                } // non-null pre-start
            }
        });
    }

    private static void outputVersionInformation() {
        LOGGER.info("Git version: {}", getGitVersionInformation());
    }

    /**
     * Get the git version information for the build.
     * 
     * @return the version or a string stating what the problem was
     */
    public static String getGitVersionInformation() {
        final URL url = ExecutePrestartCommands.class.getResource("git.properties");
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
