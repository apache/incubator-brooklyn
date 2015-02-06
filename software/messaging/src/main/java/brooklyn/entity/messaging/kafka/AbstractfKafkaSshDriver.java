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
package brooklyn.entity.messaging.kafka;

import static java.lang.String.format;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;

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

    protected abstract String getProcessIdentifier();

    @Override
    protected String getLogFileLocation() { return Os.mergePaths(getRunDir(), "console.out"); }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("kafka-%s-src", getVersion()))));
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
        commands.add("./sbt update");
        commands.add("./sbt package");
        if (isV08()) {
            // target not known in v0.7.x but required in v0.8.0-beta1
            commands.add("./sbt assembly-package-dependency");
        }

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    protected boolean isV08() {
        String v = getEntity().getConfig(Kafka.SUGGESTED_VERSION);
        if (v.startsWith("0.7.")) return false;
        return true;
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
