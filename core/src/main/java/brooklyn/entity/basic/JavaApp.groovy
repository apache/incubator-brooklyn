package brooklyn.entity.basic

import java.util.Collection
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.location.Location
import brooklyn.util.internal.Repeater

/**
* An {@link brooklyn.entity.Entity} representing a single web application instance.
*/
public abstract class JavaApp extends AbstractService {
    public static final Logger log = LoggerFactory.getLogger(JavaApp.class)

    public static final int RMI_PORT = 1099
    public static final ConfigKey<Integer> SUGGESTED_JMX_PORT = ConfigKeys.SUGGESTED_JMX_PORT;
    public static final ConfigKey<String> SUGGESTED_JMX_HOST = ConfigKeys.SUGGESTED_JMX_HOST;

    public static final AttributeSensor<Integer> JMX_PORT = Attributes.JMX_PORT;
    public static final AttributeSensor<String> JMX_HOST = Attributes.JMX_HOST;

    transient JmxSensorAdapter jmxAdapter
    transient AttributePoller attributePoller

    public JavaApp(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        if (properties.jmxPort) setConfig(SUGGESTED_JMX_PORT, properties.remove("jmxPort"))
        if (properties.jmxHost) setConfig(SUGGESTED_JMX_HOST, properties.remove("jmxHost"))
    }

    protected abstract void initJmxSensors();

    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        if (getConfig(SUGGESTED_JMX_PORT)) {
            result.add(RMI_PORT)
            result.add(getConfig(SUGGESTED_JMX_PORT))
        }
        return result
    }

    public void waitForJmx() {
        new Repeater("Wait for JMX").repeat().every(1, TimeUnit.SECONDS).until({jmxAdapter.isConnected()}).limitIterationsTo(30).run();
    }

    @Override
    public void start(Collection<Location> locations) {
        super.start locations

        if (!(getAttribute(JMX_HOST) && getAttribute(JMX_PORT)))
            throw new IllegalStateException("JMX is not available")

        attributePoller = new AttributePoller(this)
        jmxAdapter = new JmxSensorAdapter(this, 60*1000)
        jmxAdapter.connect();
        waitForJmx()

        initJmxSensors()
    }

    @Override
    public void stop() {
        if (attributePoller) attributePoller.close()
        if (jmxAdapter) jmxAdapter.disconnect();

        super.stop()
    }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['jmxPort']
    }
}
