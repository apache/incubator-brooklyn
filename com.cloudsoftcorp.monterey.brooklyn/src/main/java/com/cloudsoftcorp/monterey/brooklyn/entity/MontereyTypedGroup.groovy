package com.cloudsoftcorp.monterey.brooklyn.entity

import brooklyn.entity.basic.DynamicGroup


public class MontereyTypedGroup extends DynamicGroup {

    // FIXME Implement Startable
    
    private static final Logger LOG = Loggers.getLogger(MediatorNode.class);

    final Dmn1NodeType nodeType;
    
    protected final MontereyProvisioner montereyProvisioner;
    protected final Gson gson;
    protected final MontereyNetworkConnectionDetails connectionDetails;
    
    static MontereyTypedGroup newSingleLocationInstance(MontereyNetworkConnectionDetails connectionDetails, MontereyProvisioner montereyProvisioner, Dmn1NodeType nodeType, final Location loc) {
        return new MontereyTypedGroup(connectionDetails, montereyProvisioner, nodeType, Collections.singleton(loc), { it.contains(loc) } );
    }
    
    static MontereyTypedGroup newAllLocationsInstance(MontereyNetworkConnectionDetails connectionDetails, MontereyProvisioner montereyProvisioner, Dmn1NodeType nodeType, Collection<Location> locs) {
        return new MontereyTypedGroup(connectionDetails, montereyProvisioner, nodeType, locs, { true } );
    }
    
    MontereyTypedGroup(MontereyNetworkConnectionDetails connectionDetails, MontereyProvisioner montereyProvisioner, Dmn1NodeType nodeType, Collection<Location> locs, Closure locFilter) {
        this.connectionDetails = connectionDetails;
        this.nodeType = nodeType;
        this.locations.addAll(locs);
        ClassLoadingContext classloadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext();
        GsonSerializer gsonSerializer = new GsonSerializer(classloadingContext);
        gson = gsonSerializer.getGson();

        setEntityFilter { Entity e -> 
            e instanceof AbstractMontereyNode && nodeType.equals(((AbstractMontereyNode)e).getNodeType()) && 
                    locFilter(((AbstractMontereyNode)e).getLocations())
        }
        
        LOG.info("Group created for node type $nodeType in locations $locations");        
    }
    
    void refreshLocations(Collection<Location> locs) {
        this.locations.clear();
        this.locations.addAll(locs);
    }
    
    void dispose() {
    }
    
    public void provisionNodes(int num) {
        for (i in 0..num) {
            MontereyContainerNode node = montereyProvisioner.requestNode(locations)
            node.rollout(nodeType)
        }
    }
    
    public void resize(Integer desiredSize) {
        int currentSize = getExpecteSize()
        if (currentSize < desiredSize) {
            
        }
    }
    
    private MontereyNetwork getMontereyNetwork() {
        Entity contender = this
        while (contender != null) {
            if (contender instanceof MontereyNetwork) return (MontereyNetwork)contender
            contender = contender.getOwner()
        }
        
        return null
    }
}
