package brooklyn.entity.webapp.tomcat

import static org.junit.Assert.*

import brooklyn.entity.Entity
import brooklyn.location.Location
import java.util.concurrent.Semaphore
import javax.management.ObjectName
import javax.management.MBeanServerFactory
import javax.management.MBeanServer
import javax.management.remote.JMXConnectorServerFactory
import javax.management.remote.JMXConnectorServer
import javax.management.remote.JMXServiceURL

/**
 * A class that simulates Tomcat for the purposes of testing.
 */
class TomcatSimulator {

    public static interface PortInfoMBean { public String getStateName() }
    public static class PortInfo implements PortInfoMBean { String stateName }

    private static Semaphore lock = new Semaphore(1)
    private Location _location
    private Entity _entity

    TomcatSimulator(Location location, Entity entity) {
        assertNotNull(location)
        assertNotNull(entity)
        _location = location
        _entity = entity
    }

    public void start() {
        if (lock.tryAcquire() == false)
            throw new IllegalStateException("TomcatSimulator is already running")
    }

    public void shutdown() {
        lock.release()
    }

    Location getLocation() { return _location }
}
