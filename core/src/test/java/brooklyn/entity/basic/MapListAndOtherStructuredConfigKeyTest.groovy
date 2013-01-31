package brooklyn.entity.basic;

import static org.testng.Assert.*

import java.util.concurrent.Callable

import org.jclouds.util.Throwables2
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.event.basic.DependentConfiguration
import brooklyn.event.basic.ListConfigKey.ListModifications
import brooklyn.event.basic.MapConfigKey.MapModifications
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

import com.google.common.collect.ImmutableList

public class MapListAndOtherStructuredConfigKeyTest {

    private List<SimulatedLocation> locs;
    private TestApplication app;
    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        locs = ImmutableList.of(new SimulatedLocation());
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        entity = app.createAndManageChild(BasicEntitySpec.newInstance(TestEntity.class));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroy(app);
    }
    
    @Test    
    public void testMapConfigKeyCanStoreAndRetrieveVals() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("bkey"), "bval")
        app.start(locs)
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
    }
    
    @Test
    public void testMapConfigKeyCanStoreAndRetrieveFutureVals() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), DependentConfiguration.whenDone( {return "aval"} as Callable))
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("bkey"), DependentConfiguration.whenDone( {return "bval"} as Callable))
        app.start(locs)
        
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
    }

    @Test
    public void testConfigKeyStringWontStoreAndRetrieveMaps() throws Exception {
        Map v1 = [a:1, b:"bb"]
        //it only allows strings
        try {
            entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), v1)
            fail();
        } catch (Exception e) {
            ClassCastException cce = Throwables2.getFirstThrowableOfType(e, ClassCastException.class);
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

    @Test    
    public void testListConfigKeyCanStoreAndRetrieveVals() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "bval")
        app.start(locs)
        
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval"])
    }
    
    @Test
    public void testListConfigKeyCanStoreAndRetrieveFutureVals() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), DependentConfiguration.whenDone( {return "aval"} as Callable))
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), DependentConfiguration.whenDone( {return "bval"} as Callable))
        app.start(locs)
        
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval"])
    }

    @Test
    public void testListConfigKeyAddDirect() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, "bval")
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval"])
    }

    @Test
    public void testListConfigKeyClear() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.clearing())
        // for now defaults to null, but empty list might be better? or whatever the default is?
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), null)
    }

    @Test
    public void testListConfigKeyAddMod() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.add("bval", "cval"))
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval","cval"])
    }
    @Test
    public void testListConfigKeyAddAllMod() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.addAll(["bval", "cval"]))
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval","bval","cval"])
    }
    @Test
    public void testListConfigKeyAddItemMod() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.addItem(["bval", "cval"]))
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["aval",["bval","cval"]])
    }
    @Test
    public void testListConfigKeySetMod() throws Exception {
        entity.setConfig(TestEntity.CONF_LIST_THING.subKey(), "aval")
        entity.setConfig(TestEntity.CONF_LIST_THING, ListModifications.set(["bval", "cval"]))
        assertEquals(entity.getConfig(TestEntity.CONF_LIST_THING), ["bval","cval"])
    }

    @Test
    public void testMapConfigPutDirect() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING, [bkey:"bval"])
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [akey:"aval",bkey:"bval"])
    }

    @Test
    public void testMapConfigPutAllMod() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING, MapModifications.put([bkey:"bval"]))
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
    public void testMapConfigSetMode() throws Exception {
        entity.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
        entity.setConfig(TestEntity.CONF_MAP_THING, MapModifications.set([bkey:"bval"]))
        assertEquals(entity.getConfig(TestEntity.CONF_MAP_THING), [bkey:"bval"])
    }

}
