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
package org.apache.brooklyn.core.entity.lifecycle;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.api.sensor.EnricherSpec.ExtensibleEnricherSpec;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigInheritance;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.BrooklynLogging;
import org.apache.brooklyn.core.BrooklynLogging.LoggingLevel;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.enricher.AbstractEnricher;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityAdjuncts;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle.Transition;
import org.apache.brooklyn.enricher.stock.AbstractMultipleSensorAggregator;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.enricher.stock.UpdatingMap;
import org.apache.brooklyn.util.collections.CollectionFunctionals;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.collections.QuorumCheck;
import org.apache.brooklyn.util.core.task.ValueResolver;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;

/** Logic, sensors and enrichers, and conveniences, for computing service status */ 
public class ServiceStateLogic {

    private static final Logger log = LoggerFactory.getLogger(ServiceStateLogic.class);
    
    public static final AttributeSensor<Boolean> SERVICE_UP = Attributes.SERVICE_UP;
    public static final AttributeSensor<Map<String,Object>> SERVICE_NOT_UP_INDICATORS = Attributes.SERVICE_NOT_UP_INDICATORS;
    public static final AttributeSensor<Map<String,Object>> SERVICE_NOT_UP_DIAGNOSTICS = Attributes.SERVICE_NOT_UP_DIAGNOSTICS;
    
    public static final AttributeSensor<Lifecycle> SERVICE_STATE_ACTUAL = Attributes.SERVICE_STATE_ACTUAL;
    public static final AttributeSensor<Lifecycle.Transition> SERVICE_STATE_EXPECTED = Attributes.SERVICE_STATE_EXPECTED;
    public static final AttributeSensor<Map<String,Object>> SERVICE_PROBLEMS = Attributes.SERVICE_PROBLEMS;

    /** static only; not for instantiation */
    private ServiceStateLogic() {}

    public static <TKey,TVal> TVal getMapSensorEntry(EntityLocal entity, AttributeSensor<Map<TKey,TVal>> sensor, TKey key) {
        Map<TKey, TVal> map = entity.getAttribute(sensor);
        if (map==null) return null;
        return map.get(key);
    }
    
    @SuppressWarnings("unchecked")
    public static <TKey,TVal> void clearMapSensorEntry(EntityLocal entity, AttributeSensor<Map<TKey,TVal>> sensor, TKey key) {
        updateMapSensorEntry(entity, sensor, key, (TVal)Entities.REMOVE);
    }

    /** update the given key in the given map sensor */
    public static <TKey,TVal> void updateMapSensorEntry(EntityLocal entity, AttributeSensor<Map<TKey,TVal>> sensor, final TKey key, final TVal v) {
        /*
         * Important to *not* modify the existing attribute value; must make a copy, modify that, and publish.
         * This is because a Propagator enricher will set this same value on another entity. There was very
         * strange behaviour when this was done for a SERVICE_UP_INDICATORS sensor - the updates done here 
         * applied to the attribute of both entities!
         * 
         * Need to do this update atomically (i.e. sequentially) because there is no threading control for
         * what is calling updateMapSensorEntity. It is called directly on start, on initialising enrichers,
         * and in event listeners. These calls could be concurrent.
         */
        Function<Map<TKey,TVal>, Maybe<Map<TKey,TVal>>> modifier = new Function<Map<TKey,TVal>, Maybe<Map<TKey,TVal>>>() {
            @Override public Maybe<Map<TKey, TVal>> apply(Map<TKey, TVal> map) {
                boolean created = (map==null);
                if (created) map = MutableMap.of();
                
                boolean changed;
                if (v == Entities.REMOVE) {
                    changed = map.containsKey(key);
                    if (changed) {
                        map = MutableMap.copyOf(map);
                        map.remove(key);
                    }
                } else {
                    TVal oldV = map.get(key);
                    if (oldV==null) {
                        changed = (v!=null || !map.containsKey(key));
                    } else {
                        changed = !oldV.equals(v);
                    }
                    if (changed) {
                        map = MutableMap.copyOf(map);
                        map.put(key, (TVal)v);
                    }
                }
                if (changed || created) {
                    return Maybe.of(map);
                } else {
                    return Maybe.absent();
                }
            }
        };
        
        if (!Entities.isNoLongerManaged(entity)) { 
            entity.sensors().modify(sensor, modifier);
        }
    }
    
