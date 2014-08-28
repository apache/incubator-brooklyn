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
package brooklyn.entity.messaging.activemq;

import static java.lang.String.format;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableMap;

public class ActiveMQSshDriver extends JavaSoftwareProcessSshDriver implements ActiveMQDriver {

    public ActiveMQSshDriver(ActiveMQBrokerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() { 
        return Os.mergePathsUnix(getRunDir(), "data/activemq.log");
    }

    @Override
    public Integer getOpenWirePort() { 
        return entity.getAttribute(ActiveMQBroker.OPEN_WIRE_PORT);
    }

    public String getMirrorUrl() {
        return entity.getConfig(ActiveMQBroker.MIRROR_URL);
    }

    protected String getTemplateConfigurationUrl() {
        return entity.getAttribute(ActiveMQBroker.TEMPLATE_CONFIGURATION_URL);
    }

    public String getPidFile() {
        return "data/activemq.pid";
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("apache-activemq-%s", getVersion()))));
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        List<String> commands = new LinkedList<String>();
        commands.addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs));
        commands.add(BashCommands.INSTALL_TAR);
        commands.add("tar xzfv "+saveAs);

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        Networking.checkPortsValid(ImmutableMap.of("jmxPort", getJmxPort(), "openWirePort", getOpenWirePort()));
        newScript(CUSTOMIZING)
                .body.append(
                        format("cp -R %s/{bin,conf,data,lib,webapps} .", getExpandedInstallDir()),
                        // Required in version 5.5.1 (at least), but not in version 5.7.0
                        "sed -i.bk 's/\\[-z \"$JAVA_HOME\"]/\\[ -z \"$JAVA_HOME\" ]/g' bin/activemq",
                        // Stop it writing to dev null on start
                        "sed -i.bk \"s/\\(ACTIVEMQ_HOME..bin.run.jar.*\\)>.dev.null/\\1/\" bin/activemq",
                        // Required if launching multiple AMQ's, prevent jetty port conflicts
                        "sed -i.bk 's/8161/"+getEntity().getAttribute(ActiveMQBroker.AMQ_JETTY_PORT)+"/g' conf/jetty.xml"
                        // TODO disable persistence (this should be a flag -- but it seems to have no effect, despite ):
                        // "sed -i.bk 's/broker /broker persistent=\"false\" /g' conf/activemq.xml",
                    )
                .execute();

        // Copy the configuration file across
        String destinationConfigFile = Os.mergePathsUnix(getRunDir(), "conf/activemq.xml");
        copyTemplate(getTemplateConfigurationUrl(), destinationConfigFile);
    }

    @Override
    public void launch() {
        // Using nohup, as recommended at http://activemq.apache.org/run-broker.html
        newScript(ImmutableMap.of(USE_PID_FILE, false), LAUNCHING)
                .body.append("nohup ./bin/activemq start > ./data/activemq-extra.log 2>&1 &")
                .execute();
    }
    
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
    public Map<String, String> getShellEnvironment() {
        return MutableMap.<String,String>builder()
                .putAll(super.getShellEnvironment())
                .put("ACTIVEMQ_HOME", getRunDir())
                .put("ACTIVEMQ_PIDFILE", getPidFile())
                .renameKey("JAVA_OPTS", "ACTIVEMQ_OPTS")
                .build();
    }
}
