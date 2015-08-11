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
package org.apache.brooklyn.entity.network.bind;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.net.Protocol;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.ssh.IptablesCommands;
import brooklyn.util.ssh.IptablesCommands.Chain;
import brooklyn.util.ssh.IptablesCommands.Policy;
import brooklyn.util.text.Strings;

public class BindDnsServerSshDriver extends AbstractSoftwareProcessSshDriver implements BindDnsServerDriver {

    private static final Logger LOG = LoggerFactory.getLogger(BindDnsServerSshDriver.class);
    private volatile BindOsSupport osSupport;
    private final Object osSupportMutex = new Object();

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
                .add(BashCommands.installPackage(MutableMap.of(
                        "yum", "bind", "apt", "bind9"), "bind"))
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

        List<String> commands = Lists.newArrayList(
                BashCommands.sudo("mkdir -p " + getDataDirectory() + " " + getDynamicDirectory() + " " + getOsSupport().getConfigDirectory()),
                BashCommands.sudo("chown -R bind:bind " + getDataDirectory() + " " + getDynamicDirectory()),
                // TODO determine name of ethernet interface if not eth0?
                IptablesCommands.insertIptablesRule(Chain.INPUT, "eth0", Protocol.UDP, dnsPort, Policy.ACCEPT),
                IptablesCommands.insertIptablesRule(Chain.INPUT, "eth0", Protocol.TCP, dnsPort, Policy.ACCEPT),
                // TODO Iptables is not a service on Ubuntu
                BashCommands.sudo("service iptables save"),
                BashCommands.sudo("service iptables restart"));
        if (getEntity().getConfig(BindDnsServer.UPDATE_ROOT_ZONES_FILE)) {
            commands.add("wget --user=ftp --password=ftp ftp://ftp.rs.internic.net/domain/db.cache " +
                    "-O " + getOsSupport().getRootZonesFile());
        }
        newScript(CUSTOMIZING)
                .body.append(commands)
                // fails if iptables is not a service, e.g. on ubuntu
                //.failOnNonZeroResultCode()
                .execute();

        copyAsRoot("classpath://org/apache/brooklyn/entity/network/bind/rfc1912.zone", getRfc1912ZonesFile());
        copyAsRoot("classpath://org/apache/brooklyn/entity/network/bind/named.localhost", Os.mergePathsUnix(getOsSupport().getConfigDirectory(), "named.localhost"));
        copyAsRoot("classpath://org/apache/brooklyn/entity/network/bind/named.loopback", Os.mergePathsUnix(getOsSupport().getConfigDirectory(), "named.loopback"));
        copyAsRoot("classpath://org/apache/brooklyn/entity/network/bind/named.empty", Os.mergePathsUnix(getOsSupport().getConfigDirectory(), "named.empty"));

        newScript("Checking BIND configuration")
                .body.append(BashCommands.sudo("named-checkconf"))
                .failOnNonZeroResultCode()
                .execute();
    }

    @Override
    public void launch() {
        newScript(MutableMap.of("usePidFile", false), LAUNCHING)
                .body.append(BashCommands.sudo("service " + getOsSupport().getServiceName() + " start"))
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", false), CHECK_RUNNING)
                .body.append(BashCommands.sudo("service " + getOsSupport().getServiceName() + " status"))
                .execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", false), STOPPING)
                .body.append(BashCommands.sudo("service " + getOsSupport().getServiceName() + " stop"))
                .execute();
    }

    @Override
    public void updateBindConfiguration() {
        LOG.debug("Updating bind configuration at " + getMachine());
        copyAsRoot(entity.getConfig(BindDnsServer.NAMED_CONF_TEMPLATE), getOsSupport().getRootConfigFile());
        copyAsRoot(entity.getConfig(BindDnsServer.DOMAIN_ZONE_FILE_TEMPLATE), getDomainZoneFile());
        copyAsRoot(entity.getConfig(BindDnsServer.REVERSE_ZONE_FILE_TEMPLATE), getReverseZoneFile());
        int result = getMachine().execScript("restart bind",
                ImmutableList.of(BashCommands.sudo("service " + getOsSupport().getServiceName() + " restart")));
        LOG.info("Updated named configuration and zone file for '{}' on {} (exit code {}).",
                new Object[]{entity.getConfig(BindDnsServer.DOMAIN_NAME), entity, result});
    }

    private void copyAsRoot(String template, String destination) {
        String content = processTemplate(template);
        String temp = "/tmp/template-" + Strings.makeRandomId(6);
        getMachine().copyTo(new ByteArrayInputStream(content.getBytes()), temp);
        getMachine().execScript("copying file", ImmutableList.of(BashCommands.sudo(String.format("mv %s %s", temp, destination))));
    }

    /** @return The location on the server of the domain zone file */
    public String getDomainZoneFile() {
        return Os.mergePaths(getOsSupport().getConfigDirectory(), "domain.zone");
    }

    /** @return The location on the server of the reverse zone file */
    public String getReverseZoneFile() {
        return Os.mergePaths(getOsSupport().getConfigDirectory(), "reverse.zone");
    }

    public String getDataDirectory() {
        return Os.mergePaths(getOsSupport().getWorkingDirectory(), "data");
    }

    public String getDynamicDirectory() {
        return Os.mergePaths(getOsSupport().getWorkingDirectory(), "dynamic");
    }

    public String getRfc1912ZonesFile() {
        return Os.mergePaths(getOsSupport().getConfigDirectory(), "rfc1912.zone");
    }

    public BindOsSupport getOsSupport() {
        BindOsSupport result = osSupport;
        if (result == null) {
            synchronized (osSupportMutex) {
                result = osSupport;
                if (result == null) {
                    boolean yumExists = newScript("testing for yum")
                            .body.append(BashCommands.requireExecutable("yum"))
                            .execute() == 0;
                    osSupport = result = yumExists ? BindOsSupport.forRhel() : BindOsSupport.forDebian();
                }
            }
        }
        return result;
    }
}
