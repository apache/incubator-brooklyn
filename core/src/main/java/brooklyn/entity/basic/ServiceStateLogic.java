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

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.Enrichers;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.enricher.basic.AbstractMultipleSensorAggregator;
import brooklyn.enricher.basic.UpdatingMap;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.Lifecycle.Transition;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.EnricherSpec.ExtensibleEnricherSpec;
import brooklyn.util.collections.CollectionFunctionals;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Functionals;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/** Logic, sensors and enrichers, and conveniences, for computing service status */ 
public class ServiceStateLogic {

    public static final AttributeSensor<Boolean> SERVICE_UP = Attributes.SERVICE_UP;
    public static final AttributeSensor<Map<String,Object>> SERVICE_NOT_UP_INDICATORS = Attributes.SERVICE_NOT_UP_INDICATORS;
    
    public static final AttributeSensor<Lifecycle> SERVICE_STATE_ACTUAL = Attributes.SERVICE_STATE_ACTUAL;
    public static final AttributeSensor<Lifecycle.Transition> SERVICE_STATE_EXPECTED = Attributes.SERVICE_STATE_EXPECTED;
    public static final AttributeSensor<Map<String,Object>> SERVICE_PROBLEMS = Attributes.SERVICE_PROBLEMS;

    /** static only; not for instantiation */
    private ServiceStateLogic() {}

    @SuppressWarnings("unchecked")
    public static <TKey,TVal> void clearMapSensorEntry(EntityLocal entity, AttributeSensor<Map<TKey,TVal>> sensor, TKey key) {
        updateMapSensorEntry(entity, sensor, key, (TVal)Entities.REMOVE);
    }

    /** update the given key in the given map sensor */
    public static <TKey,TVal> void updateMapSensorEntry(EntityLocal entity, AttributeSensor<Map<TKey,TVal>> sensor, TKey key, TVal v) {
        Map<TKey, TVal> map = entity.getAttribute(sensor);

        // TODO synchronize
        
        boolean created = (map==null);
        if (created) map = MutableMap.of();
                
        boolean changed;
        if (v == Entities.REMOVE) {
            changed = map.containsKey(key);
            if (changed)
                map.remove(key);
        } else {
            TVal oldV = map.get(key);
            if (oldV==null)
                changed = (v!=null || !map.containsKey(key));
            else
                changed = !oldV.equals(v);
            if (changed)
                map.put(key, (TVal)v);
        }
        if (changed || created)
            entity.setAttribute(sensor, map);
    }
    
