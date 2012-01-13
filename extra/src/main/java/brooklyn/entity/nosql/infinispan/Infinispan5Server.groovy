package brooklyn.entity.nosql.infinispan

import java.util.Collection

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.UsesJmx;
import brooklyn.entity.basic.lifecycle.legacy.SshBasedAppSetup
import brooklyn.event.adapter.legacy.ValueProvider
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.basic.SshMachineLocation

/**
 * An {@link brooklyn.entity.Entity} that represents an Infinispan service
 */
public class Infinispan5Server extends SoftwareProcessEntity implements UsesJmx {
    private static final Logger log = LoggerFactory.getLogger(Infinispan5Server.class)
    
    public static final ConfiguredAttributeSensor<String> PROTOCOL = [String, "infinispan.server.protocol", 
            "Infinispan protocol (e.g. memcached, hotrod, or websocket)", "memcached"]
    
    public static final ConfiguredAttributeSensor<Integer> PORT = [Integer, "infinispan.server.port", 
            "TCP port number to listen on" ]

    public Infinispan5Server(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        // TODO What if port to use is the default?
        Collection<Integer> result = super.getRequiredOpenPorts()
        if (getConfig(PORT.getConfigKey())) result.add(getConfig(PORT.getConfigKey()))
        return result
    }

    public SshBasedAppSetup newDriver(SshMachineLocation machine) {
        return Infinispan5Setup.newInstance(this, machine)
    }

    public void connectSensors() {
		super.connectSensors()
		
        sensorRegistry.addSensor(SERVICE_UP, { return setup.isRunning() } as ValueProvider)
    }
}