    public static void setExpectedState(Entity entity, Lifecycle state) {
        if (state==Lifecycle.RUNNING) {
            Boolean up = ((EntityInternal)entity).getAttribute(Attributes.SERVICE_UP);
            if (!Boolean.TRUE.equals(up) && !Boolean.TRUE.equals(Entities.isReadOnly(entity))) {
                // pause briefly to allow any recent problem-clearing processing to complete
                Stopwatch timer = Stopwatch.createStarted();
                boolean nowUp = Repeater.create()
                        .every(ValueResolver.REAL_QUICK_PERIOD)
                        .limitTimeTo(ValueResolver.PRETTY_QUICK_WAIT)
                        .until(entity, EntityPredicates.attributeEqualTo(Attributes.SERVICE_UP, true))
                        .run();
                if (nowUp) {
                    log.debug("Had to wait "+Duration.of(timer)+" for "+entity+" "+Attributes.SERVICE_UP+" to be true before setting "+state);
                } else {
                    log.warn("Service is not up when setting "+state+" on "+entity+"; delayed "+Duration.of(timer)+" "
                        + "but "+Attributes.SERVICE_UP+" did not recover from "+up+"; not-up-indicators="+entity.getAttribute(Attributes.SERVICE_NOT_UP_INDICATORS));
                }
            }
        }
        ((EntityInternal)entity).sensors().set(Attributes.SERVICE_STATE_EXPECTED, new Lifecycle.Transition(state, new Date()));
        
        Maybe<Enricher> enricher = EntityAdjuncts.tryFindWithUniqueTag(entity.enrichers(), ComputeServiceState.DEFAULT_ENRICHER_UNIQUE_TAG);
        if (enricher.isPresent() && enricher.get() instanceof ComputeServiceState) {
            ((ComputeServiceState)enricher.get()).onEvent(null);
        }
    }
    public static Lifecycle getExpectedState(Entity entity) {
        Transition expected = entity.getAttribute(Attributes.SERVICE_STATE_EXPECTED);
        if (expected==null) return null;
        return expected.getState();
    }
    public static boolean isExpectedState(Entity entity, Lifecycle state) {
        return getExpectedState(entity)==state;
    }
    
    public static class ServiceNotUpLogic {
        public static final String DEFAULT_ENRICHER_UNIQUE_TAG = "service.isUp if no service.notUp.indicators";
        
        /** static only; not for instantiation */
        private ServiceNotUpLogic() {}
        
        public static final EnricherSpec<?> newEnricherForServiceUpIfNotUpIndicatorsEmpty() {
            return Enrichers.builder()
                .transforming(SERVICE_NOT_UP_INDICATORS).<Object>publishing(Attributes.SERVICE_UP)
                .suppressDuplicates(true)
                .computing(
                    Functionals.<Map<String,?>>
                        ifNotEquals(null).<Object>apply(Functions.forPredicate(CollectionFunctionals.<String>mapSizeEquals(0)))
                        .defaultValue(Entities.REMOVE) )
                .uniqueTag(DEFAULT_ENRICHER_UNIQUE_TAG)
                .build();
        }
        
