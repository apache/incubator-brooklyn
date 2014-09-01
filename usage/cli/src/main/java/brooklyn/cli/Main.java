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
package brooklyn.cli;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import io.airlift.command.Arguments;
import io.airlift.command.Cli;
import io.airlift.command.Cli.CliBuilder;
import io.airlift.command.Command;
import io.airlift.command.Help;
import io.airlift.command.Option;
import io.airlift.command.OptionType;
import io.airlift.command.ParseException;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.BrooklynVersion;
import brooklyn.catalog.BrooklynCatalog;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.trait.Startable;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.launcher.BrooklynServerDetails;
import brooklyn.launcher.config.StopWhichAppsOnShutdown;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.rest.security.PasswordHasher;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import brooklyn.util.exceptions.FatalRuntimeException;
import brooklyn.util.exceptions.UserFacingException;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.Enums;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.StringEscapes.JavaStringEscapes;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableList;

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
public class Main {

    // Launch banner
    public static final String BANNER =
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

    /** @deprecated since 0.7.0 will become private static, subclasses should define their own logger */
    @Deprecated
    public static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String... args) {
        new Main().execCli(args);
    }

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

            System.out.println(BANNER);
            System.out.println("Version:  " + BrooklynVersion.get());
            System.out.println("Website:  http://brooklyn.incubator.apache.org");
            System.out.println("Source:   https://github.com/apache/incubator-brooklyn");
            System.out.println();
            System.out.println("Copyright 2011-2014 The Apache Software Foundation.");
            System.out.println("Licensed under the Apache 2.0 License");
            System.out.println();

            return null;
        }
    }

    @Command(name = "generate-password", description = "Generates a hashed web-console password")
    public static class GeneratePasswordCommand extends BrooklynCommandCollectingArgs {

        @Option(name = { "--user" }, title = "username", required = true)
        public String user;

        @Option(name = { "--stdin" }, title = "read password from stdin, instead of console", 
                description = "Before using stdin, read http://stackoverflow.com/a/715681/1393883 for discussion of security!")
        public boolean useStdin;

        @Override
        public Void call() throws Exception {
            checkCanReadPassword();
            
            System.out.print("Enter password: ");
            System.out.flush();
            String password = readPassword();
            if (Strings.isBlank(password)) {
                throw new UserFacingException("Password must not be blank; aborting");
            }
            
            System.out.print("Re-enter password: ");
            System.out.flush();
            String password2 = readPassword();
            if (!password.equals(password2)) {
                throw new UserFacingException("Passwords did not match; aborting");
            }

            String salt = Identifiers.makeRandomId(4);
            String sha256password = PasswordHasher.sha256(salt, new String(password));
            
            System.out.println();
            System.out.println("Please add the following to your brooklyn.properties:");
            System.out.println();
            System.out.println("brooklyn.webconsole.security.users="+user);
            System.out.println("brooklyn.webconsole.security.user."+user+".salt="+salt);
            System.out.println("brooklyn.webconsole.security.user."+user+".sha256="+sha256password);

            return null;
        }
        
        private void checkCanReadPassword() {
            if (useStdin) {
                // yes; always
            } else {
                Console console = System.console();
                if (console == null) {
                    throw new FatalConfigurationRuntimeException("No console; cannot get password securely; aborting");
                }
            }
        }
        
        private String readPassword() throws IOException {
            if (useStdin) {
                return readLine(System.in);
            } else {
                return new String(System.console().readPassword());
            }
        }
        
        private String readLine(InputStream in) throws IOException {
            StringBuilder result = new StringBuilder();
            char c;
            while ((c = (char)in.read()) != '\n') {
                result.append(c);
            }
            return result.toString();
        }
    }
    
    @Command(name = "launch", description = "Starts a server, optionally with applications")
    public static class LaunchCommand extends BrooklynCommandCollectingArgs {

        @Option(name = { "--localBrooklynProperties" }, title = "local brooklyn.properties file",
                description = "local brooklyn.properties file, specific to this launch (appending to and overriding global properties)")
        public String localBrooklynProperties;

        @Option(name = { "-a", "--app" }, title = "application class or file",
                description = "The Application to start. " +
                        "For example, my.AppName, file://my/app.yaml, or classpath://my/AppName.groovy -- "
                        + "note that a BROOKLYN_CLASSPATH environment variable may be required to "
                        + "load classes from other locations")
        public String app;

        @Beta
        @Option(name = { "-s", "--script" }, title = "script URI",
                description = "EXPERIMENTAL. URI for a Groovy script to parse and load." +
                        " This script will run before starting the app.")
        public String script = null;

        @Option(name = { "-l", "--location", "--locations" }, title = "location list",
                description = "Specifies the locations where the application will be launched. " +
                        "You can specify more than one location as a comma-separated list of values " +
                        "(or as a JSON array, if the values are complex)")
        public String locations;

        @Option(name = { "-p", "--port" }, title = "port number",
                description = "Specifies the port to be used by the Brooklyn Management Console")
        public String port = "8081+";

        @Option(name = { "-nc", "--noConsole" },
                description = "Whether to start the web console")
        public boolean noConsole = false;

        @Option(name = { "-b", "--bindAddress" },
                description = "Specifies the IP address of the NIC to bind the Brooklyn Management Console to")
        public String bindAddress = null;

        @Option(name = { "-pa", "--publicAddress" },
                description = "Specifies the IP address or URL that the Brooklyn Management Console Rest API will be available on")
        public String publicAddress = null;

        @Option(name = { "--noConsoleSecurity" },
                description = "Whether to disable security for the web console with no security (i.e. no authentication required)")
        public Boolean noConsoleSecurity = false;

        @Option(name = { "--ignoreWebStartupErrors" },
            description = "Ignore web subsystem failures on startup (default is to abort if it fails to start)")
        public boolean ignoreWebErrors = false;

        @Option(name = { "--ignorePersistenceStartupErrors" },
            description = "Ignore persistence/HA subsystem failures on startup (default is to abort if it fails to start)")
        public boolean ignorePersistenceErrors = false;

        @Option(name = { "--ignoreManagedAppsStartupErrors" },
            description = "Ignore failures starting managed applications passed on the command line on startup "
                + "(default is to abort if they fail to start)")
        public boolean ignoreAppErrors = false;

        // Note in some cases, you can get java.util.concurrent.RejectedExecutionException 
        // if shutdown is not co-ordinated, e.g. with Whirr:
        // looks like: {@linktourl https://gist.github.com/47066f72d6f6f79b953e}
        @Beta
        @Option(name = { "-sk", "--stopOnKeyPress" },
                description = "After startup, shutdown on user text entry")
        public boolean stopOnKeyPress = false;

        final static String STOP_WHICH_APPS_ON_SHUTDOWN = "--stopOnShutdown";
        protected final static String STOP_ALL = "all";
        protected final static String STOP_ALL_IF_NOT_PERSISTED = "allIfNotPersisted";
        protected final static String STOP_NONE = "none";
        protected final static String STOP_THESE = "these";        
        protected final static String STOP_THESE_IF_NOT_PERSISTED = "theseIfNotPersisted";
        static { Enums.checkAllEnumeratedIgnoreCase(StopWhichAppsOnShutdown.class, STOP_ALL, STOP_ALL_IF_NOT_PERSISTED, STOP_NONE, STOP_THESE, STOP_THESE_IF_NOT_PERSISTED); }
        
        @Option(name = { STOP_WHICH_APPS_ON_SHUTDOWN },
            allowedValues = { STOP_ALL, STOP_ALL_IF_NOT_PERSISTED, STOP_NONE, STOP_THESE, STOP_THESE_IF_NOT_PERSISTED },
            description = "Which managed applications to stop on shutdown. Possible values are:\n"+
                "all: stop all apps\n"+
                "none: leave all apps running\n"+
                "these: stop the apps explicitly started on this command line, but leave others started subsequently running\n"+
                "theseIfNotPersisted: stop the apps started on this command line IF persistence is not enabled, otherwise leave all running\n"+
                "allIfNotPersisted: stop all apps IF persistence is not enabled, otherwise leave all running")
        public String stopWhichAppsOnShutdown = STOP_THESE_IF_NOT_PERSISTED;

        /** @deprecated since 0.7.0 see {@link #stopWhichAppsOnShutdown} */
        @Deprecated
        @Option(name = { "-ns", "--noShutdownOnExit" },
            description = "Deprecated synonym for `--stopOnShutdown none`")
        public boolean noShutdownOnExit = false;
        
        final static String PERSIST_OPTION = "--persist";
        protected final static String PERSIST_OPTION_DISABLED = "disabled";
        protected final static String PERSIST_OPTION_AUTO = "auto";
        protected final static String PERSIST_OPTION_REBIND = "rebind";
        protected final static String PERSIST_OPTION_CLEAN = "clean";
        static { Enums.checkAllEnumeratedIgnoreCase(PersistMode.class, PERSIST_OPTION_DISABLED, PERSIST_OPTION_AUTO, PERSIST_OPTION_REBIND, PERSIST_OPTION_CLEAN); }
        
        // TODO currently defaults to disabled; want it to default to on, when we're ready
        // TODO how to force a line-split per option?!
        //      Looks like java.io.airlift.airline.UsagePrinter is splitting the description by word, and
        //      wrapping it automatically.
        //      See https://github.com/airlift/airline/issues/30
        @Option(name = { PERSIST_OPTION }, 
                allowedValues = { PERSIST_OPTION_DISABLED, PERSIST_OPTION_AUTO, PERSIST_OPTION_REBIND, PERSIST_OPTION_CLEAN },
                title = "persistence mode",
                description =
                        "The persistence mode. Possible values are: \n"+
                        "disabled: will not read or persist any state; \n"+
                        "auto: will rebind to any existing state, or start up fresh if no state; \n"+
                        "rebind: will rebind to the existing state, or fail if no state available; \n"+
                        "clean: will start up fresh (removing any existing state)")
        public String persist = PERSIST_OPTION_DISABLED;

        @Option(name = { "--persistenceDir" }, title = "persistence dir",
                description = "The directory to read/write persisted state (or container name if using an object store)")
        public String persistenceDir;

        @Option(name = { "--persistenceLocation" }, title = "persistence location",
            description = "The location spec for an object store to read/write persisted state")
        public String persistenceLocation;
    

        final static String HA_OPTION = "--highAvailability";
        protected final static String HA_OPTION_DISABLED = "disabled";
        protected final static String HA_OPTION_AUTO = "auto";
        protected final static String HA_OPTION_MASTER = "master";
        protected final static String HA_OPTION_STANDBY = "standby";
        static { Enums.checkAllEnumeratedIgnoreCase(HighAvailabilityMode.class, HA_OPTION_AUTO, HA_OPTION_DISABLED, HA_OPTION_MASTER, HA_OPTION_STANDBY); }
        
        @Option(name = { HA_OPTION }, allowedValues = { HA_OPTION_DISABLED, HA_OPTION_AUTO, HA_OPTION_MASTER, HA_OPTION_STANDBY },
                title = "high availability mode",
                description =
                        "The high availability mode. Possible values are: \n"+
                        "disabled: management node works in isolation - will not cooperate with any other standby/master nodes in management plane; \n"+
                        "auto: will look for other management nodes, and will allocate itself as standby or master based on other nodes' states; \n"+
                        "master: will startup as master - if there is already a master then fails immediately; \n"+
                        "standby: will start up as standby - if there is not already a master then fails immediately")
        public String highAvailability = HA_OPTION_AUTO;

        @VisibleForTesting
        protected ManagementContext explicitManagementContext;
        
        @Override
        public Void call() throws Exception {
            // Configure launcher
            BrooklynLauncher launcher;
            failIfArguments();
            try {
                if (log.isDebugEnabled()) log.debug("Invoked launch command {}", this);
                
                if (!quiet) stdout.println(BANNER);
    
                if (verbose) {
                    if (app != null) {
                        stdout.println("Launching brooklyn app: " + app + " in " + locations);
                    } else {
                        stdout.println("Launching brooklyn server (no app)");
                    }
                }
    
                PersistMode persistMode = computePersistMode();
                HighAvailabilityMode highAvailabilityMode = computeHighAvailabilityMode(persistMode);
                
                StopWhichAppsOnShutdown stopWhichAppsOnShutdownMode = computeStopWhichAppsOnShutdown();
                
                computeLocations();
                
                ResourceUtils utils = ResourceUtils.create(this);
                GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader());
    
                // First, run a setup script if the user has provided one
                if (script != null) {
                    execGroovyScript(utils, loader, script);
                }
    
                launcher = createLauncher();
    
                launcher.persistMode(persistMode);
                launcher.persistenceDir(persistenceDir);
                launcher.persistenceLocation(persistenceLocation);
                
                launcher.highAvailabilityMode(highAvailabilityMode);

                launcher.stopWhichAppsOnShutdown(stopWhichAppsOnShutdownMode);
                
                computeAndSetApp(launcher, utils, loader);
                
            } catch (FatalConfigurationRuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new FatalConfigurationRuntimeException("Fatal error configuring Brooklyn launch: "+e.getMessage(), e);
            }
            
            // Launch server
            try {
                launcher.start();
            } catch (FatalRuntimeException e) {
                // rely on caller logging this propagated exception
                throw e;
            } catch (Exception e) {
                // for other exceptions we log it, possibly redundantly but better too much than too little
                Exceptions.propagateIfFatal(e);
                log.error("Error launching brooklyn: "+Exceptions.collapseText(e), e);
                try {
                    launcher.terminate();
                } catch (Exception e2) {
                    log.warn("Subsequent error during termination: "+e2);
                    log.debug("Details of subsequent error during termination: "+e2, e2);
                }
                Exceptions.propagate(e);
            }
            
            BrooklynServerDetails server = launcher.getServerDetails();
            ManagementContext ctx = server.getManagementContext();
            
            try {
                populateCatalog(launcher.getServerDetails().getManagementContext().getCatalog());
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                // don't fail to start just because catalog is not available
                log.error("Error populating catalog: "+e, e);
            }

            if (verbose) {
                Entities.dumpInfo(launcher.getApplications());
            }
            
            waitAfterLaunch(ctx);

            return null;
        }

        protected void computeLocations() {
            boolean hasLocations = !Strings.isBlank(locations);
            if (app != null) {
                if (hasLocations && isYamlApp()) {
                    log.info("YAML app combined with command line locations; YAML locations will take precedence; this behaviour may change in subsequent versions");
                } else if (!hasLocations && isYamlApp()) {
                    log.info("No locations supplied; defaulting to locations defined in YAML (if any)");
                } else if (!hasLocations) {
                    log.info("No locations supplied; starting with no locations");
                }
            } else if (hasLocations) {
                log.error("Locations specified without any applications; ignoring locations");
            }
        }

        protected boolean isYamlApp() {
            return app != null && app.endsWith(".yaml");
        }

        protected PersistMode computePersistMode() {
            Maybe<PersistMode> persistMode = Enums.valueOfIgnoreCase(PersistMode.class, persist);
            if (!persistMode.isPresent()) {
                if (Strings.isBlank(persist)) {
                    throw new FatalConfigurationRuntimeException("Persist mode must not be blank");
                } else {
                    throw new FatalConfigurationRuntimeException("Illegal persist setting: "+persist);
                }
            }
   
            if (persistMode.get() == PersistMode.DISABLED) {
                if (Strings.isNonBlank(persistenceDir))
                    throw new FatalConfigurationRuntimeException("Cannot specify persistenceDir when persist is disabled");
                if (Strings.isNonBlank(persistenceLocation))
                    throw new FatalConfigurationRuntimeException("Cannot specify persistenceLocation when persist is disabled");
            }
            return persistMode.get();
        }

        protected HighAvailabilityMode computeHighAvailabilityMode(PersistMode persistMode) {
            Maybe<HighAvailabilityMode> highAvailabilityMode = Enums.valueOfIgnoreCase(HighAvailabilityMode.class, highAvailability);
            if (!highAvailabilityMode.isPresent()) {
                if (Strings.isBlank(highAvailability)) {
                    throw new FatalConfigurationRuntimeException("High availability mode must not be blank");
                } else {
                    throw new FatalConfigurationRuntimeException("Illegal highAvailability setting: "+highAvailability);
                }
            }
   
            if (highAvailabilityMode.get() != HighAvailabilityMode.DISABLED) {
                if (persistMode == PersistMode.DISABLED) {
                    if (highAvailabilityMode.get() == HighAvailabilityMode.AUTO)
                        return HighAvailabilityMode.DISABLED;
                    throw new FatalConfigurationRuntimeException("Cannot specify highAvailability when persistence is disabled");
                } else if (persistMode == PersistMode.CLEAN && highAvailabilityMode.get() == HighAvailabilityMode.STANDBY) {
                    throw new FatalConfigurationRuntimeException("Cannot specify highAvailability STANDBY when persistence is CLEAN");
                }
            }
            return highAvailabilityMode.get();
        }
        
        protected StopWhichAppsOnShutdown computeStopWhichAppsOnShutdown() {
            if (noShutdownOnExit) {
                if (STOP_THESE_IF_NOT_PERSISTED.equals(stopWhichAppsOnShutdown)) {
                    // the default; assume it was not explicitly specified so no error
                    stopWhichAppsOnShutdown = STOP_NONE;
                    // but warn of deprecation
                    log.warn("Deprecated paramater `--noShutdownOnExit` detected; this will likely be removed in a future version; "
                        + "replace with `"+STOP_WHICH_APPS_ON_SHUTDOWN+" "+stopWhichAppsOnShutdown+"`");
                } else {
                    throw new FatalConfigurationRuntimeException("Cannot specify both `--noShutdownOnExit` and `"+STOP_WHICH_APPS_ON_SHUTDOWN+"`");
                }
            }
            return Enums.valueOfIgnoreCase(StopWhichAppsOnShutdown.class, stopWhichAppsOnShutdown).get();
        }
        
        @VisibleForTesting
        /** forces the launcher to use the given management context, when programmatically invoked;
         * mainly used when testing to inject a safe (and fast) mgmt context */
        public void useManagementContext(ManagementContext mgmt) {
            explicitManagementContext = mgmt;
        }

        protected BrooklynLauncher createLauncher() {
            BrooklynLauncher launcher;
            launcher = BrooklynLauncher.newInstance();
            launcher.localBrooklynPropertiesFile(localBrooklynProperties)
                    .webconsolePort(port)
                    .webconsole(!noConsole)
                    .ignorePersistenceErrors(ignorePersistenceErrors)
                    .ignoreWebErrors(ignoreWebErrors)
                    .ignoreAppErrors(ignoreAppErrors)
                    .locations(Strings.isBlank(locations) ? ImmutableList.<String>of() : JavaStringEscapes.unwrapJsonishListIfPossible(locations));
            if (noConsoleSecurity) {
                launcher.installSecurityFilter(false);
            }
            if (Strings.isNonEmpty(bindAddress)) {
                launcher.bindAddress( Networking.getInetAddressWithFixedName(bindAddress) );
            }
            if (Strings.isNonEmpty(publicAddress)) {
                launcher.publicAddress( URI.create(publicAddress) );
            }
            if (explicitManagementContext!=null) {
                launcher.managementContext(explicitManagementContext);
            }
            return launcher;
        }

        /** method intended for subclassing, to add items to the catalog */
        protected void populateCatalog(BrooklynCatalog catalog) {
            // Force load of catalog (so web console is up to date)
            catalog.getCatalogItems();

            // nothing else added here
        }

        /** convenience for subclasses to specify that an app should run,
         * throwing the right (caught) error if another app has already been specified */
        protected void setAppToLaunch(String className) {
            if (app!=null) {
                if (app.equals(className)) return;
                throw new FatalConfigurationRuntimeException("Cannot specify app '"+className+"' when '"+app+"' is already specified; "
                    + "remove one or more conflicting CLI arguments.");
            }
            app = className;
        }
        
        protected void computeAndSetApp(BrooklynLauncher launcher, ResourceUtils utils, GroovyClassLoader loader)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
            if (app != null) {
                // Create the instance of the brooklyn app
                log.debug("Loading the user's application: {}", app);
   
                if (isYamlApp()) {
                    log.debug("Loading application as YAML spec: {}", app);
                    String content = utils.getResourceAsString(app);
                    launcher.application(content);
                } else {
                    Object loadedApp = loadApplicationFromClasspathOrParse(utils, loader, app);
                    if (loadedApp instanceof ApplicationBuilder) {
                        launcher.application((ApplicationBuilder)loadedApp);
                    } else if (loadedApp instanceof Application) {
                        launcher.application((AbstractApplication)loadedApp);
                    } else {
                        throw new FatalConfigurationRuntimeException("Unexpected application type "+(loadedApp==null ? null : loadedApp.getClass())+", for app "+loadedApp);
                    }
                }
            }
        }
        
        protected void waitAfterLaunch(ManagementContext ctx) throws IOException {
            if (stopOnKeyPress) {
                // Wait for the user to type a key
                log.info("Server started. Press return to stop.");
                stdin.read();
                stopAllApps(ctx.getApplications());
            } else {
                // Block forever so that Brooklyn doesn't exit (until someone does cntrl-c or kill)
                log.info("Launched Brooklyn; will now block until shutdown issued. Shutdown via GUI or API or process interrupt.");
                waitUntilInterrupted();
            }
        }

        protected void waitUntilInterrupted() {
            Object mutex = new Object();
            synchronized (mutex) {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        mutex.wait();
                        log.debug("Spurious wake in brooklyn Main while waiting for interrupt, how about that!");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // exit gracefully
                }
            }
        }

        protected void execGroovyScript(ResourceUtils utils, GroovyClassLoader loader, String script) {
            log.debug("Running the user provided script: {}", script);
            String content = utils.getResourceAsString(script);
            GroovyShell shell = new GroovyShell(loader);
            shell.evaluate(content);
        }

        /**
         * Helper method that gets an instance of a brooklyn {@link AbstractApplication} or an {@link ApplicationBuilder}.
         * Guaranteed to be non-null result of one of those types (throwing exception if app not appropriate).
         */
        @SuppressWarnings("unchecked")
        protected Object loadApplicationFromClasspathOrParse(ResourceUtils utils, GroovyClassLoader loader, String app)
                throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
            
            Class<?> tempclazz;
            log.debug("Loading application as class on classpath: {}", app);
            try {
                tempclazz = loader.loadClass(app, true, false);
            } catch (ClassNotFoundException cnfe) { // Not a class on the classpath
                log.debug("Loading \"{}\" as class on classpath failed, now trying as .groovy source file", app);
                String content = utils.getResourceAsString(app);
                tempclazz = loader.parseClass(content);
            }
            final Class<?> clazz = tempclazz;
            
            // Instantiate an app builder (wrapping app class in ApplicationBuilder, if necessary)
            if (ApplicationBuilder.class.isAssignableFrom(clazz)) {
                Constructor<?> constructor = clazz.getConstructor();
                return (ApplicationBuilder) constructor.newInstance();
            } else if (StartableApplication.class.isAssignableFrom(clazz)) {
                EntitySpec<? extends StartableApplication> appSpec;
                if (tempclazz.isInterface())
                    appSpec = EntitySpec.create((Class<? extends StartableApplication>) clazz);
                else
                    appSpec = EntitySpec.create(StartableApplication.class, (Class<? extends StartableApplication>) clazz);
                return new ApplicationBuilder(appSpec) {
                    @Override protected void doBuild() {
                    }};
            } else if (AbstractApplication.class.isAssignableFrom(clazz)) {
                // TODO If this application overrides init() then in trouble, as that won't get called!
                // TODO grr; what to do about non-startable applications?
                // without this we could return ApplicationBuilder rather than Object
                Constructor<?> constructor = clazz.getConstructor();
                return (AbstractApplication) constructor.newInstance();
            } else if (AbstractEntity.class.isAssignableFrom(clazz)) {
                // TODO Should we really accept any entity type, and just wrap it in an app? That's not documented!
                return new ApplicationBuilder() {
                    @Override protected void doBuild() {
                        addChild(EntitySpec.create(Entity.class).impl((Class<? extends AbstractEntity>)clazz).additionalInterfaces(clazz.getInterfaces()));
                    }};
            } else if (Entity.class.isAssignableFrom(clazz)) {
                return new ApplicationBuilder() {
                    @Override protected void doBuild() {
                        addChild(EntitySpec.create((Class<? extends Entity>)clazz));
                    }};
            } else {
                throw new FatalConfigurationRuntimeException("Application class "+clazz+" must extend one of ApplicationBuilder or AbstractApplication");
            }
        }

        @VisibleForTesting
        protected void stopAllApps(Collection<? extends Application> applications) {
            for (Application application : applications) {
                try {
                    if (application instanceof Startable) {
                        ((Startable)application).stop();
                    }
                } catch (Exception e) {
                    log.error("Error stopping "+application+": "+e, e);
                }
            }
        }
        
        @Override
        public ToStringHelper string() {
            return super.string()
                    .add("app", app)
                    .add("script", script)
                    .add("location", locations)
                    .add("port", port)
                    .add("bindAddress", bindAddress)
                    .add("noConsole", noConsole)
                    .add("noConsoleSecurity", noConsoleSecurity)
                    .add("ignorePersistenceErrors", ignorePersistenceErrors)
                    .add("ignoreWebErrors", ignoreWebErrors)
                    .add("ignoreAppErrors", ignoreAppErrors)
                    .add("stopWhichAppsOnShutdown", stopWhichAppsOnShutdown)
                    .add("noShutdownOnExit", noShutdownOnExit)
                    .add("stopOnKeyPress", stopOnKeyPress)
                    .add("localBrooklynProperties", localBrooklynProperties)
                    .add("persist", persist)
                    .add("persistenceLocation", persistenceLocation)
                    .add("persistenceDir", persistenceDir)
                    .add("highAvailability", highAvailability);
        }
    }

    @Command(name = "copy-state", description = "Retrieves persisted state")
    public static class CopyStateCommand extends BrooklynCommandCollectingArgs {

        @Option(name = { "--localBrooklynProperties" }, title = "local brooklyn.properties file",
                description = "local brooklyn.properties file, specific to this launch (appending to and overriding global properties)")
        public String localBrooklynProperties;

        @Option(name = { "--persistenceDir" }, title = "persistence dir",
                description = "The directory to read/write persisted state (or container name if using an object store)")
        public String persistenceDir;

        @Option(name = { "--persistenceLocation" }, title = "persistence location",
            description = "The location spec for an object store to read/write persisted state")
        public String persistenceLocation;
    
        @Option(name = { "--destinationDir" }, required = true, title = "destination dir",
                description = "The directory to copy persistence data to")
            public String destinationDir;
        
        @Override
        public Void call() throws Exception {
            File destinationDirF = new File(Os.tidyPath(destinationDir));
            if (destinationDirF.isFile()) throw new FatalConfigurationRuntimeException("Destination directory is a file: "+destinationDir);


            // Configure launcher
            BrooklynLauncher launcher;
            failIfArguments();
            try {
                log.info("Retrieving and copying persisted state to "+destinationDirF.getAbsolutePath());
                
                if (!quiet) stdout.println(BANNER);
    
                PersistMode persistMode = PersistMode.AUTO;
                HighAvailabilityMode highAvailabilityMode = HighAvailabilityMode.DISABLED;
                
                launcher = BrooklynLauncher.newInstance()
                        .localBrooklynPropertiesFile(localBrooklynProperties)
                        .persistMode(persistMode)
                        .persistenceDir(persistenceDir)
                        .persistenceLocation(persistenceLocation)
                        .highAvailabilityMode(highAvailabilityMode);
                
            } catch (FatalConfigurationRuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new FatalConfigurationRuntimeException("Fatal error configuring Brooklyn launch: "+e.getMessage(), e);
            }
            
            try {
                BrooklynMemento memento = launcher.retrieveState();
                launcher.persistState(memento, destinationDirF);
                
            } catch (FatalRuntimeException e) {
                // rely on caller logging this propagated exception
                throw e;
            } catch (Exception e) {
                // for other exceptions we log it, possibly redundantly but better too much than too little
                Exceptions.propagateIfFatal(e);
                log.error("Error retrieving persisted state: "+Exceptions.collapseText(e), e);
                Exceptions.propagate(e);
            } finally {
                try {
                    launcher.terminate();
                } catch (Exception e2) {
                    log.warn("Subsequent error during termination: "+e2);
                    log.debug("Details of subsequent error during termination: "+e2, e2);
                }
            }
            
            return null;
        }

        @Override
        public ToStringHelper string() {
            return super.string()
                    .add("localBrooklynProperties", localBrooklynProperties)
                    .add("persistenceLocation", persistenceLocation)
                    .add("persistenceDir", persistenceDir)
                    .add("destinationDir", destinationDir);
        }
    }

    /** method intended for overriding when the script filename is different 
     * @return the name of the script the user has invoked */
    protected String cliScriptName() {
        return "brooklyn";
    }

    /** method intended for overriding when a different {@link Cli} is desired,
     * or when the subclass wishes to change any of the arguments */
    protected CliBuilder<BrooklynCommand> cliBuilder() {
        @SuppressWarnings({ "unchecked" })
        CliBuilder<BrooklynCommand> builder = Cli.<BrooklynCommand>builder(cliScriptName())
                .withDescription("Brooklyn Management Service")
                .withCommands(
                        HelpCommand.class,
                        InfoCommand.class,
                        GeneratePasswordCommand.class,
                        CopyStateCommand.class,
                        cliLaunchCommand()
                );

        return builder;
    }
    
    /** method intended for overriding when a custom {@link LaunchCommand} is being specified  */
    protected Class<? extends BrooklynCommand> cliLaunchCommand() {
        return LaunchCommand.class;
    }
    
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
