package brooklyn.entity.basic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
import brooklyn.entity.effector.EffectorAndBody;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.EffectorTasks.EffectorBodyTaskFactory;
import brooklyn.entity.effector.EffectorTasks.EffectorTaskFactory;
import brooklyn.entity.effector.EffectorWithBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicConfigKey.BasicConfigKeyOverwriting;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/** This is the actual type of an entity instance at runtime,
 * which can change from the static {@link EntityType}, and can change over time;
 * for this reason it does *not* implement EntityType, but 
 * callers can call {@link #getSnapshot()} to get a snapshot such instance  
 */
public class EntityDynamicType {

    private static final Logger LOG = LoggerFactory.getLogger(EntityDynamicType.class);

    private final Class<? extends Entity> entityClass;
    private final AbstractEntity entity;
    private volatile String name;
    
    /** 
     * Effectors on this entity, by name.
     */
    // TODO support overloading; requires not using a map keyed off method name.
    private final Map<String, Effector<?>> effectors = new ConcurrentHashMap<String, Effector<?>>();

    /** 
     * Map of sensors on this entity, by name.
     */
    private final ConcurrentMap<String,Sensor<?>> sensors = new ConcurrentHashMap<String, Sensor<?>>();

    /** 
     * Map of config keys (and their fields) on this entity, by name.
     */
    private final Map<String,FieldAndValue<ConfigKey<?>>> configKeys = new ConcurrentHashMap<String, FieldAndValue<ConfigKey<?>>>();

    private volatile EntityTypeSnapshot snapshot;
    private final AtomicBoolean snapshotValid = new AtomicBoolean(false);

    public EntityDynamicType(AbstractEntity entity) {
        this(entity.getClass(), entity);
    }
    public EntityDynamicType(Class<? extends Entity> clazz) {
        this(clazz, null);
    }
    private EntityDynamicType(Class<? extends Entity> clazz, AbstractEntity entity) {
        this.entityClass = clazz;
        this.entity = entity;
        // NB: official name is usu injected later, from AbstractEntity.setManagementContext
        setName((clazz.getCanonicalName() == null) ? clazz.getName() : clazz.getCanonicalName());
        
        String id = entity==null ? clazz.getName() : entity.getId();
        
        effectors.putAll(findEffectors(clazz, null));
        if (LOG.isTraceEnabled())
            LOG.trace("Entity {} effectors: {}", id, Joiner.on(", ").join(effectors.keySet()));
        
        sensors.putAll(findSensors(clazz, null));
        if (LOG.isTraceEnabled())
            LOG.trace("Entity {} sensors: {}", id, Joiner.on(", ").join(sensors.keySet()));
        
        buildConfigKeys(clazz, null, configKeys);
        if (LOG.isTraceEnabled())
            LOG.trace("Entity {} config keys: {}", id, Joiner.on(", ").join(configKeys.keySet()));

        refreshSnapshot();
    }
    
    public void setName(String name) {
        if (Strings.isBlank(name)) {
            throw new IllegalArgumentException("Invalid name "+(name == null ? "null" : "'"+name+"'")+"; name must be non-empty and not just white space");
        }
        this.name = name;
        snapshotValid.set(false);
    }
    
    public synchronized EntityType getSnapshot() {
        return refreshSnapshot();
    }
    
    public Class<? extends Entity> getEntityClass() {
        return entityClass;
    }
    
    // --------------------------------------------------
    
    /**
     * @return the effector with the given name, or null if not found
     */
    public Effector<?> getEffector(String name) {
        return effectors.get(name);
    }
    
    /**
     * Effectors available on this entity.
     */
    public Map<String,Effector<?>> getEffectors() {
        return Collections.unmodifiableMap(effectors);
    }
    
    /**
     * Adds the given {@link Effector} to this entity.
     */
    @Beta
    public void addEffector(Effector<?> newEffector) {
        Effector<?> oldEffector = effectors.put(newEffector.getName(), newEffector);
        snapshotValid.set(false);
        if (oldEffector!=null)
            entity.emit(AbstractEntity.EFFECTOR_CHANGED, newEffector.getName());
        else
            entity.emit(AbstractEntity.EFFECTOR_ADDED, newEffector.getName());
    }