        /** puts the given value into the {@link Attributes#SERVICE_NOT_UP_INDICATORS} map as if the 
         * {@link UpdatingMap} enricher for the given key */
        public static void updateNotUpIndicator(EntityLocal entity, String key, Object value) {
            updateMapSensorEntry(entity, Attributes.SERVICE_NOT_UP_INDICATORS, key, value);
        }
        /** clears any entry for the given key in the {@link Attributes#SERVICE_NOT_UP_INDICATORS} map */
        public static void clearNotUpIndicator(EntityLocal entity, String key) {
            clearMapSensorEntry(entity, Attributes.SERVICE_NOT_UP_INDICATORS, key);
        }
        /** as {@link #updateNotUpIndicator(EntityLocal, String, Object)} using the given sensor as the key */
        public static void updateNotUpIndicator(EntityLocal entity, Sensor<?> sensor, Object value) {
            updateMapSensorEntry(entity, Attributes.SERVICE_NOT_UP_INDICATORS, sensor.getName(), value);
        }
        /** as {@link #clearNotUpIndicator(EntityLocal, String)} using the given sensor as the key */
        public static void clearNotUpIndicator(EntityLocal entity, Sensor<?> sensor) {
            clearMapSensorEntry(entity, Attributes.SERVICE_NOT_UP_INDICATORS, sensor.getName());
        }

        public static void updateNotUpIndicatorRequiringNonEmptyList(EntityLocal entity, AttributeSensor<? extends Collection<?>> collectionSensor) {
            Collection<?> nodes = entity.getAttribute(collectionSensor);
            if (nodes==null || nodes.isEmpty()) ServiceNotUpLogic.updateNotUpIndicator(entity, collectionSensor, "Should have at least one entry");
            else ServiceNotUpLogic.clearNotUpIndicator(entity, collectionSensor);
        }
        public static void updateNotUpIndicatorRequiringNonEmptyMap(EntityLocal entity, AttributeSensor<? extends Map<?,?>> mapSensor) {
            Map<?, ?> nodes = entity.getAttribute(mapSensor);
            if (nodes==null || nodes.isEmpty()) ServiceNotUpLogic.updateNotUpIndicator(entity, mapSensor, "Should have at least one entry");
            else ServiceNotUpLogic.clearNotUpIndicator(entity, mapSensor);
        }
        
    }
    
    /** Enricher which sets {@link Attributes#SERVICE_STATE_ACTUAL} on changes to 
     * {@link Attributes#SERVICE_STATE_EXPECTED}, {@link Attributes#SERVICE_PROBLEMS}, and {@link Attributes#SERVICE_UP}
     * <p>
     * The default implementation uses {@link #computeActualStateWhenExpectedRunning(Map, Boolean)} if the last expected transition
     * was to {@link Lifecycle#RUNNING} and 
     * {@link #computeActualStateWhenNotExpectedRunning(Map, Boolean, org.apache.brooklyn.core.entity.lifecycle.Lifecycle.Transition)} otherwise.
     * If these methods return null, the {@link Attributes#SERVICE_STATE_ACTUAL} sensor will be cleared (removed).
     * Either of these methods can be overridden for custom logic, and that custom enricher can be created using 
     * {@link ServiceStateLogic#newEnricherForServiceState(Class)} and added to an entity.
     */
    public static class ComputeServiceState extends AbstractEnricher implements SensorEventListener<Object> {
        
        public static final String DEFAULT_ENRICHER_UNIQUE_TAG = "service.state.actual";

        public ComputeServiceState() {}
        public ComputeServiceState(Map<?,?> flags) { super(flags); }
            
        @Override
        public void init() {
            super.init();
            if (uniqueTag==null) uniqueTag = DEFAULT_ENRICHER_UNIQUE_TAG;
        }
        
        @Override
        public void setEntity(EntityLocal entity) {
            super.setEntity(entity);
            if (suppressDuplicates==null) {
                // only publish on changes, unless it is configured otherwise
                suppressDuplicates = true;
            }
            
            subscriptions().subscribe(entity, SERVICE_PROBLEMS, this);
            subscriptions().subscribe(entity, SERVICE_UP, this);
            subscriptions().subscribe(entity, SERVICE_STATE_EXPECTED, this);
            onEvent(null);
        }

