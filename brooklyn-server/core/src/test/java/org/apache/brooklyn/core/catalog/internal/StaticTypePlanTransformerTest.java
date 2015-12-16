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
package org.apache.brooklyn.core.catalog.internal;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.test.BrooklynMgmtUnitTestSupport;
import org.apache.brooklyn.core.typereg.BasicTypeImplementationPlan;
import org.apache.brooklyn.core.typereg.RegisteredTypes;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class StaticTypePlanTransformerTest extends BrooklynMgmtUnitTestSupport {

    final static String DISPLAY_NAME = "Static Test";
    String specId;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        StaticTypePlanTransformer.forceInstall();
        specId = StaticTypePlanTransformer.registerSpec(EntitySpec.create(BasicEntity.class).displayName(DISPLAY_NAME));
    }
    
    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        StaticTypePlanTransformer.clearForced();
        super.tearDown();
    }
    
    @Test
    public void testCreateSpec() {
        EntitySpec<?> spec = mgmt.getTypeRegistry().createSpec(
            RegisteredTypes.spec(specId, "1", new BasicTypeImplementationPlan(StaticTypePlanTransformer.FORMAT, null), Entity.class),
            null, EntitySpec.class);
        Assert.assertEquals(spec.getDisplayName(), DISPLAY_NAME);
        Assert.assertEquals(spec.getType(), BasicEntity.class);
    }

    // there's a lot more which could be tested but at least we have a simple pathway asserting creation

}
