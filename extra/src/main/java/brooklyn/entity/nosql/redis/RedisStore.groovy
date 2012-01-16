package brooklyn.entity.nosql.redis

import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.lifecycle.legacy.SshBasedAppSetup
import brooklyn.entity.nosql.DataStore
import brooklyn.entity.nosql.Shard
import brooklyn.event.adapter.SensorRegistry
import brooklyn.event.adapter.legacy.OldSshSensorAdapter
import brooklyn.event.adapter.legacy.ValueProvider
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.location.basic.SshMachineLocation

import com.google.common.base.Charsets
import com.google.common.base.Preconditions
import com.google.common.io.Files

/**
 * An entity that represents a Redis key-value store service.
 *
 * TODO add sensors with Redis statistics using INFO command
 */
public class RedisStore extends SoftwareProcessEntity implements DataStore {
    protected static final Logger LOG = LoggerFactory.getLogger(RedisStore.class)

    public static final PortAttributeSensorAndConfigKey REDIS_PORT = [ "redis.port", "Redis port number", 6379 ]
    public static final BasicConfigKey<String> REDIS_CONFIG_FILE = [ String, "redis.config.file", "Redis user configuration file" ]
    public static final BasicAttributeSensor<Integer> UPTIME = [ Integer, "redis.uptime", "Redis uptime in seconds" ]

    transient OldSshSensorAdapter sshAdapter
    
    public RedisStore(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        setConfigIfValNonNull(REDIS_PORT, properties.redisPort)
        setConfigIfValNonNull(REDIS_CONFIG_FILE, properties.configFile)
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts()
        result.add(getAttribute(REDIS_PORT))
        return result
    }
    
    @Override
    public void postStart() {
        initSshSensors()
    }

    protected void initSshSensors() {
        sshAdapter = new OldSshSensorAdapter(this, driver.machine)
 
        addSshSensors()
    }

    protected void addSshSensors() {
        sensorRegistry.addSensor(SERVICE_UP, sshAdapter.newMatchValueProvider("${driver.runDir}/bin/redis-cli ping", /PONG/))
        sensorRegistry.addSensor(UPTIME, { computeUptime() } as ValueProvider)
    }
    
    private Integer computeUptime() {
        String output = sshAdapter.newOutputValueProvider("${driver.runDir}/bin/redis-cli info").compute()
        for (String line : output.split("\n")) {
            if (line =~ /^uptime_in_seconds:/) {
                String data = line.trim()
		        int colon = data.indexOf(":")
		        return Integer.parseInt(data.substring(colon + 1))
            }
        }
        return null
    }

    public SshBasedAppSetup newDriver(SshMachineLocation machine) {
        return RedisSetup.newInstance(this, machine)
    }

    @Override
    public void doExtraConfigurationDuringStart() {
	    int port = getAttribute(REDIS_PORT)
        boolean include = false

        String includeName = getConfig(REDIS_CONFIG_FILE)
        if (includeName) {
            File includeFile = new File(includeName)
	        include = includeFile.exists()
        }

        File config = new File("/tmp/${id}")
        Files.write(getConfigData(port, include), config, Charsets.UTF_8)
		driver.machine.copyTo config, "${driver.runDir}/redis.conf"
        config.delete()
        if (include) driver.machine.copyTo configFile, "${driver.runDir}/include.conf"
        
        super.configure()
    }

    public String getConfigData(int port, boolean include) {
        String data = """
daemonize yes
pidfile ${driver.runDir}/pid.txt
port ${port}
"""
        if (include) data += """
include ${driver.runDir}/include.conf
"""
        data
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
    public String getConfigData(int port, boolean include) {
        String masterAddress = master.driver.machine.address.hostAddress
        int masterPort = owner.getAttribute(REDIS_PORT)

        super.getConfigData(port, include) + """
slaveof ${masterAddress} ${masterPort}
"""
    }
}

public class RedisShard extends AbstractEntity implements Shard {
    public RedisShard(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }
}
