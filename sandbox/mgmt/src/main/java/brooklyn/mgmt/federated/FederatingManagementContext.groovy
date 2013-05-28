package brooklyn.mgmt.federated

import java.util.concurrent.Callable

import org.infinispan.Cache
import org.infinispan.manager.EmbeddedCacheManager
import org.infinispan.notifications.Listener
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified
import org.infinispan.notifications.cachelistener.event.CacheEntryModifiedEvent
import org.infinispan.remoting.transport.Address
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.management.EntityManager
import brooklyn.management.ExecutionManager
import brooklyn.management.SubscriptionManager
import brooklyn.management.Task
import brooklyn.management.internal.AbstractManagementContext
import brooklyn.management.internal.CollectionChangeListener
import brooklyn.management.internal.LocalManagementContext
import brooklyn.util.text.Identifiers

import com.thoughtworks.xstream.XStream

public class FederatingManagementContext extends AbstractManagementContext {
    
    private static final Logger log = LoggerFactory.getLogger(FederatingManagementContext.class)
    
    PretendRemoteManagementAccess rem = new PretendRemoteManagementAccess()
    
    Set<Application> apps = []

    EmbeddedCacheManager cm;    
    Cache<String,Address> managementNodeMap;
    Cache<String,Entity> entitiesMap;
    Cache<String,String> entitiesMasterMap;

    public FederatingManagementContext() {
        cm = BrooklynManagementNode.newCacheManager();
        //TODO set sync stats:  mgmt goes everywhere; and entities mastering goes everywhere
        managementNodeMap = cm.getCache(BrooklynManagementNode.MAP_MGMT_NODE_ID_TO_INFINISPAN_ADDRESS);
        entitiesMap = cm.getCache(BrooklynManagementNode.MAP_ENTITIES_BY_ID);
        entitiesMasterMap = cm.getCache(BrooklynManagementNode.MAP_ENTITIES_TO_MGMT_NODE_ID);
    }

    //FIXME remove    
    public static void main(String[] args) {
        new FederatingManagementContext().onApplicationStart(null);
    }
    
    @Override
    protected void manageIfNecessary(Entity entity, Object context) {
        throw new UnsupportedOperationException(); // FIXME Code rot
    }
    
    @Override
    public void registerApplication(Application app) {
        apps.add(app);
    }
    @Override
    public Collection<Application> getApplications() {
        return apps
    }
    public Collection<Entity> getEntities() {
        null
    }
    public Entity getEntity(String id) {
        //FIXME
        null
	}
    public void manage(Entity e) {
//        synchronized (knownEntities) {
//            knownEntities.put(e.getId(), e);
//        }
//        if (e instanceof Application) apps << e
//        for (Entity ei : e.getChildren())
//            manage(ei);
    }
    public synchronized void unmanage(Entity e) {
    }
    
    public SubscriptionManager getSubscriptionManager() { return rem.getContext().getSubscriptionManager(); }
    public ExecutionManager getExecutionManager() { return rem.getContext().getExecutionManager(); }
    public EntityManager getEntityManager() { return rem.getContext().getEntityManager(); }
    
    public void onApplicationStart(Application app) {
        String remoteNodeUid = Identifiers.makeRandomId(8);
        c.addListener(new NodeStartListener());
        c.put(remoteNodeUid, BrooklynManagementNode.WELCOME_MESSAGE);
        
        BrooklynManagementNode.main(remoteNodeUid);
        Thread.sleep(10000);
        System.exit(0);
                
        //TODO this should get invoked by the app when it starts
        moveToManagementPlaneRecursively(app)
    }
    @Listener
    public static class NodeStartListener {
        @CacheEntryModified
        public void onMod(CacheEntryModifiedEvent evt) {
            println "CHANGE: ${evt}"
        }
    }
    
    public void moveToManagementPlaneRecursively(Entity e) {
        if (e.entityProxyForManagement!=null)
            //skip entities already managed
            return;

        moveToManagementPlaneJustThis(e);
                    
        for (Entity ec : e.getChildren()) {
            moveToManagementPlaneRecursively(ec)
        } 
    }
    private void moveToManagementPlaneJustThis(Entity e) {
        //FIXME should make it remote
        assert e.entityProxyForManagement == null : "cannot move an entity we do not manage ("+e+")"
        Entity remoteCopy = clone(e);
        e.entityProxyForManagement = remoteCopy; 
    }
    
    // TODO Copied from groovy LanguageUtils.clone(T), to remove dependency 
    private <T> T clone(T src) {
        XStream xstream = new XStream();
        xstream.setClassLoader(src.getClass().getClassLoader())
        xstream.fromXML(xstream.toXML(src))
    }

    public boolean isManagedLocally(Entity e) {
        //TODO
        return true
	}
    public Task runAtEntity(Map flags, Entity entity, Callable code) {
        //TODO
        entity.executionContext.submit(flags, code)
	}
    
    protected synchronized boolean manageNonRecursive(Entity e) {
        //TODO
        return true
    }
    protected synchronized boolean unmanageNonRecursive(Entity e) {
        //TODO
        return true
    }

    public void addEntitySetListener(CollectionChangeListener<Entity> listener) {
        //TODO
    }
    public void removeEntitySetListener(CollectionChangeListener<Entity> listener) {
        //TODO
    }

}


public interface RemoteManagementAccess {
}

public class PretendRemoteManagementAccess {
    LocalManagementContext context = []
}
