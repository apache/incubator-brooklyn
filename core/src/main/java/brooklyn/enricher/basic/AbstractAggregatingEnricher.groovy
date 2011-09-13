package brooklyn.enricher.basic

import groovy.lang.Closure;

import java.util.List;
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.Sensor;

/**
 * AggregatingEnrichers implicitly subscribes to the same sensor on all entities inside an
 * {@link Group}
 */
public abstract class AbstractAggregatingEnricher<T> extends AbstractEnricher {
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractAggregatingEnricher.class)
    
    private Sensor<T> source
    protected Sensor<?> target
    protected T defaultValue
    
    protected Map<Entity, T> values = new HashMap<Entity, T>()
    
    public AbstractAggregatingEnricher(List<Entity> producer, Sensor<T> source, Sensor<?> target, T defaultValue=null) {
        producer.each { values.put(it, defaultValue) }
        this.source = source
        this.target = target
        this.defaultValue = defaultValue
    }
    
    public void addProducer(Entity producer) {
        LOG.info "$this linked ($producer, $source) to $target"
        values.put(producer, defaultValue)
        subscribe(producer, source, this)
    }
    
    public T removeProducer(Entity producer) {
        LOG.info "$this unlinked ($producer, $source) from $target"
        unsubscribe(producer)
        values.remove(producer)
    }
    
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity)
        AbstractAggregatingEnricher<T> reference = this // groovy doesn't seem to like 'this' inside closures
        values.each { reference.subscribe(it.key, source, reference) }
    }
}
