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
package org.apache.brooklyn.entity.messaging.rabbit;

import static java.lang.String.format;
import static org.apache.brooklyn.util.ssh.BashCommands.*;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.messaging.amqp.AmqpServer;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Strings;

/**
 * TODO javadoc
 */
public class RabbitSshDriver extends AbstractSoftwareProcessSshDriver implements RabbitDriver {

    private static final Logger log = LoggerFactory.getLogger(RabbitSshDriver.class);

    // See http://fedoraproject.org/wiki/EPEL/FAQ#howtouse
    private static final Map<String, String> CENTOS_VERSION_TO_EPEL_VERSION = ImmutableMap.of(
        "5", "5-4",
        "6", "6-8",
        "7", "7-5"
    );

    public RabbitSshDriver(RabbitBrokerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected String getLogFileLocation() { return getRunDir()+"/"+entity.getId()+".log"; }

    public Integer getAmqpPort() { return entity.getAttribute(AmqpServer.AMQP_PORT); }

    public String getVirtualHost() { return entity.getAttribute(AmqpServer.VIRTUAL_HOST_NAME); }

    public String getErlangVersion() { return entity.getConfig(RabbitBroker.ERLANG_VERSION); }

    @Override
    public RabbitBrokerImpl getEntity() {
        return (RabbitBrokerImpl) super.getEntity();
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("rabbitmq_server-%s", getVersion()))));
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();


        List<String> commands = ImmutableList.<String>builder()
                // RabbitMQ recommends Erlang 18. Release notes state:
                // ====
                // Minimum required Erlang version is R16B03 for plain ("just TCP") connections for all protocols 
                // and 17.5 for TLS ones (18.x is recommended for both).
                // ====
                //
                // The recommended provider for up to date Erlang versions is Erlang Solutions now.
                // Supported platforms by Erlang Solutions are: CentOS, RHEL, Ubuntu and Debian.
                // 
                // New Erlang versions for SUSE are provided via the openSUSE repositories
                // 
                // EPEL 6 provides only packages for Erlang 14, but EPEL 7 - Erlang 16
                // 
                .add(ifExecutableElse0("apt-get", getAptRepository()))
                .add(ifExecutableElse0("yum", getYumRepository()))
                .add(ifExecutableElse0("zypper", getZypperRepository()))
                .add(installPackage( // NOTE only 'port' states the version of Erlang used, maybe remove this constraint?
                        ImmutableMap.of(
                                "port", "erlang@"+getErlangVersion()+"+ssl"),
                                "erlang"))
                .addAll(commandsToDownloadUrlsAs(urls, saveAs))
                .add(installExecutable("tar"))
                .add(format("tar xvf %s",saveAs))
                .build();

        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(commands).execute();
    }

    @Override
    public void customize() {
        Networking.checkPortsValid(MutableMap.of("amqpPort", getAmqpPort()));
        ScriptHelper scriptHelper = newScript(CUSTOMIZING);

        scriptHelper.body.append(
                format("cp -R %s/* .", getExpandedInstallDir())
        );

        if (Boolean.TRUE.equals(entity.getConfig(RabbitBroker.ENABLE_MANAGEMENT_PLUGIN))) {
            scriptHelper.body.append(
                    "./sbin/rabbitmq-plugins enable rabbitmq_management"
            );
        }
        scriptHelper.failOnNonZeroResultCode();
        scriptHelper.execute();

        copyTemplate(entity.getConfig(RabbitBroker.CONFIG_TEMPLATE_URL), getConfigPath() + ".config");
    }

    @Override
    public void launch() {
        newScript(MutableMap.of("usePidFile", false), LAUNCHING)
            .body.append(
                "nohup ./sbin/rabbitmq-server > console-out.log 2> console-err.log &",
                "for i in {1..60}\n" +
                    "do\n" +
                     "    grep 'Starting broker... completed' console-out.log && exit\n" +
                     "    sleep 1\n" +
                     "done",
                "echo \"Couldn't determine if rabbitmq-server is running\"",
                "exit 1"
            ).execute();
    }

    @Override
    public void configure() {
        newScript(CUSTOMIZING)
            .body.append(
                "./sbin/rabbitmqctl add_vhost "+getEntity().getVirtualHost(),
                "./sbin/rabbitmqctl set_permissions -p "+getEntity().getVirtualHost()+" guest \".*\" \".*\" \".*\""
            ).execute();
    }

    public String getPidFile() { return "rabbitmq.pid"; }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", false), CHECK_RUNNING)
                .body.append("./sbin/rabbitmqctl -q status")
                .execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", false), STOPPING)
                .body.append("./sbin/rabbitmqctl stop")
                .execute();
    }

    @Override
    public void kill() {
        stop(); // TODO No pid file to easily do `kill -9`
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        return MutableMap.<String, String>builder()
                .putAll(super.getShellEnvironment())
                .put("RABBITMQ_HOME", getRunDir())
                .put("RABBITMQ_LOG_BASE", getRunDir())
                .put("RABBITMQ_NODENAME", getEntity().getId())
                .put("RABBITMQ_NODE_PORT", getAmqpPort().toString())
                .put("RABBITMQ_PID_FILE", getRunDir()+"/"+getPidFile())
                .put("RABBITMQ_CONFIG_FILE", getConfigPath())
                .build();
    }

    private String getConfigPath() {
        return getRunDir() + "/rabbitmq";
    }

    private String getYumRepository() {
        String yumRepoFileName = "erlang_solutions.repo";
        
        // Version and architecture are only required for download of epel package on RHEL/Centos systems so pick sensible
        // defaults if unavailable
        //
        // EPEL is still required as it is a prerequisite for the packages provided by Erlang Solutions
        
        String osMajorVersion = getMachine().getOsDetails().getVersion();
        if (Strings.isBlank(osMajorVersion)) {
            osMajorVersion = "7";
        } else {
            osMajorVersion = osMajorVersion.indexOf(".") > 0 ? osMajorVersion.substring(0, osMajorVersion.indexOf('.')) : osMajorVersion;
            if (!CENTOS_VERSION_TO_EPEL_VERSION.keySet().contains(osMajorVersion)) {
                osMajorVersion = "7";
            }
        }
        String epelVersion = CENTOS_VERSION_TO_EPEL_VERSION.get(osMajorVersion);
        String osArchitecture = getMachine().getOsDetails().getArch();
        if (Strings.isBlank(osArchitecture)) {
            osArchitecture = "x86_64";
        }

        // Erlang Solutions provide separate packages for RHEL and CentOS, but they are hosted in a single repo.
        // E.g. - http://packages.erlang-solutions.com/rpm/centos/6/x86_64/
        // 
        // Erlang Solutions created a repo configuration RPM which can NOT be used on RedHat, because it has explicit checks for CentOS.
        // 
        // Bellow we are creating a repo file that works for CentOS and RedHat
        return chainGroup(
                sudo("yum -y update ca-certificates"),
                sudo("rpm -Uvh --replacepkgs " + format("http://download.fedoraproject.org/pub/epel/%s/%s/epel-release-%s.noarch.rpm", osMajorVersion, osArchitecture, epelVersion)),
                "( cat << 'EOF_BROOKLYN'\n"
                   + "[erlang-solutions]\n"
                   + "name=Centos / RHEL " + osMajorVersion + " - $basearch - Erlang Solutions\n"
                   + "baseurl=http://packages.erlang-solutions.com/rpm/centos/" + osMajorVersion + "/$basearch\n"
                   + "gpgcheck=0\n"
                   + "gpgkey=http://packages.erlang-solutions.com/debian/erlang_solutions.asc\n"
                   + "enabled=1\n"
                   + "EOF_BROOKLYN\n"
                   + ") > " + yumRepoFileName,
                  sudo(format("mv %s /etc/yum.repos.d/", yumRepoFileName)),
                  sudo(format("chown root:root /etc/yum.repos.d/%s", yumRepoFileName))
            );
    }

    private String getAptRepository() {
        String debFileName = "erlang-repo.deb";

        return chainGroup(
                INSTALL_WGET,
                format("wget --quiet -O %s %s", debFileName, entity.getConfig(RabbitBroker.ERLANG_DEB_REPO_URL)),
                sudo(format("dpkg -i %s", debFileName))
            );
    }

    private String getZypperRepository() {
        return chainGroup(
                ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/devel:/languages:/erlang/SLE_11_SP3 erlang_sles_11")),
                ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/devel:/languages:/erlang/openSUSE_11.4 erlang_suse_11")),
                ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/devel:/languages:/erlang/openSUSE_12.3 erlang_suse_12")),
                ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/devel:/languages:/erlang/openSUSE_13.1 erlang_suse_13")));
    }
}

