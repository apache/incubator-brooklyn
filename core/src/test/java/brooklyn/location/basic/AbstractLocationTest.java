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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class AbstractLocationTest {

    public static class ConcreteLocation extends AbstractLocation {
        private static final long serialVersionUID = 3954199300889119970L;
        @SetFromFlag(defaultVal="mydefault")
        String myfield;

        public ConcreteLocation() {
			super();
		}

        public ConcreteLocation(Map<?,?> properties) {
            super(properties);
        }
    }

    private ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mgmt = new LocalManagementContextForTests();
    }
    
    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        if (mgmt!=null) Entities.destroyAll(mgmt);
    }

    private ConcreteLocation createConcrete() {
        return createConcrete(MutableMap.<String,Object>of());
    }
    private ConcreteLocation createConcrete(Map<String,?> flags) {
        return createConcrete(null, flags);
    }
    @SuppressWarnings("deprecation")
    private ConcreteLocation createConcrete(String id, Map<String,?> flags) {
        return mgmt.getLocationManager().createLocation( LocationSpec.create(ConcreteLocation.class).id(id).configure(flags) );
    }
    
    @Test
    public void testEqualsUsesId() {
        Location l1 = createConcrete("1", MutableMap.of("name", "bob"));
        Location l1b = new ConcreteLocation(ImmutableMap.of("id", 1));
        Location l2 = createConcrete("2", MutableMap.of("name", "frank"));
        assertEquals(l1, l1b);
        assertNotEquals(l1, l2);
    }

    @Test
    public void nullNameAndParentLocationIsAcceptable() {
        Location location = createConcrete(MutableMap.of("name", null, "parentLocation", null));
        assertEquals(location.getDisplayName(), null);
        assertEquals(location.getParent(), null);
    }

    @Test
    public void testSettingParentLocation() {
        Location location = createConcrete();
        Location locationSub = createConcrete();
        locationSub.setParent(location);
        
        assertEquals(ImmutableList.copyOf(location.getChildren()), ImmutableList.of(locationSub));
        assertEquals(locationSub.getParent(), location);
    }

    @Test
    public void testClearingParentLocation() {
        Location location = createConcrete();
        Location locationSub = createConcrete();
        locationSub.setParent(location);
        
        locationSub.setParent(null);
        assertEquals(ImmutableList.copyOf(location.getChildren()), Collections.emptyList());
        assertEquals(locationSub.getParent(), null);
    }
    
    @Test
    public void testContainsLocation() {
        Location location = createConcrete();
        Location locationSub = createConcrete();
        locationSub.setParent(location);
        
        assertTrue(location.containsLocation(location));
        assertTrue(location.containsLocation(locationSub));
        assertFalse(locationSub.containsLocation(location));
    }


    @Test
    public void queryingNameReturnsNameGivenInConstructor() {
        String name = "Outer Mongolia";
        Location location = createConcrete(MutableMap.of("name", "Outer Mongolia"));
        assertEquals(location.getDisplayName(), name);;
    }

    @Test
    public void constructorParentLocationReturnsExpectedLocation() {
        Location parent = createConcrete(MutableMap.of("name", "Middle Earth"));
        Location child = createConcrete(MutableMap.of("name", "The Shire", "parentLocation", parent));
        assertEquals(child.getParent(), parent);
        assertEquals(ImmutableList.copyOf(parent.getChildren()), ImmutableList.of(child));
    }

    @Test
    public void setParentLocationReturnsExpectedLocation() {
        Location parent = createConcrete(MutableMap.of("name", "Middle Earth"));
        Location child = createConcrete(MutableMap.of("name", "The Shire"));
        child.setParent(parent);
        assertEquals(child.getParent(), parent);
        assertEquals(ImmutableList.copyOf(parent.getChildren()), ImmutableList.of(child));
    }
    
    @Test
    public void testAddChildToParentLocationReturnsExpectedLocation() {
        ConcreteLocation parent = createConcrete();
        Location child = createConcrete();
        parent.addChild(child);
        assertEquals(child.getParent(), parent);
        assertEquals(ImmutableList.copyOf(parent.getChildren()), ImmutableList.of(child));
    }

    @Test
    public void testFieldSetFromFlag() {
    	ConcreteLocation loc = createConcrete(MutableMap.of("myfield", "myval"));
        assertEquals(loc.myfield, "myval");
    }
    
    @Test
    public void testFieldSetFromFlagUsesDefault() {
        ConcreteLocation loc = createConcrete();
        assertEquals(loc.myfield, "mydefault");
    }

    @Test
    public void testLocationTags() throws Exception {
        LocationInternal loc = mgmt.getLocationManager().createLocation(LocationSpec.create(ConcreteLocation.class).tag("x"));
        assertEquals(loc.getTagSupport().getTags(), MutableSet.of("x"));
    }

}
