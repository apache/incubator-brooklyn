package brooklyn.entity.proxy;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicCluster;

/**
 * A cluster of load balancers, where configuring the cluster (through the LoadBalancer interface)
 * will configure all load balancers in the cluster.
 * 
 * Config keys (such as LoadBalancer.serverPool and LoadBalancer.urlMappings) are automatically
 * inherited by the children of the load balancer cluster. It is through that mechanism that
 * configuration changes on the cluster will be applied to all child load balancers (i.e. by
 * them all sharing the same serverPool and urlMappings etc).
 *  
 * @author aled
 */
public class LoadBalancerCluster extends DynamicCluster implements LoadBalancer {

    // TODO I suspect there are races with reconfiguring the load-balancers while
    // the cluster is growing: there is no synchronization around the calls to reload
    // and the resize, so presumably there's a race where a newly added load-balancer 
    // could miss the most recent reload call?
    
    public LoadBalancerCluster(Map<?, ?> flags, Entity owner) {
        super(flags, owner);
    }
}
