package brooklyn.enricher;

import java.util.Collection;
import java.util.Set;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.basic.Sensors;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.text.StringFunctions;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;

public class EnrichersTest {

    public static final AttributeSensor<Integer> NUM1 = Sensors.newIntegerSensor("test.num1");
    public static final AttributeSensor<Integer> NUM2 = Sensors.newIntegerSensor("test.num2");
    public static final AttributeSensor<Integer> NUM3 = Sensors.newIntegerSensor("test.num3");
    public static final AttributeSensor<String> STR1 = Sensors.newStringSensor("test.str1");
    public static final AttributeSensor<String> STR2 = Sensors.newStringSensor("test.str2");
    public static final AttributeSensor<Set<Object>> SET1 = Sensors.newSensor(new TypeToken<Set<Object>>() {}, "test.set1", "set1 descr");
    public static final AttributeSensor<Long> LONG1 = Sensors.newLongSensor("test.long1");
    
    private TestApplication app;
    private TestEntity entity;
    private TestEntity entity2;
    private BasicGroup group;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testAdding() {
        entity.addEnricher(Enrichers.builder()
                .combining(NUM1, NUM2)
                .publishing(NUM3)
                .computingSum()
                .build());
        
        entity.setAttribute(NUM1, 2);
        entity.setAttribute(NUM2, 3);
        EntityTestUtils.assertAttributeEqualsEventually(entity, NUM3, 5);
    }
    
    @Test
    public void testFromEntity() {
        entity.addEnricher(Enrichers.builder()
                .transforming(NUM1)
                .publishing(NUM1)
                .computing(Functions.<Integer>identity())
                .from(entity2)
                .build());
        
        entity2.setAttribute(NUM1, 2);
        EntityTestUtils.assertAttributeEqualsEventually(entity, NUM1, 2);
    }
    
    @Test
    public void testTransforming() {
        entity.addEnricher(Enrichers.builder()
                .transforming(STR1)
                .publishing(STR2)
                .computing(StringFunctions.append("mysuffix"))
                .build());
        
        entity.setAttribute(STR1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR2, "myvalmysuffix");
    }

    @Test
    public void testTransformingCastsResult() {
        entity.addEnricher(Enrichers.builder()
                .transforming(NUM1)
                .publishing(LONG1)
                .computing((Function)Functions.constant(Integer.valueOf(1)))
                .build());
        
        entity.setAttribute(NUM1, 123);
        EntityTestUtils.assertAttributeEqualsEventually(entity, LONG1, Long.valueOf(1));
    }

    @Test
    public void testTransformingFromEvent() {
        entity.addEnricher(Enrichers.builder()
                .transforming(STR1)
                .publishing(STR2)
                .computingFromEvent(new Function<SensorEvent<String>, String>() {
                    @Override public String apply(SensorEvent<String> input) {
                        return input.getValue() + "mysuffix";
                    }})
                .build());
        
        entity.setAttribute(STR1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR2, "myvalmysuffix");
    }

    @Test
    public void testPropagating() {
        entity.addEnricher(Enrichers.builder()
                .propagating(ImmutableList.of(STR1))
                .from(entity2)
                .build());
        
        entity2.setAttribute(STR1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR1, "myval");
    }
    
    @Test
    public void testPropagatingAndRenaming() {
        entity.addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(STR1, STR2))
                .from(entity2)
                .build());
        
        entity2.setAttribute(STR1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR2, "myval");
    }
    
    // FIXME What is default? members? children? fail?
    @Test
    public void testAggregatingGroupSum() {
        TestEntity child1 = group.addChild(EntitySpec.create(TestEntity.class));
        Entities.manage(child1);
        group.addMember(entity);
        group.addMember(entity2);
        group.addEnricher(Enrichers.builder()
                .aggregating(NUM1)
                .publishing(NUM2)
                .fromMembers()
                .computingSum()
                .build());
        
        child1.setAttribute(NUM1, 1);
        entity.setAttribute(NUM1, 2);
        entity2.setAttribute(NUM1, 3);
        EntityTestUtils.assertAttributeEqualsEventually(group, NUM2, 5);
    }
    
    @Test
    public void testAggregatingChildrenSum() {
        group.addMember(entity);
        TestEntity child1 = group.addChild(EntitySpec.create(TestEntity.class));
        Entities.manage(child1);
        TestEntity child2 = group.addChild(EntitySpec.create(TestEntity.class));
        Entities.manage(child2);
        group.addEnricher(Enrichers.builder()
                .aggregating(NUM1)
                .publishing(NUM2)
                .fromChildren()
                .computingSum()
                .build());
        
        entity.setAttribute(NUM1, 1);
        child1.setAttribute(NUM1, 2);
        child2.setAttribute(NUM1, 3);
        EntityTestUtils.assertAttributeEqualsEventually(group, NUM2, 5);
    }

    @Test
    public void testAggregatingExcludingBlankString() {
        group.addMember(entity);
        group.addMember(entity2);
        group.addEnricher(Enrichers.builder()
                .aggregating(STR1)
                .publishing(SET1)
                .fromMembers()
                .excludingBlank()
                .computing(new Function<Collection<?>, Set<Object>>() {
                    @Override public Set<Object> apply(Collection<?> input) {
                        // accept null values, so don't use ImmutableSet
                        return (input == null) ? ImmutableSet.<Object>of() : MutableSet.<Object>copyOf(input);
                    }})
                .build());
        
        entity.setAttribute(STR1, "1");
        entity2.setAttribute(STR1, "2");
        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of("1", "2"));
        
        entity.setAttribute(STR1, "3");
        entity2.setAttribute(STR1, null);
        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of("3"));
        
        entity.setAttribute(STR1, "");
        entity2.setAttribute(STR1, "4");
        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of("4"));
    }
    
    @Test
    public void testAggregatingExcludingNull() {
        group.addMember(entity);
        group.addEnricher(Enrichers.builder()
                .aggregating(NUM1)
                .publishing(SET1)
                .fromMembers()
                .excludingBlank()
                .computing(new Function<Collection<?>, Set<Object>>() {
                    @Override public Set<Object> apply(Collection<?> input) {
                        // accept null values, so don't use ImmutableSet
                        return (input == null) ? ImmutableSet.<Object>of() : MutableSet.<Object>copyOf(input);
                    }})
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of());

        entity.setAttribute(NUM1, 1);
        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of(1));
        
        entity.setAttribute(NUM1, null);
        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of());
        
        entity.setAttribute(NUM1, 2);
        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of(2));
    }
    
    @Test
    public void testAggregatingCastsResult() {
        group.addMember(entity);
        group.addEnricher(Enrichers.builder()
                .aggregating(NUM1)
                .publishing(LONG1)
                .fromMembers()
                .computing((Function)Functions.constant(Integer.valueOf(1)))
                .build());
        
        entity.setAttribute(NUM1, 123);
        EntityTestUtils.assertAttributeEqualsEventually(group, LONG1, Long.valueOf(1));
    }
}
