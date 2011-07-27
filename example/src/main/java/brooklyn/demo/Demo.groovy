package brooklyn.demo

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractApplication
import brooklyn.launcher.WebAppRunner
import brooklyn.location.Location
import brooklyn.management.internal.AbstractManagementContext

public class Demo {
    public static final Logger LOG = LoggerFactory.getLogger(Demo)

    public static final List<String> DEFAULT_LOCATIONS = [ Locations.LOCALHOST ]

    public static void main(String[] argv) {
        Demo demoRunner = new Demo()
        demoRunner.start(argv)
    }

    protected void start(String[] argv) {
        // Parse arguments for location ids and resolve each into a location
        List<String> ids = argv.length == 0 ? DEFAULT_LOCATIONS : Arrays.asList(argv)
        println "Starting in locations: "+ids
        List<Location> locations = Locations.getLocationsById(ids)

        // Initialize the Spring Travel application entity
        AbstractApplication app = createApplication()

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

    protected AbstractApplication createApplication() {
        SpringTravel app = new SpringTravel(name:'brooklyn-wide-area-demo',
                displayName:'Brooklyn Wide-Area Spring Travel Demo Application')
        return app
    }
}
