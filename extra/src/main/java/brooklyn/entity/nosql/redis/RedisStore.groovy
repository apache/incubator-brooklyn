package brooklyn.entity.nosql.redis

import java.net.InetAddress
import java.util.Collection
import java.util.List
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.basic.Attributes
import brooklyn.entity.group.AbstractController
import brooklyn.entity.nosql.DataStore
import brooklyn.event.Sensor
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.adapter.HttpSensorAdapter
import brooklyn.event.adapter.ValueProvider
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

import com.google.common.base.Charsets
import com.google.common.base.Preconditions
import com.google.common.io.Files

/**
 * An entity that represents a Redis key-value store service.
 */
public class RedisStore extends AbstractService implements DataStore {
    protected static final Logger LOG = LoggerFactory.getLogger(RedisStore.class)

    public static final BasicConfigKey<Integer> SUGGESTED_REDIS_PORT = [ Integer, "redis.port", "Suggested Redis port" ]

    public static final BasicAttributeSensor<Integer> REDIS_PORT = [ Integer, "redis.port", "Redis port number" ]

    int port
    File configFile

    transient HttpSensorAdapter httpAdapter
    transient AttributePoller attributePoller
    
    public RedisStore(Map properties=[:], Entity owner=null, AbstractGroup cluster=null) {
        super(properties, owner)
    }

    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        if (getConfig(SUGGESTED_REDIS_PORT)) result.add(getConfig(SUGGESTED_REDIS_PORT))
        return result
    }

    @Override
    public void start(List<Location> locations) {
        super.start(locations)

        httpAdapter = new HttpSensorAdapter(this)
        attributePoller = new AttributePoller(this)
        initHttpSensors()
    }

    @Override
    public void stop() {
        attributePoller.close()
        super.stop()
    }

    public void initHttpSensors() {
    }

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return RedisSetup.newInstance(this, machine)
    }

    public synchronized void configure() {
        MachineLocation machine = locations.first()
        SshBasedAppSetup setup = getSshBasedSetup(machine)
        File file = new File("/tmp/${id}")
        file.deleteOnExit()
        Files.write(getConfigFile(setup), file, Charsets.UTF_8)
		setup.machine.copyTo file, "${setup.runDir}/redis.conf"
        if (configFile && configFile.exists()) setup.machine.copyTo configFile, "${setup.runDir}/include.conf"
    }

    public String getConfigFile(SshBasedAppSetup setup) {
        StringBuffer config = []
        config.append """
daemonize yes
pidfile ${setup.runDir}/pid.txt
port ${getAttribute(REDIS_PORT)}
"""
        if (configFile && configFile.exists()) {
            config.append("include ${setup.runDir}/include.conf\n")
        }
        config.toString()
    }
}
