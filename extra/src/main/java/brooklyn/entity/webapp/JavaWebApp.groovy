package brooklyn.entity.webapp

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AttributeDictionary
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SshBasedJavaWebAppSetup
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.EntityStartUtils

public abstract class JavaWebApp extends AbstractEntity implements Startable {
    
    public static final Logger log = LoggerFactory.getLogger(JavaWebApp.class)

    public static final AttributeSensor<Integer> HTTP_PORT = AttributeDictionary.HTTP_PORT;
    public static final AttributeSensor<Integer> JMX_PORT = AttributeDictionary.JMX_PORT;
    public static final AttributeSensor<String> JMX_HOST = AttributeDictionary.JMX_HOST;

    transient JmxSensorAdapter jmxAdapter;

    JavaWebApp(Map properties=[:]) {
        super(properties)
    }

    public abstract SshBasedJavaWebAppSetup getSshBasedSetup(SshMachineLocation loc);
    protected abstract void initJmxSensors();
    
    public void start(Collection<Location> locations) {
        EntityStartUtils.startEntity this, locations;
        
        if (!(getAttribute(JMX_HOST) && getAttribute(JMX_PORT)))
            throw new IllegalStateException("JMX is not available")
        
        log.debug "started $this: jmxHost {} and jmxPort {}", getAttribute(JMX_HOST), getAttribute(JMX_PORT)
        
        jmxAdapter = new JmxSensorAdapter(this, 60*1000)
        initJmxSensors()
    }
    
    public void startInLocation(SshMachineLocation loc) {
        def setup = getSshBasedSetup(loc)
        setup.start loc
        locations.add(loc)
        
        log.debug "waiting to ensure $this doesn't abort prematurely"
        long startTime = System.currentTimeMillis()
        boolean isRunningResult = false;
        while (!isRunningResult && System.currentTimeMillis() < startTime+60000) {
            Thread.sleep 3000
            isRunningResult = setup.isRunning(loc)
            log.debug "checked $this, running result $isRunningResult"
        }
        if (!isRunningResult) throw new IllegalStateException("$this aborted soon after startup")
        
    }

    public void shutdown() {
        jmxAdapter.disconnect();
        shutdownInLocation(locations.iterator().next())
    }
    
    public void shutdownInLocation(SshMachineLocation loc) {
        getSshBasedSetup(loc).shutdown loc
    }

}