        @Override
        public void onEvent(@Nullable SensorEvent<Object> event) {
            Preconditions.checkNotNull(entity, "Cannot handle subscriptions or compute state until associated with an entity");
            
            Map<String, Object> serviceProblems = entity.getAttribute(SERVICE_PROBLEMS);
            Boolean serviceUp = entity.getAttribute(SERVICE_UP);
            Lifecycle.Transition serviceExpected = entity.getAttribute(SERVICE_STATE_EXPECTED);
            
            if (serviceExpected!=null && serviceExpected.getState()==Lifecycle.RUNNING) {
                setActualState( computeActualStateWhenExpectedRunning(serviceProblems, serviceUp) );
            } else {
                setActualState( computeActualStateWhenNotExpectedRunning(serviceProblems, serviceUp, serviceExpected) );
            }
        }

        protected Lifecycle computeActualStateWhenExpectedRunning(Map<String, Object> problems, Boolean serviceUp) {
            if (Boolean.TRUE.equals(serviceUp) && (problems==null || problems.isEmpty())) {
                return Lifecycle.RUNNING;
            } else {
                if (!Lifecycle.ON_FIRE.equals(entity.getAttribute(SERVICE_STATE_ACTUAL))) {
                    BrooklynLogging.log(log, BrooklynLogging.levelDependingIfReadOnly(entity, LoggingLevel.WARN, LoggingLevel.TRACE, LoggingLevel.DEBUG),
                        "Setting "+entity+" "+Lifecycle.ON_FIRE+" due to problems when expected running, up="+serviceUp+", "+
                            (problems==null || problems.isEmpty() ? "not-up-indicators: "+entity.getAttribute(SERVICE_NOT_UP_INDICATORS) : "problems: "+problems));
                }
                return Lifecycle.ON_FIRE;
            }
        }
        
        protected Lifecycle computeActualStateWhenNotExpectedRunning(Map<String, Object> problems, Boolean up, Lifecycle.Transition stateTransition) {
            if (stateTransition!=null) {
                // if expected state is present but not running, just echo the expected state (ignore problems and up-ness)
                return stateTransition.getState();
                
            } else if (problems!=null && !problems.isEmpty()) {
                // if there is no expected state, then if service is not up, say stopped, else say on fire (whether service up is true or not present)
                if (Boolean.FALSE.equals(up)) {
                    return Lifecycle.STOPPED;
                } else {
                    BrooklynLogging.log(log, BrooklynLogging.levelDependingIfReadOnly(entity, LoggingLevel.WARN, LoggingLevel.TRACE, LoggingLevel.DEBUG),
                        "Setting "+entity+" "+Lifecycle.ON_FIRE+" due to problems when expected "+stateTransition+" / up="+up+": "+problems);
                    return Lifecycle.ON_FIRE;
                }
            } else {
                // no expected transition and no problems
                // if the problems map is non-null, then infer from service up;
                // if there is no problems map, then leave unchanged (user may have set it explicitly)
                if (problems!=null)
                    return (up==null ? null /* remove if up is not set */ : 
                        up ? Lifecycle.RUNNING : Lifecycle.STOPPED);
                else
                    return entity.getAttribute(SERVICE_STATE_ACTUAL);
            }
        }

        protected void setActualState(@Nullable Lifecycle state) {
            if (log.isTraceEnabled()) log.trace("{} setting actual state {}", this, state);
            if (((EntityInternal)entity).getManagementSupport().isNoLongerManaged()) {
                // won't catch everything, but catches some
                BrooklynLogging.log(log, BrooklynLogging.levelDebugOrTraceIfReadOnly(entity),
                    entity+" is no longer managed when told to set actual state to "+state+"; suppressing");
                return;
            }
            emit(SERVICE_STATE_ACTUAL, (state==null ? Entities.REMOVE : state));
        }

    }
    
    public static final EnricherSpec<?> newEnricherForServiceStateFromProblemsAndUp() {
        return newEnricherForServiceState(ComputeServiceState.class);
    }
    public static final EnricherSpec<?> newEnricherForServiceState(Class<? extends Enricher> type) {
        return EnricherSpec.create(type);
    }
    
