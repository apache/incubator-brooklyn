package com.cloudsoftcorp.monterey.brooklyn.entity

import brooklyn.entity.basic.AbstractEntity
import brooklyn.location.Location
import brooklyn.util.task.ParallelTask

class MontereyProvisioner extends AbstractEntity {

    // FIXME This class should track spare nodes, and re-use them where possible.
    // It should also allow policies to control the choice of location etc, and whether we
    // are even allowed to provision another node.
    
    private final MontereyNetworkConnectionDetails connectionDetails;
    private final MontereyNetwork network;
    
    MontereyProvisioner(MontereyNetworkConnectionDetails connectionDetails, MontereyNetwork network) {
        this.connectionDetails = connectionDetails
        this.network = network
    }
    
    public void provisionNodes(int num) {
        MontereyNetwork mn = getMontereyNetwork()
        for (i in 0..num) {
            MontereyContainerNode node = mn.provisionNode(locations)
            node.rollout(nodeType)
        }
    }

    public Collection<MontereyContainerNode> requestNodes(Collection<Location> locs, int num) {
        return requestNodes(locs.iterator().next(), num)
    }
    
    public Collection<MontereyContainerNode> requestNodes(Location loc, int num) {
        Collection<MontereyContainerNode> result = []
        ParallelTask task = new ParallelTask( {
                    MontereyContainerNode node = network.provisionNode(loc)
                    result.add(node)
                })
        executionContext.submit(task)
        
        for (i in 0..num) {
            // TODO Parallelise starting
            MontereyContainerNode node = network.provisionNode(loc)
            result.add(node)
        }
        return result;
    }
    
    public void releaseNode(MontereyContainerNode node) {
        network.releaseNode(node)
    }
}
