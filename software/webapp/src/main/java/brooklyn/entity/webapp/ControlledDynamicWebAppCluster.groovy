package brooklyn.entity.webapp

import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.enricher.basic.SensorPropagatingEnricher
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.ConfigurableEntityFactory
import brooklyn.entity.basic.Entities
import brooklyn.entity.group.AbstractController
import brooklyn.entity.group.Cluster
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.trait.Resizable
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory
import brooklyn.event.Sensor
import brooklyn.event.SensorEventListener
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

    /** factory (or closure) to create the web server, given flags */
    @SetFromFlag("factory")
    ConfigurableEntityFactory<WebAppService> _webServerFactory;

    public static final Sensor HOSTNAME = Attributes.HOSTNAME;
    
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
        Iterables.getOnlyElement(locations); //assert just one
        
        addOwnedChild(controller);
        this.locations.addAll(locations);
        controller.bind(cluster:cluster);
        Entities.invokeEffectorList(this, [cluster, controller], Startable.START, [locations:locations]).get();
        
        connectSensors();
        setAttribute(SERVICE_UP, true);
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

    void updateHostnameFromController() {
        String url = controller.getAttribute(NginxController.ROOT_URL);
        if (url==null) url = controller.getAttribute(AbstractController.SPECIFIED_URL);
        if (url==null || url.contains("://"+AbstractController.ANONYMOUS+":") || url.contains("://"+AbstractController.ANONYMOUS+"/")) {
            //probably isn't necessary, as is done in Nginx?
            String hostname = controller.getAttribute(HOSTNAME);
            Object port = controller.getAttribute(AbstractController.PROXY_HTTP_PORT);
            if (hostname==null || port==null) return;
            url = "http://"+hostname+":"+port+"/";
            LOG.warn("Building URL for $this from $controller: $url");
        }
        setAttribute(ROOT_URL, url);
    }
    
    void connectSensors() {
        SensorPropagatingEnricher.newInstanceListeningToAllSensorsBut(cluster, SERVICE_UP, ROOT_URL).
            addToEntityAndEmitAll(this);
        
        //following 3 lines (and updateHostname method) unnecessary if above is working, I think
        controller.subscribe(controller, NginxController.ROOT_URL, { updateHostnameFromController() } as SensorEventListener);
        controller.subscribe(controller, NginxController.SPECIFIED_URL, { updateHostnameFromController() } as SensorEventListener);
        controller.subscribe(controller, AbstractController.HOSTNAME, { updateHostnameFromController() } as SensorEventListener);
        updateHostnameFromController();
        
        SensorPropagatingEnricher.newInstanceListeningTo(controller, AbstractController.HOSTNAME, SERVICE_UP).
        addToEntityAndEmitAll(this);
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
