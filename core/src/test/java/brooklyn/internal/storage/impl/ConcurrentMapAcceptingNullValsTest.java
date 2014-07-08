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
package brooklyn.internal.storage.impl;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.AbstractMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class ConcurrentMapAcceptingNullValsTest {

    private ConcurrentMap<String, String> delegateMap;
    private ConcurrentMapAcceptingNullVals<String, String> map;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        delegateMap = new ConcurrentHashMap<String,String>();
        map = new ConcurrentMapAcceptingNullVals<String,String>(delegateMap);
    }

    @Test
    public void testSimpleOperations() throws Exception {
        map.put("k1", "v1");
        assertEquals(map, ImmutableMap.of("k1", "v1"));
        assertEquals(map.keySet(), ImmutableSet.of("k1"));
        assertEquals(ImmutableList.copyOf(map.values()), ImmutableList.of("v1"));
        assertEquals(map.size(), 1);
        assertEquals(map.get("k1"), "v1");
        assertTrue(map.containsKey("k1"));
        assertTrue(map.containsValue("v1"));
        assertFalse(map.containsKey("notthere"));
        assertFalse(map.containsValue("notthere"));
        assertFalse(map.isEmpty());
        
        map.put("k1", "v2");
        assertEquals(map.get("k1"), "v2");
        assertEquals(map.size(), 1);
        assertEquals(map, ImmutableMap.of("k1", "v2"));
        
        map.remove("k1");
        assertTrue(map.isEmpty());
        assertEquals(map.size(), 0);
        assertEquals(map.get("k1"), null);
        assertEquals(map, ImmutableMap.of());
        
        map.putAll(ImmutableMap.of("k1", "v3", "k2", "v4"));
        assertEquals(map, ImmutableMap.of("k1", "v3", "k2", "v4"));
        
        map.clear();
        assertEquals(map.size(), 0);
        assertEquals(map, ImmutableMap.of());
    }
    
    @Test
    public void testAcceptsNullValue() throws Exception {
        map.put("k1", null);
        assertEquals(map, MutableMap.of("k1", null));
        assertEquals(map.keySet(), MutableSet.of("k1"));
        assertEquals(MutableList.copyOf(map.values()), MutableList.of(null));
        assertEquals(map.entrySet(), MutableSet.of(new AbstractMap.SimpleEntry<String,String>("k1", null)));
        assertEquals(map.size(), 1);
        assertEquals(map.get("k1"), null);
        assertTrue(map.containsKey("k1"));
        assertTrue(map.containsValue(null));
        assertFalse(map.containsKey("notthere"));
        assertFalse(map.containsValue("notthere"));
        assertFalse(map.isEmpty());
        
        map.put("k2", null);
        assertEquals(map.get("k2"), null);
        assertEquals(map.size(), 2);
        assertEquals(map, MutableMap.of("k1", null, "k2", null));
        assertEquals(MutableList.copyOf(map.values()), MutableList.of(null, null));
        assertEquals(map.entrySet(), MutableSet.of(new AbstractMap.SimpleEntry<String,String>("k1", null), new AbstractMap.SimpleEntry<String,String>("k2", null)));
        
        map.remove("k1");
        assertEquals(map.get("k1"), null);
        assertEquals(map.get("k2"), null);
        assertEquals(map.size(), 1);
        assertEquals(map, MutableMap.of("k2", null));
        assertEquals(MutableList.copyOf(map.values()), MutableList.of(null));
        assertEquals(map.entrySet(), MutableSet.of(new AbstractMap.SimpleEntry<String,String>("k2", null)));
    }
}
