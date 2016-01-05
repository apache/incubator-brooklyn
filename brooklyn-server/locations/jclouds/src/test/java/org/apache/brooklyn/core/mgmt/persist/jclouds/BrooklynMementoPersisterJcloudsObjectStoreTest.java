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
package org.apache.brooklyn.core.mgmt.persist.jclouds;


import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.persist.BrooklynMementoPersisterTestFixture;
import org.apache.brooklyn.core.mgmt.persist.jclouds.JcloudsBlobStoreBasedObjectStore;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestUtils;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.time.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Andrea Turli
 */
@Test(groups={"Live", "Live-sanity"})
public class BrooklynMementoPersisterJcloudsObjectStoreTest extends BrooklynMementoPersisterTestFixture {

    @Override @BeforeMethod
    public void setUp() throws Exception { super.setUp(); }
    
    protected LocalManagementContext newPersistingManagementContext() {
        objectStore = new JcloudsBlobStoreBasedObjectStore(
            BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4));
        return RebindTestUtils.managementContextBuilder(classLoader, objectStore)
            .persistPeriod(Duration.ONE_MILLISECOND)
            .properties(BrooklynProperties.Factory.newDefault())
            .buildStarted();
    }
    
    @Test(groups={"Live", "Live-sanity"})
    @Override
    public void testCheckPointAndLoadMemento() throws Exception {
        super.testCheckPointAndLoadMemento();
    }
    
    @Test(groups={"Live", "Live-sanity"})
    @Override
    public void testDeleteAndLoadMemento() throws Exception {
        super.testDeleteAndLoadMemento();
    }
    
    @Test(groups={"Live", "Live-sanity"})
    @Override
    public void testLoadAndCheckpointRawMemento() throws Exception {
        super.testLoadAndCheckpointRawMemento();
    }
}
