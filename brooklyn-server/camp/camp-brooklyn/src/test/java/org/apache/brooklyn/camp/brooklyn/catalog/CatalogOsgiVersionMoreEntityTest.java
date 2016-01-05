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
package org.apache.brooklyn.camp.brooklyn.catalog;

import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityMatcher;
import org.apache.brooklyn.core.mgmt.osgi.OsgiVersionMoreEntityTest;
import org.apache.brooklyn.core.objs.BrooklynTypes;
import org.apache.brooklyn.core.typereg.RegisteredTypePredicates;
import org.apache.brooklyn.core.typereg.RegisteredTypes;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.Iterables;

/** Many of the same tests as per {@link OsgiVersionMoreEntityTest} but using YAML for catalog and entities, so catalog item ID is set automatically */
public class CatalogOsgiVersionMoreEntityTest extends AbstractYamlTest {
    
    private static final Logger log = LoggerFactory.getLogger(CatalogOsgiVersionMoreEntityTest.class);
    
    private static String getLocalResource(String filename) {
        return ResourceUtils.create(CatalogOsgiVersionMoreEntityTest.class).getResourceAsString(
            "classpath:/"+CatalogOsgiVersionMoreEntityTest.class.getPackage().getName().replace('.', '/')+"/"+filename);
    }
    
    @Test
    public void testMoreEntityV1() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-more-entities_0.1.0.jar");

        addCatalogItems(getLocalResource("more-entity-v1-osgi-catalog.yaml"));
        RegisteredType item = mgmt().getTypeRegistry().get("more-entity");
        Assert.assertNotNull(item);
        Assert.assertEquals(item.getVersion(), "1.0");
        Assert.assertTrue(RegisteredTypePredicates.IS_ENTITY.apply(item));
        Assert.assertEquals(item.getLibraries().size(), 1);
        
        Entity app = createAndStartApplication("services: [ { type: 'more-entity:1.0' } ]");
        Entity moreEntity = Iterables.getOnlyElement(app.getChildren());
        
