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
package brooklyn.management.osgi;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.internal.CatalogEntityItemDto;
import brooklyn.catalog.internal.CatalogItems;
import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.entity.proxying.InternalPolicyFactory;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.policy.PolicySpec;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.os.Os;
import brooklyn.util.osgi.Osgis;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;


/** 
 * Tests that OSGi entities load correctly and have the right catalog information set.
 * Note further tests done elsewhere using CAMP YAML (referring to methods in this class).
 */
public class OsgiVersionMoreEntityTest {
   
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_PATH = OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH;
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_URL = "classpath:"+OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH;

    public static final String BROOKLYN_TEST_MORE_ENTITIES_V1_PATH = OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_V1_PATH;
    public static final String BROOKLYN_TEST_MORE_ENTITIES_V1_URL = "classpath:"+BROOKLYN_TEST_MORE_ENTITIES_V1_PATH;
    public static final String BROOKLYN_TEST_MORE_ENTITIES_V2_PATH = OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_V2_PATH;
    public static final String BROOKLYN_TEST_MORE_ENTITIES_V2_URL = "classpath:"+BROOKLYN_TEST_MORE_ENTITIES_V2_PATH;
    
    protected LocalManagementContext mgmt;
    protected TestApplication app;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mgmt = LocalManagementContextForTests.builder(true).disableOsgi(false).build();
        app = TestApplication.Factory.newManagedInstanceForTests(mgmt);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws BundleException, IOException, InterruptedException {
        Entities.destroyAll(mgmt);
    }
    
    /**
     * Test fix for
     * java.lang.NoClassDefFoundError: brooklyn.event.AttributeSensor not found by io.brooklyn.brooklyn-test-osgi-entities [41]
     */
    @Test
    public void testEntityProxy() throws Exception {
        File storageTempDir = Os.newTempDir("osgi-standalone");
        Framework framework = Osgis.newFrameworkStarted(storageTempDir.getAbsolutePath(), true, null);
        
        try {
            ManagementContextInternal managementContext;
            InternalEntityFactory factory;

            managementContext = new LocalManagementContextForTests();
            InternalPolicyFactory policyFactory = new InternalPolicyFactory(managementContext);
            factory = new InternalEntityFactory(managementContext, managementContext.getEntityManager().getEntityTypeRegistry(), policyFactory);

            Bundle bundle = Osgis.install(framework, BROOKLYN_TEST_OSGI_ENTITIES_PATH);
            @SuppressWarnings("unchecked")
            Class<? extends Entity> bundleCls = (Class<? extends Entity>) bundle.loadClass("brooklyn.osgi.tests.SimpleEntityImpl");
            @SuppressWarnings("unchecked")
            Class<? extends Entity> bundleInterface = (Class<? extends Entity>) bundle.loadClass("brooklyn.osgi.tests.SimpleEntity");

            @SuppressWarnings("unchecked")
            EntitySpec<Entity> spec = (EntitySpec<Entity>) (((EntitySpec<Entity>)EntitySpec.create(bundleInterface))).impl(bundleCls);
            Entity entity = bundleCls.newInstance();
            factory.createEntityProxy(spec, entity);

            if (managementContext != null) Entities.destroyAll(managementContext);
        } finally {
            OsgiStandaloneTest.tearDownOsgiFramework(framework, storageTempDir);
        }
    }
    
    @SuppressWarnings("deprecation")
    protected CatalogItem<?, ?> addCatalogItem(String type, String ...libraries) {
        CatalogEntityItemDto c1 = newCatalogItem(type, libraries);
        mgmt.getCatalog().addItem(c1);
        CatalogItem<?, ?> c2 = mgmt.getCatalog().getCatalogItem(type);
        return c2;
    }

