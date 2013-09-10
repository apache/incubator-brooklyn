package brooklyn.test

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import javax.management.MBeanNotificationInfo
import javax.management.MBeanServer
import javax.management.MBeanServerFactory
import javax.management.MBeanServerInvocationHandler
import javax.management.Notification
import javax.management.NotificationBroadcasterSupport
import javax.management.NotificationEmitter
import javax.management.ObjectName
import javax.management.StandardEmitterMBean
import javax.management.remote.JMXConnectorServer
import javax.management.remote.JMXConnectorServerFactory
import javax.management.remote.JMXServiceURL

import mx4j.tools.naming.NamingService
import mx4j.tools.naming.NamingServiceMBean

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.base.Preconditions;

import brooklyn.event.feed.jmx.JmxHelper

/**
 * Set up a JMX service ready for clients to connect. This consists of an MBean server, a connector server and a naming
 * service.
 */
class JmxService {
    private static final Logger logger = LoggerFactory.getLogger(JmxService.class)

    private MBeanServer server
    private NamingServiceMBean namingServiceMBean
    private JMXConnectorServer connectorServer
    private String jmxHost;
    private int jmxPort;
    private String url;

    public JmxService() {
        this("localhost", 28000 + Math.floor(new Random().nextDouble() * 1000));
        logger.warn("use of deprecated default host and port in JmxService");
    }
    /** @deprecated since 0.6.0; either needs abandoning, or updating to support JmxSupport (and JmxmpAgent, etc) */
    public JmxService(Entity e) {
        this(e.getAttribute(Attributes.HOSTNAME)?:"localhost", e.getAttribute(Attributes.JMX_PORT)?:null);
    }
    
    public JmxService(String jmxHost, Integer jmxPort) {
        this.jmxHost = jmxHost;
        Preconditions.checkNotNull(jmxPort, "JMX_PORT must be set when starting JmxService"); 
        this.jmxPort = jmxPort;
        url = JmxHelper.toConnectorUrl(jmxHost, jmxPort, jmxPort, "jmxrmi")

        JMXServiceURL address = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + jmxHost + ":" + jmxPort + "/jmxrmi");
        connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(address, null, null)
        server = MBeanServerFactory.createMBeanServer();
        ObjectName cntorServerName = ObjectName.getInstance("connectors:protocol=rmi");
        server.registerMBean(connectorServer, cntorServerName);

        ObjectName naming = new ObjectName("Naming:type=registry");
        server.registerMBean(new NamingService(jmxPort), naming);
        Object proxy = MBeanServerInvocationHandler.newProxyInstance(server, naming, NamingServiceMBean.class, false);
        namingServiceMBean = (NamingServiceMBean) proxy
        try {
            namingServiceMBean.start();
        } catch (Exception e) {
            // may take a bit of time for port to be available, if it had just been used
            logger.warn("JmxService couldn't start test mbean ("+e+"); will delay then retry once");
            Thread.sleep(1000);
            namingServiceMBean.start();
        }

        connectorServer.start()
        logger.info "JMX tester service started at URL {}", address
    }

    public int getJmxPort() {
        return jmxPort;
    }
    
    public void shutdown() {
        connectorServer.stop();
        namingServiceMBean.stop()
        logger.info "JMX tester service stopped ({}:{})", jmxHost, jmxPort
    }

    public String getUrl() {
        return url
    }
    
    public GeneralisedDynamicMBean registerMBean(String name) {
        return registerMBean([:], [:], name)
    }

    /**
     * Construct a {@link GeneralisedDynamicMBean} and register it with this MBean server.
     *
     * @param initialAttributes a {@link Map} of attributes that make up the MBean's initial set of attributes and their * values
     * @param name the name of the MBean
     * @return the newly created and registered MBean
     */
    public GeneralisedDynamicMBean registerMBean(Map initialAttributes, String name) {
        return registerMBean(initialAttributes, [:], name)
    }
    
    public GeneralisedDynamicMBean registerMBean(Map initialAttributes, Map operations, String name) {
        GeneralisedDynamicMBean mbean = new GeneralisedDynamicMBean(initialAttributes, operations)
        server.registerMBean(mbean, new ObjectName(name))
        return mbean
    }
    
    public StandardEmitterMBean registerMBean(List notifications, String name) {
        String[] types = notifications.toArray(new String[0])
        MBeanNotificationInfo info = new MBeanNotificationInfo(types, Notification.class.getName(), "Notification");
        NotificationEmitter emitter = new NotificationBroadcasterSupport(info);
        StandardEmitterMBean mbean = new StandardEmitterMBean(NotificationEmitter.class, emitter);
        server.registerMBean(mbean, new ObjectName(name));
        return mbean
    }
}
