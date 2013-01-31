package brooklyn.cli;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import org.iq80.cli.Cli;
import org.iq80.cli.Cli.CliBuilder;
import org.iq80.cli.Command;
import org.iq80.cli.Help;
import org.iq80.cli.Option;
import org.iq80.cli.OptionType;
import org.iq80.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.BrooklynVersion;
import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.trait.Startable;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.launcher.BrooklynServerDetails;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.util.ResourceUtils;

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
        "                              |___/       \n";

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
            log.debug("Invoked help command");
            return help.call();
        }
    }

    @Command(name = "info", description = "Display information about brooklyn")
    public static class InfoCommand extends BrooklynCommand {

        @Override
        public Void call() throws Exception {
            log.debug("Invoked info command");

            // Get current version
            String version = BrooklynVersion.get();

            // Display info text
            System.out.println(BANNER);
            System.out.println("Version:  " + version);
            System.out.println("Website:  http://brooklyn.io/");
            System.out.println("Source:   https://github.com/brooklyncentral/brooklyn/");
            System.out.println();
            System.out.println("Copyright 2011-2013 by Cloudsoft Corp.");
            System.out.println("Licensed under the Apache 2.0 License");
            System.out.println();

            return null;
        }
    }

    @Command(name = "launch", description = "Starts a brooklyn application. " +
            "Note that a BROOKLYN_CLASSPATH environment variable needs to be set up beforehand " +
            "to point to the user application classpath.")
    public static class LaunchCommand extends BrooklynCommand {

        @Option(name = { "-a", "--app" }, title = "application class or file",
                description = "The Application to start. " +
                        "For example my.AppName or file://my/AppName.groovy or classpath://my/AppName.groovy")
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
                description = "Specifies the port to be used by the Brooklyn Management Console.")
        public String port = "8081+";

        @Option(name = { "-nc", "--noConsole" },
                description = "Whether to start the web console")
        public boolean noConsole = false;

        @Option(name = { "-ns", "--noShutdownOnExit" },
                description = "Whether to stop the application when the JVM exits")
        public boolean noShutdownOnExit = false;

        /**
         * Note that this is a temporrary workaround to allow for runnig the
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

        @Override
        public Void call() throws Exception {
            log.debug("Invoked launch command");
            if (!quiet) System.out.println(BANNER);

            if (verbose) {
                if (app != null) {
                    System.out.println("Launching brooklyn app: " + app + " in " + locations);
                } else {
                    System.out.println("Launching brooklyn server (no app)");
                }
            }
            BrooklynLauncher launcher = BrooklynLauncher.newLauncher();

            ResourceUtils utils = new ResourceUtils(this);
            ClassLoader parent = utils.getLoader();
            GroovyClassLoader loader = new GroovyClassLoader(parent);

            // First, run a setup script if the user has provided one
            if (script != null) {
                log.debug("Running the user provided script: {}", script);
                String content = utils.getResourceAsString(script);
                GroovyShell shell = new GroovyShell(loader);
                shell.evaluate(content);
            }

            launcher.webconsolePort(port);
            launcher.webconsole(!noConsole);

            if (locations == null || locations.isEmpty()) {
                if (app != null) {
                    System.err.println("Locations parameter not supplied: assuming localhost");
                    locations = "localhost";
                }
            }

            if (app != null) {
                // Create the instance of the brooklyn app
                log.debug("Load the user's application: {}", app);
                Object loadedApp = loadApplicationFromClasspathOrParse(utils, loader, app);
                if (loadedApp instanceof ApplicationBuilder) {
                    launcher.managing((ApplicationBuilder)loadedApp);
                } else {
                    launcher.managing((AbstractApplication)loadedApp);
                }
            }

            // Launch server
            log.info("Launching Brooklyn web console management");
            BrooklynServerDetails server = launcher.launch();
            ManagementContext ctx = server.getManagementContext();
            
            // Force load of catalog (so web console is up to date)
            ctx.getCatalog().getCatalogItems();

            // Resolve locations
            List<Location> brooklynLocations = locations != null ?
                    ctx.getLocationRegistry().resolve(Arrays.asList(locations)) : null;

            // Start application
            startAllApps(ctx.getApplications(), brooklynLocations);

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
                    while (true) {
                        mutex.wait();
                        log.debug("spurious wake in brooklyn Main, how about that!");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // exit gracefully
                }
            }
        }

        /**
         * Helper method that gets an instance of a brooklyn {@link AbstractApplication} or an {@link ApplicationBuilder}.
         * Guaranteed to be non-null result of one of those types (throwing exception if app not appropriate).
         */
        @VisibleForTesting
        Object loadApplicationFromClasspathOrParse(ResourceUtils utils, GroovyClassLoader loader, String app) 
                throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, 
                IllegalAccessException, InvocationTargetException {
            Class<?> clazz;
            try {
                log.debug("Trying to load application as class on classpath: {}", app);
                clazz = loader.loadClass(app, true, false);
            } catch (ClassNotFoundException cnfe) { // Not a class on the classpath
                log.debug("Loading \"{}\" as class on classpath failed, now trying as .groovy source file", app);
                String content = utils.getResourceAsString(app);
                clazz = loader.parseClass(content);
            }
            if (ApplicationBuilder.class.isAssignableFrom(clazz)) {
                Constructor<?> constructor = clazz.getConstructor();
                return (ApplicationBuilder) constructor.newInstance();
            } else if (AbstractApplication.class.isAssignableFrom(clazz)) {
                Constructor<?> constructor = clazz.getConstructor();
                return (AbstractApplication) constructor.newInstance();
            } else {
                throw new IllegalArgumentException("Application class "+clazz+" must extend one of ApplicationBuilder or AbstractApplication");
            }
        }

        @VisibleForTesting
        void startAllApps(Collection<? extends Application> applications, List<? extends Location> brooklynLocations) {
            if (applications.size() > 0) {
                for (Application application : applications) {
                    // Start application
                    if (application!=null) {
                        log.info("Starting brooklyn application {} in location{} {}", new Object[] { application, brooklynLocations.size()!=1?"s":"", brooklynLocations });
                        if (!noShutdownOnExit) Entities.invokeStopOnShutdown(application);
                        try {
                            ((Startable)application).start(brooklynLocations);
                        } catch (Exception e) {
                            log.error("Error starting "+application+": "+e, e);
                        }
                    }
                    
                    if (verbose) {
                        if (application!=null) Entities.dumpInfo(application);
                    }
                }
            } else if (brooklynLocations!=null && !brooklynLocations.isEmpty()) {
                System.err.println("Locations specified without any applications; ignoring");
            }
        }
        
        @VisibleForTesting
        void stopAllApps(Collection<? extends Application> applications) {
            for (Application application : applications) {
                try {
                    ((Startable)application).stop();
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
                    .add("noConsole", noConsole)
                    .add("noShutdwonOnExit", noShutdownOnExit);
        }
    }

    @VisibleForTesting
    static Cli<BrooklynCommand> buildCli() {
        @SuppressWarnings({ "unchecked" })
        CliBuilder<BrooklynCommand> builder = Cli.buildCli("brooklyn", BrooklynCommand.class)
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
