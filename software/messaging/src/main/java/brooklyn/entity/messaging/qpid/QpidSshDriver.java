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
package brooklyn.entity.messaging.qpid;

import static java.lang.String.format;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableMap;

public class QpidSshDriver extends JavaSoftwareProcessSshDriver implements QpidDriver{

    private static final Logger log = LoggerFactory.getLogger(QpidSshDriver.class);

    public QpidSshDriver(QpidBrokerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() { return Os.mergePaths(getRunDir(), "log", "qpid.log"); }

    @Override
    public Integer getAmqpPort() { return entity.getAttribute(QpidBroker.AMQP_PORT); }

    @Override
    public String getAmqpVersion() { return entity.getAttribute(QpidBroker.AMQP_VERSION); }

    public Integer getHttpManagementPort() { return entity.getAttribute(QpidBroker.HTTP_MANAGEMENT_PORT); }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("qpid-broker-%s", getVersion()))));
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        List<String> commands = new LinkedList<String>();
        commands.addAll( BashCommands.commandsToDownloadUrlsAs(urls, saveAs));
        commands.add(BashCommands.INSTALL_TAR);
        commands.add("tar xzfv "+saveAs);

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        Networking.checkPortsValid(MutableMap.of("jmxPort", getJmxPort(), "amqpPort", getAmqpPort()));
        newScript(CUSTOMIZING)
                .body.append(
                        format("cp -R %s/{bin,etc,lib} .", getExpandedInstallDir()),
                        "mkdir lib/opt"
                    )
                .execute();

        Map runtimeFiles = entity.getConfig(QpidBroker.RUNTIME_FILES);
        copyResources(runtimeFiles);

        Map runtimeTemplates = entity.getConfig(QpidBroker.RUNTIME_TEMPLATES);
        copyTemplates(runtimeTemplates);
    }

    @Override
    public void launch() {
        newScript(ImmutableMap.of(USE_PID_FILE, false), LAUNCHING)
                .body.append("nohup ./bin/qpid-server -b '*' > qpid-server-launch.log 2>&1 &")
                .execute();
    }

    public String getPidFile() { return "qpid-server.pid"; }
    
    @Override
    public boolean isRunning() {
        return newScript(ImmutableMap.of(USE_PID_FILE, getPidFile()), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(ImmutableMap.of(USE_PID_FILE, getPidFile()), STOPPING).execute();
    }

    @Override
    public void kill() {
        newScript(ImmutableMap.of(USE_PID_FILE, getPidFile()), KILLING).execute();
    }

    @Override
    public Map<String, Object> getCustomJavaSystemProperties() {
        return MutableMap.<String, Object>builder()
                .putAll(super.getCustomJavaSystemProperties())
                .put("connector.port", getAmqpPort())
                .put("management.enabled", "true")
                .put("management.jmxport.registryServer", getRmiRegistryPort())
                .put("management.jmxport.connectorServer", getJmxPort())
                .put("management.http.enabled", Boolean.toString(getHttpManagementPort() != null))
                .putIfNotNull("management.http.port", getHttpManagementPort())
                .build();
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        return MutableMap.<String, String>builder()
                .putAll(super.getShellEnvironment())
                .put("QPID_HOME", getRunDir())
                .put("QPID_WORK", getRunDir())
                .renameKey("JAVA_OPTS", "QPID_OPTS")
                .build();
    }
}
