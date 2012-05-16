package brooklyn.cli;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

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
import brooklyn.location.basic.LocationRegistry;
import brooklyn.util.ResourceUtils;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;

public class Main {

    private static final StringBuilder launchHelp = new StringBuilder();
    
    @SuppressWarnings({ "rawtypes" })
    public static void main(String...args) {
        @SuppressWarnings({ "unchecked" })
        CliBuilder<Callable> builder = Cli.buildCli("brooklyn", Callable.class)
                .withDescription("Brooklyn Management Service")
                .withDefaultCommand(Help.class)
                .withCommands(
                        Help.class,
                        Launch.class
                );

        Cli<Callable> brooklynParser = builder.build();
        Help.help(brooklynParser.getMetadata(), Arrays.asList("launch"), launchHelp);

        try {
            Callable<?> command = brooklynParser.parse(args); 
            command.call();
        } catch (ParseException pe) {
            System.err.println("Parse error: " + pe.getMessage());
            System.err.println(launchHelp.toString());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Execution error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static abstract class BrooklynCommand implements Callable<Void> {
        @Option(type = OptionType.GLOBAL, name = { "-v", "--verbose" }, description = "Verbose mode")
        public boolean verbose = false;

        @Option(type = OptionType.GLOBAL, name = { "-q", "--quiet" }, description = "Quiet mode")
        public boolean quiet = false;
    }

    @Command(name = "launch", description = "Starts a brooklyn application. Note that a BROOKLYN_CLASSPATH environment variable needs to be set up beforehand to point to the user application classpath.")
    public static class Launch extends BrooklynCommand {
        @Option(name = { "-a", "--app" }, required = true, title = "application class or file",
                description = "The Application to start. For example my.AppName or file://my/AppName.groovy or classpath://my/AppName.groovy")
        public String app;

        @Beta
        @Option(name = { "-s", "--script" }, title = "script URI",
                description = "EXPERIMENTAL. URI for a Groovy script to parse and load. This script will run before starting the app.")
        public String script = null;
        
        @Option(name = { "-l", "--location", "--locations" }, title = "location list",
                description = "Specifies the locations where the application will be launched.")
        public Collection<String> locations = Arrays.asList("localhost");
        
        @Option(name = { "-p", "--port" }, title = "port number",
                description = "Specifies the port to be used by the Brooklyn Management Console.")
        public int port = 8081;
        
        @Option(name = { "-nc", "--noConsole" },
                description = "Whether to start the web console")
        public boolean noConsole = false;

        @Override
        public Void call() throws Exception {
            
            if (verbose) {
                System.out.println("Launching brooklyn app: "+app+" in "+Iterables.toString(locations));
            }
            
            ResourceUtils utils = new ResourceUtils(this);
            ClassLoader parent = utils.getLoader();
            GroovyClassLoader loader = new GroovyClassLoader(parent);
            
            // Get an instance of the brooklyn app
            AbstractApplication application = loadApplicationFromClasspathOrParse(utils, loader, app);
            
            //First, run a setup script if the user has provided one
            if (script != null) {
                String content = utils.getResourceAsString(script);
                GroovyShell shell = new GroovyShell(loader);
                shell.evaluate(content);
            }
            
            // Figure out the brooklyn location(s) where to launch the application
            List<Location> brooklynLocations = new LocationRegistry().getLocationsById(locations);
            
            // Start the application
            BrooklynLauncher.manage(application, port);
            application.start(brooklynLocations);
            
            if (verbose) {
                Entities.dumpInfo(application);
            }
            
            return null;
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
                appClass = loader.loadClass(app, true, false);
            } catch (ClassNotFoundException cnfe) { // Not a class on the classpath
                String content = utils.getResourceAsString(app);
                appClass = loader.parseClass(content);
            }
            
            Constructor<?> constructor = appClass.getConstructor();
            return (AbstractApplication) constructor.newInstance();
        }
        
    }
}
