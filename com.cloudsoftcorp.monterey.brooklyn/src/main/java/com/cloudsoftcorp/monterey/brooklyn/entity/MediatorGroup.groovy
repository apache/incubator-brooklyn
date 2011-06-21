package com.cloudsoftcorp.monterey.brooklyn.entity

import groovy.lang.Closure

import java.util.Map
import java.util.concurrent.ConcurrentHashMap

import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.trait.Balanceable

import com.cloudsoftcorp.monterey.network.control.plane.web.PlumberWebProxy
import com.cloudsoftcorp.monterey.network.control.wipapi.Dmn1PlumberInternalAsync
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.google.gson.Gson

public class MediatorGroup extends DynamicGroup implements Balanceable {

    private final Map<NodeId,MediatorNode> mediators = new ConcurrentHashMap<NodeId,AbstractMontereyNode>();
    
    // TODO inject or instantiate gson
    private Gson gson;
    
    private final MontereyNetworkConnectionDetails connectionDetails;
    private final NodeId nodeId;
    
    MediatorGroup(MontereyNetworkConnectionDetails connectionDetails, NodeId nodeId) {
        this.connectionDetails = connectionDetails;
        this.nodeId = nodeId;
    }

    void setEntityFilter(Closure entityFilter) {
        this.entityFilter = entityFilter
        rescanEntities()
    }
    
    private void moveSegment(String segmentId, MediatorNode destination) {
        Dmn1PlumberInternalAsync plumber = new PlumberWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        plumber.migrateSegment(segmentId, destination.getNodeId());
    }
}
