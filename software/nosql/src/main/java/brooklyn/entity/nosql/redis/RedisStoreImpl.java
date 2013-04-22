package brooklyn.entity.nosql.redis;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;

/**
 * An entity that represents a Redis key-value store service.
 *
 * TODO add sensors with Redis statistics using INFO command
 */
public class RedisStoreImpl extends SoftwareProcessImpl implements RedisStore {
    protected static final Logger LOG = LoggerFactory.getLogger(RedisStore.class);

    public RedisStoreImpl() {
        this(MutableMap.of(), null);
    }
    public RedisStoreImpl(Map properties) {
        this(properties, null);
    }
    public RedisStoreImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public RedisStoreImpl(Map properties, Entity parent) {
        super(properties, parent);
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();

        connectServiceUpIsRunning();
        
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

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
    }
    
    public Class getDriverInterface() {
        return RedisStoreDriver.class;
    }

    @Override
    public RedisStoreDriver getDriver() {
        return (RedisStoreDriver) super.getDriver();
    }
    
    @Override
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

    @Override
    public String getConfigData(int port, boolean include) {
        String data = 
                "daemonize yes"+"\n"+
                "pidfile "+getDriver().getRunDir()+"/pid.txt"+"\n"+
                "port "+port+"\n";

        if (include) data += "include "+getDriver().getRunDir()+"/include.conf";
        return data;
    }
}
