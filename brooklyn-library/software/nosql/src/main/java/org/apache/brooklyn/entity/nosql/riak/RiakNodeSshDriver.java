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
package org.apache.brooklyn.entity.nosql.riak;

import static java.lang.String.format;
import static org.apache.brooklyn.util.ssh.BashCommands.INSTALL_CURL;
import static org.apache.brooklyn.util.ssh.BashCommands.INSTALL_TAR;
import static org.apache.brooklyn.util.ssh.BashCommands.addSbinPathCommand;
import static org.apache.brooklyn.util.ssh.BashCommands.sbinPath;
import static org.apache.brooklyn.util.ssh.BashCommands.alternatives;
import static org.apache.brooklyn.util.ssh.BashCommands.chainGroup;
import static org.apache.brooklyn.util.ssh.BashCommands.commandToDownloadUrlAs;
import static org.apache.brooklyn.util.ssh.BashCommands.ifExecutableElse;
import static org.apache.brooklyn.util.ssh.BashCommands.ifNotExecutable;
import static org.apache.brooklyn.util.ssh.BashCommands.ok;
import static org.apache.brooklyn.util.ssh.BashCommands.sudo;
import static org.apache.brooklyn.util.text.StringEscapes.BashStringEscapes.escapeLiteralForDoubleQuotedBash;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.location.OsDetails;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

// TODO: Alter -env ERL_CRASH_DUMP path in vm.args
public class RiakNodeSshDriver extends JavaSoftwareProcessSshDriver implements RiakNodeDriver {

    private static final Logger LOG = LoggerFactory.getLogger(RiakNodeSshDriver.class);
    private static final String INSTALLING_FALLBACK = INSTALLING + "_fallback";

    public RiakNodeSshDriver(final RiakNodeImpl entity, final SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() {
        return "/var/log/riak/solr.log";
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

        // Set package install attribute
        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        if (osDetails.isLinux()) {
            entity.sensors().set(RiakNode.RIAK_PACKAGE_INSTALL, true);
        } else if (osDetails.isMac()) {
            entity.sensors().set(RiakNode.RIAK_PACKAGE_INSTALL, false);
        }
    }

    @Override
    public void install() {
        if (entity.getConfig(Attributes.DOWNLOAD_URL) != null) {
            LOG.warn("Ignoring download.url {}, use download.url.rhelcentos or download.url.mac", entity.getConfig(Attributes.DOWNLOAD_URL));
        }

        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();
        List<String> commands = Lists.newLinkedList();
        if (osDetails.isLinux()) {
            if (getEntity().isPackageDownloadUrlProvided()) {
                commands.addAll(installLinuxFromPackageUrl());
            } else {
                commands.addAll(installFromPackageCloud());
            }
        } else if (osDetails.isMac()) {
            commands.addAll(installMac());
        } else if (osDetails.isWindows()) {
            throw new UnsupportedOperationException("RiakNode not supported on Windows instances");
        } else {
            throw new IllegalStateException("Machine was not detected as linux, mac or windows! Installation does not know how to proceed with " +
                    getMachine() + ". Details: " + getMachine().getMachineDetails().getOsDetails());
        }

        int result = newScript(INSTALLING)
                .body.append(commands)
                .failIfBodyEmpty()
                .execute();

        if (result != 0 && osDetails.isLinux()) {
            result = newScript(INSTALLING_FALLBACK)
                    .body.append(installLinuxFromPackageUrl())
                    .execute();
        }

        if (result != 0) {
            throw new IllegalStateException(String.format("Install failed with result %d", result));
        }
    }

    private List<String> installLinuxFromPackageUrl() {
        DynamicTasks.queueIfPossible(SshTasks.dontRequireTtyForSudo(getMachine(), SshTasks.OnFailingTask.WARN_OR_IF_DYNAMIC_FAIL_MARKING_INESSENTIAL)).orSubmitAndBlock();

        String expandedInstallDir = getExpandedInstallDir();
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
                addSbinPathCommand(),
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
                sudo(String.format("yum localinstall -y %s", saveAsYum)));
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
                .add(ifNotExecutable("curl", INSTALL_CURL))
                .addAll(ifExecutableElse("yum", installDebianBased(), installRpmBased()))
                .build();
    }

