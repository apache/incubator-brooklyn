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
package brooklyn.policy.os;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.brooklyn.policy.PolicySpec;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.entity.TestEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.policy.os.CreateUserPolicy;
import brooklyn.util.internal.ssh.SshTool;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class CreateUserPolicyTest extends BrooklynAppUnitTestSupport {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(CreateUserPolicyTest.class);

    public static class RecordingSshMachineLocation extends SshMachineLocation {
        private static final long serialVersionUID = 1641930081769106380L;
        
        public static List<List<String>> execScriptCalls = Lists.newArrayList();

        @Override 
        public int execScript(String summary, List<String> cmds) {
            execScriptCalls.add(cmds);
            return 0;
        }
        @Override 
        public int execScript(Map<String,?> props, String summaryForLogging, List<String> cmds) {
            execScriptCalls.add(cmds);
            return 0;
        }
        @Override 
        public int execScript(String summaryForLogging, List<String> cmds, Map<String,?> env) {
            execScriptCalls.add(cmds);
            return 0;
        }
        @Override 
        public int execScript(Map<String,?> props, String summaryForLogging, List<String> cmds, Map<String,?> env) {
            execScriptCalls.add(cmds);
            return 0;
        }
    }

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        RecordingSshMachineLocation.execScriptCalls.clear();
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        try {
            super.tearDown();
        } finally {
            RecordingSshMachineLocation.execScriptCalls.clear();
        }
    }
    
    @Test
    public void testCallsCreateUser() throws Exception {
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(RecordingSshMachineLocation.class)
                .configure(SshTool.PROP_USER, "myuser")
                .configure(SshTool.PROP_PASSWORD, "mypassword")
                .configure("address", "1.2.3.4")
                .configure(SshTool.PROP_PORT, 1234));
    
        String newUsername = "mynewuser";
        
        app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .policy(PolicySpec.create(CreateUserPolicy.class)
                        .configure(CreateUserPolicy.GRANT_SUDO, true)
                        .configure(CreateUserPolicy.VM_USERNAME, newUsername)));
        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
        app.start(ImmutableList.of(machine));
        
        String creds = EntityTestUtils.assertAttributeEventuallyNonNull(entity, CreateUserPolicy.VM_USER_CREDENTIALS);
        Pattern pattern = Pattern.compile("(.*) : (.*) @ (.*):(.*)");
        Matcher matcher = pattern.matcher(creds);
        assertTrue(matcher.matches());
        String username2 = matcher.group(1).trim();
        String password = matcher.group(2).trim();
        String hostname = matcher.group(3).trim();
        String port = matcher.group(4).trim();
        
        assertEquals(newUsername, username2);
        assertEquals(hostname, "1.2.3.4");
        assertEquals(password.length(), 12);
        assertEquals(port, "1234");

        boolean found = false;
        for (List<String> cmds : RecordingSshMachineLocation.execScriptCalls) {
            if (cmds.toString().contains("useradd")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "useradd not found in: "+RecordingSshMachineLocation.execScriptCalls);
    }
}
