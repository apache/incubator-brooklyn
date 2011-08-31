package com.cloudsoftcorp.monterey.brooklyn.entity

import brooklyn.event.basic.BasicAttributeSensor;

import com.cloudsoftcorp.monterey.node.api.NodeId;

class MontereyAttributes {

    public static final BasicAttributeSensor<NodeId> DOWNSTREAM_ROUTER = [ NodeId, "monterey.node.downstreamRouter", "Downstream router id" ]
    
    public static final BasicAttributeSensor<Integer> WORKRATE_MSGS_PER_SEC = [ Double, "monterey.workrate.msgsPerSec", "Messages per sec" ]
    
}
