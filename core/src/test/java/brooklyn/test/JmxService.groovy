package brooklyn.test

import javax.management.MBeanServer
import javax.management.MBeanServerFactory
import javax.management.MBeanServerInvocationHandler
import javax.management.ObjectName
import javax.management.remote.JMXConnectorServer
import javax.management.remote.JMXConnectorServerFactory
import javax.management.remote.JMXServiceURL
import mx4j.tools.naming.NamingService
import mx4j.tools.naming.NamingServiceMBean

/**
 * Set up a JMX service ready for clients to connect. This consists of an MBean server, a connector server and a naming
 * service.
 */
class JmxService {

    private MBeanServer server
    private mx4j.tools.naming.NamingServiceMBean namingServiceMBean
    private JMXConnectorServer connectorServer
    private String jmxHost;
    private int jmxPort;

    public JmxService() {
        jmxHost = "localhost";
        jmxPort = 1099;
        JMXServiceURL address = new JMXServiceURL("service:jmx:rmi://localhost/jndi/rmi://localhost:" + jmxPort + "/jmxrmi");
        connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(address, null, null)
        server = MBeanServerFactory.createMBeanServer();
        ObjectName cntorServerName = ObjectName.getInstance("connectors:protocol=rmi");
        server.registerMBean(connectorServer, cntorServerName);

        ObjectName naming = new ObjectName("Naming:type=registry");
        server.registerMBean(new mx4j.tools.naming.NamingService(), naming);
        Object proxy = MBeanServerInvocationHandler.newProxyInstance(server, naming, mx4j.tools.naming.NamingServiceMBean.class, false);
        namingServiceMBean = (mx4j.tools.naming.NamingServiceMBean) proxy
        namingServiceMBean.start();

        connectorServer.start()
    }

    public void shutdown() {
        connectorServer.stop();
        namingServiceMBean.stop()
    }

    /**
     * Construct a @{link GeneralisedDynamicMBean} and register it with this MBean server.
     * @param initialAttributes a @{link Map} of attributes that make up the MBean's initial set of attributes and their
     * values
     * @param name the name of the MBean
     * @return the newly created and registered MBean
     */
    public GeneralisedDynamicMBean registerMBean(Map initialAttributes, String name) {
        GeneralisedDynamicMBean mbean = new GeneralisedDynamicMBean(initialAttributes)
        server.registerMBean(mbean, new ObjectName (name))
        return mbean
    }

}
