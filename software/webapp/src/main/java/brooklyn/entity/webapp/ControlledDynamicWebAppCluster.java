package brooklyn.entity.webapp;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxy.nginx.NginxControllerImpl;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.util.MutableList;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

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
            
    @SetFromFlag("initialSize")
    public static ConfigKey<Integer> INITIAL_SIZE = new BasicConfigKey<Integer>(Cluster.INITIAL_SIZE, 1);

    @SetFromFlag("controller")
    AbstractController _controller;

    /** factory (or closure) to create the web server, given flags */
    @SetFromFlag("factory")
    ConfigurableEntityFactory<WebAppService> _webServerFactory;

    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;

    // TODO convert to use attributes, to support rebind
    private AbstractController cachedController;
    private EntityFactory<? extends WebAppService> cachedWebServerFactory;
    private DynamicWebAppCluster cachedCluster;

    public ControlledDynamicWebAppCluster() {
        this(MutableMap.of(), null);
    }
    
    public ControlledDynamicWebAppCluster(Map flags) {
        this(flags, null);
    }
    
    public ControlledDynamicWebAppCluster(Entity parent) {
        this(MutableMap.of(), parent);
    }
    
    public ControlledDynamicWebAppCluster(Map flags, Entity parent) {
        super(flags, parent);
        setAttribute(SERVICE_UP, false);
    }

    public synchronized AbstractController getController() {
        if (cachedController!=null) return cachedController;
        cachedController = _controller;
        if (cachedController!=null) return cachedController;
        cachedController = (AbstractController) findChildOrNull(Predicates.instanceOf(AbstractController.class));
        if (cachedController!=null) return cachedController;
        log.debug("creating default controller for {}", this);
        cachedController = new NginxControllerImpl(this);
        Entities.manage(cachedController);
        return cachedController;
    }
    
    public synchronized EntityFactory<WebAppService> getFactory() {
        if (cachedWebServerFactory!=null) return (EntityFactory<WebAppService>) cachedWebServerFactory;
        cachedWebServerFactory = _webServerFactory;
        if (cachedWebServerFactory!=null) return (EntityFactory<WebAppService>) cachedWebServerFactory;
        log.debug("creating default web server factory for {}", this);
        cachedWebServerFactory = new JBoss7ServerFactory();
        return (EntityFactory<WebAppService>) cachedWebServerFactory;
    }
    
    // TODO convert to an entity reference which is serializable
    public synchronized DynamicWebAppCluster getCluster() {
        if (cachedCluster!=null) return cachedCluster;
        cachedCluster = (DynamicWebAppCluster) findChildOrNull(Predicates.instanceOf(DynamicWebAppCluster.class));
        if (cachedCluster!=null) return cachedCluster;
        log.debug("creating cluster child for {}", this);
        // Note relies on initial_size being inherited by DynamicWebAppCluster, because key id is identical
        cachedCluster = new DynamicWebAppClusterImpl(
                MutableMap.builder()
                        .put("factory", getFactory())
                        .build(),
                this);
        if (Entities.isManaged(this)) Entities.manage(cachedCluster);
        return cachedCluster;
    }
    
    public void start(Collection<? extends Location> locations) {
        if (locations.isEmpty()) locations = this.getLocations();
        Iterables.getOnlyElement(locations); //assert just one
        addLocations(locations);
        
        getController().bind(MutableMap.of("serverPool", getCluster()));

        List<Entity> childrenToStart = MutableList.<Entity>of(getCluster());
        // Set controller as child of cluster, if it does not already have a parent
        if (getController().getParent() == null) {
            addChild(getController());
        }
        // And only start controller if we are parent
        if (this.equals(getController().getParent())) childrenToStart.add(getController());
        try {
            Entities.invokeEffectorList(this, childrenToStart, Startable.START, ImmutableMap.of("locations", locations)).get();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } catch (ExecutionException e) {
            throw Exceptions.propagate(e);
        }
        
        connectSensors();
    }
    
    public void stop() {
        if (this.equals(getController().getParent())) {
            getController().stop();
        }
        getCluster().stop();

        super.getLocations().clear();
        setAttribute(SERVICE_UP, false);
    }

    public void restart() {
        // TODO prod the entities themselves to restart, instead?
        Collection<Location> locations = Lists.newArrayList(getLocations());

        stop();
        start(locations);
    }
    
    void connectSensors() {
        SensorPropagatingEnricher.newInstanceListeningToAllSensorsBut(getCluster(), SERVICE_UP, ROOT_URL).
            addToEntityAndEmitAll(this);
        SensorPropagatingEnricher.newInstanceListeningTo(getCluster(), AbstractController.HOSTNAME, SERVICE_UP, ROOT_URL).
            addToEntityAndEmitAll(this);
    }

    public Integer resize(Integer desiredSize) {
        return getCluster().resize(desiredSize);
    }

    /**
     * @return the current size of the group.
     */
    public Integer getCurrentSize() {
        return getCluster().getCurrentSize();
    }

    private Entity findChildOrNull(Predicate<? super Entity> predicate) {
        for (Entity contender : getChildren()) {
            if (predicate.apply(contender)) return contender;
        }
        return null;
    }
}
