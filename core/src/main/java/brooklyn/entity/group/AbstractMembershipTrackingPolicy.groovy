package brooklyn.entity.group

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.policy.basic.AbstractPolicy

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.base.Preconditions


abstract class AbstractMembershipTrackingPolicy extends AbstractPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractMembershipTrackingPolicy.class)
    private AbstractGroup group;
    
    
    public AbstractMembershipTrackingPolicy() { }
    
    public void setGroup(AbstractGroup group) {
        Preconditions.checkNotNull(group, "The group cannot be null");
        this.group = group;
        reset();
        group.members.each { onEntityAdded it }
        subscribe(group, group.MEMBER_ADDED, { SensorEvent<Entity> evt -> onEntityAdded evt.value } as SensorEventListener);
        subscribe(group, group.MEMBER_REMOVED, { SensorEvent<Entity> evt  -> onEntityRemoved evt.value } as SensorEventListener);
        // FIXME cluster may be remote, we need to make this retrieve the remote values, or store members in local mgmt node, or use children
    }

    public void reset() {
        if (getSubscriptionTracker()!=null) unsubscribe(group)
    }

    /**
     * Called when a member is added.
     */
    protected void onEntityAdded(Entity member) {}

    /**
     * Called when a member is removed.
     */
    protected void onEntityRemoved(Entity member) {}
}
