package brooklyn.entity.proxy;

import java.util.Collection;

import brooklyn.entity.Entity;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.location.Location;

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
public class LoadBalancerClusterImpl extends DynamicClusterImpl implements LoadBalancerCluster {

    // TODO I suspect there are races with reconfiguring the load-balancers while
    // the cluster is growing: there is no synchronization around the calls to reload
    // and the resize, so presumably there's a race where a newly added load-balancer 
    // could miss the most recent reload call?

    public LoadBalancerClusterImpl() {
        super();
    }

    @Override
    public void start(Collection<? extends Location> locs) {
        super.start(locs);
        
        // TODO Is there a race here, where (dispite super.stop() calling policy.suspend),
        // this could still be executing setAttribute(true) and hence incorrectly leave
        // the cluster in a service_up==true state after stop() returns?
        AbstractMembershipTrackingPolicy policy = new AbstractMembershipTrackingPolicy() {
            @Override protected void onEntityChange(Entity member) {
                setAttribute(SERVICE_UP, calculateServiceUp());
            }
            @Override protected void onEntityAdded(Entity member) {
                setAttribute(SERVICE_UP, calculateServiceUp());
            }
            @Override protected void onEntityRemoved(Entity member) {
                setAttribute(SERVICE_UP, calculateServiceUp());
            }
        };
        addPolicy(policy);
        policy.setGroup(this);
    }

    /**
     * Up if running and has at least one load-balancer in the cluster.
     * 
     * TODO Could also look at service_up of each load-balancer, but currently does not do that.
     */
    @Override
    protected boolean calculateServiceUp() {
        return super.calculateServiceUp() && getCurrentSize() > 0;
    }
    
}
