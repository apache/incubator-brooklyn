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
package brooklyn.util.collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

@Test
public class MutableMapTest {

    public void testEqualsExact() {
        Map<Object,Object> a = MutableMap.<Object,Object>of("a", 1, "b", false);
        Map<Object,Object> b = MutableMap.<Object,Object>of("a", 1, "b", false);
        Assert.assertEquals(a, b);
    }
    
    public void testEqualsUnordered() {
        Map<Object,Object> a = MutableMap.<Object,Object>of("a", 1, "b", false);
        Map<Object,Object> b = MutableMap.<Object,Object>of("b", false, "a", 1);
        Assert.assertEquals(a, b);
    }

    public void testEqualsDifferentTypes() {
        Map<Object,Object> a = MutableMap.<Object,Object>of("a", 1, "b", false);
        Map<Object,Object> b = ImmutableMap.<Object,Object>of("b", false, "a", 1);
        Assert.assertEquals(a, b);
    }

    public void testListOfMaps() {
        MutableMap<Object, Object> map = MutableMap.<Object,Object>of("a", 1, 2, Arrays.<Object>asList(true, "8"));
        ArrayList<Object> l = new ArrayList<Object>();
        l.add(true); l.add("8");
        MutableMap<Object, Object> map2 = MutableMap.<Object,Object>of(2, l, "a", 1);
        Assert.assertEquals(map, map2);
    }
    
}
