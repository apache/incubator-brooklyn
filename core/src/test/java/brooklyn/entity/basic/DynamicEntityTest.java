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
import static org.testng.Assert.assertFalse;

import org.apache.brooklyn.api.entity.basic.EntityLocal;
import org.apache.brooklyn.api.entity.proxying.EntityInitializer;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.test.entity.TestEntity;
import org.apache.brooklyn.util.collections.MutableMap;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.effector.EffectorTaskTest;

public class DynamicEntityTest extends BrooklynAppUnitTestSupport {

    @Test
    public void testEffectorAddedDuringInit() {
        BasicEntity entity = app.createAndManageChild(EntitySpec.create(BasicEntity.class)
                .addInitializer(new EntityInitializer() {
                    public void apply(EntityLocal entity) {
                        ((EntityInternal) entity).getMutableEntityType().addEffector(EffectorTaskTest.DOUBLE_1);
                    }
                }));
        assertEquals(entity.invoke(EffectorTaskTest.DOUBLE_BODYLESS, MutableMap.of("numberToDouble", 5)).getUnchecked(), (Integer) 10);
    }

    @Test
    public void testEffectorRemovedDuringInit() {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .addInitializer(new EntityInitializer() {
                    @Override
                    public void apply(EntityLocal entity) {
                        ((EntityInternal) entity).getMutableEntityType().removeEffector(TestEntity.IDENTITY_EFFECTOR);
                    }
                }));
        assertFalse(entity.getMutableEntityType().getEffectors().containsKey(TestEntity.IDENTITY_EFFECTOR.getName()));
    }
    
}
