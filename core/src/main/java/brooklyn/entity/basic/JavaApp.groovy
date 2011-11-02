package brooklyn.entity.basic

import java.util.Collection
import java.util.Map
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.legacy.OldJmxSensorAdapter;
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.event.basic.MapConfigKey
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.internal.Repeater

/**
 * An {@link brooklyn.entity.Entity} representing a single web application instance.
 */
public abstract class JavaApp extends SoftwareProcessEntity {
    public static final Logger log = LoggerFactory.getLogger(JavaApp.class)

    public static final int DEFAULT_JMX_PORT = 1099

    @SetFromFlag("jmxPort")
    public static final ConfiguredAttributeSensor<Integer> JMX_PORT = Attributes.JMX_PORT
    @SetFromFlag("rmiPort")
    public static final ConfiguredAttributeSensor<Integer> RMI_PORT = Attributes.RMI_PORT
    @SetFromFlag("jmxContext")
    public static final ConfiguredAttributeSensor<String> JMX_CONTEXT = Attributes.JMX_CONTEXT
    
    public static final BasicConfigKey<Map<String, String>> JAVA_OPTIONS = [ Map, "java.options", "Java options", [:] ]
    public static final MapConfigKey<Map> PROPERTY_FILES = [ Map, "java.properties.environment", "Property files to be generated, referenced by an environment variable" ]
    public static final MapConfigKey<Map> NAMED_PROPERTY_FILES = [ Map, "java.properties.named", "Property files to be generated, referenced by name relative to runDir" ]

    public static final BasicAttributeSensor<String> JMX_URL = [ String, "jmx.url", "JMX URL" ]

    transient OldJmxSensorAdapter jmxAdapter
    
    public JavaApp(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    protected void connectSensors() {
		super.connectSensors()
        initJmxSensors()
    }

	@Deprecated /* use new JmxAdapter */
	protected void initJmxSensors() {
		if (!(getAttribute(HOSTNAME) && getAttribute(JMX_PORT))) {
			throw new IllegalStateException("JMX is not available")
		}

		log.debug "Connecting {} to JMX on {}:{}", this, getAttribute(HOSTNAME), getAttribute(JMX_PORT)
		jmxAdapter = new OldJmxSensorAdapter(this, 60*1000)
		jmxAdapter.connect();
		waitForJmx()
		setAttribute(JMX_URL, jmxAdapter.url)
		addJmxSensors()
    }

	@Deprecated /* use new JmxAdapter */
    protected void addJmxSensors() { }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        result.add(getConfig(JMX_PORT.configKey))
        return result
    }

    public void waitForJmx() {
        new Repeater("Wait for JMX")
                .repeat()
                .every(1, TimeUnit.SECONDS)
                .until { jmxAdapter.isConnected() }
                .limitIterationsTo(30)
                .run();
    }

    @Override
    public void preStop() {
    	super.preStop()
        if (jmxAdapter) jmxAdapter.disconnect();
    }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['jmxPort']
    }
}
