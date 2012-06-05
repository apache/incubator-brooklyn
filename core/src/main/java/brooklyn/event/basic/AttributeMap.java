package brooklyn.event.basic;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;

import com.google.common.base.Preconditions;

/**
 * A {@link Map} of {@link Entity} attribute values.
 */
public final class AttributeMap implements Serializable {

    static final Logger log = LoggerFactory.getLogger(AttributeMap.class);

    private final static Object NULL = new Object();

    final EntityLocal entity;

    /**
     * The values are stored as nested maps, with the key being the constituent parts of the sensor.
     */
    // Note that we synchronize on the top-level map, to handle concurrent updates and and gets (ENGR-2111)
    private final ConcurrentMap<Collection<String>, Object> values = new ConcurrentHashMap<Collection<String>, Object>();

    /**
     * Creates a new AttributeMap.
     *
     * @param entity the EntityLocal this AttributeMap belongs to.
     * @throws IllegalArgumentException if entity is null
     */
    public AttributeMap(EntityLocal entity) {
        Preconditions.checkNotNull(entity, "entity must be specified");
        this.entity = entity;
    }

    /**
     * Updates the value.
     *
     * @param path the path to the value.
     * @param newValue the new value
     * @return the old value.
     * @throws IllegalArgumentException if path is null or empty
     */
    // TODO path must be ordered(and legal to contain duplicates like "a.b.a"; list would be better
    public <T> T update(Collection<String> path, T newValue) {
        checkPath(path);

        if (newValue == null) {
            newValue = typedNull();
        }

        if (log.isTraceEnabled()) {
            log.trace("setting sensor $path=$newValue for $entity");
        }

        T oldValue = (T) values.put(path, newValue);
        return (isNull(oldValue)) ? null : oldValue;
    }

    private void checkPath(Collection<String> path) {
        Preconditions.checkNotNull(path, "path can't be null");
        Preconditions.checkArgument(!path.isEmpty(), "path can't be empty");
    }

    public <T> void update(Sensor<T> sensor, T newValue) {
        Preconditions.checkArgument(sensor instanceof AttributeSensor, "AttributeMap can only update an attribute sensor's value, not %s", sensor);
        T oldValue = (T) update(sensor.getNameParts(), newValue);
        ((AbstractEntity)entity).emitInternal(sensor, newValue);
    }

    /**
     * Gets the value
     *
     * @param path the path of the value to get
     * @return the value
     * @throws IllegalArgumentException path is null or empty.
     */
    public Object getValue(Collection<String> path) {
        // TODO previously this would return a map of the sub-tree if the path matched a prefix of a group of sensors, 
        // or the leaf value if only one value. Arguably that is not required - what is/was the use-case?
        // 
        checkPath(path);
        Object result = values.get(path);
        return (isNull(result)) ? null : result;
    }

    public <T> T getValue(Sensor<T> sensor) {
        return (T) getValue(sensor.getNameParts());
    }

    @SuppressWarnings("unchecked")
    private <T> T typedNull() {
        return (T) NULL;
    }
    
    @SuppressWarnings("unchecked")
    private boolean isNull(Object t) {
        return t == NULL;
    }

    //ENGR-1458  interesting to use property change. if it works great.
    //if there are any issues with it consider instead just making attributesInternal private,
    //and forcing all changes to attributesInternal to go through update(AttributeSensor,...)
    //and do the publishing there...  (please leave this comment here for several months until we know... it's Jun 2011 right now)
//    protected final PropertiesSensorAdapter propertiesAdapter = new PropertiesSensorAdapter(this, attributes)
    //if wee need this, fold the capabilities into this class.
}
