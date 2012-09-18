package brooklyn.entity.basic;

import static org.testng.Assert.*

import java.util.concurrent.Callable

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.event.basic.DependentConfiguration
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

    // TODO more tests, where we set the structured key itself, assert additivity,
    // and implement ability to CLEAR etc, as described in {Map,List}ConfigKey.
    // NB: there are more, practical tests covering much of this in JavaOptsTest 
       
}
