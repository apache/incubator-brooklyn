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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.relations.RelationshipType;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class RelationshipTest {

    static RelationshipType<Entity, TestEntity> AUNTIE_OF = RelationshipTypes.newRelationshipPair(
        "auntie", "aunties", Entity.class, "auntie_of_nephew",
        "nephew", "nephews", TestEntity.class, "nephew_of_auntie");
    
    static RelationshipType<TestEntity, Entity> NEPHEW_OF = AUNTIE_OF.getInverseRelationshipType();
    
    public void testFields() {
        Assert.assertEquals(AUNTIE_OF.getRelationshipTypeName(), "auntie_of_nephew");
        Assert.assertEquals(AUNTIE_OF.getSourceType(), Entity.class);
        Assert.assertEquals(AUNTIE_OF.getTargetType(), TestEntity.class);
        Assert.assertEquals(AUNTIE_OF.getSourceName(), "auntie");
        Assert.assertEquals(AUNTIE_OF.getTargetName(), "nephew");
        Assert.assertEquals(AUNTIE_OF.getSourceNamePlural(), "aunties");
        Assert.assertEquals(AUNTIE_OF.getTargetNamePlural(), "nephews");
        
        Assert.assertEquals(NEPHEW_OF.getRelationshipTypeName(), "nephew_of_auntie");
        Assert.assertEquals(NEPHEW_OF.getSourceType(), TestEntity.class);
        Assert.assertEquals(NEPHEW_OF.getTargetType(), Entity.class);
        Assert.assertEquals(NEPHEW_OF.getSourceName(), "nephew");
        Assert.assertEquals(NEPHEW_OF.getTargetName(), "auntie");
        Assert.assertEquals(NEPHEW_OF.getSourceNamePlural(), "nephews");
        Assert.assertEquals(NEPHEW_OF.getTargetNamePlural(), "aunties");
        
        Assert.assertEquals(NEPHEW_OF.getInverseRelationshipType(), AUNTIE_OF);
        Assert.assertEquals(AUNTIE_OF.getInverseRelationshipType(), NEPHEW_OF);
    }
    
}
