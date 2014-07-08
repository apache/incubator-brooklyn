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
package brooklyn.storage.impl.hazelcast;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.internal.storage.DataGridFactory;
import brooklyn.internal.storage.Reference;
import brooklyn.internal.storage.impl.BrooklynStorageImpl;
import brooklyn.internal.storage.impl.hazelcast.HazelcastDataGrid;
import brooklyn.internal.storage.impl.hazelcast.HazelcastDataGridFactory;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.collections.MutableList;

import com.hazelcast.core.Hazelcast;

@Test(groups="Integration")
public class HazelcastStorageTest {

    private LocalManagementContext managementContext;
    private BrooklynStorage storage;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        BrooklynProperties properties = BrooklynProperties.Factory.newDefault();
        properties.put(DataGridFactory.class.getName(), HazelcastDataGridFactory.class.getName());
        managementContext = new LocalManagementContext(properties);
        storage = managementContext.getStorage();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (managementContext != null) managementContext.terminate();
        Hazelcast.shutdownAll();
    }

    //test to verify that our HazelcastDatagrid really is being picked up.
    @Test
    public void testPickUp(){
       assertTrue(storage instanceof BrooklynStorageImpl,"storage should be instance of BrooklynStorageImpl");
       BrooklynStorageImpl brooklynStorageImpl = (BrooklynStorageImpl)storage;
       assertTrue(brooklynStorageImpl.getDataGrid() instanceof HazelcastDataGrid,"storage should be instanceof HazelcastDataGrid");
    }

    @Test
    public void testGetMap() {
        Map<String,String> map = storage.getMap("somemap");
        map.put("foo", "bar");
        assertEquals( map.get("foo"),"bar");
    }

    @Test
    public void testGetReference() {
        Reference<String> ref = storage.getReference("someReference");
        ref.set("bar");
        assertEquals(ref.get(), "bar");
    }

    @Test
    public void testNonConcurrentList(){
        Reference<List<String>> ref = storage.getNonConcurrentList("someReference");
        ref.set(MutableList.of("bar"));

        assertEquals(ref.get().get(0),"bar");
    }

    @Test
    public void testRemoveReference(){
        Reference<String> ref = storage.getReference("someReference");
        ref.set("bar");
        storage.remove("someReference");
        assertEquals(ref.get(), null);
    }

    @Test
    public void testRemoveMap(){
        Map<String,String> map = storage.getMap("somemap");
        map.put("foo", "bar");
        storage.remove("somemap");
        assertEquals(null, map.get("foo"));
    }
}
