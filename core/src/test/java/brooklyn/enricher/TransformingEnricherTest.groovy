package brooklyn.enricher;

import java.util.concurrent.Callable

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.enricher.basic.SensorTransformingEnricher
import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxying.EntitySpec
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.TestUtils
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.util.collections.MutableMap;

public class TransformingEnricherTest {

    public static final Logger log = LoggerFactory.getLogger(TransformingEnricherTest.class);
            
    private static final long TIMEOUT_MS = 10*1000;
//    private static final long SHORT_WAIT_MS = 250;
    
    TestApplication app;
    TestEntity producer;
    AttributeSensor<Integer> intSensorA;
    AttributeSensor<Long> target;

    @BeforeMethod()
    public void before() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        producer = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        intSensorA = new BasicAttributeSensor<Integer>(Integer.class, "int.sensor.a");
        target = new BasicAttributeSensor<Long>(Long.class, "long.sensor.target");
        
        app.start(Arrays.asList(new SimulatedLocation()));
    }
    
    @AfterMethod(alwaysRun=true)
    public void after() {
        if (app!=null) Entities.destroyAll(app.getManagementContext());
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
