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
package org.apache.brooklyn.core.mgmt.osgi;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.core.catalog.internal.CatalogEntityItemDto;
import org.apache.brooklyn.core.catalog.internal.CatalogItemBuilder;
import org.apache.brooklyn.core.catalog.internal.CatalogItemDtoAbstract;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.objs.proxy.InternalEntityFactory;
import org.apache.brooklyn.core.objs.proxy.InternalPolicyFactory;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.osgi.OsgiTestBase;
import org.apache.brooklyn.util.core.osgi.Osgis;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.osgi.OsgiTestResources;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Preconditions;
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
    public static final String BROOKLYN_TEST_MORE_ENTITIES_V2_EVIL_TWIN_PATH = OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_V2_EVIL_TWIN_PATH;
    public static final String BROOKLYN_TEST_MORE_ENTITIES_V2_EVIL_TWIN_URL = "classpath:"+BROOKLYN_TEST_MORE_ENTITIES_V2_EVIL_TWIN_PATH;
    
    public static final String TEST_VERSION = "0.1.0";

    public static final String EXPECTED_SAY_HI_BROOKLYN_RESPONSE_FROM_V1 = "Hi BROOKLYN from V1";
    public static final String EXPECTED_SAY_HI_BROOKLYN_RESPONSE_FROM_V2 = "HI BROOKLYN FROM V2";
    public static final String EXPECTED_SAY_HI_BROOKLYN_RESPONSE_FROM_V2_EVIL_TWIN = "HO BROOKLYN FROM V2 EVIL TWIN";
    
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
     * NoClassDefFoundError: org.apache.brooklyn.api.sensor.AttributeSensor not found by ....brooklyn-test-osgi-entities
     * (see comments in InternalEntityFactory)
     */
    @Test
    public void testEntityProxy() throws Exception {
        File storageTempDir = Os.newTempDir("osgi-standalone");
        Framework framework = Osgis.getFramework(storageTempDir.getAbsolutePath(), true);
        
        try {
            ManagementContextInternal managementContext;
            InternalEntityFactory factory;

            managementContext = new LocalManagementContextForTests();
            InternalPolicyFactory policyFactory = new InternalPolicyFactory(managementContext);
            factory = new InternalEntityFactory(managementContext, managementContext.getEntityManager().getEntityTypeRegistry(), policyFactory);

            TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_OSGI_ENTITIES_PATH);
            Bundle bundle = Osgis.install(framework, BROOKLYN_TEST_OSGI_ENTITIES_PATH);
            @SuppressWarnings("unchecked")
            Class<? extends Entity> bundleCls = (Class<? extends Entity>) bundle.loadClass("org.apache.brooklyn.test.osgi.entities.SimpleEntityImpl");
            @SuppressWarnings("unchecked")
            Class<? extends Entity> bundleInterface = (Class<? extends Entity>) bundle.loadClass("org.apache.brooklyn.test.osgi.entities.SimpleEntity");

            @SuppressWarnings("unchecked")
            EntitySpec<Entity> spec = (EntitySpec<Entity>) (((EntitySpec<Entity>)EntitySpec.create(bundleInterface))).impl(bundleCls);
            Entity entity = bundleCls.newInstance();
            factory.createEntityProxy(spec, entity);

            if (managementContext != null) Entities.destroyAll(managementContext);
        } finally {
            OsgiTestBase.tearDownOsgiFramework(framework, storageTempDir);
        }
    }
    
    protected RegisteredType addCatalogItemWithTypeAsName(String type, String version, String ...libraries) {
        return addCatalogItemWithNameAndType(type, version, type, libraries);
    }
    protected RegisteredType addCatalogItemWithNameAndType(String symName, String version, String type, String ...libraries) {
        return addCatalogItemWithNameAndType(mgmt, symName, version, type, libraries);
    }

    @SuppressWarnings("deprecation")
    static RegisteredType addCatalogItemWithNameAndType(ManagementContext mgmt, String symName, String version, String type, String ...libraries) {
        CatalogEntityItemDto c1 = newCatalogItemWithNameAndType(symName, version, type, libraries);
        mgmt.getCatalog().addItem(c1);
        RegisteredType c2 = mgmt.getTypeRegistry().get(symName, version);
        Preconditions.checkNotNull(c2, "Item "+type+":"+version+" was not found after adding it");
        return c2;
    }

    static CatalogEntityItemDto newCatalogItemWithTypeAsName(String type, String version, String ...libraries) {
        return newCatalogItemWithNameAndType(type, version, type, libraries);
    }
    static CatalogEntityItemDto newCatalogItemWithNameAndType(String symName, String version, String type, String ...libraries) {
        @SuppressWarnings("deprecation")
        CatalogEntityItemDto c1 = CatalogItemBuilder.newEntity(symName, version)
                .javaType(type)
                .libraries(CatalogItemDtoAbstract.parseLibraries(Arrays.asList(libraries)))
                .build();
        return c1;
    }

    protected Entity addItemFromCatalog(RegisteredType c2) {
        return addItemFromCatalog(mgmt, app, c2);
    }
    
    @SuppressWarnings("unchecked")
    public static Entity addItemFromCatalog(ManagementContext mgmt, TestApplication parent, RegisteredType c2) {
        return parent.createAndManageChild( mgmt.getTypeRegistry().createSpec(c2, null, EntitySpec.class) );
    }

    public static void assertV1MethodCall(Entity me) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Assert.assertEquals(doMethodCallBrooklyn(me), EXPECTED_SAY_HI_BROOKLYN_RESPONSE_FROM_V1);
    }
    public static void assertV2MethodCall(BrooklynObject me) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Assert.assertEquals(doMethodCallBrooklyn(me), EXPECTED_SAY_HI_BROOKLYN_RESPONSE_FROM_V2);
    }
    public static void assertV2EvilTwinMethodCall(Entity me) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Assert.assertEquals(doMethodCallBrooklyn(me), EXPECTED_SAY_HI_BROOKLYN_RESPONSE_FROM_V2_EVIL_TWIN);
    }

    public static Object doMethodCallBrooklyn(BrooklynObject me) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        return me.getClass().getMethod("sayHI", String.class).invoke(me, "Brooklyn");
    }

    public static void assertV1EffectorCall(Entity me) {
        Assert.assertEquals(doEffectorCallBrooklyn(me), EXPECTED_SAY_HI_BROOKLYN_RESPONSE_FROM_V1);
    }
    public static void assertV2EffectorCall(Entity me) {
        Assert.assertEquals(doEffectorCallBrooklyn(me), EXPECTED_SAY_HI_BROOKLYN_RESPONSE_FROM_V2);
    }
    public static void assertV2EvilTwinEffectorCall(Entity me) {
        Assert.assertEquals(doEffectorCallBrooklyn(me), EXPECTED_SAY_HI_BROOKLYN_RESPONSE_FROM_V2_EVIL_TWIN);
    }

    public static String doEffectorCallBrooklyn(Entity me) {
        return me.invoke(Effectors.effector(String.class, "sayHI").buildAbstract(), ImmutableMap.of("name", "brooklyn")).getUnchecked();
    }

    public static RegisteredType addMoreEntityV1(ManagementContext mgmt, String versionToRegister) {
        TestResourceUnavailableException.throwIfResourceUnavailable(OsgiVersionMoreEntityTest.class, BROOKLYN_TEST_MORE_ENTITIES_V1_PATH);
        return addCatalogItemWithNameAndType(mgmt,
            OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
            versionToRegister,
            OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
            BROOKLYN_TEST_MORE_ENTITIES_V1_URL);
    }
    public static RegisteredType addMoreEntityV2(ManagementContext mgmt, String versionToRegister) {
        TestResourceUnavailableException.throwIfResourceUnavailable(OsgiVersionMoreEntityTest.class, BROOKLYN_TEST_MORE_ENTITIES_V1_PATH);
        return addCatalogItemWithNameAndType(mgmt,
            OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
            versionToRegister,
            OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
            BROOKLYN_TEST_MORE_ENTITIES_V2_URL,
            BROOKLYN_TEST_OSGI_ENTITIES_URL);
    }
    
    @Test
    public void testMoreEntitiesV1() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_MORE_ENTITIES_V1_PATH);

        RegisteredType c2 = addMoreEntityV1(mgmt, TEST_VERSION);
        
        // test load and instantiate
        Entity me = addItemFromCatalog(c2);
        Assert.assertEquals(me.getCatalogItemId(), CatalogUtils.getVersionedId(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, TEST_VERSION));
        
        assertV1MethodCall(me);
        assertV1EffectorCall(me);
        
        // test adding a child gets the right type; this time by entity parent hierarchy
        @SuppressWarnings({ "unchecked" })
        Entity me2 = me.addChild( mgmt.getTypeRegistry().createSpec(c2, null, EntitySpec.class) );
        Assert.assertEquals(me2.getCatalogItemId(), CatalogUtils.getVersionedId(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, TEST_VERSION));
    }

    protected PolicySpec<?> getPolicySpec(RegisteredType cp) {
        return mgmt.getTypeRegistry().createSpec(cp, null, PolicySpec.class);
    }

    @Test
    public void testMoreEntitiesV1Policy() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_MORE_ENTITIES_V1_PATH);
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        RegisteredType c2 = addCatalogItemWithTypeAsName(
                OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
                TEST_VERSION,
                BROOKLYN_TEST_MORE_ENTITIES_V1_URL);
        
        // test load and instantiate
        Entity me = addItemFromCatalog(c2);

        RegisteredType cp = addCatalogItemWithTypeAsName(
                OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_SIMPLE_POLICY,
                TEST_VERSION,
                BROOKLYN_TEST_OSGI_ENTITIES_URL);
        me.policies().add(getPolicySpec(cp));
        
        Assert.assertEquals(me.policies().size(), 1, "Wrong number of policies: "+MutableList.copyOf(me.policies()));
        
        String catalogItemId = Iterables.getOnlyElement( me.policies() ).getCatalogItemId();
        Assert.assertNotNull(catalogItemId);
        // must be the actual source bundle
        Assert.assertFalse(catalogItemId.equals(me.getCatalogItemId()), "catalog item id is: "+catalogItemId);
        Assert.assertTrue(catalogItemId.equals(CatalogUtils.getVersionedId(OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_SIMPLE_POLICY, TEST_VERSION)), "catalog item id is: "+catalogItemId);
    }

    @Test
    public void testMoreEntitiesV2FailsWithoutBasicTestOsgiEntitiesBundle() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_MORE_ENTITIES_V2_PATH);

        RegisteredType c2 = addCatalogItemWithTypeAsName(
                OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
                TEST_VERSION,
                BROOKLYN_TEST_MORE_ENTITIES_V2_URL);
        
        // test load and instantiate
        try {
            Entity me = addItemFromCatalog(c2);
            Assert.fail("Should have failed, with unresolved dependency; instead got "+me);
        } catch (Exception e) {
            Assert.assertTrue(e.toString().toLowerCase().contains("unresolved constraint"), "Missing expected text in error: "+e);
            Assert.assertTrue(e.toString().toLowerCase().contains("wiring.package"), "Missing expected text in error: "+e);
            Assert.assertTrue(e.toString().toLowerCase().contains("org.apache.brooklyn.test.osgi.entities"), "Missing expected text in error: "+e);
        }
    }
    
    // V2 works with dependency declared, and can load
    // and has policy, with policy item catalog ID is reasonable
    @Test
    public void testMoreEntitiesV2() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_MORE_ENTITIES_V2_PATH);

        RegisteredType c2 = addCatalogItemWithTypeAsName(
                OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
                TEST_VERSION,
                BROOKLYN_TEST_MORE_ENTITIES_V2_URL,
                BROOKLYN_TEST_OSGI_ENTITIES_URL);
        
        // test load and instantiate
        Entity me = addItemFromCatalog(c2);
        Assert.assertEquals(me.getCatalogItemId(), CatalogUtils.getVersionedId(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, TEST_VERSION));
        
        assertV2MethodCall(me);
        assertV2EffectorCall(me);
        Assert.assertEquals(me.policies().size(), 1, "Wrong number of policies: "+MutableList.copyOf(me.policies()));
        
        String catalogItemId = Iterables.getOnlyElement( me.policies() ).getCatalogItemId();
        Assert.assertNotNull(catalogItemId);
        // allow either me's bundle (more) or the actual source bundle
        Assert.assertTrue(catalogItemId.equals(me.getCatalogItemId()) || catalogItemId.startsWith("brooklyn-test-osgi-entities"));
    }

    @Test
    public void testMoreEntitiesV1ThenV2GivesV2() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_MORE_ENTITIES_V1_PATH);
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_MORE_ENTITIES_V2_PATH);

        addCatalogItemWithTypeAsName(
                OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
                TEST_VERSION,
                BROOKLYN_TEST_MORE_ENTITIES_V1_URL);
        addCatalogItemWithTypeAsName(
                OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
                TEST_VERSION,
                BROOKLYN_TEST_MORE_ENTITIES_V2_URL, BROOKLYN_TEST_OSGI_ENTITIES_URL);
        
        // test load and instantiate
        Entity me = addItemFromCatalog( mgmt.getTypeRegistry().get(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, TEST_VERSION) );
        
        assertV2MethodCall(me);
        assertV2EffectorCall(me);
        Assert.assertEquals(me.policies().size(), 1, "Wrong number of policies: "+MutableList.copyOf(me.policies()));
    }

    @Test
    public void testMoreEntitiesV2ThenV1GivesV1() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_MORE_ENTITIES_V1_PATH);
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_MORE_ENTITIES_V2_PATH);

        addCatalogItemWithTypeAsName(
                OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
                TEST_VERSION,
                BROOKLYN_TEST_MORE_ENTITIES_V2_URL, BROOKLYN_TEST_OSGI_ENTITIES_URL);
        addCatalogItemWithTypeAsName(
                OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
                TEST_VERSION,
                BROOKLYN_TEST_MORE_ENTITIES_V1_URL);
        
        // test load and instantiate
        Entity me = addItemFromCatalog( mgmt.getTypeRegistry().get(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, TEST_VERSION) );
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
        Assert.assertEquals(me.policies().size(), 0, "Wrong number of policies: "+MutableList.copyOf(me.policies()));
    }

    @Test
    public void testUnfazedByMoreEntitiesV1AndV2AndV2EvilTwin() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_MORE_ENTITIES_V1_PATH);
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_MORE_ENTITIES_V2_PATH);
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_MORE_ENTITIES_V2_EVIL_TWIN_PATH);

        addCatalogItemWithNameAndType("v1",
                TEST_VERSION,
                OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
                BROOKLYN_TEST_MORE_ENTITIES_V1_URL);
        addCatalogItemWithNameAndType("v2",
                TEST_VERSION,
                OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
                BROOKLYN_TEST_MORE_ENTITIES_V2_URL, BROOKLYN_TEST_OSGI_ENTITIES_URL);
        addCatalogItemWithNameAndType("v2-evil", 
                TEST_VERSION,
                OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY,
                BROOKLYN_TEST_MORE_ENTITIES_V2_EVIL_TWIN_URL, BROOKLYN_TEST_OSGI_ENTITIES_URL);

        // test osgi finding
        
        List<Bundle> bundles = Osgis.bundleFinder(mgmt.getOsgiManager().get().getFramework())
            .symbolicName(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_SYMBOLIC_NAME_FULL).findAll();
        Assert.assertEquals(bundles.size(), 3, "Wrong list of bundles: "+bundles);
        
        Maybe<Bundle> preferredVersion = Osgis.bundleFinder(mgmt.getOsgiManager().get().getFramework())
            .symbolicName(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_SYMBOLIC_NAME_FULL).find();
        Assert.assertTrue(preferredVersion.isPresent());
        Assert.assertEquals(preferredVersion.get().getVersion().toString(), "0.2.0");
        
        Maybe<Bundle> evilVersion = Osgis.bundleFinder(mgmt.getOsgiManager().get().getFramework()).
            preferringFromUrl(BROOKLYN_TEST_MORE_ENTITIES_V2_EVIL_TWIN_URL).find();
        Assert.assertTrue(evilVersion.isPresent());
        Assert.assertEquals(evilVersion.get().getVersion().toString(), "0.2.0");

        // test preferredUrl vs requiredUrl

        Maybe<Bundle> versionIgnoresInvalidPreferredUrl = Osgis.bundleFinder(mgmt.getOsgiManager().get().getFramework())
            .symbolicName(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_SYMBOLIC_NAME_FULL)
            .version(TEST_VERSION)
            .preferringFromUrl(BROOKLYN_TEST_MORE_ENTITIES_V2_EVIL_TWIN_URL).find();
        Assert.assertTrue(versionIgnoresInvalidPreferredUrl.isPresent());
        Assert.assertEquals(versionIgnoresInvalidPreferredUrl.get().getVersion().toString(), TEST_VERSION);

        Maybe<Bundle> versionRespectsInvalidRequiredUrl = Osgis.bundleFinder(mgmt.getOsgiManager().get().getFramework())
            .symbolicName(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_SYMBOLIC_NAME_FULL)
            .version(TEST_VERSION)
            .requiringFromUrl(BROOKLYN_TEST_MORE_ENTITIES_V2_EVIL_TWIN_URL).find();
        Assert.assertFalse(versionRespectsInvalidRequiredUrl.isPresent());

        // test entity resolution
        
        Entity v2 = addItemFromCatalog( mgmt.getTypeRegistry().get("v2", TEST_VERSION) );
        assertV2MethodCall(v2);
        assertV2EffectorCall(v2);
        Assert.assertEquals(v2.policies().size(), 1, "Wrong number of policies: "+MutableList.copyOf(v2.policies()));

        Entity v2_evil = addItemFromCatalog( mgmt.getTypeRegistry().get("v2-evil", TEST_VERSION) );
        assertV2EvilTwinMethodCall(v2_evil);
        assertV2EvilTwinEffectorCall(v2_evil);
        Assert.assertEquals(v2_evil.policies().size(), 1, "Wrong number of policies: "+MutableList.copyOf(v2_evil.policies()));

        Entity v1 = addItemFromCatalog( mgmt.getTypeRegistry().get("v1", TEST_VERSION) );
        assertV1MethodCall(v1);
        assertV1EffectorCall(v1);
        Assert.assertEquals(v1.policies().size(), 0, "Wrong number of policies: "+MutableList.copyOf(v1.policies()));
    }

    // TODO versioning (WIP until #92), install both V1 and V2 with version number, and test that both work
        
}