    public static void setExpectedState(Entity entity, Lifecycle state) {
        ((EntityInternal)entity).setAttribute(Attributes.SERVICE_STATE_EXPECTED, new Lifecycle.Transition(state, new Date()));
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
        /** static only; not for instantiation */
        private ServiceNotUpLogic() {}
        
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public static final EnricherSpec<?> newEnricherForServiceUpIfNoNotUpIndicators() {
            return Enrichers.builder()
                .transforming(SERVICE_NOT_UP_INDICATORS).publishing(Attributes.SERVICE_UP)
                .computing( /* cast hacks to support removing */ (Function)
                    Functionals.<Map<String,?>>
                        ifNotEquals(null).<Object>apply(Functions.forPredicate(CollectionFunctionals.<String>mapSizeEquals(0)))
                        .defaultValue(Entities.REMOVE) )
                .uniqueTag("service.isUp if no service.notUp.indicators")
                .build();
        }
        
        /** puts the given value into the {@link Attributes#SERVICE_NOT_UP_INDICATORS} map as if the 
         * {@link UpdatingMap} enricher for the given sensor reported */
        public static void updateNotUpIndicator(EntityLocal entity, Sensor<?> sensor, Object value) {
            updateMapSensorEntry(entity, Attributes.SERVICE_NOT_UP_INDICATORS, sensor.getName(), value);
        }
        /** clears any entry for the given sensor in the {@link Attributes#SERVICE_NOT_UP_INDICATORS} map */
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
     * {@link #computeActualStateWhenNotExpectedRunning(Map, Boolean, brooklyn.entity.basic.Lifecycle.Transition)} otherwise.
     * If these methods return null, the {@link Attributes#SERVICE_STATE_ACTUAL} sensor will be cleared (removed).
     * Either of these methods can be overridden for custom logic, and that custom enricher can be created using 
     * {@link ServiceStateLogic#newEnricherForServiceState(Class)} and added to an entity.
     */
    public static class ComputeServiceState extends AbstractEnricher implements SensorEventListener<Object> {
        public void setEntity(EntityLocal entity) {
            super.setEntity(entity);
            if (suppressDuplicates==null) {
                // only publish on changes, unless it is configured otherwise
                suppressDuplicates = Boolean.TRUE;
            }
            
            subscribe(entity, SERVICE_PROBLEMS, this);
            subscribe(entity, SERVICE_UP, this);
            subscribe(entity, SERVICE_STATE_EXPECTED, this);
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
                return Lifecycle.ON_FIRE;
            }
        }
        
        protected Lifecycle computeActualStateWhenNotExpectedRunning(Map<String, Object> problems, Boolean up, Lifecycle.Transition stateTransition) {
            if (stateTransition!=null) {
                return stateTransition.getState();
            } else if (problems!=null && !problems.isEmpty()) {
                return Lifecycle.ON_FIRE;
            } else {
                return (up==null ? null : up ? Lifecycle.RUNNING : Lifecycle.STOPPED);
            }
        }

        protected void setActualState(@Nullable Lifecycle state) {
            emit(SERVICE_STATE_ACTUAL, (state==null ? Entities.REMOVE : state));
        }
    }
    
    public static final EnricherSpec<?> newEnricherForServiceStateFromProblemsAndUp() {
        return newEnricherForServiceState(ComputeServiceState.class);
    }
    public static final EnricherSpec<?> newEnricherForServiceState(Class<? extends Enricher> type) {
        return EnricherSpec.create(type).uniqueTag("service.state.actual from service.state.expected and service.problems");
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
    }
    
    public static class ComputeServiceIndicatorsFromChildrenAndMembers extends AbstractMultipleSensorAggregator<Void> implements SensorEventListener<Object> {
        /** standard unique tag identifying instances of this enricher at runtime, also used for the map sensor if no unique tag specified */
        public final static String IDENTIFIER_DEFAULT = "service-lifecycle-indicators-from-children-and-members";
        
        /** as {@link #IDENTIFIER_LIFECYCLE}, but when a second distinct instance is responsible for computing service up */
        public final static String IDENTIFIER_UP = "service-not-up-indicators-from-children-and-members";

        public static final ConfigKey<QuorumCheck> UP_QUORUM_CHECK = ConfigKeys.newConfigKey(QuorumCheck.class, "enricher.service_state.children_and_members.quorum.up", 
            "Logic for checking whether this service is up, based on children and/or members, defaulting to allowing none but if there are any requiring at least one to be up", QuorumCheck.QuorumChecks.atLeastOneUnlessEmpty());
        public static final ConfigKey<QuorumCheck> RUNNING_QUORUM_CHECK = ConfigKeys.newConfigKey(QuorumCheck.class, "enricher.service_state.children_and_members.quorum.running", 
            "Logic for checking whether this service is healthy, based on children and/or members running, defaulting to requiring none to be ON-FIRE", QuorumCheck.QuorumChecks.all());
        public static final ConfigKey<Boolean> DERIVE_SERVICE_NOT_UP = ConfigKeys.newBooleanConfigKey("enricher.service_state.children_and_members.service_up.publish", "Whether to derive a service-not-up indicator from children", true);
        public static final ConfigKey<Boolean> DERIVE_SERVICE_PROBLEMS = ConfigKeys.newBooleanConfigKey("enricher.service_state.children_and_members.service_problems.publish", "Whether to derive a service-problem indicator from children", true);

        protected String getKeyForMapSensor() {
            return Preconditions.checkNotNull(super.getUniqueTag());
        }

        @Override
        protected void setEntityLoadingConfig() {
            fromChildren = true;
            fromMembers = true;
            // above sets default
            super.setEntityLoadingConfig();
            if (fromMembers && (!(entity instanceof Group))) {
                if (fromChildren) fromMembers=false;
                else throw new IllegalStateException("Cannot monitor only members for non-group entity "+entity+": "+this);
            }
            Preconditions.checkNotNull(getKeyForMapSensor());
        }

        protected void setEntityLoadingTargetConfig() {
            if (getConfig(TARGET_SENSOR)!=null)
                throw new IllegalArgumentException("Must not set "+TARGET_SENSOR+" when using "+this);
        }

        public void setEntity(EntityLocal entity) {
            super.setEntity(entity);
            if (suppressDuplicates==null) {
                // only publish on changes, unless it is configured otherwise
                suppressDuplicates = Boolean.TRUE;
            }
        }

        private final List<Sensor<?>> SOURCE_SENSORS = ImmutableList.<Sensor<?>>of(SERVICE_UP, SERVICE_STATE_ACTUAL);
        @Override
        protected Collection<Sensor<?>> getSourceSensors() {
            return SOURCE_SENSORS;
        }

        @Override
        protected void onUpdated() {
            // override superclass to publish potentially several items

            if (getConfig(DERIVE_SERVICE_PROBLEMS))
                updateMapSensor(SERVICE_PROBLEMS, computeServiceProblems());

            if (getConfig(DERIVE_SERVICE_NOT_UP))
                updateMapSensor(SERVICE_NOT_UP_INDICATORS, computeServiceNotUp());
        }

        protected Object computeServiceNotUp() {
            Map<Entity, Boolean> values = getValues(SERVICE_UP);
            List<Entity> violators = MutableList.of();
            for (Map.Entry<Entity, Boolean> state: values.entrySet()) {
                if (!Boolean.TRUE.equals(state.getValue())) {
                    violators.add(state.getKey());
                }
            }

            QuorumCheck qc = getConfig(UP_QUORUM_CHECK);
            if (qc!=null) {
                if (qc.isQuorate(values.size()-violators.size(), values.size()))
                    // quorate
                    return null;

                if (values.isEmpty()) return "No entities present";
                if (violators.isEmpty()) return "Not enough entities";
            } else {
                if (violators.isEmpty())
                    return null;
            }

            if (violators.size()==1) return violators.get(0)+" is not up";
            if (violators.size()==values.size()) return "None of the entities are up";
            return violators.size()+" entities are not up, including "+violators.get(0);
        }

        protected Object computeServiceProblems() {
            Map<Entity, Lifecycle> values = getValues(SERVICE_STATE_ACTUAL);
            int numRunning=0, numOnFire=0;
            for (Lifecycle state: values.values()) {
                if (state==Lifecycle.RUNNING) numRunning++;
                else if (state==Lifecycle.ON_FIRE) numOnFire++;
            }

            QuorumCheck qc = getConfig(RUNNING_QUORUM_CHECK);
            if (qc!=null) {
                if (qc.isQuorate(numRunning, numOnFire+numRunning))
                    // quorate
                    return null;

                if (numOnFire==0)
                    return "Not enough entities running to be quorate";
            } else {
                if (numOnFire==0)
                    return null;
            }

            return numOnFire+" entit"+Strings.ies(numOnFire)+" are on fire";
        }

        protected void updateMapSensor(AttributeSensor<Map<String, Object>> sensor, Object value) {
            if (value!=null)
                updateMapSensorEntry(entity, sensor, getKeyForMapSensor(), value);
            else
                clearMapSensorEntry(entity, sensor, getKeyForMapSensor());
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
            entity.addEnricher(this);
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
     * using the default unique tag ({@link ComputeServiceIndicatorsFromChildrenAndMembers#IDENTIFIER_DEFAULT}),
     * configured here to require none on fire, and either no children or at least one up child,
     * the spec can be further configured as appropriate */
    public static ComputeServiceIndicatorsFromChildrenAndMembersSpec newEnricherFromChildren() {
        return new ComputeServiceIndicatorsFromChildrenAndMembersSpec()
            .uniqueTag(ComputeServiceIndicatorsFromChildrenAndMembers.IDENTIFIER_DEFAULT);
    }

    /** as {@link #newEnricherFromChildren()} but only publishing service not-up indicators, 
     * using a different unique tag ({@link ComputeServiceIndicatorsFromChildrenAndMembers#IDENTIFIER_UP}),
     * listening to children only, ignoring lifecycle/service-state,
     * and using the same logic 
     * (viz looking only at children (not members) and requiring either no children or at least one child up) by default */
    public static ComputeServiceIndicatorsFromChildrenAndMembersSpec newEnricherFromChildrenUp() {
        return newEnricherFromChildren()
            .uniqueTag(ComputeServiceIndicatorsFromChildrenAndMembers.IDENTIFIER_UP)
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
