package brooklyn.demo

import java.util.List
import java.util.ArrayList

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.messaging.amqp.AmqpServer
import brooklyn.entity.messaging.qpid.QpidBroker
import brooklyn.event.basic.DependentConfiguration
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry
import brooklyn.location.basic.CommandLineLocations
import brooklyn.util.CommandLineUtil

/** This example starts a Qpid broker, waits for a keypress, then stops it. */
public class StandaloneBrokerExample extends AbstractApplication {

    public static final String DEFAULT_LOCATION = "localhost"
    public static final String CUSTOM_CONFIG_PATH = "classpath://custom-config.xml"
    public static final String PASSWD_PATH = "classpath://passwd"
    public static final String QPID_BDBSTORE_JAR_PATH = "classpath://qpid-bdbstore-0.14.jar"
    public static final String BDBSTORE_JAR_PATH = "classpath://je-5.0.34.jar"
        
    public static void main(String[] argv) {
        // Parse args to get locations and console port
        List args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: [ DEFAULT_LOCATION ])

        // Create application
        StandaloneBrokerExample app = new StandaloneBrokerExample(displayName: "Brooklyn Messaging Broker Example")
        
	    // Configure the Qpid broker entity
	    QpidBroker broker = new QpidBroker(app,
	        amqpPort:5672,
	        amqpVersion:AmqpServer.AMQP_0_10,
	        runtimeFiles:[ (QpidBroker.CONFIG_XML):CUSTOM_CONFIG_PATH,
                           (QpidBroker.PASSWD):PASSWD_PATH,
                           ("lib/opt/qpid-bdbstore-0.14.jar"):QPID_BDBSTORE_JAR_PATH,
                           ("lib/opt/je-5.0.34.jar"):BDBSTORE_JAR_PATH ],
	        queue:"testQueue")
	
        // Start the application
        log.info("starting application")
        BrooklynLauncher.manage(app, port)
        app.start(locations)
        Entities.dumpInfo(app)
    }
}
