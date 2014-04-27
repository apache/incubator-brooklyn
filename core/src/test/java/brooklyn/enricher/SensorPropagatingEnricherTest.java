package brooklyn.enricher;

import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.enricher.basic.Propagator;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.policy.EnricherSpec;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.AtomicReferences;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SensorPropagatingEnricherTest {

    private TestApplication app;
    private TestEntity entity;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testPropagatesSpecificSensor() {
        app.addEnricher(Enrichers.builder()
                .propagating(TestEntity.NAME)
                .from(entity)
                .build());

        // name propagated
        entity.setAttribute(TestEntity.NAME, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(app, TestEntity.NAME, "foo");
        
        // sequence not propagated
        entity.setAttribute(TestEntity.SEQUENCE, 2);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 100), app, TestEntity.SEQUENCE, null);
    }
    
    @Test
    public void testPropagatesCurrentValue() {
        entity.setAttribute(TestEntity.NAME, "foo");
        
        app.addEnricher(Enrichers.builder()
                .propagating(TestEntity.NAME)
                .from(entity)
                .build());

        // name propagated
        EntityTestUtils.assertAttributeEqualsEventually(app, TestEntity.NAME, "foo");
    }
    
    @Test
    public void testPropagatesAllStaticSensors() {
        app.addEnricher(Enrichers.builder()
                .propagatingAll()
                .from(entity)
                .build());

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
    public void testPropagatesAllSensorsIncludesDynamicallyAdded() {
        AttributeSensor<String> dynamicAttribute = Sensors.newStringSensor("test.dynamicsensor.strattrib");
        BasicNotificationSensor<String> dynamicNotificationSensor = new BasicNotificationSensor(String.class, "test.dynamicsensor.strnotif");
        
        app.addEnricher(Enrichers.builder()
                .propagatingAll()
                .from(entity)
                .build());

        entity.setAttribute(dynamicAttribute, "foo");
        
        EntityTestUtils.assertAttributeEqualsEventually(app, dynamicAttribute, "foo");
        
        // notification-sensor propagated
        final AtomicReference<String> notif = new AtomicReference<String>();
        app.subscribe(app, dynamicNotificationSensor, new SensorEventListener<String>() {
                @Override public void onEvent(SensorEvent<String> event) {
                    notif.set(event.getValue());
                }});
        entity.emit(dynamicNotificationSensor, "mynotifval");
        Asserts.eventually(AtomicReferences.supplier(notif), Predicates.equalTo("mynotifval"));
    }
    
    @Test
    public void testPropagatesAllBut() {
        app.addEnricher(Enrichers.builder()
                .propagatingAllBut(TestEntity.SEQUENCE)
                .from(entity)
                .build());

        // name propagated
        entity.setAttribute(TestEntity.NAME, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(app, TestEntity.NAME, "foo");
        
        // sequence not propagated
        entity.setAttribute(TestEntity.SEQUENCE, 2);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 100), app, TestEntity.SEQUENCE, null);
    }
    
    @Test
    public void testPropagatingAsDifferentSensor() {
        final AttributeSensor<String> ANOTHER_ATTRIBUTE = Sensors.newStringSensor("another.attribute", "");
        
        app.addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(TestEntity.NAME, ANOTHER_ATTRIBUTE))
                .from(entity)
                .build());

        // name propagated as different attribute
        entity.setAttribute(TestEntity.NAME, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(app, ANOTHER_ATTRIBUTE, "foo");
    }
    
    @Test
    public void testEnricherSpecPropagatesSpecificSensor() throws Exception {
        app.addEnricher(EnricherSpec.create(Propagator.class)
                .configure(MutableMap.builder()
                        .putIfNotNull(Propagator.PRODUCER, entity)
                        .putIfNotNull(Propagator.PROPAGATING, ImmutableList.of(TestEntity.NAME))
                        .build()));

        // name propagated
        entity.setAttribute(TestEntity.NAME, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(app, TestEntity.NAME, "foo");
        
        // sequence not propagated
        entity.setAttribute(TestEntity.SEQUENCE, 2);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 100), app, TestEntity.SEQUENCE, null);
    }
    
    @Test
    public void testEnricherSpecPropagatesSpecificSensorAndMapsOthers() throws Exception {
        final AttributeSensor<String> ANOTHER_ATTRIBUTE = Sensors.newStringSensor("another.attribute", "");
        
        app.addEnricher(EnricherSpec.create(Propagator.class)
                .configure(MutableMap.builder()
                        .putIfNotNull(Propagator.PRODUCER, entity)
                        .putIfNotNull(Propagator.SENSOR_MAPPING, ImmutableMap.of(TestEntity.NAME, ANOTHER_ATTRIBUTE))
                        .putIfNotNull(Propagator.PROPAGATING, ImmutableList.of(TestEntity.SEQUENCE))
                        .build()));

        // name propagated as alternative sensor
        entity.setAttribute(TestEntity.NAME, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(app, ANOTHER_ATTRIBUTE, "foo");
        
        // sequence also propagated
        entity.setAttribute(TestEntity.SEQUENCE, 2);
        EntityTestUtils.assertAttributeEqualsEventually(app, TestEntity.SEQUENCE, 2);

        // name not propagated as original sensor
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 100), app, TestEntity.NAME, null);
    }
    
    @Test
    public void testEnricherSpecThrowsOnPropagatesAndPropagatesAllSet() throws Exception {
        try {
            app.addEnricher(EnricherSpec.create(Propagator.class)
                    .configure(MutableMap.builder()
                            .put(Propagator.PRODUCER, entity)
                            .put(Propagator.PROPAGATING, ImmutableList.of(TestEntity.NAME))
                            .put(Propagator.PROPAGATING_ALL, true)
                            .build()));
        } catch (Exception e) {
            IllegalStateException ise = Exceptions.getFirstThrowableOfType(e, IllegalStateException.class);
            if (ise == null) throw e;
        }
    }
}
