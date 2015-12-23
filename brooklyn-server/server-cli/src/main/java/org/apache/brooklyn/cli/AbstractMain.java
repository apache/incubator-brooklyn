/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.cli;

import io.airlift.command.Arguments;
import io.airlift.command.Cli;
import io.airlift.command.Cli.CliBuilder;
import io.airlift.command.Command;
import io.airlift.command.Help;
import io.airlift.command.Option;
import io.airlift.command.OptionType;
import io.airlift.command.ParseException;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.cli.Main.LaunchCommand;
import org.apache.brooklyn.core.BrooklynVersion;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import org.apache.brooklyn.util.exceptions.FatalRuntimeException;
import org.apache.brooklyn.util.exceptions.UserFacingException;
import org.apache.brooklyn.util.text.KeyValueParser;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;

/**
 * This class is the primary CLI for brooklyn.
 * Run with the `help` argument for help.
 * <p>
 * This class is designed for subclassing, with subclasses typically:
 * <li> providing their own static {@link #main(String...)} (of course) which need simply invoke 
 *      {@link #execCli(String[])} with the arguments 
 * <li> returning their CLI name (e.g. "start.sh") in an overridden {@link #cliScriptName()}
 * <li> providing an overridden {@link LaunchCommand} via {@link #cliLaunchCommand()} if desired
 * <li> providing any other CLI customisations by overriding {@link #cliBuilder()}
 *      (typically calling the parent and then customizing the builder)
 * <li> populating a custom catalog using {@link LaunchCommand#populateCatalog(BrooklynCatalog)}
 */
public abstract class AbstractMain {

    private static final Logger log = LoggerFactory.getLogger(AbstractMain.class);

    // Launch banner
    public static final String DEFAULT_BANNER =
        " _                     _    _             \n" +
        "| |__  _ __ ___   ___ | | _| |_   _ _ __ (R)\n" +
        "| '_ \\| '__/ _ \\ / _ \\| |/ / | | | | '_ \\ \n" +
        "| |_) | | | (_) | (_) |   <| | |_| | | | |\n" +
        "|_.__/|_|  \\___/ \\___/|_|\\_\\_|\\__, |_| |_|\n" +
        "                              |___/             "+BrooklynVersion.get()+"\n";

    // Error codes
    public static final int SUCCESS = 0;
    public static final int PARSE_ERROR = 1;
    public static final int EXECUTION_ERROR = 2;
    public static final int CONFIGURATION_ERROR = 3;

    /**
     * Field intended for sub-classes (with their own {@code main()}) to customize the banner.
     * All accesses to the banner are done through this field, to ensure consistent customization.
     * 
     * Note that a {@code getBanner()} method is not an option for supporting this, because
     * it is accessed from static inner-classes (such as {@link InfoCommand}, so non-static
     * methods are not an option (and one can't override static methods).
     */
    protected static volatile String banner = DEFAULT_BANNER;
    
    /** abstract superclass for commands defining global options, but not arguments,
     * as that prevents Help from being injectable in the {@link HelpCommand} subclass */
    public static abstract class BrooklynCommand implements Callable<Void> {

        @Option(type = OptionType.GLOBAL, name = { "-v", "--verbose" }, description = "Verbose mode")
        public boolean verbose = false;

        @Option(type = OptionType.GLOBAL, name = { "-q", "--quiet" }, description = "Quiet mode")
        public boolean quiet = false;

        @VisibleForTesting
        protected PrintStream stdout = System.out;
        
        @VisibleForTesting
        protected PrintStream stderr = System.err;

        @VisibleForTesting
        protected InputStream stdin = System.in;

        public ToStringHelper string() {
            return Objects.toStringHelper(getClass())
                    .add("verbose", verbose)
                    .add("quiet", quiet);
        }
        
        @Override
        public String toString() {
            return string().toString();
        }
    }
    
    /** common superclass for commands, defining global options (in our super) and extracting the arguments */
    public static abstract class BrooklynCommandCollectingArgs extends BrooklynCommand {

        /** extra arguments */
        @Arguments
        public List<String> arguments = new ArrayList<String>();
        
        /** @return true iff there are arguments; it also sys.errs a warning in that case  */
        protected boolean warnIfArguments() {
            if (arguments.isEmpty()) return false;
            stderr.println("Invalid subcommand arguments: "+Strings.join(arguments, " "));
            return true;
        }
        
        /** throw {@link ParseException} iff there are arguments */
        protected void failIfArguments() {
            if (arguments.isEmpty()) return ;
            throw new ParseException("Invalid subcommand arguments '"+Strings.join(arguments, " ")+"'");
        }
        
