package brooklyn.entity.webapp;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;


/**
 * This entity contains the sub-groups and entities that go in to a single location (e.g. datacenter)
 * to provide web-app cluster functionality, viz load-balancer (controller) and webapp software processes.
 * <p>
 * You can customise the web server by customising
 * the factory (by reference in calling code)
 * or supplying your own factory (as a config flag).
 * <p>
 * The contents of this group entity are:
 * <ul>
 * <li>a {@link brooklyn.entity.group.DynamicCluster} of {@link JavaWebAppService}s (defaults to JBoss7Server)
 * <li>a cluster controller (defaulting to Nginx if none supplied)
 * <li>a {@link brooklyn.policy.Policy} to resize the DynamicCluster
 * </ul>
 */
@ImplementedBy(ControlledDynamicWebAppClusterImpl.class)
public interface ControlledDynamicWebAppCluster extends Entity, Startable, Resizable, ElasticJavaWebAppService {

    @SetFromFlag("initialSize")
    public static ConfigKey<Integer> INITIAL_SIZE = new BasicConfigKey<Integer>(Cluster.INITIAL_SIZE, 1);

    @SetFromFlag("controller")
    public static ConfigKey<AbstractController> CONTROLLER = new BasicConfigKey<AbstractController>(
            AbstractController.class, "controlleddynamicweballcluster.controller", "Controller for the cluster; if null a default will created");

    /** factory (or closure) to create the web server, given flags */
    @SetFromFlag("factory")
    public static ConfigKey<ConfigurableEntityFactory<WebAppService>> FACTORY = new BasicConfigKey(
            ConfigurableEntityFactory.class, "controlleddynamicweballcluster.factory", "factory (or closure) to create the web server");

    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;

    public AbstractController getController();
    
    public ConfigurableEntityFactory<WebAppService> getFactory();
    
    public DynamicWebAppCluster getCluster();
}
