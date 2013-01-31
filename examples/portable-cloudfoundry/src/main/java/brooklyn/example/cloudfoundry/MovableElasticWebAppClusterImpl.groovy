package brooklyn.example.cloudfoundry

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.trait.Startable
import brooklyn.entity.trait.StartableMethods
import brooklyn.entity.webapp.ElasticJavaWebAppService
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry

import com.google.common.collect.Iterables

public class MovableElasticWebAppClusterImpl extends AbstractEntity implements MovableElasticWebAppCluster {

    public static final Logger log = LoggerFactory.getLogger(MovableElasticWebAppClusterImpl.class);
    
    public MovableElasticWebAppClusterImpl() {
    }
    
    @Deprecated // use EntityManager.createEntity() or ApplicationBuilder.createChild()
    public MovableElasticWebAppClusterImpl(Map flags, Entity parent) {
        super(flags, parent);
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        if (!getChildren().isEmpty()) {
            log.debug("Starting $this; it already has children, so start on children is being invoked")
            StartableMethods.start(this, locations);
        } else {
            Entity svc = createClusterIn( Iterables.getOnlyElement(locations) );
            log.debug("Starting $this; no children, so created $svc and now starting it")
            if (svc in Startable) ((Startable)svc).start(locations);
            setAttribute(PRIMARY_SVC_ENTITY_ID, svc.id)
        }
    }

    public EntityLocal createClusterIn(Location location) {
        //TODO the policy
//        app.web.cluster.addPolicy(app.policy)
        EntityLocal result = new ElasticJavaWebAppService.Factory()
                .newFactoryForLocation(location)
                .newEntity([:], this);
        Entities.manage(result);
        return result;
    }
    
    @Override
    public void stop() {
        StartableMethods.stop(this);
    }

    @Override
    public void restart() {
        StartableMethods.restart(this);
    }

    /* 
     * "move" consists of creating a secondary (call it Y),
     * promoting this one (Y) swapping it for the old-primary (call it X),
     * then destroying the old-primary-now-secondary (X)
     */

    @Override
    public String createSecondaryInLocation(String l) {
        Location location = new LocationRegistry().resolve(l);
        Entity svc = createClusterIn(location);
        Entities.start(svc, [location]);
        setAttribute(SECONDARY_SVC_ENTITY_IDS, (getAttribute(SECONDARY_SVC_ENTITY_IDS) ?: []) + svc.id);
        return svc.id;
    }

    @Override
    public String promoteSecondary(String idOfSecondaryToPromote) {
        Collection<String> currentSecondaryIds = getAttribute(SECONDARY_SVC_ENTITY_IDS)
        if (!currentSecondaryIds.contains(idOfSecondaryToPromote)) 
            throw new IllegalStateException("Cannot promote unknown secondary $idOfSecondaryToPromote "+
                "(available secondaries are $currentSecondaryIds)");
            
        String primaryId = getAttribute(PRIMARY_SVC_ENTITY_ID);
        
        setAttribute(PRIMARY_SVC_ENTITY_ID, idOfSecondaryToPromote);
        currentSecondaryIds.remove(idOfSecondaryToPromote);
        currentSecondaryIds << primaryId;
        setAttribute(SECONDARY_SVC_ENTITY_IDS, currentSecondaryIds);
        return primaryId;
    }
    
    @Override
    public void destroySecondary(String idOfSecondaryToDestroy) {
        Collection<String> currentSecondaryIds = getAttribute(SECONDARY_SVC_ENTITY_IDS)
        if (!currentSecondaryIds.contains(idOfSecondaryToDestroy))
            throw new IllegalStateException("Cannot promote unknown secondary $idOfSecondaryToDestroy "+
                "(available secondaries are $currentSecondaryIds)");
            
        currentSecondaryIds.remove(idOfSecondaryToDestroy);
        setAttribute(SECONDARY_SVC_ENTITY_IDS, currentSecondaryIds);
        
        Entity secondary = getEntityManager().getEntity(idOfSecondaryToDestroy);
        Entities.destroy(secondary);
    }

    @Override
    public String move(String location) {
        String newPrimary = createSecondaryInLocation(location);
        String oldPrimary = promoteSecondary(newPrimary);
        destroySecondary(oldPrimary);
        return newPrimary;
    }
        
}
