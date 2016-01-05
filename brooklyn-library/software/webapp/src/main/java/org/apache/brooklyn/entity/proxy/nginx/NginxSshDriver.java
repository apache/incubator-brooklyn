/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.entity.proxy.nginx;

import static java.lang.String.format;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.brooklyn.api.entity.drivers.downloads.DownloadResolver;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.OsDetails;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.entity.proxy.AbstractController;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasks.OnFailingTask;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * Start a {@link NginxController} in a {@link Location} accessible over ssh.
 */
public class NginxSshDriver extends AbstractSoftwareProcessSshDriver implements NginxDriver {

    // TODO An alternative way of installing nginx is described at:
    //   http://sjp.co.nz/posts/building-nginx-for-debian-systems/
    // It's use of `apt-get source nginx` and `apt-get build-dep nginx` makes
    // it look higher level and therefore more appealing.

    public static final Logger log = LoggerFactory.getLogger(NginxSshDriver.class);
    public static final String NGINX_PID_FILE = "logs/nginx.pid";

    private boolean customizationCompleted = false;

    public NginxSshDriver(NginxControllerImpl entity, SshMachineLocation machine) {
        super(entity, machine);

        entity.sensors().set(Attributes.LOG_FILE_LOCATION, getLogFileLocation());
        entity.sensors().set(NginxController.ACCESS_LOG_LOCATION, getAccessLogLocation());
        entity.sensors().set(NginxController.ERROR_LOG_LOCATION, getErrorLogLocation());
    }

    @Override
    public NginxControllerImpl getEntity() {
        return (NginxControllerImpl) super.getEntity();
    }

    public String getLogFileLocation() {
        return format("%s/console", getRunDir());
    }

    public String getAccessLogLocation() {
        String accessLog = entity.getConfig(NginxController.ACCESS_LOG_LOCATION);
        return format("%s/%s", getRunDir(), accessLog);
    }

    public String getErrorLogLocation() {
        String errorLog = entity.getConfig(NginxController.ERROR_LOG_LOCATION);
        return format("%s/%s", getRunDir(), errorLog);
    }

    /** By default Nginx writes the pid of the master process to {@code logs/nginx.pid} */
    @Override
    public String getPidFile() {
        return format("%s/%s", getRunDir(), NGINX_PID_FILE);
    }

    @Deprecated /** @deprecated since 0.7.0 use #getPort */
    public Integer getHttpPort() {
        return getEntity().getPort();
    }

    public Integer getPort() {
        return getEntity().getPort();
    }

    @Override
    public void rebind() {
        customizationCompleted = true;
    }

