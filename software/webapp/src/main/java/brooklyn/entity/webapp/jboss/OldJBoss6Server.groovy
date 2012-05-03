package brooklyn.entity.webapp.jboss

import java.util.Collection
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.lifecycle.legacy.SshBasedAppSetup
import brooklyn.entity.webapp.OldJavaWebApp
import brooklyn.event.adapter.ConfigSensorAdapter
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag

/**
 * JBoss web application server.
 */
public class OldJBoss6Server extends OldJavaWebApp {
    private static final Logger log = LoggerFactory.getLogger(OldJBoss6Server.class)

	@SetFromFlag("serverProfile")
    public static final BasicAttributeSensorAndConfigKey<String>  SERVER_PROFILE = [ String, "jboss.serverProfile", "Profile used when running server", "standard" ] 
    @SetFromFlag("portIncrement")
    public static final BasicAttributeSensorAndConfigKey<Integer> PORT_INCREMENT = [ Integer, "jboss.portincrement", "Increment to be used for all jboss ports", 0 ]
	@SetFromFlag("clusterName")
    public static final BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = [ String, "jboss.clusterName", "Identifier used to group JBoss instances", "" ]
  
    public OldJBoss6Server(Map flags=[:], Entity owner=null) {
        super(flags, owner)
    }

    public SshBasedAppSetup newDriver(SshMachineLocation loc) {
        return JBoss6SshSetup.newInstance(this, loc);
    }
    
    public void connectSensors() {
		super.connectSensors();
		
		sensorRegistry.register(new ConfigSensorAdapter());
		
		JmxSensorAdapter jmx = sensorRegistry.register(new JmxSensorAdapter(period: 500*TimeUnit.MILLISECONDS));
		jmx.objectName("jboss.web:type=GlobalRequestProcessor,name=http-*").with {
			attribute("errorCount").subscribe(ERROR_COUNT)
			attribute("requestCount").subscribe(REQUEST_COUNT)
			attribute("processingTime").subscribe(TOTAL_PROCESSING_TIME)
		}
		jmx.objectName("jboss.system:type=Server").attribute("Started").subscribe(SERVICE_UP)
    }
}
