package brooklyn

import java.util.List
import java.util.ArrayList

import com.google.common.base.Preconditions;

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
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
    
    //TODO check params
    public static void main(String[] argv) {
        // Parse args to get locations and console port
        List args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081)
        String script = CommandLineUtil.getCommandLineOption(args, "--script")
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: [ DEFAULT_LOCATION ])
        File file = new File(Preconditions.checkNotNull(script, "script"))
        Preconditions.checkState(file.exists(), "File ${file.path} does not exist")

        // Load the application
        ClassLoader parent = Main.class.getClassLoader()
		GroovyClassLoader loader = new GroovyClassLoader(parent)
		Class groovyClass = loader.parseClass(file)

        // Start the application
		AbstractApplication application = (AbstractApplication) groovyClass.newInstance()
        BrooklynLauncher.manage(application, port)
        application.start(locations)
        Entities.dumpInfo(application)
    }
}
