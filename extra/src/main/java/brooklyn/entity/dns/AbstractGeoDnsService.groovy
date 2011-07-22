package brooklyn.entity.dns

import java.util.Map;
import java.util.Set

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.group.AbstractMembershipTrackingPolicy
import brooklyn.policy.Policy


abstract class AbstractGeoDnsService extends AbstractEntity {
    
//    public static AttributeSensor<Set<HostGeoInfo>> TARGET_HOSTS = [ Set.class, "target.hosts" ];
    
    private final Map<Entity, HostGeoInfo> targetHosts = new HashMap<Entity, HostGeoInfo>();
    

    public AbstractGeoDnsService(Map properties = [:], Entity owner = null) {
        super(properties, owner);
    }
    
    public void setGroup(AbstractGroup group) {
        AbstractMembershipTrackingPolicy amtp = new AbstractMembershipTrackingPolicy() {
            protected void onEntityAdded(Entity entity) { addTargetHost(entity); }
            protected void onEntityRemoved(Entity entity) { removeTargetHost(entity); }
        }
        addPolicy(amtp);
        amtp.setGroup(group);
    }

    protected abstract void reconfigureService(Set<HostGeoInfo> targetHosts);
    
    public void addTargetHost(Entity e) {
        if (targetHosts.containsKey(e)) return;
        HostGeoInfo hgi = HostGeoInfo.fromEntity(e)
        if (hgi == null) {
            // TODO: log warning (no geo info found for entity)
        } else {
            targetHosts.put(e, hgi);
            update();
        }
    }

    public void removeTargetHost(Entity e) {
        if (targetHosts.remove(e))
            update();
    }
    
    public void update() {
        reconfigureService(new LinkedHashSet<HostGeoInfo>(targetHosts.values()));
//        emit(TARGET_HOSTS, targetHosts);
    }
    
}
