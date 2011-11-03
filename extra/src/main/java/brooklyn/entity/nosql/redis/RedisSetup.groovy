package brooklyn.entity.nosql.redis

import java.util.List
import java.util.Map

import com.google.common.base.Preconditions;

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.lifecycle.legacy.SshBasedAppSetup;
import brooklyn.location.basic.SshMachineLocation

/**
 * Start a {@link RedisStore} in a {@link Location} accessible over ssh.
 */
public class RedisSetup extends SshBasedAppSetup {
    public static final String DEFAULT_VERSION = "2.2.12"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"redis"

    int redisPort

    public static RedisSetup newInstance(RedisStore entity, SshMachineLocation machine) {
        String suggestedVersion = entity.getConfig(RedisStore.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(RedisStore.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(RedisStore.SUGGESTED_RUN_DIR)

        String version = suggestedVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: (DEFAULT_INSTALL_DIR+"/"+"${version}"+"/"+"redis-${version}")
        String runDir = suggestedRunDir ?: (BROOKLYN_HOME_DIR+"/"+"${entity.application.id}"+"/"+"redis-${entity.id}")

        int redisPort = entity.getConfig(RedisStore.REDIS_PORT.configKey)
        Preconditions.checkState machine.obtainSpecificPort(redisPort), "The port ${redisPort} must be available"

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

    @Override
    protected void setEntityAttributes() {
		super.setEntityAttributes()
        entity.setAttribute(RedisStore.REDIS_PORT, redisPort)
    }

    @Override
    public List<String> getInstallScript() {
        makeInstallScript([
                "wget http://redis.googlecode.com/files/redis-${version}.tar.gz",
                "tar xvzf redis-${version}.tar.gz",
                "cd redis-${version}",
	            "make"
            ])
    }

    /**
     * Starts redis from the {@link #runDir} directory.
     */
    public List<String> getRunScript() {
        List<String> script = [
            "cd ${runDir}",
            "./bin/redis-server redis.conf",
        ]
        return script
    }
 

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
        machine.releasePort(redisPort);
    }
    
    @Override
    public void config() {
        super.config()
        ((RedisStore)entity).doExtraConfigurationDuringStart()
    }
}
