package brooklyn.enricher;

import static org.testng.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.event.basic.Sensors;
import brooklyn.management.SubscriptionContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

public class TimeFractionDeltaEnricherTest {

    private static final double PRECISION = 0.000001;
    
    private TestApplication app;
    private EntityLocal producer;

    Sensor<Integer> intSensor;
    AttributeSensor<Double> fractionSensor;
    SubscriptionContext subscription;
    
    @BeforeMethod(alwaysRun=true)
    public void before() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        producer = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        intSensor = Sensors.newIntegerSensor("int sensor");
        fractionSensor = Sensors.newDoubleSensor("fraction sensor");
    }

    @AfterMethod(alwaysRun=true)
    public void after() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testCalculatesFractions() {
        TimeFractionDeltaEnricher<Integer> enricher = new TimeFractionDeltaEnricher<Integer>(producer, intSensor, fractionSensor, TimeUnit.MILLISECONDS);
        producer.addEnricher(enricher);
        
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 0, 1000000L));
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 0, 1001000L));
        assertEquals(producer.getAttribute(fractionSensor), 0d);
        
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 100, 1002000L));
        assertEquals(producer.getAttribute(fractionSensor), 0.1d, PRECISION);
        
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 300, 1003000L));
        assertEquals(producer.getAttribute(fractionSensor), 0.2d, PRECISION);
        
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 2300, 1004000L));
        assertEquals(producer.getAttribute(fractionSensor), 2d, PRECISION);
    }
    
    @Test
    public void testConvertsTimeUnits() {
        TimeFractionDeltaEnricher<Integer> enricher = new TimeFractionDeltaEnricher<Integer>(producer, intSensor, fractionSensor, TimeUnit.MICROSECONDS);
        producer.addEnricher(enricher);
        
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 0, 1000000L));
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 1000000, 1001000L));
        assertEquals(producer.getAttribute(fractionSensor), 1d);
    }
    
    @Test
    public void testConverts100NanosTimeBlocks() {
        TimeFractionDeltaEnricher<Integer> enricher = new TimeFractionDeltaEnricher<Integer>(producer, intSensor, fractionSensor, 100);
        producer.addEnricher(enricher);
        
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 0, 1000000L));
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 10000000, 1001000L));
        assertEquals(producer.getAttribute(fractionSensor), 1d);
    }
}
