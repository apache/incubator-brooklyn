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
package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.mementos.LocationMemento;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class RebindLocationTest extends RebindTestFixtureWithApp {

    @SuppressWarnings("unused")
    private MyEntity origE;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
    }

    @Test
    public void testSetsLocationOnEntities() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("name", "mylocname"));
        origApp.start(ImmutableList.of(origLoc));

        newApp = (TestApplication) rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));

        assertEquals(newApp.getLocations().size(), 1, "locs="+newE.getLocations());
        assertTrue(Iterables.get(newApp.getLocations(), 0) instanceof MyLocation);
        
        assertEquals(newE.getLocations().size(), 1, "locs="+newE.getLocations());
        assertTrue(Iterables.get(newE.getLocations(), 0) instanceof MyLocation);
    }
    
    @Test
    public void testRestoresLocationIdAndDisplayName() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("name", "mylocname"));
        origApp.start(ImmutableList.of(origLoc));
        
        newApp = (TestApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.getId(), origLoc.getId());
        assertEquals(newLoc.getDisplayName(), origLoc.getDisplayName());
    }
    
    @Test
    public void testCanCustomizeLocationRebind() throws Exception {
        MyLocationCustomProps origLoc = new MyLocationCustomProps(MutableMap.of("name", "mylocname", "myfield", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        newApp = (TestApplication) rebind();
        MyLocationCustomProps newLoc2 = (MyLocationCustomProps) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc2.getId(), origLoc.getId());
        assertEquals(newLoc2.getDisplayName(), origLoc.getDisplayName());
        assertEquals(newLoc2.rebound, true);
        assertEquals(newLoc2.myfield, "myval");
    }
    
    @Test
    public void testRestoresFieldsWithSetFromFlag() throws Exception {
    	MyLocation origLoc = new MyLocation(MutableMap.of("myfield", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        newApp = (TestApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myfield, "myval");
    }
    
    @Test
    public void testRestoresAtomicLongWithSetFromFlag() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("myAtomicLong", "123"));
        origApp.start(ImmutableList.of(origLoc));

        origLoc.myAtomicLong.incrementAndGet();
        assertEquals(origLoc.myAtomicLong.get(), 124L);
        ((EntityInternal)origApp).getManagementContext().getRebindManager().getChangeListener().onChanged(origLoc);
        
        newApp = (TestApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        // should get _modified_ value, not the one in the config map
        assertEquals(newLoc.myAtomicLong.get(), 124L);
    }
    
    @Test
    public void testRestoresConfig() throws Exception {
        MyLocation origLoc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(MyLocation.class)
                .configure(MyLocation.MY_CONFIG_WITH_SETFROMFLAG_NO_SHORT_NAME, "myVal for with setFromFlag noShortName")
                .configure(MyLocation.MY_CONFIG_WITH_SETFROMFLAG_WITH_SHORT_NAME, "myVal for setFromFlag withShortName")
                .configure(MyLocation.MY_CONFIG_WITHOUT_SETFROMFLAG, "myVal for witout setFromFlag"));
        origApp.start(ImmutableList.of(origLoc));

        newApp = (TestApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.getConfig(MyLocation.MY_CONFIG_WITH_SETFROMFLAG_NO_SHORT_NAME), "myVal for with setFromFlag noShortName");
        assertEquals(newLoc.getConfig(MyLocation.MY_CONFIG_WITH_SETFROMFLAG_WITH_SHORT_NAME), "myVal for setFromFlag withShortName");
        assertEquals(newLoc.getConfig(MyLocation.MY_CONFIG_WITHOUT_SETFROMFLAG), "myVal for witout setFromFlag");
    }
    
    @Test
    public void testIgnoresTransientFieldsNotSetFromFlag() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of());
        origLoc.myTransientFieldNotSetFromFlag = "myval";
        origApp.start(ImmutableList.of(origLoc));

        newApp = (TestApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);

        // transient fields normally not persisted
        assertEquals(newLoc.myTransientFieldNotSetFromFlag, null);
    }
    
    @Test
    public void testIgnoresTransientFieldsSetFromFlag() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("myTransientFieldSetFromFlag", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        newApp = (TestApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myTransientFieldSetFromFlag, null);
    }
    
    @Test
    public void testIgnoresStaticFieldsNotSetFromFlag() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of());
        MyLocation.myStaticFieldNotSetFromFlag = "myval";
        origApp.start(ImmutableList.of(origLoc));

        RebindTestUtils.waitForPersisted(origApp);
        MyLocation.myStaticFieldNotSetFromFlag = "mynewval";
        newApp = (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        Assert.assertEquals(newLoc, origLoc);
        
        // static fields normally not persisted (we see new value)
        assertEquals(MyLocation.myStaticFieldNotSetFromFlag, "mynewval");
    }
    
    @Test
    public void testIgnoresStaticFieldsSetFromFlag() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("myStaticFieldSetFromFlag", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        RebindTestUtils.waitForPersisted(origApp);
        MyLocation.myStaticFieldSetFromFlag = "mynewval"; // not auto-checkpointed
        newApp = (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        Assert.assertEquals(newLoc, origLoc);
        
        assertEquals(MyLocation.myStaticFieldSetFromFlag, "mynewval");
    }
    
    @Test
    public void testHandlesFieldReferencingOtherLocations() throws Exception {
    	MyLocation origOtherLoc = new MyLocation();
    	MyLocationReffingOthers origLoc = new MyLocationReffingOthers(MutableMap.of("otherLocs", ImmutableList.of(origOtherLoc), "myfield", "myval"));
    	origOtherLoc.setParent(origLoc);
    	
        origApp.start(ImmutableList.of(origLoc));

        newApp = rebind();
        MyLocationReffingOthers newLoc = (MyLocationReffingOthers) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.getChildren().size(), 1);
        assertTrue(Iterables.get(newLoc.getChildren(), 0) instanceof MyLocation, "children="+newLoc.getChildren());
        assertEquals(newLoc.otherLocs, ImmutableList.copyOf(newLoc.getChildren()));
        
        // Confirm this didn't override other values (e.g. setting other fields back to their defaults, as was once the case!)
        assertEquals(newLoc.myfield, "myval");
    }

    /**
     * @deprecated since 0.7; support for rebinding old-style locations is deprecated
     */
    @Test
    public void testHandlesOldStyleLocation() throws Exception {
        MyOldStyleLocation origLoc = new MyOldStyleLocation(ImmutableMap.of("myfield", "myval"));
        
        origApp.start(ImmutableList.of(origLoc));

        newApp = rebind();
        MyOldStyleLocation newLoc = (MyOldStyleLocation) Iterables.get(newApp.getLocations(), 0);
        assertEquals(newLoc.myfield, "myval");
    }

    @Test
    public void testReboundConfigDoesNotContainId() throws Exception {
        MyLocation origLoc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(MyLocation.class));
        origApp.start(ImmutableList.of(origLoc));

        newApp = (TestApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);

        assertNull(newLoc.getAllConfigBag().getStringKey("id"));
        assertEquals(newLoc.getId(), origLoc.getId());
    }
    
    @Test
    public void testIsRebinding() throws Exception {
        LocationChecksIsRebinding origLoc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(LocationChecksIsRebinding.class));

        rebind();
        LocationChecksIsRebinding newLoc = (LocationChecksIsRebinding) newManagementContext.getLocationManager().getLocation(origLoc.getId());

        assertTrue(newLoc.isRebindingValWhenRebinding());
        assertFalse(newLoc.isRebinding());
    }
    
    public static class LocationChecksIsRebinding extends AbstractLocation {
        boolean isRebindingValWhenRebinding;
        
        public boolean isRebindingValWhenRebinding() {
            return isRebindingValWhenRebinding;
        }
        @Override public boolean isRebinding() {
            return super.isRebinding();
        }
        @Override public void rebind() {
            super.rebind();
            isRebindingValWhenRebinding = isRebinding();
        }
    }
    
    public static class MyOldStyleLocation extends AbstractLocation {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag
        String myfield;

        public MyOldStyleLocation(Map<?,?> flags) {
            super(flags);
        }
    }
    
    public static class MyLocation extends AbstractLocation {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag
        public static final ConfigKey<String> MY_CONFIG_WITH_SETFROMFLAG_NO_SHORT_NAME = ConfigKeys.newStringConfigKey("myconfig.withSetfromflag.noShortName");

        @SetFromFlag("myConfigWithSetFromFlagWithShortName")
        public static final ConfigKey<String> MY_CONFIG_WITH_SETFROMFLAG_WITH_SHORT_NAME = ConfigKeys.newStringConfigKey("myconfig.withSetfromflag.withShortName");

        public static final ConfigKey<String> MY_CONFIG_WITHOUT_SETFROMFLAG = ConfigKeys.newStringConfigKey("myconfig.withoutSetfromflag");

        @SetFromFlag
        String myfield;

        @SetFromFlag(defaultVal="1")
        AtomicLong myAtomicLong;

        @SuppressWarnings("unused")
        private final Object dummy = new Object(); // so not serializable
        
        @SetFromFlag
        transient String myTransientFieldSetFromFlag;
        
        transient String myTransientFieldNotSetFromFlag;
        
        @SetFromFlag
        static String myStaticFieldSetFromFlag;
        
        static String myStaticFieldNotSetFromFlag;
        
        public MyLocation() {
        }
        
        public MyLocation(Map<?,?> flags) {
            super(flags);
        }
    }
    
    public static class MyLocationReffingOthers extends AbstractLocation {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag(defaultVal="a")
        String myfield;

        @SetFromFlag
        List<Location> otherLocs;

        @SuppressWarnings("unused")
        private final Object dummy = new Object(); // so not serializable

        public MyLocationReffingOthers() {
        }
        
        public MyLocationReffingOthers(Map<?,?> flags) {
            super(flags);
        }
    }
    
    public static class MyLocationCustomProps extends AbstractLocation {
        private static final long serialVersionUID = 1L;
        
        String myfield;
        boolean rebound;

        @SuppressWarnings("unused")
        private final Object dummy = new Object(); // so not serializable

        public MyLocationCustomProps() {
        }
        
        public MyLocationCustomProps(Map<?,?> flags) {
            super(flags);
            myfield = (String) flags.get("myfield");
        }
        
        @Override
        public RebindSupport<LocationMemento> getRebindSupport() {
            return new BasicLocationRebindSupport(this) {
                @Override public LocationMemento getMemento() {
                    return getMementoWithProperties(MutableMap.<String,Object>of("myfield", myfield));
                }
                @Override
                protected void doReconstruct(RebindContext rebindContext, LocationMemento memento) {
                    super.doReconstruct(rebindContext, memento);
                    myfield = (String) memento.getCustomField("myfield");
                    rebound = true;
                }
            };
        }
    }
}
