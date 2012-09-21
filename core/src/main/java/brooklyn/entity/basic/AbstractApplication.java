package brooklyn.entity.basic;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import brooklyn.config.BrooklynProperties;
import brooklyn.util.MutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.basic.EntityReferences.SelfEntityReference;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.location.Location;
import brooklyn.management.internal.AbstractManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.flags.SetFromFlag;

public abstract class AbstractApplication extends AbstractEntity implements Startable, Application {
    public static final Logger log = LoggerFactory.getLogger(AbstractApplication.class);
    
    @SetFromFlag("mgmt")
    private volatile AbstractManagementContext mgmt;
    
    private boolean deployed = false;

    BrooklynProperties brooklynProperties = null;

    public AbstractApplication(){
        this(new LinkedHashMap());
        log.debug("Using the AbstractApplication no arg constructor will rely on the properties defined in ~/.brooklyn/brooklyn.properties, " +
                       "potentially bypassing explicitly loaded properties");
    }

    public AbstractApplication(Map properties) {
        super(properties);
        this.application = new SelfEntityReference(this);

        if (properties.containsKey("mgmt")) {
            mgmt = (AbstractManagementContext) properties.remove("mgmt");
        }

        if(properties.containsKey("brooklyn.properties")){
            brooklynProperties = (BrooklynProperties) properties.remove("brooklyn.properties");
        }else{
            brooklynProperties = BrooklynProperties.Factory.newDefault();
        }

        setAttribute(SERVICE_UP, false);
    }

    /**
     * Default start will start all Startable children (child.start(Collection<? extends Location>)),
     * calling preStart(locations) first and postStart(locations) afterwards.
     */
    public void start(Collection<? extends Location> locations) {
        this.getLocations().addAll(locations);

        preStart(locations);
        StartableMethods.start(this, locations);
        postStart(locations);

        setAttribute(SERVICE_UP, true);
        deployed = true;

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
        StartableMethods.stop(this);

        synchronized (this) {
            deployed = false;
            //TODO review mgmt destroy lifecycle
            //  we don't necessarily want to forget all about the app on stop, 
            //since operator may be interested in things recently stopped;
            //but that could be handled by the impl at management
            //(keeping recently unmanaged things)  
            //  however unmanaging must be done last, _after_ we stop children and set attributes 
            getManagementContext().unmanage(this);
        }

        log.info("Stopped application " + this);
    }

    public void restart() {
        throw new UnsupportedOperationException();
    }

    public boolean hasManagementContext() {
        return mgmt!=null;
    }
    
    public synchronized void setManagementContext(AbstractManagementContext mgmt) {
        if (mgmt!=null && mgmt.equals(this.mgmt))
            return;
        if (hasManagementContext() && mgmt!=null) {
            // TODO it is too easy to accidentally create an automatic local mgmt context
            // e.g. by emitting a sensor in the constructor ... as AbstractApplication does !!
            // (only affects us in testing, but still)
            throw new IllegalStateException("Cannot set management context on "+this+" to "+mgmt+" as it already has a management context "+this.mgmt+"; " +
            		"if unwanted auto-management is occurring try passing mgmt flag to application creation");
        }
        if (isDeployed()) {
            // do we have to be so strict about this? it is a weird case, but still...
            throw new IllegalStateException("Cannot set management context on "+this+" to "+mgmt+" as it is already deployed");
        }
        
        this.mgmt = mgmt;
        if (isDeployed()) {
            mgmt.manage(this);            
        }
    }
    
    @Override
    public synchronized AbstractManagementContext getManagementContext() {
        if (hasManagementContext())
            return mgmt;

        LocalManagementContext newMgmt = new LocalManagementContext(brooklynProperties);
        if (log.isDebugEnabled()) {
            log.debug("creating new local management context for "+this+" ("+newMgmt+") automatically on attempt to access context");
            if (log.isTraceEnabled())
                log.trace("trace for local mgmt creation of "+this, new Throwable("trace for local mgmt creation of "+this));
        }
        
        setManagementContext(newMgmt);
        return mgmt;
    }

    public boolean isDeployed() {
        return deployed;
    }
}