    static CatalogEntityItemDto newCatalogItem(String type, String ...libraries) {
        CatalogEntityItemDto c1 = CatalogItems.newEntityFromJava(type, type);
        c1.setCatalogItemId(type);
        for (String library: libraries)
            c1.getLibrariesDto().addBundle(library);
        return c1;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected Entity addItemFromCatalog(CatalogItem<?, ?> c2) {
        BrooklynClassLoadingContext loader = CatalogUtils.newClassLoadingContext(mgmt, c2);
        EntitySpec spec = EntitySpec.create( (Class)loader.loadClass(c2.getJavaType()) );
        // not a great test as we set the ID here; but:
        // YAML test will do better;
        // and we can check that downstream items are loaded correctly
        spec.catalogItemId(c2.getRegisteredTypeName());
        Entity me = app.createAndManageChild(spec);
        return me;
    }

    public static void assertV1MethodCall(Entity me) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Assert.assertEquals(doMethodCallBrooklyn(me), "Hi BROOKLYN");
    }
    public static void assertV2MethodCall(Entity me) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Assert.assertEquals(doMethodCallBrooklyn(me), "HI BROOKLYN");
    }

    public static Object doMethodCallBrooklyn(Entity me) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        return me.getClass().getMethod("sayHI", String.class).invoke(me, "Brooklyn");
    }

    public static void assertV1EffectorCall(Entity me) {
        Assert.assertEquals(doEffectorCallBrooklyn(me), "Hi BROOKLYN");
    }
    public static void assertV2EffectorCall(Entity me) {
        Assert.assertEquals(doEffectorCallBrooklyn(me), "HI BROOKLYN");
    }

    public static String doEffectorCallBrooklyn(Entity me) {
        return me.invoke(Effectors.effector(String.class, "sayHI").buildAbstract(), ImmutableMap.of("name", "brooklyn")).getUnchecked();
    }

    @Test
    public void testMoreEntitiesV1() throws Exception {
        CatalogItem<?, ?> c2 = addCatalogItem(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, BROOKLYN_TEST_MORE_ENTITIES_V1_URL);
        
        // test load and instantiate
        Entity me = addItemFromCatalog(c2);
        Assert.assertEquals(me.getCatalogItemId(), OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY);
        
        assertV1MethodCall(me);
        assertV1EffectorCall(me);
        
        // test adding a child gets the right type; this time by entity parent hierarchy
        BrooklynClassLoadingContext loader = CatalogUtils.newClassLoadingContext(mgmt, c2);
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Entity me2 = me.addChild(EntitySpec.create( (Class)loader.loadClass(c2.getJavaType()) ));
        Assert.assertEquals(me2.getCatalogItemId(), OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected PolicySpec<?> getPolicySpec(CatalogItem<?, ?> cp) {
        BrooklynClassLoadingContext loader = CatalogUtils.newClassLoadingContext(mgmt, cp);
        PolicySpec spec = PolicySpec.create( (Class)loader.loadClass(cp.getJavaType()) );
        spec.catalogItemId(cp.getRegisteredTypeName());
        return spec;
    }

    @Test
    public void testMoreEntitiesV1Policy() throws Exception {
        CatalogItem<?, ?> c2 = addCatalogItem(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, BROOKLYN_TEST_MORE_ENTITIES_V1_URL);
        
        // test load and instantiate
        Entity me = addItemFromCatalog(c2);

        CatalogItem<?, ?> cp = addCatalogItem(OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_SIMPLE_POLICY, 
            BROOKLYN_TEST_OSGI_ENTITIES_URL);
        me.addPolicy(getPolicySpec(cp));
        
        Assert.assertEquals(me.getPolicies().size(), 1, "Wrong number of policies: "+me.getPolicies());
        
        String catalogItemId = Iterables.getOnlyElement( me.getPolicies() ).getCatalogItemId();
        Assert.assertNotNull(catalogItemId);
        // must be the actual source bundle
        Assert.assertFalse(catalogItemId.equals(me.getCatalogItemId()), "catalog item id is: "+catalogItemId);
        Assert.assertTrue(catalogItemId.equals(OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_SIMPLE_POLICY), "catalog item id is: "+catalogItemId);
    }

    @Test
    public void testMoreEntitiesV2FailsWithoutBasicTestOsgiEntitiesBundle() throws Exception {
        CatalogItem<?, ?> c2 = addCatalogItem(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, 
            BROOKLYN_TEST_MORE_ENTITIES_V2_URL);
        
        // test load and instantiate
        try {
            Entity me = addItemFromCatalog(c2);
            Assert.fail("Should have failed, with unresolved dependency; instead got "+me);
        } catch (Exception e) {
            Assert.assertTrue(e.toString().toLowerCase().contains("unresolved constraint"), "Missing expected text in error: "+e);
            Assert.assertTrue(e.toString().toLowerCase().contains("wiring.package"), "Missing expected text in error: "+e);
            Assert.assertTrue(e.toString().toLowerCase().contains("brooklyn.osgi.tests"), "Missing expected text in error: "+e);
        }
    }
    
    // V2 works with dependency declared, and can load
    // and has policy, with policy item catalog ID is reasonable
    @Test
    public void testMoreEntitiesV2() throws Exception {
        CatalogItem<?, ?> c2 = addCatalogItem(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, 
            BROOKLYN_TEST_MORE_ENTITIES_V2_URL, BROOKLYN_TEST_OSGI_ENTITIES_URL);
        
        // test load and instantiate
        Entity me = addItemFromCatalog(c2);
        Assert.assertEquals(me.getCatalogItemId(), OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY);
        
        assertV2MethodCall(me);
        assertV2EffectorCall(me);
        Assert.assertEquals(me.getPolicies().size(), 1, "Wrong number of policies: "+me.getPolicies());
        
        String catalogItemId = Iterables.getOnlyElement( me.getPolicies() ).getCatalogItemId();
        Assert.assertNotNull(catalogItemId);
        // allow either me's bundle (more) or the actual source bundle
        Assert.assertTrue(catalogItemId.equals(me.getCatalogItemId()) || catalogItemId.startsWith("brooklyn-test-osgi-entities"));
    }

    @Test
    public void testMoreEntitiesV1ThenV2GivesV2() throws Exception {
        addCatalogItem(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, 
            BROOKLYN_TEST_MORE_ENTITIES_V1_URL);
        addCatalogItem(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, 
            BROOKLYN_TEST_MORE_ENTITIES_V2_URL, BROOKLYN_TEST_OSGI_ENTITIES_URL);
        
        // test load and instantiate
        Entity me = addItemFromCatalog( mgmt.getCatalog().getCatalogItem(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY) );
        
        assertV2MethodCall(me);
        assertV2EffectorCall(me);
        Assert.assertEquals(me.getPolicies().size(), 1, "Wrong number of policies: "+me.getPolicies());
    }

    @Test
    public void testMoreEntitiesV2ThenV1GivesV1() throws Exception {
        addCatalogItem(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, 
            BROOKLYN_TEST_MORE_ENTITIES_V2_URL, BROOKLYN_TEST_OSGI_ENTITIES_URL);
        addCatalogItem(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, 
            BROOKLYN_TEST_MORE_ENTITIES_V1_URL);
        
        // test load and instantiate
        Entity me = addItemFromCatalog( mgmt.getCatalog().getCatalogItem(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY) );
        /*
         * WARNING - Weird maven-bundle-plugin and OSGi behaviour.  Some caveats:
         * <p>
         * (1) When "import-package" declares a version, that is the *minimum* version;
         *     it may be that semantic versioning is applied, so 1.3.1 = [1.3.1,4.0.0);
         *     or it may be just a minimum 1.3.1 = [1.3.1,) ... i can't find good docs
         *     [but see http://www.christianposta.com/blog/?p=241]
         * <p>
         * (2) Different versions of maven-bundle-plugin do wildly different things.
         *     * v1.4.0 attaches the version to import-package (so you get the minimum
         *       which can cause this test to fail);
         *     * v2.x does not seem to declare the exported package at all in import-package
         *       (violating the so-called best practice, see
         *       http://blog.osgi.org/2007/04/importance-of-exporting-nd-importing.html )
         *     * v2.4.0 gives a huge list in import/export package, with semver ranges;
         *       but the other versions seem not to list much and they do NOT have versions
         * <p>
         * The tests are all working with 2.5.3 but if version dependencies become any
         * more intertwined maven-bundle-plugin will almost certainly NOT do the wrong
         * thing because packages do not have versions. (Ironically, 1.4.0 seems the
         * best behaved, but for the minimum/semver package version behaviour, and
         * even that wouldn't be so bad if you're doing semver, or if you figure out
         * how to override with a _versionpolicy tag!)
         */
        assertV1MethodCall(me);
        assertV1EffectorCall(me);
        Assert.assertEquals(me.getPolicies().size(), 0, "Wrong number of policies: "+me.getPolicies());
    }

    // TODO test YAML for many of the above (in the camp project CAMP, using other code below)
    
    // TODO versioning (WIP until #92), install both V1 and V2 with version number, and test that both work
    
    // TODO other code which might be useful - but requires CAMP:
//        mgmt.getCatalog().addItem(Strings.lines(
//            "brooklyn.catalog:",
//            "  id: my-entity",
//            "brooklyn.library:",
//            "- url: "+BROOKLYN_TEST_MORE_ENTITIES_V1_URL,
//            "services:",
//            "- type: "+OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY
//        ));
    
}
