package brooklyn.entity.proxy;

import brooklyn.entity.Group;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * A load balancer that routes requests to set(s) of servers.
 * 
 * There is an optional "serverPool" that will have requests routed to it (e.g. as round-robin). 
 * This is a group whose members are appropriate servers; membership of that group will be tracked 
 * to automatically update the load balancer's configuration as appropriate.
 * 
 * There is an optional urlMappings group for defining additional mapping rules. Members of this
 * group (of type UrlMapping) will be tracked, to automatically update the load balancer's configuration.
 * The UrlMappings can give custom routing rules so that specific urls are routed (and potentially re-written)
 * to particular sets of servers. 
 * 
 * @author aled
 */
public interface LoadBalancer {

    @SetFromFlag("serverPool")
    public static final BasicConfigKey<Group> SERVER_POOL = new BasicConfigKey<Group>(
            Group.class, "loadbalancer.serverpool", "The default servers to route messages to");

    @SetFromFlag("urlMappings")
    public static final BasicConfigKey<Group> URL_MAPPINGS = new BasicConfigKey<Group>(
            Group.class, "loadbalancer.urlmappings", "Special mapping rules (e.g. for domain/path matching, rewrite, etc)");
}
