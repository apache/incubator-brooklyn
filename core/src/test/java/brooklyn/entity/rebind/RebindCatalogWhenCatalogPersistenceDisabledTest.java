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

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.core.config.BrooklynProperties;
import org.apache.brooklyn.core.config.BrooklynServerConfig;
import org.apache.brooklyn.core.internal.BrooklynFeatureEnablement;
import org.apache.brooklyn.core.management.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.camp.brooklyn.lite.CampPlatformWithJustBrooklynMgmt;
import org.apache.brooklyn.test.entity.TestEntity;

import com.google.common.collect.Iterables;

public class RebindCatalogWhenCatalogPersistenceDisabledTest extends RebindTestFixtureWithApp {

    private static final String TEST_CATALOG = "classpath://brooklyn/entity/rebind/rebind-catalog-item-test-catalog.xml";
    private boolean catalogPersistenceWasPreviouslyEnabled;

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        catalogPersistenceWasPreviouslyEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_CATALOG_PERSISTENCE_PROPERTY);
        BrooklynFeatureEnablement.disable(BrooklynFeatureEnablement.FEATURE_CATALOG_PERSISTENCE_PROPERTY);
        super.setUp();
        new CampPlatformWithJustBrooklynMgmt(origManagementContext);
        origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
    }

    @AfterMethod(alwaysRun = true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        BrooklynFeatureEnablement.setEnablement(BrooklynFeatureEnablement.FEATURE_CATALOG_PERSISTENCE_PROPERTY, catalogPersistenceWasPreviouslyEnabled);
    }

    @Override
    protected LocalManagementContext createOrigManagementContext() {
        BrooklynProperties properties = BrooklynProperties.Factory.newDefault();
        properties.put(BrooklynServerConfig.BROOKLYN_CATALOG_URL, TEST_CATALOG);
        return RebindTestUtils.managementContextBuilder(mementoDir, classLoader)
                .properties(properties)
                .persistPeriodMillis(getPersistPeriodMillis())
                .forLive(useLiveManagementContext())
                .buildStarted();
    }

    @Override
    protected LocalManagementContext createNewManagementContext(File mementoDir) {
        BrooklynProperties properties = BrooklynProperties.Factory.newDefault();
        properties.put(BrooklynServerConfig.BROOKLYN_CATALOG_URL, TEST_CATALOG);
        return RebindTestUtils.managementContextBuilder(mementoDir, classLoader)
                .properties(properties)
                .forLive(useLiveManagementContext())
                .buildUnstarted();
    }

    @Test
    public void testModificationsToCatalogAreNotPersistedWhenCatalogPersistenceFeatureIsDisabled() throws Exception {
        assertEquals(Iterables.size(origManagementContext.getCatalog().getCatalogItems()), 1);
        CatalogItem<Object, Object> toRemove = Iterables.getOnlyElement(origManagementContext.getCatalog().getCatalogItems());
        origManagementContext.getCatalog().deleteCatalogItem(toRemove.getSymbolicName(), toRemove.getVersion());
        assertEquals(Iterables.size(origManagementContext.getCatalog().getCatalogItems()), 0);

        rebind();

        assertEquals(Iterables.size(newManagementContext.getCatalog().getCatalogItems()), 1);
        assertCatalogItemsEqual(Iterables.getOnlyElement(newManagementContext.getCatalog().getCatalogItems()), toRemove);
    }

}
