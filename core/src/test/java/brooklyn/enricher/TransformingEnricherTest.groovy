package brooklyn.enricher;

import java.util.concurrent.Callable

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.enricher.basic.SensorTransformingEnricher
import brooklyn.entity.SimpleApp
import brooklyn.entity.SimpleEntity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.TestUtils
import brooklyn.util.MutableMap

public class TransformingEnricherTest {

    public static final Logger log = LoggerFactory.getLogger(TransformingEnricherTest.class);
            
    private static final long TIMEOUT_MS = 10*1000;
//    private static final long SHORT_WAIT_MS = 250;
    
    AbstractApplication app;

    EntityLocal producer;
    AttributeSensor<Integer> intSensorA;
    AttributeSensor<Long> target;

    @BeforeMethod()
    public void before() {
        app = new SimpleApp();
        producer = new SimpleEntity(app);
        intSensorA = new BasicAttributeSensor<Integer>(Integer.class, "int.sensor.a");
        target = new BasicAttributeSensor<Long>(Long.class, "long.sensor.target");
        Entities.startManagement(app);
        
        app.start(Arrays.asList(new SimulatedLocation()));
    }
    
    @AfterMethod(alwaysRun=true)
    public void after() {
        if (app!=null) Entities.destroy(app);
    }
    
    @Test
    public void testTransformingEnricher() throws InterruptedException {
        final SensorTransformingEnricher e1 = new SensorTransformingEnricher<Integer,Long>(intSensorA, target, 
            { 2*it });
        
        producer.setAttribute(intSensorA, 3);
        //ensure previous values get picked up
        producer.addEnricher(e1);

        TestUtils.assertEventually(MutableMap.of("timeout", TIMEOUT_MS), 
                new Callable<Object>() { public Object call() {
                    Assert.assertEquals(producer.getAttribute(target), (Long)((long)6));
                    return null;
                }});

    }
}
