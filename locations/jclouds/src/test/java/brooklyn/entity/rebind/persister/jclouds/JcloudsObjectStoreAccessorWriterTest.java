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

import org.apache.brooklyn.management.ha.HighAvailabilityMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessorWithLock;
import brooklyn.entity.rebind.persister.PersistenceStoreObjectAccessorWriterTestFixture;
import brooklyn.entity.rebind.persister.StoreObjectAccessorLocking;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Urls;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Duration;

@Test(groups={"Live", "Live-sanity"})
public class JcloudsObjectStoreAccessorWriterTest extends PersistenceStoreObjectAccessorWriterTestFixture {

    private static final Logger log = LoggerFactory.getLogger(JcloudsObjectStoreAccessorWriterTest.class);
    
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
        return newPersistenceStoreObjectAccessor(store, "");
    }
    protected StoreObjectAccessorWithLock newPersistenceStoreObjectAccessor(JcloudsBlobStoreBasedObjectStore aStore, String prefix) throws IOException {
        return new StoreObjectAccessorLocking(aStore.newAccessor(prefix+"sample-file-"+Identifiers.makeRandomId(4)));
    }

    @Override
    protected Duration getLastModifiedResolution() {
        // Not sure what timing resolution is on things like Softlayer's Swift.
        // It passed for Aled repeatedly on 2014-11-05 with 2 seconds.
        return Duration.seconds(2);
    }
    
    protected int biggishSize() {
        // bit smaller since it's actually uploading here!
        return 10000;
    }

    /** Tests what happen when we ask the store to be in a container with a path, e.g. path1/path2 
     * and then the accessor to a file within that (path3/file) --
     * this does it an emulated way, where the store tracks the subpath so we don't have to */
    @Test(groups={"Live"})
    public void testNestedPath1() throws IOException {
        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
        String path1 = BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4);
        String path2 = BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4);
        String path3 = BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4);
        JcloudsBlobStoreBasedObjectStore store0 = null;
        try {
            store0 = new JcloudsBlobStoreBasedObjectStore(BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, Urls.mergePaths(path1, path2));
            store0.injectManagementContext(mgmt);
            store0.prepareForSharedUse(PersistMode.CLEAN, HighAvailabilityMode.DISABLED);

            newPersistenceStoreObjectAccessor(store0, path3+"/").put("hello world");
        } catch (Exception e) {
            log.warn("Failed with: "+e, e);
            throw Exceptions.propagate(e);
            
        } finally {
            store0.deleteCompletely();
            
            JcloudsBlobStoreBasedObjectStore storeD = new JcloudsBlobStoreBasedObjectStore(BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, path1);
            storeD.injectManagementContext(mgmt);
            storeD.prepareForSharedUse(PersistMode.CLEAN, HighAvailabilityMode.DISABLED);
            storeD.deleteCompletely();
        }
    }

    /** Tests what happen when we ask the store to be in a container with a path, e.g. path1/path2 
     * and then the accessor to a file within that (path3/file) --
     * this does it the "official" way, where we ask for the store's container
     * to be the first path segment */
    @Test(groups={"Live"})
    public void testNestedPath2() throws IOException {
        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
        String path1 = BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4);
        String path2 = BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4);
        String path3 = BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4);
        JcloudsBlobStoreBasedObjectStore store1 = null, store2 = null;
        try {
            store1 = new JcloudsBlobStoreBasedObjectStore(BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, 
                path1);
            store1.injectManagementContext(mgmt);
            store1.prepareForSharedUse(PersistMode.CLEAN, HighAvailabilityMode.DISABLED);
            store1.createSubPath(path2);
            newPersistenceStoreObjectAccessor(store1, path2+"/"+path3+"/").put("hello world");
            
            store2 = new JcloudsBlobStoreBasedObjectStore(BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, 
                Urls.mergePaths(path1, path2));
            store2.injectManagementContext(mgmt);
            store2.prepareForSharedUse(PersistMode.CLEAN, HighAvailabilityMode.DISABLED);

            newPersistenceStoreObjectAccessor(store2, path3+"/").put("hello world");
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // this doesn't work
//            store2.deleteCompletely();
            // this is how you have to do it:
            store1.newAccessor(path2).delete();
            
            store1.deleteCompletely();
        }
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
    
    @Test(groups={"Live", "Live-sanity"})
    @Override
    public void testExists() throws Exception {
        super.testExists();
    }
    
    @Test(groups={"Live", "Live-sanity"})
    @Override
    public void testAppendsFile() throws Exception {
        super.testAppendsFile();
    }
}
