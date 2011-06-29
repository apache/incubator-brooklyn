package brooklyn.entity.webapp.tomcat

import static org.testng.Assert.*

import java.util.Map.Entry
import java.util.concurrent.Semaphore

import javax.management.Attribute
import javax.management.AttributeList
import javax.management.DynamicMBean
import javax.management.MBeanAttributeInfo
import javax.management.MBeanInfo
import javax.management.MBeanServer
import javax.management.MBeanServerFactory
import javax.management.MBeanServerInvocationHandler
import javax.management.ObjectName
import javax.management.remote.JMXConnectorServer
import javax.management.remote.JMXConnectorServerFactory
import javax.management.remote.JMXServiceURL

import mx4j.tools.naming.NamingServiceMBean
import mx4j.tools.naming.NamingService

import brooklyn.entity.Entity
import brooklyn.location.Location
import brooklyn.test.JmxService

/**
 * A class that simulates Tomcat for the purposes of testing.
 */
public class TomcatSimulator {
    private static final int MAXIMUM_LOCKS = 1

    private static Semaphore lock = new Semaphore(MAXIMUM_LOCKS)
    private static Collection<TomcatSimulator> activeInstances = []
    private Location location
    private Entity entity
    private JmxService jmxService

    TomcatSimulator(Location location, Entity entity) {
        assertNotNull(location)
        assertNotNull(entity)
        this.location = location
        this.entity = entity
    }

    public void start() {
        if (lock.tryAcquire() == false)
            throw new IllegalStateException("TomcatSimulator is already running")
        synchronized (activeInstances) { activeInstances.add(this) }

        jmxService = new JmxService();

        int httpPort = 8080
        jmxService.registerMBean "Catalina:type=Connector,port="+httpPort, stateName: "STARTED"
        jmxService.registerMBean "Catalina:type=GlobalRequestProcessor,name=http-"+httpPort,
            errorCount: 0,
            requestCount: 0,
            processingTime: 0

        entity.updateAttribute(TomcatNode.HTTP_PORT, httpPort)
        entity.updateAttribute(TomcatNode.JMX_HOST, jmxService.jmxHost)
        entity.updateAttribute(TomcatNode.JMX_PORT, jmxService.jmxPort)
    }

    public void shutdown() {
        if (jmxService) {
            jmxService.shutdown();
        }
        jmxService = null;
        synchronized (activeInstances) { activeInstances.remove(this) }
        lock.release()
    }

    Location getLocation() { return location }

    static boolean reset() {
        boolean wasFree = true;
        Collection<TomcatSimulator> copyActiveInstances;
        synchronized (activeInstances) { copyActiveInstances = new ArrayList(activeInstances) }
        copyActiveInstances.each { wasFree = false; it.shutdown(); }
        return wasFree
    }
}
