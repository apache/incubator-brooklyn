package brooklyn.entity.group

import java.net.InetAddress
import java.util.List
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.location.MachineLocation
import brooklyn.management.SubscriptionHandle
import brooklyn.policy.basic.AbstractPolicy

import com.google.common.base.Preconditions


abstract class AbstractMembershipTrackingPolicy extends AbstractPolicy {
    private AbstractGroup group;
    
    
    public AbstractMembershipTrackingPolicy() { }
    
    public AbstractMembershipTrackingPolicy(AbstractGroup group) {
        setGroup(group);
    }
    
    public void setGroup(AbstractGroup group) {
        Preconditions.checkNotNull(group, "The group cannot be null");
        this.group = group;
        reset();
        group.members.each { onEntityAdded it }
        subscribe(group, group.MEMBER_ADDED, { onEntityAdded it } as EventListener);
        subscribe(group, group.MEMBER_REMOVED, { onEntityRemoved it } as EventListener);
    }

    public void reset() {
        unsubscribe(group)
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
