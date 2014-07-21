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
package io.brooklyn.camp.brooklyn.catalog;

import io.brooklyn.camp.brooklyn.AbstractYamlTest;
import io.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityMatcher;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.management.osgi.OsgiVersionMoreEntityTest;
import brooklyn.policy.Policy;
import brooklyn.util.ResourceUtils;

import com.google.common.collect.Iterables;

/** Many of the same tests as per {@link OsgiVersionMoreEntityTest} but using YAML for catalog and entities, so catalog item ID is set automatically */
public class CatalogOsgiVersionMoreEntityTest extends AbstractYamlTest {
    
    private static String getLocalResource(String filename) {
        return ResourceUtils.create(CatalogOsgiVersionMoreEntityTest.class).getResourceAsString(
            "classpath:/"+CatalogOsgiVersionMoreEntityTest.class.getPackage().getName().replace('.', '/')+"/"+filename);
    }
    
    @Test
    public void testMoreEntityV1() throws Exception {
        addCatalogItem(getLocalResource("more-entity-v1-osgi-catalog.yaml"));
        Entity app = createAndStartApplication("services: [ { type: 'more-entity:1.0' } ]");
        Entity moreEntity = Iterables.getOnlyElement(app.getChildren());
        
        Assert.assertEquals(moreEntity.getCatalogItemId(), "more-entity");
        OsgiVersionMoreEntityTest.assertV1EffectorCall(moreEntity);
        OsgiVersionMoreEntityTest.assertV1MethodCall(moreEntity);
    }

    /** TODO we get warnings from {@link BrooklynEntityMatcher#extractValidConfigFlagsOrKeys};
     * if we passed the correct loader at that point we could avoid those warnings. */ 
    @Test
    public void testMoreEntityV1WithPolicy() throws Exception {
        addCatalogItem(getLocalResource("simple-policy-osgi-catalog.yaml"));
        addCatalogItem(getLocalResource("more-entity-v1-with-policy-osgi-catalog.yaml"));
        Entity app = createAndStartApplication("services: [ { type: 'more-entity:1.0' } ]");
        Entity moreEntity = Iterables.getOnlyElement(app.getChildren());
        
        Assert.assertEquals(moreEntity.getCatalogItemId(), "more-entity");
        
        Assert.assertEquals(moreEntity.getPolicies().size(), 1, "wrong policies: "+moreEntity.getPolicies());
        Policy policy = Iterables.getOnlyElement(moreEntity.getPolicies());
        // it was loaded by yaml w ref to catalog, so should have the simple-policy catalog-id
        Assert.assertEquals(policy.getCatalogItemId(), "simple-policy");
    }

    @Test
    public void testMoreEntityV2() throws Exception {
        addCatalogItem(getLocalResource("more-entity-v2-osgi-catalog.yaml"));
        Entity app = createAndStartApplication("services: [ { type: 'more-entity:1.0' } ]");
        Entity moreEntity = Iterables.getOnlyElement(app.getChildren());
        
        Assert.assertEquals(moreEntity.getCatalogItemId(), "more-entity");
        OsgiVersionMoreEntityTest.assertV2EffectorCall(moreEntity);
        OsgiVersionMoreEntityTest.assertV2MethodCall(moreEntity);
        
        Assert.assertEquals(moreEntity.getPolicies().size(), 1, "wrong policies: "+moreEntity.getPolicies());
        Policy policy = Iterables.getOnlyElement(moreEntity.getPolicies());
        // it was loaded from the java so should have the base more-entity catalog id
        Assert.assertEquals(policy.getCatalogItemId(), "more-entity");
    }

    @Test
    /** TODO this test works if we assume most recent version wins, but semantics TBC */
    public void testMoreEntityV2ThenV1GivesV1() throws Exception {
        addCatalogItem(getLocalResource("more-entity-v2-osgi-catalog.yaml"));
        addCatalogItem(getLocalResource("more-entity-v1-osgi-catalog.yaml"));
        Entity app = createAndStartApplication("services: [ { type: 'more-entity:1.0' } ]");
        Entity moreEntity = Iterables.getOnlyElement(app.getChildren());
        
        OsgiVersionMoreEntityTest.assertV1EffectorCall(moreEntity);
        OsgiVersionMoreEntityTest.assertV1MethodCall(moreEntity);
    }

    /** unlike {@link #testMoreEntityV2ThenV1GivesV1()} this test should always work,
     * because default should probably be either most-recent version or highest version,
     * in either case this works */
    @Test
    public void testMoreEntityV1ThenV2GivesV2() throws Exception {
        addCatalogItem(getLocalResource("more-entity-v1-osgi-catalog.yaml"));
        addCatalogItem(getLocalResource("more-entity-v2-osgi-catalog.yaml"));
        Entity app = createAndStartApplication("services: [ { type: 'more-entity:1.0' } ]");
        Entity moreEntity = Iterables.getOnlyElement(app.getChildren());
        
        OsgiVersionMoreEntityTest.assertV2EffectorCall(moreEntity);
        OsgiVersionMoreEntityTest.assertV2MethodCall(moreEntity);
    }

    @Test
    public void testMoreEntityBothV1AndV2() throws Exception {
        addCatalogItem(getLocalResource("more-entity-v1-called-v1-osgi-catalog.yaml"));
        addCatalogItem(getLocalResource("more-entity-v2-osgi-catalog.yaml"));
        Entity v1 = createAndStartApplication("services: [ { type: 'more-entity-v1:1.0' } ]");
        Entity v2 = createAndStartApplication("services: [ { type: 'more-entity:1.0' } ]");
        
        Entity moreEntityV1 = Iterables.getOnlyElement(v1.getChildren());
        Entity moreEntityV2 = Iterables.getOnlyElement(v2.getChildren());
        
        OsgiVersionMoreEntityTest.assertV1EffectorCall(moreEntityV1);
        OsgiVersionMoreEntityTest.assertV1MethodCall(moreEntityV1);
        
        OsgiVersionMoreEntityTest.assertV2EffectorCall(moreEntityV2);
        OsgiVersionMoreEntityTest.assertV2MethodCall(moreEntityV2);
    }


}
