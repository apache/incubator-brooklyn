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
package org.apache.brooklyn.core.relations;

import java.util.Collections;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.EntityRelations;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.util.collections.MutableSet;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class RelationsEntityBasicTest extends BrooklynAppUnitTestSupport {

    @Override
    protected void setUpApp() {
        super.setUpApp();
    }
    
    public void testCustomEntityRelation() {
        TestEntity t1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity t2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        t1.relations().add(EntityRelations.HAS_TARGET, t2);
        Assert.assertEquals(t1.relations().getRelations(EntityRelations.HAS_TARGET), Collections.singleton(t2));
        Assert.assertEquals(t2.relations().getRelations(EntityRelations.HAS_TARGET), Collections.emptySet());
        Assert.assertEquals(t2.relations().getRelations(EntityRelations.TARGETTED_BY), Collections.singleton(t1));
        
        t1.relations().add(EntityRelations.HAS_TARGET, t2);
        Assert.assertEquals(t1.relations().getRelations(EntityRelations.HAS_TARGET), Collections.singleton(t2));
        
        t1.relations().add(EntityRelations.HAS_TARGET, t1);
        Assert.assertEquals(t1.relations().getRelations(EntityRelations.HAS_TARGET), MutableSet.of(t1, t2));
    }
    
}
