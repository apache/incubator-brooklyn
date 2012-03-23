package brooklyn.enricher.basic

import java.util.List
import java.util.Map
import java.util.Map.Entry

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.collect.ImmutableMap

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.Sensor

/**
 * AggregatingEnrichers implicitly subscribes to the same sensor<S> on all entities inside an
 * {@link Group} and should emit an aggregate<T> on the target sensor
 */
public abstract class AbstractAggregatingEnricher<S,T> extends AbstractEnricher {
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractAggregatingEnricher.class)
    
    Sensor<S> source
    protected Sensor<T> target
    protected S defaultValue

    /**
     * Users of values should either on it synchronize when iterating over its entries or use
     * copyOfValues to obtain an immutable copy of the map.
     */
    // We use a synchronizedMap over a ConcurrentHashMap for entities that store null values.
    protected Map<Entity, S> values = Collections.synchronizedMap(new LinkedHashMap<Entity, S>())
    
    public AbstractAggregatingEnricher(List<Entity> producers, Sensor<S> source, Sensor<T> target, S defaultValue=null) {
        for (Entity producer : producers) { values.put(producer, defaultValue) }
        this.source = source
        this.target = target
        this.defaultValue = defaultValue
    }
    
    public void addProducer(Entity producer) {
        LOG.debug "$this linked ($producer, $source) to $target"
        values.put(producer, defaultValue)
        subscribe(producer, source, this)
    }
    
    public S removeProducer(Entity producer) {
        LOG.debug "$this unlinked ($producer, $source) from $target"
        unsubscribe(producer)
        values.remove(producer)
    }
    
    protected Map<Entity, S> copyOfValues() {
        synchronized (values) {
            return ImmutableMap.copyOf(values)
        }
    }
    
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity)
        for (Entry<Entity, S> entry : copyOfValues()) {
            subscribe(entry.getKey(), source, this)
        }
    }
}
