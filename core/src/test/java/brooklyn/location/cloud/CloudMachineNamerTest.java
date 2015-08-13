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
package brooklyn.location.cloud;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.test.entity.TestApplication;
import org.apache.brooklyn.test.entity.TestEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.location.cloud.names.AbstractCloudMachineNamer;
import brooklyn.location.cloud.names.BasicCloudMachineNamer;
import brooklyn.location.cloud.names.CloudMachineNamer;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;
public class CloudMachineNamerTest {

    private static final Logger log = LoggerFactory.getLogger(CloudMachineNamerTest.class);
    
    private TestApplication app;
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testGenerateGroupIdWithEntity() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("TistApp"), LocalManagementContextForTests.newInstance());
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("TestEnt"));

        ConfigBag cfg = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);

        String result = new BasicCloudMachineNamer().generateNewGroupId(cfg);

        log.info("test entity child group id gives: "+result);
        // e.g. brooklyn-alex-tistapp-uube-testent-xisg-rwad
        Assert.assertTrue(result.length() <= 60);

        String user = Strings.maxlen(System.getProperty("user.name"), 4).toLowerCase();
        Assert.assertTrue(result.indexOf(user) >= 0);
        Assert.assertTrue(result.indexOf("-tistapp-") >= 0);
        Assert.assertTrue(result.indexOf("-testent-") >= 0);
        Assert.assertTrue(result.indexOf("-"+Strings.maxlen(app.getId(), 4).toLowerCase()) >= 0);
        Assert.assertTrue(result.indexOf("-"+Strings.maxlen(child.getId(), 4).toLowerCase()) >= 0);
    }
    
    @Test
    public void testGenerateNewMachineName() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("TistApp"), LocalManagementContextForTests.newInstance());
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("TestEnt"));

        ConfigBag cfg = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);
        BasicCloudMachineNamer namer = new BasicCloudMachineNamer();
        
        String result = namer.generateNewMachineUniqueName(cfg);
        Assert.assertTrue(result.length() <= namer.getMaxNameLength(cfg));
        String user = Strings.maxlen(System.getProperty("user.name"), 4).toLowerCase();
        Assert.assertTrue(result.indexOf(user) >= 0);
        Assert.assertTrue(result.indexOf("-tistapp-") >= 0);
        Assert.assertTrue(result.indexOf("-testent-") >= 0);
        Assert.assertTrue(result.indexOf("-"+Strings.maxlen(app.getId(), 4).toLowerCase()) >= 0);
        Assert.assertTrue(result.indexOf("-"+Strings.maxlen(child.getId(), 4).toLowerCase()) >= 0);
    }
    
    @Test
    public void testGenerateNewMachineUniqueNameFromGroupId() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("TistApp"), LocalManagementContextForTests.newInstance());
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("TestEnt"));

        ConfigBag cfg = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);
        CloudMachineNamer namer = new BasicCloudMachineNamer();
        
        String groupId = namer.generateNewGroupId(cfg);
        String result = namer.generateNewMachineUniqueNameFromGroupId(cfg, groupId);
        Assert.assertTrue(result.startsWith(groupId));
        Assert.assertTrue(result.length() == groupId.length() + 5);
    }
    
    @Test
    public void testLengthMaxPermittedForMachineName() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("TistApp"), LocalManagementContextForTests.newInstance());
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("TestEnt"));
        
        ConfigBag cfg = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);
        BasicCloudMachineNamer namer = new BasicCloudMachineNamer();
        namer.setDefaultMachineNameMaxLength(10);
        String result = namer.generateNewMachineUniqueName(cfg);
        Assert.assertEquals(result.length(), 10);
    }
    
    @Test
    public void testLengthReservedForNameInGroup() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("TistApp"), LocalManagementContextForTests.newInstance());
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("TestEnt"));
        
        ConfigBag cfg = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);
        BasicCloudMachineNamer namer = new BasicCloudMachineNamer();
        namer.setDefaultMachineNameMaxLength(20);
        namer.setDefaultMachineNameSeparatorAndSaltLength(":I", 5);
        String groupId = namer.generateNewGroupId(cfg);
        Assert.assertEquals(13, groupId.length(), "groupId="+groupId);
        String machineId = namer.generateNewMachineUniqueNameFromGroupId(cfg, groupId);
        Assert.assertEquals(20, machineId.length(), "machineId="+machineId);
        // separator is not sanitized -- caller should know what they are doing there!
        Assert.assertTrue(machineId.startsWith(groupId+"-i"), "machineId="+machineId);
    }

    @Test
    public void testSanitizesNewMachineName() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("T_%$()\r\n\t[]*.!App"), LocalManagementContextForTests.newInstance());
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("ent"));

        ConfigBag cfg = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);
        CloudMachineNamer namer = new BasicCloudMachineNamer();
        
        String result = namer.generateNewMachineUniqueName(cfg);
        assertTrue(result.indexOf("t-ap") >= 0, "result="+result);
        for (int c : "_%$()\r\n\t[]*.!".getBytes()) {
            assertFalse(result.contains(new String(new char [] {(char)c})), "result="+result);
        }
    }
    
    @Test
    public void testSanitize() {
        Assert.assertEquals(AbstractCloudMachineNamer.sanitize(
                "me & you like alphanumeric but not _ or !!! or dots...dots...dots %$()\r\n\t[]*etc"),
                "me-you-like-alphanumeric-but-not-or-or-dots-dots-dots-etc");
    }
}
