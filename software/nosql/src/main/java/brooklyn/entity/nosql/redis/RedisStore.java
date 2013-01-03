package brooklyn.entity.nosql.redis;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.nosql.DataStore;
import brooklyn.event.AttributeSensor;
import brooklyn.event.adapter.FunctionSensorAdapter;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

/**
 * An entity that represents a Redis key-value store service.
 *
 * TODO add sensors with Redis statistics using INFO command
 */
public class RedisStore extends SoftwareProcessEntity implements DataStore {
    protected static final Logger LOG = LoggerFactory.getLogger(RedisStore.class);

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "2.6.7");

    public static final PortAttributeSensorAndConfigKey REDIS_PORT = new PortAttributeSensorAndConfigKey("redis.port", "Redis port number", 6379);
    public static final ConfigKey<String> REDIS_CONFIG_FILE = new BasicConfigKey<String>(String.class, "redis.config.file", "Redis user configuration file");
    public static final AttributeSensor<Integer> UPTIME = new BasicAttributeSensor<Integer>(Integer.class, "redis.uptime", "Redis uptime in seconds");

    public RedisStore() {
        this(MutableMap.of(), null);
    }
    public RedisStore(Map properties) {
        this(properties, null);
    }
    public RedisStore(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public RedisStore(Map properties, Entity parent) {
        super(properties, parent);

        setConfigIfValNonNull(REDIS_PORT, properties.get("redisPort"));
        setConfigIfValNonNull(REDIS_CONFIG_FILE, properties.get("configFile"));
    }
    
    @Override
    protected void connectSensors() {
        FunctionSensorAdapter serviceUpAdapter = sensorRegistry.register(new FunctionSensorAdapter(
                MutableMap.of("period", 1*1000),
                new Callable<Boolean>() {
                    public Boolean call() {
                        return getDriver().isRunning();
                    }}));
        serviceUpAdapter.poll(SERVICE_UP);
        
        // TODO IF desired, port this for setting UPTIME (because legacy sshAdapter is deleted)
//        String output = sshAdapter.newOutputValueProvider("${driver.runDir}/bin/redis-cli info").compute()
//        for (String line : output.split("\n")) {
//            if (line =~ /^uptime_in_seconds:/) {
//                String data = line.trim()
//                int colon = data.indexOf(":")
//                return Integer.parseInt(data.substring(colon + 1))
//            }
//        }
    }
    
    public Class getDriverInterface() {
        return RedisStoreDriver.class;
    }

    @Override
    public RedisStoreDriver getDriver() {
        return (RedisStoreDriver) super.getDriver();
    }
    
    public String getAddress() {
        MachineLocation machine = getMachineOrNull();
        return (machine != null) ? machine.getAddress().getHostAddress() : null;
    }
    
    
    // FIXME Don't want to hard-code this as SshMachineLocatoin; want generic way of doing machine.copyTo
    @Override
    protected SshMachineLocation getMachineOrNull() {
        return (SshMachineLocation) super.getMachineOrNull();
    }
    
    // FIXME This logic should all be in the driver
    void doExtraConfigurationDuringStart() {
	    int port = getAttribute(REDIS_PORT);
        boolean include = false;

        String includeName = getConfig(REDIS_CONFIG_FILE);
        if (includeName != null && includeName.length() > 0) {
            File includeFile = new File(includeName);
	        include = includeFile.exists();
        }

		getMachineOrNull().copyTo(new ByteArrayInputStream(getConfigData(port, include).getBytes()), getDriver().getRunDir()+"/redis.conf");
        if (include) getMachineOrNull().copyTo(new File(includeName), getDriver().getRunDir()+"/include.conf");
        
        super.configure();
    }

    public String getConfigData(int port, boolean include) {
        String data = 
                "daemonize yes"+"\n"+
                "pidfile "+getDriver().getRunDir()+"/pid.txt"+"\n"+
                "port "+port+"\n";

        if (include) data += "include "+getDriver().getRunDir()+"/include.conf";
        return data;
    }
}
