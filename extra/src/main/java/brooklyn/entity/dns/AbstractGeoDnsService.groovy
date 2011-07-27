package brooklyn.entity.dns

import brooklyn.entity.Entity;
import java.util.Map
import java.util.Set
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.ScheduledThreadPoolExecutor;

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.group.AbstractMembershipTrackingPolicy
import brooklyn.management.internal.CollectionChangeListener
import brooklyn.management.internal.LocalManagementContext


abstract class AbstractGeoDnsService extends AbstractEntity {
    protected static final Logger log = LoggerFactory.getLogger(AbstractGeoDnsService.class);
    protected Group targetEntityProvider = null;
    protected Map<Entity, HostGeoInfo> targetHosts = new HashMap<Entity, HostGeoInfo>();
    

    public AbstractGeoDnsService(Map properties = [:], Entity owner = null) {
        super(properties, owner);
    }
    
    // FIXME: also accept a closure in case the group doesn't exist yet
    public void setTargetEntityProvider(final AbstractGroup group) {
        this.targetEntityProvider = group;
        
        // TODO: remove polling once locations can be determined via subscriptions
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
            new Runnable() {
                public void run() { refreshGroupMembership(); }
            }, 0, 5, TimeUnit.SECONDS
        );
    }

    protected abstract void reconfigureService(Set<HostGeoInfo> targetHosts);
    
    // TODO: remove group member polling once locations can be determined via subscriptions
    protected void refreshGroupMembership() {
        try {
            if (targetEntityProvider == null)
                return;
            if (targetEntityProvider instanceof DynamicGroup)
                ((DynamicGroup) targetEntityProvider).rescanEntities();
            
            boolean changed = false; 
            targetEntityProvider.members.each { Entity e ->
                if (targetHosts.containsKey(e))
                    return;
                HostGeoInfo hgi = HostGeoInfo.fromEntity(e)
                if (hgi == null)
                    log.warn("Failed to derive geo information for entity $e");
                else {
                    targetHosts.put(e, hgi);
                    changed = true;
                }
            }
            // TODO: handle member removal
            if (changed)
                update();
            
        } catch (Exception e) {
            log.error("Problem refreshing group membership: $e", e);
        }
    }
    
    protected void addTargetHost(Entity e) {
        log.info("Adding DNS redirection target entity: $e");
        if (targetHosts.containsKey(e)) {
            log.warn("Ignoring already-added entity $e");
            return;
        }
        HostGeoInfo hgi = HostGeoInfo.fromEntity(e)
        if (hgi == null) {
            log.warn("Failed to derive geo information for entity $e");
        } else {
            targetHosts.put(e, hgi);
            update();
        }
    }

    protected void removeTargetHost(Entity e) {
        if (targetHosts.remove(e))
            update();
    }
    
    protected void update() {
        reconfigureService(new LinkedHashSet<HostGeoInfo>(targetHosts.values()));
    }
    
}
