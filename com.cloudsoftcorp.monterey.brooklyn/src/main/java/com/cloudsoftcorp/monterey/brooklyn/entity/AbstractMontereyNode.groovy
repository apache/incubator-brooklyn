package com.cloudsoftcorp.monterey.brooklyn.entity

import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.basic.BasicAttributeSensor

import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.node.api.NodeId

/**
 * Represents a network-node (e.g. LPP, MR, M, TP, or Spare). 
 * 
 * When reverting, this entity will stop existing; when a node is rolled out, a new 
 * AbstractMontereyNode entity will come into existance.
 *  
 * This is contained by a MontereyContainerNode.
 * 
 * @author aled
 */
abstract class AbstractMontereyNode extends AbstractEntity {

    public static final BasicAttributeSensor<Integer> WORKRATE_MSGS_PER_SEC = [ "MsgsPerSec", "monterey.workrate.msgsPerSec", Double ]
    public static final BasicAttributeSensor<Dmn1NodeType> NODE_TYPE = [ "MsgsPerSec", "monterey.node-type", Dmn1NodeType.class ]
    
    private final MontereyNetworkConnectionDetails connectionDetails;
    private final NodeId nodeId;
    private final Dmn1NodeType nodeType;
    
    AbstractMontereyNode(MontereyNetworkConnectionDetails connectionDetails, NodeId nodeId, Dmn1NodeType nodeType) {
        this.connectionDetails = connectionDetails;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
    }
    
    public NodeId getNodeId() {
        return nodeId;
    }

    public Dmn1NodeType getNodeType() {
        return nodeType;
    }
    
    abstract void updateWorkrate(WorkrateReport report);
}
