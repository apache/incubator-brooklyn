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
package brooklyn.util.ssh;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.net.Protocol;
import brooklyn.util.ssh.IptablesCommands.Chain;
import brooklyn.util.ssh.IptablesCommands.Policy;

public class IptablesCommandsFirewalldTest {
    private static final String addFirewalldRule = "( if test \"$UID\" -eq 0; then "
            + "( ( /usr/bin/firewall-cmd --direct --add-rule ipv4 filter INPUT 0  -p tcp --dport 3306 -j ACCEPT "
            + "&& /usr/bin/firewall-cmd --permanent --direct --add-rule ipv4 filter INPUT 0  -p tcp --dport 3306 -j ACCEPT ) ); "
            + "else echo \"( /usr/bin/firewall-cmd --direct --add-rule ipv4 filter INPUT 0  -p tcp --dport 3306 -j ACCEPT "
            + "&& /usr/bin/firewall-cmd --permanent --direct --add-rule ipv4 filter INPUT 0  -p tcp --dport 3306 -j ACCEPT )\" "
            + "| sudo -E -n -S -s -- bash ; fi )";

    private static final String firewalldService = "( if test \"$UID\" -eq 0; then ( ( { "
            + "which systemctl && systemctl status firewalld ; } || /usr/bin/systemctl status firewalld ) ); "
            + "else echo \"( { which systemctl && systemctl status firewalld ; } || "
            + "/usr/bin/systemctl status firewalld )\" | sudo -E -n -S -s -- bash ; fi )";

    private static final String firewalldServiceRestart = "( if test \"$UID\" -eq 0; then ( ( { "
            + "which systemctl && systemctl restart firewalld ; } || "
            + "/usr/bin/systemctl restart firewalld ) ); else echo \"( { "
            + "which systemctl && systemctl restart firewalld ; } || /usr/bin/systemctl restart firewalld )\" | "
            + "sudo -E -n -S -s -- bash ; fi )";

    private static final String firewalldServiceStart = "( if test \"$UID\" -eq 0; then ( ( { "
            + "which systemctl && systemctl start firewalld ; } "
            + "|| /usr/bin/systemctl start firewalld ) ); "
            + "else echo \"( { which systemctl && systemctl start firewalld ; } || "
            + "/usr/bin/systemctl start firewalld )\" | sudo -E -n -S -s -- bash ; fi )";

    private static final String firewalldServiceStatus = "( if test \"$UID\" -eq 0; then ( ( { "
            + "which systemctl && systemctl status firewalld ; "
            + "} || /usr/bin/systemctl status firewalld ) ); else echo \"( { "
            + "which systemctl && systemctl status firewalld ; } || "
            + "/usr/bin/systemctl status firewalld )\" | sudo -E -n -S -s -- bash ; fi )";

    private static final String firewalldServiceStop = "( if test \"$UID\" -eq 0; then ( ( { "
            + "which systemctl && systemctl stop firewalld ; } || /usr/bin/systemctl stop firewalld ) ); "
            + "else echo \"( { which systemctl && systemctl stop firewalld ; } || "
            + "/usr/bin/systemctl stop firewalld )\" | sudo -E -n -S -s -- bash ; fi )";

    private static final String firewalldServiceIsActive = "( if test \"$UID\" -eq 0; then ( ( { "
            + "which systemctl && systemctl is-active firewalld ; } || /usr/bin/systemctl is-active firewalld ) ); "
            + "else echo \"( { which systemctl && systemctl is-active firewalld ; } || /usr/bin/systemctl is-active firewalld )\" | "
            + "sudo -E -n -S -s -- bash ; fi )";

    @Test
    public void testAddFirewalldRule() {
        Assert.assertEquals(IptablesCommands.addFirewalldRule(Chain.INPUT,
                Protocol.TCP, 3306, Policy.ACCEPT), addFirewalldRule);
    }

    @Test
    public void testFirewalldService() {
        Assert.assertEquals(IptablesCommands.firewalldService("status"), firewalldService);
    }

    @Test
    public void testFirewalldServiceRestart() {
        Assert.assertEquals(IptablesCommands.firewalldServiceRestart(), firewalldServiceRestart);
    }

    @Test
    public void testFirewalldServiceStart() {
        Assert.assertEquals(IptablesCommands.firewalldServiceStart(), firewalldServiceStart);
    }

    @Test
    public void testFirewalldServiceStatus() {
        Assert.assertEquals(IptablesCommands.firewalldServiceStatus(), firewalldServiceStatus);
    }

    @Test
    public void testFirewalldServiceStop() {
        Assert.assertEquals(IptablesCommands.firewalldServiceStop(), firewalldServiceStop);
    }

    @Test
    public void testFirewalldServiceIsActive() {
        Assert.assertEquals(IptablesCommands.firewalldServiceIsActive(), firewalldServiceIsActive);
    }
}
