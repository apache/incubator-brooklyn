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

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.config.ConfigBag;

import com.google.common.collect.ImmutableMap;

public class CustomMachineNamerTest {
    
    private TestApplication app;
    private TestEntity child;
    private ConfigBag config;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("TestApp"), LocalManagementContextForTests.newInstance());
        child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("TestEnt"));
        config = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testMachineNameNoConfig() {
        config.configure(CloudLocationConfig.CALLER_CONTEXT, child);
        Assert.assertEquals(new CustomMachineNamer(config).generateNewMachineUniqueName(), "TestEnt");
    }
    
    @Test
    public void testMachineNameWithConfig() {
        child.setSequenceValue(999);
        config.configure(CustomMachineNamer.MACHINE_NAME_TEMPLATE, "number${entity.sequenceValue}");
        Assert.assertEquals(new CustomMachineNamer(config).generateNewMachineUniqueName(), "number999");
    }
    
    @Test
    public void testMachineNameWithExtraSubstitutions() {
        config.configure(CustomMachineNamer.MACHINE_NAME_TEMPLATE, "foo-${fooName}-bar-${barName}-baz-${bazName.substitution}")
            .configure(CustomMachineNamer.EXTRA_SUBSTITUTIONS, ImmutableMap.of("fooName", "foo", "barName", "bar", "bazName", this));
        Assert.assertEquals(new CustomMachineNamer(config).generateNewMachineUniqueName(), "foo-foo-bar-bar-baz-baz");
    }
    
    public String getSubstitution() {
        return "baz";
    }
}
