package brooklyn.entity.webapp.tomcat

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import java.util.Map;

import brooklyn.entity.basic.BasicConfigurableEntityFactory;

import java.util.concurrent.TimeUnit


import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.UsesJmx;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.adapter.ConfigSensorAdapter
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.PortRange
import brooklyn.location.basic.PortRanges
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag

/**
 * An {@link brooklyn.entity.Entity} that represents a single Tomcat instance.
 */
public class TomcatServer extends JavaWebAppSoftwareProcess implements JavaWebAppService, UsesJmx {
    private static final Logger log = LoggerFactory.getLogger(TomcatServer.class)
    
    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [ SoftwareProcessEntity.SUGGESTED_VERSION, "7.0.27" ]
    
    /**
     * Tomcat insists on having a port you can connect to for the sole purpose of shutting it down.
     * Don't see an easy way to disable it; causes collisions in its out-of-the-box location of 8005,
     * so override default here to a high-numbered port.
     */
    @SetFromFlag("shutdownPort")
    public static final PortAttributeSensorAndConfigKey SHUTDOWN_PORT = 
            [ "tomcat.shutdownport", "Suggested shutdown port", PortRanges.fromString("31880+") ]
    
    /** 
     * @deprecated will be deleted in 0.5. Use SHUTDOWN_PORT
     */
    @Deprecated
    public static final BasicConfigKey<PortRange> SUGGESTED_SHUTDOWN_PORT = 
            [ PortRange, "tomcat.shutdownport.deprecated", "Suggested shutdown port" ]

    /** 
     * @deprecated will be deleted in 0.5. Use SHUTDOWN_PORT
     */
    @Deprecated
    public static final BasicAttributeSensor<Integer> TOMCAT_SHUTDOWN_PORT =
            [ Integer, "webapp.tomcat.shutdownPort.deprecated", "Port to use for shutting down" ]
    
    public static final BasicAttributeSensor<String> CONNECTOR_STATUS =
            [String, "webapp.tomcat.connectorStatus", "Catalina connector state name"]
    
    /**
     * @deprecated will be deleted in 0.5. Unsupported in 0.4.0.
     */
    @Deprecated
    //TODO property copied from legacy JavaApp, but underlying implementation has not been
    public static final MapConfigKey<Map> PROPERTY_FILES = [ Map, "java.properties.environment", "Property files to be generated, referenced by an environment variable" ]
    
    private JmxSensorAdapter jmx
    
    public TomcatServer(Map flags=[:], Entity owner=null) {
        super(flags, owner)
    }

    @Override   
    public void connectSensors() {
        super.connectSensors();
        
        sensorRegistry.register(new ConfigSensorAdapter());

        jmx = sensorRegistry.register(new JmxSensorAdapter(period: 500*MILLISECONDS));
        jmx.objectName("Catalina:type=GlobalRequestProcessor,name=\"http-*\"").with {
            attribute("errorCount").subscribe(ERROR_COUNT)
            attribute("requestCount").subscribe(REQUEST_COUNT)
            attribute("processingTime").subscribe(TOTAL_PROCESSING_TIME)
        }
        
        jmx.objectName("Catalina:type=Connector,port=${getAttribute(HTTP_PORT)}").with {
            attribute("stateName").with {
                subscribe(CONNECTOR_STATUS)
                subscribe(SERVICE_UP) { it == "STARTED" }
                        // TODO set to "InstanceNotFound" if can't connect to MBean
                        //      e.g..onError( {"InstanceNotFound"} )
            }
        }
    }
    
    @Override    
    protected void postActivation() {
        super.postActivation()

        // wait for MBeans to be available, rather than just the process to have started
        LOG.info("Waiting for {} up, via {}", this, jmx?.getConnectionUrl())
        waitForServiceUp(5*MINUTES)
    }
    

    public Tomcat7SshDriver newDriver(SshMachineLocation machine) {
        return new Tomcat7SshDriver(this, machine)
    }
}

public class TomcatServerFactory extends BasicConfigurableEntityFactory<TomcatServer> {
    public TomcatServerFactory(Map flags=[:]) {
        super(flags, TomcatServer)
    }
}
