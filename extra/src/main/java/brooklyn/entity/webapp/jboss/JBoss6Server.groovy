package brooklyn.entity.webapp.jboss

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

/**
 * JBoss web application server.
 */
public class JBoss6Server extends JavaWebApp {
    private static final Logger log = LoggerFactory.getLogger(JBoss6Server.class)

    public static BasicConfigKey<String>  SUGGESTED_SERVER_PROFILE = 
            [ String, "jboss.serverProfile", "Profile used when running server" ] 
    public static BasicConfigKey<Integer> SUGGESTED_PORT_INCREMENT = 
            [ Integer, "jboss.portincrement", "Increment to be used for all jboss ports" ]
    public static BasicConfigKey<Integer> SUGGESTED_CLUSTER_NAME =
            [ String, "jboss.clusterName", "Identifier used to group JBoss instances" ]
  
    public static final BasicAttributeSensor<Integer> PORT_INCREMENT = [ Integer, "webapp.portIncrement", "Increment added to default JBoss ports" ];
            
    public JBoss6Server(Map flags=[:], Entity owner=null) {
        super(flags, owner)

        setConfigIfValNonNull(SUGGESTED_PORT_INCREMENT, flags.portIncrement)
        setConfigIfValNonNull(SUGGESTED_CLUSTER_NAME, flags.clusterName)
        setConfigIfValNonNull(SUGGESTED_SERVER_PROFILE, flags.serverProfile)
    }

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation loc) {
        return JBoss6SshSetup.newInstance(this, loc);
    }
    
    public void addJmxSensors() {
        attributePoller.addSensor(ERROR_COUNT, jmxAdapter.newAttributeProvider("jboss.web:type=GlobalRequestProcessor,name=http-*", "errorCount"))
        attributePoller.addSensor(REQUEST_COUNT, jmxAdapter.newAttributeProvider("jboss.web:type=GlobalRequestProcessor,name=http-*", "requestCount"))
        attributePoller.addSensor(TOTAL_PROCESSING_TIME, jmxAdapter.newAttributeProvider("jboss.web:type=GlobalRequestProcessor,name=http-*", "processingTime"))
        attributePoller.addSensor(SERVICE_UP, jmxAdapter.newAttributeProvider("jboss.system:type=Server", "Started"))
    }
}
