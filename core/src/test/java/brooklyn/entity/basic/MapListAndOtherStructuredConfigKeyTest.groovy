package brooklyn.entity.basic;

import static org.testng.Assert.*

import java.util.concurrent.Callable

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.event.basic.DependentConfiguration
import brooklyn.event.basic.ListConfigKey.ListModifications
import brooklyn.event.basic.MapConfigKey.MapModifications
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

public class MapListAndOtherStructuredConfigKeyTest {

    private TestApplication app;
    
    @BeforeMethod
    public void setUp() {
        app = new TestApplication();
    }

    @Test    
    public void testMapConfigKeyCanStoreAndRetrieveVals() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("bkey"), "bval")
        app.start([new SimulatedLocation()])
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
    }
    
    @Test
    public void testMapConfigKeyCanStoreAndRetrieveFutureVals() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), DependentConfiguration.whenDone( {return "aval"} as Callable))
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("bkey"), DependentConfiguration.whenDone( {return "bval"} as Callable))
        app.start([new SimulatedLocation()])
        
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
    }

    @Test(expectedExceptions = [IllegalArgumentException.class, ClassCastException.class])
    public void testConfigKeyStringWontStoreAndRetrieveMaps() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        Map v1 = [a:1, b:"bb"]
        //it only allows strings
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), v1)
    }
    
    @Test
    public void testConfigKeyCanStoreAndRetrieveMaps() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        Map v1 = [a:1, b:"bb"]
        entity.setConfig(TestEntity.CONF_MAP_PLAIN, v1)
        app.start([new SimulatedLocation()])
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_PLAIN), v1)
    }

    @Test    
    public void testListConfigKeyCanStoreAndRetrieveVals() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "bval")
        app.start([new SimulatedLocation()])
        
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval"])
    }
    
    @Test
    public void testListConfigKeyCanStoreAndRetrieveFutureVals() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), DependentConfiguration.whenDone( {return "aval"} as Callable))
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), DependentConfiguration.whenDone( {return "bval"} as Callable))
        app.start([new SimulatedLocation()])
        
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval"])
    }

    @Test
    public void testListConfigKeyAddDirect() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, "bval")
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval"])
    }

    @Test
    public void testListConfigKeyClear() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.clear())
        // for now defaults to null, but empty list might be better? or whatever the default is?
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), null)
    }

    @Test
    public void testListConfigKeyAddMod() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.add("bval", "cval"))
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval","cval"])
    }
    @Test
    public void testListConfigKeyAddAllMod() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.addAll(["bval", "cval"]))
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval","cval"])
    }
    @Test
    public void testListConfigKeyAddItemMod() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.addItem(["bval", "cval"]))
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval",["bval","cval"]])
    }
    @Test
    public void testListConfigKeySetMod() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.set(["bval", "cval"]))
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["bval","cval"])
    }

    @Test
    public void testMapConfigPutDirect() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING, [bkey:"bval"])
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
    }

    @Test
    public void testMapConfigPutAllMod() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING, MapModifications.put([bkey:"bval"]))
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
    }

    @Test
    public void testMapConfigClearMod() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING, MapModifications.clear())
        // for now defaults to null, but empty map might be better? or whatever the default is?
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), null)
    }
    @Test
    public void testMapConfigSetMode() throws Exception {
        TestEntity entity = new TestEntity([owner:app])
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING, MapModifications.set([bkey:"bval"]))
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [bkey:"bval"])
    }

}
