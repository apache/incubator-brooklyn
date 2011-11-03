package brooklyn.entity.nosql.gemfire

import java.util.List
import java.util.Map

import brooklyn.entity.basic.lifecycle.legacy.SshBasedAppSetup;
import brooklyn.location.basic.SshMachineLocation

/**
 * Install and run a {@link GemfireServer} in a {@link brooklyn.location.Location} accessible via ssh.
 */
public class GemfireSetup extends SshBasedAppSetup {
    public static final String DEFAULT_VERSION = "6.5"
    
    private int webPort
    private File configFile
    private File jarFile
    private File licenseFile
    private String configFileServersidePath
    private String jarFileServersidePath
    
    public static GemfireSetup newInstance(GemfireServer entity, SshMachineLocation machine) {
        String suggestedVersion = entity.getConfig(GemfireServer.SUGGESTED_VERSION)
        String suggestedLicenseFile = entity.getConfig(GemfireServer.LICENSE)
        String suggestedInstallDir = entity.getConfig(GemfireServer.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(GemfireServer.SUGGESTED_RUN_DIR)
        String suggestedConfigFile = entity.getConfig(GemfireServer.CONFIG_FILE)
        String suggestedJarFile = entity.getConfig(GemfireServer.JAR_FILE)
        Integer suggestedWebPort = entity.getConfig(GemfireServer.WEB_CONTROLLER_PORT)
        
        // TODO Would like to auto-intall!
        if (!suggestedInstallDir) throw new IllegalArgumentException("Installation directory must be specified; cannot auto-install gemfire server")
        //String installDir = suggestedInstallDir ?: "$DEFAULT_INSTALL_DIR/${version}/gemfire-${version}"
        String installDir = suggestedInstallDir
        
        String version = suggestedVersion ?: DEFAULT_VERSION
        String runDir = suggestedRunDir ?: "$BROOKLYN_HOME_DIR/${entity.application.id}/gemfire-${entity.id}"
        File configFile = checkFileExists(suggestedConfigFile, "config")
        File jarFile = (suggestedJarFile) ? checkFileExists(suggestedJarFile, "jar") : null
        File licenseFile = checkFileExists(suggestedLicenseFile, "license")
        String logFileLocation = "$runDir/nohup.out"
        int webPort = suggestedWebPort ?: 8089
        
        GemfireSetup result = new GemfireSetup(entity, machine)
        result.setInstallDir(installDir)
        result.setRunDir(runDir)
        result.setConfigFile(configFile)
        result.setJarFile(jarFile)
        result.setLicenseFile(licenseFile)
        result.setWebPort(webPort)
        result.setLogFileLocation(logFileLocation)
        return result
    }

    public GemfireSetup(GemfireServer entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public void setConfigFile(File val) {
        configFile = val
    }

    public void setJarFile(File val) {
        jarFile = val
    }

    public void setLicenseFile(File val) {
        licenseFile = val
    }

    public void setWebPort(int val) {
        webPort = val
    }
    
    @Override
    public void install() {
        // no-op; already installed
    }
    
    @Override
    public void config() {
        super.config();
        
        if (configFile) {
            configFileServersidePath = runDir+"/"+configFile.getName()
            machine.copyTo(configFile, configFileServersidePath)
        }
        if (jarFile) {
            jarFileServersidePath = runDir+"/"+jarFile.getName()
            machine.copyTo(jarFile, jarFileServersidePath)
        }
    }

    @Override
    public List<String> getConfigScript() {
        return [ "mkdir -p ${runDir}" ]
    }

    /**
     * Starts gemfire process.
     */
    public List<String> getRunScript() {
        String startArgs = "$webPort $configFileServersidePath $jarFileServersidePath $licenseFile"
        return [
            "cd ${runDir}",
            "nohup ${installDir}/start.sh ${startArgs} &",
            "echo \$! > startup-pid.txt",
        ]
    }


    public List<String> getCheckRunningScript() {
        return makeCheckRunningScript("gemfire", "server-pid.txt")
    }
    
    /**
     * Shutdown gemfire
     */
    @Override
    public List<String> getShutdownScript() {
        return makeShutdownScript("gemfire", "server-pid.txt")
    }

    private static File checkFileExists(String path, Object errorMessage) {
        if (!path) {
            throw new IllegalArgumentException(String.valueOf(errorMessage)+" empty path");
        }
        File result = new File(path)
        if (!(result.exists())) {
            throw new IllegalArgumentException(String.valueOf(errorMessage)+" file does not exist");
        }
        return result;
    }
}
