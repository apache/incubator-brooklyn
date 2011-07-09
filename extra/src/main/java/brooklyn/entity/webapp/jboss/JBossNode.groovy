package brooklyn.entity.webapp.jboss

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.util.SshBasedJavaWebAppSetup
import brooklyn.location.basic.SshMachineLocation

/**
 * JBoss web application server.
 */
public class JBossNode extends JavaWebApp {
    private static final Logger log = LoggerFactory.getLogger(JBossNode.class)

    public static BasicConfigKey<String>  SUGGESTED_SERVER_PROFILE = [ String, "jboss.serverProfile", "Profile used when running server" ] 
    public static BasicConfigKey<Integer> SUGGESTED_PORT_INCREMENT = [ Integer, "jboss.portincrement", "Increment to be used for all jboss ports" ]
    
    // Jboss specific
    public static final BasicAttributeSensor<Integer> PORT_INCREMENT = [ Integer, "webapp.portIncrement", "Increment added to default JBoss ports" ];

    
    public JBossNode(Map properties=[:]) {
        super(properties);
        
        def portIncrement = properties.portIncrement ?: 0
        if (portIncrement < 0) {
            throw new IllegalArgumentException("JBoss port increment cannot be negative")
        }
        setConfig SUGGESTED_PORT_INCREMENT, portIncrement
    }

    public SshBasedJavaWebAppSetup getSshBasedSetup(SshMachineLocation loc) {
        return JBoss6SshSetup.newInstance(this, loc);
    }
    
    public void initJmxSensors() {
        attributePoller.addSensor(ERROR_COUNT, jmxAdapter.newAttributeProvider("jboss.web:type=GlobalRequestProcessor,name=http-*", "errorCount"))
        attributePoller.addSensor(REQUEST_COUNT, jmxAdapter.newAttributeProvider("jboss.web:type=GlobalRequestProcessor,name=http-*", "requestCount"))
        attributePoller.addSensor(TOTAL_PROCESSING_TIME, jmxAdapter.newAttributeProvider("jboss.web:type=GlobalRequestProcessor,name=http-*", "processingTime"))
        attributePoller.addSensor(NODE_UP, jmxAdapter.newAttributeProvider("jboss.system:type=Server", "Started"))
    }

    public void waitForHttpPort() {
        log.debug "started jboss server: jmxHost {} and jmxPort {}", getAttribute(JMX_HOST), getAttribute(JMX_PORT)
    }
}
