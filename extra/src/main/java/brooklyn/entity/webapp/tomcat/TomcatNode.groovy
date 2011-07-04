package brooklyn.entity.webapp.tomcat

import java.util.concurrent.TimeUnit;
import javax.management.InstanceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.webapp.JavaWebApp;
import brooklyn.event.EntityStartException;
import brooklyn.event.adapter.JmxSensorAdapter;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.basic.SshBasedJavaWebAppSetup;


import brooklyn.util.internal.Repeater
import brooklyn.location.basic.SshMachineLocation;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Tomcat instance.
 */
public class TomcatNode extends JavaWebApp {
    
    private static final Logger log = LoggerFactory.getLogger(TomcatNode.class)
    
    public static final BasicAttributeSensor<Integer> TOMCAT_SHUTDOWN_PORT = [ Integer, "webapp.tomcat.shutdownPort", "Port to use for shutting down" ];
    
    public TomcatNode(Map properties=[:]) {
        super(properties);
    }

    public SshBasedJavaWebAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return new Tomcat7SshSetup(this, machine)
    }
    
    public void initJmxSensors() {
        jmxAdapter.addSensor(ERROR_COUNT, "Catalina:type=GlobalRequestProcessor,name=\"http-*\"", "errorCount")
        jmxAdapter.addSensor(REQUEST_COUNT, "Catalina:type=GlobalRequestProcessor,name=\"http-*\"", "requestCount")
        jmxAdapter.addSensor(TOTAL_PROCESSING_TIME, "Catalina:type=GlobalRequestProcessor,name=\"http-*\"", "processingTime")
    }

    public void waitForHttpPort() {
        jmxAdapter = new JmxSensorAdapter(this, 60*1000)
        new Repeater("Wait for Tomcat JMX").repeat({}).every(1, TimeUnit.SECONDS).until({jmxAdapter.isConnected()}).limitIterationsTo(30).run();

        String state = null;
        new Repeater("Wait for Tomcat HTTP port status")
            .repeat({
                int port = getAttribute(HTTP_PORT)?:-1
                if (port <= 0) return;
                try {
                    Map connectorAttrs = jmxAdapter.getAttributes("Catalina:type=Connector,port=$port")
                    state = connectorAttrs['stateName']
                } catch (InstanceNotFoundException e) {
                    state = "InstanceNotFound"
                }
            })
            .every(1, TimeUnit.SECONDS)
            .until({
                state == "STARTED" || state == "FAILED" || state == "InstanceNotFound"
            })
            .limitIterationsTo(30)
            .run();

        if (state == "STARTED") {
            updateAttribute(NODE_UP, true)
        } else {
            updateAttribute(NODE_UP, false)
            throw new EntityStartException("Tomcat connector for port "+getAttribute(HTTP_PORT)+" is in state $state")
        }

    }
    
    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['tomcatHttpPort']
    }

}
