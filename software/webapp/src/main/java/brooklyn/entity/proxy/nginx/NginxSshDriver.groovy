package brooklyn.entity.proxy.nginx


import brooklyn.location.basic.SshMachineLocation;

import java.util.Map;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.lifecycle.CommonCommands
import brooklyn.entity.basic.lifecycle.ScriptHelper
import brooklyn.entity.basic.lifecycle.StartStopSshDriver
import brooklyn.entity.trait.Startable
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.NetworkUtils

/**
 * Start a {@link NginxController} in a {@link Location} accessible over ssh.
 */
public class NginxSshDriver extends StartStopSshDriver {
    public static final Logger log = LoggerFactory.getLogger(NginxSshDriver.class);
    
    public NginxSshDriver(NginxController entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    protected String getLogFileLocation() { "${runDir}/logs/error.log" }
    protected Integer getHttpPort() { entity.getAttribute(NginxController.PROXY_HTTP_PORT) }

    @Override
    public void postLaunch(){
        entity.setAttribute(Attributes.HTTP_PORT, httpPort)
        super.postLaunch()
    }
    
    @Override
    public void install() {
        String nginxUrl = "http://nginx.org/download/nginx-${version}.tar.gz"
        String nginxSaveAs  = "nginx-${version}.tar.gz"
        String stickyModuleUrl = "http://nginx-sticky-module.googlecode.com/files/nginx-sticky-module-1.0.tar.gz"
        String stickyModuleSaveAs = "nginx-sticky-module-1.0.tar.gz"
        boolean sticky = ((NginxController)entity).isSticky();
        
        ScriptHelper script = newScript(INSTALLING).
            body.append(
                CommonCommands.INSTALL_WGET,
                CommonCommands.INSTALL_TAR,
                CommonCommands.installPackage("nginx-prerequisites",
                        yum: "openssl-devel",
                        rpm: "openssl-devel",  //TODO RH needs testing
                        apt: "libssl-dev zlib1g-dev libpcre3-dev"),
                CommonCommands.downloadUrlAs(nginxUrl, getEntityVersionLabel('/'), nginxSaveAs),
                "tar xvzf ${nginxSaveAs}",
                "cd ${installDir}/nginx-${version}");
        if (sticky)
            script.body.append(
                "cd src",
                CommonCommands.downloadUrlAs(stickyModuleUrl, getEntityVersionLabel('/'), stickyModuleSaveAs),
                "tar xvzf ${stickyModuleSaveAs}",
                "cd ..");
        script.body.append(
                "mkdir -p dist",
                "./configure --prefix=${installDir}/nginx-${version}/dist "+
                    (sticky ? "--add-module=${installDir}/nginx-${version}/src/nginx-sticky-module-1.0 " : "")+
                    "--without-http_rewrite_module",
                "make install");
        
        int result = script.execute();
        if (result!=0)
            throw new IllegalStateException("execution failed, invalid result ${result} for ${script.summary}; NB gcc 4.2 is required");
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).
            body.append(
                "mkdir -p ${runDir}",
                "cp -R ${installDir}/nginx-${version}/dist/{conf,html,logs,sbin} ${runDir}"
            ).execute();
        
        ((NginxController)entity).doExtraConfigurationDuringStart()
    }
    
    @Override
    public void launch() {
        // By default, nginx writes the pid of the master process to "logs/nginx.pid"
        NetworkUtils.checkPortsValid(httpPort:httpPort);
        newScript(LAUNCHING, usePidFile:false).
            body.append(
                "cd ${runDir}",
                "nohup ./sbin/nginx -p ${runDir}/ -c conf/server.conf > ./console 2>&1 &"
            ).execute();
    }
    
    @Override
    public boolean isRunning() {
        return newScript(CHECK_RUNNING, usePidFile:"logs/nginx.pid").execute() == 0
    }
    
    @Override
    public void stop() {
        // Don't `kill -9`, as that doesn't stop the worker processes
        String pidFile = "logs/nginx.pid"
        newScript(STOPPING, usePidFile:false).
            body.append(
                "cd ${runDir}",
                "export PID=`cat ${pidFile}`",
                '[[ -n "$PID" ]] || exit 0',
                'kill $PID'
            ).execute();
    }

    @Override
    public void restart() {
        //if it hasn't come up we can't do the restart optimization
        if (entity.getAttribute(Startable.SERVICE_UP)) {
        newScript(RESTARTING, usePidFile:"logs/nginx.pid").
            body.append(
                "cd ${runDir}",
                "./sbin/nginx -p ${runDir}/ -c conf/server.conf -s reload",
            ).execute();
        } else {
            try {
                if (isRunning())
                    stop();
            } catch (Exception e) {
                log.debug("$entity stop failed during restart (but wasn't in stop state, so not surprising): "+e);
            }
            launch();
        }
    }
    
    @Override
    public Map<String, String> getShellEnvironment() {
        return super.getShellEnvironment()
    }
    
    @Override
    protected Map getCustomJavaSystemProperties() {
        // FIXME Anything needed?
        return super.getCustomJavaSystemProperties()
    }

}
