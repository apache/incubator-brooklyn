package brooklyn.entity.proxy;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;

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
@ImplementedBy(LoadBalancerClusterImpl.class)
public interface LoadBalancerCluster extends DynamicCluster, LoadBalancer {
}
