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
package brooklyn.entity.nosql.riak;

import static brooklyn.util.ssh.BashCommands.INSTALL_CURL;
import static brooklyn.util.ssh.BashCommands.INSTALL_TAR;
import static brooklyn.util.ssh.BashCommands.addSbinPathCommand;
import static brooklyn.util.ssh.BashCommands.alternatives;
import static brooklyn.util.ssh.BashCommands.chainGroup;
import static brooklyn.util.ssh.BashCommands.commandToDownloadUrlAs;
import static brooklyn.util.ssh.BashCommands.ifExecutableElse;
import static brooklyn.util.ssh.BashCommands.ifNotExecutable;
import static brooklyn.util.ssh.BashCommands.ok;
import static brooklyn.util.ssh.BashCommands.sudo;
import static java.lang.String.format;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.ssh.SshTasks;
import brooklyn.util.text.Strings;

// TODO: Alter -env ERL_CRASH_DUMP path in vm.args
public class RiakNodeSshDriver extends AbstractSoftwareProcessSshDriver implements RiakNodeDriver {

    private static final Logger LOG = LoggerFactory.getLogger(RiakNodeSshDriver.class);
    private static final String sbinPath = "$PATH:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin";
    private static final String INSTALLING_FALLBACK = INSTALLING + "_fallback";

