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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.entity.rebind.transformer.CompoundTransformer;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.osgi.OsgiTestResources;
import brooklyn.management.osgi.OsgiVersionMoreEntityTest;

public class ActivePartialRebindVersionTest extends RebindTestFixtureWithApp {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(ActivePartialRebindVersionTest.class);
    
    protected LocalManagementContext createOrigManagementContext() {
        return RebindTestUtils.managementContextBuilder(mementoDir, classLoader)
                .persistPeriodMillis(getPersistPeriodMillis())
                .forLive(useLiveManagementContext())
                .emptyCatalog(useEmptyCatalog())
                .enableOsgi(true)
                .buildStarted();
    }
    
    protected void doPartialRebindByObjectById(CompoundTransformer transformer, String ...objectsToRebindIds) {
        RebindManagerImpl rm = (RebindManagerImpl) origManagementContext.getRebindManager();
        rm.rebindPartialActive(transformer, objectsToRebindIds);        
    }
    
    @Test
    public void testSwitchingVersions() throws Exception {
        CatalogItem<?, ?> catV1 = OsgiVersionMoreEntityTest.addMoreEntityV1(origManagementContext, "1.0");
        Entity childV1 = OsgiVersionMoreEntityTest.addItemFromCatalog(origManagementContext, origApp, catV1);
        
        OsgiVersionMoreEntityTest.assertV1EffectorCall(childV1);
        
        // simply adding to catalog doesn't change
        CatalogItem<?, ?> catV2 = OsgiVersionMoreEntityTest.addMoreEntityV2(origManagementContext, "1.1");
        OsgiVersionMoreEntityTest.assertV1EffectorCall(childV1);
        Entity child2V2 = OsgiVersionMoreEntityTest.addItemFromCatalog(origManagementContext, origApp, catV2);
        OsgiVersionMoreEntityTest.assertV2EffectorCall(child2V2);
        
        // now transform, with a version change
        CompoundTransformer transformer = CompoundTransformer.builder().changeCatalogItemId(
            OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, "1.0",
            OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY, "1.1").build();
        doPartialRebindByObjectById(transformer, childV1.getId());

        Entity childV2 = origManagementContext.lookup(childV1.getId(), Entity.class);
        OsgiVersionMoreEntityTest.assertV2EffectorCall(childV2);
        
        // _v1_ child also points to new implementation -- saying HI
        OsgiVersionMoreEntityTest.assertV2EffectorCall(childV1);

        // (in fact they are the same)
        Assert.assertTrue(childV1==childV2, "Expected same instance: "+childV1+" / "+childV2);
    }

}
