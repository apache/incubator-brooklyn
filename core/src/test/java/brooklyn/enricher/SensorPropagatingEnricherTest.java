package brooklyn.enricher;

import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor.StringAttributeSensor;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.javalang.AtomicReferences;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;

public class SensorPropagatingEnricherTest {

    private TestApplication app;
    private TestEntity entity;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }
    
    @Test
    public void testPropagatesSpecificSensor() {
        app.addEnricher(SensorPropagatingEnricher.newInstanceListeningTo(entity, TestEntity.NAME));

        // name propagated
        entity.setAttribute(TestEntity.NAME, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(app, TestEntity.NAME, "foo");
        
        // sequence not propagated
        entity.setAttribute(TestEntity.SEQUENCE, 2);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 100), app, TestEntity.SEQUENCE, null);
    }
    
    @Test
    public void testPropagatesAllSensors() {
        app.addEnricher(SensorPropagatingEnricher.newInstanceListeningToAllSensors(entity));

        // all attributes propagated
        entity.setAttribute(TestEntity.NAME, "foo");
        entity.setAttribute(TestEntity.SEQUENCE, 2);
        
        EntityTestUtils.assertAttributeEqualsEventually(app, TestEntity.NAME, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(app, TestEntity.SEQUENCE, 2);
        
        // notification-sensor propagated
        final AtomicReference<Integer> notif = new AtomicReference<Integer>();
        app.subscribe(app, TestEntity.MY_NOTIF, new SensorEventListener<Integer>() {
                @Override public void onEvent(SensorEvent<Integer> event) {
                    notif.set(event.getValue());
                }});
        entity.emit(TestEntity.MY_NOTIF, 7);
        Asserts.eventually(AtomicReferences.supplier(notif), Predicates.equalTo(7));
    }
    
    @Test
    public void testPropagatesAllBut() {
        app.addEnricher(SensorPropagatingEnricher.newInstanceListeningToAllSensorsBut(entity, TestEntity.SEQUENCE)) ;

        // name propagated
        entity.setAttribute(TestEntity.NAME, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(app, TestEntity.NAME, "foo");
        
        // sequence not propagated
        entity.setAttribute(TestEntity.SEQUENCE, 2);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 100), app, TestEntity.SEQUENCE, null);
    }
    
    @Test
    public void testPropagatingAsDifferentSensor() {
        final BasicAttributeSensor<String> ANOTHER_ATTRIBUTE = new StringAttributeSensor("another.attribute", "");
        app.addEnricher(SensorPropagatingEnricher.newInstanceListeningTo(entity, ImmutableMap.of(TestEntity.NAME, ANOTHER_ATTRIBUTE)));

        // name propagated as different attribute
        entity.setAttribute(TestEntity.NAME, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(app, ANOTHER_ATTRIBUTE, "foo");
    }
}
