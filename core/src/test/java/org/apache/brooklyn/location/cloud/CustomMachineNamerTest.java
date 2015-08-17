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
package org.apache.brooklyn.location.cloud;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.core.util.config.ConfigBag;
import org.apache.brooklyn.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.test.entity.TestApplication;
import org.apache.brooklyn.test.entity.TestEntity;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;

import org.apache.brooklyn.location.cloud.names.CustomMachineNamer;

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
        Assert.assertEquals(new CustomMachineNamer().generateNewMachineUniqueName(config), "TestEnt");
    }
    
    @Test
    public void testMachineNameWithConfig() {
        child.setSequenceValue(999);
        config.configure(CustomMachineNamer.MACHINE_NAME_TEMPLATE, "number${entity.sequenceValue}");
        Assert.assertEquals(new CustomMachineNamer().generateNewMachineUniqueName(config), "number999");
    }
    
    @Test
    public void testMachineNameWithExtraSubstitutions() {
        config.configure(CustomMachineNamer.MACHINE_NAME_TEMPLATE, "foo-${fooName}-bar-${barName}-baz-${bazName.substitution}")
            .configure(CustomMachineNamer.EXTRA_SUBSTITUTIONS, ImmutableMap.of("fooName", "foo", "barName", "bar", "bazName", this));
        Assert.assertEquals(new CustomMachineNamer().generateNewMachineUniqueName(config), "foo-foo-bar-bar-baz-baz");
    }
    
    public String getSubstitution() {
        return "baz";
    }
}