    /** Adds an effector with an explicit body */
    @Beta
    public <T> void addEffector(Effector<T> effector, EffectorTaskFactory<T> body) {
        addEffector(new EffectorAndBody<T>(effector, body));
    }
    /** Adds an effector with an explicit body */
    @Beta
    public <T> void addEffector(Effector<T> effector, EffectorBody<T> body) {
        addEffector(effector, new EffectorBodyTaskFactory<T>(body));
    }

    // --------------------------------------------------
    
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
            entity.emit(AbstractEntity.SENSOR_ADDED, newSensor);
        }
    }
    
    public Sensor<?> addSensorIfAbsentWithoutPublishing(Sensor<?> newSensor) {
        Sensor<?> prev = sensors.putIfAbsent(newSensor.getName(), newSensor);
        if (prev == null) {
            snapshotValid.set(false);
        }
        return prev;
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
    
    // --------------------------------------------------
    
    // --------------------------------------------------
    
    /**
     * ConfigKeys available on this entity.
     */
    public Map<String,ConfigKey<?>> getConfigKeys() {
        return Collections.unmodifiableMap(value(configKeys));
    }

    /**
     * ConfigKeys available on this entity.
     */
    public ConfigKey<?> getConfigKey(String keyName) { 
        return value(configKeys.get(keyName)); 
    }

    /** field where a config key is defined, for use getting annotations. note annotations are not inherited. */
    public Field getConfigKeyField(String keyName) { 
        return field(configKeys.get(keyName)); 
    }

    private EntityTypeSnapshot refreshSnapshot() {
        if (snapshotValid.compareAndSet(false, true)) {
            snapshot = new EntityTypeSnapshot(name, value(configKeys), sensors, effectors.values());
        }
        return snapshot;
    }
    
    /**
     * Finds the effectors defined on the entity's class, statics and optionally any non-static (discouraged).
     */
    public static Map<String,Effector<?>> findEffectors(Class<? extends Entity> clazz, Entity optionalEntity) {
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
                    if (eff==null) {
                        LOG.warn("Effector "+f+" undefined for "+clazz+" ("+optionalEntity+")");
                        continue;
                    }
                    Effector<?> overwritten = result.put(eff.getName(), eff);
                    Field overwrittenFieldSource = fieldSources.put(eff.getName(), f);
                    if (overwritten!=null && !Effectors.sameInstance(overwritten, eff)) {
                        LOG.debug("multiple definitions for effector {} on {}; preferring {} from {} to {} from {}", new Object[] {
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
                    Effector<?> overwritten = result.get(eff.getName());
                    
                    if ((overwritten instanceof EffectorWithBody) && !(overwritten instanceof MethodEffector<?>)) {
                        // don't let annotations on methods override a static, unless that static is a MethodEffector
                        // TODO not perfect, but approx right; we should clarify whether we prefer statics or methods
                    } else {
                        result.put(eff.getName(), eff);
                        Method overwrittenMethodSource = methodSources.put(eff.getName(), m);
                        Field overwrittenFieldSource = fieldSources.remove(eff.getName());
                        LOG.debug("multiple definitions for effector {} on {}; preferring {} from {} to {} from {}", new Object[] {
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
    public static Map<String,Sensor<?>> findSensors(Class<? extends Entity> clazz, Entity optionalEntity) {
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
     * Prefers keys which overwrite other keys, and prefers keys which are lower in the hierarchy;
     * logs warnings if there are two conflicting keys which don't have an overwriting relationship.
     */
    protected static void buildConfigKeys(Class<? extends Entity> clazz, AbstractEntity optionalEntity, 
            Map<String, FieldAndValue<ConfigKey<?>>> configKeys) {
        ListMultimap<String,FieldAndValue<ConfigKey<?>>> configKeysAll = 
                ArrayListMultimap.<String, FieldAndValue<ConfigKey<?>>>create();
        
        for (Field f : FlagUtils.getAllFields(clazz)) {
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
            try {
                ConfigKey<?> k = isConfigKey ? (ConfigKey<?>) f.get(optionalEntity) : 
                    ((HasConfigKey<?>)f.get(optionalEntity)).getConfigKey();
                
                if (k==null) {
                    LOG.warn("no value defined for config key field (skipping): "+f);
                } else {
                    configKeysAll.put(k.getName(), new FieldAndValue<ConfigKey<?>>(f, k));
                }
                
            } catch (IllegalAccessException e) {
                LOG.warn("cannot access config key (skipping): "+f);
            }
        }
        LinkedHashSet<String> keys = new LinkedHashSet<String>(configKeysAll.keys());
        for (String kn: keys) {
            List<FieldAndValue<ConfigKey<?>>> kk = Lists.newArrayList(configKeysAll.get(kn));
            if (kk.size()>1) {
                // remove anything which extends another value in the list
                for (FieldAndValue<ConfigKey<?>> k: kk) {
                    ConfigKey<?> key = value(k);
                    if (key instanceof BasicConfigKeyOverwriting) {                            
                        ConfigKey<?> parent = ((BasicConfigKeyOverwriting<?>)key).getParentKey();
                        // find and remove the parent from consideration
                        for (FieldAndValue<ConfigKey<?>> k2: kk) {
                            if (value(k2) == parent)
                                configKeysAll.remove(kn, k2);
                        }
                    }
                }
                kk = Lists.newArrayList(configKeysAll.get(kn));
            }
            // multiple keys, not overwriting; if their values are the same then we don't mind
            FieldAndValue<ConfigKey<?>> best = null;
            for (FieldAndValue<ConfigKey<?>> k: kk) {
                if (best==null) {
                    best=k;
                } else {
                    Field lower = Reflections.inferSubbestField(k.field, best.field);
                    ConfigKey<? extends Object> lowerV = lower==null ? null : lower.equals(k.field) ? k.value : best.value;
                    if (best.value == k.value) {
                        // same value doesn't matter which we take (but take lower if there is one)
                        if (LOG.isDebugEnabled()) 
                            LOG.debug("multiple definitions for config key {} on {}; same value {}; " +
                                    "from {} and {}, preferring {}", 
                                    new Object[] {
                                    best.value.getName(), optionalEntity!=null ? optionalEntity : clazz,
                                    best.value.getDefaultValue(),
                                    k.field, best.field, lower});
                        best = new FieldAndValue<ConfigKey<?>>(lower!=null ? lower : best.field, best.value);
                    } else if (lower!=null) {
                        // different value, but one clearly lower (in type hierarchy)
                        if (LOG.isDebugEnabled()) 
                            LOG.debug("multiple definitions for config key {} on {}; " +
                                    "from {} and {}, preferring lower {}, value {}", 
                                    new Object[] {
                                    best.value.getName(), optionalEntity!=null ? optionalEntity : clazz,
                                    k.field, best.field, lower,
                                    lowerV.getDefaultValue() });
                        best = new FieldAndValue<ConfigKey<?>>(lower, lowerV);
                    } else {
                        // different value, neither one lower than another in hierarchy
                        LOG.warn("multiple ambiguous definitions for config key {} on {}; " +
                                "from {} and {}, values {} and {}; " +
                                "keeping latter (arbitrarily)", 
                                new Object[] {
                                best.value.getName(), optionalEntity!=null ? optionalEntity : clazz,
                                k.field, best.field, 
                                k.value.getDefaultValue(), best.value.getDefaultValue() });
                        // (no change)
                    }
                }
            }
            if (best==null) {
                // shouldn't happen
                LOG.error("Error - no matching config key from "+kk+" in class "+clazz+", even though had config key name "+kn);
                continue;
            } else {
                configKeys.put(best.value.getName(), best);
            }
        }
    }
    
    private static class FieldAndValue<V> {
        public final Field field;
        public final V value;
        public FieldAndValue(Field field, V value) {
            this.field = field;
            this.value = value;
        }
        @Override
        public String toString() {
            return Objects.toStringHelper(this).add("field", field).add("value", value).toString();
        }
    }
    
    private static <V> V value(FieldAndValue<V> fv) {
        if (fv==null) return null;
        return fv.value;
    }
    
    private static Field field(FieldAndValue<?> fv) {
        if (fv==null) return null;
        return fv.field;
    }

    @SuppressWarnings("unused")
    private static <V> Collection<V> value(Collection<FieldAndValue<V>> fvs) {
        List<V> result = new ArrayList<V>();
        for (FieldAndValue<V> fv: fvs) result.add(value(fv));
        return result;
    }

    private static <K,V> Map<K,V> value(Map<K,FieldAndValue<V>> fvs) {
        Map<K,V> result = new LinkedHashMap<K,V>();
        for (K key: fvs.keySet())
            result.put(key, value(fvs.get(key)));
        return result;
    }

}
