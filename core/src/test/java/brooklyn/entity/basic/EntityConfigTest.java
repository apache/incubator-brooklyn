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
package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableMap;

public class EntityConfigTest {

    private ManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContextForTests();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }

    @Test
    public void testConfigBagContainsMatchesForConfigKeyName() throws Exception {
        EntityInternal entity = managementContext.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("myentity.myconfig", "myval1")
                .configure("myentity.myconfigwithflagname", "myval2"));
        
        assertEquals(entity.getAllConfig(), ImmutableMap.of(MyEntity.MY_CONFIG, "myval1", MyEntity.MY_CONFIG_WITH_FLAGNAME, "myval2"));
        assertEquals(entity.getAllConfigBag().getAllConfig(), ImmutableMap.of("myentity.myconfig", "myval1", "myentity.myconfigwithflagname", "myval2"));
        assertEquals(entity.getLocalConfigBag().getAllConfig(), ImmutableMap.of("myentity.myconfig", "myval1", "myentity.myconfigwithflagname", "myval2"));
    }

    @Test
    public void testConfigBagContainsMatchesForFlagName() throws Exception {
        // Prefers flag-name, over config-key's name
        EntityInternal entity = managementContext.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("myconfigflagname", "myval"));
        
        assertEquals(entity.getAllConfig(), ImmutableMap.of(MyEntity.MY_CONFIG_WITH_FLAGNAME, "myval"));
        assertEquals(entity.getAllConfigBag().getAllConfig(), ImmutableMap.of("myentity.myconfigwithflagname", "myval"));
        assertEquals(entity.getLocalConfigBag().getAllConfig(), ImmutableMap.of("myentity.myconfigwithflagname", "myval"));
    }

    // TODO Which way round should it be?!
    @Test(enabled=false)
    public void testPrefersFlagNameOverConfigKeyName() throws Exception {
        EntityInternal entity = managementContext.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("myconfigflagname", "myval")
                .configure("myentity.myconfigwithflagname", "shouldIgnoreAndPreferFlagName"));
        
        assertEquals(entity.getAllConfig(), ImmutableMap.of(MyEntity.MY_CONFIG_WITH_FLAGNAME, "myval"));
    }

    @Test
    public void testConfigBagContainsUnmatched() throws Exception {
        EntityInternal entity = managementContext.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("notThere", "notThereVal"));
        
        assertEquals(entity.getAllConfig(), ImmutableMap.of());
        assertEquals(entity.getAllConfigBag().getAllConfig(), ImmutableMap.of("notThere", "notThereVal"));
        assertEquals(entity.getLocalConfigBag().getAllConfig(), ImmutableMap.of("notThere", "notThereVal"));
    }
    
    @Test
    public void testChildConfigBagInheritsUnmatchedAtParent() throws Exception {
        EntityInternal entity = managementContext.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("mychildentity.myconfig", "myval1")
                .configure("mychildconfigflagname", "myval2")
                .configure("notThere", "notThereVal"));

        EntityInternal child = managementContext.getEntityManager().createEntity(EntitySpec.create(MyChildEntity.class)
                .parent(entity));

        assertEquals(child.getAllConfig(), ImmutableMap.of(MyChildEntity.MY_CHILD_CONFIG, "myval1", MyChildEntity.MY_CHILD_CONFIG_WITH_FLAGNAME, "myval2"));
        assertEquals(child.getAllConfigBag().getAllConfig(), ImmutableMap.of("mychildentity.myconfig", "myval1", "mychildentity.myconfigwithflagname", "myval2", "notThere", "notThereVal"));
        assertEquals(child.getLocalConfigBag().getAllConfig(), ImmutableMap.of());
    }
    
    @Test
    public void testChildInheritsFromParent() throws Exception {
        EntityInternal entity = managementContext.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("myentity.myconfig", "myval1"));

        EntityInternal child = managementContext.getEntityManager().createEntity(EntitySpec.create(MyChildEntity.class)
                .parent(entity));

        assertEquals(child.getAllConfig(), ImmutableMap.of(MyEntity.MY_CONFIG, "myval1"));
        assertEquals(child.getAllConfigBag().getAllConfig(), ImmutableMap.of("myentity.myconfig", "myval1"));
        assertEquals(child.getLocalConfigBag().getAllConfig(), ImmutableMap.of());
    }
    
    @Test
    public void testChildCanOverrideConfigUsingKeyName() throws Exception {
        EntityInternal entity = managementContext.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("mychildentity.myconfigwithflagname", "myval")
                .configure("notThere", "notThereVal"));

        EntityInternal child = managementContext.getEntityManager().createEntity(EntitySpec.create(MyChildEntity.class)
                .parent(entity)
                .configure("mychildentity.myconfigwithflagname", "overrideMyval")
                .configure("notThere", "overrideNotThereVal"));

        assertEquals(child.getAllConfig(), ImmutableMap.of(MyChildEntity.MY_CHILD_CONFIG_WITH_FLAGNAME, "overrideMyval"));
        assertEquals(child.getAllConfigBag().getAllConfig(), ImmutableMap.of("mychildentity.myconfigwithflagname", "overrideMyval", "notThere", "overrideNotThereVal"));
        assertEquals(child.getLocalConfigBag().getAllConfig(), ImmutableMap.of("mychildentity.myconfigwithflagname", "overrideMyval", "notThere", "overrideNotThereVal"));
    }
    
    @Test
    public void testChildCanOverrideConfigUsingFlagName() throws Exception {
        EntityInternal entity = managementContext.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("mychildconfigflagname", "myval"));

        EntityInternal child = managementContext.getEntityManager().createEntity(EntitySpec.create(MyChildEntity.class)
                .parent(entity)
                .configure("mychildconfigflagname", "overrideMyval"));

        assertEquals(child.getAllConfig(), ImmutableMap.of(MyChildEntity.MY_CHILD_CONFIG_WITH_FLAGNAME, "overrideMyval"));
        assertEquals(child.getAllConfigBag().getAllConfig(), ImmutableMap.of("mychildentity.myconfigwithflagname", "overrideMyval"));
        assertEquals(child.getLocalConfigBag().getAllConfig(), ImmutableMap.of("mychildentity.myconfigwithflagname", "overrideMyval"));
    }
    
    public static class MyEntity extends AbstractEntity {
        public static final ConfigKey<String> MY_CONFIG = ConfigKeys.newStringConfigKey("myentity.myconfig");

        @SetFromFlag("myconfigflagname")
        public static final ConfigKey<String> MY_CONFIG_WITH_FLAGNAME = ConfigKeys.newStringConfigKey("myentity.myconfigwithflagname");
    }
    
    public static class MyChildEntity extends AbstractEntity {
        public static final ConfigKey<String> MY_CHILD_CONFIG = ConfigKeys.newStringConfigKey("mychildentity.myconfig");

        @SetFromFlag("mychildconfigflagname")
        public static final ConfigKey<String> MY_CHILD_CONFIG_WITH_FLAGNAME = ConfigKeys.newStringConfigKey("mychildentity.myconfigwithflagname");
    }
}
