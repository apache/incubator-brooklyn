package com.cloudsoftcorp.monterey.brooklyn.entity

import java.util.Collection
import java.util.logging.Logger

import brooklyn.entity.basic.AbstractGroup
import brooklyn.location.Location

import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.api.NodeSummary
import com.cloudsoftcorp.monterey.network.control.plane.GsonSerializer
import com.cloudsoftcorp.monterey.network.control.plane.web.PlumberWebProxy
import com.cloudsoftcorp.monterey.network.control.wipapi.DmnFuture
import com.cloudsoftcorp.monterey.network.control.wipapi.NodesRolloutConfiguration
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.cloudsoftcorp.util.Loggers
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.google.gson.Gson

/**
 * Represents a "proto node", i.e. a container that can host a LPP, MR, M, TP. 
 * 
 * When reverting/rolling out, the same MontereyContainerNode instance exists throughput. 
 * 
 * @aled
 */
public class MontereyContainerNode extends AbstractGroup {

    // TODO Would be great if we supported a "container", aka protonode, being able to host M,TP,etc

    private static final Logger LOG = Loggers.getLogger(MontereyContainerNode.class);
        
    private final MontereyNetworkConnectionDetails connectionDetails;
    private final NodeId nodeId;
    private final Location location;
    
    private AbstractMontereyNode node;
    
    private final Gson gson;
    
    MontereyContainerNode(MontereyNetworkConnectionDetails connectionDetails, NodeId nodeId, Location location) {
        this.connectionDetails = connectionDetails;
        this.nodeId = nodeId;
        this.location = location;
        
        ClassLoadingContext classloadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext();
        GsonSerializer gsonSerializer = new GsonSerializer(classloadingContext);
        gson = gsonSerializer.getGson();

        LOG.info("Node "+nodeId+" created in location "+location);        
    }
    
    public NodeId getNodeId() {
        return nodeId;
    }
    
    public AbstractMontereyNode getContainedMontereyNode() {
        return node;
    }

    public void rollout(Dmn1NodeType type) {
        PlumberWebProxy plumber = new PlumberWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        DmnFuture<Collection<NodeId>> future = plumber.rolloutNodes(new NodesRolloutConfiguration.Builder()
                .nodesToUse(Collections.singleton(nodeId))
                .ofType(type, 1)
                .build());
    }
    
    public void revert() {
        PlumberWebProxy plumber = new PlumberWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        DmnFuture<?> future = plumber.revert(nodeId);
    }
    
    void updateContents(NodeSummary nodeSummary) {
        if (nodeSummary.getType() == node?.getNodeType()) {
            // already has correct type; nothing to do
            return;
        }
        
        if (node != null) {
            node.dispose();
        }
        
        switch (nodeSummary.getType()) {
            case Dmn1NodeType.M:
                node = new MediatorNode(connectionDetails, nodeId, location);
                break;
            case Dmn1NodeType.SPARE:
                node = new SpareNode(connectionDetails, nodeId, location);
                break;
            case Dmn1NodeType.LPP:
                node = new LppNode(connectionDetails, nodeId, location);
                break;
            case Dmn1NodeType.MR:
                node = new MrNode(connectionDetails, nodeId, location);
                break;
            case Dmn1NodeType.TP:
                node = new TpNode(connectionDetails, nodeId, location);
                break;
            case Dmn1NodeType.SATELLITE_BOT:
                node = new SatelliteLppNode(connectionDetails, nodeId, location);
                break;
            case Dmn1NodeType.CHANGING:
                // no-op; will change type again shortly
                // TODO How to handle "changing"? Should we have no child until it changes?
                break;
            default: 
                throw new IllegalStateException("Cannot create entity for mediator node type "+nodeSummary.getType()+" at "+nodeId);
        }

        if (node != null) {
            addOwnedChild(node)
        }
        
        LOG.info("Node "+nodeId+" changed type to "+nodeSummary.getType());        
    }   
    
    void updateWorkrate(WorkrateReport report) {
        node?.updateWorkrate(report)
    }
}
