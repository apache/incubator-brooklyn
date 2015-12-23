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
import java.util.Iterator;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.EntityRelations;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class RelationsEntityRebindTest extends RebindTestFixtureWithApp {

    public void testCustomEntityRelation() throws Exception {
        TestEntity origT1 = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity origT2 = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
        origT1.relations().add(EntityRelations.HAS_TARGET, origT2);
        
        TestApplication newApp = rebind();
        Iterator<Entity> ci = newApp.getChildren().iterator();
        Entity t1 = ci.next();
        Entity t2 = ci.next();
        
        Assert.assertEquals(t1.relations().getRelations(EntityRelations.HAS_TARGET), Collections.singleton(t2));
        Assert.assertEquals(t2.relations().getRelations(EntityRelations.HAS_TARGET), Collections.emptySet());
        Assert.assertEquals(t2.relations().getRelations(EntityRelations.TARGETTED_BY), Collections.singleton(t1));
    }
    
}
