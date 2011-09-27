package brooklyn.entity.webapp.jboss

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup
import brooklyn.util.internal.LanguageUtils

/**
 * JBoss web application server.
 */
public class JBoss6Server extends JavaWebApp {
    private static final Logger log = LoggerFactory.getLogger(JBoss6Server.class)

    public static final ConfiguredAttributeSensor<String>  SERVER_PROFILE = [ String, "jboss.serverProfile", "Profile used when running server", "standard" ] 
    public static final ConfiguredAttributeSensor<Integer> PORT_INCREMENT = [ Integer, "jboss.portincrement", "Increment to be used for all jboss ports", 0 ]
    public static final ConfiguredAttributeSensor<String> CLUSTER_NAME = [ String, "jboss.clusterName", "Identifier used to group JBoss instances", "" ]
  
    public JBoss6Server(Map flags=[:], Entity owner=null) {
        super(flags, owner)

        setConfigIfValNonNull(PORT_INCREMENT.configKey, flags.portIncrement)
        setConfigIfValNonNull(CLUSTER_NAME.configKey, flags.clusterName)
        setConfigIfValNonNull(SERVER_PROFILE.configKey, flags.serverProfile)
    }

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation loc) {
        return JBoss6SshSetup.newInstance(this, loc);
    }

    @Override
    protected void initAttributes() {
        super.initAttributes()
        setAttribute(PORT_INCREMENT)
        setAttribute(CLUSTER_NAME)
        setAttribute(SERVER_PROFILE)
    }
    
    @Override
    public void addJmxSensors() {
        attributePoller.addSensor(ERROR_COUNT, jmxAdapter.newAttributeProvider("jboss.web:type=GlobalRequestProcessor,name=http-*", "errorCount"))
        attributePoller.addSensor(REQUEST_COUNT, jmxAdapter.newAttributeProvider("jboss.web:type=GlobalRequestProcessor,name=http-*", "requestCount"))
        attributePoller.addSensor(TOTAL_PROCESSING_TIME, jmxAdapter.newAttributeProvider("jboss.web:type=GlobalRequestProcessor,name=http-*", "processingTime"))
        attributePoller.addSensor(SERVICE_UP, jmxAdapter.newAttributeProvider("jboss.system:type=Server", "Started"))
    }
}
