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
package brooklyn.entity.rebind.persister.jclouds;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.management.ha.HighAvailabilityManagerTestFixture;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.text.Identifiers;

@Test(groups={"Live", "Live-sanity"})
public class HighAvailabilityManagerJcloudsObjectStoreTest extends HighAvailabilityManagerTestFixture {

    protected ManagementContextInternal newLocalManagementContext() {
        return new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
    }

    @Override @BeforeMethod
    public void setUp() throws Exception { super.setUp(); }
    
    protected PersistenceObjectStore newPersistenceObjectStore() {
        return new JcloudsBlobStoreBasedObjectStore(
            BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4));
    }

    @Test(groups="Live", invocationCount=5) //run fewer times w softlayer... 
    public void testGetManagementPlaneStatusManyTimes() throws Exception {
        testGetManagementPlaneStatus();
    }
    
    @Test(groups={"Live", "Live-sanity"})
    @Override
    public void testDoesNotPromoteIfMasterTimeoutNotExpired() throws Exception {
        super.testDoesNotPromoteIfMasterTimeoutNotExpired();
    }
    
    @Test(groups={"Live", "Live-sanity"})
    @Override
    public void testGetManagementPlaneStatus() throws Exception {
        super.testGetManagementPlaneStatus();
    }
    
    @Test(groups={"Live", "Live-sanity"})
    @Override
    public void testPromotes() throws Exception {
        super.testPromotes();
    }

    @Test(groups={"Live", "Live-sanity"})
    @Override
    public void testGetManagementPlaneSyncStateInfersTimedOutNodeAsFailed() throws Exception {
        super.testGetManagementPlaneSyncStateInfersTimedOutNodeAsFailed();
    }
    
    @Test(groups={"Live", "Live-sanity"})
    @Override
    public void testGetManagementPlaneSyncStateDoesNotThrowNpeBeforePersisterSet() throws Exception {
        super.testGetManagementPlaneSyncStateDoesNotThrowNpeBeforePersisterSet();
    }
}
