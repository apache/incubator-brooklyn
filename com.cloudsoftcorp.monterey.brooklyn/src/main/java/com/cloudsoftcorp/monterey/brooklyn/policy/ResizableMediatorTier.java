package com.cloudsoftcorp.monterey.brooklyn.policy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


import brooklyn.activity.Event;
import brooklyn.activity.EventDictionary;
import brooklyn.activity.NestedMapAccessor;
import brooklyn.activity.impl.EventImpl;
import brooklyn.activity.impl.NestedMapAccessorImpl;
import brooklyn.policy.BalanceableEntity;
import brooklyn.policy.Entity;
import brooklyn.policy.MoveableEntity;
import brooklyn.policy.ResizableEntity;

import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport;
import com.cloudsoftcorp.monterey.location.api.MontereyActiveLocation;
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType;
import com.cloudsoftcorp.monterey.network.control.api.NodeSummary;
import com.cloudsoftcorp.monterey.network.control.plane.ManagementNode;
import com.cloudsoftcorp.monterey.network.control.wipapi.Dmn1PlumberInternalAsync;
import com.cloudsoftcorp.monterey.network.control.wipapi.DmnFuture;
import com.cloudsoftcorp.monterey.network.control.wipapi.LocationUtils;
import com.cloudsoftcorp.monterey.network.control.wipapi.NodesRolloutConfiguration;
import com.cloudsoftcorp.monterey.network.m.AbstractMediationWorkrateItem.BasicMediatorTotalWorkrateItem;
import com.cloudsoftcorp.monterey.network.m.MediationWorkrateItem.MediatorTotalWorkrateItem;
import com.cloudsoftcorp.monterey.node.api.NodeId;
import com.cloudsoftcorp.util.Loggers;
import com.cloudsoftcorp.util.exception.ExceptionUtils;
import com.google.common.collect.ImmutableMap;

public class ResizableMediatorTier extends MontereyEntity implements ResizableEntity, BalanceableEntity {

    // FIXME This is not production quality! It assumes that provisioning is instantaneous, so if you 
    // call resize(10); resize(10); it will probably give you 20!
    // It also doesn't play well with if there are any other policies executing concurrently.
    // TODO Could count rollout etc to track when things are completed
    //      managementNode.getEventDispatcher().addNodeLifecycleListener(new NodeLifecycleListener() {...})
    // FIXME Leaks the executor thread
    
    private static final Logger LOG = Loggers.getLoggerForClass();
    
    public static final String AVERAGE_MSGS_PER_SEC_INBOUND_METRIC = "averageMsgsPerSecInbound";
    
    private final MontereyActiveLocation location;
    private final Dmn1PlumberInternalAsync plumber;
    private final ExecutorService transitionExecutor = Executors.newFixedThreadPool(1);
    
    public ResizableMediatorTier(final MontereyActiveLocation location, ManagementNode managementNode) {
        super(managementNode);
        this.location = location;
        this.plumber = managementNode.getAsyncPlumber();
    }
    
    @Override
    protected void doNotifySubscribers() {
        notifySubscribers(new EventImpl(EventDictionary.ATTRIBUTE_CHANGED_EVENT_NAME, getMetrics()));
    }

    @Override
    public NestedMapAccessor getMetrics() {
        int numNodes = 0;
        double totalWorkrate = 0;
        StringBuilder logMsg = new StringBuilder();
        for (NodeId node : findContenderNodes(Dmn1NodeType.M, location)) {
            WorkrateReport report = getNetworkInfo().getActivityModel().getWorkrateReport(node);
            MediatorTotalWorkrateItem item = (MediatorTotalWorkrateItem) report.getWorkrateItem(BasicMediatorTotalWorkrateItem.NAME);
            if (item != null) {
                double msgCount = item.getReceivedRequestCount();
                double msgCountPerSec = (msgCount/report.getReportPeriodDuration())*1000;
                totalWorkrate += msgCountPerSec;
                numNodes++;
                if (LOG.isLoggable(Level.FINEST)) logMsg.append(String.format("(node=%s, msgCount=%s, duration=%s), ",report.getSourceNodeAddress(), msgCount, report.getReportPeriodDuration()));
            }
        }
        double averageMsgsPerSecInbound = (numNodes != 0) ? totalWorkrate/numNodes : 0;
        if (LOG.isLoggable(Level.FINEST)) LOG.finest("Calculated metrics for mediator-tier in "+location+"; numNodes="+numNodes+"; totalWorkrate="+totalWorkrate+"; average="+averageMsgsPerSecInbound+"; breakdown="+logMsg);
        
        Map<String, Object> metrics = ImmutableMap.of(AVERAGE_MSGS_PER_SEC_INBOUND_METRIC, (Object)averageMsgsPerSecInbound);
        return new NestedMapAccessorImpl(metrics);
    }