    @Override
    public void postLaunch() {
        entity.sensors().set(NginxController.PID_FILE, getRunDir() + "/" + AbstractSoftwareProcessSshDriver.PID_FILENAME);
        if (((AbstractController)entity).isSsl()) {
            entity.sensors().set(Attributes.HTTPS_PORT, getPort());
            ((EntityInternal)entity).removeAttribute(Attributes.HTTP_PORT);
        } else {
            entity.sensors().set(Attributes.HTTP_PORT, getPort());
            ((EntityInternal)entity).removeAttribute(Attributes.HTTPS_PORT);
        }
        super.postLaunch();
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("nginx-%s", getVersion()))));
    }

    @Override
    public void install() {
        // inessential here, installation will fail later if it needs to sudo (eg if using port 80)
        DynamicTasks.queueIfPossible(SshTasks.dontRequireTtyForSudo(getMachine(), OnFailingTask.WARN_OR_IF_DYNAMIC_FAIL_MARKING_INESSENTIAL)).orSubmitAndBlock();

        List<String> nginxUrls = resolver.getTargets();
        String nginxSaveAs = resolver.getFilename();

        boolean sticky = ((NginxController) entity).isSticky();
        boolean isMac = getMachine().getOsDetails().isMac();

        MutableMap<String, String> installGccPackageFlags = MutableMap.of(
                "onlyifmissing", "gcc",
                "yum", "gcc",
                "apt", "gcc",
                "zypper", "gcc",
                "port", null);
        MutableMap<String, String> installMakePackageFlags = MutableMap.of(
                "onlyifmissing", "make",
                "yum", "make",
                "apt", "make",
                "zypper", "make",
                "port", null);
        MutableMap<String, String> installPackageFlags = MutableMap.of(
                "yum", "openssl-devel pcre-devel",
                "apt", "libssl-dev zlib1g-dev libpcre3-dev",
                "zypper", "libopenssl-devel pcre-devel",
                "port", null);

        String stickyModuleVersion = entity.getConfig(NginxController.STICKY_VERSION);
        DownloadResolver stickyModuleResolver = mgmt().getEntityDownloadsManager().newDownloader(
                this, "stickymodule", ImmutableMap.of("addonversion", stickyModuleVersion));
        List<String> stickyModuleUrls = stickyModuleResolver.getTargets();
        String stickyModuleSaveAs = stickyModuleResolver.getFilename();
        String stickyModuleExpandedInstallDir = String.format("%s/src/%s", getExpandedInstallDir(),
                stickyModuleResolver.getUnpackedDirectoryName("nginx-sticky-module-"+stickyModuleVersion));

        List<String> cmds = Lists.newArrayList();

        cmds.add(BashCommands.INSTALL_TAR);
        cmds.add(BashCommands.alternatives(
                BashCommands.ifExecutableElse0("apt-get", BashCommands.installPackage("build-essential")),
                BashCommands.ifExecutableElse0("yum", BashCommands.sudo("yum -y --nogpgcheck groupinstall \"Development Tools\""))));
        cmds.add(BashCommands.installPackage(installGccPackageFlags, "nginx-prerequisites-gcc"));
        cmds.add(BashCommands.installPackage(installMakePackageFlags, "nginx-prerequisites-make"));
        cmds.add(BashCommands.installPackage(installPackageFlags, "nginx-prerequisites"));
        cmds.addAll(BashCommands.commandsToDownloadUrlsAs(nginxUrls, nginxSaveAs));

        String pcreExpandedInstallDirname = "";
        if (isMac) {
            String pcreVersion = entity.getConfig(NginxController.PCRE_VERSION);
            DownloadResolver pcreResolver = mgmt().getEntityDownloadsManager().newDownloader(
                    this, "pcre", ImmutableMap.of("addonversion", pcreVersion));
            List<String> pcreUrls = pcreResolver.getTargets();
            String pcreSaveAs = pcreResolver.getFilename();
            pcreExpandedInstallDirname = pcreResolver.getUnpackedDirectoryName("pcre-"+pcreVersion);

            // Install PCRE
            cmds.addAll(BashCommands.commandsToDownloadUrlsAs(pcreUrls, pcreSaveAs));
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
            // Latest versions of sticky module expand to a different folder than the file name.
            // Extract to folder set by us so we know where the sources are.
            cmds.add(format("mkdir -p %s", stickyModuleExpandedInstallDir));
            cmds.add(format("pushd %s", stickyModuleExpandedInstallDir));
            cmds.addAll(BashCommands.commandsToDownloadUrlsAs(stickyModuleUrls, stickyModuleSaveAs));
            cmds.add(format("tar --strip-component=1 -xvzf %s", stickyModuleSaveAs));
            cmds.add("popd");
        }

        // Note that for OS X, not including space after "-L" because broken in 10.6.8 (but fixed in 10.7.x)
        //      see http://trac.nginx.org/nginx/ticket/227
        String withLdOpt = entity.getConfig(NginxController.WITH_LD_OPT);
        if (isMac) withLdOpt = format("-L%s/pcre-dist/lib", getInstallDir()) + (Strings.isBlank(withLdOpt) ? "" : " " + withLdOpt);
        String withCcOpt = entity.getConfig(NginxController.WITH_CC_OPT);
        
        if (isMac) {
            // TODO Upgrade sticky module as soon as a fix for https://bitbucket.org/nginx-goodies/nginx-sticky-module-ng/issue/16/can-not-compile-on-macosx-yosemite
            // is released and remove this block.
            withCcOpt = (Strings.isBlank(withCcOpt) ? "" : (withCcOpt + " ")) + "-Wno-error";
        }

        StringBuilder configureCommand = new StringBuilder("./configure")
                .append(format(" --prefix=%s/dist", getExpandedInstallDir()))
                .append(" --with-http_ssl_module")
                .append(sticky ? format(" --add-module=%s ", stickyModuleExpandedInstallDir) : "")
                .append(!Strings.isBlank(withLdOpt) ? format(" --with-ld-opt=\"%s\"", withLdOpt) : "")
                .append(!Strings.isBlank(withCcOpt) ? format(" --with-cc-opt=\"%s\"", withCcOpt) : "")
                ;
        if (isMac) {
            configureCommand.append(" --with-pcre=")
                    .append(getInstallDir()).append("/").append(pcreExpandedInstallDirname);
        }

        cmds.addAll(ImmutableList.of(
                "mkdir -p dist",
                configureCommand.toString(),
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

            if (!script.getResultStderr().isEmpty()) {
                notes += "\n" + "STDERR\n" + script.getResultStderr()+"\n";
                Streams.logStreamTail(log, "STDERR of problem in "+Tasks.current(), Streams.byteArrayOfString(script.getResultStderr()), 1024);
            }
            if (!script.getResultStdout().isEmpty()) {
                notes += "\n" + "STDOUT\n" + script.getResultStdout()+"\n";
                Streams.logStreamTail(log, "STDOUT of problem in "+Tasks.current(), Streams.byteArrayOfString(script.getResultStdout()), 1024);
            }

            Tasks.setExtraStatusDetails(notes.trim());

            throw new IllegalStateException("Installation of nginx failed (shell returned non-zero result "+result+")");
        }
    }

    private ManagementContext mgmt() {
        return ((EntityInternal) entity).getManagementContext();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING)
                .body.append(
                        format("mkdir -p %s", getRunDir()),
                        format("cp -R %s/dist/{conf,html,logs,sbin} %s", getExpandedInstallDir(), getRunDir()))
                .execute();

        // Install static content archive, if specified
        String archiveUrl = entity.getConfig(NginxController.STATIC_CONTENT_ARCHIVE_URL);
        if (Strings.isNonBlank(archiveUrl)) {
            getEntity().deploy(archiveUrl);
        }

        customizationCompleted = true;
    }

    @Override
    public boolean isCustomizationCompleted() {
        return customizationCompleted;
    }

    @Override
    public void launch() {
        // TODO if can't be root, and ports > 1024 are in the allowed port range,
        // prefer that; could do this on SshMachineLocation which implements PortSupplier,
        // invoked from PortAttrSensorAndConfigKey, which is invoked from MachineLifecycleTasks.preStartCustom
        Networking.checkPortsValid(MutableMap.of("port", getPort()));

        getEntity().doExtraConfigurationDuringStart();

        // We wait for evidence of running because, using SshCliTool,
        // we saw the ssh session return before the tomcat process was fully running
        // so the process failed to start.
        newScript(MutableMap.of("usePidFile", false), LAUNCHING)
                .body.append(
                        format("cd %s", getRunDir()),
                        BashCommands.requireExecutable("./sbin/nginx"),
                        sudoBashCIfPrivilegedPort(getPort(), format(
                                "nohup ./sbin/nginx -p %s/ -c conf/server.conf > %s 2>&1 &", getRunDir(), getLogFileLocation())),
                        format("for i in {1..10}\n" +
                                "do\n" +
                                "    test -f %1$s && ps -p `cat %1$s` && exit\n" +
                                "    sleep 1\n" +
                                "done\n" +
                                "echo \"No explicit error launching nginx but couldn't find process by pid; continuing but may subsequently fail\"\n" +
                                "cat %2$s | tee /dev/stderr",
                                getPidFile(), getLogFileLocation()))
                .execute();
    }

    public static String sudoIfPrivilegedPort(int port, String command) {
        return port < 1024 ? BashCommands.sudo(command) : command;
    }

    public static String sudoBashCIfPrivilegedPort(int port, String command) {
        return port < 1024 ? BashCommands.sudo("bash -c '"+command+"'") : command;
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", getPidFile()), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        // Don't `kill -9`, as that doesn't stop the worker processes
        newScript(MutableMap.of("usePidFile", false), STOPPING).
                body.append(
                        format("cd %s", getRunDir()),
                        format("export PID=`cat %s`", getPidFile()),
                        "test -n \"$PID\" || exit 0",
                        sudoIfPrivilegedPort(getPort(), "kill $PID"))
                .execute();
    }

    @Override
    public void kill() {
        stop();
    }

    private final ExecController reloadExecutor = new ExecController(
            entity+"->reload",
            new Runnable() {
                @Override
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

        Lifecycle lifecycle = entity.getAttribute(NginxController.SERVICE_STATE_ACTUAL);
        if (lifecycle==Lifecycle.STOPPING || lifecycle==Lifecycle.STOPPED || !isRunning()) {
            log.debug("Ignoring reload of nginx "+entity+", because service is not running (state "+lifecycle+")");
            return;
        }

        doReloadNow();
    }

    /**
     * Instructs nginx to reload its configuration (without restarting, so don't lose any requests).
     * Can be overridden if necessary, to change the call used for reloading.
     */
    private void doReloadNow() {
        // We use kill -HUP because that is recommended at http://wiki.nginx.org/CommandLine,
        // but there is no noticeable difference (i.e. no impact on #365) compared to:
        //   sudoIfPrivilegedPort(getHttpPort(), format("./sbin/nginx -p %s/ -c conf/server.conf -s reload", getRunDir()))
        //
        // Note that if conf file is invalid, you'll get no stdout/stderr from `kill` but you
        // do from using `nginx ... -s reload` so that can be handy when manually debugging.

        log.debug("reloading nginx by simularing restart (kill -HUP) - {}", entity);
        newScript(RESTARTING)
                .body.append(
                        format("cd %s", getRunDir()),
                        format("export PID=`cat %s`", getPidFile()),
                        sudoIfPrivilegedPort(getPort(), "kill -HUP $PID"))
                .execute();
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
                        throw Exceptions.propagate(e);
                    }
                } else {
                    if (log.isDebugEnabled()) log.debug("Not executing {} because executed by another thread subsequent to us attempting (preCount {}; count {})", new Object[] {summary, preCount, counter});
                }
            }
        }
    }
}
