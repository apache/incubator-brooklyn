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
import mx4j.tools.naming.NamingServiceMBean
import javax.management.MBeanServerInvocationHandler
import mx4j.tools.naming.NamingService
import javax.management.DynamicMBean
import javax.management.Attribute
import javax.management.AttributeList
import javax.management.MBeanInfo
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import javax.management.MBeanAttributeInfo
import java.util.Map.Entry

/**
 * A class that simulates Tomcat for the purposes of testing.
 */
class TomcatSimulator {

    private static Semaphore lock = new Semaphore(1)
    private Location location
    private Entity entity
    private MBeanServer server
    private NamingServiceMBean namingServiceMBean
    private JMXConnectorServer connectorServer

    TomcatSimulator(Location location, Entity entity) {
        assertNotNull(location)
        assertNotNull(entity)
        this.location = location
        this.entity = entity
    }

    public void start() {
        if (lock.tryAcquire() == false)
            throw new IllegalStateException("TomcatSimulator is already running")

        String host = "localhost";
        int port = 1099;
        JMXServiceURL address = new JMXServiceURL("service:jmx:rmi://localhost/jndi/rmi://localhost:"+port+"/jmxrmi");
        connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(address, null, null)
        server = MBeanServerFactory.createMBeanServer();
        ObjectName cntorServerName = ObjectName.getInstance("connectors:protocol=rmi");
        server.registerMBean(connectorServer, cntorServerName);

        ObjectName naming = new ObjectName("Naming:type=registry");
        server.registerMBean(new NamingService(), naming);
        Object proxy = MBeanServerInvocationHandler.newProxyInstance(server, naming, NamingServiceMBean.class, false);
        namingServiceMBean = (NamingServiceMBean) proxy
        namingServiceMBean.start();

        connectorServer.start();

        entity.jmxHost = host
        entity.jmxPort = port

        int httpPort = 8080
        entity.updateAttribute(TomcatNode.HTTP_PORT, httpPort)
        registerMBean "Catalina:type=Connector,port="+httpPort, stateName: "STARTED"
    }

    private GeneralisedDynamicMBean registerMBean(Map initialAttributes, String name) {
        GeneralisedDynamicMBean mbean = new GeneralisedDynamicMBean(initialAttributes)
        server.registerMBean(mbean, new ObjectName (name))
        return mbean
    }

    public void shutdown() {
        connectorServer.stop();
        namingServiceMBean.stop();
        lock.release()
    }

    Location getLocation() { return location }
}
