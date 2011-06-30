package brooklyn.entity.webapp.tomcat

import static brooklyn.entity.basic.AttributeDictionary.*
import static brooklyn.entity.basic.ConfigKeyDictionary.*

import java.util.Collection
import java.util.Map

import javax.management.InstanceNotFoundException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.AttributeSensor
import brooklyn.event.EntityStartException
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.basic.SshBasedJavaWebAppSetup
import brooklyn.location.basic.SshMachine
import brooklyn.management.internal.task.Futures

/**
 * An {@link brooklyn.entity.Entity} that represents a single Tomcat instance.
 */
public class TomcatNode extends JavaWebApp {
    
    private static final Logger log = LoggerFactory.getLogger(TomcatNode.class)
    
    public TomcatNode(Map properties=[:]) {
        super(properties);
        propertiesAdapter.addSensor HTTP_PORT, (properties.httpPort ?: -1)
    }
    
    public SshBasedJavaWebAppSetup getSshBasedSetup(SshMachine machine) {
        return new Tomcat7SshSetup(this, machine)
    }
    
    public void initJmxSensors() {
        jmxAdapter.addSensor(ERROR_COUNT, "Catalina:type=GlobalRequestProcessor,name=http-*", "errorCount")
        jmxAdapter.addSensor(REQUEST_COUNT, "Catalina:type=GlobalRequestProcessor,name=http-*", "requestCount")
        jmxAdapter.addSensor(TOTAL_PROCESSING_TIME, "Catalina:type=GlobalRequestProcessor,name=http-*", "processingTime")
        jmxAdapter.addSensor(REQUESTS_PER_SECOND, { computeReqsPerSec() }, 1000L)
    }
    
    public void waitForHttpPort() {
        Futures.when({
                // Wait for the HTTP port to become available
                String state = null
                int port = getAttribute(HTTP_PORT) ?: -1
                for (int attempts = 0; attempts < 30; attempts++) {
                    Map connectorAttrs;
                    try {
                        connectorAttrs = jmxAdapter.getAttributes("Catalina:type=Connector,port=$port")
                        log.info "attempt {} - connector attribs are {}", attempts, connectorAttrs
                        state = connectorAttrs['stateName']
                    } catch (InstanceNotFoundException e) {
                        state = "InstanceNotFound"
                    }
                    updateAttribute(NODE_STATUS, state)
                    log.trace "state: $state"
                    if (state == "FAILED") {
                        updateAttribute(NODE_UP, false)
                        throw new EntityStartException("Tomcat connector for port $port is in state $state")
                    } else if (state == "STARTED") {
                        updateAttribute(NODE_UP, true)
                        break;
                    }
                    Thread.sleep 250
                }
                if (state != "STARTED") {
                    updateAttribute(NODE_UP, false)
                    throw new EntityStartException("Tomcat connector for port $port is in state $state after 30 seconds")
                }
            }, {
                boolean connected = jmxAdapter.isConnected()
                if (connected) log.info "jmx connected"
                connected
            })
    }
    
    private void computeReqsPerSec() {
        def reqs = jmxAdapter.getAttributes("Catalina:type=GlobalRequestProcessor,name=\"*\"")
        log.trace "running computeReqsPerSec - {}", reqs
 
        def curTimestamp = System.currentTimeMillis()
        def curCount = reqs?.requestCount ?: 0
        
        // TODO Andrew reviewing/changing?
        def prevTimestamp = tempWorkings['tmp.reqs.timestamp'] ?: 0
        def prevCount = tempWorkings['tmp.reqs.count'] ?: 0
        tempWorkings['tmp.reqs.timestamp'] = curTimestamp
        tempWorkings['tmp.reqs.count'] = curCount
        log.trace "previous data {} at {}, current {} at {}", prevCount, prevTimestamp, curCount, curTimestamp
        
        // Calculate requests per second
        double diff = curCount - prevCount
        long dt = curTimestamp - prevTimestamp
        
        if (dt <= 0 || dt > 60*1000) {
            diff = -1; 
        } else {
            diff = ((double) 1000.0 * diff) / dt
        }
        int rps = (int) Math.round(diff)
        log.trace "computed $rps reqs/sec over $dt millis"
        updateAttribute(REQUESTS_PER_SECOND, rps)
    }
    
    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['tomcatHttpPort']
    }

}
