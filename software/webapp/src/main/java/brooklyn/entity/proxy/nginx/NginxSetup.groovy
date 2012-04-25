package brooklyn.entity.proxy.nginx

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.basic.lifecycle.legacy.SshBasedAppSetup;
import brooklyn.location.basic.SshMachineLocation

/**
 * Start a {@link NginxController} in a {@link Location} accessible over ssh.
 */
public class NginxSetup extends SshBasedAppSetup {
    public static final String DEFAULT_VERSION = "1.0.8"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"nginx"

    int httpPort

    public static NginxSetup newInstance(NginxController entity, SshMachineLocation machine) {
        NginxSetup result = new NginxSetup(entity, machine)
        
        String suggestedVersion = entity.getConfig(NginxController.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(NginxController.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(NginxController.SUGGESTED_RUN_DIR)

        String version = suggestedVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: "$DEFAULT_INSTALL_DIR/${version}/nginx-${version}"
        String logFileLocation = "${result.runDir}/logs/error.log"

        // We must have the specified HTTP port as this is part of the public URL
        int httpPort = entity.getAttribute(NginxController.PROXY_HTTP_PORT)
        if (!machine.obtainSpecificPort(httpPort)) {
            throw new IllegalStateException("Could not allocate port ${httpPort} for Nginx")
        }

        result.setHttpPort(httpPort)
        result.setVersion(version)
        result.setInstallDir(installDir)
		entity.setAttribute(Attributes.LOG_FILE_LOCATION, logFileLocation)

        return result
    }

    public NginxSetup(NginxController entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    @Override
    protected void setEntityAttributes() {
		super.setEntityAttributes()
        entity.setAttribute(Attributes.HTTP_PORT, httpPort)
    }

    @Override
    public List<String> getInstallScript() {
        makeInstallScript([
		"export INSTALL_DIR=${installDir}",
                CommonCommands.INSTALL_WGET,
                CommonCommands.INSTALL_TAR,
                CommonCommands.installPackage("nginx-prerequisites", 
                        yum: "openssl-devel", 
                        rpm: "openssl-devel",  //TODO RH needs testing
                        apt: "libssl-dev zlib1g-dev libpcre3-dev"),
                "wget http://nginx.org/download/nginx-${version}.tar.gz",
                "tar xvzf nginx-${version}.tar.gz",
	            "cd \$INSTALL_DIR/src",
                "wget http://nginx-sticky-module.googlecode.com/files/nginx-sticky-module-1.0-rc2.tar.gz",
                "tar xvzf nginx-sticky-module-1.0-rc2.tar.gz",
                "cd ..",
	            "mkdir -p dist",
	            "./configure --prefix=\$INSTALL_DIR/dist --add-module=\$INSTALL_DIR/src/nginx-sticky-module-1.0-rc2 --without-http_rewrite_module",
	            "make install"
            ])
    }

    /**
     * Starts nginx from the {@link #runDir} directory.
     */
    public List<String> getRunScript() {
        List<String> script = [
            "cd ${runDir}",
            "nohup ./sbin/nginx -p ${runDir}/ -c conf/server.conf &",
        ]
        return script
    }
 
    /**
     * Restarts nginx with the current configuration.
     */
    @Override
    public List<String> getRestartScript() {
        List<String> script = [
            "cd ${runDir}",
            "test -f logs/nginx.pid || exit 1",
            "./sbin/nginx -p ${runDir}/ -c conf/server.conf -s reload",
        ]
        return script
    }

    /** @see SshBasedAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() {
        return makeCheckRunningScript("nginx", "logs/nginx.pid")
    }

    /**
     * Restarts nginx with the current configuration.
     */
    @Override
    public List<String> getShutdownScript() {
        List<String> script = [
            "cd ${runDir}",
            "test -f logs/nginx.pid || exit 1",
            "./sbin/nginx -p ${runDir}/ -c conf/server.conf -s quit",
        ]
        return script
    }

    @Override
    public List<String> getConfigScript() {
        List<String> script = [
            "mkdir -p ${runDir}",
            "cp -R ${installDir}/dist/{conf,html,logs,sbin} ${runDir}"
        ]
        return script
    }
    
    @Override
    public void config() {
        super.config();
        ((NginxController)entity).doExtraConfigurationDuringStart()
    }

    @Override
    protected void postShutdown() {
        machine.releasePort(httpPort);
    }
}
