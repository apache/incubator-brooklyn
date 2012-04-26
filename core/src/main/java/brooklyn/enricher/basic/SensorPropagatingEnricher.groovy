package brooklyn.enricher.basic;

import java.util.Set

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener

import com.google.common.collect.ImmutableSet

/** an enricher policy which just listens for the target sensor(s) on a child entity and passes it up */
public class SensorPropagatingEnricher extends AbstractEnricher implements SensorEventListener {
    
    public static final Logger log = LoggerFactory.getLogger(SensorPropagatingEnricher.class);
        
    /** the entity to listen to */
    private final Entity source
    /** the sensors to listen to */
    private final Set<Sensor> sensors = []
    
    public static SensorPropagatingEnricher newInstanceListeningToAllSensors(Entity source) {
        newInstanceListeningToAllSensorsBut(source);
    }
    public static SensorPropagatingEnricher newInstanceListeningToAllSensorsBut(Entity source, Sensor ...excludes) {
        Set excluded = ImmutableSet.copyOf(excludes);
        Set includes = []
        
        source.entityClass.sensors.each {
            if (!excluded.contains(it)) includes << it
        }
        return new SensorPropagatingEnricher(source, includes);
    }

    public static SensorPropagatingEnricher newInstanceListeningTo(Entity source, Sensor ...includes) {
        return new SensorPropagatingEnricher(source, includes);
    }

    public SensorPropagatingEnricher(Entity source, Sensor ...sensors) {
        this(source, Arrays.asList(sensors));
    }
    public SensorPropagatingEnricher(Entity source, Collection<Sensor> sensors) {
        this.source = source
        this.sensors.addAll(sensors);
    }
    
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity)
        for (Sensor s: sensors) {
            subscribe(source, s, this)
        }
    }
    
    void onEvent(SensorEvent event) {
        if (log.isTraceEnabled()) log.trace("policy {} got {}, propagating via {}", this, event, entity);
        //just propagate upwards
        if (event.sensor in AttributeSensor) {
            entity.setAttribute(event.sensor, event.value);
        } else {
            entity.emit(event.sensor, event.value);
        }       
    }
    
    /** useful post-addition to emit current values */
    public void emitAllAttributes() {
        for (Sensor s: sensors) {
            if (s in AttributeSensor) {
                def v = source.getAttribute(s);
                if (v!=null) entity.setAttribute(s, v);
            }
        }
    }

    /** convenience, to be called by the host */
    public SensorPropagatingEnricher addToEntityAndEmitAll(AbstractEntity host) {
        host.addEnricher(this);
        emitAllAttributes();
        this;
    }    
}
