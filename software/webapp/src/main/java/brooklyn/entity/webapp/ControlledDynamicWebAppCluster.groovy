package brooklyn.entity.webapp

import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.enricher.basic.SensorPropagatingEnricher
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.ConfigurableEntityFactory
import brooklyn.entity.group.AbstractController
import brooklyn.entity.group.Cluster
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.trait.Resizable
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.util.flags.SetFromFlag

import com.google.common.collect.Iterables

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
public class ControlledDynamicWebAppCluster extends AbstractEntity implements Startable, Resizable, ElasticJavaWebAppService {

    public static final Logger log = LoggerFactory.getLogger(ControlledDynamicWebAppCluster.class);
            
    @SetFromFlag('initialSize')
    public static BasicConfigKey<Integer> INITIAL_SIZE = [ Cluster.INITIAL_SIZE, 1 ]

    @SetFromFlag("controller")
    AbstractController _controller;

    /** closure to create the web server, given flags */
    @SetFromFlag("factory")
    ConfigurableEntityFactory<WebAppService> _webServerFactory;

    public ControlledDynamicWebAppCluster(Entity owner) { this([:], owner) }
    public ControlledDynamicWebAppCluster(Map flags = [:], Entity owner = null) {
        super(flags, owner)
        setAttribute(SERVICE_UP, false)
    }

    // TODO convert to an entity reference which is serializable
    transient private AbstractController cachedController;
    public synchronized AbstractController getController() {
        if (cachedController!=null) return cachedController;
        cachedController = _controller;
        if (cachedController!=null) return cachedController;
        cachedController = getOwnedChildren().find { it in AbstractController }
        if (cachedController!=null) return cachedController;
        log.debug("creating default controller for {}", this);
        cachedController = new NginxController(this);
    }
    
    private ConfigurableEntityFactory<WebAppService> cachedWebServerFactory;
    public synchronized ConfigurableEntityFactory<WebAppService> getFactory() {
        if (cachedWebServerFactory!=null) return cachedWebServerFactory;
        cachedWebServerFactory = _webServerFactory;
        if (cachedWebServerFactory!=null) return cachedWebServerFactory;
        log.debug("creating default web server factory for {}", this);
        cachedWebServerFactory = new JBoss7ServerFactory();
    }
    
    // TODO convert to an entity reference which is serializable
    transient private DynamicWebAppCluster cachedCluster;
    public synchronized DynamicWebAppCluster getCluster() {
        if (cachedCluster!=null) return cachedCluster;
        cachedCluster = getOwnedChildren().find { it in DynamicWebAppCluster }
        if (cachedCluster!=null) return cachedCluster;
        log.debug("creating cluster child for {}", this);
        cachedCluster = new DynamicWebAppCluster(this,
            factory: factory,
            initialSize: { getConfig(INITIAL_SIZE) });
    }
    
    public void start(Collection<? extends Location> locations) {
        Iterables.getOnlyElement(locations) //assert just one
        
        addOwnedChild(controller)

        this.locations.addAll(locations)
        cluster.start(locations)

        controller.bind(cluster:cluster)
        controller.start(locations)
        
        connectSensors();

        setAttribute(SERVICE_UP, true)
    }
    
    public void stop() {
        controller.stop()
        cluster.stop()

        locations.clear();
        setAttribute(SERVICE_UP, false)
    }

    public void restart() {
        // TODO prod the entities themselves to restart, instead?
        def locations = []
        locations.addAll(this.locations)

        stop();
        start(locations);
    }

    void connectSensors() {
        String url = "http://"+controller.getAttribute(AbstractController.HOSTNAME)+":"+
            controller.getAttribute(AbstractController.HTTP_PORT)+"/";
        setAttribute(ROOT_URL, url)
        
        def prop = SensorPropagatingEnricher.newInstanceListeningToAllSensorsBut(cluster, SERVICE_UP, ROOT_URL)
        addEnricher(prop);
        prop.emitAllAttributes();
    }

    public Integer resize(Integer desiredSize) {
        cluster.resize(desiredSize);
    }

    /**
     * @return the current size of the group.
     */
    public Integer getCurrentSize() {
        cluster.getCurrentSize();
    }

}
