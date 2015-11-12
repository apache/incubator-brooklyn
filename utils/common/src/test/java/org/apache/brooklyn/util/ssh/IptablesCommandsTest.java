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

import org.apache.brooklyn.util.net.Protocol;
import org.apache.brooklyn.util.ssh.IptablesCommands;
import org.apache.brooklyn.util.ssh.IptablesCommands.Chain;
import org.apache.brooklyn.util.ssh.IptablesCommands.Policy;
import org.testng.Assert;
import org.testng.annotations.Test;

public class IptablesCommandsTest {

    private static final String cleanUptptablesRules = "( if test \"$UID\" -eq 0; then ( /sbin/iptables -F ); else sudo -E -n -S -- /sbin/iptables -F; fi )";

    public static final String insertIptablesRule = "( if test \"$UID\" -eq 0; then ( /sbin/iptables -I INPUT -i eth0 -p tcp --dport 3306 -j ACCEPT ); "
            + "else sudo -E -n -S -- /sbin/iptables -I INPUT -i eth0 -p tcp --dport 3306 -j ACCEPT; fi )";
    private static final String appendIptablesRule = "( if test \"$UID\" -eq 0; then ( /sbin/iptables -A INPUT -i eth0 -p tcp --dport 3306 -j ACCEPT ); "
            + "else sudo -E -n -S -- /sbin/iptables -A INPUT -i eth0 -p tcp --dport 3306 -j ACCEPT; fi )";
    private static final String insertIptablesRuleAll = "( if test \"$UID\" -eq 0; then ( /sbin/iptables -I INPUT -p tcp --dport 3306 -j ACCEPT ); "
            + "else sudo -E -n -S -- /sbin/iptables -I INPUT -p tcp --dport 3306 -j ACCEPT; fi )";
    private static final String appendIptablesRuleAll = "( if test \"$UID\" -eq 0; then ( /sbin/iptables -A INPUT -p tcp --dport 3306 -j ACCEPT ); "
            + "else sudo -E -n -S -- /sbin/iptables -A INPUT -p tcp --dport 3306 -j ACCEPT; fi )";
    private static final String saveIptablesRules = "( ( if test \"$UID\" -eq 0; then ( service iptables save ); else sudo -E -n -S -- service iptables save; fi ) || " +
            "( ( { which zypper && { echo zypper exists, doing refresh && (( if test \"$UID\" -eq 0; then ( zypper --non-interactive --no-gpg-checks refresh ); else sudo -E -n -S -- zypper --non-interactive --no-gpg-checks refresh; fi ) || true) && ( if test \"$UID\" -eq 0; then ( zypper --non-interactive --no-gpg-checks install iptables-persistent ); else sudo -E -n -S -- zypper --non-interactive --no-gpg-checks install iptables-persistent; fi ) ; } ; } || " +
            "{ which apt-get && { echo apt-get exists, doing update && export DEBIAN_FRONTEND=noninteractive && (( if test \"$UID\" -eq 0; then ( apt-get update ); else sudo -E -n -S -- apt-get update; fi ) || true) && ( if test \"$UID\" -eq 0; then ( apt-get install -y --allow-unauthenticated iptables-persistent ); else sudo -E -n -S -- apt-get install -y --allow-unauthenticated iptables-persistent; fi ) ; } ; } || " +
            "{ which yum && { echo yum exists, doing update && (( if test \"$UID\" -eq 0; then ( yum check-update ); else sudo -E -n -S -- yum check-update; fi ) || true) && (( if test \"$UID\" -eq 0; then ( yum -y install epel-release ); else sudo -E -n -S -- yum -y install epel-release; fi ) || true) && ( if test \"$UID\" -eq 0; then ( yum -y --nogpgcheck install iptables-persistent ); else sudo -E -n -S -- yum -y --nogpgcheck install iptables-persistent; fi ) ; } ; } || " +
            "{ which brew && brew install iptables-persistent ; } || " +
            "{ which port && ( if test \"$UID\" -eq 0; then ( port install iptables-persistent ); else sudo -E -n -S -- port install iptables-persistent; fi ) ; } || " +
            "(( echo \"WARNING: no known/successful package manager to install iptables-persistent, may fail subsequently\" | tee /dev/stderr ) || true) ) && ( if test \"$UID\" -eq 0; then ( /etc/init.d/iptables-persistent save ); else sudo -E -n -S -- /etc/init.d/iptables-persistent save; fi ) ) )";

    @Test
    public void testCleanUpIptablesRules() {
        Assert.assertEquals(IptablesCommands.cleanUpIptablesRules(), cleanUptptablesRules);
    }

    @Test
    public void testInsertIptablesRules() {
        Assert.assertEquals(IptablesCommands.insertIptablesRule(Chain.INPUT, "eth0", Protocol.TCP, 3306, Policy.ACCEPT),
                insertIptablesRule);
    }

    @Test
    public void testAppendIptablesRules() {
        Assert.assertEquals(IptablesCommands.appendIptablesRule(Chain.INPUT, "eth0", Protocol.TCP, 3306, Policy.ACCEPT),
                appendIptablesRule);
    }

    @Test
    public void testInsertIptablesRulesForAllInterfaces() {
        Assert.assertEquals(IptablesCommands.insertIptablesRule(Chain.INPUT, Protocol.TCP, 3306, Policy.ACCEPT),
                insertIptablesRuleAll);
    }

    @Test
    public void testAppendIptablesRulesForAllInterfaces() {
        Assert.assertEquals(IptablesCommands.appendIptablesRule(Chain.INPUT, Protocol.TCP, 3306, Policy.ACCEPT),
                appendIptablesRuleAll);
    }

    @Test
    public void testSaveIptablesRules() {
        Assert.assertEquals(IptablesCommands.saveIptablesRules(), saveIptablesRules);
    }
}
