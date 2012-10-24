package brooklyn.entity.basic;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.EntityType;
import brooklyn.event.Sensor;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

public class EntityDynamicType {

    protected static final Logger LOG = LoggerFactory.getLogger(EntityDynamicType.class);

    private final Class<? extends Entity> entityClass;
    private final AbstractEntity entity;

    /** 
     * Effectors on this entity.
     * TODO support overloading; requires not using a map keyed off method name.
     */
    private final ConcurrentMap<String, Effector<?>> effectors = new ConcurrentHashMap<String, Effector<?>>();

    /** 
     * Map of sensors on this entity by name.
     */
    private final ConcurrentMap<String,Sensor<?>> sensors = new ConcurrentHashMap<String, Sensor<?>>();

    /** 
     * Map of config keys on this entity by name.
     */
    private final ConcurrentMap<String,ConfigKey<?>> configKeys = new ConcurrentHashMap<String, ConfigKey<?>>();

    private volatile EntityTypeSnapshot snapshot;
    private final AtomicBoolean snapshotValid = new AtomicBoolean(false);

    public EntityDynamicType(AbstractEntity entity) {
        this(entity.getClass(), entity);
    }
    protected EntityDynamicType(Class<? extends Entity> clazz, AbstractEntity entity) {
        this.entityClass = clazz;
        this.entity = entity;
        String id = entity==null ? clazz.getName() : entity.getId();
        
        effectors.putAll(findEffectors(clazz, entity));
        if (LOG.isTraceEnabled())
            LOG.trace("Entity {} effectors: {}", id, Joiner.on(", ").join(effectors.keySet()));
        
        sensors.putAll(findSensors(clazz, entity));
        if (LOG.isTraceEnabled())
            LOG.trace("Entity {} sensors: {}", id, Joiner.on(", ").join(sensors.keySet()));
        
        configKeys.putAll(findConfigKeys(clazz, entity));
        if (LOG.isTraceEnabled())
            LOG.trace("Entity {} config keys: {}", id, Joiner.on(", ").join(configKeys.keySet()));

        refreshSnapshot();
    }
    
    public synchronized EntityType getSnapshot() {
        return refreshSnapshot();
    }
    
    /**
     * @return the effector with the given name, or null if not found
     */
    public Effector<?> getEffector(String name) {
        return effectors.get(name);
    }
    
    /**
     * Effectors available on this entity.
     *
     * NB no work has been done supporting changing this after initialization,
     * but the idea of these so-called "dynamic effectors" has been discussed and it might be supported in future...
     */
    public Map<String,Effector<?>> getEffectors() {
        return Collections.unmodifiableMap(effectors);
    }
    
    /**
     * Sensors available on this entity.
     */
    public Map<String,Sensor<?>> getSensors() {
        return Collections.unmodifiableMap(sensors);
    }
    
    /** 
     * Convenience for finding named sensor.
     */
    public Sensor<?> getSensor(String sensorName) {
        return sensors.get(sensorName);
    }

    /**
     * ConfigKeys available on this entity.
     */
    public Map<String,ConfigKey<?>> getConfigKeys() {
        return Collections.unmodifiableMap(configKeys);
    }

    /**
     * Adds the given {@link Sensor} to this entity.
     */
    public void addSensor(Sensor<?> newSensor) {
        sensors.put(newSensor.getName(), newSensor);
        snapshotValid.set(false);
        entity.emit(AbstractEntity.SENSOR_ADDED, newSensor);
    }
    
    /**
     * Adds the given {@link Sensor}s to this entity.
     */
    public void addSensors(Iterable<? extends Sensor<?>> newSensors) {
        for (Sensor<?> sensor : newSensors) {
            addSensor(sensor);
        }
    }
    
    public void addSensorIfAbsent(Sensor<?> newSensor) {
        Sensor<?> prev = addSensorIfAbsentWithoutPublishing(newSensor);
        if (prev == null) {
            snapshotValid.set(false);
            entity.emit(AbstractEntity.SENSOR_ADDED, newSensor);
        }
    }
    
    public Sensor<?> addSensorIfAbsentWithoutPublishing(Sensor<?> newSensor) {
        return sensors.putIfAbsent(newSensor.getName(), newSensor);
    }

    /**
     * Removes the named {@link Sensor} from this entity.
     */
    public Sensor<?> removeSensor(String sensorName) {
        Sensor<?> result = sensors.remove(sensorName);
        if (result != null) {
            snapshotValid.set(false);
            entity.emit(AbstractEntity.SENSOR_REMOVED, result);
        }
        return result;
    }
    
    /**
     * Removes the named {@link Sensor} from this entity.
     */
    public boolean removeSensor(Sensor<?> sensor) {
        return (removeSensor(sensor.getName()) != null);
    }
    
    /**
     * ConfigKeys available on this entity.
     */
    public ConfigKey<?> getConfigKey(String keyName) { 
        return configKeys.get(keyName); 
    }
    
    private EntityTypeSnapshot refreshSnapshot() {
        if (snapshotValid.compareAndSet(false, true)) {
            snapshot = new EntityTypeSnapshot(entityClass.getCanonicalName(), configKeys, 
                    sensors, effectors.values());
        }
        return snapshot;
    }
    
