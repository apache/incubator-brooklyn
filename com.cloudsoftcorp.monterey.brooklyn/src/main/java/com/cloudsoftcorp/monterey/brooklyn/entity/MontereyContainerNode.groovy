package com.cloudsoftcorp.monterey.brooklyn.entity

import brooklyn.entity.basic.AbstractGroup
import brooklyn.location.Location

import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport
import com.cloudsoftcorp.monterey.network.control.api.NodeSummary
import com.cloudsoftcorp.monterey.node.api.NodeId

/**
 * Represents a "proto node", i.e. a container that can host a LPP, MR, M, TP. 
 * 
 * When reverting/rolling out, the same MontereyContainerNode instance exists throughput. 
 * 
 * @aled
 */
public class MontereyContainerNode extends AbstractGroup {

    // TODO Would be great if we supported a "container", aka protonode, being able to host M,TP,etc
    
    private final MontereyNetworkConnectionDetails connectionDetails;
    private final NodeId nodeId;
    
    private AbstractMontereyNode node;
    
    MontereyContainerNode(MontereyNetworkConnectionDetails connectionDetails, NodeId nodeId, Location location) {
        this.connectionDetails = connectionDetails;
        this.nodeId = nodeId;
    }
    
    public NodeId getNodeId() {
        return nodeId;
    }
    
    public Collection<AbstractMontereyNode> getContainedMontereyNodes() {
        return nodes;
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
            case M:
                node = new MediatorNode(connectionDetails, nodeId);
                break;
            case LPP:
            case MR:
            case TP:
            case SPARE:
                throw new UnsupportedOperationException("Work-in-progress, type="+nodeSummary.getType());
            default: 
                throw new IllegalStateException("Cannot create entity for mediator node type "+nodeSummary.getType()+" at "+nodeId);
        }
    }   
     
    void updateWorkrate(WorkrateReport report) {
        node?.updateWorkrate(report)
    }
}
