package brooklyn.entity.basic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.ListConfigKey.ListModifications;
import brooklyn.event.basic.MapConfigKey.MapModifications;
import brooklyn.event.basic.SetConfigKey.SetModifications;
import brooklyn.event.basic.StructuredConfigKey.StructuredModification;
import brooklyn.event.basic.SubElementConfigKey;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

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
public class MapConfigKeyAndFriendsMoreTest {

    private static final Logger log = LoggerFactory.getLogger(MapConfigKeyAndFriendsMoreTest.class);
    
    private TestApplication app;
    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
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
    

}
