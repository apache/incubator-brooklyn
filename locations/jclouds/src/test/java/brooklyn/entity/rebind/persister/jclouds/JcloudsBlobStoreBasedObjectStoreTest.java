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


import java.util.List;

import org.apache.brooklyn.core.management.internal.LocalManagementContext;
import org.apache.brooklyn.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.rebind.persister.BrooklynPersistenceUtils;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessor;

import com.google.common.base.Stopwatch;

/**
 * @author Andrea Turli
 */
@Test(groups={"Live", "Live-sanity"})
public class JcloudsBlobStoreBasedObjectStoreTest {

    private static final Logger log = LoggerFactory.getLogger(JcloudsBlobStoreBasedObjectStoreTest.class);
    
    private List<PersistenceObjectStore> objectStores = MutableList.of();;
    private LocalManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception { 
        mgmt = LocalManagementContextForTests.builder(true).useDefaultProperties().build();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        for (PersistenceObjectStore store: objectStores) store.deleteCompletely();
        Entities.destroyAll(mgmt);
        objectStores.clear();
    }

    public PersistenceObjectStore newObjectStore(String spec, String container) {
        PersistenceObjectStore newObjectStore = BrooklynPersistenceUtils.newPersistenceObjectStore(mgmt, spec, container);
        objectStores.add(newObjectStore);
        return newObjectStore;
    }
    
    @Test(groups={"Integration"})
    public void testLocalhost() throws Exception {
        doTestWithStore( newObjectStore(null, 
            BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4)) );
    }
    
    @Test(groups={"Integration"})
    public void testLocalhostWithSubPathInContainerName() throws Exception {
        doTestWithStore( newObjectStore(null, 
            BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4)+"/subpath1/subpath2") );
    }
    
    @Test(groups={"Live", "Live-sanity"})
    public void testJclouds() throws Exception {
        doTestWithStore( newObjectStore(BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, 
            BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4)) );
    }
    
    @Test(groups={"Live", "Live-sanity"})
    public void testJcloudsWithSubPathInContainerName() throws Exception {
        doTestWithStore( newObjectStore(BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, 
            BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4)+"/subpath1/subpath2") );
    }
    
    protected void doTestWithStore(PersistenceObjectStore objectStore) {
        log.info("testing against "+objectStore.getSummaryName());
        
        objectStore.createSubPath("foo");
        StoreObjectAccessor f = objectStore.newAccessor("foo/file1.txt");
        Assert.assertFalse(f.exists());

        Stopwatch timer = Stopwatch.createStarted();
        f.append("Hello world");
        log.info("created in "+Duration.of(timer));
        timer.reset();
        Assert.assertEquals(f.get(), "Hello world");
        log.info("retrieved in "+Duration.of(timer));
        Assert.assertTrue(f.exists());
        
        timer.reset();
        List<String> files = objectStore.listContentsWithSubPath("foo");
        log.info("list retrieved in "+Duration.of(timer)+"; is: "+files);
        Assert.assertEquals(files, MutableList.of("foo/file1.txt"));
        
        f.delete();
    }
    
}
