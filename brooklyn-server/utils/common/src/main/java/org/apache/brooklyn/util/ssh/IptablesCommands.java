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
package org.apache.brooklyn.util.ssh;

import static org.apache.brooklyn.util.ssh.BashCommands.alternatives;
import static org.apache.brooklyn.util.ssh.BashCommands.chain;
import static org.apache.brooklyn.util.ssh.BashCommands.installPackage;
import static org.apache.brooklyn.util.ssh.BashCommands.sudo;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;

public class IptablesCommands {

    public enum Chain {
        INPUT, FORWARD, OUTPUT
    }

    public enum Policy {
        ACCEPT, REJECT, DROP, LOG
    }

    /*** @deprecated since 0.7; use {@link org.apache.brooklyn.util.net.Protocol} */
    @Deprecated
    public enum Protocol {
        TCP("tcp"), UDP("udp"), ALL("all");

        final String protocol;

        private Protocol(String protocol) {
            this.protocol = protocol;
        }

        @Override
        public String toString() {
            return protocol;
        }

        org.apache.brooklyn.util.net.Protocol convert() {
            switch (this) {
                case TCP: return org.apache.brooklyn.util.net.Protocol.TCP;
                case UDP: return org.apache.brooklyn.util.net.Protocol.UDP;
                case ALL: return org.apache.brooklyn.util.net.Protocol.ALL;
                default: throw new IllegalStateException("Unexpected protocol "+this);
            }
        }
    }

    @Beta // implementation not portable across distros
    public static String iptablesService(String cmd) {
        return sudo(alternatives(
                BashCommands.ifExecutableElse1("service", "service iptables " + cmd),
                "/sbin/service iptables " + cmd));
    }

    @Beta // implementation not portable across distros
    public static String iptablesServiceStop() {
        return iptablesService("stop");
    }

    @Beta // implementation not portable across distros
    public static String iptablesServiceStart() {
        return iptablesService("start");
    }

    @Beta // implementation not portable across distros
    public static String iptablesServiceRestart() {
        return iptablesService("restart");
    }

    @Beta // implementation not portable across distros
    public static String iptablesServiceStatus() {
        return iptablesService("status");
    }

    @Beta // implementation not portable across distros
    public static String firewalldService(String cmd) {
        return sudo(alternatives(
                BashCommands.ifExecutableElse1("systemctl", "systemctl " + cmd + " firewalld"),
                "/usr/bin/systemctl " + cmd + " firewalld"));
    }

    @Beta // implementation not portable across distros
    public static String firewalldServiceStop() {
        return firewalldService("stop");
    }

    @Beta // implementation not portable across distros
    public static String firewalldServiceStart() {
        return firewalldService("start");
    }

    @Beta // implementation not portable across distros
    public static String firewalldServiceRestart() {
        return firewalldService("restart");
    }

    @Beta // implementation not portable across distros
    public static String firewalldServiceStatus() {
        return firewalldService("status");
    }

    @Beta // implementation not portable across distros
    public static String firewalldServiceIsActive() {
        return firewalldService("is-active");
    }

    /**
     * Returns the command that saves iptables rules on file.
     *
     * @return Returns the command that saves iptables rules on file.
     *
     */
    public static String saveIptablesRules() {
        return alternatives(sudo("service iptables save"),
                            chain(installPackage("iptables-persistent"), sudo("/etc/init.d/iptables-persistent save")));
    }

    /**
     * Returns the command that cleans up iptables rules.
     *
     * @return Returns the command that cleans up iptables rules.
     */
    public static String cleanUpIptablesRules() {
       return sudo("/sbin/iptables -F");
    }

    /**
     * Returns the iptables rules.
     *
     * @return Returns the command that list all the iptables rules.
     */
    public static String listIptablesRule() {
       return sudo("/sbin/iptables -L -v -n");
    }

    /**
     * Returns the command that inserts a rule on top of the iptables' rules to all interfaces.
     *
     * @return Returns the command that inserts a rule on top of the iptables'
     *         rules.
     */
    public static String insertIptablesRule(Chain chain, org.apache.brooklyn.util.net.Protocol protocol, int port, Policy policy) {
        return addIptablesRule("-I", chain, Optional.<String> absent(), protocol, port, policy);
    }

    /** @deprecated since 0.7.0; use {@link #insertIptablesRule(Chain, org.apache.brooklyn.util.net.Protocol, int, Policy)} */
    @Deprecated
    public static String insertIptablesRule(Chain chain, Protocol protocol, int port, Policy policy) {
        return insertIptablesRule(chain, protocol.convert(), port, policy);
    }

