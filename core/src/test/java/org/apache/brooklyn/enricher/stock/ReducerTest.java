package org.apache.brooklyn.enricher.stock;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.EntityTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

public class ReducerTest extends BrooklynAppUnitTestSupport {

    public static final AttributeSensor<String> STR1 = Sensors.newStringSensor("test.str1");
    public static final AttributeSensor<String> STR2 = Sensors.newStringSensor("test.str2");
    public static final AttributeSensor<String> STR3 = Sensors.newStringSensor("test.str3");

    private TestEntity entity;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }

    @Test
    public void testBasicReducer() {
        entity.addEnricher(EnricherSpec.create(Reducer.class)
            .configure(Reducer.PRODUCER, entity)
            .configure(Reducer.SOURCE_SENSORS, ImmutableList.of(STR1, STR2))
            .configure(Reducer.TARGET_SENSOR, STR3)
            .configure(Reducer.REDUCER_FUNCTION, new Concatenator())
        );
        entity.sensors().set(STR1, "foo");
        EntityTestUtils.assertAttributeEqualsContinually(entity, STR3, null);

        entity.sensors().set(STR2, "bar");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR3, "foobar");
    }

    @Test
    public void testReducingBuilder() {
        entity.addEnricher(Enrichers.builder().reducing(ImmutableList.<AttributeSensor<?>>of(STR1, STR2))
                .from(entity)
                .computing(new Concatenator())
                .publishing(STR3)
                .build()
        );

        entity.sensors().set(STR1, "foo");
        EntityTestUtils.assertAttributeEqualsContinually(entity, STR3, null);

        entity.sensors().set(STR2, "bar");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR3, "foobar");
    }

    private class Concatenator implements Function<List<?>, String> {
        @Nullable
        @Override
        public String apply(List<?> values) {
            String result = "";
            for (Object value : values) {
                if (value == null) {
                    return null;
                } else {
                    result += String.valueOf(value);
                }
            }
            return result;
        }
    }
}
