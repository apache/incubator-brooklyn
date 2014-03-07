package brooklyn.entity.group;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.trait.Startable;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

/** abstract class which helps track membership of a group, invoking (empty) methods in this class on MEMBER{ADDED,REMOVED} events, as well as SERVICE_UP {true,false} for those members. */
public abstract class AbstractMembershipTrackingPolicy extends AbstractPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractMembershipTrackingPolicy.class);
    
    private Group group;
    
    @SetFromFlag
    private Set<Sensor<?>> sensorsToTrack;
    
    public AbstractMembershipTrackingPolicy(Map<?,?> flags) {
        super(flags);
        if (sensorsToTrack == null)  sensorsToTrack = Sets.newLinkedHashSet();
    }
    
    public AbstractMembershipTrackingPolicy() {
        this(Collections.emptyMap());
    }

    /**
     * Sets the group to be tracked; unsubscribes from any previous group, and subscribes to this group.
     * 
     * Note this must be called *after* adding the policy to the entity.
     * 
     * @param group
     */
    public void setGroup(Group group) {
        Preconditions.checkNotNull(group, "The group cannot be null");
        unsubscribeFromGroup();
        this.group = group;
        subscribeToGroup();
    }
    
    /**
     * Unsubscribes from the group.
     */
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
        super.resume();
        if (group != null) {
            subscribeToGroup();
        }
    }

    // TODO having "subscribe to changes only" semantics as part of subscription would be much cleaner
    // than this lightweight map
    Map<String,Boolean> lastKnownServiceUpCache = new ConcurrentHashMap<String, Boolean>();
    
    protected void subscribeToGroup() {
        Preconditions.checkNotNull(group, "The group cannot be null");

        LOG.debug("Subscribing to group "+group+", for memberAdded, memberRemoved, serviceUp, and {}", sensorsToTrack);
        
        subscribe(group, DynamicGroup.MEMBER_ADDED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                onEntityEvent(EventType.ENTITY_ADDED, event.getValue());
            }
        });
        subscribe(group, DynamicGroup.MEMBER_REMOVED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                lastKnownServiceUpCache.remove(event.getSource());
                onEntityEvent(EventType.ENTITY_REMOVED, event.getValue());
            }
        });
        subscribeToMembers(group, Startable.SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override public void onEvent(SensorEvent<Boolean> event) {
                if (event.getValue() == lastKnownServiceUpCache.put(event.getSource().getId(), event.getValue()))
                    // ignore if value has not changed
                    return;
                onEntityEvent(EventType.ENTITY_CHANGE, event.getSource());
            }
        });
        for (Sensor<?> sensor : sensorsToTrack) {
            subscribeToMembers(group, sensor, new SensorEventListener<Object>() {
                @Override public void onEvent(SensorEvent<Object> event) {
                    onEntityEvent(EventType.ENTITY_CHANGE, event.getSource());
                }
            });
        }
        
        for (Entity it : group.getMembers()) { onEntityEvent(EventType.ENTITY_ADDED, it); }
        
        // FIXME cluster may be remote, we need to make this retrieve the remote values, or store members in local mgmt node, or use children
    }

    protected void unsubscribeFromGroup() {
        if (getSubscriptionTracker()!=null && group != null) unsubscribe(group);
    }

    public enum EventType { ENTITY_CHANGE, ENTITY_ADDED, ENTITY_REMOVED }
    
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
