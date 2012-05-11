package brooklyn

import java.lang.reflect.Constructor
import java.util.List
import java.util.ArrayList
import java.util.Map

import com.google.common.base.Preconditions;

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.trait.Startable
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry
import brooklyn.location.basic.CommandLineLocations
import brooklyn.util.CommandLineUtil
import brooklyn.config.BrooklynProperties

/** Starts Brooklyn with a Groovy application script. */
public class Main {
    public static final String DEFAULT_LOCATION = "localhost"

    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()
    
    public static void main(String[] argv) {
        
        AbstractApplication application
        
        // Parse command line args
        List args = new ArrayList(Arrays.asList(argv))
        int numArgs = args.size()
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081)
        String scriptFileName = CommandLineUtil.getCommandLineOption(args, "--script")
        String applicationClassName = CommandLineUtil.getCommandLineOption(args, "--application")
        String location = CommandLineUtil.getCommandLineOption(args, "--location")
        
        // If no args, then print usage info
        if(numArgs<=1) {
            System.out.println("Usage:")
            System.out.println("% export BROOKLYN_CLASSPATH=/path/to/my/application/classes")
            System.out.println("% ./brooklyn.sh [--script GROOVY_FILE | --application CLASS_NAME] --location COMMA_SEPARATED_LIST")
            System.exit(0)
        }
        
        // Figure out the location(s) where to launch the application
        List<String> locs = Arrays.asList(location.split("\\s*,\\s*"));
        List<Location> locations = new LocationRegistry().getLocationsById(locs ?: [ DEFAULT_LOCATION ])
        
        // Get a Brooklyn application instance according to the supplied cli args
        if(scriptFileName) { // We are launching an application provided via a .groovy script
            File file = new File(scriptFileName)
            Preconditions.checkState(file.exists(), "File ${file.path} does not exist")
            ClassLoader parent = Main.class.getClassLoader()
            GroovyClassLoader loader = new GroovyClassLoader(parent)
            Class groovyClass = loader.parseClass(file)
            application = (AbstractApplication) groovyClass.newInstance()
        } else if(applicationClassName) { // We are launching a compiled application provided in the classpath
            Class<AbstractApplication> clazz = (Class<AbstractApplication>) Class.forName(applicationClassName);
            Constructor<AbstractApplication> constructor = clazz.getConstructor(/*[ Map ] as Class[]*/);
            //Map<String, String> config = [ displayName: applicationClassName ];
            application = constructor.newInstance();
        } else { // Signal problem to the user and exit
            System.out.println("Must specify either the --script option or the --application option")
            System.exit(1)
        }

        // Start the application
        BrooklynLauncher.manage(application, port)
        application.start(locations)
        Entities.dumpInfo(application)
    }
}
