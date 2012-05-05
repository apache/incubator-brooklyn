package brooklyn.enricher.basic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.trait.Changeable
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener

import com.google.common.base.Predicate
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap


/**
 * AggregatingEnrichers implicitly subscribes to the same sensor<S> on all entities inside an
 * {@link Group} and should emit an aggregate<T> on the target sensor
 */
public abstract class AbstractAggregatingEnricher<S,T> extends AbstractEnricher implements SensorEventListener<S> {
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractAggregatingEnricher.class)
    
    Sensor<S> source
    protected Sensor<T> target
    protected S defaultValue

    Set<Entity> producers
    List<Entity> hardCodedProducers
    boolean allMembers
    Predicate<Entity> filter
    
    /**
     * Users of values should either on it synchronize when iterating over its entries or use
     * copyOfValues to obtain an immutable copy of the map.
     */
    // We use a synchronizedMap over a ConcurrentHashMap for entities that store null values.
    protected final Map<Entity, S> values = Collections.synchronizedMap(new LinkedHashMap<Entity, S>())
    
    public AbstractAggregatingEnricher(Map<String,?> flags, Sensor<S> source, Sensor<T> target, S defaultValue=null) {
        this.source = source
        this.target = target
        this.defaultValue = defaultValue
        hardCodedProducers = flags.producers ?: []
        allMembers = flags.allMembers ?: false
        filter = (flags.filter in Closure) ? (flags.filter as Predicate) : (flags.filter ?: Predicates.alwaysTrue())
    }

    /**
     * @deprecated Instead use AbstractAggregatingEnricher(source, target, defaultValue, producers:producers, allMembers:true)
     */
    @Deprecated 
    public AbstractAggregatingEnricher(List<Entity> producers, Sensor<S> source, Sensor<T> target, S defaultValue=null) {
        this(source, target, defaultValue, producers:producers, allMembers:true)
    }

    public void addProducer(Entity producer) {
        if (LOG.isDebugEnabled()) LOG.debug "$this linked ($producer, $source) to $target"
        values.put(producer, producer.getAttribute(source) ?: defaultValue)
        subscribe(producer, source, this)
        onUpdated()
    }
    
    // TODO If producer removed but then get (queued) event from it after this method returns,  
    public S removeProducer(Entity producer) {
        if (LOG.isDebugEnabled()) LOG.debug "$this unlinked ($producer, $source) from $target"
        unsubscribe(producer)
        values.remove(producer)
        onUpdated()
    }
    
    @Override
    public void onEvent(SensorEvent<S> event) {
        Entity e = event.getSource();
        synchronized (values) {
            if (values.containsKey(e)) {
                values.put(e, event.getValue())
            } else {
                if (LOG.isDebugEnabled()) LOG.debug("$this received event for unknown producer ($e); presumably that producer has recently been removed");
            }
        }
        onUpdated();
    }

    /**
     * Called whenever the values for the set of producers changes (e.g. on an event, or on a member added/removed).
     * Defaults to no-op
     */
    // TODO should this be abstract?
    protected void onUpdated() {
        // no-op
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity)
        
        for (Entity producer : hardCodedProducers) {
            if (filter.apply(producer)) {
                addProducer(producer)
            }
        }
        
        if (allMembers) {
            subscribe(entity, Changeable.MEMBER_ADDED, { if (filter.apply(it.value)) addProducer(it.value) } as SensorEventListener)
            subscribe(entity, Changeable.MEMBER_REMOVED, { removeProducer(it.value) } as SensorEventListener)
            
            if (entity instanceof Group) {
                for (Entity member : ((Group)entity).getMembers()) {
                    if (filter.apply(member)) {
                        addProducer(member)
                    }
                }
            }
        }
    }
    
    protected Map<Entity, S> copyOfValues() {
        synchronized (values) {
            return ImmutableMap.copyOf(values)
        }
    }
}