    public RiakNodeSshDriver(final RiakNodeImpl entity, final SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public RiakNodeImpl getEntity() {
        return RiakNodeImpl.class.cast(super.getEntity());
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        MutableMap<String, String> result = MutableMap.copyOf(super.getShellEnvironment());
        // how to change epmd port, according to
        // http://serverfault.com/questions/582787/how-to-change-listening-interface-of-rabbitmqs-epmd-port-4369
        if (getEntity().getEpmdListenerPort() != null) {
            result.put("ERL_EPMD_PORT", Integer.toString(getEntity().getEpmdListenerPort()));
        }
        result.put("WAIT_FOR_ERLANG", "60");
        return result;
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("riak-%s", getVersion()))));
    }

    @Override
    public void install() {
        if (entity.getConfig(Attributes.DOWNLOAD_URL) != null) {
            LOG.warn("Ignoring download.url {}, use download.url.rhelcentos or download.url.mac", entity.getConfig(Attributes.DOWNLOAD_URL));
        }

        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        List<String> commands = Lists.newLinkedList();
        if (osDetails.isLinux()) {
            if(getEntity().isPackageDownloadUrlProvided()) {
                commands.addAll(installLinuxFromPackageUrl(getExpandedInstallDir()));
            } else {
                commands.addAll(installFromPackageCloud());
            }
            entity.setAttribute(RiakNode.RIAK_PACKAGE_INSTALL, true);
        } else if (osDetails.isMac()) {
            entity.setAttribute(RiakNode.RIAK_PACKAGE_INSTALL, false);
            commands.addAll(installMac());
        } else if (osDetails.isWindows()) {
            throw new UnsupportedOperationException("RiakNode not supported on Windows instances");
        } else {
            throw new IllegalStateException("Machine was not detected as linux, mac or windows! Installation does not know how to proceed with " +
                    getMachine() + ". Details: " + getMachine().getMachineDetails().getOsDetails());
        }

        try {
            newScript(INSTALLING)
                    .body.append(commands)
                    .failIfBodyEmpty()
                    .failOnNonZeroResultCode()
                    .inessential()
                    .execute();
        } catch(RuntimeException e) {
            if (osDetails.isLinux()) {
                newScript(INSTALLING_FALLBACK).body
                        .append(installLinuxFromPackageUrl(getExpandedInstallDir()))
                        .failIfBodyEmpty()
                        .failOnNonZeroResultCode()
                        .execute();
            }
        }

        checkRiakOnPath();
    }

    private List<String> installLinuxFromPackageUrl(String expandedInstallDir) {
        DynamicTasks.queueIfPossible(SshTasks.dontRequireTtyForSudo(getMachine(), SshTasks.OnFailingTask.WARN_OR_IF_DYNAMIC_FAIL_MARKING_INESSENTIAL)).orSubmitAndBlock();

        String installBin = Urls.mergePaths(expandedInstallDir, "bin");
        String saveAsYum = "riak.rpm";
        String saveAsApt = "riak.deb";
        OsDetails osDetails = getMachine().getOsDetails();

        String downloadUrl;
        String osReleaseCmd;
        if ("debian".equalsIgnoreCase(osDetails.getName())) {
            // TODO osDetails.getName() is returning "linux", instead of debian/ubuntu on AWS with jenkins image,
            //      running as integration test targetting localhost.
            // TODO Debian support (default debian image fails with 'sudo: command not found')
            downloadUrl = (String)entity.getAttribute(RiakNode.DOWNLOAD_URL_DEBIAN);
            osReleaseCmd = osDetails.getVersion().substring(0, osDetails.getVersion().indexOf("."));
        } else {
            // assume Ubuntu
            downloadUrl = (String)entity.getAttribute(RiakNode.DOWNLOAD_URL_UBUNTU);
            osReleaseCmd = "`lsb_release -sc` && " +
                    "export OS_RELEASE=`([[ \"lucid natty precise\" =~ (^| )\\$OS_RELEASE($| ) ]] && echo $OS_RELEASE || echo precise)`";
        }
        String apt = chainGroup(
                //debian fix
                "export PATH=$PATH:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "which apt-get",
                ok(sudo("apt-get -y --allow-unauthenticated install logrotate libpam0g-dev libssl0.9.8")),
                "export OS_NAME=" + Strings.toLowerCase(osDetails.getName()),
                "export OS_RELEASE=" + osReleaseCmd,
                String.format("wget -O %s %s", saveAsApt, downloadUrl),
                sudo(String.format("dpkg -i %s", saveAsApt)));
        String yum = chainGroup(
                "which yum",
                ok(sudo("yum -y install openssl")),
                String.format("wget -O %s %s", saveAsYum, entity.getAttribute(RiakNode.DOWNLOAD_URL_RHEL_CENTOS)),
                sudo(String.format("rpm -Uvh %s", saveAsYum)));
        return ImmutableList.<String>builder()
                .add("mkdir -p " + installBin)
                .add(INSTALL_CURL)
                .add(alternatives(apt, yum))
                .add("ln -s `which riak` " + Urls.mergePaths(installBin, "riak"))
                .add("ln -s `which riak-admin` " + Urls.mergePaths(installBin, "riak-admin"))
                .build();
    }

    private List<String> installFromPackageCloud() {
        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        return ImmutableList.<String>builder()
                .add(osDetails.getName().toLowerCase().contains("debian") ? addSbinPathCommand() : "")
                .add(ifNotExecutable("curl", Joiner.on('\n').join(installCurl())))
                .addAll(ifExecutableElse("yum", installDebianBased(), installRpmBased()))
                .build();
    }

    public List<String> installCurl() {
        return ImmutableList.<String>builder()
                .add(ifExecutableElse("yum",
                        BashCommands.sudo("apt-get install --assume-yes curl"),
                        BashCommands.sudo("yum install -y curl")))
                .build();
    }

    private ImmutableList<String> installDebianBased() {
        return ImmutableList.<String>builder()
                .add("curl https://packagecloud.io/install/repositories/basho/riak/script.deb | " + BashCommands.sudo("bash"))
                .add(BashCommands.sudo("apt-get install --assume-yes riak=" + getEntity().getFullVersion() + "-1"))
                .build();
    }

    private ImmutableList<String> installRpmBased() {
        return ImmutableList.<String>builder()
                .add("curl https://packagecloud.io/install/repositories/basho/riak/script.rpm | " + BashCommands.sudo("bash"))
                .add(BashCommands.sudo("yum install -y riak-" + getEntity().getFullVersion() + "-1"))
                .build();
    }

    protected List<String> installMac() {
        String saveAs = resolver.getFilename();
        String url = entity.getAttribute(RiakNode.DOWNLOAD_URL_MAC).toString();
        return ImmutableList.<String>builder()
                .add(INSTALL_TAR)
                .add(INSTALL_CURL)
                .add(commandToDownloadUrlAs(url, saveAs))
                .add("tar xzvf " + saveAs)
                .build();
    }

    @Override
    public void customize() {
        //create entity's runDir
        newScript(CUSTOMIZING).execute();

        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();

        List<String> commands = Lists.newLinkedList();
        commands.add(sudo("mkdir -p " + getRiakEtcDir()));

        if (isVersion1()) {
            String vmArgsTemplate = processTemplate(entity.getConfig(RiakNode.RIAK_VM_ARGS_TEMPLATE_URL));
            String saveAsVmArgs = Urls.mergePaths(getRunDir(), "vm.args");
            DynamicTasks.queueIfPossible(SshEffectorTasks.put(saveAsVmArgs).contents(vmArgsTemplate));
            commands.add(sudo("mv " + saveAsVmArgs + " " + getRiakEtcDir()));

            String appConfigTemplate = processTemplate(entity.getConfig(RiakNode.RIAK_APP_CONFIG_TEMPLATE_URL));
            String saveAsAppConfig = Urls.mergePaths(getRunDir(), "app.config");
            DynamicTasks.queueIfPossible(SshEffectorTasks.put(saveAsAppConfig).contents(appConfigTemplate));
            commands.add(sudo("mv " + saveAsAppConfig + " " + getRiakEtcDir()));
        } else {
            String templateUrl = osDetails.isMac() ? entity.getConfig(RiakNode.RIAK_CONF_TEMPLATE_URL_MAC) :
                    entity.getConfig(RiakNode.RIAK_CONF_TEMPLATE_URL_LINUX);
            String riakConfTemplate = processTemplate(templateUrl);
            String saveAsRiakConf = Urls.mergePaths(getRunDir(), "riak.conf");
            DynamicTasks.queueIfPossible(SshEffectorTasks.put(saveAsRiakConf).contents(riakConfTemplate));
            commands.add(sudo("mv " + saveAsRiakConf + " " + getRiakEtcDir()));
        }

        //increase open file limit (default min for riak is: 4096)
        //TODO: detect the actual limit then do the modification.
        //TODO: modify ulimit for linux distros
        //    commands.add(sudo("launchctl limit maxfiles 4096 32768"));
        if (osDetails.isMac()) {
            commands.add("ulimit -n 4096");
        } else if (osDetails.isLinux() && isVersion1()) {
            commands.add(sudo("chown -R riak:riak " + getRiakEtcDir()));
        }

        // TODO platform_*_dir
        // TODO riak config log

        ScriptHelper customizeScript = newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(commands);

        if (!isRiakOnPath()) {
            Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
            log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
            customizeScript.environmentVariablesReset(newPathVariable);
        }
        customizeScript.failOnNonZeroResultCode().execute();

        if (osDetails.isLinux()) {
            ImmutableMap<String, String> sysctl = ImmutableMap.<String, String>builder()
                    .put("vm.swappiness", "0")
                    .put("net.core.somaxconn", "40000")
                    .put("net.ipv4.tcp_max_syn_backlog", "40000")
                    .put("net.ipv4.tcp_sack",  "1")
                    .put("net.ipv4.tcp_window_scaling",  "15")
                    .put("net.ipv4.tcp_fin_timeout",     "1")
                    .put("net.ipv4.tcp_keepalive_intvl", "30")
                    .put("net.ipv4.tcp_tw_reuse",        "1")
                    .put("net.ipv4.tcp_moderate_rcvbuf", "1")
                    .build();

            ScriptHelper optimize = newScript(CUSTOMIZING + "network")
                .body.append(sudo("sysctl " + Joiner.on(' ').withKeyValueSeparator("=").join(sysctl)));

            Optional<Boolean> enable = Optional.fromNullable(entity.getConfig(RiakNode.OPTIMIZE_HOST_NETWORKING));
            if (!enable.isPresent()) optimize.inessential();
            if (enable.or(true)) optimize.execute();
        }

        //set the riak node name
        entity.setAttribute(RiakNode.RIAK_NODE_NAME, format("riak@%s", getSubnetHostname()));
    }

    @Override
    public void launch() {
        List<String> commands = Lists.newLinkedList();

        if (isPackageInstall()) {
            commands.add(addSbinPathCommand());
            commands.add(sudo("service riak start"));
        } else {
            // NOTE: See instructions at http://superuser.com/questions/433746/is-there-a-fix-for-the-too-many-open-files-in-system-error-on-os-x-10-7-1
            // for increasing the system limit for number of open files
            commands.add("ulimit -n 65536 || true"); // `BashCommands.ok` will put this in parentheses, which will set ulimit -n in the subshell
            commands.add(format("%s start >/dev/null 2>&1 < /dev/null &", getRiakCmd()));
        }

        ScriptHelper launchScript = newScript(LAUNCHING)
                .body.append(commands);

        if (!isRiakOnPath()) {
            Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
            log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
            launchScript.environmentVariablesReset(newPathVariable);
        }
        launchScript.failOnNonZeroResultCode().execute();

        String mainUri = String.format("http://%s:%s/admin", entity.getAttribute(Attributes.HOSTNAME), entity.getAttribute(RiakNode.RIAK_WEB_PORT));
        entity.setAttribute(Attributes.MAIN_URI, URI.create(mainUri));
    }

    @Override
    public void stop() {
        leaveCluster("");

        String command = format("%s stop", getRiakCmd());
        command = isPackageInstall() ? sudo(command) : command;

        ScriptHelper stopScript = newScript(ImmutableMap.of(USE_PID_FILE, false), STOPPING)
                .body.append(command);

        if (!isRiakOnPath()) {
            Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
            log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
            stopScript.environmentVariablesReset(newPathVariable);
        }

        int result = stopScript.failOnNonZeroResultCode().execute();
        if (result != 0) {
            newScript(ImmutableMap.of(USE_PID_FILE, false), STOPPING).execute();
        }
    }

    @Override
    public boolean isRunning() {
        // Version 2.0.0 requires sudo for `riak ping`
        ScriptHelper checkRunningScript = newScript(CHECK_RUNNING)
                .body.append(sudo(format("%s ping", getRiakCmd())));

        if (!isRiakOnPath()) {
            Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
            log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
            checkRunningScript.environmentVariablesReset(newPathVariable);
        }
        return (checkRunningScript.execute() == 0);
    }

    public boolean isPackageInstall() {
        return entity.getAttribute(RiakNode.RIAK_PACKAGE_INSTALL);
    }

    public boolean isRiakOnPath() {
        return entity.getAttribute(RiakNode.RIAK_ON_PATH);
    }

    public String getRiakEtcDir() {
        return isPackageInstall() ? "/etc/riak" : Urls.mergePaths(getExpandedInstallDir(), "etc");
    }

    protected String getRiakCmd() {
        return isPackageInstall() ? "riak" : Urls.mergePaths(getExpandedInstallDir(), "bin/riak");
    }

    protected String getRiakAdminCmd() {
        return isPackageInstall() ? "riak-admin" : Urls.mergePaths(getExpandedInstallDir(), "bin/riak-admin");
    }

    @Override
    public void joinCluster(String nodeName) {
        //FIXME: find a way to batch commit the changes, instead of committing for every operation.

        if (getRiakName().equals(nodeName)) {
            log.warn("cannot join riak node: {} to itself", nodeName);
        } else {
            if (!hasJoinedCluster()) {

                ScriptHelper joinClusterScript = newScript("joinCluster")
                        .body.append(sudo(format("%s cluster join %s", getRiakAdminCmd(), nodeName)))
                        .failOnNonZeroResultCode();

                if (!isRiakOnPath()) {
                    Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
                    log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
                    joinClusterScript.environmentVariablesReset(newPathVariable);
                }

                joinClusterScript.execute();

                entity.setAttribute(RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, Boolean.TRUE);
            } else {
                log.warn("entity {}: is already in the riak cluster", entity.getId());
            }
        }
    }

    @Override
    public void leaveCluster(String nodeName) {
        //TODO: add 'riak-admin cluster force-remove' for erroneous and unrecoverable nodes.
        //FIXME: find a way to batch commit the changes, instead of committing for every operation.
        //FIXME: find a way to check if the node is the last in the cluster to avoid removing the only member and getting "last node error"

        if (hasJoinedCluster()) {
            ScriptHelper leaveClusterScript = newScript("leaveCluster")
                    .body.append(sudo(format("%s cluster leave %s", getRiakAdminCmd(), nodeName)))
                    .body.append(sudo(format("%s cluster plan", getRiakAdminCmd())))
                    .body.append(sudo(format("%s cluster commit", getRiakAdminCmd())))
                    .failOnNonZeroResultCode();

            if (!isRiakOnPath()) {
                Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
                log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
                leaveClusterScript.environmentVariablesReset(newPathVariable);
            }

            leaveClusterScript.execute();

            entity.setAttribute(RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, Boolean.FALSE);
        } else {
            log.warn("entity {}: is not in the riak cluster", entity.getId());
        }
    }

    @Override
    public void commitCluster() {
        if (hasJoinedCluster()) {
            ScriptHelper commitClusterScript = newScript("commitCluster")
                    .body.append(sudo(format("%s cluster plan", getRiakAdminCmd())))
                    .body.append(sudo(format("%s cluster commit", getRiakAdminCmd())))
                    .failOnNonZeroResultCode();

            if (!isRiakOnPath()) {
                Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
                log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
                commitClusterScript.environmentVariablesReset(newPathVariable);
            }
            commitClusterScript.execute();

        } else {
            log.warn("entity {}: is not in the riak cluster", entity.getId());
        }
    }

    @Override
    public void recoverFailedNode(String nodeName) {
        //TODO find ways to detect a faulty/failed node
        //argument passed 'node' is any working node in the riak cluster
        //following the instruction from: http://docs.basho.com/riak/latest/ops/running/recovery/failed-node/

        if (hasJoinedCluster()) {
            String failedNodeName = getRiakName();


            String stopCommand = format("%s stop", getRiakCmd());
            stopCommand = isPackageInstall() ? sudo(stopCommand) : stopCommand;

            String startCommand = format("%s start > /dev/null 2>&1 < /dev/null &", getRiakCmd());
            startCommand = isPackageInstall() ? sudo(startCommand) : startCommand;

            ScriptHelper recoverNodeScript = newScript("recoverNode")
                    .body.append(stopCommand)
                    .body.append(format("%s down %s", getRiakAdminCmd(), failedNodeName))
                    .body.append(sudo(format("rm -rf %s", getRingStateDir())))
                    .body.append(startCommand)
                    .body.append(sudo(format("%s cluster join %s", getRiakAdminCmd(), nodeName)))
                    .body.append(sudo(format("%s cluster plan", getRiakAdminCmd())))
                    .body.append(sudo(format("%s cluster commit", getRiakAdminCmd())))
                    .failOnNonZeroResultCode();

            if (!isRiakOnPath()) {
                Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath);
                log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
                recoverNodeScript.environmentVariablesReset(newPathVariable);
            }

            recoverNodeScript.execute();

        } else {
            log.warn("entity {}: is not in the riak cluster", entity.getId());
        }
    }

    private Boolean hasJoinedCluster() {
        return Boolean.TRUE.equals(entity.getAttribute(RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER));
    }

    protected void checkRiakOnPath() {
        boolean riakOnPath = newScript("riakOnPath")
                .body.append("which riak")
                .execute() == 0;
        entity.setAttribute(RiakNode.RIAK_ON_PATH, riakOnPath);
    }

    private String getRiakName() {
        return entity.getAttribute(RiakNode.RIAK_NODE_NAME);
    }

    private String getRingStateDir() {
        //TODO: check for non-package install.
        return isPackageInstall() ? "/var/lib/riak/ring" : Urls.mergePaths(getExpandedInstallDir(), "lib/ring");
    }

    protected boolean isVersion1() {
        return getVersion().startsWith("1.");
    }

    @Override
    public String getOsMajorVersion() {
        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        String osVersion = osDetails.getVersion();
        return osVersion.contains(".") ? osVersion.substring(0, osVersion.indexOf(".")) : osVersion;
    }
}
