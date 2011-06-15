package com.cloudsoftcorp.monterey.brooklyn.policy;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.overpaas.activity.EventDictionary;
import org.overpaas.activity.EventFilter;
import org.overpaas.activity.EventListener;
import org.overpaas.activity.NestedMapAccessor;
import org.overpaas.activity.impl.EventImpl;
import org.overpaas.activity.impl.NestedMapAccessorImpl;
import org.overpaas.policy.Entity;

import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport;
import com.cloudsoftcorp.monterey.network.control.plane.ManagementNode;
import com.cloudsoftcorp.monterey.network.m.AbstractMediationWorkrateItem.BasicMediatorTotalWorkrateItem;
import com.cloudsoftcorp.monterey.network.m.MediationWorkrateItem.MediatorTotalWorkrateItem;
import com.cloudsoftcorp.monterey.node.api.NodeId;
import com.cloudsoftcorp.util.Loggers;
import com.cloudsoftcorp.util.condition.Filter;
import com.google.common.collect.ImmutableMap;

public class MediatorEntity extends MontereyEntity implements Entity {

    private static final Logger LOG = Loggers.getLoggerForClass();
    
    public static final String MSGS_PER_SEC_INBOUND_METRIC = "msgsPerSecInbound";

    private final NodeId nodeId;

    public MediatorEntity(ManagementNode managementNode, NodeId nodeId) {
        super(managementNode);
        this.nodeId = nodeId;
    }
    
    public NodeId getNodeId() {
        return nodeId;
    }

    @Override
    public NestedMapAccessor getMetrics() {
        WorkrateReport report = getNetworkInfo().getActivityModel().getWorkrateReport(nodeId);
        MediatorTotalWorkrateItem item = (MediatorTotalWorkrateItem) report.getWorkrateItem(BasicMediatorTotalWorkrateItem.NAME);
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

    @Override
    protected void doNotifySubscribers() {
        notifySubscribers(new EventImpl(EventDictionary.ATTRIBUTE_CHANGED_EVENT_NAME, getMetrics()));
    }
}
