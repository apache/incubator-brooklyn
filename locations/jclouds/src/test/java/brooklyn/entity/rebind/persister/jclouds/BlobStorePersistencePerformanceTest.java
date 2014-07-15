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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessor;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.qa.performance.AbstractPerformanceTest;
import brooklyn.util.text.Identifiers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class BlobStorePersistencePerformanceTest extends AbstractPerformanceTest {

    public static final String LOCATION_SPEC = "named:brooklyn-jclouds-objstore-test-1";
    
    JcloudsBlobStoreBasedObjectStore objectStore;
    StoreObjectAccessor blobstoreAccessor;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();

        objectStore = new JcloudsBlobStoreBasedObjectStore(LOCATION_SPEC, "BlobStorePersistencePerformanceTest");
        objectStore.injectManagementContext(mgmt);
        objectStore.prepareForSharedUse(PersistMode.AUTO, HighAvailabilityMode.AUTO);
        blobstoreAccessor = objectStore.newAccessor(Identifiers.makeRandomId(8));
        
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (blobstoreAccessor != null) blobstoreAccessor.delete();
        if (objectStore != null) {
            objectStore.deleteCompletely();
            objectStore.close();
        }
    }
    
    protected int numIterations() {
        return 100;
    }
    
     @Test(groups={"Live", "Acceptance"})
     public void testStoreObjectPuts() throws Exception {
         int numIterations = numIterations();
         double minRatePerSec = 10 * PERFORMANCE_EXPECTATION;
         final AtomicInteger i = new AtomicInteger();
         
         measureAndAssert("StoreObjectAccessor.put", numIterations, minRatePerSec, new Runnable() {
             public void run() {
                 blobstoreAccessor.put(""+i.incrementAndGet());
             }});
     }
 
     @Test(groups={"Live", "Acceptance"})
     public void testStoreObjectGet() throws Exception {
         // The file system will have done a lot of caching here - we are unlikely to touch the disk more than once.
         int numIterations = numIterations();
         double minRatePerSec = 10 * PERFORMANCE_EXPECTATION;

         measureAndAssert("FileBasedStoreObjectAccessor.get", numIterations, minRatePerSec, new Runnable() {
             public void run() {
                 blobstoreAccessor.get();
             }});
     }
 
     @Test(groups={"Live", "Acceptance"})
     public void testStoreObjectDelete() throws Exception {
         int numIterations = numIterations();
         double minRatePerSec = 10 * PERFORMANCE_EXPECTATION;

         // Will do 10% warm up runs first
         final List<StoreObjectAccessor> blobstoreAccessors = Lists.newArrayList();
         for (int i = 0; i < (numIterations * 1.1 + 1); i++) {
             blobstoreAccessors.add(objectStore.newAccessor("storeObjectDelete-"+i));
         }
         
         final AtomicInteger i = new AtomicInteger();

         try {
             measureAndAssert("FileBasedStoreObjectAccessor.delete", numIterations, minRatePerSec, new Runnable() {
                 public void run() {
                     StoreObjectAccessor blobstoreAccessor = blobstoreAccessors.get(i.getAndIncrement());
                     blobstoreAccessor.delete();
                 }});
         } finally {
             for (StoreObjectAccessor blobstoreAccessor : blobstoreAccessors) {
                 blobstoreAccessor.delete();
             }
         }
     }
}
