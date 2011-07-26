package brooklyn.entity.nosql.redis

import java.util.Collection
import java.util.List
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.nosql.DataStore
import brooklyn.entity.nosql.Shard
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.adapter.SshSensorAdapter
import brooklyn.event.adapter.ValueProvider
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

import com.google.common.base.Charsets
import com.google.common.io.Files

/**
 * An entity that represents a Redis key-value store service.
 */
public class RedisStore extends AbstractService implements DataStore {
    protected static final Logger LOG = LoggerFactory.getLogger(RedisStore.class)

    public static final ConfiguredAttributeSensor<Integer> REDIS_PORT = [ Integer, "redis.port", "Redis port number", 6379 ]
    public static final BasicAttributeSensor<Integer> UPTIME = [ Integer, "redis.uptime", "Redis uptime in seconds" ]

    int port
    File configFile

    transient SshSensorAdapter sshAdapter
    transient AttributePoller attributePoller
    
    public RedisStore(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        if (properties.redisPort) setConfig(REDIS_PORT.configKey, properties.remove("redisPort"))
        port = getConfig(REDIS_PORT.configKey)
    }

    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        result.add(getConfig(REDIS_PORT.configKey))
        return result
    }

    @Override
    public void start(List<Location> locations) {
        super.start(locations)

        sshAdapter = new SshSensorAdapter(this, setup.machine)
        attributePoller = new AttributePoller(this)
        initSshSensors()
    }

    @Override
    public void stop() {
        attributePoller.close()
        super.stop()
    }

    public void initSshSensors() {
        attributePoller.addSensor(SERVICE_UP, sshAdapter.newMatchValueProvider("${setup.runDir}/bin/redis-cli ping", /PONG/))
        attributePoller.addSensor(UPTIME, {} as ValueProvider)
    }
    
    private Integer computeUptime() {
        String output = sshAdapter.newOutputValueProvider("${setup.runDir}/bin/redis-cli info | grep uptime_in_seconds")
        int colon = output.lastIndexOf(":")
        return Integer.parseInt(output.substring(colon))
    }

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return RedisSetup.newInstance(this, machine)
    }

    public synchronized void configure() {
        MachineLocation machine = locations.first()
        File file = new File("/tmp/${id}")
        file.deleteOnExit()
        Files.write(getConfigFile(), file, Charsets.UTF_8)
		setup.machine.copyTo file, "${setup.runDir}/redis.conf"
        if (configFile && configFile.exists()) setup.machine.copyTo configFile, "${setup.runDir}/include.conf"
    }

    public String getConfigFile() {
        StringBuffer config = []
        config.append """
daemonize yes
pidfile ${setup.runDir}/pid.txt
port ${port}
"""
        if (configFile && configFile.exists()) {
            config.append("include ${setup.runDir}/include.conf\n")
        }
        config.toString()
    }
}

public class RedisShard extends AbstractEntity implements Shard {
    public RedisShard(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }
}
