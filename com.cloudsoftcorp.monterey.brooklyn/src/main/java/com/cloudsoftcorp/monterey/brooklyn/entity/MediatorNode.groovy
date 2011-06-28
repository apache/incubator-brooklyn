package com.cloudsoftcorp.monterey.brooklyn.entity

import java.util.logging.Level
import java.util.logging.Logger

import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location

import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.m.AbstractMediationWorkrateItem.BasicMediatorTotalWorkrateItem
import com.cloudsoftcorp.monterey.network.m.MediationWorkrateItem.MediatorTotalWorkrateItem
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.cloudsoftcorp.util.Loggers

public class MediatorNode extends AbstractMontereyNode {

    private static final Logger LOG = Loggers.getLogger(MediatorNode.class);
    
    public static final BasicAttributeSensor<Integer> WORKRATE_MSGS_PER_SEC = [ Double, "monterey.workrate.msgsPerSec", "Messages per sec" ]
    
    MediatorNode(MontereyNetworkConnectionDetails connectionDetails, NodeId nodeId, Location location) {
        super(connectionDetails, nodeId, Dmn1NodeType.M);
    }
    
    @Override
    void updateWorkrate(WorkrateReport report) {
        MediatorTotalWorkrateItem item = (MediatorTotalWorkrateItem) report.getWorkrateItem(BasicMediatorTotalWorkrateItem.NAME);
        if (item != null) {
            double msgCount = item.getReceivedRequestCount();
            double msgCountPerSec = (msgCount/report.getReportPeriodDuration())*1000;
            
            updateAttribute WORKRATE_MSGS_PER_SEC, msgCountPerSec
            if (LOG.isLoggable(Level.FINEST)) LOG.finest(String.format("(node=%s, msgCount=%s, duration=%s), ",report.getSourceNodeAddress(), msgCount, report.getReportPeriodDuration()));
        }
    }
}
