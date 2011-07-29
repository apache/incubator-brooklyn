package brooklyn.entity.nosql.gemfire

import java.util.List
import java.util.Map

import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

/**
 * Start a {@link QpidNode} in a {@link Location} accessible over ssh.
 */
public class GemfireSetup extends SshBasedAppSetup {
    public static final String DEFAULT_VERSION = "1.0.4"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"nginx"

    private File configFile
    private File jarFile
    private String configFileServersidePath
    private String jarFileServersidePath

    public static GemfireSetup newInstance(GemfireServer entity, SshMachineLocation machine) {
        String installDir = entity.getConfig(GemfireServer.INSTALL_DIR)
        File configFile = entity.getConfig(GemfireServer.CONFIG_FILE)
        File jarFile = entity.getConfig(GemfireServer.JAR_FILE)
        
        GemfireSetup result = new GemfireSetup(entity, machine)
        result.setInstallDir(installDir)
        result.setConfigFile(configFile)
        result.setJarFile(jarFile)
        result.setRunDir(installDir)
        return result
    }

    public GemfireSetup(GemfireServer entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public GemfireSetup setConfigFile(File val) {
        configFile = val
        return this
    }

    public GemfireSetup setJarFile(File val) {
        jarFile = val
        return this
    }

    @Override
    protected void postStart() {
    }

    @Override
    public void install() {
        // no-op; already installed
    }
    
    @Override
    public void config() {
        if (configFile) {
            configFileServersidePath = runDir+"/"+configFile.getName()
            machine.copyTo(configFile, configFileServersidePath)
        }
        if (jarFile) {
            jarFileServersidePath = runDir+"/"+jarFile.getName()
            machine.copyTo(jarFile, jarFileServersidePath)
        }
    }

    /**
     * Starts gemfire process.
     */
    public List<String> getRunScript() {
        String startArgs = configFileServersidePath+" "+
                (jarFileServersidePath ? jarFileServersidePath : "");
        List<String> script = [
            "cd ${runDir}",
            "nohup ${installDir}/start.sh ${startArgs} &",
        ]
        return script
    }
 
    /** @see SshBasedAppSetup#getRunEnvironment() */
    public Map<String, String> getRunEnvironment() { [:] }

    public List<String> getCheckRunningScript() {
        return ["ps -p `cat ${runDir}/gemfire.pid`"]
    }
    
    /**
     * Shutdown gemfire
     */
    @Override
    public List<String> getShutdownScript() {
        // FIXME not cleanly shutting down yet!
        List<String> script = []
        return script
    }

    @Override
    protected void postShutdown() {
    }
}
