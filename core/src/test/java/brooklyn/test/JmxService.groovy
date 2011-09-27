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

import org.slf4j.LoggerFactory
import org.slf4j.Logger

/**
 * Set up a JMX service ready for clients to connect. This consists of an MBean server, a connector server and a naming
 * service.
 */
class JmxService {
    private static final Logger logger = LoggerFactory.getLogger(JmxService.class)

    private MBeanServer server
    private mx4j.tools.naming.NamingServiceMBean namingServiceMBean
    private JMXConnectorServer connectorServer
    private String jmxHost;
    private int jmxPort;

    public JmxService() {
        jmxHost = "localhost";
        jmxPort = 28000 + Math.floor(new Random().nextDouble() * 1000);

        JMXServiceURL address = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + jmxPort + "/jmxrmi");
        connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(address, null, null)
        server = MBeanServerFactory.createMBeanServer();
        ObjectName cntorServerName = ObjectName.getInstance("connectors:protocol=rmi");
        server.registerMBean(connectorServer, cntorServerName);

        ObjectName naming = new ObjectName("Naming:type=registry");
        server.registerMBean(new NamingService(jmxPort), naming);
        Object proxy = MBeanServerInvocationHandler.newProxyInstance(server, naming, NamingServiceMBean.class, false);
        namingServiceMBean = (NamingServiceMBean) proxy
        namingServiceMBean.start();

        connectorServer.start()
        logger.info "JMX tester service started at URL {}", address
    }

    public void shutdown() {
        connectorServer.stop();
        namingServiceMBean.stop()
    }

    /**
     * Construct a {@link GeneralisedDynamicMBean} and register it with this MBean server.
     *
     * @param initialAttributes a {@link Map} of attributes that make up the MBean's initial set of attributes and their * values
     * @param name the name of the MBean
     * @return the newly created and registered MBean
     */
    public GeneralisedDynamicMBean registerMBean(Map initialAttributes, String name) {
        GeneralisedDynamicMBean mbean = new GeneralisedDynamicMBean(initialAttributes)
        server.registerMBean(mbean, new ObjectName (name))
        return mbean
    }
}
