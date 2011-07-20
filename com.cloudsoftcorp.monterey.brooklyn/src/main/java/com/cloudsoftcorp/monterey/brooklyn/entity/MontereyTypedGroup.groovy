package com.cloudsoftcorp.monterey.brooklyn.entity

import brooklyn.entity.basic.DynamicGroup

import java.util.Collection
import java.util.logging.Logger

import brooklyn.entity.Entity
import brooklyn.entity.basic.DynamicGroup
import brooklyn.location.Location

import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.plane.GsonSerializer
import com.cloudsoftcorp.util.Loggers
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.google.gson.Gson

public class MontereyTypedGroup extends DynamicGroup {

    // FIXME Implement Startable
    
    private static final Logger LOG = Loggers.getLogger(MediatorNode.class);

    final Dmn1NodeType nodeType;
    
    protected final Gson gson;
    protected final MontereyNetworkConnectionDetails connectionDetails;
    
    static MontereyTypedGroup newSingleLocationInstance(MontereyNetworkConnectionDetails connectionDetails, Dmn1NodeType nodeType, final Location loc) {
        return new MontereyTypedGroup(connectionDetails, nodeType, Collections.singleton(loc), { it.contains(loc) } );
    }
    
    static MontereyTypedGroup newAllLocationsInstance(MontereyNetworkConnectionDetails connectionDetails, Dmn1NodeType nodeType, Collection<Location> locs) {
        return new MontereyTypedGroup(connectionDetails, nodeType, locs, { true } );
    }
    
    MontereyTypedGroup(MontereyNetworkConnectionDetails connectionDetails, Dmn1NodeType nodeType, Collection<Location> locs, Closure locFilter) {
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
        MontereyNetwork mn = getMontereyNetwork()
        for (i in 0..num) {
            MontereyContainerNode node = mn.provisionNode(locations)
            node.rollout(nodeType)
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
