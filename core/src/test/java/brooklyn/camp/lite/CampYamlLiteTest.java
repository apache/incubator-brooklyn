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
package brooklyn.camp.lite;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.pdp.PdpYamlTest;
import io.brooklyn.camp.test.mock.web.MockWebPlatform;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.catalog.internal.BasicBrooklynCatalog;
import brooklyn.catalog.internal.CatalogDto;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.effector.AddChildrenEffector;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.Task;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.osgi.OsgiStandaloneTest;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.stream.Streams;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/** Tests of lightweight CAMP integration. Since the "real" integration is in brooklyn-camp project,
 * but some aspects of CAMP we want to be able to test here. */
public class CampYamlLiteTest {

    private static final Logger log = LoggerFactory.getLogger(CampYamlLiteTest.class);
    
    protected LocalManagementContext mgmt;
    protected CampPlatformWithJustBrooklynMgmt platform;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        mgmt = LocalManagementContextForTests.newInstanceWithOsgi();
        platform = new CampPlatformWithJustBrooklynMgmt(mgmt);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (mgmt!=null) mgmt.terminate();
    }
    
    /** based on {@link PdpYamlTest} for parsing,
     * then creating a {@link TestAppAssembly} */
    @Test
    public void testYamlServiceMatchAndBrooklynInstantiate() throws Exception {
        MockWebPlatform.populate(platform, TestAppAssemblyInstantiator.class);
        
        Reader input = new InputStreamReader(getClass().getResourceAsStream("test-app-service-blueprint.yaml"));
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);
        log.info("AT is:\n"+at.toString());
        Assert.assertEquals(at.getName(), "sample");
        Assert.assertEquals(at.getPlatformComponentTemplates().links().size(), 1);
        
        // now use brooklyn to instantiate - note it won't be faithful, but it will set some config keys
        Assembly assembly = at.getInstantiator().newInstance().instantiate(at, platform);
        
        TestApplication app = ((TestAppAssembly)assembly).getBrooklynApp();
        Assert.assertEquals( app.getConfig(TestEntity.CONF_NAME), "sample" );
        Map<String, String> map = app.getConfig(TestEntity.CONF_MAP_THING);
        Assert.assertEquals( map.get("desc"), "Tomcat sample JSP and servlet application." );
        
        Assert.assertEquals( app.getChildren().size(), 1 );
        Entity svc = Iterables.getOnlyElement(app.getChildren());
        Assert.assertEquals( svc.getConfig(TestEntity.CONF_NAME), "Hello WAR" );
        map = svc.getConfig(TestEntity.CONF_MAP_THING);
        Assert.assertEquals( map.get("type"), MockWebPlatform.APPSERVER.getType() );
        // desc ensures we got the information from the matcher, as this value is NOT in the yaml
        Assert.assertEquals( map.get("desc"), MockWebPlatform.APPSERVER.getDescription() );
    }

    /** based on {@link PdpYamlTest} for parsing,
     * then creating a {@link TestAppAssembly} */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testAddChildrenEffector() throws Exception {
        // NB: as above, with this mock instantiator, not everything will be set nicely
        MockWebPlatform.populate(platform, TestAppAssemblyInstantiator.class);
        
        String childYaml = Streams.readFullyString(getClass().getResourceAsStream("test-app-service-blueprint.yaml"));
        AddChildrenEffector newEff = new AddChildrenEffector(ConfigBag.newInstance()
            .configure(AddChildrenEffector.EFFECTOR_NAME, "add_tomcat")
            .configure(AddChildrenEffector.BLUEPRINT_YAML, childYaml)
            .configure(AddChildrenEffector.EFFECTOR_PARAMETER_DEFS, MutableMap.of("war", (Object)MutableMap.of(
                "defaultValue", "foo.war"))) ) ;
        TestApplication app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).addInitializer(newEff), mgmt);

        // test adding, with a parameter
        Task<List> task = app.invoke(Effectors.effector(List.class, "add_tomcat").buildAbstract(), MutableMap.of("war", "foo.bar"));
        List result = task.get();
        
        Entity newChild = Iterables.getOnlyElement(app.getChildren());
        Assert.assertEquals(newChild.getConfig(ConfigKeys.newStringConfigKey("war")), "foo.bar");
        
        Assert.assertEquals(Iterables.getOnlyElement(result), newChild.getId());
        Entities.unmanage(newChild);
        
        // and test default value
        task = app.invoke(Effectors.effector(List.class, "add_tomcat").buildAbstract(), MutableMap.<String,Object>of());
        result = task.get();
        
        newChild = Iterables.getOnlyElement(app.getChildren());
        Assert.assertEquals(newChild.getConfig(ConfigKeys.newStringConfigKey("war")), "foo.war");
        
        Assert.assertEquals(Iterables.getOnlyElement(result), newChild.getId());
        Entities.unmanage(newChild);
    }

    @Test
    public void testYamlServiceForCatalog() {
        MockWebPlatform.populate(platform, TestAppAssemblyInstantiator.class);
        
        CatalogItem<?, ?> realItem = mgmt.getCatalog().addItem(Streams.readFullyString(getClass().getResourceAsStream("test-app-service-blueprint.yaml")));
        Iterable<CatalogItem<Object, Object>> retrievedItems = mgmt.getCatalog()
                .getCatalogItems(CatalogPredicates.registeredType(Predicates.equalTo("catalog-name")));
        
        Assert.assertEquals(Iterables.size(retrievedItems), 1, "Wrong retrieved items: "+retrievedItems);
        CatalogItem<Object, Object> retrievedItem = Iterables.getOnlyElement(retrievedItems);
        Assert.assertEquals(retrievedItem, realItem);

        Set<String> expectedBundles = Sets.newHashSet(OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL);
        Assert.assertEquals(retrievedItem.getLibraries().getBundles(), expectedBundles);
        // Assert.assertEquals(retrievedItem.getVersion(), "0.9");

        EntitySpec<?> spec1 = (EntitySpec<?>) mgmt.getCatalog().createSpec(retrievedItem);
        assertNotNull(spec1);
        Assert.assertEquals(spec1.getConfig().get(TestEntity.CONF_NAME), "sample");
        
        // TODO other assertions, about children
    }

    @Test
    public void testRegisterCustomEntityWithBundleWhereEntityIsFromCoreAndIconFromBundle() throws IOException {
        String registeredTypeName = "my.catalog.app.id";
        String bundleUrl = OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL;
        String yaml = getSampleMyCatalogAppYaml(registeredTypeName, bundleUrl);

        mgmt.getCatalog().addItem(yaml);

        assertMgmtHasSampleMyCatalogApp(registeredTypeName, bundleUrl);
    }

    @Test
    public void testResetXmlWithCustomEntity() throws IOException {
        String registeredTypeName = "my.catalog.app.id";
        String bundleUrl = OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL;
        String yaml = getSampleMyCatalogAppYaml(registeredTypeName, bundleUrl);

        LocalManagementContext mgmt2 = LocalManagementContextForTests.newInstanceWithOsgi();
        try {
            CampPlatformWithJustBrooklynMgmt platform2 = new CampPlatformWithJustBrooklynMgmt(mgmt2);
            MockWebPlatform.populate(platform2, TestAppAssemblyInstantiator.class);

            mgmt2.getCatalog().addItem(yaml);
            String xml = ((BasicBrooklynCatalog) mgmt2.getCatalog()).toXmlString();
            ((BasicBrooklynCatalog) mgmt.getCatalog()).reset(CatalogDto.newDtoFromXmlContents(xml, "copy of temporary catalog"));
        } finally {
            mgmt2.terminate();
        }

        assertMgmtHasSampleMyCatalogApp(registeredTypeName, bundleUrl);
    }

    private String getSampleMyCatalogAppYaml(String registeredTypeName, String bundleUrl) {
        return "brooklyn.catalog:\n" +
                "  id: " + registeredTypeName + "\n" +
                "  name: My Catalog App\n" +
                "  description: My description\n" +
                "  icon_url: classpath:/brooklyn/osgi/tests/icon.gif\n" +
                "  version: 0.1.2\n" +
                "  libraries:\n" +
                "  - url: " + bundleUrl + "\n" +
                "\n" +
                "services:\n" +
                "- type: brooklyn.test.entity.TestEntity\n";
    }

    private void assertMgmtHasSampleMyCatalogApp(String registeredTypeName, String bundleUrl) {
        CatalogItem<?, ?> item = mgmt.getCatalog().getCatalogItem(registeredTypeName);
        assertNotNull(item, "failed to load item with id=" + registeredTypeName + " from catalog. Entries were: " +
                Joiner.on(",").join(mgmt.getCatalog().getCatalogItems()));
        assertEquals(item.getRegisteredTypeName(), registeredTypeName);

        // stored as yaml, not java
//      assertEquals(entityItem.getJavaType(), "brooklyn.test.entity.TestEntity");
        assertNotNull(item.getPlanYaml());
        Assert.assertTrue(item.getPlanYaml().contains("brooklyn.test.entity.TestEntity"));

        assertEquals(item.getId(), registeredTypeName);

        // and let's check we have libraries
        List<String> libs = item.getLibraries().getBundles();
        assertEquals(libs, MutableList.of(bundleUrl));

        // now let's check other things on the item
        assertEquals(item.getName(), "My Catalog App");
        assertEquals(item.getDescription(), "My description");
        assertEquals(item.getIconUrl(), "classpath:/brooklyn/osgi/tests/icon.gif");

        // and confirm we can resolve ICON
        byte[] iconData = Streams.readFully(ResourceUtils.create(item.newClassLoadingContext(mgmt)).getResourceFromUrl(item.getIconUrl()));
        assertEquals(iconData.length, 43);
    }

}
