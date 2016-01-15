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
package org.apache.brooklyn.entity.messaging.kafka;

import static java.lang.String.format;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;

public abstract class AbstractfKafkaSshDriver extends JavaSoftwareProcessSshDriver {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(KafkaZooKeeperSshDriver.class);

    public AbstractfKafkaSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected abstract Map<String, Integer> getPortMap();

    protected abstract ConfigKey<String> getConfigTemplateKey();

    protected abstract String getConfigFileName();

    protected abstract String getLaunchScriptName();

    protected abstract String getTopicsScriptName();

    protected abstract String getProcessIdentifier();

    @Override
    protected String getLogFileLocation() { return Os.mergePaths(getRunDir(), "console.out"); }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("kafka_%s", getVersion()))));
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        List<String> commands = new LinkedList<String>();
        commands.addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs));
        commands.add(BashCommands.INSTALL_TAR);
        commands.add("tar xzfv "+saveAs);
        commands.add("cd "+getExpandedInstallDir());

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        Networking.checkPortsValid(getPortMap());

        newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(format("cp -R %s/* %s", getExpandedInstallDir(), getRunDir()))
                .execute();

        String config = entity.getConfig(getConfigTemplateKey());
        copyTemplate(config, getConfigFileName());
    }

    @Override
    public void launch() {
        newScript(MutableMap.of(USE_PID_FILE, getPidFile()), LAUNCHING)
                .failOnNonZeroResultCode()
                .body.append(String.format("nohup ./bin/%s ./%s > console.out 2>&1 &", getLaunchScriptName(), getConfigFileName()))
                .execute();
    }

    public String getPidFile() { return Os.mergePathsUnix(getRunDir(), "kafka.pid"); }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, getPidFile()), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, false), STOPPING)
                .body.append(String.format("ps ax | grep %s | awk '{print $1}' | xargs kill", getProcessIdentifier()))
                .body.append(String.format("ps ax | grep %s | awk '{print $1}' | xargs kill -9", getProcessIdentifier()))
                .execute();
    }

    /**
     * Use RMI agent to provide JMX.
     */
    @Override
    public Map<String, String> getShellEnvironment() {
        return MutableMap.<String, String>builder()
                .putAll(super.getShellEnvironment())
                .renameKey("JAVA_OPTS", "KAFKA_JMX_OPTS")
                .build();
    }

}
