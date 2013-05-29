package brooklyn.entity.basic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.text.Strings;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

/** This is the actual type of an entity instance at runtime,
 * which can change from the static {@link EntityType}, and can change over time;
 * for this reason it does *not* implement EntityType, but 
 * callers can call {@link #getSnapshot()} to get a snapshot such instance  
 */
public class EntityDynamicType {

    protected static final Logger LOG = LoggerFactory.getLogger(EntityDynamicType.class);

    private final Class<? extends Entity> entityClass;
    private final AbstractEntity entity;
    private volatile String name;
    private volatile String simpleName;
    
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
    private final ConcurrentMap<String,Field> configKeyFields = new ConcurrentHashMap<String, Field>();

    private volatile EntityTypeSnapshot snapshot;
    private final AtomicBoolean snapshotValid = new AtomicBoolean(false);

    public EntityDynamicType(AbstractEntity entity) {
        this(entity.getClass(), entity);
    }
    protected EntityDynamicType(Class<? extends Entity> clazz) {
        this(clazz, null);
    }
    private EntityDynamicType(Class<? extends Entity> clazz, AbstractEntity entity) {
        this.entityClass = clazz;
        this.entity = entity;
        setName((clazz.getCanonicalName() == null) ? clazz.getName() : clazz.getCanonicalName());
        String id = entity==null ? clazz.getName() : entity.getId();
        
        effectors.putAll(findEffectors(clazz, entity));
        if (LOG.isTraceEnabled())
            LOG.trace("Entity {} effectors: {}", id, Joiner.on(", ").join(effectors.keySet()));
        
        sensors.putAll(findSensors(clazz, entity));
        if (LOG.isTraceEnabled())
            LOG.trace("Entity {} sensors: {}", id, Joiner.on(", ").join(sensors.keySet()));
        
        buildConfigKeys(clazz, entity, configKeys, configKeyFields);
        if (LOG.isTraceEnabled())
            LOG.trace("Entity {} config keys: {}", id, Joiner.on(", ").join(configKeys.keySet()));

        refreshSnapshot();
    }
    
    public void setName(String name) {
        if (Strings.isBlank(name)) {
            throw new IllegalArgumentException("Invalid name "+(name == null ? "null" : "'"+name+"'")+"; name must be non-empty and not just white space");
        }
        this.name = name;
        this.simpleName = toSimpleName(name);
        snapshotValid.set(false);
    }
    
    private String toSimpleName(String name) {
        String simpleName = name.substring(name.lastIndexOf(".")+1);
        if (Strings.isBlank(simpleName)) simpleName = name.trim();
        return Strings.makeValidFilename(simpleName);
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

    /** field where a config key is defined, for use getting annotations. note annotations are not inherited. */
    public Field getConfigKeyField(String keyName) { 
        return configKeyFields.get(keyName); 
    }

    private EntityTypeSnapshot refreshSnapshot() {
        if (snapshotValid.compareAndSet(false, true)) {
            snapshot = new EntityTypeSnapshot(name, simpleName, configKeys, sensors, effectors.values());
        }
        return snapshot;
    }
    
    /**
     * Finds the effectors defined on the entity's class, statics and optionally any non-static (discouraged).
     */
    protected static Map<String,Effector<?>> findEffectors(Class<? extends Entity> clazz, Entity optionalEntity) {
        try {
            Map<String,Effector<?>> result = Maps.newLinkedHashMap();
            Map<String,Field> fieldSources = Maps.newLinkedHashMap();
            Map<String,Method> methodSources = Maps.newLinkedHashMap();
            for (Field f : Reflections.findPublicFieldsOrderedBySuper(clazz)) {
                if (Effector.class.isAssignableFrom(f.getType())) {
                    if (!Modifier.isStatic(f.getModifiers())) {
                        // require it to be static or we have an instance
                        LOG.warn("Discouraged/deprecated use of non-static effector field "+f+" defined in " + (optionalEntity!=null ? optionalEntity : clazz));
                        if (optionalEntity==null) continue;
                    }
                    Effector<?> eff = (Effector<?>) f.get(optionalEntity);
                    Effector<?> overwritten = result.put(eff.getName(), eff);
                    Field overwrittenFieldSource = fieldSources.put(eff.getName(), f);
                    if (overwritten!=null && !overwritten.equals(eff)) {
                        LOG.warn("multiple definitions for effector {} on {}; preferring {} from {} to {} from {}", new Object[] {
                                eff.getName(), (optionalEntity != null ? optionalEntity : clazz), eff, f, overwritten, 
                                overwrittenFieldSource});
                    }
                }
            }

            for (Method m : Reflections.findPublicMethodsOrderedBySuper(clazz)) {
                brooklyn.entity.annotation.Effector effectorAnnotation = m.getAnnotation(brooklyn.entity.annotation.Effector.class);
                if (effectorAnnotation != null) {
                    if (Modifier.isStatic(m.getModifiers())) {
                        // require it to be static or we have an instance
                        LOG.warn("Discouraged/deprecated use of static annotated effector method "+m+" defined in " + (optionalEntity!=null ? optionalEntity : clazz));
                        if (optionalEntity==null) continue;
                    }

                    Effector<?> eff = MethodEffector.create(m);
                    Effector<?> overwritten = result.put(eff.getName(), eff);
                    Method overwrittenMethodSource = methodSources.put(eff.getName(), m);
                    Field overwrittenFieldSource = fieldSources.remove(eff.getName());
                    if (overwritten != null && !overwritten.equals(eff)) {
                        LOG.warn("multiple definitions for effector {} on {}; preferring {} from {} to {} from {}", new Object[] {
                                eff.getName(), (optionalEntity != null ? optionalEntity : clazz), eff, m, overwritten, 
                                (overwrittenMethodSource != null ? overwrittenMethodSource : overwrittenFieldSource)});
                    }
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
            for (Field f : Reflections.findPublicFieldsOrderedBySuper((clazz))) {
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
    protected static void buildConfigKeys(Class<? extends Entity> clazz, AbstractEntity optionalEntity, 
            Map<String, ConfigKey<?>> configKeys, Map<String, Field> configKeyFields) {
        try {
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
                if (k==null) {
                    LOG.warn("no value defined for config key field (skipping): "+f);
                } else {
                    Field alternativeField = configKeyFields.get(k.getName());
                    // Allow overriding config keys (e.g. to set default values) when there is an assignable-from relationship between classes
                    Field definitiveField = alternativeField != null ? Reflections.inferSubbestField(alternativeField, f) : f;
                    boolean skip = false;
                    if (definitiveField != f) {
                        // If they refer to the _same_ instance, just keep the one we already have
                        if (alternativeField.get(optionalEntity) == f.get(optionalEntity)) skip = true;
                    }
                    if (skip) {
                        //nothing
                    } else if (definitiveField == f) {
                        configKeys.put(k.getName(), k);
                        configKeyFields.put(k.getName(), f);
                    } else if (definitiveField != null) {
                        if (LOG.isDebugEnabled()) LOG.debug("multiple definitions for config key {} on {}; preferring that in sub-class: {} to {}", new Object[] {
                                k.getName(), optionalEntity!=null ? optionalEntity : clazz, alternativeField, f});
                    } else if (definitiveField == null) {
                        LOG.warn("multiple definitions for config key {} on {}; preferring {} to {}", new Object[] {
                                k.getName(), optionalEntity!=null ? optionalEntity : clazz, alternativeField, f});
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw Exceptions.propagate(e);
        }
    }
}
