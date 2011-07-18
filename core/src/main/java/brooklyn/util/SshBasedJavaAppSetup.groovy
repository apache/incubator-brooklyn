package brooklyn.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.location.PortRange
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.BasicPortRange
import brooklyn.util.internal.SshJschTool;

/**
 * Java application installation, configuration and startup using ssh.
 *
 * This class should be extended for use by entities that are implemented by a Java
 * application.
 *
 * TODO complete documentation
 */
public abstract class SshBasedJavaAppSetup extends SshBasedAppSetup {
    static final Logger log = LoggerFactory.getLogger(SshBasedJavaAppSetup.class)

    public static final int DEFAULT_FIRST_JMX_PORT = 32199

    protected int jmxPort
    protected String jmxHost

    public SshBasedJavaAppSetup(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine)
        jmxHost = machine.getAddress().getHostName()
    }

    public SshBasedJavaAppSetup setJmxPort(int val) {
        jmxPort = val
        return this
    }

    public SshBasedJavaAppSetup setJmxHost(String val) {
        jmxHost = val
        return this
    }

    /**
     * Convenience method to generate Java environment options string.
     *
     * Converts the properties {@link Map} entries with a value to {@code -Dkey=value}
     * and entries where the value is null to {@code -Dkey}.
     */
    public static String toJavaDefinesString(Map properties) {
        StringBuffer options = []
        properties.each { key, value ->
	            options.append("-D").append(key)
	            if (value != null && value != "") options.append("=\'").append(value).append("\'")
	            options.append(" ")
	        }
        return options.toString().trim()
    }

    /**
     * Returns the complete set of Java configuration options required by
     * the application.
     *
     * These should be formatted and passed to the JVM as the contents of
     * the {@code JAVA_OPTS} environment variable. The default set contains
     * only the options required to enable JMX. To add application specific
     * options, override the {@link #getJavaConfigOptions()} method.
     *
     * @see #toJavaDefinesString(Map)
     */
    protected Map getJvmStartupProperties() {
        getJavaConfigOptions() + getJmxConfigOptions()
    }

    /**
     * Return extra Java configuration options required by the application.
     * 
     * This should be overridden; default is an empty {@link Map}.
     */
    protected Map getJavaConfigOptions() { return [:] }

    /**
     * Return the configuration properties required to enable JMX for a Java application.
     *
     * These should be set as properties in the {@code JAVA_OPTS} environment variable
     * when calling the run script for the application.
     *
     * TODO security!
     */
    protected Map getJmxConfigOptions() {
        [
          "com.sun.management.jmxremote" : "",
          "com.sun.management.jmxremote.port" : jmxPort,
          "com.sun.management.jmxremote.ssl" : false,
          "com.sun.management.jmxremote.authenticate" : false,
          "java.rmi.server.hostname" : jmxHost,
        ]
    }
}
