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
package brooklyn.entity.group;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;

/** abstract class which helps track membership of a group, invoking (empty) methods in this class on MEMBER{ADDED,REMOVED} events, as well as SERVICE_UP {true,false} for those members. */
public abstract class AbstractMembershipTrackingPolicy extends AbstractPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractMembershipTrackingPolicy.class);
    
    public enum EventType { ENTITY_CHANGE, ENTITY_ADDED, ENTITY_REMOVED }
    
    @SuppressWarnings("serial")
    public static final ConfigKey<Set<Sensor<?>>> SENSORS_TO_TRACK = ConfigKeys.newConfigKey(
            new TypeToken<Set<Sensor<?>>>() {},
            "sensorsToTrack",
            "Sensors of members to be monitored (implicitly adds service-up to this list, but that behaviour may be deleted in a subsequent release!)",
            ImmutableSet.<Sensor<?>>of());

    public static final ConfigKey<Boolean> NOTIFY_ON_DUPLICATES = ConfigKeys.newBooleanConfigKey("notifyOnDuplicates",
            "Whether to notify listeners when a sensor is published with the same value as last time",
            false);

    public static final ConfigKey<Group> GROUP = ConfigKeys.newConfigKey(Group.class, "group");

    private ConcurrentMap<String,Map<Sensor<Object>, Object>> entitySensorCache = new ConcurrentHashMap<String, Map<Sensor<Object>, Object>>();
    
    public AbstractMembershipTrackingPolicy(Map<?,?> flags) {
        super(flags);
    }
    
    public AbstractMembershipTrackingPolicy() {
        super();
    }

    protected Set<Sensor<?>> getSensorsToTrack() {
        return ImmutableSet.<Sensor<?>>builder()
                .addAll(getRequiredConfig(SENSORS_TO_TRACK))
                .add(Attributes.SERVICE_UP)
                .build();
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        Group group = getGroup();
        if (group != null) {
            subscribeToGroup(group);
        } else {
            LOG.warn("Deprecated use of "+AbstractMembershipTrackingPolicy.class.getSimpleName()+"; group should be set as config");
        }
    }
    
    /**
     * Sets the group to be tracked; unsubscribes from any previous group, and subscribes to this group.
     * 
     * Note this must be called *after* adding the policy to the entity.
     * 
     * @param group
     * 
     * @deprecated since 0.7; instead set the group as config
     */
    @Deprecated
    public void setGroup(Group group) {
        // relies on doReconfigureConfig to make the actual change
        LOG.warn("Deprecated use of setGroup in "+AbstractMembershipTrackingPolicy.class.getSimpleName()+"; group should be set as config");
        setConfig(GROUP, group);
    }
    
    @Override
    protected <T> void doReconfigureConfig(ConfigKey<T> key, T val) {
        if (GROUP.getName().equals(key.getName())) {
            Preconditions.checkNotNull(val, "%s value must not be null", GROUP.getName());
            Preconditions.checkNotNull(val, "%s value must be a group, but was %s (of type %s)", GROUP.getName(), val, val.getClass());
            if (val.equals(getConfig(GROUP))) {
                if (LOG.isDebugEnabled()) LOG.debug("No-op for reconfigure group of "+AbstractMembershipTrackingPolicy.class.getSimpleName()+"; group is still "+val);
            } else {
                if (LOG.isInfoEnabled()) LOG.info("Membership tracker "+AbstractMembershipTrackingPolicy.class+", resubscribing to group "+val+", previously was "+getGroup());
                unsubscribeFromGroup();
                subscribeToGroup((Group)val);
            }
        } else {
            throw new UnsupportedOperationException("reconfiguring "+key+" unsupported for "+this);
        }
    }
    
    /**
     * Unsubscribes from the group.
     * 
     * @deprecated since 0.7; misleading method name; either remove the policy, or suspend/resume
     */
    @Deprecated
    public void reset() {
        unsubscribeFromGroup();
    }

    @Override
    public void suspend() {
        unsubscribeFromGroup();
        super.suspend();
    }
    
    @Override
    public void resume() {
        boolean wasSuspended = isSuspended();
        super.resume();
        
        Group group = getGroup();
        if (wasSuspended && group != null) {
            subscribeToGroup(group);
        }
    }

    protected Group getGroup() {
        return getConfig(GROUP);
    }
    
    protected void subscribeToGroup(final Group group) {
        Preconditions.checkNotNull(group, "The group must not be null");

        LOG.debug("Subscribing to group "+group+", for memberAdded, memberRemoved, and {}", getSensorsToTrack());
        
        subscribe(group, DynamicGroup.MEMBER_ADDED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                onEntityEvent(EventType.ENTITY_ADDED, event.getValue());
            }
        });
        subscribe(group, DynamicGroup.MEMBER_REMOVED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                entitySensorCache.remove(event.getSource().getId());
                onEntityEvent(EventType.ENTITY_REMOVED, event.getValue());
            }
        });

        for (Sensor<?> sensor : getSensorsToTrack()) {
            subscribeToMembers(group, sensor, new SensorEventListener<Object>() {
                @Override public void onEvent(SensorEvent<Object> event) {
                    boolean notifyOnDuplicates = getRequiredConfig(NOTIFY_ON_DUPLICATES);
                    String entityId = event.getSource().getId();

                    if (!notifyOnDuplicates) {
                        Map<Sensor<Object>, Object> newMap = MutableMap.<Sensor<Object>, Object>of();
                        // NOTE: putIfAbsent returns null if the key is not present, or the *previous* value if present
                        Map<Sensor<Object>, Object> sensorCache = entitySensorCache.putIfAbsent(entityId, newMap);
                        if (sensorCache == null) {
                            sensorCache = newMap;
                        }
                        
                        boolean oldExists = sensorCache.containsKey(event.getSensor());
                        Object oldVal = sensorCache.put(event.getSensor(), event.getValue());
                        
                        if (oldExists && Objects.equal(event.getValue(), oldVal)) {
                            // ignore if value has not changed
                            return;
                        }
                    }

                    onEntityEvent(EventType.ENTITY_CHANGE, event.getSource());
                }
            });
        }
        
        for (Entity it : group.getMembers()) { onEntityEvent(EventType.ENTITY_ADDED, it); }
    }

    protected void unsubscribeFromGroup() {
        Group group = getGroup();
        if (getSubscriptionTracker() != null && group != null) unsubscribe(group);
    }

    /** All entity events pass through this method. Default impl delegates to onEntityXxxx methods, whose default behaviours are no-op.
     * Callers may override this to intercept all entity events in a single place, and to suppress subsequent processing if desired. 
     */
    protected void onEntityEvent(EventType type, Entity entity) {
        switch (type) {
        case ENTITY_CHANGE: onEntityChange(entity); break;
        case ENTITY_ADDED: onEntityAdded(entity); break;
        case ENTITY_REMOVED: onEntityRemoved(entity); break;
        }
    }
    
    /**
     * Called when a member's "up" sensor changes.
     */
    protected void onEntityChange(Entity member) {}

    /**
     * Called when a member is added.
     * Note that the change event may arrive before this event; implementations here should typically look at the last value.
     */
    protected void onEntityAdded(Entity member) {}

    /**
     * Called when a member is removed.
     * Note that entity change events may arrive after this event; they should typically be ignored. 
     */
    protected void onEntityRemoved(Entity member) {}
}
