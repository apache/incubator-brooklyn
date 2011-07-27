package com.cloudsoftcorp.monterey.brooklyn.entity

import java.util.logging.Level
import java.util.logging.Logger

import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.basic.BasicAttributeSensor

import com.cloudsoftcorp.monterey.control.api.SegmentSummary
import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport
import com.cloudsoftcorp.monterey.network.m.MediationWorkrateItem.MediationWorkrateItemNames
import com.cloudsoftcorp.monterey.network.m.MediationWorkrateItem.SegmentWorkrateItem
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.cloudsoftcorp.util.Loggers

public class Segment extends AbstractEntity {

    private static final Logger LOG = Loggers.getLogger(Segment.class);
    
    // TODO Is this the best place to maintain "predicted" when a segment is moving around?
    // Currently leaving that in the logic behind Dmn1NetworkInfo.getActivityModel.

    // TODO Share constant for all nodes plus segment?
    public static final BasicAttributeSensor<String> ID = [ String, "monterey.segment.id", "Segment id" ]
    public static final BasicAttributeSensor<String> NAME = [ String, "monterey.segment.name", "Segment name" ]
    public static final BasicAttributeSensor<Integer> WORKRATE_MSGS_PER_SEC = [ Double, "monterey.workrate.msgsPerSec", "Messages per sec" ]
    public static final BasicAttributeSensor<NodeId> MEDIATOR = [ NodeId, "monterey.segment.mediator", "Mediator that contains this segment" ]
    
    private final MontereyNetworkConnectionDetails connectionDetails;
    private final String segmentId;
    
    Segment(MontereyNetworkConnectionDetails connectionDetails, String segmentId) {
        this.connectionDetails = connectionDetails;
        this.segmentId = segmentId;
        
        LOG.info("Segment "+segmentId+" created");        
    }
    
    @Override
    public String getDisplayName() {
        return (segmentId ? "Segment ("+segmentId+")" : super.getDisplayName())    
    }
    
    public String segmentId() {
        return segmentId;
    }

    public void updateTopology(SegmentSummary summary, NodeId mediator) {
        setAttribute ID, summary.getUid() // TODO dont' keep re-publishing!
        setAttribute NAME, summary.getDisplayName()
        setAttribute MEDIATOR, mediator
    }

    public void updateWorkrate(WorkrateReport report) {
        SegmentWorkrateItem item = (SegmentWorkrateItem) report.getWorkrateItem(MediationWorkrateItemNames.nameForSegment(segmentId));
        if (item != null) {
            double msgCount = item.getReceivedRequestCount();
            double msgCountPerSec = (msgCount/report.getReportPeriodDuration())*1000;
            
            setAttribute WORKRATE_MSGS_PER_SEC, msgCountPerSec
            if (LOG.isLoggable(Level.FINEST)) LOG.finest(String.format("(node=%s, msgCount=%s, duration=%s), ",report.getSourceNodeAddress(), msgCount, report.getReportPeriodDuration()));
        }
    }
}
