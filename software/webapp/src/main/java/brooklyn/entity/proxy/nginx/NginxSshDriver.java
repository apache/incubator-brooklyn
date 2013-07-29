package brooklyn.entity.proxy.nginx;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * Start a {@link NginxController} in a {@link brooklyn.location.Location} accessible over ssh.
 */
public class NginxSshDriver extends AbstractSoftwareProcessSshDriver implements NginxDriver {

    // TODO An alternative way of installing nginx is described at:
    //   http://sjp.co.nz/posts/building-nginx-for-debian-systems/
    // It's use of `apt-get source nginx` and `apt-get build-dep nginx` makes
    // it look higher level and therefore more appealing.
    
    public static final Logger log = LoggerFactory.getLogger(NginxSshDriver.class);
    private static final String NGINX_PID_FILE = "logs/nginx.pid";

    protected boolean customizationCompleted = false;
    private String expandedInstallDir;

    public NginxSshDriver(NginxControllerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }
    
    @Override
    public NginxControllerImpl getEntity() {
        return (NginxControllerImpl) super.getEntity();
    }

    protected String getLogFileLocation() {
        return format("%s/logs/error.log", getRunDir());
    }

    protected Integer getHttpPort() {
        return entity.getAttribute(NginxController.PROXY_HTTP_PORT);
    }

