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
package brooklyn.entity.messaging.rabbit;

import static brooklyn.util.ssh.BashCommands.*;
import static java.lang.String.format;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.messaging.amqp.AmqpServer;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * TODO javadoc
 */
public class RabbitSshDriver extends AbstractSoftwareProcessSshDriver implements RabbitDriver {

    private static final Logger log = LoggerFactory.getLogger(RabbitSshDriver.class);
    
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
                .add(ifExecutableElse0("zypper", chainGroup(
                        ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/devel:/languages:/erlang/SLE_11_SP3 erlang_sles_11")),
                        ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/devel:/languages:/erlang/openSUSE_11.4 erlang_suse_11")),
                        ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/devel:/languages:/erlang/openSUSE_12.3 erlang_suse_12")),
                        ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/devel:/languages:/erlang/openSUSE_13.1 erlang_suse_13")))))
                .add(installPackage( // NOTE only 'port' states the version of Erlang used, maybe remove this constraint?
                        ImmutableMap.of(
                                "apt", "erlang-nox erlang-dev",
                                "port", "erlang@"+getErlangVersion()+"+ssl"),
                        "erlang"))
                .addAll(commandsToDownloadUrlsAs(urls, saveAs))
                .add(installExecutable("tar"))
                .add(format("tar xvzf %s",saveAs))
                .build();

        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(commands).execute();
    }

    @Override
    public void customize() {
        Networking.checkPortsValid(MutableMap.of("amqpPort", getAmqpPort()));
        newScript(CUSTOMIZING)
                .body.append(
                    format("cp -R %s/* .", getExpandedInstallDir())
                )
                .execute();
    }

    @Override
    public void launch() {
        newScript(MutableMap.of("usePidFile", false), LAUNCHING)
            .body.append(
                "nohup ./sbin/rabbitmq-server > console-out.log 2> console-err.log &",
                "for i in {1..10}\n" +
                    "do\n" +
                     "    grep 'broker running' console-out.log && exit\n" +
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
                .build();
    }
}
