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
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LocationExtensionsTest {

    public static class ConcreteLocation extends AbstractLocation {
        private static final long serialVersionUID = 2407231019435442876L;

        public ConcreteLocation() {
			super();
		}
    }

    public interface MyExtension {
    }
    
    public static class MyExtensionImpl implements MyExtension {
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
        return mgmt.getLocationManager().createLocation(LocationSpec.create(ConcreteLocation.class));
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private ConcreteLocation createConcrete(Class<?> extensionType, Object extension) {
        // this cast is needed to make IntelliJ happy
        return (ConcreteLocation) mgmt.getLocationManager().createLocation(LocationSpec.create(ConcreteLocation.class).extension((Class)extensionType, extension));
    }
    
    @Test
    public void testHasExtensionWhenMissing() {
        Location loc = createConcrete();
        assertFalse(loc.hasExtension(MyExtension.class));
    }

    @Test
    public void testWhenExtensionPresent() {
        MyExtension extension = new MyExtensionImpl();
        ConcreteLocation loc = createConcrete();
        loc.addExtension(MyExtension.class, extension);
        
        assertTrue(loc.hasExtension(MyExtension.class));
        assertEquals(loc.getExtension(MyExtension.class), extension);
    }

    @Test
    public void testAddExtensionThroughLocationSpec() {
        MyExtension extension = new MyExtensionImpl();
        Location loc = createConcrete(MyExtension.class, extension);
        
        assertTrue(loc.hasExtension(MyExtension.class));
        assertEquals(loc.getExtension(MyExtension.class), extension);
    }

    @Test
    public void testGetExtensionWhenMissing() {
        Location loc = createConcrete();

        try {
            loc.getExtension(MyExtension.class);
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
        
        try {
            loc.getExtension(null);
            fail();
        } catch (NullPointerException e) {
            // success
        }
    }

    @Test
    public void testWhenExtensionDifferent() {
        MyExtension extension = new MyExtensionImpl();
        ConcreteLocation loc = createConcrete();
        loc.addExtension(MyExtension.class, extension);
        
        assertFalse(loc.hasExtension(Object.class));
        
        try {
            loc.getExtension(Object.class);
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testAddExtensionIllegally() {
        MyExtension extension = new MyExtensionImpl();
        ConcreteLocation loc = createConcrete();
        
        try {
            loc.addExtension((Class)MyExtension.class, "not an extension");
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
        
        try {
            loc.addExtension(MyExtension.class, null);
            fail();
        } catch (NullPointerException e) {
            // success
        }
        
        try {
            loc.addExtension(null, extension);
            fail();
        } catch (NullPointerException e) {
            // success
        }
    }

    @Test
    public void testAddExtensionThroughLocationSpecIllegally() {
        MyExtension extension = new MyExtensionImpl();
        
        try {
            Location loc = createConcrete(MyExtension.class, "not an extension");
            fail("loc="+loc);
        } catch (IllegalArgumentException e) {
            // success
        }
        
        try {
            Location loc = createConcrete(MyExtension.class, null);
            fail("loc="+loc);
        } catch (NullPointerException e) {
            // success
        }
        
        try {
            Location loc = createConcrete(null, extension);
            fail("loc="+loc);
        } catch (NullPointerException e) {
            // success
        }
    }
}