    private String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }
    
    @Override
    public void rebind() {
        customizationCompleted = true;
    }

    @Override
    public void postLaunch() {
        entity.setAttribute(Attributes.HTTP_PORT, getHttpPort());
        super.postLaunch();
    }

    @Override
    public void install() {
        newScript("disable requiretty").
            setFlag("allocatePTY", true).
            body.append(CommonCommands.dontRequireTtyForSudo()).
            execute();

        DownloadResolver nginxResolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(this);
        List<String> nginxUrls = nginxResolver.getTargets();
        String nginxSaveAs = nginxResolver.getFilename();
        expandedInstallDir = getInstallDir()+"/" + nginxResolver.getUnpackedDirectoryName(format("nginx-%s", getVersion()));

        boolean sticky = ((NginxController) entity).isSticky();
        boolean isMac = getMachine().getOsDetails().isMac();

        MutableMap<String, String> installGccPackageFlags = MutableMap.of(
                "onlyifmissing", "gcc",
                "yum", "gcc", 
                "apt", "gcc",
                "port", null);
        MutableMap<String, String> installMakePackageFlags = MutableMap.of(
                "onlyifmissing", "make",
                "yum", "make", 
                "apt", "make",
                "port", null);
        MutableMap<String, String> installPackageFlags = MutableMap.of(
                "yum", "openssl-devel pcre-devel", 
                "apt", "libssl-dev zlib1g-dev libpcre3-dev",
                "port", null);

        String stickyModuleVersion = entity.getConfig(NginxController.STICKY_VERSION);
        DownloadResolver stickyModuleResolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(
                this, "stickymodule", ImmutableMap.of("addonversion", stickyModuleVersion));
        List<String> stickyModuleUrls = stickyModuleResolver.getTargets();
        String stickyModuleSaveAs = stickyModuleResolver.getFilename();
        String stickyModuleExpandedInstallDir = String.format("%s/src/%s", expandedInstallDir, 
                stickyModuleResolver.getUnpackedDirectoryName("nginx-sticky-module-"+stickyModuleVersion));
        
        List<String> cmds = Lists.newArrayList();
        
        cmds.add(CommonCommands.INSTALL_TAR);
        cmds.add(CommonCommands.installPackage(installGccPackageFlags, "nginx-prerequisites-gcc"));
        cmds.add(CommonCommands.installPackage(installMakePackageFlags, "nginx-prerequisites-make"));
        cmds.add(CommonCommands.installPackage(installPackageFlags, "nginx-prerequisites"));
        cmds.addAll(CommonCommands.downloadUrlAs(nginxUrls, nginxSaveAs));
        
        if (isMac) {
            String pcreVersion = entity.getConfig(NginxController.PCRE_VERSION);
            DownloadResolver pcreResolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(
                    this, "pcre", ImmutableMap.of("addonversion", pcreVersion));
            List<String> pcreUrls = pcreResolver.getTargets();
            String pcreSaveAs = pcreResolver.getFilename();
            String pcreExpandedInstallDirname = pcreResolver.getUnpackedDirectoryName("pcre-"+pcreVersion);

            // Install PCRE
            cmds.addAll(CommonCommands.downloadUrlAs(pcreUrls, pcreSaveAs));
            cmds.add(format("mkdir -p %s/pcre-dist", getInstallDir()));
            cmds.add(format("tar xvzf %s", pcreSaveAs));
            cmds.add(format("cd %s", pcreExpandedInstallDirname));
            cmds.add(format("./configure --prefix=%s/pcre-dist", getInstallDir()));
            cmds.add("make");
            cmds.add("make install");
            cmds.add("cd ..");
        }
        
        cmds.add(format("tar xvzf %s", nginxSaveAs));
        cmds.add(format("cd %s", getExpandedInstallDir()));

        if (sticky) {
            cmds.add("cd src");
            cmds.addAll(CommonCommands.downloadUrlAs(stickyModuleUrls, stickyModuleSaveAs));
            cmds.add(format("tar xvzf %s", stickyModuleSaveAs));
            cmds.add("cd ..");
        }

        // Note that for OS X, not including space after "-L" because broken in 10.6.8 (but fixed in 10.7.x)
        //      see http://trac.nginx.org/nginx/ticket/227
        String withLdOpt = entity.getConfig(NginxController.WITH_LD_OPT);
        if (isMac) withLdOpt = format("-L%s/pcre-dist/lib", getInstallDir()) + (Strings.isBlank(withLdOpt) ? "" : " " + withLdOpt);
        String withCcOpt = entity.getConfig(NginxController.WITH_CC_OPT);
        
        cmds.addAll(ImmutableList.<String>of(
                "mkdir -p dist",
                "./configure"+
                    format(" --prefix=%s/dist", getExpandedInstallDir()) +
                    " --with-http_ssl_module" +
                    (sticky ? format(" --add-module=%s ", stickyModuleExpandedInstallDir) : "") +
                    (!Strings.isBlank(withLdOpt) ? format(" --with-ld-opt=\"%s\"", withLdOpt) : "") +
                    (!Strings.isBlank(withCcOpt) ? format(" --with-cc-opt=\"%s\"", withCcOpt) : "") ,
                "make install"));

        ScriptHelper script = newScript(INSTALLING)
                .body.append(cmds)
                .header.prepend("set -x")
                .gatherOutput()
                .failOnNonZeroResultCode(false);
        
        int result = script.execute();
        
        if (result != 0) {
            String notes = "likely an error building nginx. consult the brooklyn log ssh output for further details.\n"+
                    "note that this Brooklyn nginx driver compiles nginx from source. " +
                    "it attempts to install common prerequisites but this does not always succeed.\n";
            OsDetails os = getMachine().getOsDetails();
            if (os.isMac()) {
                notes += "deploying to Mac OS X, you will require Xcode and Xcode command-line tools, and on " +
                		"some versions the pcre library (e.g. using macports, sudo port install pcre).\n";
            }
            if (os.isWindows()) {
                notes += "this nginx driver is not designed for windows, unless cygwin is installed, and you are patient.\n";
            }
            if (getEntity().getApplication().getClass().getCanonicalName().startsWith("brooklyn.demo.")) {
                // this is maybe naughty ... but since we use nginx in the first demo example,
                // and since it's actually pretty complicated, let's give a little extra hand-holding
                notes +=
                		"if debugging this is all a bit much and you just want to run a demo, " +
                		"you have two fairly friendly options.\n" +
                		"1. you can use a well known cloud, like AWS or Rackspace, where this should run " +
                		"in a tried-and-tested Ubuntu or CentOS environment, without any problems " +
                		"(and if it does let us know and we'll fix it!).\n"+
                		"2. or you can just use the demo without nginx, instead access the appserver instances directly.\n";
            }

            if (!script.getResultStderr().isEmpty())
                notes += "\n" + "STDERR\n" + script.getResultStderr()+"\n";
            if (!script.getResultStdout().isEmpty())
                notes += "\n" + "STDOUT\n" + script.getResultStdout()+"\n";
            
            Tasks.setExtraStatusDetails(notes.trim());
            
            throw new IllegalStateException("Installation of nginx failed (shell returned non-zero result "+result+")");
        }
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).
            body.append(
                format("mkdir -p %s", getRunDir()),
                format("cp -R %s/dist/{conf,html,logs,sbin} %s", getExpandedInstallDir(), getRunDir())
        ).execute();
        
        customizationCompleted = true;
        getEntity().doExtraConfigurationDuringStart();
    }

    public boolean isCustomizationCompleted() {
        return customizationCompleted;
    }
    
    @Override
    public void launch() {
        // By default, nginx writes the pid of the master process to "logs/nginx.pid"
        Networking.checkPortsValid(MutableMap.of("httpPort", getHttpPort()));
        Map flags = MutableMap.of("usePidFile", false);

        // We wait for evidence of running because, using 
        // brooklyn.ssh.config.tool.class=brooklyn.util.internal.ssh.cli.SshCliTool,
        // we saw the ssh session return before the tomcat process was fully running 
        // so the process failed to start.
        newScript(flags, LAUNCHING).
                body.append(
                format("cd %s", getRunDir()),
                sudoBashCIfPrivilegedPort(getHttpPort(), format(
                        "nohup ./sbin/nginx -p %s/ -c conf/server.conf > ./console 2>&1 &", getRunDir())),
                format("for i in {1..10}\n" +
                        "do\n" +
                        "    test -f %s && ps -p `cat %s` && exit\n" +
                        "    sleep 1\n" +
                        "done\n" +
                        "echo \"Couldn't determine if process is running (pid not running); continuing but may subsequently fail\"",
                        getRunDir()+"/"+NGINX_PID_FILE, getRunDir()+"/"+NGINX_PID_FILE)
        ).execute();
    }

    public static String sudoIfPrivilegedPort(int port, String command) {
        return port < 1024 ? CommonCommands.sudo(command) : command;
    }
    
    public static String sudoBashCIfPrivilegedPort(int port, String command) {
        return port < 1024 ? CommonCommands.sudo("bash -c '"+command+"'") : command;
    }
    
    @Override
    public boolean isRunning() {
        Map flags = MutableMap.of("usePidFile", NGINX_PID_FILE);
        return newScript(flags, CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        // Don't `kill -9`, as that doesn't stop the worker processes
        String pidFile = NGINX_PID_FILE;
        Map flags = MutableMap.of("usePidFile", false);

        newScript(flags, STOPPING).
                body.append(
                format("cd %s", getRunDir()),
                format("export PID=`cat %s`", pidFile),
                "[[ -n \"$PID\" ]] || exit 0",
                sudoIfPrivilegedPort(getHttpPort(), "kill $PID")
        ).execute();
    }

    @Override
    public void kill() {
        stop(); // TODO Don't `kill -9`, as that doesn't stop the worker processes
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
    
    private final ExecController reloadExecutor = new ExecController(
            entity+"->reload",
            new Runnable() {
                public void run() {
                    reloadImpl();
                }
            });
    
    public void reload() {
        // If there are concurrent calls to reload (such that some calls come in when another call is queued), then 
        // don't bother doing the subsequent calls. Instead just rely on the currently queued call.
        //
        // Motivation is that calls to nginx.reload were backing up: we ended up executing lots of them in parallel
        // when there were several changes to the nginx conifg that requiring a reload. The problem can be particularly
        // bad because the ssh commands take a second or two - if 10 changes were made to the config in that time, we'd
        // end up executing reload 10 times in parallel.
        
        reloadExecutor.run();
    }
    
    private void reloadImpl() {
        // Note that previously, if serviceUp==false then we'd restart nginx.
        // That caused a race on stop()+reload(): nginx could simultaneously be stopping and also reconfiguring 
        // (e.g. due to a cluster-resize), the restart() would leave nginx running even after stop() had returned.
        //
        // Now we rely on NginxController always calling update (and thus reload) once it has started. This is
        // done in AbstractController.postActivation().
        //
        // If our blocking check sees that !isRunning() (and if a separate thread is starting it, and subsequently
        // calling waitForEntityStart()), we can guarantee that the start-thread's call to update will happen after 
        // this call to reload. So we this can be a no-op, and just rely on that subsequent call to update.
        
        Lifecycle lifecycle = entity.getAttribute(NginxController.SERVICE_STATE);
        boolean running = isRunning();

        if (!running) {
            log.debug("Ignoring reload of nginx "+entity+", because service is not running (state "+lifecycle+")");
            return;
        }
        
        doReloadNow();
    }
    
    /**
     * Instructs nginx to reload its configuration (without restarting, so don't lose any requests).
     * Can be overridden if necessary, to change the call used for reloading.
     */
    protected void doReloadNow() {
        /*
         * We use kill -HUP because that is recommended at http://wiki.nginx.org/CommandLine, 
         * but there is no noticeable difference (i.e. no impact on #365) compared to:
         *   sudoIfPrivilegedPort(getHttpPort(), format("./sbin/nginx -p %s/ -c conf/server.conf -s reload", getRunDir()))
         * 
         * Note that if conf file is invalid, you'll get no stdout/stderr from `kill` but you
         * do from using `nginx ... -s reload` so that can be handly when manually debugging.
         */
        newScript(RESTARTING).
            body.append(
                format("cd %s", getRunDir()),
                format("export PID=`cat %s`", NGINX_PID_FILE),
                sudoIfPrivilegedPort(getHttpPort(), "kill -HUP $PID")
        ).execute();
    }
    
    /**
     * Executes the given task, but only if another thread hasn't executed it for us (where the other thread
     * began executing it after the current caller of {@link #run()} began attempting to do so itself).
     * 
     * @author aled
     */
    private static class ExecController {
        private final String summary;
        private final Runnable task;
        private final AtomicLong counter = new AtomicLong();
        
        ExecController(String summary, Runnable task) {
            this.summary = summary;
            this.task = task;
        }
        
        void run() {
            long preCount = counter.get();
            synchronized (this) {
                if (counter.compareAndSet(preCount, preCount+1)) {
                    try {
                        if (log.isDebugEnabled()) log.debug("Executing {}; incremented count to {}", new Object[] {summary, counter});
                        task.run();
                    } catch (Exception e) {
                        if (log.isDebugEnabled()) log.debug("Failed executing {}; reseting count to {} and propagating exception: {}", new Object[] {summary, preCount, e});
                        counter.set(preCount);
                        throw Throwables.propagate(e);
                    }
                } else {
                    if (log.isDebugEnabled()) log.debug("Not executing {} because executed by another thread subsequent to us attempting (preCount {}; count {})", new Object[] {summary, preCount, counter});
                }
            }
        }
    }
}
