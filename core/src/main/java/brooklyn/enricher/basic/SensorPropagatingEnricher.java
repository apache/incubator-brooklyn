package brooklyn.enricher.basic;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/** an enricher policy which just listens for the target sensor(s) on a child entity and passes it up */
public class SensorPropagatingEnricher extends AbstractEnricher implements SensorEventListener<Object> {
    
    public static final Logger log = LoggerFactory.getLogger(SensorPropagatingEnricher.class);
        
    /** the entity to listen to */
    private final Entity source;
    /** the sensors to listen to */
    private final Set<Sensor<?>> sensors = Sets.newLinkedHashSet();
    
    public static SensorPropagatingEnricher newInstanceListeningToAllSensors(Entity source) {
        return newInstanceListeningToAllSensorsBut(source);
    }
    public static SensorPropagatingEnricher newInstanceListeningToAllSensorsBut(Entity source, Sensor<?>... excludes) {
        Set<Sensor<?>> excluded = ImmutableSet.copyOf(excludes);
        Set<Sensor<?>> includes = Sets.newLinkedHashSet();
        
        for (Sensor<?> it : source.getEntityClass().getSensors()) {
            if (!excluded.contains(it)) includes.add(it);
        }
        return new SensorPropagatingEnricher(source, includes);
    }

    public static SensorPropagatingEnricher newInstanceListeningTo(Entity source, Sensor<?>... includes) {
        return new SensorPropagatingEnricher(source, includes);
    }

    public SensorPropagatingEnricher(Entity source, Sensor<?>... sensors) {
        this(source, Arrays.asList(sensors));
    }
    public SensorPropagatingEnricher(Entity source, Collection<Sensor<?>> sensors) {
        this.source = source;
        this.sensors.addAll(sensors);
    }
    
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        for (Sensor<?> s: sensors) {
            subscribe(source, s, this);
        }
    }
    
    public void onEvent(SensorEvent event) {
        if (log.isTraceEnabled()) log.trace("policy {} got {}, propagating via {}", new Object[] {this, event, entity});
        //just propagate upwards
        if (event.getSensor() instanceof AttributeSensor) {
            entity.setAttribute((AttributeSensor)event.getSensor(), event.getValue());
        } else {
            entity.emit(event.getSensor(), event.getValue());
        }       
    }
    
    /** useful post-addition to emit current values */
    public void emitAllAttributes() {
        emitAllAttributes(false);
    }

    public void emitAllAttributes(boolean includeNullValues) {
        for (Sensor s: sensors) {
            if (s instanceof AttributeSensor) {
                Object v = source.getAttribute((AttributeSensor)s);
                if (v!=null || includeNullValues) entity.setAttribute((AttributeSensor)s, v);
            }
        }
    }

    /** convenience, to be called by the host */
    public SensorPropagatingEnricher addToEntityAndEmitAll(AbstractEntity host) {
        host.addEnricher(this);
        emitAllAttributes();
        return this;
    }    
}
