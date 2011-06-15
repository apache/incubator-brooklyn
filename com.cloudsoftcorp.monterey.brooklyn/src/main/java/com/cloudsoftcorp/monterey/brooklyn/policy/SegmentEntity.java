package com.cloudsoftcorp.monterey.brooklyn.policy;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.overpaas.activity.EventDictionary;
import org.overpaas.activity.NestedMapAccessor;
import org.overpaas.activity.impl.EventImpl;
import org.overpaas.activity.impl.NestedMapAccessorImpl;
import org.overpaas.policy.MoveableEntity;

import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport;
import com.cloudsoftcorp.monterey.network.control.plane.ManagementNode;
import com.cloudsoftcorp.monterey.network.m.MediationWorkrateItem.MediationWorkrateItemNames;
import com.cloudsoftcorp.monterey.network.m.MediationWorkrateItem.SegmentWorkrateItem;
import com.cloudsoftcorp.monterey.node.api.NodeId;
import com.cloudsoftcorp.util.Loggers;
import com.google.common.collect.ImmutableMap;

public class SegmentEntity extends MontereyEntity implements MoveableEntity {

    private static final Logger LOG = Loggers.getLoggerForClass();
    
    public static final String MSGS_PER_SEC_INBOUND_METRIC = "msgsPerSecInbound";

    private final String segmentId;

    public SegmentEntity(ManagementNode managementNode, String segmentId) {
        super(managementNode);
        this.segmentId = segmentId;
    }

    public String getSegmentId() {
        return segmentId;
    }

    @Override
    protected void doNotifySubscribers() {
        notifySubscribers(new EventImpl(EventDictionary.ATTRIBUTE_CHANGED_EVENT_NAME, getMetrics()));
    }

    @Override
    public NestedMapAccessor getMetrics() {
        NodeId node = getNetworkInfo().getSegmentAllocation(segmentId);
        WorkrateReport report = getNetworkInfo().getActivityModel().getWorkrateReport(node);
        SegmentWorkrateItem item = (SegmentWorkrateItem) report.getWorkrateItem(MediationWorkrateItemNames.nameForSegment(segmentId));
        double msgsPerSecInbound;
        if (item != null) {
            double msgCount = item.getReceivedRequestCount();
            msgsPerSecInbound = (msgCount/report.getReportPeriodDuration())*1000;
            if (LOG.isLoggable(Level.FINEST)) LOG.finest(String.format("Metrics for mediator: node=%s, msgCount=%s, duration=%s",report.getSourceNodeAddress(), msgCount, report.getReportPeriodDuration()));
        } else {
            msgsPerSecInbound = 0;
            if (LOG.isLoggable(Level.FINEST)) LOG.finest(String.format("Metrics for mediator missing: node=%s",report.getSourceNodeAddress()));
        }
        
        Map<String, Object> metrics = ImmutableMap.of(MSGS_PER_SEC_INBOUND_METRIC, (Object)msgsPerSecInbound);
        return new NestedMapAccessorImpl(metrics);
    }
}
