package brooklyn.enricher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.math.MathFunctions;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

public class TransformingEnricherTest extends BrooklynAppUnitTestSupport {

    public static final Logger log = LoggerFactory.getLogger(TransformingEnricherTest.class);
            
    TestEntity producer;
    AttributeSensor<Integer> intSensorA;
    AttributeSensor<Long> target;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        producer = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        intSensorA = new BasicAttributeSensor<Integer>(Integer.class, "int.sensor.a");
        target = new BasicAttributeSensor<Long>(Long.class, "long.sensor.target");
        
        app.start(ImmutableList.of(new SimulatedLocation()));
    }
    
    @Test
    public void testTransformingEnricher() throws Exception {
        //ensure previous values get picked up
        producer.setAttribute(intSensorA, 3);

        producer.addEnricher(Enrichers.builder()
                .transforming(intSensorA)
                //.computing(MathFunctions.times(2)) // TODO calling it before "publishing" means it doesn't check return type!
                .publishing(target)
                .computing((Function)MathFunctions.times(2)) // TODO doesn't match strongly typed int->long
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(producer, target, 6L);
    }
}
