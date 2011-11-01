package brooklyn.entity.database.derby

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.JavaApp;
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedJavaAppSetup;
import brooklyn.util.SshBasedJavaWebAppSetup

/**
 * Start a {@link DerbyDatabase} in a {@link Location} accessible over ssh.
 *
 * TODO work in progress
 */
public class DerbySetup extends SshBasedJavaAppSetup {
    public static final String DEFAULT_VERSION = "10.8.1.2"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"derby"

    private int rmiPort

    public static DerbySetup newInstance(DerbyDatabase entity, SshMachineLocation machine) {
        String suggestedVersion = entity.getConfig(DerbyDatabase.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(DerbyDatabase.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(DerbyDatabase.SUGGESTED_RUN_DIR)
        Integer suggestedJmxPort = entity.getConfig(DerbyDatabase.JMX_PORT.configKey)

        String version = suggestedVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: (DEFAULT_INSTALL_DIR+"/"+"${version}"+"/"+"derby-broker-${version}")
        String runDir = suggestedRunDir ?: (BROOKLYN_HOME_DIR+"/"+"${entity.application.id}"+"/"+"derby-${entity.id}")
        int jmxPort = machine.obtainPort(toDesiredPortRange(suggestedJmxPort))
        int rmiPort = machine.obtainPort(toDesiredPortRange(jmxPort - 100))

        DerbySetup result = new DerbySetup(entity, machine)
        result.setRmiPort(rmiPort)
        result.setJmxPort(jmxPort)
        result.setVersion(version)
        result.setInstallDir(installDir)
        result.setRunDir(runDir)

        return result
    }

    public DerbySetup(DerbyDatabase entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public void setRmiPort(int val) {
        rmiPort = val
    }

    /** JMX is configured using command line switch. */
    @Override
    protected Map getJmxJavaSystemProperties() { [:] }

    @Override
    public List<String> getInstallScript() {
        makeInstallScript([
                "wget http://www.mirrorservice.org/sites/ftp.apache.org//db/derby/db-derby-${version}/db-derby-${version}-lib.tar.gz",
                "tar xvzf db-derby-${version}-lib.tar.gz",
            ])
    }

    /**
     * Creates the directories Derby needs to run in a different location from where it is installed.
     */
    public List<String> getRunScript() {
        List<String> script = [
            "cd ${runDir}",
			"nohup ./bin/derby &",
        ]
        return script
    }

    public Map<String, String> getShellEnvironment() {
        Map<String, String> env = [
			"DERBY_HOME" : "${runDir}",
			"DERBY_WORK" : "${runDir}",
			"DERBY_OPTS" : toJavaDefinesString(getJvmStartupProperties()),
        ]
        return env
    }

    /** @see SshBasedJavaAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() {
       return makeCheckRunningScript("derby", "derby.pid")
    }

    @Override
    public List<String> getConfigScript() {
        List<String> script = [
            "mkdir -p ${runDir}",
            "cd ${runDir}",
            "cp -R ${installDir}/{bin,etc,lib} .",
        ]
        return script
    }

    @Override
    public List<String> getRestartScript() {
       return makeRestartScript("derby", "derby.pid")
    }

    @Override
    public List<String> getShutdownScript() {
       return makeShutdownScript("derby", "derby.pid")
    }

    @Override
    protected void postShutdown() {
        machine.releasePort(rmiPort)
        machine.releasePort(jmxPort)
    }
}
