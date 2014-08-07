/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.basic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.BrooklynDynamicType;
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
import brooklyn.util.javalang.Reflections;

import com.google.common.annotations.Beta;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

/** This is the actual type of an entity instance at runtime,
 * which can change from the static {@link EntityType}, and can change over time;
 * for this reason it does *not* implement EntityType, but 
 * callers can call {@link #getSnapshot()} to get a snapshot such instance  
 */
public class EntityDynamicType extends BrooklynDynamicType<Entity, AbstractEntity> {

    private static final Logger LOG = LoggerFactory.getLogger(EntityDynamicType.class);

    /** 
     * Effectors on this entity, by name.
     */
    // TODO support overloading; requires not using a map keyed off method name.
    private final Map<String, Effector<?>> effectors = new ConcurrentHashMap<String, Effector<?>>();

    /** 
     * Map of sensors on this entity, by name.
     */
    private final ConcurrentMap<String,Sensor<?>> sensors = new ConcurrentHashMap<String, Sensor<?>>();

    public EntityDynamicType(AbstractEntity entity) {
        this(entity.getClass(), entity);
    }
    public EntityDynamicType(Class<? extends Entity> clazz) {
        this(clazz, null);
    }
    private EntityDynamicType(Class<? extends Entity> clazz, AbstractEntity entity) {
        super(clazz, entity);
        String id = entity==null ? clazz.getName() : entity.getId();
        
        effectors.putAll(findEffectors(clazz, null));
        if (LOG.isTraceEnabled())
            LOG.trace("Entity {} effectors: {}", id, Joiner.on(", ").join(effectors.keySet()));
        
        sensors.putAll(findSensors(clazz, null));
        if (LOG.isTraceEnabled())
            LOG.trace("Entity {} sensors: {}", id, Joiner.on(", ").join(sensors.keySet()));
        
        refreshSnapshot();
    }
    
    /**
     * @deprecated since 0.7; unused code; instead use {@link #getBrooklynClass()}
     */
    @Deprecated
    public Class<? extends Entity> getEntityClass() {
        return super.getBrooklynClass();
    }
    
    public EntityType getSnapshot() {
        return (EntityType) super.getSnapshot();
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
        invalidateSnapshot();
        if (oldEffector!=null)
            instance.emit(AbstractEntity.EFFECTOR_CHANGED, newEffector.getName());
        else
            instance.emit(AbstractEntity.EFFECTOR_ADDED, newEffector.getName());
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
        invalidateSnapshot();
        instance.emit(AbstractEntity.SENSOR_ADDED, newSensor);
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
            instance.emit(AbstractEntity.SENSOR_ADDED, newSensor);
        }
    }
    
    public Sensor<?> addSensorIfAbsentWithoutPublishing(Sensor<?> newSensor) {
        Sensor<?> prev = sensors.putIfAbsent(newSensor.getName(), newSensor);
        if (prev == null) {
            invalidateSnapshot();
        }
        return prev;
    }

    /**
     * Removes the named {@link Sensor} from this entity.
     */
    public Sensor<?> removeSensor(String sensorName) {
        Sensor<?> result = sensors.remove(sensorName);
        if (result != null) {
            invalidateSnapshot();
            instance.emit(AbstractEntity.SENSOR_REMOVED, result);
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
    
    @Override
    protected EntityTypeSnapshot newSnapshot() {
        return new EntityTypeSnapshot(name, value(configKeys), sensors, effectors.values());
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
                        LOG.trace("multiple definitions for effector {} on {}; preferring {} from {} to {} from {}", new Object[] {
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
                        LOG.trace("multiple definitions for effector {} on {}; preferring {} from {} to {} from {}", new Object[] {
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
                            // probably overriding defaults, just log low level (there will be add'l logging in config key section)
                            LOG.trace("multiple definitions for config sensor {} on {}; preferring {} from {} to {} from {}", new Object[] {
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
}
