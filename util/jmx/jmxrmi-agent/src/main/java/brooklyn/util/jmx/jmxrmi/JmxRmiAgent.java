/*
 * Copyright 2013 by Cloudsoft Corp.
 * Copyright 2007 by Sun Microsystems, Inc.
 */
package brooklyn.util.jmx.jmxrmi;

import java.io.IOException;
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
 * Listens on 9001 unless overridden by system property {@code brooklyn.jmx-rmi-agent.port}.
 * <p>
 * {@code -Dbrooklyn.jmx-rmi-agent.port=9001 -javaagent:brooklyn-jmxrmi-agent-0.5.0-SNAPSHOT.jar}
    
 * @see brooklyn.util.jmx.jmxmp.JmxmpAgent
 * @see https://blogs.oracle.com/jmxetc/entry/connecting_through_firewall_using_jmx
 * @see https://blogs.oracle.com/jmxetc/entry/more_on_premain_and_jmx
 */
public class JmxRmiAgent {

    /** Port for RMI registry to listen on. Default to {@link #RMI_REGISTRY_DEFAULT_PORT}. */
    public static final String RMI_REGISTRY_PORT_PROPERTY = "brooklyn.jmx-agent.rmi-port";
    public static final String RMI_REGISTRY_DEFAULT_PORT = "9001";

    /** Port for JMX server to listen on. Default to {@link #JMX_SERVER_DEFAULT_PORT}. */
    public static final String JMX_SERVER_PORT_PROPERTY = "brooklyn.jmx-agent.jmx-port";
    public static final String JMX_SERVER_DEFAULT_PORT = "11099";

    /** Hostname to advertise, and if {@value #JMX_SERVER_ADDRESS_WILDCARD_PROPERTY} is false also the hostname/interface to bind to. */
    public static final String RMI_HOSTNAME_PROPERTY = "java.rmi.server.hostname";

    /** Whether JMX should bind to all interfaces. */
    public static final String JMX_SERVER_ADDRESS_WILDCARD_PROPERTY = "jmx.remote.server.address.wildcard";

    /**
     * The entry point, uses the JDK dynamic agent loading feature.
     */
    public static void premain(String agentArgs) throws IOException {
        new JmxRmiAgent().startServer(System.getProperties());
    }

    public JMXConnectorServer startServer(Properties properties) {
        try {
            // Ensure cryptographically strong random number generator used
            // to choose the object number - see java.rmi.server.ObjID
            System.setProperty("java.rmi.server.randomIDs", "true");

            // Start an RMI registry on port specified
            final int rmiPort = Integer.parseInt(System.getProperty(RMI_REGISTRY_PORT_PROPERTY, RMI_REGISTRY_DEFAULT_PORT));
            final int jmxPort = Integer.parseInt(System.getProperty(JMX_SERVER_PORT_PROPERTY, JMX_SERVER_DEFAULT_PORT));
            LocateRegistry.createRegistry(jmxPort);

            // Retrieve the PlatformMBeanServer.
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

            // Environment map.
            Map<String, Object> env = new LinkedHashMap<String, Object>();
            propagate(properties, env, JMX_SERVER_ADDRESS_WILDCARD_PROPERTY, "true");

            // TODO Security

            // Create an RMI connector server.
            final String hostname = getLocalhostHostname(properties);
            JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://" + hostname + ":" + jmxPort + "/jndi/rmi://" + hostname + ":" + jmxPort + "/jmxrmi");

            // Now create the server from the JMXServiceURL
            JMXConnectorServer connector = JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbs);

            // Start the RMI connector server.
            connector.start();
            System.out.println("JMXConnectorServer active at: " + url);

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

    /**
     * Returns boolean interpretation of a string, defaulting to {@code valueIfUnknownText} if the value is unset or unrecognised.
     * 
     * @throws IllegalStateException if default is null and value is unset or unrecognised
     */
    private boolean asBoolean(Properties properties, String key, Boolean valueIfNull, Boolean valueIfUnknownText) {
        Object v = properties.get(key);
        if (v == null) {
            if (valueIfNull == null) throw new IllegalStateException("Property '" + key + "' is required.");
            return valueIfNull;
        }
        String vv = v.toString();
        if ("true".equalsIgnoreCase(vv)) return true;
        if ("false".equalsIgnoreCase(vv)) return false;
        if (valueIfUnknownText == null)
            throw new IllegalStateException("Property '" + key + "' has illegal value '" + vv + "'; should be true or false");
        return valueIfUnknownText;
    }

    private String getLocalhostHostname(Properties properties) throws UnknownHostException {
        String hostname = properties == null ? null : properties.getProperty(RMI_HOSTNAME_PROPERTY);
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
