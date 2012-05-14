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
import brooklyn.util.ResourceUtils
import brooklyn.config.BrooklynProperties


/** 
 * Starts Brooklyn with various options 
 */
public class Main {
    public static final String DEFAULT_LOCATION = "localhost"

    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()
    
    public static void main(String[] argv) {
        
        AbstractApplication application
        
        // Parse command line args
        List args = new ArrayList(Arrays.asList(argv))
        int numArgs = args.size()
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081)
        String script = CommandLineUtil.getCommandLineOption(args, "--script")
        String app = CommandLineUtil.getCommandLineOption(args, "--app")
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION)
        
        // If no args, then print usage info
        if(numArgs<=1) {
            System.out.println("Usage:")
            System.out.println("% export BROOKLYN_CLASSPATH=/path/to/my/application/classes")
            System.out.println("% ./brooklyn.sh --script GROOVY_FILE  [ --location COMMA_SEPARATED_LIST ]")
            System.out.println("% ./brooklyn.sh --app CLASS_NAME | GROOVY_CLASS_FILE_PATH | GROOVY_CLASS_URL [ --location COMMA_SEPARATED_LIST ]")
            System.exit(0)
        }
        
        // Figure out the location(s) where to launch the application
        List<String> locs = Arrays.asList(location.split("\\s*,\\s*"));
        List<Location> locations = new LocationRegistry().getLocationsById(locs)
        
        // Get a Brooklyn application instance according to the supplied cli args
        if(script) { // We are launching an application provided via a .groovy script
            // TODO: --script argument has different meaning now, needs to be interpreted as a script
            /*
            File file = new File(script)
            Preconditions.checkState(file.exists(), "File ${file.path} does not exist")
            ClassLoader parent = Main.class.getClassLoader()
            GroovyClassLoader loader = new GroovyClassLoader(parent)
            Class groovyClass = loader.parseClass(file)
            application = (AbstractApplication) groovyClass.newInstance()
            */
            System.out.println("TODO")
            System.exit(0)
        } else if(app) { // We are launching an application
            try { // If this a class name on the classpath then create an instance of it 
                Class<AbstractApplication> clazz = (Class<AbstractApplication>) Class.forName(app)
                Constructor<AbstractApplication> constructor = clazz.getConstructor()
                application = constructor.newInstance() //TODO call constructor with Map argument, might need to use eval
            } catch(ClassNotFoundException) { // Not a class on the classpath
                try { // Try loading a .groovy class provided as a file or URL
                    ResourceUtils utils = new ResourceUtils(this)
                    InputStream stream = utils.getResourceFromUrl(app)
                    ClassLoader parent = utils.getLoader()
                    GroovyClassLoader loader = new GroovyClassLoader(parent)
                    Class groovyClass = loader.parseClass(stream)
                    application = (AbstractApplication) groovyClass.newInstance()
                } catch(Exception) { // Looks like the provided string is not a file or URL either
                    System.out.println("The following --app argument is not valid: ${app}")
                    System.exit(1)
                }
            }
        } else { // Signal problem to the user and exit
            System.out.println("Must specify either the --script option or the --app option")
            System.exit(1)
        }

        // Start the application
        BrooklynLauncher.manage(application, port)
        application.start(locations)
        Entities.dumpInfo(application)
    }
}
