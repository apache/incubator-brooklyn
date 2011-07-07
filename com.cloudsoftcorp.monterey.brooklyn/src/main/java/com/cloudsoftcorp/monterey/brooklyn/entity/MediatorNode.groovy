package com.cloudsoftcorp.monterey.brooklyn.entity

import java.util.Collection
import java.util.logging.Level
import java.util.logging.Logger

import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location

import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.api.NodeSummary
import com.cloudsoftcorp.monterey.network.control.plane.GsonSerializer
import com.cloudsoftcorp.monterey.network.control.plane.web.PlumberWebProxy
import com.cloudsoftcorp.monterey.network.control.wipapi.Dmn1PlumberInternalAsync
import com.cloudsoftcorp.monterey.network.m.AbstractMediationWorkrateItem.BasicMediatorTotalWorkrateItem
import com.cloudsoftcorp.monterey.network.m.MediationWorkrateItem.MediatorTotalWorkrateItem
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.cloudsoftcorp.util.Loggers
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.google.common.base.Preconditions
import com.google.gson.Gson

public class MediatorNode extends AbstractMontereyNode {

    private static final Logger LOG = Loggers.getLogger(MediatorNode.class);

    public static final BasicAttributeSensor<NodeId> DOWNSTREAM_ROUTER = MontereyAttributes.DOWNSTREAM_ROUTER;
    public static final BasicAttributeSensor<Integer> WORKRATE_MSGS_PER_SEC = MontereyAttributes.WORKRATE_MSGS_PER_SEC;
    
    private final Gson gson;
    
    MediatorNode(MontereyNetworkConnectionDetails connectionDetails, NodeId nodeId, Location location) {
        super(connectionDetails, nodeId, Dmn1NodeType.M, location);
        
        ClassLoadingContext classloadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext();
        GsonSerializer gsonSerializer = new GsonSerializer(classloadingContext);
        gson = gsonSerializer.getGson();
    }

    public void routerSwitchover(TpNode newRouter) {
        Dmn1PlumberInternalAsync plumber = new PlumberWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        plumber.routerSwitchover(nodeId, newRouter.getNodeId());
    }
    
    @Override    
    void updateTopology(NodeSummary summary, Collection<NodeId> downstreamNodes) {
        Preconditions.checkArgument(downstreamNodes.size() <= 1, "downstreamNodes.size=="+downstreamNodes.size());
        NodeId downstreamRouter = (downstreamNodes.isEmpty()) ? null : downstreamNodes.iterator().next();
        setAttribute DOWNSTREAM_ROUTER, downstreamRouter
    }

    @Override
    void updateWorkrate(WorkrateReport report) {
        MediatorTotalWorkrateItem item = (MediatorTotalWorkrateItem) report.getWorkrateItem(BasicMediatorTotalWorkrateItem.NAME);
        if (item != null) {
            double msgCount = item.getReceivedRequestCount();
            double msgCountPerSec = (msgCount/report.getReportPeriodDuration())*1000;
            
            setAttribute WORKRATE_MSGS_PER_SEC, msgCountPerSec
            if (LOG.isLoggable(Level.FINEST)) LOG.finest(String.format("(node=%s, msgCount=%s, duration=%s), ",report.getSourceNodeAddress(), msgCount, report.getReportPeriodDuration()));
        }
    }
}
