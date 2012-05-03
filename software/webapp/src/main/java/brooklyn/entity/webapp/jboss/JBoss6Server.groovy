package brooklyn.entity.webapp.jboss

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.with

import java.util.Map
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.BasicConfigurableEntityFactory
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.UsesJmx
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess
import brooklyn.event.adapter.ConfigSensorAdapter
import brooklyn.event.adapter.HttpSensorAdapter
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag


/**
 *  FIXME propFiles are never being generated!
 */
public class JBoss6Server extends JavaWebAppSoftwareProcess implements JavaWebAppService, UsesJmx {

	public static final Logger log = LoggerFactory.getLogger(JBoss6Server.class)

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [ SoftwareProcessEntity.SUGGESTED_VERSION, "6.0.0.Final" ]
	@SetFromFlag("portIncrement")
	public static final BasicAttributeSensorAndConfigKey<Integer> PORT_INCREMENT = [ Integer, "jboss.portincrement", "Increment to be used for all jboss ports", 0 ]
	@SetFromFlag("clusterName")
	public static final BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = [ String, "jboss.clusterName", "Identifier used to group JBoss instances", "" ]	  

	//copied from JavaApp
	public static final MapConfigKey<Map> PROPERTY_FILES = [ Map, "java.properties.environment", "Property files to be generated, referenced by an environment variable" ]
	
    public JBoss6Server(Map flags=[:], Entity owner=null) {
        super(flags, owner)
		log.info "Running the refactored JBoss6Server !!!!!!!"
    }

	@Override	
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

    public JBoss6SshDriver newDriver(SshMachineLocation machine) {
        return new JBoss6SshDriver(this, machine)
    }
}

// Do we need this? We are currently not using it.
public class JBoss6ServerFactory extends BasicConfigurableEntityFactory<JBoss6Server> {
    public JBoss6ServerFactory(Map flags=[:]) {
        super(flags, JBoss6Server)
    }
}
