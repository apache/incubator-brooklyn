package brooklyn.entity.nosql.redis

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

/**
 * Start a {@link QpidNode} in a {@link Location} accessible over ssh.
 */
public class RedisSetup extends SshBasedAppSetup {
    public static final String DEFAULT_VERSION = "2.2.12"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"redis"
    public static final int DEFAULT_REDIS_PORT = 6379

    private int redisPort

    public static RedisSetup newInstance(RedisStore entity, SshMachineLocation machine) {
        Integer suggestedVersion = entity.getConfig(RedisStore.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(RedisStore.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(RedisStore.SUGGESTED_RUN_DIR)
        Integer suggestedRedisPort = entity.getConfig(RedisStore.SUGGESTED_REDIS_PORT)

        String version = suggestedVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: (DEFAULT_INSTALL_DIR+"/"+"${version}"+"/"+"redis-${version}")
        String runDir = suggestedRunDir ?: (BROOKLYN_HOME_DIR+"/"+"${entity.application.id}"+"/"+"redis-${entity.id}")
        int redisPort = machine.obtainPort(toDesiredPortRange(suggestedRedisPort, DEFAULT_REDIS_PORT))

        RedisSetup result = new RedisSetup(entity, machine)
        result.setRedisPort(redisPort)
        result.setVersion(version)
        result.setInstallDir(installDir)
        result.setRunDir(runDir)

        return result
    }

    public RedisSetup(RedisStore entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public RedisSetup setRedisPort(int val) {
        redisPort = val
        return this
    }

    @Override
    protected void postStart() {
        entity.setAttribute(RedisStore.REDIS_PORT, redisPort)
        entity.setAttribute(Attributes.VERSION, version)
    }

    @Override
    public List<String> getInstallScript() {
        makeInstallScript([
                "wget http://redis.googlecode.com/files/redis-${version}.tar.gz",
                "tar xvzf redis-${version}.tar.gz",
	            "make"
            ])
    }

    /**
     * Starts redis from the {@link #runDir} directory.
     */
    public List<String> getRunScript() {
        List<String> script = [
            "cd ${runDir}",
            "nohup ./bin/redis-server redis.conf &",
        ]
        return script
    }
 
    /** @see SshBasedAppSetup#getRunEnvironment() */
    public Map<String, String> getRunEnvironment() { [:] }

    /** @see SshBasedAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() {
        List<String> script = [
            "cd ${runDir}",
            "./bin/redis-cli ping > /dev/null"
        ]
        return script
    }

    /**
     * Restarts redis with the current configuration.
     */
    @Override
    public List<String> getShutdownScript() {
        List<String> script = [
            "cd ${runDir}",
            "./bin/redis-cli shutdown"
        ]
        return script
    }

    @Override
    public List<String> getConfigScript() {
        List<String> script = [
            "mkdir -p ${runDir}",
            "cd ${installDir}",
            "make install PREFIX=${runDir}",
        ]
        return script
    }

    @Override
    protected void postShutdown() {
        machine.releasePort(httpPort);
    }
    
    @Override
    public void config() {
        super.config()
        entity.configure()
    }
}
