package brooklyn.entity.basic.legacy

import java.io.File;
import java.util.Collection
import java.util.Map
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.UsesJmx
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.SensorRegistry;
import brooklyn.event.adapter.legacy.OldJmxSensorAdapter
import brooklyn.event.basic.MapConfigKey
import brooklyn.util.internal.Repeater

/**
 * An {@link brooklyn.entity.Entity} representing a single web application instance.
 */
public abstract class JavaApp extends SoftwareProcessEntity implements UsesJmx {
    public static final Logger log = LoggerFactory.getLogger(JavaApp.class)
	
	// TODO too complicated? Used to inject monterey credentials into a web-app, where the code in the 
    // war expects a particular environment variable name
    public static final MapConfigKey<Map> PROPERTY_FILES = [ Map, "java.properties.environment", "Property files to be generated, referenced by an environment variable" ]
    
    // TODO too complicated? Used by KarafContainer
    public static final MapConfigKey<Map> NAMED_PROPERTY_FILES = [ Map, "java.properties.named", "Property files to be generated, referenced by name relative to runDir" ]


    transient OldJmxSensorAdapter jmxAdapter
    
    public JavaApp(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

	public SensorRegistry getAttributePoller() { sensorRegistry }
	
    @Override
    protected void connectSensors() {
		super.connectSensors()
        initJmxSensors()
    }

	protected final void setJmxConfig() {
		throw new UnsupportedOperationException("not used anymore!")
	}
	
	@Deprecated /* use new JmxAdapter */
	protected void initJmxSensors() {
		if (!(getAttribute(HOSTNAME) && getAttribute(JMX_PORT))) {
			throw new IllegalStateException("JMX is not available")
		}

		if (log.isDebugEnabled()) log.debug "Connecting {} to JMX on {}:{}", this, getAttribute(HOSTNAME), getAttribute(JMX_PORT)
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
        result.add(getConfig(JMX_PORT))
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

	public File copy(String file) {
		return copy(new File(file))
	}

	public File copy(File file) {
		return driver.copy(file)
	}

	public void deploy(String file) {
		deploy(new File(file))
	}

	public void deploy(File file, File target=null) {
		driver.deploy(file, target)
	}

}