    /**
     * Finds the effectors defined on the entity's class, statics and optionally any non-static (discouraged).
     */
    protected static Map<String,Effector<?>> findEffectors(Class<? extends Entity> clazz, Entity optionalEntity) {
        try {
            Map<String,Effector<?>> result = Maps.newLinkedHashMap();
            Map<String,Field> sources = Maps.newLinkedHashMap();
            for (Field f : clazz.getFields()) {
                if (Effector.class.isAssignableFrom(f.getType())) {
                    if (!Modifier.isStatic(f.getModifiers())) {
                        // require it to be static or we have an instance
                        LOG.warn("Discouraged use of non-static effector "+f+" defined in " + (optionalEntity!=null ? optionalEntity : clazz));
                        if (optionalEntity==null) continue;
                    }
                    Effector<?> eff = (Effector<?>) f.get(optionalEntity);
                    Effector<?> overwritten = result.put(eff.getName(), eff);
                    Field source = sources.put(eff.getName(), f);
                    if (overwritten!=null && overwritten != eff) 
                        LOG.warn("multiple definitions for effector {} on {}; preferring {} from {} to {} from {}", new Object[] {
                                eff.getName(), optionalEntity!=null ? optionalEntity : clazz, eff, f, overwritten, source});
                }
            }
            
            return result;
        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        }
    }
    
    /**
     * Finds the sensors defined on the entity's class, statics and optionally any non-static (discouraged).
     */
    protected static Map<String,Sensor<?>> findSensors(Class<? extends Entity> clazz, Entity optionalEntity) {
        try {
            Map<String,Sensor<?>> result = Maps.newLinkedHashMap();
            Map<String,Field> sources = Maps.newLinkedHashMap();
            for (Field f : clazz.getFields()) {
                if (Sensor.class.isAssignableFrom(f.getType())) {
                    if (!Modifier.isStatic(f.getModifiers())) {
                        // require it to be static or we have an instance
                        LOG.warn("Discouraged use of non-static sensor "+f+" defined in " + (optionalEntity!=null ? optionalEntity : clazz));
                        if (optionalEntity==null) continue;
                    }
                    Sensor<?> sens = (Sensor<?>) f.get(optionalEntity);
                    Sensor<?> overwritten = result.put(sens.getName(), sens);
                    Field source = sources.put(sens.getName(), f);
                    if (overwritten!=null && overwritten != sens) {
                        if (sens instanceof HasConfigKey) {
                            // probably overriding defaults, just log as debug (there will be add'l logging in config key section)
                            LOG.debug("multiple definitions for config sensor {} on {}; preferring {} from {} to {} from {}", new Object[] {
                                    sens.getName(), optionalEntity!=null ? optionalEntity : clazz, sens, f, overwritten, source});
                        } else {
                            LOG.warn("multiple definitions for sensor {} on {}; preferring {} from {} to {} from {}", new Object[] {
                                    sens.getName(), optionalEntity!=null ? optionalEntity : clazz, sens, f, overwritten, source});
                        }
                    }
                }
            }

            return result;
        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        }
    }
    
    /**
     * Finds the config keys defined on the entity's class, statics and optionally any non-static (discouraged).
     */
    protected static Map<String,ConfigKey<?>> findConfigKeys(Class<? extends Entity> clazz, Entity optionalEntity) {
        try {
            Map<String,ConfigKey<?>> result = Maps.newLinkedHashMap();
            Map<String,Field> configFields = Maps.newLinkedHashMap();
            for (Field f : clazz.getFields()) {
                boolean isConfigKey = ConfigKey.class.isAssignableFrom(f.getType());
                if (!isConfigKey) {
                    if (!HasConfigKey.class.isAssignableFrom(f.getType())) {
                        // neither ConfigKey nor HasConfigKey
                        continue;
                    }
                }
                if (!Modifier.isStatic(f.getModifiers())) {
                    // require it to be static or we have an instance
                    LOG.warn("Discouraged use of non-static config key "+f+" defined in " + (optionalEntity!=null ? optionalEntity : clazz));
                    if (optionalEntity==null) continue;
                }
                ConfigKey<?> k = isConfigKey ? (ConfigKey<?>) f.get(optionalEntity) : 
                    ((HasConfigKey<?>)f.get(optionalEntity)).getConfigKey();

                Field alternativeField = configFields.get(k.getName());
                // Allow overriding config keys (e.g. to set default values) when there is an assignable-from relationship between classes
                Field definitiveField = alternativeField != null ? inferSubbestField(alternativeField, f) : f;
                boolean skip = false;
                if (definitiveField != f) {
                    // If they refer to the _same_ instance, just keep the one we already have
                    if (alternativeField.get(optionalEntity) == f.get(optionalEntity)) skip = true;
                }
                if (skip) {
                    //nothing
                } else if (definitiveField == f) {
                    result.put(k.getName(), k);
                    configFields.put(k.getName(), f);
                } else if (definitiveField != null) {
                    if (LOG.isDebugEnabled()) LOG.debug("multiple definitions for config key {} on {}; preferring that in sub-class: {} to {}", new Object[] {
                            k.getName(), optionalEntity!=null ? optionalEntity : clazz, alternativeField, f});
                } else if (definitiveField == null) {
                    LOG.warn("multiple definitions for config key {} on {}; preferring {} to {}", new Object[] {
                            k.getName(), optionalEntity!=null ? optionalEntity : clazz, alternativeField, f});
                }
            }
            
            return result;
        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        }
    }
    
    /**
     * Gets the field that is in the sub-class; or null if one field does not come from a sub-class of the other field's class
     */
    private static Field inferSubbestField(Field f1, Field f2) {
        Class<?> c1 = f1.getDeclaringClass();
        Class<?> c2 = f2.getDeclaringClass();
        boolean isSuper1 = c1.isAssignableFrom(c2);
        boolean isSuper2 = c2.isAssignableFrom(c1);
        return (isSuper1) ? (isSuper2 ? null : f2) : (isSuper2 ? f1 : null);
    }
    
}