        Assert.assertEquals(moreEntity.getCatalogItemId(), "more-entity:1.0");
        OsgiVersionMoreEntityTest.assertV1EffectorCall(moreEntity);
        OsgiVersionMoreEntityTest.assertV1MethodCall(moreEntity);
    }

    /** TODO we get warnings from {@link BrooklynEntityMatcher#extractValidConfigFlagsOrKeys};
     * if we passed the correct loader at that point we could avoid those warnings. */ 
    @Test
    public void testMoreEntityV1WithPolicy() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-more-entities_0.1.0.jar");
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-entities.jar");

        addCatalogItems(getLocalResource("simple-policy-osgi-catalog.yaml"));
        addCatalogItems(getLocalResource("more-entity-v1-with-policy-osgi-catalog.yaml"));
        Entity app = createAndStartApplication("services: [ { type: 'more-entity:1.0' } ]");
        Entity moreEntity = Iterables.getOnlyElement(app.getChildren());
        
        Assert.assertEquals(moreEntity.getCatalogItemId(), "more-entity:1.0");
        
        Assert.assertEquals(moreEntity.policies().size(), 1, "wrong policies: "+moreEntity.policies());
        Policy policy = Iterables.getOnlyElement(moreEntity.policies());
        // it was loaded by yaml w ref to catalog, so should have the simple-policy catalog-id
        Assert.assertEquals(policy.getCatalogItemId(), "simple-policy:1.0");
    }

    @Test
    public void testMoreEntityV2() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-more-entities_0.2.0.jar");
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-entities.jar");

        addCatalogItems(getLocalResource("more-entity-v2-osgi-catalog.yaml"));
        Entity app = createAndStartApplication("services: [ { type: 'more-entity:1.0' } ]");
        Entity moreEntity = Iterables.getOnlyElement(app.getChildren());
        
        Assert.assertEquals(moreEntity.getCatalogItemId(), "more-entity:1.0");
        OsgiVersionMoreEntityTest.assertV2EffectorCall(moreEntity);
        OsgiVersionMoreEntityTest.assertV2MethodCall(moreEntity);
        
        Assert.assertEquals(moreEntity.policies().size(), 1, "wrong policies: "+moreEntity.policies());
        Policy policy = Iterables.getOnlyElement(moreEntity.policies());
        // it was loaded from the java so should have the base more-entity catalog id
        Assert.assertEquals(policy.getCatalogItemId(), "more-entity:1.0");
    }

    @Test
    /** TODO this test works if we assume most recent version wins, but semantics TBC */
    public void testMoreEntityV2ThenV1GivesV1() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-more-entities_0.1.0.jar");
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-more-entities_0.2.0.jar");
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-entities.jar");

        addCatalogItems(getLocalResource("more-entity-v2-osgi-catalog.yaml"));
        forceCatalogUpdate();
        addCatalogItems(getLocalResource("more-entity-v1-osgi-catalog.yaml"));
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
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-more-entities_0.1.0.jar");
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-more-entities_0.2.0.jar");
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-entities.jar");

        addCatalogItems(getLocalResource("more-entity-v1-osgi-catalog.yaml"));
        forceCatalogUpdate();
        addCatalogItems(getLocalResource("more-entity-v2-osgi-catalog.yaml"));
        Entity app = createAndStartApplication("services: [ { type: 'more-entity:1.0' } ]");
        Entity moreEntity = Iterables.getOnlyElement(app.getChildren());
        
        OsgiVersionMoreEntityTest.assertV2EffectorCall(moreEntity);
        OsgiVersionMoreEntityTest.assertV2MethodCall(moreEntity);
    }

    @Test
    public void testMoreEntityBothV1AndV2() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-more-entities_0.1.0.jar");
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-more-entities_0.2.0.jar");
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-entities.jar");

        addCatalogItems(getLocalResource("more-entity-v1-called-v1-osgi-catalog.yaml"));
        addCatalogItems(getLocalResource("more-entity-v2-osgi-catalog.yaml"));
        Entity v1 = createAndStartApplication("services: [ { type: 'more-entity-v1:1.0' } ]");
        Entity v2 = createAndStartApplication("services: [ { type: 'more-entity:1.0' } ]");
        
        Entity moreEntityV1 = Iterables.getOnlyElement(v1.getChildren());
        Entity moreEntityV2 = Iterables.getOnlyElement(v2.getChildren());
        
        OsgiVersionMoreEntityTest.assertV1EffectorCall(moreEntityV1);
        OsgiVersionMoreEntityTest.assertV1MethodCall(moreEntityV1);
        
        OsgiVersionMoreEntityTest.assertV2EffectorCall(moreEntityV2);
        OsgiVersionMoreEntityTest.assertV2MethodCall(moreEntityV2);
    }

    @Test
    public void testMoreEntityV2AutoscanWithClasspath() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-more-entities_0.2.0.jar");
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-entities.jar");
        
        addCatalogItems(getLocalResource("more-entities-osgi-catalog-scan.yaml"));
        
        log.info("autoscan for osgi found catalog items: "+Strings.join(mgmt().getCatalog().getCatalogItems(), ", "));

        RegisteredType item = mgmt().getTypeRegistry().get("more-entity");
        Assert.assertNotNull(item);
        Assert.assertEquals(item.getVersion(), "2.0.test");
        Assert.assertTrue(RegisteredTypePredicates.IS_ENTITY.apply(item));
        
        // this refers to the java item, where the libraries are defined
        item = mgmt().getTypeRegistry().get("org.apache.brooklyn.test.osgi.entities.more.MoreEntity");
        Assert.assertEquals(item.getVersion(), "2.0.test_java");
        Assert.assertEquals(item.getLibraries().size(), 2);
        
        Entity app = createAndStartApplication("services: [ { type: 'more-entity:2.0.test' } ]");
        Entity moreEntity = Iterables.getOnlyElement(app.getChildren());
        
        Assert.assertEquals(moreEntity.getCatalogItemId(), "more-entity:2.0.test");
        OsgiVersionMoreEntityTest.assertV2EffectorCall(moreEntity);
        OsgiVersionMoreEntityTest.assertV2MethodCall(moreEntity);
    }

    @Test
    public void testMorePolicyV2AutoscanWithClasspath() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-more-entities_0.2.0.jar");
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-entities.jar");
        
        addCatalogItems(getLocalResource("more-policies-osgi-catalog-scan.yaml"));
        
        log.info("autoscan for osgi found catalog items: "+Strings.join(mgmt().getCatalog().getCatalogItems(), ", "));

        RegisteredType item = mgmt().getTypeRegistry().get("more-policy");
        Assert.assertNotNull(item);
        Assert.assertEquals(item.getVersion(), "2.0.test");
        Assert.assertTrue(RegisteredTypePredicates.IS_POLICY.apply(item));
        
        // this refers to the java item, where the libraries are defined
        item = mgmt().getTypeRegistry().get("org.apache.brooklyn.test.osgi.entities.more.MorePolicy");
        Assert.assertEquals(item.getVersion(), "2.0.test_java");
        Assert.assertEquals(item.getLibraries().size(), 2);
        
        Entity app = createAndStartApplication(
                "services: ",
                "- type: org.apache.brooklyn.entity.stock.BasicEntity",
                "  brooklyn.policies:",
                "  - type: more-policy:2.0.test");
        Entity basicEntity = Iterables.getOnlyElement(app.getChildren());
        Policy morePolicy = Iterables.getOnlyElement(basicEntity.policies());
        
        Assert.assertEquals(morePolicy.getCatalogItemId(), "more-policy:2.0.test");
        OsgiVersionMoreEntityTest.assertV2MethodCall(morePolicy);
    }

    @Test
    public void testAutoscanWithClasspathCanCreateSpecs() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-more-entities_0.2.0.jar");
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/brooklyn/osgi/brooklyn-test-osgi-entities.jar");

        addCatalogItems(getLocalResource("more-entities-osgi-catalog-scan.yaml"));

        log.info("autoscan for osgi found catalog items: "+Strings.join(mgmt().getTypeRegistry().getAll(), ", "));

        BrooklynTypeRegistry types = mgmt().getTypeRegistry();
        Iterable<RegisteredType> items = types.getAll();
        for (RegisteredType item: items) {
            Object spec = types.createSpec(item, null, null);
            int match = 0;
            if (RegisteredTypes.isSubtypeOf(item, Entity.class)) {
                assertTrue(spec instanceof EntitySpec, "Not an EntitySpec: " + spec);
                BrooklynTypes.getDefinedEntityType(((EntitySpec<?>)spec).getType());
                match++;
            }
            if (RegisteredTypes.isSubtypeOf(item, Policy.class)) {
                assertTrue(spec instanceof PolicySpec, "Not a PolicySpec: " + spec);
                BrooklynTypes.getDefinedBrooklynType(((PolicySpec<?>)spec).getType());
                match++;
            }
            if (RegisteredTypes.isSubtypeOf(item, Location.class)) {
                assertTrue(spec instanceof LocationSpec, "Not a LocationSpec: " + spec);
                BrooklynTypes.getDefinedBrooklynType(((LocationSpec<?>)spec).getType());
                match++;
            }
            if (match==0) {
                Assert.fail("Unexpected type: "+item+" ("+item.getSuperTypes()+")");
            }
        }
    }

}