    private ImmutableList<String> installDebianBased() {
        return ImmutableList.<String>builder()
                .add("curl https://packagecloud.io/install/repositories/basho/riak/script.deb.sh | " + BashCommands.sudo("bash"))
                .add(BashCommands.sudo("apt-get install --assume-yes riak=" + getEntity().getFullVersion() + "-1"))
                .build();
    }

    private ImmutableList<String> installRpmBased() {
        return ImmutableList.<String>builder()
                .add("curl https://packagecloud.io/install/repositories/basho/riak/script.rpm.sh | " + BashCommands.sudo("bash"))
                .add(BashCommands.sudo("yum install -y riak-" + getEntity().getFullVersion() + "*"))
                .build();
    }

    protected List<String> installMac() {
        String saveAs = resolver.getFilename();
        String url = entity.getAttribute(RiakNode.DOWNLOAD_URL_MAC);
        return ImmutableList.<String>builder()
                .add(INSTALL_TAR)
                .add(INSTALL_CURL)
                .add(commandToDownloadUrlAs(url, saveAs))
                .add("tar xzvf " + saveAs)
                .build();
    }

    @Override
    public void customize() {
        checkRiakOnPath();

        //create entity's runDir
        newScript(CUSTOMIZING).execute();

        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();

        List<String> commands = Lists.newLinkedList();
        commands.add(sudo("mkdir -p " + getRiakEtcDir()));

        if (isVersion1()) {
            String vmArgsTemplate = processTemplate(entity.getConfig(RiakNode.RIAK_VM_ARGS_TEMPLATE_URL));
            String saveAsVmArgs = Urls.mergePaths(getRunDir(), "vm.args");
            DynamicTasks.queue(SshEffectorTasks.put(saveAsVmArgs).contents(vmArgsTemplate));
            commands.add(sudo("mv " + saveAsVmArgs + " " + getRiakEtcDir()));

            String appConfigTemplate = processTemplate(entity.getConfig(RiakNode.RIAK_APP_CONFIG_TEMPLATE_URL));
            String saveAsAppConfig = Urls.mergePaths(getRunDir(), "app.config");
            DynamicTasks.queue(SshEffectorTasks.put(saveAsAppConfig).contents(appConfigTemplate));
            commands.add(sudo("mv " + saveAsAppConfig + " " + getRiakEtcDir()));
        } else {
            String templateUrl = osDetails.isMac() ? entity.getConfig(RiakNode.RIAK_CONF_TEMPLATE_URL_MAC) :
                    entity.getConfig(RiakNode.RIAK_CONF_TEMPLATE_URL_LINUX);
            String riakConfContent = processTemplate(templateUrl);
            String saveAsRiakConf = Urls.mergePaths(getRunDir(), "riak.conf");

            if(Strings.isNonBlank(entity.getConfig(RiakNode.RIAK_CONF_ADDITIONAL_CONTENT))) {
                String additionalConfigContent = processTemplateContents(entity.getConfig(RiakNode.RIAK_CONF_ADDITIONAL_CONTENT));
                riakConfContent += "\n## Brooklyn note: additional config\n";
                riakConfContent += additionalConfigContent;
            }

            DynamicTasks.queue(SshEffectorTasks.put(saveAsRiakConf).contents(riakConfContent));
            commands.add(sudo("mv " + saveAsRiakConf + " " + getRiakEtcDir()));
        }

        //increase open file limit (default min for riak is: 4096)
        //TODO: detect the actual limit then do the modification.
        //TODO: modify ulimit for linux distros
        //    commands.add(sudo("launchctl limit maxfiles 4096 32768"));
        if (osDetails.isMac()) {
            commands.add("ulimit -n 4096");
        }

        if (osDetails.isLinux() && isVersion1()) {
            commands.add(sudo("chown -R riak:riak " + getRiakEtcDir()));
        }

        // TODO platform_*_dir
        // TODO riak config log

        ScriptHelper customizeScript = newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(commands);

        if (!isRiakOnPath()) {
            addRiakOnPath(customizeScript);
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
        entity.sensors().set(RiakNode.RIAK_NODE_NAME, format("riak@%s", getSubnetHostname()));
    }

    @Override
    public void launch() {
        List<String> commands = Lists.newLinkedList();

        if (isPackageInstall()) {
            commands.add(addSbinPathCommand());
            commands.add(sudo(format("sh -c \"ulimit -n %s && service riak start\"", maxOpenFiles())));
        } else {
            // NOTE: See instructions at http://superuser.com/questions/433746/is-there-a-fix-for-the-too-many-open-files-in-system-error-on-os-x-10-7-1
            // for increasing the system limit for number of open files
            commands.add("ulimit -n 65536 || true"); // `BashCommands.ok` will put this in parentheses, which will set ulimit -n in the subshell
            commands.add(format("%s start >/dev/null 2>&1 < /dev/null &", getRiakCmd()));
        }

        ScriptHelper launchScript = newScript(LAUNCHING)
                .body.append(commands);

        if (!isRiakOnPath()) {
            addRiakOnPath(launchScript);
        }
        launchScript.failOnNonZeroResultCode().execute();

        String mainUri = String.format("http://%s:%s/admin", entity.getAttribute(Attributes.HOSTNAME), entity.getAttribute(RiakNode.RIAK_WEB_PORT));
        entity.sensors().set(Attributes.MAIN_URI, URI.create(mainUri));
    }

    @Override
    public void stop() {
        leaveCluster();

        String command = format("%s stop", getRiakCmd());
        command = isPackageInstall() ? sudo(command) : command;

        ScriptHelper stopScript = newScript(ImmutableMap.of(USE_PID_FILE, false), STOPPING)
                .body.append(command);

        if (!isRiakOnPath()) {
            addRiakOnPath(stopScript);
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
            addRiakOnPath(checkRunningScript);
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

    // TODO find a way to batch commit the changes, instead of committing for every operation.

    @Override
    public void joinCluster(String nodeName) {
        if (getRiakName().equals(nodeName)) {
            log.warn("Cannot join Riak node: {} to itself", nodeName);
        } else {
            if (!hasJoinedCluster()) {
                ScriptHelper joinClusterScript = newScript("joinCluster")
                        .body.append(sudo(format("%s cluster join %s", getRiakAdminCmd(), nodeName)))
                        .body.append(sudo(format("%s cluster plan", getRiakAdminCmd())))
                        .body.append(sudo(format("%s cluster commit", getRiakAdminCmd())))
                        .failOnNonZeroResultCode();

                if (!isRiakOnPath()) {
                    addRiakOnPath(joinClusterScript);
                }

                joinClusterScript.execute();

                entity.sensors().set(RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, Boolean.TRUE);
            } else {
                log.warn("entity {}: is already in the riak cluster", entity.getId());
            }
        }
    }

    @Override
    public void leaveCluster() {
        if (hasJoinedCluster()) {
            ScriptHelper leaveClusterScript = newScript("leaveCluster")
                    .body.append(sudo(format("%s cluster leave", getRiakAdminCmd())))
                    .body.append(sudo(format("%s cluster plan", getRiakAdminCmd())))
                    .body.append(sudo(format("%s cluster commit", getRiakAdminCmd())))
                    .failOnNonZeroResultCode();

            if (!isRiakOnPath()) {
                addRiakOnPath(leaveClusterScript);
            }

            leaveClusterScript.execute();

            entity.sensors().set(RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, Boolean.FALSE);
        } else {
            log.warn("entity {}: has already left the riak cluster", entity.getId());
        }
    }

    @Override
    public void removeNode(String nodeName) {
        ScriptHelper removeNodeScript = newScript("removeNode")
                .body.append(sudo(format("%s cluster force-remove %s", getRiakAdminCmd(), nodeName)))
                .body.append(sudo(format("%s down %s", getRiakAdminCmd(), nodeName)))
                .body.append(sudo(format("%s cluster plan", getRiakAdminCmd())))
                .body.append(sudo(format("%s cluster commit", getRiakAdminCmd())))
                .failOnNonZeroResultCode();

        if (!isRiakOnPath()) {
            addRiakOnPath(removeNodeScript);
        }

        removeNodeScript.execute();
    }

    @Override
    public void bucketTypeCreate(String bucketTypeName, String bucketTypeProperties) {
        ScriptHelper bucketTypeCreateScript = newScript("bucket-type_create " + bucketTypeName)
                .body.append(sudo(format("%s bucket-type create %s %s",
                        getRiakAdminCmd(),
                        bucketTypeName,
                        escapeLiteralForDoubleQuotedBash(bucketTypeProperties))));
        if(!isRiakOnPath()) {
            addRiakOnPath(bucketTypeCreateScript);
        }
        bucketTypeCreateScript.body.append(sudo(format("%s bucket-type activate %s", getRiakAdminCmd(), bucketTypeName)))
                .failOnNonZeroResultCode();

        bucketTypeCreateScript.execute();
    }

    @Override
    public List<String> bucketTypeList() {
        ScriptHelper bucketTypeListScript = newScript("bucket-types_list")
                .body.append(sudo(format("%s bucket-type list", getRiakAdminCmd())))
                .gatherOutput()
                .noExtraOutput()
                .failOnNonZeroResultCode();
        if (!isRiakOnPath()) {
            addRiakOnPath(bucketTypeListScript);
        }
        bucketTypeListScript.execute();
        String stdout = bucketTypeListScript.getResultStdout();
        return Arrays.asList(stdout.split("[\\r\\n]+"));
    }

    @Override
    public List<String> bucketTypeStatus(String bucketTypeName) {
        ScriptHelper bucketTypeStatusScript = newScript("bucket-type_status")
                .body.append(sudo(format("%s bucket-type status %s", getRiakAdminCmd(), bucketTypeName)))
                .gatherOutput()
                .noExtraOutput()
                .failOnNonZeroResultCode();
        if (!isRiakOnPath()) {
            addRiakOnPath(bucketTypeStatusScript);
        }
        bucketTypeStatusScript.execute();
        String stdout = bucketTypeStatusScript.getResultStdout();
        return Arrays.asList(stdout.split("[\\r\\n]+"));
    }

    @Override
    public void bucketTypeUpdate(String bucketTypeName, String bucketTypeProperties) {
        ScriptHelper bucketTypeStatusScript = newScript("bucket-type_update")
                .body.append(sudo(format("%s bucket-type update %s %s",
                        getRiakAdminCmd(),
                        bucketTypeName,
                        escapeLiteralForDoubleQuotedBash(bucketTypeProperties))))
                .failOnNonZeroResultCode();
        if (!isRiakOnPath()) {
            addRiakOnPath(bucketTypeStatusScript);
        }
        bucketTypeStatusScript.execute();
    }

    @Override
    public void bucketTypeActivate(String bucketTypeName) {
        ScriptHelper bucketTypeStatusScript = newScript("bucket-type_activate")
                .body.append(sudo(format("%s bucket-type activate %s", getRiakAdminCmd(), bucketTypeName)))
                .failOnNonZeroResultCode();
        if (!isRiakOnPath()) {
            addRiakOnPath(bucketTypeStatusScript);
        }
        bucketTypeStatusScript.execute();
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
                addRiakOnPath(recoverNodeScript);
            }

            recoverNodeScript.execute();

        } else {
            log.warn("entity {}: is not in the riak cluster", entity.getId());
        }
    }

    @Override
    public void setup() {
        if(entity.getConfig(RiakNode.SEARCH_ENABLED)) {
            // JavaSoftwareProcessSshDriver.setup() is called in order to install java
            super.setup();
        }
    }

    private Boolean hasJoinedCluster() {
        return Boolean.TRUE.equals(entity.getAttribute(RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER));
    }

    protected void checkRiakOnPath() {
        boolean riakOnPath = newScript("riakOnPath")
                .body.append("which riak")
                .execute() == 0;
        entity.sensors().set(RiakNode.RIAK_ON_PATH, riakOnPath);
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

    private void addRiakOnPath(ScriptHelper scriptHelper) {
        Map<String, String> newPathVariable = ImmutableMap.of("PATH", sbinPath());
//        log.warn("riak command not found on PATH. Altering future commands' environment variables from {} to {}", getShellEnvironment(), newPathVariable);
        scriptHelper.environmentVariablesReset(newPathVariable);
    }

    public Integer maxOpenFiles() {
        return entity.getConfig(RiakNode.RIAK_MAX_OPEN_FILES);
    }
}
