package brooklyn.entity.webapp.tomcat

import java.util.concurrent.TimeUnit

import javax.management.InstanceNotFoundException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.EntityStartException
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.adapter.ValueProvider
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.util.SshBasedJavaWebAppSetup
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.Repeater

/**
 * An {@link brooklyn.entity.Entity} that represents a single Tomcat instance.
 */
public class TomcatNode extends JavaWebApp {
    
    private static final Logger log = LoggerFactory.getLogger(TomcatNode.class)
    
    public static final BasicConfigKey<Integer> SUGGESTED_SHUTDOWN_PORT = [Integer, "tomcat.shutdownport", "Suggested shutdown port" ]
    
    public static final BasicAttributeSensor<Integer> TOMCAT_SHUTDOWN_PORT = [ Integer, "webapp.tomcat.shutdownPort", "Port to use for shutting down" ];
    public static final BasicAttributeSensor<String> CONNECTOR_STATUS = [String, "webapp.tomcat.connectorStatus", "Catalina connector state name"]
    
    public TomcatNode(Map properties=[:]) {
        super(properties);
    }

    public SshBasedJavaWebAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return Tomcat7SshSetup.newInstance(this, machine)
    }
    
    public void initJmxSensors() {
        attributePoller.addSensor(ERROR_COUNT, jmxAdapter.newValueProvider("Catalina:type=GlobalRequestProcessor,name=\"http-*\"", "errorCount"))
        attributePoller.addSensor(REQUEST_COUNT, jmxAdapter.newValueProvider("Catalina:type=GlobalRequestProcessor,name=\"http-*\"", "requestCount"))
        attributePoller.addSensor(TOTAL_PROCESSING_TIME, jmxAdapter.newValueProvider("Catalina:type=GlobalRequestProcessor,name=\"http-*\"", "processingTime"))
        attributePoller.addSensor(CONNECTOR_STATUS, { computeConnectorStatus() } as ValueProvider)
        attributePoller.addSensor(NODE_UP, { computeNodeUp() } as ValueProvider)
    }

    public void waitForHttpPort() {
        String state = null;
        new Repeater("Wait for Tomcat HTTP port status")
            .repeat({
                state = getAttribute(CONNECTOR_STATUS)
            })
            .every(1, TimeUnit.SECONDS)
            .until({
                state == "STARTED" || state == "FAILED" || state == "InstanceNotFound"
            })
            .limitIterationsTo(30)
            .run();

        if (state != "STARTED") {
            throw new EntityStartException("Tomcat connector for port "+getAttribute(HTTP_PORT)+" is in state $state")
        }
    }
    
    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['tomcatHttpPort']
    }

    // state values include: STARTED, FAILED, InstanceNotFound
    protected String computeConnectorStatus() {
        int port = getAttribute(HTTP_PORT)
        ValueProvider<String> rawProvider = jmxAdapter.newValueProvider("Catalina:type=Connector,port=$port", "stateName")
        try {
            return rawProvider.compute()
        } catch (InstanceNotFoundException e) {
            return "InstanceNotFound"
        }
    }
    
    protected boolean computeNodeUp() {
        String connectorStatus = getAttribute(CONNECTOR_STATUS)
        if (connectorStatus == "STARTED") {
            return true
        } else {
            return false
        }
    }
}
