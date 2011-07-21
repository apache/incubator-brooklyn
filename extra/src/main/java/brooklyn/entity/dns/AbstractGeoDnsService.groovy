package brooklyn.entity.dns

import java.util.Set

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.dns.HostGeoInfo
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.SmartSubdomain
import brooklyn.entity.group.AbstractMembershipTrackingPolicy
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicConfigKey


abstract class AbstractGeoDnsService extends AbstractEntity {
    
    public static AttributeSensor<Set<HostGeoInfo>> TARGET_HOSTS = [ Set.class, "target.hosts" ];
    
    private final Map<Entity,HostGeoInfo> targetHosts = new HashMap<Entity,HostGeoInfo>();
    

    public AbstractGeoDnsService(AbstractGroup group) {
        addPolicy(new AbstractMembershipTrackingPolicy(group) {
            protected void onEntityAdded(Entity entity) { addTargetHost(entity); }
            protected void onEntityRemoved(Entity entity) { removeTargetHost(entity); }
        });
    }

    protected abstract void reconfigureService(Set<HostGeoInfo> targetHosts);
    
    private void addTargetHost(Entity e) {
        if (targetHosts.containsKey(e)) return;
        targetHosts.put(e, HostGeoInfo.fromEntity(e));
        update();
    }

    private void removeTargetHost(Entity e) {
        if (targetHosts.remove(e))
            update();
    }
    
    private void update() {
        reconfigureService(targetHosts.values());
        emit(TARGET_HOSTS, targetHosts);
    }
    
}
