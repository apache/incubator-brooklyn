package brooklyn.entity.basic

import java.util.Collection
import java.util.Map;
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.event.basic.MapConfigKey
import brooklyn.util.internal.Repeater

/**
 * An {@link brooklyn.entity.Entity} representing a single web application instance.
 */
public abstract class JavaApp extends AbstractService {
    public static final Logger log = LoggerFactory.getLogger(JavaApp.class)

    public static final int DEFAULT_JMX_PORT = 1099

    public static final ConfiguredAttributeSensor<Integer> JMX_PORT = Attributes.JMX_PORT
    public static final ConfiguredAttributeSensor<Integer> RMI_PORT = Attributes.RMI_PORT
    public static final ConfiguredAttributeSensor<String> JMX_CONTEXT = Attributes.JMX_CONTEXT
    public static final BasicConfigKey<Map<String, String>> JAVA_OPTIONS = [ Map, "java.options", "Java options", [:] ]
    public static final MapConfigKey<Map> PROPERTY_FILES = [ Map, "java.properties.environment", "Property files to be generated, referenced by an environment variable" ]
    public static final MapConfigKey<Map> NAMED_PROPERTY_FILES = [ Map, "java.properties.named", "Property files to be generated, referenced by name relative to runDir" ]

    public static final BasicAttributeSensor<String> JMX_URL = [ String, "jmx.url", "JMX URL" ]

    boolean jmxEnabled = true
    transient JmxSensorAdapter jmxAdapter
    
    public JavaApp(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        setJmxConfig(properties)
    }

    public void setJmxConfig(Map properties=[:]) {
        setConfigIfValNonNull(JMX_PORT.configKey, properties.jmxPort)
        setConfigIfValNonNull(JMX_CONTEXT.configKey, properties.jmxContext)
    }

    @Override
    protected void initSensors() {
        initJmxSensors()
    }

    protected void initJmxSensors() {
        if (jmxEnabled) {
            if (!(getAttribute(HOSTNAME) && getAttribute(JMX_PORT))) {
                throw new IllegalStateException("JMX is not available")
            }

            log.debug "Connecting to JMX on ${getAttribute(HOSTNAME)}:${getAttribute(JMX_PORT)}"
            jmxAdapter = new JmxSensorAdapter(this, 60*1000)
            jmxAdapter.connect();
            waitForJmx()
            setAttribute(JMX_URL, jmxAdapter.url)
            addJmxSensors()
        }
    }

    protected void addJmxSensors() { }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        result.add(DEFAULT_JMX_PORT)
        result.add(getConfig(JMX_PORT.configKey))
        result.add(getConfig(RMI_PORT.configKey))
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
    public void stop() {
        if (jmxAdapter) jmxAdapter.disconnect();

        super.stop()
    }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['jmxPort']
    }
}
