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
package brooklyn.entity.network.bind;

import java.util.List;
import java.util.Map;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.ssh.IptablesCommands;
import brooklyn.util.ssh.IptablesCommands.Chain;
import brooklyn.util.ssh.IptablesCommands.Policy;
import brooklyn.util.ssh.IptablesCommands.Protocol;

import com.google.common.collect.ImmutableList;

public class BindDnsServerSshDriver extends AbstractSoftwareProcessSshDriver implements BindDnsServerDriver {

    public BindDnsServerSshDriver(BindDnsServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public BindDnsServerImpl getEntity() {
        return (BindDnsServerImpl) super.getEntity();
    }

    @Override
    public void install() {
        List<String> commands = ImmutableList.<String>builder()
                .add(BashCommands.installPackage(MutableMap.of("yum", "bind"), "bind"))
                .add(BashCommands.ok("which setenforce && " + BashCommands.sudo("setenforce 0")))
                .build();

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        Integer dnsPort = getEntity().getDnsPort();
        Map<String, Object> ports = MutableMap.<String, Object>of("dnsPort", dnsPort);
        Networking.checkPortsValid(ports);
        newScript(CUSTOMIZING)
                .body.append(
                        // TODO determine name of ethernet interface if not eth0?
                        IptablesCommands.insertIptablesRule(Chain.INPUT, "eth0", Protocol.UDP, dnsPort, Policy.ACCEPT),
                        IptablesCommands.insertIptablesRule(Chain.INPUT, "eth0", Protocol.TCP, dnsPort, Policy.ACCEPT),
                        BashCommands.sudo("service iptables save"),
                        BashCommands.sudo("service iptables restart")
                ).execute();
    }

    @Override
    public void launch() {
        newScript(MutableMap.of("usePidFile", false), LAUNCHING).
        body.append(BashCommands.sudo("service named start")).execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", false), CHECK_RUNNING)
                    .body.append(BashCommands.sudo("service named status")).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", false), STOPPING)
                .body.append(BashCommands.sudo("service named stop")).execute();
    }

}
