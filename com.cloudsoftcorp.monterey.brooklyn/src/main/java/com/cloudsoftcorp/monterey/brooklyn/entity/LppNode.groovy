package com.cloudsoftcorp.monterey.brooklyn.entity

import java.util.Collection

import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location

import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.api.NodeSummary
import com.cloudsoftcorp.monterey.network.control.plane.GsonSerializer
import com.cloudsoftcorp.monterey.network.control.plane.web.PlumberWebProxy
import com.cloudsoftcorp.monterey.network.control.wipapi.Dmn1PlumberInternalAsync
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.google.common.base.Preconditions
import com.google.gson.Gson

public class LppNode extends AbstractMontereyNode {

    public static final BasicAttributeSensor<NodeId> DOWNSTREAM_ROUTER = [ NodeId, "monterey.node.downstreamRouter", "Downstream router id" ]

    private final Gson gson;
    
    LppNode(MontereyNetworkConnectionDetails connectionDetails, NodeId nodeId, Location location) {
        super(connectionDetails, nodeId, Dmn1NodeType.LPP, location);
        
        ClassLoadingContext classloadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext();
        GsonSerializer gsonSerializer = new GsonSerializer(classloadingContext);
        gson = gsonSerializer.getGson();
    }
    
    public void updateTopology(NodeSummary summary, Collection<NodeId> downstreamNodes) {
        Preconditions.checkArgument(downstreamNodes.size() <= 1, "downstreamNodes.size=="+downstreamNodes.size());
        NodeId downstreamRouter = (downstreamNodes.isEmpty()) ? null : downstreamNodes.iterator().next();
        setAttribute DOWNSTREAM_ROUTER, downstreamRouter
    }

    @Override
    void updateWorkrate(WorkrateReport report) {
    }
    
    public void routerSwitchover(MrNode newRouter) {
        Dmn1PlumberInternalAsync plumber = new PlumberWebProxy(connectionDetails.managementUrl, gson, connectionDetails.webApiAdminCredential);
        plumber.routerSwitchover(nodeId, newRouter.getNodeId());
    }
}
