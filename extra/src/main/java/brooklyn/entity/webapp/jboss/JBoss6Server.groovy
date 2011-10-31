package brooklyn.entity.webapp.jboss

import groovy.lang.Closure;

import java.util.Collection
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.adapter.ConfigSensorAdapter;
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup
import brooklyn.util.flags.SetFromFlag

/**
 * JBoss web application server.
 */
public class JBoss6Server extends JavaWebApp {
    private static final Logger log = LoggerFactory.getLogger(JBoss6Server.class)

	@SetFromFlag("serverProfile")
    public static final ConfiguredAttributeSensor<String>  SERVER_PROFILE = [ String, "jboss.serverProfile", "Profile used when running server", "standard" ] 
    @SetFromFlag("portIncrement")
    public static final ConfiguredAttributeSensor<Integer> PORT_INCREMENT = [ Integer, "jboss.portincrement", "Increment to be used for all jboss ports", 0 ]
	@SetFromFlag("clusterName")
    public static final ConfiguredAttributeSensor<String> CLUSTER_NAME = [ String, "jboss.clusterName", "Identifier used to group JBoss instances", "" ]
  
    public JBoss6Server(Map flags=[:], Entity owner=null) {
        super(flags, owner)
    }

	public void start(Collection<Location> locations) {
		super.start(locations)
		connectSensors()
		sensorRegistry.activateAdapters()
	}

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation loc) {
        return JBoss6SshSetup.newInstance(this, loc);
    }
    
    public void connectSensors() {
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
