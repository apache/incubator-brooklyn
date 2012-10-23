package brooklyn.enricher

import org.testng.Assert
import org.testng.annotations.Test

import brooklyn.enricher.basic.SensorPropagatingEnricher
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

class SensorPropagatingEnricherTest {

    @Test
    public void testPropagation() {
        TestApplication app = []
        TestEntity entity = [ app ];
        app.startManagement();
        
        app.addEnricher(SensorPropagatingEnricher.newInstanceListeningToAllSensorsBut(entity, TestEntity.SEQUENCE)) 

        //name propagated
        entity.setAttribute(TestEntity.NAME, "foo")
        TestUtils.executeUntilSucceeds { Assert.assertEquals(app.getAttribute(TestEntity.NAME), "foo") }
        
        //sequence not picked up
        entity.setAttribute(TestEntity.SEQUENCE, 2)
        Thread.sleep(100)                       
        Assert.assertEquals(app.getAttribute(TestEntity.SEQUENCE), null)
        
        //notif propagated
        int notif = 0;
        app.subscribe(app, TestEntity.MY_NOTIF, { SensorEvent evt -> notif = evt.value } as SensorEventListener)
        entity.emit(TestEntity.MY_NOTIF, 7)
        TestUtils.executeUntilSucceeds { Assert.assertEquals(notif, 7) }
    }
    
    
}
