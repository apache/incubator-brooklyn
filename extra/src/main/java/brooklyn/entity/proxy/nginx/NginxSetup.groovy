package brooklyn.entity.proxy.nginx

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

/**
 * Start a {@link QpidNode} in a {@link Location} accessible over ssh.
 */
public class NginxSetup extends SshBasedAppSetup {
    public static final String DEFAULT_VERSION = "1.0.4"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"nginx"
    public static final int DEFAULT_HTTP_PORT = 80

    private int httpPort

    public static NginxSetup newInstance(NginxController entity, SshMachineLocation machine) {
        Integer suggestedVersion = entity.getConfig(NginxController.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(NginxController.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(NginxController.SUGGESTED_RUN_DIR)
        Integer suggestedHttpPort = entity.getConfig(NginxController.SUGGESTED_HTTP_PORT)

        String version = suggestedVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: (DEFAULT_INSTALL_DIR+"/"+"nginx-${version}")
        String runDir = suggestedRunDir ?: (DEFAULT_RUN_DIR+"/"+"app-"+entity.getApplication()?.id+"/nginx-"+entity.id)
        int httpPort = machine.obtainPort(toDesiredPortRange(suggestedHttpPort, DEFAULT_HTTP_PORT))

        NginxSetup result = new NginxSetup(entity, machine)
        result.setHttpPort(httpPort)
        result.setVersion(version)
        result.setInstallDir(installDir)
        result.setRunDir(runDir)

        return result
    }

    public NginxSetup(NginxController entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public NginxSetup setHttpPort(int val) {
        httpPort = val
        return this
    }

    @Override
    protected void postStart() {
        entity.setAttribute(Attributes.HTTP_PORT, httpPort)
        entity.setAttribute(Attributes.VERSION, version)
    }

    @Override
    public List<String> getInstallScript() {
        makeInstallScript([
                "wget http://nginx.org/download/nginx-${version}.tar.gz",
                "tar xvzf nginx-${version}.tar.gz",
            ])
    }

    /**
     * Starts nginx from the {@link #runDir} directory.
     */
    public List<String> getRunScript() {
        List<String> script = [
            "cd ${runDir}",
            "nohup ./sbin/nginx -c ./conf/server.conf &",
        ]
        return script
    }
 
    /** @see SshBasedAppSetup#getRunEnvironment() */
    public Map<String, String> getRunEnvironment() { [:] }

    /**
     * Restarts nginx with the current configuration.
     */
    @Override
    public List<String> getRestartScript() {
        List<String> script = [
            "cd ${runDir}",
            "ps aux | grep '[n]'ginx | grep `cat pid.txt` || exit 1",
            "kill -HUP `cat pid.txt`"
        ]
        return script
    }

    /** @see SshBasedAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() {
        List<String> script = [
            "cd ${runDir}",
            "echo pid is `cat pid.txt`",
            "(ps aux | grep '[n]'ginx | grep `cat pid.txt` > pid.list || echo \"no nginx processes found\")",
            "cat pid.list",
            "if [ -z \"`cat pid.list`\" ] ; then echo process no longer running ; exit 1 ; fi",
        ]
        return script
        //note grep can return exit code 1 if text not found, hence the || in the block above
    }

    /**
     * Restarts nginx with the current configuration.
     */
    @Override
    public List<String> getShutdownScript() {
        List<String> script = [
            "cd ${runDir}",
            "ps aux | grep '[n]'ginx | grep `cat pid.txt` || exit 1",
            "kill `cat pid.txt`",
            "sleep 5",
            "kill -9 `cat pid.txt`"
        ]
        return script
    }

    @Override
    public List<String> getConfigScript() {
        List<String> script = [
            "mkdir -p ${runDir}",
            "cd ${installDir}",
            "./configure --prefix=${runDir}",
            "make install",
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