    /**
     * Returns the command that inserts a rule on top of the iptables' rules.
     *
     * @return Returns the command that inserts a rule on top of the iptables'
     *         rules.
     */
    public static String insertIptablesRule(Chain chain, String networkInterface, org.apache.brooklyn.util.net.Protocol protocol, int port, Policy policy) {
        return addIptablesRule("-I", chain, Optional.of(networkInterface), protocol, port, policy);
    }

    /** @deprecated since 0.7.0; use {@link #insertIptablesRule(Chain, String, org.apache.brooklyn.util.net.Protocol, int, Policy)} */
    @Deprecated
    public static String insertIptablesRule(Chain chain, String networkInterface, Protocol protocol, int port, Policy policy) {
        return insertIptablesRule(chain, networkInterface, protocol.convert(), port, policy);
    }

    /**
     * Returns the command that appends a rule to iptables to all interfaces.
     *
     * @return Returns the command that appends a rule to iptables.
     */
    public static String appendIptablesRule(Chain chain, org.apache.brooklyn.util.net.Protocol protocol, int port, Policy policy) {
        return addIptablesRule("-A", chain, Optional.<String> absent(), protocol, port, policy);
    }

    /** @deprecated since 0.7.0; use {@link #appendIptablesRule(Chain, org.apache.brooklyn.util.net.Protocol, int, Policy)} */
    @Deprecated
    public static String appendIptablesRule(Chain chain, Protocol protocol, int port, Policy policy) {
        return appendIptablesRule(chain, protocol.convert(), port, policy);
    }

    /**
     * Returns the command that appends a rule to iptables.
     *
     * @return Returns the command that appends a rule to iptables.
     */
    public static String appendIptablesRule(Chain chain, String networkInterface, org.apache.brooklyn.util.net.Protocol protocol, int port, Policy policy) {
        return addIptablesRule("-A", chain, Optional.of(networkInterface), protocol, port, policy);
    }

    /** @deprecated since 0.7.0; use {@link #appendIptablesRule(Chain, String, org.apache.brooklyn.util.net.Protocol, int, Policy)} */
    @Deprecated
    public static String appendIptablesRule(Chain chain, String networkInterface, Protocol protocol, int port, Policy policy) {
        return appendIptablesRule(chain, networkInterface, protocol.convert(), port, policy);
    }

    /**
     * Returns the command that creates a rule to iptables.
     *
     * @return Returns the command that creates a rule for iptables.
     */
    public static String addIptablesRule(String direction, Chain chain, Optional<String> networkInterface, org.apache.brooklyn.util.net.Protocol protocol, int port, Policy policy) {
        String addIptablesRule;
        if(networkInterface.isPresent()) {
           addIptablesRule = String.format("/sbin/iptables %s %s -i %s -p %s --dport %d -j %s", direction, chain, networkInterface.get(), protocol, port, policy);
        } else {
           addIptablesRule = String.format("/sbin/iptables %s %s -p %s --dport %d -j %s", direction, chain, protocol, port, policy);
        }
        return sudo(addIptablesRule);
    }

    /** @deprecated since 0.7.0; use {@link #addIptablesRule(String, Chain, Optional, org.apache.brooklyn.util.net.Protocol, int, Policy)} */
    @Deprecated
    public static String addIptablesRule(String direction, Chain chain, Optional<String> networkInterface, Protocol protocol, int port, Policy policy) {
        return addIptablesRule(direction, chain, networkInterface, protocol.convert(), port, policy);
    }

    /**
     * Returns the command that adds firewalld direct rule.
     *
     * @return Returns the command that adds firewalld direct rule.
     */
    public static String addFirewalldRule(Chain chain, org.apache.brooklyn.util.net.Protocol protocol, int port, Policy policy) {
        return addFirewalldRule(chain, Optional.<String>absent(), protocol, port, policy);
    }
    
    /**
     * Returns the command that adds firewalld direct rule.
     *
     * @return Returns the command that adds firewalld direct rule.
     */
    public static String addFirewalldRule(Chain chain, Optional<String> networkInterface, org.apache.brooklyn.util.net.Protocol protocol, int port, Policy policy) {
        String command = new String("/usr/bin/firewall-cmd");
        String commandPermanent = new String("/usr/bin/firewall-cmd --permanent");
        
        String interfaceParameter = String.format("%s", networkInterface.isPresent() ? " -i " + networkInterface.get() : "");
        
        String commandParameters = String.format(" --direct --add-rule ipv4 filter %s 0 %s -p %s --dport %d -j %s", 
                                                                chain, interfaceParameter,  protocol, port, policy);
        
        return sudo(chain(command + commandParameters, commandPermanent + commandParameters));
    }
}
