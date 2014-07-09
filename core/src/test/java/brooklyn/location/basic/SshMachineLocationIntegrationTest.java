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

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.crypto.SecureKeys;
import brooklyn.util.internal.ssh.sshj.SshjTool;
import brooklyn.util.internal.ssh.sshj.SshjTool.SshjToolBuilder;

import com.google.common.base.Preconditions;

public class SshMachineLocationIntegrationTest {

    protected TestApplication app;
    protected ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        mgmt = app.getManagementContext();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
    }

    // Note: requires `named:localhost-passphrase` set up with a key whose passphrase is "localhost"    
    @Test(groups = "Integration")
    public void testExtractingConnectablePassphraselessKey() throws Exception {
        LocalhostMachineProvisioningLocation lhp = (LocalhostMachineProvisioningLocation) mgmt.getLocationRegistry().resolveIfPossible("named:localhost-passphrase");
        Preconditions.checkNotNull(lhp, "This test requires a localhost named location called 'localhost-passphrase' (which should have a passphrase set)");
        SshMachineLocation sm = lhp.obtain();
        
        SshjToolBuilder builder = SshjTool.builder().host(sm.getAddress().getHostName()).user(sm.getUser());
        
        KeyPair data = sm.findKeyPair();
        if (data!=null) builder.privateKeyData(SecureKeys.stringPem(data));
        String password = sm.findPassword();
        if (password!=null) builder.password(password);
        SshjTool tool = builder.build();
        tool.connect();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int result = tool.execCommands(MutableMap.<String,Object>of("out", out), Arrays.asList("date"));
        Assert.assertTrue(out.toString().contains(" 20"), "out="+out);
        Assert.assertEquals(result, 0);
    }

}
