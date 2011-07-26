package brooklyn.demo

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.launcher.WebAppRunner
import brooklyn.location.Location
import brooklyn.management.internal.AbstractManagementContext

public class Demo {
    public static final Logger LOG = LoggerFactory.getLogger(Demo)

    public static final List<String> DEFAULT_LOCATIONS = ["eu-west-1", "ap-northeast-1", "monterey-east" ]

    public static void main(String[] argv) {
        // Parse arguments for location ids and resolve each into a location
        List<String> ids = argv.length == 0 ? DEFAULT_LOCATIONS : Arrays.asList(argv)
        println "Starting in locations: "
        ids.each { println it }
        List<Location> locations = ids.collect { String location ->
	        if (Locations.AWS_REGIONS.contains(location)) {
	            Locations.lookupAwsRegion(location)     
	        } else if (Locations.MONTEREY_EAST == location) {
		        Locations.newMontereyEastLocation()
	        } else if (Locations.EDINBURGH == location) {
		        Locations.newMontereyEdinburghLocation()
	        }
        }

        // Initialize the Spring Travel application entity
        SpringTravel app = new SpringTravel(name:'brooklyn-wide-area-demo',
                                            displayName:'Brooklyn Wide-Area Spring Travel Demo Application')

        // Locate the management context
        AbstractManagementContext context = app.getManagementContext()
        context.manage(app)

        // Start the web console service
        WebAppRunner web
        try {
            web = new WebAppRunner(context)
            web.start()
        } catch (Exception e) {
            LOG.warn("Failed to start web-console", e)
        }
        
        // Start the application
        app.start(locations)
        
        addShutdownHook {
            app?.stop()
            web?.stop()
        }
    }
}
