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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
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
import brooklyn.entity.trait.Startable;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.launcher.BrooklynServerDetails;
import brooklyn.launcher.FatalConfigurationRuntimeException;
import brooklyn.launcher.PersistMode;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.Enums;
import brooklyn.util.net.Networking;
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
        protected boolean warnIfNoArguments() {
            if (arguments.isEmpty()) return false;
            System.err.println("Invalid subcommand arguments: "+Strings.join(arguments, " "));
            return true;
        }
        
        /** throw {@link ParseException} iff there are arguments */
        protected void failIfNoArguments() {
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
            warnIfNoArguments();

            // Get current version
            String version = BrooklynVersion.get();

            // Display info text
            System.out.println(BANNER);
            System.out.println("Version:  " + version);
            System.out.println("Website:  http://brooklyn.io/");
            System.out.println("Source:   https://github.com/brooklyncentral/brooklyn/");
            System.out.println();
            System.out.println("Copyright 2011-2014 by Cloudsoft Corp.");
            System.out.println("Licensed under the Apache 2.0 License");
            System.out.println();

            return null;
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

        @Option(name = { "--noConsoleSecurity" },
                description = "Whether to disable security for the web console with no security (i.e. no authentication required)")
        public Boolean noConsoleSecurity = false;

        @Option(name = { "-ns", "--noShutdownOnExit" },
                description = "Whether to stop the application when the JVM exits")
        public boolean noShutdownOnExit = false;

        /**
         * Note that this is a temporary workaround to allow for running the
         * brooklyn-whirr example.
         * 
         * This will be replaced by more powerful CLI control for running
         * processes, to send shutdown and other commands to brooklyn.
         * 
         * Without using this flag you get a
         * java.util.concurrent.RejectedExecutionException because the brooklyn
         * and whirr shutdown hooks get executed in parallel. This is how it
         * looks like: {@linktourl https://gist.github.com/47066f72d6f6f79b953e}
         */
        @Beta
        @Option(name = { "-sk", "--stopOnKeyPress" },
                description = "After the application gets started, brooklyn will wait for a key press to stop it.")
        public boolean stopOnKeyPress = false;

        final static String PERSIST_OPTION = "--persist";
        final static String PERSIST_OPTION_DISABLED = "disabled";
        final static String PERSIST_OPTION_AUTO = "auto";
        final static String PERSIST_OPTION_REBIND = "rebind";
        final static String PERSIST_OPTION_CLEAN = "clean";
        
        // TODO currently defaults to disabled; want it to default to on, when we're ready
        // TODO how to force a line-split per option?!
        //      Looks like java.io.airlift.airline.UsagePrinter is splitting the description by word, and
        //      wrapping it automatically.
        //      See https://github.com/airlift/airline/issues/30
        @Option(name = { PERSIST_OPTION }, allowedValues = { PERSIST_OPTION_DISABLED, PERSIST_OPTION_AUTO, PERSIST_OPTION_REBIND, PERSIST_OPTION_CLEAN },
                title = "persistance mode",
                description =
                        "The persistence mode. Possible values are: \n"+
                        "disabled: will not read or persist any state; \n"+
                        "auto: will rebind to any existing state, or start up fresh if no state; \n"+
                        "rebind: will rebind to the existing state, or fail if no state available; \n"+
                        "clean: will start up fresh (not using any existing state)")
        public String persist = PERSIST_OPTION_DISABLED;

        @Option(name = { "--persistenceDir" }, title = "persistence dir",
                description = "the directory to read/write persisted state")
        public String persistenceDir;
        
        final static String HA_OPTION = "--highAvailability";
        final static String HA_OPTION_DISABLED = "disabled";
        final static String HA_OPTION_AUTO = "auto";
        final static String HA_OPTION_MASTER = "master";
        final static String HA_OPTION_STANDBY = "standby";
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
            failIfNoArguments();
            try {
                if (log.isDebugEnabled()) log.debug("Invoked launch command {}", this);
                
                if (!quiet) System.out.println(BANNER);
    
                if (verbose) {
                    if (app != null) {
                        System.out.println("Launching brooklyn app: " + app + " in " + locations);
                    } else {
                        System.out.println("Launching brooklyn server (no app)");
                    }
                }
    
                computeLocations();
                
                PersistMode persistMode = computePersistMode();
                HighAvailabilityMode highAvailabilityMode = computeHighAvailabilityMode(persistMode);
                
                ResourceUtils utils = ResourceUtils.create(this);
                ClassLoader parent = utils.getLoader();
                GroovyClassLoader loader = new GroovyClassLoader(parent);
    
                // First, run a setup script if the user has provided one
                if (script != null) {
                    execGroovyScript(utils, loader, script);
                }
    
                launcher = createLauncher();
    
                launchApp(launcher, utils, loader);
    
                launcher.persistMode(persistMode);
                if (persistMode != PersistMode.DISABLED && Strings.isNonBlank(persistenceDir)) {
                    launcher.persistenceDir(persistenceDir);
                }
                
                launcher.highAvailabilityMode(highAvailabilityMode);

            } catch (FatalConfigurationRuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new FatalConfigurationRuntimeException("Fatal error configuring Brooklyn launch: "+e.getMessage(), e);
            }
            
            // Launch server
            try {
                launcher.start();
            } catch (FatalConfigurationRuntimeException e) {
                // rely on caller logging this propagated exception
                throw e;
            } catch (Exception e) {
                // Don't terminate the JVM; leave it as-is until someone explicitly stops it
                Exceptions.propagateIfFatal(e);
                log.error("Error launching brooklyn: "+Exceptions.collapseText(e), e);
            }
            
            BrooklynServerDetails server = launcher.getServerDetails();
            ManagementContext ctx = server.getManagementContext();
            
            populateCatalog(launcher.getServerDetails().getManagementContext().getCatalog());

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
                if (Strings.isNonBlank(persistenceDir)) {
                    throw new FatalConfigurationRuntimeException("Cannot specify peristanceDir when persist is disabled");
                }
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
                    .shutdownOnExit(!noShutdownOnExit)
                    .locations(Strings.isBlank(locations) ? ImmutableList.<String>of() : JavaStringEscapes.unwrapJsonishListIfPossible(locations));
            if (noConsoleSecurity) {
                launcher.installSecurityFilter(false);
            }
            if (Strings.isNonEmpty(bindAddress)) {
                InetAddress ip = Networking.getInetAddressWithFixedName(bindAddress);
                launcher.bindAddress(ip);
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
        
        protected void launchApp(BrooklynLauncher launcher, ResourceUtils utils, GroovyClassLoader loader)
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
                System.in.read();
                stopAllApps(ctx.getApplications());
            } else {
                // Block forever so that Brooklyn doesn't exit (until someone does cntrl-c or kill)
                log.info("Launched Brooklyn; now blocking to wait for cntrl-c or kill");
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
                    .add("noShutdownOnExit", noShutdownOnExit)
                    .add("stopOnKeyPress", stopOnKeyPress)
                    .add("localBrooklynProperties", localBrooklynProperties)
                    .add("persist", persist)
                    .add("persistenceDir", persistenceDir)
                    .add("highAvailability", highAvailability);
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
            log.error("Configuration error: " + e.getMessage(), e);
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(CONFIGURATION_ERROR);
        } catch (Exception e) { // unexpected error during command execution
            log.error("Execution error: " + e.getMessage(), e);
            System.err.println("Execution error: " + e.getMessage());
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
