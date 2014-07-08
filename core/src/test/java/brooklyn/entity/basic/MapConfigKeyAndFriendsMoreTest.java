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
package brooklyn.entity.basic;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.ListConfigKey;
import brooklyn.event.basic.ListConfigKey.ListModifications;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.MapConfigKey.MapModifications;
import brooklyn.event.basic.SetConfigKey;
import brooklyn.event.basic.SetConfigKey.SetModifications;
import brooklyn.event.basic.StructuredConfigKey.StructuredModification;
import brooklyn.event.basic.SubElementConfigKey;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/** like MapListAndOtherStructuredConfigKeyTest but covering the basic usage modes:
 * <li>Modification - value is {@link StructuredModification}
 * <li>Subkey - key is strongly typed {@link SubElementConfigKey} subkey(a.map, subkey)
 * <li>direct - a.map = {subkey: 1}
 * <li>dot-extension - the key is of the form a.map.subkey
 * <p>
 * (also this is pure java so we get nice ide support) */
@Test
public class MapConfigKeyAndFriendsMoreTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(MapConfigKeyAndFriendsMoreTest.class);
    
    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }

    public void testMapModUsage() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING_OBJECT, MapModifications.add(MutableMap.<String,Object>of("a", 1)));
        log.info("Map-Mod: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING_OBJECT), ImmutableMap.<String,Object>of("a", 1));
    }

    public void testMapSubkeyUsage() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING_OBJECT.subKey("a"), 1);
        log.info("Map-SubKey: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING_OBJECT), ImmutableMap.<String,Object>of("a", 1));
    }

    public void testMapDirectUsage() throws Exception {
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_MAP_THING_OBJECT.getName()), ImmutableMap.<String,Object>of("a", 1));
        log.info("Map-Direct: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING_OBJECT), ImmutableMap.<String,Object>of("a", 1));
    }
    
    public void testMapDotExtensionUsage() throws Exception {
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_MAP_THING_OBJECT.getName()+".a"), 1);
        log.info("Map-DotExt: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING_OBJECT), ImmutableMap.<String,Object>of("a", 1));
    }
    
    public void testMapManyWays() throws Exception {
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_MAP_THING_OBJECT.getName()), ImmutableMap.<String,Object>of("map", 1, "subkey", 0, "dotext", 0));
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_MAP_THING_OBJECT.getName()+".dotext"), 1);
        entity.setConfig(TestEntity.CONF_MAP_THING_OBJECT.subKey("subkey"), 1);
        
        log.info("Map-ManyWays: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING_OBJECT), ImmutableMap.<String,Object>of("map", 1, "subkey", 1, "dotext", 1));
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testMapEmpty() throws Exception {
        // ensure it is null before we pass something in, and passing an empty collection makes it be empty
        log.info("Map-Empty-1: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), null);
        entity.setConfig((MapConfigKey)TestEntity.CONF_MAP_THING, MutableMap.of());
        log.info("Map-Empty-2: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), ImmutableMap.of());
    }

    
    public void testSetModUsage() throws Exception {
        entity.setConfig(TestEntity.CONF_SET_THING, SetModifications.addItem("x"));
        log.info("Set-Mod: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of("x"));
    }

    public void testSetSubKeyUsage() throws Exception {
        entity.setConfig(TestEntity.CONF_SET_THING.subKey(), "x");
        log.info("Set-SubKey: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of("x"));
    }

    public void testSetPutDirectUsage() throws Exception {
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_SET_THING.getName()), ImmutableSet.of("x"));
        log.info("Set-Direct: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of("x"));
    }
    
    public void testSetDotExtensionUsage() throws Exception {
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_SET_THING.getName()+".a"), "x");
        log.info("Set-DotExt: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of("x"));
    }
    
    public void testSetManyWays() throws Exception {
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_SET_THING.getName()), ImmutableSet.of("directX"));
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_SET_THING.getName()+".dotext"), "dotextX");
        entity.setConfig(TestEntity.CONF_SET_THING.subKey(), "subkeyX");
        
        log.info("Set-ManyWays: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of("directX", "subkeyX", "dotextX"));
    }
    
    public void testSetCollectionUsage() throws Exception {
        // passing a collection to the RHS of setConfig can be ambiguous,
        // esp if there are already values set, but attempt to act sensibly
        // (logging warnings if the set is not empty)
        entity.setConfig(TestEntity.CONF_SET_OBJ_THING, SetModifications.addItem("w"));
        entity.setConfig(TestEntity.CONF_SET_OBJ_THING, MutableSet.of("x"));
        entity.setConfig(TestEntity.CONF_SET_OBJ_THING, MutableSet.of("y"));
        entity.setConfig(TestEntity.CONF_SET_OBJ_THING, MutableSet.of("a", "b"));
        entity.setConfig(TestEntity.CONF_SET_OBJ_THING, SetModifications.addItem("z"));
        entity.setConfig(TestEntity.CONF_SET_OBJ_THING, SetModifications.addItem(MutableSet.of("c", "d")));
        entity.setConfig(TestEntity.CONF_SET_OBJ_THING, MutableSet.of(MutableSet.of("e", "f")));
        log.info("Set-Coll: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_SET_OBJ_THING), ImmutableSet.of(
            "a", "b", "w", "x", "y", "z", ImmutableSet.of("c", "d"), ImmutableSet.of("e", "f")));
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testSetEmpty() throws Exception {
        // ensure it is null before we pass something in, and passing an empty collection makes it be empty
        log.info("Set-Empty-1: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), null);
        entity.setConfig((SetConfigKey)TestEntity.CONF_SET_THING, MutableSet.of());
        log.info("Set-Empty-2: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of());
    }


    public void testListModUsage() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.add("x", "x"));
        log.info("List-Mod: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ImmutableList.of("x", "x"));
    }
    
    public void testListSubKeyUsage() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "x");
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "x");
        log.info("List-SubKey: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ImmutableList.of("x", "x"));
    }

    public void testListPutDirectUsage() throws Exception {
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_LIST_THING.getName()), ImmutableList.of("x", "x"));
        log.info("List-Direct: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ImmutableList.of("x", "x"));
    }
    
    public void testListDotExtensionUsage() throws Exception {
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_LIST_THING.getName()+".a"), "x");
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_LIST_THING.getName()+".b"), "x");
        log.info("List-DotExt: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ImmutableList.of("x", "x"));
    }
    

    /* see comments on ListConfigKey for why these -- which assert order -- are disabled */
    
    @Test(enabled=false)
    public void testListModUsageMultiValues() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.add("x", "w", "x"));
        log.info("List-Mod: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ImmutableList.of("x", "w", "x"));
    }
    
    @Test(enabled=false)
    public void testListSubKeyUsageMultiValues() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "x");
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "w");
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "x");
        log.info("List-SubKey: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ImmutableList.of("x", "w", "x"));
    }

    @Test(enabled=false)
    public void testListPutDirectUsageMultiValues() throws Exception {
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_LIST_THING.getName()), ImmutableList.of("x", "w", "x"));
        log.info("List-Direct: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ImmutableList.of("x", "w", "x"));
    }
    
    @Test(enabled=false)
    public void testListDotExtensionUsageMultiValues() throws Exception {
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_LIST_THING.getName()+".a"), "x");
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_LIST_THING.getName()+".c"), "w");
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_LIST_THING.getName()+".b"), "x");
        log.info("List-DotExt: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ImmutableList.of("x", "w", "x"));
    }
    
    @Test(enabled=false)
    public void testListManyWaysMultiValues() throws Exception {
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_LIST_THING.getName()), ImmutableList.of("x1", "w1"));
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_LIST_THING.getName()+".dotext"), "x2");
        entity.setConfig(ConfigKeys.newConfigKey(Object.class, TestEntity.CONF_LIST_THING.getName()+".dotext"), "w2");
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "x3");
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "w3");
        
        log.info("List-ManyWays: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ImmutableList.of("x1", "w1", "x2", "x2", "x3", "w3"));
    }
    
    public void testListCollectionUsage() throws Exception {
        // passing a collection to the RHS of setConfig can be ambiguous,
        // esp if there are already values set, but attempt to act sensibly
        // (logging warnings if the set is not empty)
        entity.setConfig(TestEntity.CONF_LIST_OBJ_THING, ListModifications.addItem("w"));
        entity.setConfig(TestEntity.CONF_LIST_OBJ_THING, MutableList.of("x"));
        entity.setConfig(TestEntity.CONF_LIST_OBJ_THING, MutableList.of("y"));
        entity.setConfig(TestEntity.CONF_LIST_OBJ_THING, MutableList.of("a", "b"));
        entity.setConfig(TestEntity.CONF_LIST_OBJ_THING, ListModifications.addItem("z"));
        entity.setConfig(TestEntity.CONF_LIST_OBJ_THING, ListModifications.addItem(MutableList.of("c", "d")));
        entity.setConfig(TestEntity.CONF_LIST_OBJ_THING, MutableList.of(MutableList.of("e", "f")));
        log.info("List-Coll: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        List<? extends Object> list = entity.getConfig(TestEntity.CONF_LIST_OBJ_THING);
        Assert.assertEquals(list.size(), 8, "list is: "+list);
        // "a", "b", "w", "x", "y", "z", ImmutableList.of("c", "d"), ImmutableList.of("e", "f"))
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testListEmpty() throws Exception {
        // ensure it is null before we pass something in, and passing an empty collection makes it be empty
        log.info("List-Empty-1: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), null);
        entity.setConfig((ListConfigKey)TestEntity.CONF_LIST_THING, MutableList.of());
        log.info("List-Empty-2: "+MutableMap.copyOf(entity.getConfigMap().asMapWithStringKeys()));
        Assert.assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ImmutableList.of());
    }
    

}
