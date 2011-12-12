package brooklyn.entity.basic.lifecycle;

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.basic.UsesJmx
import brooklyn.entity.basic.legacy.JavaApp
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.StringEscapeUtils

public abstract class JavaStartStopSshDriver extends StartStopSshDriver {

	public JavaStartStopSshDriver(EntityLocal entity, SshMachineLocation machine) {
		super(entity, machine);
        
        entity.setAttribute(Attributes.LOG_FILE_LOCATION, logFileLocation)
	}

    protected abstract String getLogFileLocation();
    
    
	public boolean isJmxEnabled() { entity in UsesJmx }
	
	/** 
	 * Sets all JVM options (-X.. -D..) in an environment var JAVA_OPTS.
	 * <p>
	 * That variable is constructed from getJavaOpts(), then wrapped _unescaped_ in double quotes.
	 * An error is thrown if there is an unescaped double quote in the string.
	 * All other unescaped characters are permitted, but unless $var expansion or `command` execution is desired
	 * (although this is not confirmed as supported) the generally caller should escape any such characters,
	 * for example using {@link StringEscapeUtils#escapeLiteralForDoubleQuotedBash(String)}. 
	 */
	@Override
	public Map<String, String> getShellEnvironment() {
		def sJavaOpts = getJavaOpts().collect({
			if (!StringEscapeUtils.isValidForDoubleQuotingInBash(it))
				throw new IllegalArgumentException("will not accept ${it} as valid BASH string (has unescaped double quote)")
			it
		}).join(" ");
//		println "using java opts: $sJavaOpts"
		super.getShellEnvironment() + [ "JAVA_OPTS" : sJavaOpts ]
	}

	/** arguments to pass to the JVM; this is the config options
	 * (e.g. -Xmx1024; only the contents of {@link #getCustomJavaConfigOptions()} by default) 
	 * and java system properties (-Dk=v; add custom properties in {@link #getCustomJavaSystemProperties()})
	 * <p>
	 * See {@link #getShellEnvironment()} for discussion of quoting/escaping strategy.
	 **/
	public List<String> getJavaOpts() {
		getCustomJavaConfigOptions() + (getJavaSystemProperties().collect { k,v -> "-D"+k+(v!=null? "="+v : "") })
	}

	/**
	 * Returns the complete set of Java system properties (-D defines) to set for the application.
	 * <p>
	 * This is exposed to the JVM as the contents of the {@code JAVA_OPTS} environment variable. 
	 * Default set contains config key, custom system properties, and JMX defines.
	 * <p>
	 * Null value means to set -Dkey otherwise it is -Dkey=value.
	 * <p>
	 * See {@link #getShellEnvironment()} for discussion of quoting/escaping strategy.
	 */
	protected Map getJavaSystemProperties() {
		entity.getConfig(JavaApp.JAVA_OPTIONS) + getCustomJavaSystemProperties() + (jmxEnabled ? getJmxJavaSystemProperties() : [:])
	}

	/**
	 * Return extra Java system properties (-D defines) used by the application.
	 * 
	 * Override as needed; default is an empty map.
	 */
	protected Map getCustomJavaSystemProperties() { return [:] }

	/**
	 * Return extra Java config options, ie arguments starting with - which are
	 * passed to the JVM prior to the class name.
	 * <p>
	 * Note defines are handled separately, in {@link #getCustomJavaSystemProperties()}.
	 * <p>
	 * Override as needed; default is an empty list.
	 */
	protected List<String> getCustomJavaConfigOptions() { return [] }

	public Integer getJmxPort() { !jmxEnabled ? -1 : entity.getAttribute(UsesJmx.JMX_PORT) }
	public Integer getRmiPort() { !jmxEnabled ? -1 : entity.getAttribute(UsesJmx.RMI_PORT) }
	public String getJmxContext() { !jmxEnabled ? null : entity.getAttribute(UsesJmx.JMX_CONTEXT) }

	/**
	 * Return the configuration properties required to enable JMX for a Java application.
	 *
	 * These should be set as properties in the {@code JAVA_OPTS} environment variable
	 * when calling the run script for the application.
	 *
	 * TODO security!
	 */
	protected Map getJmxJavaSystemProperties() {
		[
		 "com.sun.management.jmxremote" : null,
		 "com.sun.management.jmxremote.port" : jmxPort,
		 "com.sun.management.jmxremote.ssl" : false,
		 "com.sun.management.jmxremote.authenticate" : false,
		 "java.rmi.server.hostname" : machine.address.hostName,
		 ]
	}
}
