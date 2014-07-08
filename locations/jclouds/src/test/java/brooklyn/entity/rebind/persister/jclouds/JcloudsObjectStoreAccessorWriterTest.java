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

import java.io.IOException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessorWithLock;
import brooklyn.entity.rebind.persister.PersistenceStoreObjectAccessorWriterTestFixture;
import brooklyn.entity.rebind.persister.StoreObjectAccessorLocking;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.text.Identifiers;

@Test(groups={"Live", "Live-sanity"})
public class JcloudsObjectStoreAccessorWriterTest extends PersistenceStoreObjectAccessorWriterTestFixture {

    private JcloudsBlobStoreBasedObjectStore store;
    private LocalManagementContextForTests mgmt;

    @Override @BeforeMethod
    public void setUp() throws Exception {
        store = new JcloudsBlobStoreBasedObjectStore(
            BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4));
        store.injectManagementContext(mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault()));
        store.prepareForSharedUse(PersistMode.CLEAN, HighAvailabilityMode.DISABLED);
        super.setUp();
    }

    @Override @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        super.tearDown();
        if (mgmt!=null) Entities.destroyAll(mgmt);
        if (store!=null) store.deleteCompletely();
    }
    
    protected StoreObjectAccessorWithLock newPersistenceStoreObjectAccessor() throws IOException {
        return new StoreObjectAccessorLocking(store.newAccessor("sample-file-"+Identifiers.makeRandomId(4)));
    }

    protected int biggishSize() {
        // bit smaller since it's actually uploading here!
        return 10000;
    }
    
    @Test(groups={"Live", "Live-sanity"})
    @Override
    public void testWriteBacklogThenDeleteWillLeaveFileDeleted() throws Exception {
        super.testWriteBacklogThenDeleteWillLeaveFileDeleted();
    }
    
    @Test(groups={"Live", "Live-sanity"})
    @Override
    public void testWritesFile() throws Exception {
        super.testWritesFile();
    }

    @Test(groups={"Live", "Live-sanity"})
    @Override
    public void testLastModifiedTime() throws Exception {
        super.testLastModifiedTime();
    }
}
