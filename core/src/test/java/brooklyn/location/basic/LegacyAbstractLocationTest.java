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
package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.util.Collections;
import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;

public class LegacyAbstractLocationTest {

    private static class ConcreteLocation extends AbstractLocation {
		@SetFromFlag(defaultVal="mydefault")
        String myfield;

        public ConcreteLocation() {
			super();
		}

        public ConcreteLocation(Map properties) {
            super(properties);
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        // nothing to tear down; did not create a management context
    }

    @Test
    public void testEquals() {
        AbstractLocation l1 = new ConcreteLocation(MutableMap.of("id", "1", "name", "bob"));
        AbstractLocation l2 = new ConcreteLocation(MutableMap.of("id", "1", "name", "frank"));
        AbstractLocation l3 = new ConcreteLocation(MutableMap.of("id", "2", "name", "frank"));
        assertEquals(l1, l2);
        assertNotEquals(l2, l3);
    }

    @Test
    public void nullNameAndParentLocationIsAcceptable() {
        AbstractLocation location = new ConcreteLocation(MutableMap.of("name", null, "parentLocation", null));
        assertEquals(location.getDisplayName(), null);
        assertEquals(location.getParent(), null);
    }

    @Test
    public void testSettingParentLocation() {
        AbstractLocation location = new ConcreteLocation();
        AbstractLocation locationSub = new ConcreteLocation();
        locationSub.setParent(location);
        
        assertEquals(ImmutableList.copyOf(location.getChildren()), ImmutableList.of(locationSub));
        assertEquals(locationSub.getParent(), location);
    }

    @Test
    public void testClearingParentLocation() {
        AbstractLocation location = new ConcreteLocation();
        AbstractLocation locationSub = new ConcreteLocation();
        locationSub.setParent(location);
        
        locationSub.setParent(null);
        assertEquals(ImmutableList.copyOf(location.getChildren()), Collections.emptyList());
        assertEquals(locationSub.getParent(), null);
    }
    
    @Test
    public void testContainsLocation() {
        AbstractLocation location = new ConcreteLocation();
        AbstractLocation locationSub = new ConcreteLocation();
        locationSub.setParent(location);
        
        assertTrue(location.containsLocation(location));
        assertTrue(location.containsLocation(locationSub));
        assertFalse(locationSub.containsLocation(location));
    }


    @Test
    public void queryingNameReturnsNameGivenInConstructor() {
        String name = "Outer Mongolia";
        AbstractLocation location = new ConcreteLocation(MutableMap.of("name", "Outer Mongolia"));
        assertEquals(location.getDisplayName(), name);;
    }

    @Test
    public void constructorParentLocationReturnsExpectedLocation() {
        AbstractLocation parent = new ConcreteLocation(MutableMap.of("name", "Middle Earth"));
        AbstractLocation child = new ConcreteLocation(MutableMap.of("name", "The Shire", "parentLocation", parent));
        assertEquals(child.getParent(), parent);
        assertEquals(ImmutableList.copyOf(parent.getChildren()), ImmutableList.of(child));
    }

    @Test
    public void setParentLocationReturnsExpectedLocation() {
        AbstractLocation parent = new ConcreteLocation(MutableMap.of("name", "Middle Earth"));
        AbstractLocation child = new ConcreteLocation(MutableMap.of("name", "The Shire"));
        child.setParent(parent);
        assertEquals(child.getParent(), parent);
        assertEquals(ImmutableList.copyOf(parent.getChildren()), ImmutableList.of(child));
    }
    
    @Test
    public void testAddChildToParentLocationReturnsExpectedLocation() {
        AbstractLocation parent = new ConcreteLocation(MutableMap.of("id", "1"));
        AbstractLocation child = new ConcreteLocation(MutableMap.of("id", "2"));
        parent.addChild(child);
        assertEquals(child.getParent(), parent);
        assertEquals(ImmutableList.copyOf(parent.getChildren()), ImmutableList.of(child));
    }

    @Test
    public void testFieldSetFromFlag() {
    	ConcreteLocation loc = new ConcreteLocation(MutableMap.of("myfield", "myval"));
        assertEquals(loc.myfield, "myval");
    }
    
    @Test
    public void testFieldSetFromFlagUsesDefault() {
        ConcreteLocation loc = new ConcreteLocation();
        assertEquals(loc.myfield, "mydefault");
    }
    
}
