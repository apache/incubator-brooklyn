package brooklyn.entity.webapp;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
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
 * <li>a {@link brooklyn.entity.group.DynamicCluster} of {@link WebAppService}s (defaults to JBoss7Server)
 * <li>a cluster controller (defaulting to Nginx if none supplied)
 * <li>a {@link brooklyn.policy.Policy} to resize the DynamicCluster
 * </ul>
 */
@ImplementedBy(ControlledDynamicWebAppClusterImpl.class)
public interface ControlledDynamicWebAppCluster extends Entity, Startable, Resizable, ElasticJavaWebAppService {

    public static class Spec<T extends ControlledDynamicWebAppCluster, S extends Spec<T,S>> extends BasicEntitySpec<T,S> {

        private static class ConcreteSpec extends Spec<ControlledDynamicWebAppCluster, ConcreteSpec> {
            ConcreteSpec() {
                super(ControlledDynamicWebAppCluster.class);
            }
        }
        
        public static Spec<ControlledDynamicWebAppCluster, ?> newInstance() {
            return new ConcreteSpec();
        }
        
        protected Spec(Class<T> type) {
            super(type);
        }
        
        public S initialSize(int val) {
            configure(INITIAL_SIZE, 1);
            return self();
        }
        
        public S controller(AbstractController val) {
            configure(CONTROLLER, val);
            return self();
        }
        
        public S memberSpec(EntitySpec<? extends WebAppService> val) {
            configure(MEMBER_SPEC, val);
            return self();
        }
        
        public S factory(ConfigurableEntityFactory<? extends WebAppService> val) {
            configure(FACTORY, val);
            return self();
        }
    }
    
    @SetFromFlag("initialSize")
    public static ConfigKey<Integer> INITIAL_SIZE = new BasicConfigKey<Integer>(Cluster.INITIAL_SIZE, 1);

    @SetFromFlag("controller")
    public static BasicAttributeSensorAndConfigKey<AbstractController> CONTROLLER = new BasicAttributeSensorAndConfigKey<AbstractController>(
            AbstractController.class, "controlleddynamicweballcluster.controller", "Controller for the cluster; if null a default will created");

    @SetFromFlag("controllerSpec")
    public static BasicAttributeSensorAndConfigKey<EntitySpec<? extends AbstractController>> CONTROLLER_SPEC = new BasicAttributeSensorAndConfigKey(
            EntitySpec.class, "controlleddynamicweballcluster.controllerSpec", "Spec for creating the cluster (if one not supplied explicitly); if null an NGINX instance will be created");

    /** factory (or closure) to create the web server, given flags */
    @SetFromFlag("factory")
    public static BasicAttributeSensorAndConfigKey<ConfigurableEntityFactory<? extends WebAppService>> FACTORY = new BasicAttributeSensorAndConfigKey(
            ConfigurableEntityFactory.class, DynamicCluster.FACTORY.getName(), "factory (or closure) to create the web server");

    /** Spec for web server entiites to be created */
    @SetFromFlag("memberSpec")
    public static BasicAttributeSensorAndConfigKey<EntitySpec<? extends WebAppService>> MEMBER_SPEC = new BasicAttributeSensorAndConfigKey(
            EntitySpec.class, DynamicCluster.MEMBER_SPEC.getName(), "Spec for web server entiites to be created");

    public static AttributeSensor<DynamicWebAppCluster> CLUSTER = new BasicAttributeSensor<DynamicWebAppCluster>(
            DynamicWebAppCluster.class, "controlleddynamicweballcluster.cluster", "Underlying web-app cluster");

    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;

    public AbstractController getController();
    
    public ConfigurableEntityFactory<WebAppService> getFactory();
    
    public DynamicWebAppCluster getCluster();
}
