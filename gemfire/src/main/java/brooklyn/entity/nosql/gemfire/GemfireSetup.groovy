package brooklyn.entity.nosql.gemfire

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.lifecycle.legacy.SshBasedAppSetup
import brooklyn.location.basic.SshMachineLocation

/**
 * Install and run a {@link GemfireServer} in a {@link brooklyn.location.Location} accessible via ssh.
 */
public class GemfireSetup extends SshBasedAppSetup {

    public static final String DEFAULT_VERSION = "6.5"
    private static final String PID_FILE = "server-pid.txt"

    private int webPort
    private File apiJar
    private File configFile
    private File jarFileToDeploy
    private File licenseFile

    private String apiJarServerSidePath
    private String configFileServersidePath
    private String jarFileServersidePath
    
    public static GemfireSetup newInstance(GemfireServer entity, SshMachineLocation machine) {
        String suggestedVersion = entity.getConfig(GemfireServer.SUGGESTED_VERSION)
        String suggestedLicenseFile = entity.getConfig(GemfireServer.LICENSE)
        String suggestedApiJar = entity.getConfig(GemfireServer.SUGGESTED_API_JAR)
        String suggestedRunDir = entity.getConfig(GemfireServer.SUGGESTED_RUN_DIR)
        String suggestedConfigFile = entity.getConfig(GemfireServer.CONFIG_FILE)
        String suggestedJarFile = entity.getConfig(GemfireServer.JAR_FILE)
        Integer suggestedWebPort = entity.getConfig(GemfireServer.WEB_CONTROLLER_PORT)
        
        // TODO Would like to auto-install!
        if (!suggestedApiJar) {
            throw new IllegalArgumentException("API jar must be specified; cannot auto-install gemfire server")
        }
        
        String version = suggestedVersion ?: DEFAULT_VERSION
        String runDir = suggestedRunDir ?: "$BROOKLYN_HOME_DIR/${entity.application.id}/gemfire-${entity.id}"
        File apiJar = checkFileExists(suggestedApiJar, "API jar")
        File configFile = checkFileExists(suggestedConfigFile, "config")
        File jarFileToDeploy = (suggestedJarFile) ? checkFileExists(suggestedJarFile, "jar") : null
        File licenseFile = checkFileExists(suggestedLicenseFile, "license")
        String logFileLocation = "$runDir/nohup.out"
        int webPort = suggestedWebPort ?: 8089
        
        GemfireSetup result = new GemfireSetup(entity, machine)
        result.setApiJar(apiJar)
        result.setRunDir(runDir)
        result.setConfigFile(configFile)
        result.setJarFileToDeploy(jarFileToDeploy)
        result.setLicenseFile(licenseFile)
        result.setWebPort(webPort)
        entity.setAttribute(Attributes.LOG_FILE_LOCATION, logFileLocation)
        
        return result
    }

    public GemfireSetup(GemfireServer entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public void setConfigFile(File val) {
        configFile = val
    }

    public void setApiJar(File val) {
        apiJar = val
    }

    public void setJarFileToDeploy(File val) {
        jarFileToDeploy = val
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
            configFileServersidePath = "${runDir}/${configFile.name}"
            machine.copyTo(configFile, configFileServersidePath)
        }
        if (jarFileToDeploy) {
            jarFileServersidePath = "$runDir/${jarFileToDeploy.name}"
            machine.copyTo(jarFileToDeploy, jarFileServersidePath)
        }
        if (apiJar) {
            apiJarServerSidePath = "$runDir/gemfireApi.jar"
            machine.copyTo(apiJar, apiJarServerSidePath)
        }
    }

    @Override
    public List<String> getConfigScript() {
        return [ "mkdir -p ${runDir}" ]
    }

    /**
     * Starts a Gemfire process.
     */
    public List<String> getRunScript() {
        String jarDeploy = jarFileServersidePath ?: ""
        return [
            "cd $runDir",
            "nohup java -cp gemfireAPI.jar:$jarDeploy brooklyn.gemfire.api.Server $webPort $configFileServersidePath $runDir/gemfire.log $licenseFile &",
            "echo \$! > $PID_FILE",
        ]
    }


    public List<String> getCheckRunningScript() {
        return makeCheckRunningScript("gemfire", PID_FILE)
    }
    
    /**
     * Shutdown Gemfire
     */
    @Override
    public List<String> getShutdownScript() {
        return makeShutdownScript("gemfire", PID_FILE)
    }

    private static File checkFileExists(String path, Object errorMessage) {
        if (!path) {
            throw new IllegalArgumentException(String.valueOf(errorMessage)+" empty path");
        }
        File result = new File(path)
        if (!result.exists()) {
            throw new IllegalArgumentException(String.valueOf(errorMessage)+" file does not exist");
        }
        return result;
    }
}
