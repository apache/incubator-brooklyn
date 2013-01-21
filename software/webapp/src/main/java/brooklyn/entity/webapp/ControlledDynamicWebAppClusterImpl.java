package brooklyn.entity.webapp;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.location.Location;
import brooklyn.util.MutableList;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class ControlledDynamicWebAppClusterImpl extends AbstractEntity implements ControlledDynamicWebAppCluster {

    public static final Logger log = LoggerFactory.getLogger(ControlledDynamicWebAppClusterImpl.class);
            
    // TODO convert to use attributes, to support rebind
    private AbstractController controller;
    private ConfigurableEntityFactory<? extends WebAppService> webServerFactory;
    private EntitySpec<? extends WebAppService> webServerSpec;
    private DynamicWebAppCluster cluster;

    public ControlledDynamicWebAppClusterImpl() {
        this(MutableMap.of(), null);
    }
    
    public ControlledDynamicWebAppClusterImpl(Map flags) {
        this(flags, null);
    }
    
    public ControlledDynamicWebAppClusterImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    
    public ControlledDynamicWebAppClusterImpl(Map flags, Entity parent) {
        super(flags, parent);
        setAttribute(SERVICE_UP, false);
    }

    @Override
    public void postConstruct() {
        webServerFactory = getConfig(FACTORY);
        webServerSpec = getConfig(MEMBER_SPEC);
        if (webServerFactory == null && webServerSpec == null) {
            log.debug("creating default web server spec for {}", this);
            webServerSpec = BasicEntitySpec.newInstance(JBoss7Server.class);
        }
        
        log.debug("creating cluster child for {}", this);
        // Note relies on initial_size being inherited by DynamicWebAppCluster, because key id is identical
        Map<String,Object> flags;
        if (webServerSpec != null) {
            flags = MutableMap.<String,Object>of("memberSpec", webServerSpec);
        } else {
            flags = MutableMap.<String,Object>of("factory", webServerFactory);
        }
        cluster = getEntityManager().createEntity(BasicEntitySpec.newInstance(DynamicWebAppCluster.class)
                .parent(this)
                .configure(flags));
        if (Entities.isManaged(this)) Entities.manage(cluster);
        
        controller = getConfig(CONTROLLER);
        if (controller == null) {
            log.debug("creating default controller for {}", this);
            controller = getManagementSupport().getManagementContext(true).getEntityManager().createEntity(BasicEntitySpec.newInstance(NginxController.class)
                    .parent(this));
            if (Entities.isManaged(this)) Entities.manage(controller);
        }
    }
    
    public AbstractController getController() {
        return controller;
    }

    public synchronized ConfigurableEntityFactory<WebAppService> getFactory() {
        return (ConfigurableEntityFactory<WebAppService>) webServerFactory;
    }
    
    // TODO convert to an entity reference which is serializable
    public synchronized DynamicWebAppCluster getCluster() {
        return cluster;
    }
    
    public void start(Collection<? extends Location> locations) {
        if (isLegacyConstruction()) {
            postConstruct();
        }
        
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
