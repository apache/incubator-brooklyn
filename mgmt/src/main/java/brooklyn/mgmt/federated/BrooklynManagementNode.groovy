package brooklyn.mgmt.federated

import org.infinispan.Cache
import org.infinispan.config.Configuration
import org.infinispan.config.GlobalConfiguration
import org.infinispan.manager.DefaultCacheManager
import org.infinispan.manager.EmbeddedCacheManager

class BrooklynManagementNode {

    public BrooklynManagementNode() {
        
    }
    
    
    /** expects a single key which is used to announce its presence to the invoker
     */
    public static void main(String[] args) {
        assert args.length==1 : "single argument being a key in a map expected; instead had ${args.length}, $args";
        
        FederatingManagementContext ctx = new FederatingManagementContext();

        EmbeddedCacheManager cm = newCacheManager();        
        
        Cache c = cm.getCache(MAP_MGMT_NODE_ID_TO_INFINISPAN_ADDRESS);
        println "registering $this ${cm.getAddress()} at ${args[0]}"
        Object old = c.put(args[0], cm.getAddress());
        if (!WELCOME_MESSAGE.equals(old)) {
            c.remove(args[0]);
            println "expected welcome message not found";
            System.exit(-1);
        }
        
        //TODO okay, now run...
    }
    
    public static final String MAP_MGMT_NODE_ID_TO_INFINISPAN_ADDRESS = "brooklyn.MgmtNodeIdToInfinispanAddress"
    public static final String MAP_ENTITIES_BY_ID = "brooklyn.EntityIdToEntity"
    public static final String MAP_ENTITIES_TO_MGMT_NODE_ID = "brooklyn.EntityIdToMgmtNodeId"
    public static final String WELCOME_MESSAGE = "welcome";

    public static EmbeddedCacheManager newCacheManager() {
        GlobalConfiguration gc = GlobalConfiguration.getClusteredDefault();
        Configuration cfg = new Configuration().fluent().
        mode(Configuration.CacheMode.DIST_SYNC)
            .hash().numOwners(1)
            .clustering().l1().disable()
            .build();
        return new DefaultCacheManager(gc, cfg);
    }
    
}
