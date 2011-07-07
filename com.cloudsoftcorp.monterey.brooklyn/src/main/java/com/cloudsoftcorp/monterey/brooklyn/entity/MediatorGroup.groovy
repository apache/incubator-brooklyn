package com.cloudsoftcorp.monterey.brooklyn.entity

import java.util.Map
import java.util.concurrent.ConcurrentHashMap

import brooklyn.entity.Entity
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.trait.Balanceable
import brooklyn.location.Location

import com.cloudsoftcorp.monterey.network.control.plane.GsonSerializer
import com.cloudsoftcorp.monterey.network.control.plane.web.PlumberWebProxy
import com.cloudsoftcorp.monterey.network.control.wipapi.Dmn1PlumberInternalAsync
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.google.gson.Gson

public class MediatorGroup extends DynamicGroup implements Balanceable {

    private final Map<NodeId,MediatorNode> mediators = new ConcurrentHashMap<NodeId,AbstractMontereyNode>();
    
    private final Gson gson;
    
    final MontereyNetworkConnectionDetails connectionDetails;
    final Location loc;
    
    MediatorGroup(MontereyNetworkConnectionDetails connectionDetails, Location loc) {
        this.connectionDetails = connectionDetails;
        this.loc = loc;
        this.locations.add(loc);
        
        ClassLoadingContext classloadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext();
        GsonSerializer gsonSerializer = new GsonSerializer(classloadingContext);
        gson = gsonSerializer.getGson();

        setEntityFilter {
            Entity e -> e instanceof MediatorNode && e.locations.contains(loc)
        }
    }

    public void moveSegment(String segmentId, MediatorNode destination) {
        Dmn1PlumberInternalAsync plumber = new PlumberWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        plumber.migrateSegment(segmentId, destination.getNodeId());
    }
}
