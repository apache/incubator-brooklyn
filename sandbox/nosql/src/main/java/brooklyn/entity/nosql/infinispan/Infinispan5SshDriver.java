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
package brooklyn.entity.nosql.infinispan;

import static java.lang.String.format;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableList;

/**
 * Start a {@link TomcatServer} in a {@link Location} accessible over ssh.
 */
public class Infinispan5SshDriver extends JavaSoftwareProcessSshDriver implements Infinispan5Driver {

    public Infinispan5SshDriver(Infinispan5Server entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() {
        throw new UnsupportedOperationException("Work in progress");
    }

    protected String getProtocol() {
        return entity.getAttribute(Infinispan5Server.PROTOCOL);
    }

    protected Integer getPort() {
        return entity.getAttribute(Infinispan5Server.PORT);
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        List<String> commands = ImmutableList.<String>builder()
                .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
                .add(BashCommands.INSTALL_ZIP)
                .add("unzip " + saveAs)
                .build();

        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(commands).execute();
    }

    @Override
    public void customize() {
        // TODO create and reference a conf.xml? And start with --cache_config <path>
        Map ports = MutableMap.of("port", getPort(), "jmxPort", getJmxPort());
        Networking.checkPortsValid(ports);

        newScript(CUSTOMIZING)
                .body.append()
                .execute();
    }

    @Override
    public void launch() {
        // FIXME Do we want to redirect stdout/stderr: >> %s/console 2>&1 </dev/null &", getRunDir())
        newScript(MutableMap.of("usePidFile", true), LAUNCHING).
                body.append(
                        format("%s/bin/startServer.sh --protocol %s "
                                +(getPort() != null ? " --port %s" : "")+" &", 
                                getExpandedInstallDir(), getProtocol(), getPort()))
                .execute();
    }


    @Override
    public boolean isRunning() {
        Map flags = MutableMap.of("usePidFile", true);
        return newScript(flags, CHECK_RUNNING).execute() == 0;
    }
    
    @Override
    public void stop() {
        Map flags = MutableMap.of("usePidFile", true);
        newScript(flags, STOPPING).execute();
    }

    @Override
    public void kill() {
        Map flags = MutableMap.of("usePidFile", true);
        newScript(flags, KILLING).execute();
    }

    @Override
    protected List<String> getCustomJavaConfigOptions() {
        List<String> options = new LinkedList<String>();
        options.addAll(super.getCustomJavaConfigOptions());
        options.add("-Xms200m");
        options.add("-Xmx800m");
        options.add("-XX:MaxPermSize=400m");
        return options;
    }
}