    public static class ServiceProblemsLogic {
        /** static only; not for instantiation */
        private ServiceProblemsLogic() {}
        
        /** puts the given value into the {@link Attributes#SERVICE_PROBLEMS} map as if the 
         * {@link UpdatingMap} enricher for the given sensor reported this value */
        public static void updateProblemsIndicator(EntityLocal entity, Sensor<?> sensor, Object value) {
            updateMapSensorEntry(entity, Attributes.SERVICE_PROBLEMS, sensor.getName(), value);
        }
        /** clears any entry for the given sensor in the {@link Attributes#SERVICE_PROBLEMS} map */
        public static void clearProblemsIndicator(EntityLocal entity, Sensor<?> sensor) {
            clearMapSensorEntry(entity, Attributes.SERVICE_PROBLEMS, sensor.getName());
        }
        /** as {@link #updateProblemsIndicator(EntityLocal, Sensor, Object)} */
        public static void updateProblemsIndicator(EntityLocal entity, Effector<?> eff, Object value) {
            updateMapSensorEntry(entity, Attributes.SERVICE_PROBLEMS, eff.getName(), value);
        }
        /** as {@link #clearProblemsIndicator(EntityLocal, Sensor)} */
        public static void clearProblemsIndicator(EntityLocal entity, Effector<?> eff) {
            clearMapSensorEntry(entity, Attributes.SERVICE_PROBLEMS, eff.getName());
        }
        /** as {@link #updateProblemsIndicator(EntityLocal, Sensor, Object)} */
        public static void updateProblemsIndicator(EntityLocal entity, String key, Object value) {
            updateMapSensorEntry(entity, Attributes.SERVICE_PROBLEMS, key, value);
        }
        /** as {@link #clearProblemsIndicator(EntityLocal, Sensor)} */
        public static void clearProblemsIndicator(EntityLocal entity, String key) {
            clearMapSensorEntry(entity, Attributes.SERVICE_PROBLEMS, key);
        }
    }
    
    public static class ComputeServiceIndicatorsFromChildrenAndMembers extends AbstractMultipleSensorAggregator<Void> implements SensorEventListener<Object> {
        /** standard unique tag identifying instances of this enricher at runtime, also used for the map sensor if no unique tag specified */
        public final static String DEFAULT_UNIQUE_TAG = "service-lifecycle-indicators-from-children-and-members";
        
        /** as {@link #DEFAULT_UNIQUE_TAG}, but when a second distinct instance is responsible for computing service up */
        public final static String DEFAULT_UNIQUE_TAG_UP = "service-not-up-indicators-from-children-and-members";

        public static final ConfigKey<QuorumCheck> UP_QUORUM_CHECK = ConfigKeys.builder(QuorumCheck.class, "enricher.service_state.children_and_members.quorum.up")
            .description("Logic for checking whether this service is up, based on children and/or members, defaulting to allowing none but if there are any requiring at least one to be up")
            .defaultValue(QuorumCheck.QuorumChecks.atLeastOneUnlessEmpty())
            .inheritance(ConfigInheritance.NONE)
            .build();
        public static final ConfigKey<QuorumCheck> RUNNING_QUORUM_CHECK = ConfigKeys.builder(QuorumCheck.class, "enricher.service_state.children_and_members.quorum.running") 
            .description("Logic for checking whether this service is healthy, based on children and/or members running, defaulting to requiring none to be ON-FIRE")
            .defaultValue(QuorumCheck.QuorumChecks.all())
            .inheritance(ConfigInheritance.NONE)
            .build();
        // TODO items below should probably also have inheritance NONE ?
        public static final ConfigKey<Boolean> DERIVE_SERVICE_NOT_UP = ConfigKeys.newBooleanConfigKey("enricher.service_state.children_and_members.service_up.publish", "Whether to derive a service-not-up indicator from children", true);
        public static final ConfigKey<Boolean> DERIVE_SERVICE_PROBLEMS = ConfigKeys.newBooleanConfigKey("enricher.service_state.children_and_members.service_problems.publish", "Whether to derive a service-problem indicator from children", true);
        public static final ConfigKey<Boolean> IGNORE_ENTITIES_WITH_SERVICE_UP_NULL = ConfigKeys.newBooleanConfigKey("enricher.service_state.children_and_members.ignore_entities.service_up_null", "Whether to ignore children reporting null values for service up", true);
        @SuppressWarnings("serial")
        public static final ConfigKey<Set<Lifecycle>> IGNORE_ENTITIES_WITH_THESE_SERVICE_STATES = ConfigKeys.newConfigKey(new TypeToken<Set<Lifecycle>>() {},
            "enricher.service_state.children_and_members.ignore_entities.service_state_values", 
            "Service states (including null) which indicate an entity should be ignored when looking at children service states; anything apart from RUNNING not in this list will be treated as not healthy (by default just ON_FIRE will mean not healthy)", 
            MutableSet.<Lifecycle>builder().addAll(Lifecycle.values()).add(null).remove(Lifecycle.RUNNING).remove(Lifecycle.ON_FIRE).build().asUnmodifiable());

