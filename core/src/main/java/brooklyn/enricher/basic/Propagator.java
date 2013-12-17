package brooklyn.enricher.basic;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.Map;

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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

public class Propagator extends AbstractEnricher implements SensorEventListener<Object> {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(Propagator.class);

    public static ConfigKey<Entity> PRODUCER = ConfigKeys.newConfigKey(Entity.class, "enricher.producer");

    public static ConfigKey<Collection<Sensor<?>>> PROPAGATING_ALL_BUT = ConfigKeys.newConfigKey(new TypeToken<Collection<Sensor<?>>>() {}, "enricher.propagating.propagatingAllBut");

    public static ConfigKey<Boolean> PROPAGATING_ALL = ConfigKeys.newBooleanConfigKey("enricher.propagating.propagatingAll");

    public static ConfigKey<Map<? extends Sensor<?>, ? extends Sensor<?>>> SENSOR_MAPPING = ConfigKeys.newConfigKey(new TypeToken<Map<? extends Sensor<?>, ? extends Sensor<?>>>() {}, "enricher.propagating.sensorMapping");

    protected Entity producer;
    protected Map<? extends Sensor<?>, ? extends Sensor<?>> sensorMapping;
    protected boolean propagatingAll;
    protected Predicate<Sensor<?>> sensorFilter;

    public Propagator() {
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        this.producer = getConfig(PRODUCER) == null ? entity : getConfig(PRODUCER);
        if (getConfig(PROPAGATING_ALL_BUT) == null) {
            this.sensorMapping = getConfig(SENSOR_MAPPING) == null ? ImmutableMap.<Sensor<?>, Sensor<?>>of() : getConfig(SENSOR_MAPPING);
            this.propagatingAll = Boolean.TRUE.equals(getConfig(PROPAGATING_ALL));
            this.sensorFilter = Predicates.alwaysTrue();
        } else {
            this.sensorMapping = getConfig(SENSOR_MAPPING) == null ? ImmutableMap.<Sensor<?>, Sensor<?>>of() : getConfig(SENSOR_MAPPING);
            this.propagatingAll = true;
            this.sensorFilter = new Predicate<Sensor<?>>() {
                @Override public boolean apply(Sensor<?> input) {
                    Collection<Sensor<?>> exclusions = getConfig(PROPAGATING_ALL_BUT);
                    return input != null && !exclusions.contains(input);
                }
            };
        }
            
        checkState(propagatingAll ^ sensorMapping.size() > 0,
                "Exactly one must be set of propagatingAll (%s, excluding %s), sensorMapping (%s)", propagatingAll, getConfig(PROPAGATING_ALL_BUT), sensorMapping);

        if (propagatingAll) {
            subscribe(producer, null, this);
        } else {
            for (Sensor<?> sensor : sensorMapping.keySet()) {
                subscribe(producer, sensor, this);
            }
        }
        
        emitAllAttributes();
    }

    @Override
    public void onEvent(SensorEvent<Object> event) {
        // propagate upwards
        Sensor<?> sourceSensor = event.getSensor();
        Sensor<?> destinationSensor = getDestinationSensor(sourceSensor);
        
        if (!sensorFilter.apply(sourceSensor)) {
            return; // ignoring excluded sensor
        }
        
        if (LOG.isTraceEnabled()) LOG.trace("enricher {} got {}, propagating via {}{}", 
                new Object[] {this, event, entity, (sourceSensor == destinationSensor ? "" : " (as "+destinationSensor+")")});
        
        emit((Sensor)destinationSensor, event.getValue());
    }

    /** useful post-addition to emit current values */
    public void emitAllAttributes() {
        emitAllAttributes(false);
    }

    public void emitAllAttributes(boolean includeNullValues) {
        Iterable<? extends Sensor<?>> sensorsToPupulate = propagatingAll 
                ? Iterables.filter(producer.getEntityType().getSensors(), sensorFilter)
                : sensorMapping.keySet();

        for (Sensor<?> s : sensorsToPupulate) {
            if (s instanceof AttributeSensor) {
                AttributeSensor destinationSensor = (AttributeSensor<?>) getDestinationSensor(s);
                Object v = producer.getAttribute((AttributeSensor<?>)s);
                if (v != null || includeNullValues) entity.setAttribute(destinationSensor, v);
            }
        }
    }

    private Sensor<?> getDestinationSensor(Sensor<?> sourceSensor) {
        return sensorMapping.containsKey(sourceSensor) ? sensorMapping.get(sourceSensor): sourceSensor;
    }
}
