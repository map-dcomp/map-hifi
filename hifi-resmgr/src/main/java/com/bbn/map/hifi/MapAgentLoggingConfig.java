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
package com.bbn.map.hifi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Objects;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;

/**
 * Custom logging configuration for MAP hifi agent that reads
 * map-hifi.logging.xml from the current directory if found. If not found then
 * uses default.logging.xml from inside the jar file.
 * 
 * @author jschewe
 *
 */
@Plugin(name = "MapAgentLoggingConfig", category = ConfigurationFactory.CATEGORY)
// CHECKSTYLE:OFF can't name constant
@Order(20)
// CHECKSTYLE:ON
public class MapAgentLoggingConfig extends ConfigurationFactory {

    static Configuration createConfiguration(final String name,
            final ConfigurationBuilder<BuiltConfiguration> builder) {

        builder.setConfigurationName(name);
        builder.setStatusLevel(Level.ERROR);
        builder.add(builder.newFilter("ThresholdFilter", Filter.Result.ACCEPT, Filter.Result.NEUTRAL)
                .addAttribute("level", Level.DEBUG));
        AppenderComponentBuilder appenderBuilder = builder.newAppender("Stdout", "CONSOLE").addAttribute("target",
                ConsoleAppender.Target.SYSTEM_OUT);
        appenderBuilder
                .add(builder.newLayout("PatternLayout").addAttribute("pattern", "%d{yyyy-MM-dd/HH:mm:ss.SSS/Z} [%t] %-5level: %msg%n%throwable"));
        appenderBuilder.add(builder.newFilter("MarkerFilter", Filter.Result.DENY, Filter.Result.NEUTRAL)
                .addAttribute("marker", "FLOW"));
        builder.add(appenderBuilder);
        builder.add(builder.newLogger("org.apache.logging.log4j", Level.DEBUG).add(builder.newAppenderRef("Stdout"))
                .addAttribute("additivity", false));
        builder.add(builder.newRootLogger(Level.ERROR).add(builder.newAppenderRef("Stdout")));

        return builder.build();
    }

    private static Configuration mapConfiguration(final LoggerContext loggerContext) {
        final File loggingConfig = new File("map-hifi.logging.xml");

        if (loggingConfig.exists()) {
            System.err.println("Using logging config at '" + loggingConfig.getAbsolutePath() + "'.");

            try (InputStream stream = new FileInputStream(loggingConfig)) {
                final ConfigurationSource configSource = new ConfigurationSource(stream, loggingConfig);
                return new XmlConfiguration(loggerContext, configSource);
            } catch (final IOException e) {
                throw new RuntimeException("Error closing logging configuration stream", e);
            }
        } else {
            System.err.println("Cannot find logging configuration at '" + loggingConfig.getAbsolutePath()
                    + "', using internal configuration.");

            final URL url = MapAgentLoggingConfig.class.getResource("default.logging.xml");
            Objects.requireNonNull(url, "Unable to find default.logging.xml");
            try (InputStream stream = url.openStream()) {
                final ConfigurationSource configSource = new ConfigurationSource(stream, url);
                return new XmlConfiguration(loggerContext, configSource);
            } catch (final IOException e) {
                throw new RuntimeException("Error closing logging configuration stream", e);
            }
        }

    }

    @Override
    public Configuration getConfiguration(final LoggerContext loggerContext, final ConfigurationSource source) {
        // return getConfiguration(loggerContext, source.toString(), null);
        return mapConfiguration(loggerContext);
    }

    @Override
    public Configuration getConfiguration(final LoggerContext loggerContext,
            final String name,
            final URI configLocation) {
        return mapConfiguration(loggerContext);
        // ConfigurationBuilder<BuiltConfiguration> builder =
        // newConfigurationBuilder();
        // return createConfiguration(name, builder);
    }

    @Override
    protected String[] getSupportedTypes() {
        return new String[] { "*" };
    }
}
