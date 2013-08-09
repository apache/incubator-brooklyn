package brooklyn.entity.basic;

import static brooklyn.entity.basic.AbstractEntity.POLICY_ADDED;
import static brooklyn.entity.basic.AbstractEntity.POLICY_REMOVED;
import static brooklyn.entity.basic.AbstractEntity.SENSOR_ADDED;
import static brooklyn.entity.basic.AbstractEntity.SENSOR_REMOVED;
import static brooklyn.entity.basic.AbstractEntity.EFFECTOR_ADDED;
import static brooklyn.entity.basic.AbstractEntity.EFFECTOR_REMOVED;
import static brooklyn.entity.basic.AbstractEntity.EFFECTOR_CHANGED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Set;

import javax.annotation.Nullable;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.event.basic.Sensors;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.CollectionGuavas;
import brooklyn.util.collections.MutableSet;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class EntityTypeTest {
    private static final AttributeSensor<String> TEST_SENSOR = Sensors.newStringSensor("test.sensor");
    private TestApplication app;
    private EntityInternal entity;
    private EntitySubscriptionTest.RecordingSensorEventListener listener;
    
    public final static Set<Sensor<?>> DEFAULT_SENSORS = ImmutableSet.<Sensor<?>>of(
            SENSOR_ADDED, SENSOR_REMOVED,
            EFFECTOR_ADDED, EFFECTOR_REMOVED, EFFECTOR_CHANGED,
            POLICY_ADDED, POLICY_REMOVED); 

    public static class EmptyEntityForTesting extends AbstractEntity {}
    
    @BeforeMethod(alwaysRun=true)
    public void setUpTestEntity() throws Exception{
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = (EntityInternal) app.createAndManageChild(EntitySpec.create(Entity.class, EmptyEntityForTesting.class));
//        entity = new AbstractEntity(app) {};
//        Entities.startManagement(entity);
        
        listener = new EntitySubscriptionTest.RecordingSensorEventListener();
        app.getSubscriptionContext().subscribe(entity, SENSOR_ADDED, listener);
        app.getSubscriptionContext().subscribe(entity, SENSOR_REMOVED, listener);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testGetName() throws Exception {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        assertEquals(entity2.getEntityType().getName(), TestEntity.class.getCanonicalName());
    }
    
    @Test
    public void testGetSimpleName() throws Exception {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        assertEquals(entity2.getEntityType().getSimpleName(), TestEntity.class.getSimpleName());
    }

    @Test
    public void testGetEffectors() throws Exception {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        Set<Effector<?>> effectors = entity2.getEntityType().getEffectors();
        
        class MatchesNamePredicate implements Predicate<Effector<?>> {
            private final String name;
            public MatchesNamePredicate(String name) {
                this.name = name;
            }
            @Override public boolean apply(@Nullable Effector<?> input) {
                return name.equals(input.getName());
            }
        };
        
        assertNotNull(Iterables.find(effectors, new MatchesNamePredicate("myEffector")), null);
        assertNotNull(Iterables.find(effectors, new MatchesNamePredicate("identityEffector")), null);
    }

    @Test
    public void testGetEffector() throws Exception {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        Effector<?> effector = entity2.getEntityType().getEffector("myEffector");
        Effector<?> effector2 = entity2.getEntityType().getEffector("identityEffector", Object.class);
        assertEquals(effector.getName(), "myEffector");
        assertEquals(effector2.getName(), "identityEffector");
    }

    @Test
    public void testCustomSimpleName() throws Exception {
        class CustomTypeNamedEntity extends AbstractEntity {
            private final String typeName;
            CustomTypeNamedEntity(Entity parent, String typeName) {
                super(parent);
                this.typeName = typeName;
            }
            @Override protected String getEntityTypeName() {
                return typeName;
            }
        }
        
        CustomTypeNamedEntity entity2 = new CustomTypeNamedEntity(app, "a.b.with space");
        Entities.manage(entity2);
        assertEquals(entity2.getEntityType().getSimpleName(), "with_space");
        
        CustomTypeNamedEntity entity3 = new CustomTypeNamedEntity(app, "a.b.with$dollar");
        Entities.manage(entity3);
        assertEquals(entity3.getEntityType().getSimpleName(), "with_dollar");
        
        CustomTypeNamedEntity entity4 = new CustomTypeNamedEntity(app, "a.nothingafterdot.");
        Entities.manage(entity4);
        assertEquals(entity4.getEntityType().getSimpleName(), "a.nothingafterdot.");
    }
    
    @Test
    public void testGetSensors() throws Exception{
        assertEquals(entity.getEntityType().getSensors(), DEFAULT_SENSORS);
    }

    @Test
    public void testAddSensors() throws Exception{
        entity.getMutableEntityType().addSensor(TEST_SENSOR);
        assertEquals(entity.getEntityType().getSensors(), 
                ImmutableSet.builder().addAll(DEFAULT_SENSORS).add(TEST_SENSOR).build());
        
        TestUtils.assertEventually(
                Suppliers.ofInstance(listener.events), 
                Predicates.equalTo(ImmutableList.of(new BasicSensorEvent(SENSOR_ADDED, entity, TEST_SENSOR))));
    }

    @Test
    public void testAddSensorValueThroughEntity() throws Exception{
        entity.setAttribute(TEST_SENSOR, "abc");
        assertEquals(entity.getEntityType().getSensors(), 
                ImmutableSet.builder().addAll(DEFAULT_SENSORS).add(TEST_SENSOR).build());
        
        TestUtils.assertEventually(
                Suppliers.ofInstance(listener.events), 
                Predicates.equalTo(ImmutableList.of(new BasicSensorEvent(SENSOR_ADDED, entity, TEST_SENSOR))));
    }

    @Test
    public void testRemoveSensorThroughEntity() throws Exception{
        entity.setAttribute(TEST_SENSOR, "abc");
        entity.removeAttribute(TEST_SENSOR);
        assertFalse(entity.getEntityType().getSensors().contains(TEST_SENSOR), "sensors="+entity.getEntityType().getSensors()); 
        assertEquals(entity.getAttribute(TEST_SENSOR), null);
        
        TestUtils.assertEventually(
                Suppliers.ofInstance(listener.events), 
                Predicates.equalTo(ImmutableList.of(
                        new BasicSensorEvent(SENSOR_ADDED, entity, TEST_SENSOR), 
                        new BasicSensorEvent(SENSOR_REMOVED, entity, TEST_SENSOR))));
    }

    @Test
    public void testRemoveSensor() throws Exception {
        entity.getMutableEntityType().removeSensor(SENSOR_ADDED);
        assertEquals(entity.getEntityType().getSensors(), 
                MutableSet.builder().addAll(DEFAULT_SENSORS).remove(SENSOR_ADDED).build().toImmutable());
        
        TestUtils.assertEventually(
                Suppliers.ofInstance(listener.events), 
                Predicates.equalTo(ImmutableList.of(new BasicSensorEvent(SENSOR_REMOVED, entity, SENSOR_ADDED))));
    }

    @Test
    public void testRemoveSensors() throws Exception {
        entity.getMutableEntityType().removeSensor(SENSOR_ADDED.getName());
        entity.getMutableEntityType().removeSensor(POLICY_ADDED.getName());
        assertEquals(entity.getEntityType().getSensors(), 
                MutableSet.builder().addAll(DEFAULT_SENSORS).remove(SENSOR_ADDED).remove(POLICY_ADDED).build().toImmutable());
        
        TestUtils.assertEventually(
                CollectionGuavas.size(listener.events), 
                Predicates.equalTo(2));
        TestUtils.assertEventually(
                Suppliers.ofInstance(listener.events), 
                CollectionGuavas.equalsUnordered(
                        new BasicSensorEvent(SENSOR_REMOVED, entity, SENSOR_ADDED),
                        new BasicSensorEvent(SENSOR_REMOVED, entity, POLICY_ADDED)
                    ));
    }

    @Test
    public void testGetSensor() throws Exception {
        Sensor<?> sensor = entity.getEntityType().getSensor("entity.sensor.added");
        assertEquals(sensor.getDescription(), "Sensor dynamically added to entity");
        assertEquals(sensor.getName(), "entity.sensor.added");
        
        assertNull(entity.getEntityType().getSensor("does.not.exist"));
    }

    @Test
    public void testHasSensor() throws Exception {
        assertTrue(entity.getEntityType().hasSensor("entity.sensor.added"));
        assertFalse(entity.getEntityType().hasSensor("does.not.exist"));
    }
}