        protected String getKeyForMapSensor() {
            return Preconditions.checkNotNull(super.getUniqueTag());
        }

        @Override
        protected void setEntityLoadingConfig() {
            fromChildren = true;
            fromMembers = true;
            // above sets default
            super.setEntityLoadingConfig();
            if (isAggregatingMembers() && (!(entity instanceof Group))) {
                if (fromChildren) fromMembers=false;
                else throw new IllegalStateException("Cannot monitor only members for non-group entity "+entity+": "+this);
            }
            Preconditions.checkNotNull(getKeyForMapSensor());
        }

        @Override
        protected void setEntityLoadingTargetConfig() {
            if (getConfig(TARGET_SENSOR)!=null)
                throw new IllegalArgumentException("Must not set "+TARGET_SENSOR+" when using "+this);
        }

        @Override
        public void setEntity(EntityLocal entity) {
            super.setEntity(entity);
            if (suppressDuplicates==null) {
                // only publish on changes, unless it is configured otherwise
                suppressDuplicates = true;
            }
        }

        final static Set<ConfigKey<?>> RECONFIGURABLE_KEYS = ImmutableSet.<ConfigKey<?>>of(
            UP_QUORUM_CHECK, RUNNING_QUORUM_CHECK,
            DERIVE_SERVICE_NOT_UP, DERIVE_SERVICE_NOT_UP, 
            IGNORE_ENTITIES_WITH_SERVICE_UP_NULL, IGNORE_ENTITIES_WITH_THESE_SERVICE_STATES);
        
        @Override
        protected <T> void doReconfigureConfig(ConfigKey<T> key, T val) {
            if (RECONFIGURABLE_KEYS.contains(key)) {
                return;
            } else {
                super.doReconfigureConfig(key, val);
            }
        }
        
        @Override
        protected void onChanged() {
            super.onChanged();
            if (entity != null && isRunning())
                onUpdated();
        }
        
        private final List<Sensor<?>> SOURCE_SENSORS = ImmutableList.<Sensor<?>>of(SERVICE_UP, SERVICE_STATE_ACTUAL);
        @Override
        protected Collection<Sensor<?>> getSourceSensors() {
            return SOURCE_SENSORS;
        }

        @Override
        protected void onUpdated() {
            if (entity==null || !Entities.isManaged(entity)) {
                // either invoked during setup or entity has become unmanaged; just ignore
                BrooklynLogging.log(log, BrooklynLogging.levelDebugOrTraceIfReadOnly(entity),
                    "Ignoring {} onUpdated when entity is not in valid state ({})", this, entity);
                return;
            }

            // override superclass to publish multiple sensors
            if (getConfig(DERIVE_SERVICE_PROBLEMS)) {
                updateMapSensor(SERVICE_PROBLEMS, computeServiceProblems());
            }

            if (getConfig(DERIVE_SERVICE_NOT_UP)) {
                updateMapSensor(SERVICE_NOT_UP_INDICATORS, computeServiceNotUp());
            }
        }

