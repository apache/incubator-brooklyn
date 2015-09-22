package org.apache.brooklyn.enricher.stock;

import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.enricher.AbstractEnricher;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.ValueResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.util.Lists;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

public class Reducer extends AbstractEnricher implements SensorEventListener<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(Reducer.class);

    @SetFromFlag("producer")
    public static ConfigKey<Entity> PRODUCER = ConfigKeys.newConfigKey(Entity.class, "enricher.producer");

    public static ConfigKey<Sensor<?>> TARGET_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.targetSensor");
    public static ConfigKey<List<? extends AttributeSensor<?>>> SOURCE_SENSORS = ConfigKeys.newConfigKey(new TypeToken<List<? extends AttributeSensor<?>>>() {}, "enricher.sourceSensors");
    public static ConfigKey<Function<List<?>,?>> REDUCER_FUNCTION = ConfigKeys.newConfigKey(new TypeToken<Function<List<?>, ?>>() {}, "enricher.reducerFunction");

    protected Entity producer;

    protected List<AttributeSensor<?>> subscribedSensors;
    protected Sensor<?> targetSensor;

    protected Function<List<?>, ?> reducerFunction;


    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        Preconditions.checkNotNull(getConfig(SOURCE_SENSORS), "source sensors");

        this.producer = getConfig(PRODUCER) == null ? entity : getConfig(PRODUCER);
        List<AttributeSensor<?>> sensorListTemp = Lists.newArrayList();

        for (Object sensorO : getConfig(SOURCE_SENSORS)) {
            AttributeSensor<?> sensor = Tasks.resolving(sensorO).as(AttributeSensor.class).timeout(ValueResolver.REAL_QUICK_WAIT).context(producer).get();
            if(!sensorListTemp.contains(sensor)) {
                sensorListTemp.add(sensor);
            }
        }

        reducerFunction = config().get(REDUCER_FUNCTION);
        Preconditions.checkState(sensorListTemp.size() > 0, "Nothing to reduce");

        for (Sensor<?> sensor : sensorListTemp) {
            subscribe(producer, sensor, this);
        }

        subscribedSensors = ImmutableList.copyOf(sensorListTemp);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void onEvent(SensorEvent<Object> event) {
        Sensor<?> destinationSensor = getConfig(TARGET_SENSOR);

        List<Object> values = Lists.newArrayList();

        for (AttributeSensor<?> sourceSensor : subscribedSensors) {
            Object resolvedSensorValue = entity.sensors().get(sourceSensor);
            values.add(resolvedSensorValue);
        }

        Object result = reducerFunction.apply(values);

        if (LOG.isTraceEnabled()) LOG.trace("enricher {} got {}, propagating via {} as {}",
                new Object[] {this, event, entity, reducerFunction, destinationSensor});

        emit((Sensor)destinationSensor, result);
    }
}