    @Override
    public void raiseEvent(Event event) {
        notifySubscribers(event);
    }

    @Override
    public int getCurrentSize() {
        return findContenderNodes(Dmn1NodeType.M, location).size();
    }

    @Override
    public int resize(final int desiredSize) {
        transitionExecutor.execute(new Runnable() {
            public void run() {
                int currentSize = getNetworkInfo().getAllMs().size();
                int desiredDelta = (desiredSize-currentSize);
                DmnFuture<?> future;
                if (desiredDelta > 0) {
                    Collection<NodeId> spares = chooseSparesToGrow(desiredDelta);
                    NodesRolloutConfiguration conf = new NodesRolloutConfiguration.Builder()
                            .nodesToUse(spares)
                            .ms(Math.min(desiredDelta, spares.size()))
                            .build();
                    future = plumber.rolloutNodes(conf);
                } else if (desiredDelta < 0) {
                    Collection<NodeId> mediators = chooseMediatorsToRevert(-desiredDelta);
                    future = plumber.revert(mediators);
                } else {
                    return;
                }
                
                try {
                    future.waitForDone(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw ExceptionUtils.throwRuntime(e);
                }
            }});
        
        return desiredSize;
    }

    @Override
    public Collection<Entity> getBalanceableSubContainers() {
        Collection<Entity> result = new ArrayList<Entity>();
        for (NodeId m : getNetworkInfo().getAllMs()) {
            result.add(new MediatorEntity(getManagementNode(), m));
        }
        return result;
    }
    
    @Override
    public Collection<MoveableEntity> getMovableItems() {
        Collection<MoveableEntity> result = new ArrayList<MoveableEntity>();
        for (String segment : getNetworkInfo().getAllSegments()) {
            result.add(new SegmentEntity(getManagementNode(), segment));
        }
        return result;
    }
    
    @Override
    public Collection<MoveableEntity> getMovableItemsAt(Entity subEntity) {
        Collection<MoveableEntity> result = new ArrayList<MoveableEntity>();
        for (String segment : getNetworkInfo().getSegmentsAtNode(((MediatorEntity)subEntity).getNodeId())) {
            result.add(new SegmentEntity(getManagementNode(), segment));
        }
        return result;
    }
    
    @Override
    public void move(MoveableEntity item, Entity targetContainer) {
        plumber.migrateSegment(((SegmentEntity)item).getSegmentId(), ((MediatorEntity)targetContainer).getNodeId());
    }

    private Collection<NodeId> chooseSparesToGrow(int desiredIncrease) {
        return findContenderNodes(Dmn1NodeType.SPARE, location, desiredIncrease);
    }
    
    private Collection<NodeId> chooseMediatorsToRevert(int desiredDecrease) {
        return findContenderNodes(Dmn1NodeType.M, location, desiredDecrease);
    }
    
    private List<NodeId> findContenderNodes(Dmn1NodeType desiredType, MontereyActiveLocation desiredLoc) {
        List<NodeId> result = new ArrayList<NodeId>();
        Map<NodeId, NodeSummary> contenders = getNetworkInfo().getNodeSummaries();
        for (NodeSummary contender : contenders.values()) {
            if (contender.getType() == desiredType && LocationUtils.containsLocation(desiredLoc, contender.getMontereyActiveLocation())) {
                result.add(contender.getNodeId());
            }
        }
        return result;
    }
    
    private Collection<NodeId> findContenderNodes(Dmn1NodeType desiredType, MontereyActiveLocation desiredLoc, int numDesired) {
        List<NodeId> result = findContenderNodes(desiredType, desiredLoc);
        return result.subList(0, Math.min(result.size(), numDesired));
    }
}
