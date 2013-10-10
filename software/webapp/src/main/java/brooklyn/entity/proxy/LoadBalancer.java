package brooklyn.entity.proxy;

import java.util.Map;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.BasicAttributeSensor;
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
public interface LoadBalancer extends Entity, Startable {

    @SetFromFlag("serverPool")
    ConfigKey<Group> SERVER_POOL = new BasicConfigKey<Group>(
            Group.class, "loadbalancer.serverpool", "The default servers to route messages to");

    @SetFromFlag("urlMappings")
    ConfigKey<Group> URL_MAPPINGS = new BasicConfigKey<Group>(
            Group.class, "loadbalancer.urlmappings", "Special mapping rules (e.g. for domain/path matching, rewrite, etc); not supported by all load balancers");
    
    public static final BasicAttributeSensor<Set<String>> SERVER_POOL_TARGETS = new BasicAttributeSensor(
            Set.class, "proxy.serverpool.targets", "The downstream targets in the server pool");
    
    /**
     * @deprecated since 0.6; Use SERVER_POOL_TARGETS
     */
    public static final BasicAttributeSensor<Set<String>> TARGETS = SERVER_POOL_TARGETS;
    
    public static final MethodEffector<Void> RELOAD = new MethodEffector<Void>(LoadBalancer.class, "reload");
    
    public static final MethodEffector<Void> UPDATE = new MethodEffector<Void>(LoadBalancer.class, "update");

    @Effector(description="Forces reload of the configuration")
    public void reload();

    @Effector(description="Updates the entities configuration, and then forces reload of that configuration")
    public void update();
    
    /**
     * Opportunity to do late-binding of the cluster that is being controlled. Must be called before start().
     * Can pass in the 'serverPool'.
     */
    public void bind(Map flags);
}
