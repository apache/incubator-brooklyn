package brooklyn.entity.basic;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.management.internal.ManagementContextInternal;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

/**
 * Users can extend this to define the entities in their application, and the relationships between
 * those entities. Users should override the {@link #init()} method, and in there should create 
 * their entities.
 */
public abstract class AbstractApplication extends AbstractEntity implements StartableApplication {
    public static final Logger log = LoggerFactory.getLogger(AbstractApplication.class);
    
    public static final String APPLICATION_USAGE_KEY = "application-usage";
    
    @SetFromFlag("mgmt")
    private volatile ManagementContext mgmt;
    
    private boolean deployed = false;

    BrooklynProperties brooklynProperties = null;

    private volatile Application application;
    
    public AbstractApplication() {
    }

    /**
     * 
     * @deprecated since 0.6; use EntitySpec so no-arg constructor
     */
    @Deprecated
    public AbstractApplication(Map properties) {
        super(properties);
    }

    /** 
     * Constructor for when application is nested inside another application
     * 
     * @deprecated Nesting applications is not currently supported
     */
    @Deprecated
    public AbstractApplication(Map properties, Entity parent) {
        super(properties, parent);
    }

    @Override
    public void init() {
        log.warn("Deprecated: AbstractApplication.init() will be declared abstract in a future release; please override for code instantiating child entities");
    }

    @Override
    public Application getApplication() {
        if (application!=null) {
            if (application.getId().equals(getId()))
                return (Application) getProxyIfAvailable();
            return application;
        }
        if (getParent()==null) return (Application)getProxyIfAvailable();
        return getParent().getApplication();
    }
    
    @Override
    protected synchronized void setApplication(Application app) {
        if (app.getId().equals(getId())) {
            application = getProxy()!=null ? (Application)getProxy() : app;
        } else {
            application = app;

            // Alex, Mar 2013: added some checks; 
            // i *think* these conditions should not happen, 
            // and so should throw but don't want to break things (yet)
            if (getParent()==null) {
                log.warn("Setting application of "+this+" to "+app+", but "+this+" is not parented");
            } else if (getParent().getApplicationId().equals(app.getParent())) {
                log.warn("Setting application of "+this+" to "+app+", but parent "+getParent()+" has different app "+getParent().getApplication());
            }
        }
        super.setApplication(app);
    }
    
    public AbstractApplication setParent(Entity parent) {
        super.setParent(parent);
        return this;
    }
    
    /**
     * Default start will start all Startable children (child.start(Collection<? extends Location>)),
     * calling preStart(locations) first and postStart(locations) afterwards.
     */
    public void start(Collection<? extends Location> locations) {
        this.addLocations(locations);

        setAttribute(Attributes.SERVICE_STATE, Lifecycle.STARTING);
        try {
            preStart(locations);
            StartableMethods.start(this, locations);
            postStart(locations);
        } catch (Exception e) {
            setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
            // no need to log here; the effector invocation should do that
            throw Exceptions.propagate(e);
        }

        setAttribute(SERVICE_UP, true);
        setAttribute(Attributes.SERVICE_STATE, Lifecycle.RUNNING);
        deployed = true;

        putApplicationEvent(Lifecycle.RUNNING);

        log.info("Started application " + this);
    }

    /**
     * Default is no-op. Subclasses can override.
     * */
    public void preStart(Collection<? extends Location> locations) {
        //no-op
    }

    /**
     * Default is no-op. Subclasses can override.
     * */
    public void postStart(Collection<? extends Location> locations) {
        //no-op
    }

    /**
     * Default stop will stop all Startable children
     */
    public void stop() {
        log.info("Stopping application " + this);

        setAttribute(SERVICE_UP, false);
        setAttribute(Attributes.SERVICE_STATE, Lifecycle.STOPPING);
        try {
            StartableMethods.stop(this);
        } catch (Exception e) {
            setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
            log.warn("Error stopping application " + this + " (rethrowing): "+e);
            throw Exceptions.propagate(e);
        }
        setAttribute(Attributes.SERVICE_STATE, Lifecycle.STOPPED);

        putApplicationEvent(Lifecycle.DESTROYED);

        synchronized (this) {
            deployed = false;
            //TODO review mgmt destroy lifecycle
            //  we don't necessarily want to forget all about the app on stop, 
            //since operator may be interested in things recently stopped;
            //but that could be handled by the impl at management
            //(keeping recently unmanaged things)  
            //  however unmanaging must be done last, _after_ we stop children and set attributes 
            getEntityManager().unmanage(this);
        }

        log.info("Stopped application " + this);
    }

    public void restart() {
        throw new UnsupportedOperationException();
    }

    private void putApplicationEvent(Lifecycle state) {
        log.debug("Location lifecycle event: application {} in state {};", new Object[] {this, state});
        BrooklynStorage storage = ((ManagementContextInternal) getManagementContext()).getStorage();
        ConcurrentMap<String, ApplicationUsage> eventMap = storage.getMap(APPLICATION_USAGE_KEY);
        ApplicationUsage usage = eventMap.get(getId());
        if (usage == null) {
            usage = new ApplicationUsage(getId(), getDisplayName(), getEntityTypeName(), toMetadataRecord());
        }
        usage.addEvent(new ApplicationUsage.ApplicationEvent(state));        
        eventMap.put(getId(), usage);
    }
    
    public Map<String, String> toMetadataRecord() {
        return ImmutableMap.of();
    }

}
