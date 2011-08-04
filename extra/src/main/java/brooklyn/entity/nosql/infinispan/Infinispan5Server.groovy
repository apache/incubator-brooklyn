package brooklyn.entity.nosql.infinispan

import java.util.Collection

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.trait.Startable
import brooklyn.event.adapter.ValueProvider
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

/**
 * An {@link brooklyn.entity.Entity} that represents a single Tomcat instance.
 */
public class Infinispan5Server extends AbstractService implements Startable {
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

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return Infinispan5Setup.newInstance(this, machine)
    }

    public void initSensors() {
        super.initSensors()
        attributePoller.addSensor(SERVICE_UP, { return setup.isRunning() } as ValueProvider)
    }
}
