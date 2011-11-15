package brooklyn.entity.webapp.tomcat

import java.util.Collection
import java.util.concurrent.TimeUnit

import javax.management.InstanceNotFoundException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.lifecycle.legacy.SshBasedAppSetup;
import brooklyn.entity.webapp.OldJavaWebApp
import brooklyn.event.EntityStartException
import brooklyn.event.adapter.legacy.ValueProvider;
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.Repeater

/**
 * An {@link brooklyn.entity.Entity} that represents a single Tomcat instance.
 */
public class TomcatServer extends OldJavaWebApp {
    private static final Logger log = LoggerFactory.getLogger(TomcatServer.class)
    
    public static final BasicConfigKey<Integer> SUGGESTED_SHUTDOWN_PORT =
            [ Integer, "tomcat.shutdownport", "Suggested shutdown port", 31880 ]
    
    public static final BasicAttributeSensor<Integer> TOMCAT_SHUTDOWN_PORT =
        [ Integer, "webapp.tomcat.shutdownPort", "Port to use for shutting down" ]
    public static final BasicAttributeSensor<String> CONNECTOR_STATUS =
        [String, "webapp.tomcat.connectorStatus", "Catalina connector state name"]
    
    public TomcatServer(Map flags=[:], Entity owner=null) {
        super(flags, owner)
        
        setConfigIfValNonNull(SUGGESTED_SHUTDOWN_PORT, flags.shutdownPort)
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        if (getConfig(SUGGESTED_SHUTDOWN_PORT)) result.add(getConfig(SUGGESTED_SHUTDOWN_PORT))
        return result
    }

    public SshBasedAppSetup newDriver(SshMachineLocation machine) {
        return Tomcat7SshSetup.newInstance(this, machine)
    }
    
    @Override
    public void addJmxSensors() {
		super.addJmxSensors()
        sensorRegistry.addSensor(ERROR_COUNT, 
				jmxAdapter.newAttributeProvider("Catalina:type=GlobalRequestProcessor,name=\"http-*\"", "errorCount"))
        sensorRegistry.addSensor(REQUEST_COUNT, 
				jmxAdapter.newAttributeProvider("Catalina:type=GlobalRequestProcessor,name=\"http-*\"", "requestCount"))
        sensorRegistry.addSensor(TOTAL_PROCESSING_TIME, 
				jmxAdapter.newAttributeProvider("Catalina:type=GlobalRequestProcessor,name=\"http-*\"", "processingTime"))
        sensorRegistry.addSensor(CONNECTOR_STATUS, { computeConnectorStatus() } as ValueProvider)
        sensorRegistry.addSensor(SERVICE_UP, { computeNodeUp() } as ValueProvider)
    }
    
    // state values include: STARTED, FAILED, InstanceNotFound
    protected String computeConnectorStatus() {
        int port = getAttribute(HTTP_PORT)
        ValueProvider<String> rawProvider = jmxAdapter.newAttributeProvider("Catalina:type=Connector,port=$port", "stateName")
        try {
            return rawProvider.compute()
        } catch (InstanceNotFoundException infe) {
            return "InstanceNotFound"
        }
    }
    
    protected boolean computeNodeUp() {
        String connectorStatus = getAttribute(CONNECTOR_STATUS)
        return (connectorStatus == "STARTED")
    }
}
