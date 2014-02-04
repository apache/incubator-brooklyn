package brooklyn.cli;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import io.airlift.command.Cli;
import io.airlift.command.Cli.CliBuilder;
import io.airlift.command.Command;
import io.airlift.command.Help;
import io.airlift.command.Option;
import io.airlift.command.OptionType;
import io.airlift.command.ParseException;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.BrooklynVersion;
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
import brooklyn.launcher.PersistMode;
import brooklyn.management.ManagementContext;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Networking;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableList;

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

    public static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String... args) {
        Cli<BrooklynCommand> parser = buildCli();
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
        } catch (Exception e) { // unexpected error during command execution
            log.error("Execution error: {}\n{}" + e.getMessage(), e.getStackTrace());
            System.err.println("Execution error: " + e.getMessage());
            e.printStackTrace();
            System.exit(EXECUTION_ERROR);
        }
    }

    public static abstract class BrooklynCommand implements Callable<Void> {

        @Inject
        public Help help;

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

    @Command(name = "help", description = "Display help for available commands")
    public static class HelpCommand extends BrooklynCommand {

        @Override
        public Void call() throws Exception {
            if (log.isDebugEnabled()) log.debug("Invoked help command: {}", this);
            return help.call();
        }
    }

    @Command(name = "info", description = "Display information about brooklyn")
    public static class InfoCommand extends BrooklynCommand {

        @Override
        public Void call() throws Exception {
            if (log.isDebugEnabled()) log.debug("Invoked info command: {}", this);

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

    @Command(name = "launch", description = "Starts a brooklyn application. " +
            "Note that a BROOKLYN_CLASSPATH environment variable needs to be set up beforehand " +
            "to point to the user application classpath.")
    public static class LaunchCommand extends BrooklynCommand {

        @Option(name = { "--localBrooklynProperties" }, title = "local brooklyn.properties file",
                description = "local brooklyn.properties file, specific to this launch (appending to and overriding global properties)")
        public String localBrooklynProperties;

        @Option(name = { "-a", "--app" }, title = "application class or file",
                description = "The Application to start. " +
                        "For example, my.AppName, file://my/app.yaml, or classpath://my/AppName.groovy")
        public String app;

        @Beta
        @Option(name = { "-s", "--script" }, title = "script URI",
                description = "EXPERIMENTAL. URI for a Groovy script to parse and load." +
                        " This script will run before starting the app.")
        public String script = null;

        @Option(name = { "-l", "--location", "--locations" }, title = "location list",
                description = "Specifies the locations where the application will be launched. " +
                        "You can specify more than one location like this: \"loc1,loc2,loc3\"")
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

        // TODO currently defaults to disabled; want it to default to on, when we're ready
        @Option(name = { "--persist" }, allowedValues = { "disabled", "auto", "rebind", "clean" },
                title = "persistance mode",
                description = "the persistence mode")
        public String persist = "disabled";

        @Option(name = { "--persistenceDir" }, title = "persistence dir",
                description = "the directory to read/write persisted state")
        public String persistenceDir;

        @Override
        public Void call() throws Exception {
            if (log.isDebugEnabled()) log.debug("Invoked launch command {}", this);
            if (!quiet) System.out.println(BANNER);

            if (verbose) {
                if (app != null) {
                    System.out.println("Launching brooklyn app: " + app + " in " + locations);
                } else {
                    System.out.println("Launching brooklyn server (no app)");
                }
            }

            boolean isYamlApp = app != null && app.endsWith(".yaml");
            boolean hasLocations = !Strings.isBlank(locations);
            if (app != null) {
                if (hasLocations && isYamlApp) {
                    System.err.println("YAML app combined with command line locations; locations in the YAML file will take precedence");
                } else if (!hasLocations && isYamlApp) {
                    System.err.println("Using locations defined in YAML or localhost if none given");
                    locations = "localhost";
                } else if (!hasLocations) {
                    System.err.println("Locations parameter not supplied: assuming localhost");
                    locations = "localhost";
                }
            } else if (hasLocations) {
                System.err.println("Locations specified without any applications; ignoring locations");
            }
            
            PersistMode persistMode;
            if (Strings.isBlank(persist)) {
                throw new IllegalStateException("Persist mode must not be blank");
            } else if (persist.equalsIgnoreCase("disabled")) {
                persistMode = PersistMode.DISABLED;
            } else if (persist.equalsIgnoreCase("auto")) {
                persistMode = PersistMode.AUTO;
            } else if (persist.equalsIgnoreCase("rebind")) {
                persistMode = PersistMode.REBIND;
            } else if (persist.equalsIgnoreCase("clean")) {
                persistMode = PersistMode.CLEAN;
            } else {
                throw new IllegalStateException("Illegal persist setting: "+persist);
            }

            if (persistMode == PersistMode.DISABLED) {
                if (Strings.isNonBlank(persistenceDir)) {
                    throw new IllegalStateException("Cannot specify peristanceDir when persist is disabled");
                }
            } else {
                if (Strings.isBlank(persistenceDir)) {
                    persistenceDir = "brooklyn-persisted-state"+File.separator+"data"+File.separator; 
                }
            }
            
            ResourceUtils utils = ResourceUtils.create(this);
            ClassLoader parent = utils.getLoader();
            GroovyClassLoader loader = new GroovyClassLoader(parent);

            // First, run a setup script if the user has provided one
            if (script != null) {
                execGroovyScript(utils, loader, script);
            }

            BrooklynLauncher launcher = BrooklynLauncher.newInstance();
            launcher.localBrooklynPropertiesFile(localBrooklynProperties)
                    .webconsolePort(port)
                    .webconsole(!noConsole)
                    .shutdownOnExit(!noShutdownOnExit)
                    .locations(Strings.isBlank(locations) ? ImmutableList.<String>of() : ImmutableList.of(locations));
            if (noConsoleSecurity) {
                launcher.installSecurityFilter(false);
            }
            if (Strings.isNonEmpty(bindAddress)) {
                InetAddress ip = Networking.getInetAddressWithFixedName(bindAddress);
                launcher.bindAddress(ip);
            }

            if (app != null) {
                // Create the instance of the brooklyn app
                log.debug("Loading the user's application: {}", app);

                if (isYamlApp) {
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
                        throw new IllegalStateException("Unexpected application type "+(loadedApp==null ? null : loadedApp.getClass())+", for app "+loadedApp);
                    }
                }
            }

            launcher.persistMode(persistMode);
            if (persistMode != PersistMode.DISABLED) {
                if (Strings.isBlank(persistenceDir)) {
                    persistenceDir = "/some/default/somewhere?"; 
                }
                launcher.persistenceDir(persistenceDir);
            }
            
            // Launch server
            try {
                launcher.start();
            } catch (Exception e) {
                // Don't terminate the JVM; leave it as-is until someone explicitly stops it
                Exceptions.propagateIfFatal(e);
                log.error("Error launching brooklyn: "+e, e);
            }
            
            BrooklynServerDetails server = launcher.getServerDetails();
            ManagementContext ctx = server.getManagementContext();
            
            // Force load of catalog (so web console is up to date)
            ctx.getCatalog().getCatalogItems();

            if (verbose) {
                Entities.dumpInfo(launcher.getApplications());
            }
            
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

            return null;
        }

        @VisibleForTesting
        void waitUntilInterrupted() {
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

        @VisibleForTesting
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
        @VisibleForTesting
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

            // Intantiate an app builder (wrapping app class in ApplicationBuilder, if necessary)
            if (ApplicationBuilder.class.isAssignableFrom(clazz)) {
                Constructor<?> constructor = clazz.getConstructor();
                return (ApplicationBuilder) constructor.newInstance();
            } else if (StartableApplication.class.isAssignableFrom(clazz)) {
                @SuppressWarnings("unchecked")
                EntitySpec<StartableApplication> appSpec = EntitySpec.create(StartableApplication.class, (Class<? extends StartableApplication>) clazz);
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
                    @SuppressWarnings("unchecked")
                    @Override protected void doBuild() {
                        addChild(EntitySpec.create(Entity.class).impl((Class<? extends AbstractEntity>)clazz));
                    }};
            } else if (Entity.class.isAssignableFrom(clazz)) {
                return new ApplicationBuilder() {
                    @SuppressWarnings("unchecked")
                    @Override protected void doBuild() {
                        addChild(EntitySpec.create((Class<? extends Entity>)clazz));
                    }};
            } else {
                throw new IllegalArgumentException("Application class "+clazz+" must extend one of ApplicationBuilder or AbstractApplication");
            }
        }

        @VisibleForTesting
        void stopAllApps(Collection<? extends Application> applications) {
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
                    .add("persistenceDir", persistenceDir);
        }
    }

    @VisibleForTesting
    static Cli<BrooklynCommand> buildCli() {
        @SuppressWarnings({ "unchecked" })
        CliBuilder<BrooklynCommand> builder = Cli.<BrooklynCommand>builder("brooklyn").withCommand(BrooklynCommand.class)
                .withDescription("Brooklyn Management Service")
                .withCommands(
                        HelpCommand.class,
                        InfoCommand.class,
                        LaunchCommand.class
                );

        return builder.build();
    }

    static String getUsageInfo(Cli<BrooklynCommand> parser) {
        StringBuilder help = new StringBuilder();
        help.append("\n");
        Help.help(parser.getMetadata(), ImmutableList.of("brooklyn"), help);
        help.append("See 'brooklyn help <command>' for more information on a specific command.");
        return help.toString();
    }

}
