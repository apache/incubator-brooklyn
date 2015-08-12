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
package brooklyn.location.basic;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Map;

import brooklyn.util.internal.ssh.SshTool;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.apache.brooklyn.management.ManagementContext;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.crypto.SecureKeys;
import brooklyn.util.internal.ssh.sshj.SshjTool;
import brooklyn.util.internal.ssh.sshj.SshjTool.SshjToolBuilder;

import com.google.common.base.Preconditions;

import static org.testng.Assert.assertEquals;

public class SshMachineLocationIntegrationTest {

    protected TestApplication app;
    protected ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        mgmt = LocalManagementContextForTests.builder(true)
            .useDefaultProperties()
            .build();
        app = TestApplication.Factory.newManagedInstanceForTests(mgmt);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
    }

    // Note: requires `named:localhost-passphrase` set up with a key whose passphrase is "localhost"
    // * create the key with:
    //      ssh-keygen -t rsa -N "brooklyn" -f ~/.ssh/id_rsa_passphrase
    //      ssh-copy-id localhost
    // * create brooklyn.properties, containing:
    //      brooklyn.location.named.localhost-passphrase=localhost
    //      brooklyn.location.named.localhost-passphrase.privateKeyFile=~/.ssh/id_rsa_passphrase
    //      brooklyn.location.named.localhost-passphrase.privateKeyPassphrase=brooklyn
    @Test(groups = "Integration")
    public void testExtractingConnectablePassphraselessKey() throws Exception {
        LocalhostMachineProvisioningLocation lhp = (LocalhostMachineProvisioningLocation) mgmt.getLocationRegistry().resolve("named:localhost-passphrase", true, null).orNull();
        Preconditions.checkNotNull(lhp, "This test requires a localhost named location called 'localhost-passphrase' (which should have a passphrase set)");
        SshMachineLocation sm = lhp.obtain();
        
        SshjToolBuilder builder = SshjTool.builder().host(sm.getAddress().getHostName()).user(sm.getUser());
        
        KeyPair data = sm.findKeyPair();
        if (data!=null) builder.privateKeyData(SecureKeys.toPem(data));
        String password = sm.findPassword();
        if (password!=null) builder.password(password);
        SshjTool tool = builder.build();
        tool.connect();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int result = tool.execCommands(MutableMap.<String,Object>of("out", out), Arrays.asList("date"));
        Assert.assertTrue(out.toString().contains(" 20"), "out="+out);
        assertEquals(result, 0);
    }

    @Test(groups = "Integration")
    public void testExecScriptScriptDirFlagIsRespected() throws Exception {
        // For explanation of (some of) the magic behind this command, see http://stackoverflow.com/a/229606/68898
        final String command = "if [[ \"$0\" == \"/var/tmp/\"* ]]; then true; else false; fi";

        LocalhostMachineProvisioningLocation lhp = (LocalhostMachineProvisioningLocation) mgmt.getLocationRegistry().resolve("localhost", true, null).orNull();
        SshMachineLocation sm = lhp.obtain();

        Map<String, Object> props = ImmutableMap.<String, Object>builder()
                .put(SshTool.PROP_SCRIPT_DIR.getName(), "/var/tmp")
                .build();
        int rc = sm.execScript(props, "Test script directory execution", ImmutableList.of(command));
        assertEquals(rc, 0);
    }

    @Test(groups = "Integration")
    public void testLocationScriptDirConfigIsRespected() throws Exception {
        // For explanation of (some of) the magic behind this command, see http://stackoverflow.com/a/229606/68898
        final String command = "if [[ \"$0\" == \"/var/tmp/\"* ]]; then true; else false; fi";

        Map<String, Object> locationConfig = ImmutableMap.<String, Object>builder()
                .put(SshMachineLocation.SCRIPT_DIR.getName(), "/var/tmp")
                .build();

        LocalhostMachineProvisioningLocation lhp = (LocalhostMachineProvisioningLocation) mgmt.getLocationRegistry().resolve("localhost", locationConfig);
        SshMachineLocation sm = lhp.obtain();

        int rc = sm.execScript("Test script directory execution", ImmutableList.of(command));
        assertEquals(rc, 0);
    }
    
    @Test(groups = "Integration")
    public void testMissingLocationScriptDirIsAlsoOkay() throws Exception {
        final String command = "echo hello";

        Map<String, Object> locationConfig = ImmutableMap.<String, Object>builder()
//                .put(SshMachineLocation.SCRIPT_DIR.getName(), "/var/tmp")
                .build();

        LocalhostMachineProvisioningLocation lhp = (LocalhostMachineProvisioningLocation) mgmt.getLocationRegistry().resolve("localhost", locationConfig);
        SshMachineLocation sm = lhp.obtain();

        int rc = sm.execScript("Test script directory execution", ImmutableList.of(command));
        assertEquals(rc, 0);
    }
}
