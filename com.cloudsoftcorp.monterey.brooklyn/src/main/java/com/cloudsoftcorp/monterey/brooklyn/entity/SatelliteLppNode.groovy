package com.cloudsoftcorp.monterey.brooklyn.entity

import brooklyn.location.Location

import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.node.api.NodeId

public class SatelliteLppNode extends AbstractMontereyNode {

    SatelliteLppNode(MontereyNetworkConnectionDetails connectionDetails, NodeId nodeId, Location location) {
        super(connectionDetails, nodeId, Dmn1NodeType.SATELLITE_BOT);
    }
    
    @Override
    void updateWorkrate(WorkrateReport report) {
    }
}