        protected Object computeServiceNotUp() {
            Map<Entity, Boolean> values = getValues(SERVICE_UP);
            List<Entity> violators = MutableList.of();
            boolean ignoreNull = getConfig(IGNORE_ENTITIES_WITH_SERVICE_UP_NULL);
            Set<Lifecycle> ignoreStates = getConfig(IGNORE_ENTITIES_WITH_THESE_SERVICE_STATES);
            int entries=0;
            int numUp=0;
            for (Map.Entry<Entity, Boolean> state: values.entrySet()) {
                if (ignoreNull && state.getValue()==null)
                    continue;
                entries++;
                Lifecycle entityState = state.getKey().getAttribute(SERVICE_STATE_ACTUAL);
                
                if (Boolean.TRUE.equals(state.getValue())) numUp++;
                else if (!ignoreStates.contains(entityState)) {
                    violators.add(state.getKey());
                }
            }

            QuorumCheck qc = getConfig(UP_QUORUM_CHECK);
            if (qc!=null) {
                if (qc.isQuorate(numUp, violators.size()+numUp))
                    // quorate
                    return null;

                if (values.isEmpty()) return "No entities present";
                if (entries==0) return "No entities publishing service up";
                if (violators.isEmpty()) return "Not enough entities";
            } else {
                if (violators.isEmpty())
                    return null;
            }

            if (violators.size()==1) return violators.get(0)+" is not up";
            if (violators.size()==entries) return "None of the entities are up";
            return violators.size()+" entities are not up, including "+violators.get(0);
        }

        protected Object computeServiceProblems() {
            Map<Entity, Lifecycle> values = getValues(SERVICE_STATE_ACTUAL);
            int numRunning=0;
            List<Entity> onesNotHealthy=MutableList.of();
            Set<Lifecycle> ignoreStates = getConfig(IGNORE_ENTITIES_WITH_THESE_SERVICE_STATES);
            for (Map.Entry<Entity,Lifecycle> state: values.entrySet()) {
                if (state.getValue()==Lifecycle.RUNNING) numRunning++;
                else if (!ignoreStates.contains(state.getValue())) 
                    onesNotHealthy.add(state.getKey());
            }

            QuorumCheck qc = getConfig(RUNNING_QUORUM_CHECK);
            if (qc!=null) {
                if (qc.isQuorate(numRunning, onesNotHealthy.size()+numRunning))
                    // quorate
                    return null;

                if (onesNotHealthy.isEmpty())
                    return "Not enough entities running to be quorate";
            } else {
                if (onesNotHealthy.isEmpty())
                    return null;
            }

            return "Required entit"+Strings.ies(onesNotHealthy.size())+" not healthy: "+
                (onesNotHealthy.size()>3 ? onesNotHealthy.get(0)+" and "+(onesNotHealthy.size()-1)+" others"
                    : Strings.join(onesNotHealthy, ", "));
        }

        protected void updateMapSensor(AttributeSensor<Map<String, Object>> sensor, Object value) {
            if (log.isTraceEnabled()) log.trace("{} updating map sensor {} with {}", new Object[] { this, sensor, value });

            if (value!=null) {
                updateMapSensorEntry(entity, sensor, getKeyForMapSensor(), value);
            } else {
                clearMapSensorEntry(entity, sensor, getKeyForMapSensor());
            }
        }

        /** not used; see specific `computeXxx` methods, invoked by overridden onUpdated */
        @Override
        protected Object compute() {
            return null;
        }
    }
    
    public static class ComputeServiceIndicatorsFromChildrenAndMembersSpec extends ExtensibleEnricherSpec<ComputeServiceIndicatorsFromChildrenAndMembers,ComputeServiceIndicatorsFromChildrenAndMembersSpec> {
        private static final long serialVersionUID = -607444925297963712L;
        
        protected ComputeServiceIndicatorsFromChildrenAndMembersSpec() {
            this(ComputeServiceIndicatorsFromChildrenAndMembers.class);
        }
        
