/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package brooklyn.entity.rebind.persister.jclouds;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.qa.performance.EntityPersistencePerformanceTest;

public class EntityToBlobStorePersistencePerformanceTest extends EntityPersistencePerformanceTest {

    private static final String LOCATION_SPEC = BlobStorePersistencePerformanceTest.LOCATION_SPEC;
    
    private JcloudsBlobStoreBasedObjectStore objectStore;

    @Override
    protected LocalManagementContext createOrigManagementContext() {
        objectStore = new JcloudsBlobStoreBasedObjectStore(LOCATION_SPEC, "EntityToBlobStorePersistencePerformanceTest");
        
        return RebindTestUtils.managementContextBuilder(classLoader, objectStore)
                .forLive(true)
                .persistPeriodMillis(getPersistPeriodMillis())
                .buildStarted();
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (objectStore != null) {
            objectStore.deleteCompletely();
            objectStore.close();
        }
    }

    @Test(groups="Live")
    @Override
    public void testManyEntities() throws Exception {
        super.testManyEntities();
    }
    
    @Test(groups="Live")
    @Override
    public void testRapidChanges() throws Exception {
        super.testRapidChanges();
    }
}
