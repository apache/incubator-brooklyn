package com.cloudsoftcorp.monterey.brooklyn.entity

import java.util.logging.Logger

import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location

import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.api.NodeSummary
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.cloudsoftcorp.util.Loggers
import com.google.common.base.Preconditions

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

    private static final Logger LOG = Loggers.getLogger(AbstractMontereyNode.class);
    
    public static final BasicAttributeSensor<Integer> WORKRATE_MSGS_PER_SEC = [ Double, "monterey.workrate.msgsPerSec", "Messages per sec" ]
    public static final BasicAttributeSensor<Dmn1NodeType> NODE_TYPE = [ Dmn1NodeType.class, "monterey.node-type", "Node type" ]
    
    protected final MontereyNetworkConnectionDetails connectionDetails;
    private final NodeId nodeId;
    private final Dmn1NodeType nodeType;
    
    AbstractMontereyNode(MontereyNetworkConnectionDetails connectionDetails, NodeId nodeId, Dmn1NodeType nodeType, Location loc) {
        this.connectionDetails = Preconditions.checkNotNull(connectionDetails, "connectionDetails")
        this.nodeId = nodeId = Preconditions.checkNotNull(nodeId, "nodeId")
        this.nodeType = nodeType = Preconditions.checkNotNull(nodeType, "nodeType")
        locations.add(Preconditions.checkNotNull(loc, "loc"))
        
        LOG.info("Monterey network node $nodeId in $loc of type $nodeType");
    }
    
    @Override
    public String getDisplayName() {
        return nodeType ? nodeType.getLongLabel()+"("+nodeId+")" : super.getDisplayName()
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public Dmn1NodeType getNodeType() {
        return nodeType;
    }
    
    void updateTopology(NodeSummary summary, Collection<NodeId> downstreamNodes) {
        // default is no-op; can be over-ridden
    }
        
    abstract void updateWorkrate(WorkrateReport report);
    
    protected void dispose() {
    }
}