        @Override
        public ToStringHelper string() {
            return super.string()
                    .add("arguments", arguments);
        }
    }
    
    /** superclass which reads `-D` system property definitions and applies them
     * <p>
     * useful when scripting, e.g. where brooklyn.sh encodes `java o.a.b.Main "$@"` 
     * but we want the caller to be able to pass system properties
     */
    public static abstract class BrooklynCommandWithSystemDefines extends BrooklynCommandCollectingArgs {
        @Option(type = OptionType.GLOBAL, name = { "-D" }, description = "Set java system property")
        public List<String> defines1;

        @Option(name = { "-D" }, description = "Set java system property")
        public List<String> defines2;

        public List<String> getDefines() { return MutableList.copyOf(defines1).appendAll(defines2); }
        public Map<String,String> getDefinesAsMap() { return KeyValueParser.parseMap(Strings.join(getDefines(),",")); }
        public void applyDefinesAsSystemProperties() { System.getProperties().putAll(getDefinesAsMap()); }
        
        public ToStringHelper string() {
            return super.string()
                    .add("defines", getDefines());
        }
        
        @Override
        public Void call() throws Exception {
            applyDefinesAsSystemProperties();
            return null;
        }
    }

    @Command(name = "help", description = "Display help for available commands")
    public static class HelpCommand extends BrooklynCommand {

        @Inject
        public Help help;

        @Override
        public Void call() throws Exception {
            if (log.isDebugEnabled()) log.debug("Invoked help command: {}", this);
            return help.call();
        }
    }

    @Command(name = "info", description = "Display information about brooklyn")
    public static class InfoCommand extends BrooklynCommandCollectingArgs {
        
        @Override
        public Void call() throws Exception {
            if (log.isDebugEnabled()) log.debug("Invoked info command: {}", this);
            warnIfArguments();

            System.out.println(banner);
            System.out.println("Version:  " + BrooklynVersion.get());
            if (BrooklynVersion.INSTANCE.isSnapshot()) {
                System.out.println("Git SHA1: " + BrooklynVersion.INSTANCE.getSha1FromOsgiManifest());
            }
            System.out.println("Website:  http://brooklyn.incubator.apache.org");
            System.out.println("Source:   https://github.com/apache/incubator-brooklyn");
            System.out.println();
            System.out.println("Copyright 2011-2015 The Apache Software Foundation.");
            System.out.println("Licensed under the Apache 2.0 License");
            System.out.println();

            return null;
        }
    }

    public static class DefaultInfoCommand extends InfoCommand {
        @Override
        public Void call() throws Exception {
            super.call();
            System.out.println("ERROR: No command specified.");
            System.out.println();
            throw new ParseException("No command specified.");
        }
    }

    /** method intended for overriding when the script filename is different 
     * @return the name of the script the user has invoked */
    protected String cliScriptName() {
        return "brooklyn";
    }

    /** 
     * Build the commands.
     */
    protected abstract CliBuilder<BrooklynCommand> cliBuilder();
    
    protected void execCli(String ...args) {
        execCli(cliBuilder().build(), args);
    }
    
    protected void execCli(Cli<BrooklynCommand> parser, String ...args) {
        try {
            log.debug("Parsing command line arguments: {}", Arrays.asList(args));
            BrooklynCommand command = parser.parse(args);
            log.debug("Executing command: {}", command);
            command.call();
            System.exit(SUCCESS);
        } catch (ParseException pe) { // looks like the user typed it wrong
            System.err.println("Parse error: " + pe.getMessage()); // display
                                                                   // error
            System.err.println(getUsageInfo(parser)); // display cli help
            System.exit(PARSE_ERROR);
        } catch (FatalConfigurationRuntimeException e) {
            log.error("Configuration error: "+e.getMessage(), e.getCause());
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(CONFIGURATION_ERROR);
        } catch (FatalRuntimeException e) { // anticipated non-configuration error
            log.error("Startup error: "+e.getMessage(), e.getCause());
            System.err.println("Startup error: "+e.getMessage());
            System.exit(EXECUTION_ERROR);
        } catch (Exception e) { // unexpected error during command execution
            log.error("Execution error: " + e.getMessage(), e);
            System.err.println("Execution error: " + e.getMessage());
            if (!(e instanceof UserFacingException))
                e.printStackTrace();
            System.exit(EXECUTION_ERROR);
        }
    }

    protected String getUsageInfo(Cli<BrooklynCommand> parser) {
        StringBuilder help = new StringBuilder();
        help.append("\n");
        Help.help(parser.getMetadata(), Collections.<String>emptyList(), help);
        return help.toString();
    }

}
