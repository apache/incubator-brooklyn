/*
 * Copyright 2013 by Cloudsoft Corp.
 * Copyright 2007 by Sun Microsystems, Inc.
 */
package brooklyn.util.jmx.jmxrmi;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.registry.LocateRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

/**
 * This exposes JMX support for going through firewalls by starting an RMI registry server
 * on a well-known port.
 * <p>
 * This implementation DOES NOT support port-forwarding however. The same hostname used internally
 * (specified in {@link #RMI_HOSTNAME_PROPERTY} or autodetected by java) must also be addressable
 * by the JMX client. This is due to how the property is used internally by java during the 
 * RMI registry re-direction.
 * <p>
 * If you require that the client connects to a different hostname/IP than the one where the
 * service is bound, consider using the Brooklyn JmxmpAgent, as this will not work!
 * <p>
 * This listens on {@value #RMI_REGISTRY_PORT_PROPERTY} unless overridden by system property 
 * {@link #RMI_REGISTRY_PORT_PROPERTY} ({@value #RMI_REGISTRY_PORT_PROPERTY}).
 *
 * @see brooklyn.util.jmx.jmxmp.JmxmpAgent
 * @see https://blogs.oracle.com/jmxetc/entry/connecting_through_firewall_using_jmx
 * @see https://blogs.oracle.com/jmxetc/entry/more_on_premain_and_jmx
 */
public class JmxRmiAgent {

    /** Port for RMI registry to listen on. Default to {@link #RMI_REGISTRY_DEFAULT_PORT}. */
    public static final String RMI_REGISTRY_PORT_PROPERTY = "brooklyn.jmx-agent.rmi-port";
    public static final String RMI_REGISTRY_DEFAULT_PORT = "9001";

    /** Port for JMX server (sometimes called JMX_RMI server) to listen on. Default to {@link #JMX_SERVER_DEFAULT_PORT}. */
    public static final String JMX_SERVER_PORT_PROPERTY = "brooklyn.jmx-agent.jmx-port";
    public static final String JMX_SERVER_DEFAULT_PORT = "11099";

    /** Hostname to advertise, and if {@value #JMX_SERVER_ADDRESS_WILDCARD_PROPERTY} is false also the hostname/interface to bind to. 
     *  Should never be 0.0.0.0 as it is publicly advertised. */
    public static final String RMI_HOSTNAME_PROPERTY = "java.rmi.server.hostname";

    /** Whether JMX should bind to all interfaces. */
    public static final String JMX_SERVER_ADDRESS_WILDCARD_PROPERTY = "jmx.remote.server.address.wildcard";

    /**
     * The entry point, uses the JDK dynamic agent loading feature.
     */
    public static void premain(String agentArgs) {
        doMain(agentArgs);
    }
    
    public static void agentmain(String agentArgs) {
        doMain(agentArgs);
    }
    
    public static void doMain(final String agentArgs) {
        // taken from JmxmpAgent in sister project
        
        // do the work in a daemon thread so that if the main class terminates abnormally,
        // such that shutdown hooks aren't called, we don't keep the application running
        // (e.g. if the app is compiled with java7 then run with java6, with a java6 agent here;
        // that causes the agent to launch, the main to fail, but the process to keep going)
        Thread t = new Thread() {
            public void run() {
                doMainForeground(agentArgs);
            }
        };
        t.setDaemon(true);
        t.start();
    }

    public static void doMainForeground(String agentArgs) {
        final JMXConnectorServer connector = new JmxRmiAgent().startServer(System.getProperties());
        if (connector != null) {
            Runtime.getRuntime().addShutdownHook(new Thread("jmxrmi-agent-shutdownHookThread") {
                @Override public void run() {
                    try {
                        connector.stop();
                    } catch (Exception e) {
                        System.err.println("Error closing jmxrmi connector in shutdown hook (continuing): "+e);
                    }
                }});
        }
    }

    public JMXConnectorServer startServer(Properties properties) {
        try {
            // Ensure cryptographically strong random number generator used
            // to choose the object number - see java.rmi.server.ObjID
            System.setProperty("java.rmi.server.randomIDs", "true");

            // Start an RMI registry on port specified
            final int rmiPort = Integer.parseInt(System.getProperty(RMI_REGISTRY_PORT_PROPERTY, RMI_REGISTRY_DEFAULT_PORT));
            final int jmxPort = Integer.parseInt(System.getProperty(JMX_SERVER_PORT_PROPERTY, JMX_SERVER_DEFAULT_PORT));
            final String hostname = getLocalhostHostname(properties);
            
            System.out.println("Setting up JmxRmiAgent for: "+hostname+" "+rmiPort+" / "+jmxPort);
            
            LocateRegistry.createRegistry(rmiPort);

            // Retrieve the PlatformMBeanServer.
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

            // Environment map.
            Map<String, Object> env = new LinkedHashMap<String, Object>();
            propagate(properties, env, JMX_SERVER_ADDRESS_WILDCARD_PROPERTY, "true");

            // TODO Security

            // Create an RMI connector server.
            JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://" + hostname + ":" + jmxPort + "/jndi/rmi://" + hostname + ":" + rmiPort + "/jmxrmi");

            // Now create the server from the JMXServiceURL
            JMXConnectorServer connector = JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbs);

            // Start the RMI connector server.
            connector.start();
            System.out.println("JmxRmiAgent JMXConnectorServer active at: " + url);

            return connector;
        } catch (RuntimeException e) {
            System.err.println("Unable to start JMXConnectorServer: " + e);
            throw e;
        } catch (Exception e) {
            System.err.println("Unable to start JMXConnectorServer: " + e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Copies the value of key from the source to the target, if set. Otherwise
     * sets the {@code defaultValueIfNotNull} if that is not null.
     * 
     * @return whether anything is set
     */
    private static boolean propagate(Properties source, Map<String, Object> target, String key, Object defaultValueIfNotNull) {
        Object v = source.getProperty(key);
        if (v == null) v = defaultValueIfNotNull;
        if (v == null) return false;
        target.put(key, v);
        return true;
    }

    private String getLocalhostHostname(Properties properties) throws UnknownHostException {
        String hostname = properties == null ? null : properties.getProperty(RMI_HOSTNAME_PROPERTY);
        if ("0.0.0.0".equals(hostname)) {
            System.err.println("WARN: invalid hostname 0.0.0.0 specified for JmxRmiAgent; " +
            		"it typically must be an address or hostname which is bindable on the machine where " +
            		"this service is running AND accessible by a client machine (access will likely be impossible)");
        }
        if (hostname == null || hostname.isEmpty()) {
            hostname = InetAddress.getLocalHost().getHostName();
        }
        return hostname;
    }

    /**
     * Convenience main method.
     */
    public static void main(String[] args) throws Exception {
        premain("");
    }
}
