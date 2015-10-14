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
package org.apache.brooklyn.feed.jmx;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class JmxValueFunctionsTest {

    @Test
    public void testCompositeDataToMap() throws Exception {
        CompositeType compositeType = new CompositeType(
                "MyCompositeType", 
                "my composite descr", 
                new String[] {"key1", "key2"}, 
                new String[] {"key1 descr", "key2 descr"}, 
                new OpenType[] {SimpleType.STRING, SimpleType.STRING});
        Map<String, ?> items = ImmutableMap.of(
                "key1", "val1",
                "key2", "val2");
        CompositeData data = new CompositeDataSupport(compositeType, items);
        
        Map<String,?> result = JmxValueFunctions.compositeDataToMap(data);
        assertEquals(result, items);
        
        Map<?,?> result2 = JmxValueFunctions.compositeDataToMap().apply(data);
        assertEquals(result2, items);
    }
    
    @Test
    public void testTabularDataToMapOfMaps() throws Exception {
        CompositeType rowType = new CompositeType(
                "MyRowType", 
                "my row descr", 
                new String[] {"key1", "key2"}, 
                new String[] {"key1 descr", "key2 descr"}, 
                new OpenType[] {SimpleType.STRING, SimpleType.STRING});
        TabularType tabularType = new TabularType(
                "MyTabularType", 
                "my table descr", 
                rowType,
                new String[] {"key1"});
        Map<String, ?> row1 = ImmutableMap.of(
                "key1", "row1.val1",
                "key2", "row1.val2");
        Map<String, ?> row2 = ImmutableMap.of(
                "key1", "row2.val1",
                "key2", "row2.val2");
        TabularDataSupport table = new TabularDataSupport(tabularType);
        table.put(new CompositeDataSupport(rowType, row1));
        table.put(new CompositeDataSupport(rowType, row2));
        
        Map<List<?>, Map<String, Object>> result = JmxValueFunctions.tabularDataToMapOfMaps(table);
        assertEquals(result, ImmutableMap.of(ImmutableList.of("row1.val1"), row1, ImmutableList.of("row2.val1"), row2));
        
        Map<?,?> result2 = JmxValueFunctions.tabularDataToMapOfMaps().apply(table);
        assertEquals(result2, ImmutableMap.of(ImmutableList.of("row1.val1"), row1, ImmutableList.of("row2.val1"), row2));
    }
    
    @Test
    public void testTabularDataToMap() throws Exception {
        CompositeType rowType = new CompositeType(
                "MyRowType", 
                "my row descr", 
                new String[] {"key1", "key2"}, 
                new String[] {"key1 descr", "key2 descr"}, 
                new OpenType[] {SimpleType.STRING, SimpleType.STRING});
        TabularType tabularType = new TabularType(
                "MyTabularType", 
                "my table descr", 
                rowType,
                new String[] {"key1"});
        Map<String, ?> row1 = ImmutableMap.of(
                "key1", "row1.val1",
                "key2", "row1.val2");
        Map<String, ?> row2 = ImmutableMap.of(
                "key1", "row2.val1",
                "key2", "row2.val2");
        TabularDataSupport table = new TabularDataSupport(tabularType);
        table.put(new CompositeDataSupport(rowType, row1));
        table.put(new CompositeDataSupport(rowType, row2));
        
        Map<?,?> result = JmxValueFunctions.tabularDataToMap(table);
        assertEquals(result, row2);
        
        Map<?,?> result2 = JmxValueFunctions.tabularDataToMap().apply(table);
        assertEquals(result2, row2);
    }
}
