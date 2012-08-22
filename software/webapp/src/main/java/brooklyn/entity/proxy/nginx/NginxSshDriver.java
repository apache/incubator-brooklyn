package brooklyn.entity.proxy.nginx;


import static java.lang.String.format;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.basic.lifecycle.StartStopSshDriver;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.NetworkUtils;

/**
 * Start a {@link NginxController} in a {@link brooklyn.location.Location} accessible over ssh.
 */
public class NginxSshDriver extends StartStopSshDriver {
    public static final Logger log = LoggerFactory.getLogger(NginxSshDriver.class);

    boolean customizationCompleted = false;

    public NginxSshDriver(NginxController entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected String getLogFileLocation() {
        return format("%s/logs/error.log", getRunDir());
    }

    protected Integer getHttpPort() {
        return entity.getAttribute(NginxController.PROXY_HTTP_PORT);
    }

    @Override
    public void postLaunch() {
        entity.setAttribute(Attributes.HTTP_PORT, getHttpPort());
        super.postLaunch();
    }

    @Override
    public void install() {
        String nginxUrl = format("http://nginx.org/download/nginx-%s.tar.gz", getVersion());
        String nginxSaveAs = format("nginx-%s.tar.gz", getVersion());
        String stickyModuleUrl = "http://nginx-sticky-module.googlecode.com/files/nginx-sticky-module-1.0.tar.gz";
        String stickyModuleSaveAs = "nginx-sticky-module-1.0.tar.gz";
        boolean sticky = ((NginxController) entity).isSticky();

        ScriptHelper script = newScript(INSTALLING);
        script.body.append(CommonCommands.INSTALL_TAR);
        MutableMap<String, String> installPackageFlags = MutableMap.of(
                "yum", "openssl-devel", 
                "rpm", "openssl-devel", 
                "apt", "libssl-dev zlib1g-dev libpcre3-dev",
                "port", null);
        script.body.append(CommonCommands.installPackage(installPackageFlags, "nginx-prerequisites"));
        script.body.append(CommonCommands.downloadUrlAs(nginxUrl, getEntityVersionLabel("/"), nginxSaveAs));
        script.body.append(format("tar xvzf %s", nginxSaveAs));
        script.body.append(format("cd %s/nginx-%s", getInstallDir(), getVersion()));

        if (sticky) {
            script.body.append("cd src");
            script.body.append(CommonCommands.downloadUrlAs(stickyModuleUrl, getEntityVersionLabel("/"), stickyModuleSaveAs));
            script.body.append(format("tar xvzf %s", stickyModuleSaveAs));
            script.body.append("cd ..");
        }

        script.body.append(
                "mkdir -p dist",
                "./configure"+
                    format(" --prefix=%s/nginx-%s/dist", getInstallDir(), getVersion()) +
                    " --with-http_ssl_module"+
                    (sticky ? format(" --add-module=%s/nginx-%s/src/nginx-sticky-module-1.0 ", getInstallDir(), getVersion()) : ""),
                "make install");

        int result = script.execute();
        if (result != 0)
            throw new IllegalStateException(String.format("execution failed, invalid result %s for %s; NB gcc 4.2 is required", result, script.summary));
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).
                body.append(
                format("mkdir -p %s", getRunDir()),
                format("cp -R %s/nginx-%s/dist/{conf,html,logs,sbin} %s", getInstallDir(), getVersion(), getRunDir())
        ).execute();
        
        customizationCompleted = true;
        ((NginxController) entity).doExtraConfigurationDuringStart();
    }

    public boolean isCustomizationCompleted() {
        return customizationCompleted;
    }
    
    @Override
    public void launch() {
        // By default, nginx writes the pid of the master process to "logs/nginx.pid"
        NetworkUtils.checkPortsValid(MutableMap.of("httpPort", getHttpPort()));
        Map flags = MutableMap.of("usePidFile", false);

        newScript(flags, LAUNCHING).
                body.append(
                format("cd %s", getRunDir()),
                format("nohup ./sbin/nginx -p %s/ -c conf/server.conf > ./console 2>&1 &", getRunDir())
        ).execute();
    }

    @Override
    public boolean isRunning() {
        Map flags = MutableMap.of("usePidFile", "logs/nginx.pid");
        return newScript(flags, CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        // Don't `kill -9`, as that doesn't stop the worker processes
        String pidFile = "logs/nginx.pid";
        Map flags = MutableMap.of("usePidFile", false);

        newScript(flags, STOPPING).
                body.append(
                format("cd %s", getRunDir()),
                format("export PID=`cat %s`", pidFile),
                "[[ -n \"$PID\" ]] || exit 0",
                "kill $PID"
        ).execute();
    }

    @Override
    public void restart() {
        try {
            if (isRunning()) {
                stop();
            }
        } catch (Exception e) {
            log.debug(getEntity() + " stop failed during restart (but wasn't in stop state, so not surprising): " + e);
        }
        launch();
    }
    
    public void reload() {
        if (!entity.getAttribute(Startable.SERVICE_UP)) {
            //if it hasn't come up completely then do restart instead
            log.debug("Reload of nginx "+entity+" is doing restart because has not come up");
            restart();
            return;
        }
        
        Map flags = MutableMap.of("usePidFile", "logs/nginx.pid");
        newScript(flags, RESTARTING).
                body.append(
                format("cd %s", getRunDir()),
                format("./sbin/nginx -p %s/ -c conf/server.conf -s reload", getRunDir())
        ).execute();
    }
}
