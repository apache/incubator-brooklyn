package brooklyn.cli;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.Location;
import brooklyn.location.basic.CommandLineLocations;
import brooklyn.location.basic.LocationRegistry;
import brooklyn.util.ResourceUtils;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    // Error codes
    public static final int PARSE_ERROR = 1;
    public static final int EXECUTION_ERROR = 2;

    public static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String...args) {
        Cli<BrooklynCommand> parser = buildCli();
        try {
            log.debug("Parsing command line arguments: {}",args);
            BrooklynCommand command = parser.parse(args); 
            log.debug("Executing command: {}", command);
            command.call();
        } catch (ParseException pe) { // looks like the user typed it wrong
            System.err.println("Parse error: " + pe.getMessage()); // display error
            System.err.println(getUsageInfo(parser)); // display cli help
            System.exit(PARSE_ERROR);
        } catch (Exception e) { // unexpected error during command execution
            log.error("Execution error: {}\n{}" + e.getMessage(),e.getStackTrace());
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

    @Command(name = "help", description = "Display help information about brooklyn")
    public static class HelpCommand extends BrooklynCommand {
        @Override
        public Void call() throws Exception {
            log.debug("Invoked help command");
            return help.call();
        }
    }
    
    @Command(name = "launch", description = "Starts a brooklyn application. Note that a BROOKLYN_CLASSPATH environment variable needs to be set up beforehand to point to the user application classpath.")
    public static class LaunchCommand extends BrooklynCommand {
        @Option(name = { "-a", "--app" }, required = true, title = "application class or file",
                description = "The Application to start. For example my.AppName or file://my/AppName.groovy or classpath://my/AppName.groovy")
        public String app;

        @Beta
        @Option(name = { "-s", "--script" }, title = "script URI",
                description = "EXPERIMENTAL. URI for a Groovy script to parse and load. This script will run before starting the app.")
        public String script = null;
        
        @Option(name = { "-l", "--location", "--locations" }, title = "location list",
                description = "Specifies the locations where the application will be launched. You can specify more than one location like this: \"loc1,loc2,loc3\"")
        public String locations;
        
        @Option(name = { "-p", "--port" }, title = "port number",
                description = "Specifies the port to be used by the Brooklyn Management Console.")
        public int port = 8081;
        
        @Option(name = { "-nc", "--noConsole" },
                description = "Whether to start the web console")
        public boolean noConsole = false;

        @Option(name = { "-ns", "--noShutdownOnExit" },
                description = "Whether to stop the application when the JVM exits")
        public boolean noShutdownOnExit = false;
        
        /**
         * Note that this is a temporrary workaround to allow  for runnig the
         * brooklyn-whirr example.
         * 
         * This will be replaced by more powerful CLI control for running processes,
         * to send shutdown and other commands to brooklyn.
         * 
         * Without using this flag you get a java.util.concurrent.RejectedExecutionException
         * because the brooklyn and whirr shutdown hooks get executed in parallel.
         * This is how it looks like: {@linktourl https://gist.github.com/47066f72d6f6f79b953e}
         */
        @Beta
        @Option(name = { "-sk", "--stopOnKeyPress" },
                description = "After the application gets started, brooklyn will wait for a key press to stop it.")
        public boolean stopOnKeyPress = false;

        @Override
        public Void call() throws Exception {

            log.debug("Invoked launch command");

            if (verbose) {
                System.out.println("Launching brooklyn app: "+app+" in "+locations);
            }
            
            ResourceUtils utils = new ResourceUtils(this);
            ClassLoader parent = utils.getLoader();
            GroovyClassLoader loader = new GroovyClassLoader(parent);
            
            // Get an instance of the brooklyn app
            log.debug("Load the user's application: {}", app);
            AbstractApplication application = loadApplicationFromClasspathOrParse(utils, loader, app);
            
            //First, run a setup script if the user has provided one
            if (script != null) {
                log.debug("Running the user povided script: {}", script);
                String content = utils.getResourceAsString(script);
                GroovyShell shell = new GroovyShell(loader);
                shell.evaluate(content);
            }
            
            // Figure out the brooklyn location(s) where to launch the application
            Iterable<String> parsedLocations = Splitter.on(",").split(locations);
            List<Location> brooklynLocations = new LocationRegistry().getLocationsById(
                    (parsedLocations==null || Iterables.isEmpty(parsedLocations)) ? ImmutableSet.of(CommandLineLocations.LOCALHOST) : parsedLocations);
            
            // Start the application
            log.info("Adding application under brooklyn management");
            BrooklynLauncher.manage(application, port, !noShutdownOnExit, !noConsole);
            log.info("Starting brooklyn application {} in location(s) {}", app, brooklynLocations);
            application.start(brooklynLocations);
            
            if (verbose) {
                Entities.dumpInfo(application);
            }
            
            if(stopOnKeyPress){
            	// Wait for the user to type a key
            	log.info("Application started. Press return to stop.");
            	System.in.read();
            	application.stop();
            } else {
                // Block forever so that Brooklyn doesn't exit (until someone does cntrl-c or kill)
                log.info("Launched application; now blocking to wait for cntrl-c or kill");
                waitUntilInterrupted();
            }
            return null;
        }

        private synchronized void waitUntilInterrupted() {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // exit gracefully
            }
        }
        
        /**
         * Helper method that gets an instance of a brooklyn application
         */
        @VisibleForTesting
        AbstractApplication loadApplicationFromClasspathOrParse(ResourceUtils utils, GroovyClassLoader loader, String app) 
                throws SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, 
                IllegalAccessException, InvocationTargetException {
            Class<?> appClass;
            try {
                log.debug("Trying to load application as class on classpath: {}", app);
                appClass = loader.loadClass(app, true, false);
            } catch (ClassNotFoundException cnfe) { // Not a class on the classpath
                log.debug("Loading \"{}\" as class on classpath failed, now trying as .groovy source file",app);
                String content = utils.getResourceAsString(app);
                appClass = loader.parseClass(content);
            }
            
            Constructor<?> constructor = appClass.getConstructor();
            return (AbstractApplication) constructor.newInstance();
        }

        @Override
        public ToStringHelper string() {
            return super.string()
                    .add("app", app)
                    .add("script", script)
                    .add("location", locations)
                    .add("port", port)
                    .add("noConsole",noConsole)
                    .add("noShutdwonOnExit",noShutdownOnExit);
        }
    }

    @VisibleForTesting
    static Cli<BrooklynCommand> buildCli() {
        @SuppressWarnings({ "unchecked" })
        CliBuilder<BrooklynCommand> builder = Cli.buildCli("brooklyn", BrooklynCommand.class)
                .withDescription("Brooklyn Management Service")
                .withDefaultCommand(HelpCommand.class)
                .withCommands(
                        HelpCommand.class,
                        LaunchCommand.class
                );

        return builder.build();
    }

    static String getUsageInfo(Cli<BrooklynCommand> parser) {
        StringBuilder help = new StringBuilder();
        help.append("\n");
        Help.help(parser.getMetadata(), ImmutableList.of("brooklyn"),help);
        help.append("See 'brooklyn help <command>' for more information on a specific command.");
        return help.toString();
    }

}