        protected ComputeServiceIndicatorsFromChildrenAndMembersSpec(Class<? extends ComputeServiceIndicatorsFromChildrenAndMembers> clazz) {
            super(clazz);
        }

        public void addTo(Entity entity) {
            entity.enrichers().add(this);
        }

        public ComputeServiceIndicatorsFromChildrenAndMembersSpec checkChildrenAndMembers() {
            configure(ComputeServiceIndicatorsFromChildrenAndMembers.FROM_MEMBERS, true);
            configure(ComputeServiceIndicatorsFromChildrenAndMembers.FROM_CHILDREN, true);
            return self();
        }
        public ComputeServiceIndicatorsFromChildrenAndMembersSpec checkMembersOnly() {
            configure(ComputeServiceIndicatorsFromChildrenAndMembers.FROM_MEMBERS, true);
            configure(ComputeServiceIndicatorsFromChildrenAndMembers.FROM_CHILDREN, false);
            return self();
        }
        public ComputeServiceIndicatorsFromChildrenAndMembersSpec checkChildrenOnly() {
            configure(ComputeServiceIndicatorsFromChildrenAndMembers.FROM_MEMBERS, false);
            configure(ComputeServiceIndicatorsFromChildrenAndMembers.FROM_CHILDREN, true);
            return self();
        }

        public ComputeServiceIndicatorsFromChildrenAndMembersSpec requireUpChildren(QuorumCheck check) {
            configure(ComputeServiceIndicatorsFromChildrenAndMembers.UP_QUORUM_CHECK, check);
            return self();
        }
        public ComputeServiceIndicatorsFromChildrenAndMembersSpec requireRunningChildren(QuorumCheck check) {
            configure(ComputeServiceIndicatorsFromChildrenAndMembers.RUNNING_QUORUM_CHECK, check);
            return self();
        }
    }

    /** provides the default {@link ComputeServiceIndicatorsFromChildrenAndMembers} enricher, 
     * using the default unique tag ({@link ComputeServiceIndicatorsFromChildrenAndMembers#DEFAULT_UNIQUE_TAG}),
     * configured here to require none on fire, and either no children or at least one up child,
     * the spec can be further configured as appropriate */
    public static ComputeServiceIndicatorsFromChildrenAndMembersSpec newEnricherFromChildren() {
        return new ComputeServiceIndicatorsFromChildrenAndMembersSpec()
            .uniqueTag(ComputeServiceIndicatorsFromChildrenAndMembers.DEFAULT_UNIQUE_TAG);
    }

    /** as {@link #newEnricherFromChildren()} but only publishing service not-up indicators, 
     * using a different unique tag ({@link ComputeServiceIndicatorsFromChildrenAndMembers#DEFAULT_UNIQUE_TAG_UP}),
     * listening to children only, ignoring lifecycle/service-state,
     * and using the same logic 
     * (viz looking only at children (not members) and requiring either no children or at least one child up) by default */
    public static ComputeServiceIndicatorsFromChildrenAndMembersSpec newEnricherFromChildrenUp() {
        return newEnricherFromChildren()
            .uniqueTag(ComputeServiceIndicatorsFromChildrenAndMembers.DEFAULT_UNIQUE_TAG_UP)
            .checkChildrenOnly()
            .configure(ComputeServiceIndicatorsFromChildrenAndMembers.DERIVE_SERVICE_PROBLEMS, false);
    }
    
    /** as {@link #newEnricherFromChildren()} but only publishing service problems,
     * listening to children and members, ignoring service up,
     * and using the same logic 
     * (viz looking at children and members and requiring none are on fire) by default */
    public static ComputeServiceIndicatorsFromChildrenAndMembersSpec newEnricherFromChildrenState() {
        return newEnricherFromChildren()
            .configure(ComputeServiceIndicatorsFromChildrenAndMembers.DERIVE_SERVICE_NOT_UP, false);
    }
    
}
