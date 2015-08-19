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
package org.apache.brooklyn.core.config

import static org.testng.Assert.*

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import org.apache.brooklyn.api.entity.Entity
import org.apache.brooklyn.api.entity.EntitySpec
import org.apache.brooklyn.core.config.ListConfigKey.ListModifications
import org.apache.brooklyn.core.config.MapConfigKey.MapModifications
import org.apache.brooklyn.core.config.SetConfigKey.SetModifications
import org.apache.brooklyn.core.test.entity.TestApplication
import org.apache.brooklyn.core.test.entity.TestEntity
import org.apache.brooklyn.core.entity.Entities
import org.apache.brooklyn.core.location.SimulatedLocation
import org.apache.brooklyn.core.sensor.DependentConfiguration
import org.apache.brooklyn.util.collections.MutableMap
import org.apache.brooklyn.util.core.task.DeferredSupplier
import org.apache.brooklyn.util.exceptions.Exceptions
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet

public class MapListAndOtherStructuredConfigKeyTest {

    private List<SimulatedLocation> locs;
    private TestApplication app;
    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        locs = ImmutableList.of(new SimulatedLocation());
        app = TestApplication.Factory.newManagedInstanceForTests();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test    
    public void testMapConfigKeyCanStoreAndRetrieveVals() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("bkey"), "bval")
        app.start(locs)
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING.subKey("akey")), "aval")
    }
    
    @Test
    public void testMapConfigKeyCanStoreAndRetrieveFutureValsPutByKeys() throws Exception {
        String bval = "bval-too-early"
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), DependentConfiguration.whenDone( {return "aval"} as Callable))
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("bkey"), DependentConfiguration.whenDone( {return bval} as Callable))
        app.start(locs)
        bval = "bval";
        
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
    }

    @Test
    public void testMapConfigKeyCanStoreAndRetrieveFutureValsPutAsMap() throws Exception {
        String bval = "bval-too-early"
        entity.setConfig(TestEntity.CONF_MAP_THING, MutableMap.of("akey", DependentConfiguration.whenDone( {return "aval"} as Callable),
            "bkey", DependentConfiguration.whenDone( {return bval} as Callable)));
        app.start(locs)
        bval = "bval";
        
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
    }

    @Test
    public void testUnstructuredConfigKeyCanStoreAndRetrieveFutureValsPutAsMap() throws Exception {
        final AtomicReference<String> bval = new AtomicReference<String>("bval-too-early");
        final AtomicInteger bref = new AtomicInteger(0);
        
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_MAP_THING.getName()), 
            MutableMap.of("akey", DependentConfiguration.whenDone( {return "aval"} as Callable),
                "bkey", {bref.incrementAndGet(); return bval.get();} as DeferredSupplier));
        app.start(locs)
        assertEquals(bref.get(), 0);
        bval.set("bval");
  
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING.subKey("akey")), "aval")
        assertEquals(bref.get(), 0);
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING.subKey("bkey")), "bval")
        assertEquals(bref.get(), 1);
        
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
        assertEquals(bref.get(), 2);
        
        // and changes are also visible
        bval.set("bval2");
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval2"])
        assertEquals(bref.get(), 3);
    }

    @Test
    public void testResolvesMapKeysOnGetNotPut() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING,
            MutableMap.of({return "akey";} as DeferredSupplier, {return "aval";} as DeferredSupplier));
        app.start(locs)
  
        // subkey is not resolvable in this way
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING.subKey("akey")), null)
        // deferred supplier keys are only resolved when map is gotten
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval"])
    }

    @Test
    public void testConfigKeyStringWontStoreAndRetrieveMaps() throws Exception {
        Map v1 = [a:1, b:"bb"]
        //it only allows strings
        try {
            entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), v1)
            fail();
        } catch (Exception e) {
            ClassCastException cce = Exceptions.getFirstThrowableOfType(e, ClassCastException.class);
            if (cce == null) throw e;
            if (!cce.getMessage().contains("Cannot coerce type")) throw e;
        }
    }
    
    @Test
    public void testConfigKeyCanStoreAndRetrieveMaps() throws Exception {
        Map v1 = [a:1, b:"bb"]
        entity.setConfig(TestEntity.CONF_MAP_PLAIN, v1)
        app.start(locs)
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_PLAIN), v1)
    }

    // TODO getConfig returns null; it iterated over the set to add each value so setting it to a null set was like a no-op
    @Test(enabled=false)
    public void testSetConfigKeyAsEmptySet() throws Exception {
        Entity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class)
            .configure(TestEntity.CONF_SET_THING.getName(), ImmutableSet.of()));

        Entity entity3 = app.createAndManageChild(EntitySpec.create(TestEntity.class)
            .configure(TestEntity.CONF_SET_THING, ImmutableSet.of()));

        app.start(locs)
        
        assertEquals(entity3.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of())
        assertEquals(entity2.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of())
    }
    
    @Test
    public void testSetConfigKeyCanStoreAndRetrieveVals() throws Exception {
        entity.setConfig(TestEntity.CONF_SET_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_SET_THING.subKey(), "bval")
        app.start(locs)
        
        assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of("aval","bval"))
    }
    
    @Test
    public void testSetConfigKeyCanStoreAndRetrieveFutureVals() throws Exception {
        entity.setConfig(TestEntity.CONF_SET_THING.subKey(), DependentConfiguration.whenDone( {return "aval"} as Callable))
        entity.setConfig(TestEntity.CONF_SET_THING.subKey(), DependentConfiguration.whenDone( {return "bval"} as Callable))
        app.start(locs)
        
        assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of("aval","bval"))
    }

    @Test
    public void testSetConfigKeyAddDirect() throws Exception {
        entity.setConfig(TestEntity.CONF_SET_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_SET_THING, "bval")
        assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of("aval","bval"))
    }

    @Test
    public void testSetConfigKeyClear() throws Exception {
        entity.setConfig(TestEntity.CONF_SET_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_SET_THING, SetModifications.clearing())
        // for now defaults to null, but empty list might be better? or whatever the default is?
        assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), null)
    }

    @Test
    public void testSetConfigKeyAddMod() throws Exception {
        entity.setConfig(TestEntity.CONF_SET_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_SET_THING, SetModifications.add("bval", "cval"))
        assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of("aval","bval","cval"))
    }
    @Test
    public void testSetConfigKeyAddAllMod() throws Exception {
        entity.setConfig(TestEntity.CONF_SET_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_SET_THING, SetModifications.addAll(["bval", "cval"]))
        assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of("aval","bval","cval"))
    }
    @Test
    public void testSetConfigKeyAddItemMod() throws Exception {
        entity.setConfig(TestEntity.CONF_SET_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_SET_THING, SetModifications.addItem(["bval", "cval"]))
        assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of("aval",["bval","cval"]))
    }
    @Test
    public void testSetConfigKeyListMod() throws Exception {
        entity.setConfig(TestEntity.CONF_SET_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_SET_THING, SetModifications.set(["bval", "cval"]))
        assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of("bval","cval"))
    }
    
    @Test // ListConfigKey deprecated, as order no longer guaranteed
    public void testListConfigKeyCanStoreAndRetrieveVals() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "bval")
        app.start(locs)
        
        //assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval"])
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING) as Set, ["aval","bval"] as Set)
    }
    
    @Test // ListConfigKey deprecated, as order no longer guaranteed
    public void testListConfigKeyCanStoreAndRetrieveFutureVals() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), DependentConfiguration.whenDone( {return "aval"} as Callable))
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), DependentConfiguration.whenDone( {return "bval"} as Callable))
        app.start(locs)
        
        //assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval"])
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING) as Set, ["aval","bval"] as Set)
    }

    @Test // ListConfigKey deprecated, as order no longer guaranteed
    public void testListConfigKeyAddDirect() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, "bval")
        //assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval"])
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING) as Set, ["aval","bval"] as Set)
    }

    @Test // ListConfigKey deprecated, as order no longer guaranteed
    public void testListConfigKeyClear() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.clearing())
        // for now defaults to null, but empty list might be better? or whatever the default is?
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING) as Set, null)
    }

    @Test // ListConfigKey deprecated, as order no longer guaranteed
    public void testListConfigKeyAddMod() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.add("bval", "cval"))
        //assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval","cval"])
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING) as Set, ["aval","bval","cval"] as Set)
    }

    @Test // ListConfigKey deprecated, as order no longer guaranteed
    public void testListConfigKeyAddAllMod() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.addAll(["bval", "cval"]))
        //assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval","cval"])
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING) as Set, ["aval","bval","cval"] as Set)
    }
    
    @Test // ListConfigKey deprecated, as order no longer guaranteed
    public void testListConfigKeyAddItemMod() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.addItem(["bval", "cval"]))
        //assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval",["bval","cval"]])
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING) as Set, ["aval",["bval","cval"]] as Set)
    }
    
    @Test // ListConfigKey deprecated, as order no longer guaranteed
    public void testListConfigKeyListMod() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.set(["bval", "cval"]))
        //assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["bval","cval"])
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING) as Set, ["bval","cval"] as Set)
    }

    @Test
    public void testMapConfigPutDirect() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING, [bkey:"bval"])
        //assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
    }

    @Test
    public void testMapConfigPutAllMod() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING, MapModifications.put([bkey:"bval"]))
        //assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
    }

    @Test
    public void testMapConfigClearMod() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING, MapModifications.clearing())
        // for now defaults to null, but empty map might be better? or whatever the default is?
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), null)
    }
    @Test
    public void testMapConfigSetMod() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING, MapModifications.set([bkey:"bval"]))
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [bkey:"bval"])
    }
    @Test
    public void testMapConfigDeepSetFromMap() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING_OBJECT, [akey: [aa:"AA", a2:"A2"], bkey: "b"])
        
        entity.getConfig(TestEntity.CONF_MAP_THING_OBJECT.subKey("akey"), [aa:"AA", a2:"A2"])
        entity.getConfig(TestEntity.CONF_MAP_THING_OBJECT.subKey("bkey"), ["b"])
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING_OBJECT), 
            [akey:[aa:"AA",a2:"A2"],bkey:"b" ])
    }
    @Test
    public void testMapConfigDeepSetFromSubkeys() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING_OBJECT.subKey("akey"), [aa:"AA", a2:"A2"])
        entity.setConfig(TestEntity.CONF_MAP_THING_OBJECT.subKey("bkey"), "b")
        
        entity.getConfig(TestEntity.CONF_MAP_THING_OBJECT.subKey("akey"), [aa:"AA", a2:"A2"])
        entity.getConfig(TestEntity.CONF_MAP_THING_OBJECT.subKey("bkey"), ["b"])
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING_OBJECT), 
            [akey:[aa:"AA",a2:"A2"],bkey:"b" ])
    }
    @Test
    public void testMapConfigAdd() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING_OBJECT.subKey("0key"), 0)
        entity.setConfig(TestEntity.CONF_MAP_THING_OBJECT.subKey("akey"), [aa:"AA", a2:"A2"])
        entity.setConfig(TestEntity.CONF_MAP_THING_OBJECT.subKey("bkey"), ["b"])
        entity.setConfig(TestEntity.CONF_MAP_THING_OBJECT, MapModifications.add([akey:[a3:3],bkey:"b2",ckey:"cc"]))
        
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING_OBJECT), 
            ["0key":0, akey:[aa:"AA",a2:"A2",a3:3],bkey:["b","b2"],ckey:"cc" ])
        entity.getConfig(TestEntity.CONF_MAP_THING_OBJECT.subKey("akey"), [aa:"AA", a2:"A2", a3:3])
    }

}
