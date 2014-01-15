package brooklyn.enricher.basic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.base.Function;
import com.google.common.reflect.TypeToken;

public class Transformer<T,U> extends AbstractEnricher implements SensorEventListener<T> {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(Transformer.class);

    public static ConfigKey<Function<?, ?>> TRANSFORMATION = ConfigKeys.newConfigKey(new TypeToken<Function<?, ?>>() {}, "enricher.transformation");

    public static ConfigKey<Entity> PRODUCER = ConfigKeys.newConfigKey(Entity.class, "enricher.producer");

    public static ConfigKey<Sensor<?>> SOURCE_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.sourceSensor");

    public static ConfigKey<Sensor<?>> TARGET_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.targetSensor");

    protected Function<? super T, ? extends U> transformation;
    protected Entity producer;
    protected Sensor<T> sourceSensor;
    protected Sensor<U> targetSensor;

    public Transformer() {
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        this.transformation = (Function<? super T, ? extends U>) getRequiredConfig(TRANSFORMATION);
        this.producer = getConfig(PRODUCER) == null ? entity: getConfig(PRODUCER);
        this.sourceSensor = (Sensor<T>) getRequiredConfig(SOURCE_SENSOR);
        this.targetSensor = (Sensor<U>) getRequiredConfig(TARGET_SENSOR);
        subscribe(producer, sourceSensor, this);
        
        if (sourceSensor instanceof AttributeSensor) {
            Object value = producer.getAttribute((AttributeSensor<?>)sourceSensor);
            // TODO Aled didn't you write a convenience to "subscribeAndRunIfSet" ? (-Alex)
            //      Unfortunately not yet!
            if (value!=null) {
                onEvent(new BasicSensorEvent(sourceSensor, producer, value));
            }
        }
    }

    @Override
    public void onEvent(SensorEvent<T> event) {
        emit(targetSensor, compute(event.getValue()));
    }

    protected U compute(T value) {
        Object result = transformation.apply(value);
        return TypeCoercions.coerce(result, targetSensor.getTypeToken());
    }
}
