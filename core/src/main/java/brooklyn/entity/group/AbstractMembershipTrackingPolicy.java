package brooklyn.entity.group;

import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.trait.Startable;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.basic.AbstractPolicy;

import com.google.common.base.Preconditions;


abstract class AbstractMembershipTrackingPolicy extends AbstractPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractMembershipTrackingPolicy.class);
    private AbstractGroup group;
    
    public AbstractMembershipTrackingPolicy(Map flags) {
        super(flags);
    }
    public AbstractMembershipTrackingPolicy() {
        this(Collections.emptyMap());
    }
    
    public void setGroup(AbstractGroup group) {
        Preconditions.checkNotNull(group, "The group cannot be null");
        this.group = group;
        reset();
        subscribe(group, DynamicGroup.MEMBER_ADDED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                onEntityAdded(event.getValue());
            }
        });
        subscribe(group, DynamicGroup.MEMBER_REMOVED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                onEntityRemoved(event.getValue());
            }
        });
        // TODO having last value would be handy in the event publication (or suppressing if no change)
        subscribeToMembers(group, Startable.SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override public void onEvent(SensorEvent<Boolean> event) {
                onEntityChange(event.getSource());
            }
        });
        for (Entity it : group.getMembers()) { onEntityAdded(it); }
        
        // FIXME cluster may be remote, we need to make this retrieve the remote values, or store members in local mgmt node, or use children
    }

    public void reset() {
        if (getSubscriptionTracker()!=null) unsubscribe(group);
    }

    /**
     * Called when a member's "up" sensor changes
     */
    protected void onEntityChange(Entity member) {}

    //TODO - don't need/want members below, if we have the above
    
    /**
     * Called when a member is added.
     */
    protected void onEntityAdded(Entity member) {}

    /**
     * Called when a member is removed.
     */
    protected void onEntityRemoved(Entity member) {}
}
