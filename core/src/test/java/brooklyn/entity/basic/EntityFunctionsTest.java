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
import static org.testng.Assert.assertNull;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.TestEntity;

public class EntityFunctionsTest extends BrooklynAppUnitTestSupport {

    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("mydisplayname"));
    }

    @Test
    public void testAttribute() throws Exception {
        entity.setAttribute(TestEntity.NAME, "myname");
        assertEquals(EntityFunctions.attribute(TestEntity.NAME).apply(entity), "myname");
        assertNull(EntityFunctions.attribute(TestEntity.SEQUENCE).apply(entity));
    }
    
    @Test
    public void testConfig() throws Exception {
        entity.setConfig(TestEntity.CONF_NAME, "myname");
        assertEquals(EntityFunctions.config(TestEntity.CONF_NAME).apply(entity), "myname");
        assertNull(EntityFunctions.config(TestEntity.CONF_OBJECT).apply(entity));
    }
    
    @Test
    public void testDisplayName() throws Exception {
        assertEquals(EntityFunctions.displayName().apply(entity), "mydisplayname");
    }
    
    @Test
    public void testId() throws Exception {
        assertEquals(EntityFunctions.id().apply(entity), entity.getId());
    }
}
