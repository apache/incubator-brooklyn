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
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

import com.google.common.base.Charsets
import com.google.common.base.Preconditions
import com.google.common.io.Files

/**
 * An entity that represents a Redis key-value store service.
 *
 * TODO add sensors with Redis statistics using INFO command
 */
public class RedisStore extends AbstractService implements DataStore {
    protected static final Logger LOG = LoggerFactory.getLogger(RedisStore.class)

    public static final ConfiguredAttributeSensor<Integer> REDIS_PORT = [ Integer, "redis.port", "Redis port number", 6379 ]
    public static final BasicConfigKey<String> REDIS_CONFIG_FILE = [ String, "redis.config.file", "Redis user configuration file" ]
    public static final BasicAttributeSensor<Integer> UPTIME = [ Integer, "redis.uptime", "Redis uptime in seconds" ]

    int port
    File configFile

    transient SshSensorAdapter sshAdapter
    transient AttributePoller attributePoller
    
    public RedisStore(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        setConfigIfValNonNull(REDIS_PORT.configKey, properties.redisPort)
        setConfigIfValNonNull(REDIS_CONFIG_FILE, properties.configFile)
    }

    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        result.add(getAttribute(REDIS_PORT))
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
        attributePoller.addSensor(UPTIME, { computeUptime() } as ValueProvider)
    }
    
    private Integer computeUptime() {
        String output = sshAdapter.newOutputValueProvider("${setup.runDir}/bin/redis-cli info").compute()
        for (String line : output.split("\n")) {
            if (line =~ /^uptime_in_seconds:/) {
                String data = line.trim()
		        int colon = data.indexOf(":")
		        return Integer.parseInt(data.substring(colon + 1))
            }
        }
        return null
    }

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return RedisSetup.newInstance(this, machine)
    }

    @Override
    public synchronized void configure() {
        port = getConfig(REDIS_PORT.configKey)
        setAttribute(REDIS_PORT, port)

        MachineLocation machine = locations.first()
        File file = new File("/tmp/${id}")
        file.deleteOnExit()
        Files.write(getConfigFile(), file, Charsets.UTF_8)
		setup.machine.copyTo file, "${setup.runDir}/redis.conf"

        String configFileName = getConfig(REDIS_CONFIG_FILE)
        if (configFileName) {
            configFile = new File(configFileName)
	        if (configFile.exists()) setup.machine.copyTo configFile, "${setup.runDir}/include.conf"
        }
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

/**
 * A {@link RedisStore} configured as a slave.
 *
 * The {@code master} property must be set to the master Redis store entity.
 */
public class RedisSlave extends RedisStore {
    RedisStore master

    public RedisSlave(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        Preconditions.checkArgument properties.containsKey("master"), "The Redis master entity must be specified"
        master = properties.master
    }

    @Override
    public String getConfigFile() {
        String masterAddress = master.setup.machine.address.hostAddress
        int masterPort = owner.getAttribute(REDIS_PORT)
        String config = super.getConfigFile()
        config += """
slaveof ${masterAddress} ${masterPort}
"""
    }
}

public class RedisShard extends AbstractEntity implements Shard {
    public RedisShard(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }
}
