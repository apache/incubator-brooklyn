package brooklyn.entity.nosql.infinispan

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.legacy.SshBasedAppSetup
import brooklyn.entity.java.UsesJmx
import brooklyn.event.adapter.legacy.ValueProvider
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.location.MachineLocation

/**
 * An {@link brooklyn.entity.Entity} that represents an Infinispan service
 */
public class Infinispan5Server extends SoftwareProcessEntity implements UsesJmx {
    private static final Logger log = LoggerFactory.getLogger(Infinispan5Server.class)
    
    public static final BasicAttributeSensorAndConfigKey<String> PROTOCOL = [String, "infinispan.server.protocol", 
            "Infinispan protocol (e.g. memcached, hotrod, or websocket)", "memcached"]
    
    public static final PortAttributeSensorAndConfigKey PORT = ["infinispan.server.port", "TCP port number to listen on" ]

    public Infinispan5Server(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        // TODO What if port to use is the default?
        Collection<Integer> result = super.getRequiredOpenPorts()
        if (getConfig(PORT)) result.add(getConfig(PORT))
        return result
    }

    @Override
    public SshBasedAppSetup newDriver(MachineLocation machine) {
        return Infinispan5Setup.newInstance(this, machine)
    }

    @Override
    public Class getDriverInterface() {
        return Infinispan5Setup.class;
    }

    public void connectSensors() {
		super.connectSensors()
		
        sensorRegistry.addSensor(SERVICE_UP, { return getDriver().isRunning() } as ValueProvider)
    }
}
