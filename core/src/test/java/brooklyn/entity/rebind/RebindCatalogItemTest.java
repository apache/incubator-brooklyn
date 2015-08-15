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
package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import io.brooklyn.camp.BasicCampPlatform;
import io.brooklyn.camp.test.mock.web.MockWebPlatform;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.camp.lite.CampPlatformWithJustBrooklynMgmt;
import brooklyn.camp.lite.TestAppAssemblyInstantiator;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogItemType;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.management.ManagementContext;
import org.apache.brooklyn.core.management.internal.LocalManagementContext;
import org.apache.brooklyn.test.entity.TestEntity;

import brooklyn.catalog.internal.BasicBrooklynCatalog;
import brooklyn.catalog.internal.CatalogDto;
import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.internal.BrooklynFeatureEnablement;

import org.apache.brooklyn.location.basic.LocalhostMachineProvisioningLocation;

import brooklyn.policy.basic.AbstractPolicy;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class RebindCatalogItemTest extends RebindTestFixtureWithApp {

    private static final String TEST_VERSION = "0.0.1";

    private static final Logger LOG = LoggerFactory.getLogger(RebindCatalogItemTest.class);
    public static class MyPolicy extends AbstractPolicy {}
    private boolean catalogPersistenceWasEnabled;

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        catalogPersistenceWasEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_CATALOG_PERSISTENCE_PROPERTY);
        BrooklynFeatureEnablement.enable(BrooklynFeatureEnablement.FEATURE_CATALOG_PERSISTENCE_PROPERTY);
        super.setUp();
        BasicCampPlatform platform = new CampPlatformWithJustBrooklynMgmt(origManagementContext);
        MockWebPlatform.populate(platform, TestAppAssemblyInstantiator.class);
        origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
    }

    @AfterMethod(alwaysRun = true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        BrooklynFeatureEnablement.setEnablement(BrooklynFeatureEnablement.FEATURE_CATALOG_PERSISTENCE_PROPERTY, catalogPersistenceWasEnabled);
    }

    @Override
    protected LocalManagementContext createOrigManagementContext() {
        BrooklynProperties properties = BrooklynProperties.Factory.newDefault();
        properties.put(BrooklynServerConfig.BROOKLYN_CATALOG_URL, "classpath://brooklyn/entity/rebind/rebind-catalog-item-test-catalog.xml");
        properties.put(BrooklynServerConfig.CATALOG_LOAD_MODE, brooklyn.catalog.CatalogLoadMode.LOAD_BROOKLYN_CATALOG_URL);
        return RebindTestUtils.managementContextBuilder(mementoDir, classLoader)
                .properties(properties)
                .persistPeriodMillis(getPersistPeriodMillis())
                .forLive(useLiveManagementContext())
                .buildStarted();
    }

    @Override
    protected LocalManagementContext createNewManagementContext(File mementoDir) {
        BrooklynProperties properties = BrooklynProperties.Factory.newDefault();
        properties.put(BrooklynServerConfig.BROOKLYN_CATALOG_URL, "classpath://brooklyn/entity/rebind/rebind-catalog-item-test-catalog.xml");
        return RebindTestUtils.managementContextBuilder(mementoDir, classLoader)
                .properties(properties)
                .forLive(useLiveManagementContext())
                .emptyCatalog(useEmptyCatalog())
                .buildUnstarted();
    }

    @Test
    public void testPersistsEntityFromCatalogXml() {
        assertEquals(Iterables.size(origManagementContext.getCatalog().getCatalogItems()), 1);
        rebindAndAssertCatalogsAreEqual();
    }

    @Test
    public void testAddAndRebindEntity() throws Exception {
        String yaml = 
                "name: rebind-yaml-catalog-item-test\n" +
                "brooklyn.catalog:\n" +
                "  version: " + TEST_VERSION + "\n" +
                "services:\n" +
                "- type: io.camp.mock:AppServer";
        addItem(origManagementContext, yaml);
        rebindAndAssertCatalogsAreEqual();
    }

    @Test(enabled = false)
    public void testAddAndRebindTemplate() {
        // todo: could use (deprecated, perhaps wrongly) BBC.addItem(Class/CatalogItem)
        fail("Unimplemented because the catalogue does not currently distinguish between application templates and entities");
    }

    @Test
    public void testAddAndRebindPolicy() {
        // Doesn't matter that SamplePolicy doesn't exist
        String yaml = "name: Test Policy\n" +
                "brooklyn.catalog:\n" +
                "  id: sample_policy\n" +
                "  version: " + TEST_VERSION + "\n" +
                "brooklyn.policies: \n" +
                "- type: brooklyn.entity.rebind.RebindCatalogItemTest$MyPolicy\n" +
                "  brooklyn.config:\n" +
                "    cfg1: 111\n" +
                "    cfg2: 222";
        addItem(origManagementContext, yaml);
        rebindAndAssertCatalogsAreEqual();
    }

    @Test
    public void testAddAndRebindAndDeleteLocation() {
        String yaml = Joiner.on("\n").join(ImmutableList.of(
                "name: Test Location",
                "brooklyn.catalog:",
                "  id: sample_location",
                "  version: " + TEST_VERSION,
                "brooklyn.locations:",
                "- type: "+LocalhostMachineProvisioningLocation.class.getName(),
                "  brooklyn.config:",
                "    cfg1: 111",
                "    cfg2: 222"));
        CatalogItem<?, ?> added = addItem(origManagementContext, yaml);
        assertEquals(added.getCatalogItemType(), CatalogItemType.LOCATION);
        rebindAndAssertCatalogsAreEqual();
        
        deleteItem(newManagementContext, added.getSymbolicName(), added.getVersion());
        
        switchOriginalToNewManagementContext();
        rebindAndAssertCatalogsAreEqual();
    }

    @Test(enabled = false)
    public void testAddAndRebindEnricher() {
        fail("unimplemented");
    }

    @Test(invocationCount = 3)
    public void testDeletedCatalogItemIsNotPersisted() {
        assertEquals(Iterables.size(origManagementContext.getCatalog().getCatalogItems()), 1);
        CatalogItem<Object, Object> toRemove = Iterables.getOnlyElement(origManagementContext.getCatalog().getCatalogItems());
        // Must make sure that the original catalogue item is not managed and unmanaged in the same
        // persistence window. Because BrooklynMementoPersisterToObjectStore applies writes/deletes
        // asynchronously the winner is down to a race and the test might pass or fail.
        origManagementContext.getRebindManager().forcePersistNow(false, null);
        origManagementContext.getCatalog().deleteCatalogItem(toRemove.getSymbolicName(), toRemove.getVersion());
        assertEquals(Iterables.size(origManagementContext.getCatalog().getCatalogItems()), 0);
        rebindAndAssertCatalogsAreEqual();
        assertEquals(Iterables.size(newManagementContext.getCatalog().getCatalogItems()), 0);
    }

    @Test(invocationCount = 3)
    public void testCanTagCatalogItemAfterRebind() {
        assertEquals(Iterables.size(origManagementContext.getCatalog().getCatalogItems()), 1);
        CatalogItem<Object, Object> toTag = Iterables.getOnlyElement(origManagementContext.getCatalog().getCatalogItems());
        final String tag = "tag1";
        toTag.tags().addTag(tag);
        assertTrue(toTag.tags().containsTag(tag));

        rebindAndAssertCatalogsAreEqual();

        toTag = Iterables.getOnlyElement(newManagementContext.getCatalog().getCatalogItems());
        assertTrue(toTag.tags().containsTag(tag));
        toTag.tags().removeTag(tag);
    }

    @Test
    public void testSameCatalogItemIdRemovalAndAdditionRebinds() throws Exception {
        //The test is not reliable on Windows (doesn't catch the pre-fix problem) -
        //the store is unable to delete still locked files so the bug doesn't manifest.
        //TODO investigate if locked files not caused by unclosed streams!
        String yaml = 
                "name: rebind-yaml-catalog-item-test\n" +
                "brooklyn.catalog:\n" +
                "  version: " + TEST_VERSION + "\n" +
                "services:\n" +
                "- type: io.camp.mock:AppServer";
        addItem(origManagementContext, yaml);
        
        BasicBrooklynCatalog catalog = (BasicBrooklynCatalog) origManagementContext.getCatalog();
        String catalogXml = catalog.toXmlString();
        catalog.reset(CatalogDto.newDtoFromXmlContents(catalogXml, "Test reset"));
        rebindAndAssertCatalogsAreEqual();
    }

    @Test
    public void testRebindAfterItemDeprecated() {
        String yaml =
                "name: rebind-yaml-catalog-item-test\n" +
                "brooklyn.catalog:\n" +
                "  version: " + TEST_VERSION + "\n" +
                "services:\n" +
                "- type: io.camp.mock:AppServer";
        CatalogItem<?, ?> catalogItem = addItem(origManagementContext, yaml);
        assertNotNull(catalogItem, "catalogItem");
        BasicBrooklynCatalog catalog = (BasicBrooklynCatalog) origManagementContext.getCatalog();
        
        catalogItem.setDeprecated(true);
        catalog.persist(catalogItem);
        rebindAndAssertCatalogsAreEqual();
        CatalogItem<?, ?> catalogItemAfterRebind = newManagementContext.getCatalog().getCatalogItem("rebind-yaml-catalog-item-test", TEST_VERSION);
        assertTrue(catalogItemAfterRebind.isDeprecated(), "Expected item to be deprecated");
    }

    protected CatalogItem<?, ?> addItem(ManagementContext mgmt, String yaml) {
        CatalogItem<?, ?> added = Iterables.getOnlyElement(mgmt.getCatalog().addItems(yaml));
        LOG.info("Added item to catalog: {}, id={}", added, added.getId());
        assertCatalogContains(mgmt.getCatalog(), added);
        return added;
    }
    
    protected void deleteItem(ManagementContext mgmt, String symbolicName, String version) {
        mgmt.getCatalog().deleteCatalogItem(symbolicName, version);
        LOG.info("Deleted item from catalog: {}:{}", symbolicName, version);
        assertCatalogDoesNotContain(mgmt.getCatalog(), symbolicName, version);
    }
    
    private void rebindAndAssertCatalogsAreEqual() {
        try {
            rebind();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        assertCatalogsEqual(newManagementContext.getCatalog(), origManagementContext.getCatalog());
    }
}
