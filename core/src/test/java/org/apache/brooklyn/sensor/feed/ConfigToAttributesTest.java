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
package org.apache.brooklyn.sensor.feed;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.sensor.core.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.sensor.core.TemplatedStringAttributeSensorAndConfigKey;
import org.apache.brooklyn.sensor.feed.ConfigToAttributes;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ConfigToAttributesTest {

    private ManagementContextInternal managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testApplyTemplatedConfigWithEntity() {
        TestApplication app = managementContext.getEntityManager().createEntity(EntitySpec.create(TestApplication.class)
                .configure(TestEntity.CONF_NAME, "myval"));
        Entities.startManagement(app, managementContext);
        
        BasicAttributeSensorAndConfigKey<String> key = new TemplatedStringAttributeSensorAndConfigKey("mykey", "my descr", "${config['test.confName']!'notfound'}");
        String val = ConfigToAttributes.apply(app, key);
        assertEquals(app.getAttribute(key), val);
        assertEquals(val, "myval");

    }
    
    @Test
    public void testApplyTemplatedConfigWithManagementContext() {
        managementContext.getBrooklynProperties().put(TestEntity.CONF_NAME, "myglobalval");
        BasicAttributeSensorAndConfigKey<String> key = new TemplatedStringAttributeSensorAndConfigKey("mykey", "my descr", "${config['test.confName']!'notfound'}");
        String val = ConfigToAttributes.transform(managementContext, key);
        assertEquals(val, "myglobalval");
    }
}
