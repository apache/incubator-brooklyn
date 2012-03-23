package brooklyn.entity.basic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.trait.Startable
import brooklyn.entity.trait.StartableMethods
import brooklyn.location.Location
import brooklyn.management.internal.AbstractManagementContext
import brooklyn.management.internal.LocalManagementContext

public abstract class AbstractApplication extends AbstractEntity implements Startable, Application {
    public static final Logger log = LoggerFactory.getLogger(AbstractApplication.class);
    private volatile AbstractManagementContext mgmt = null;
    private boolean deployed = false
    
    public AbstractApplication(Map properties=[:]) {
        super(properties)
        this.@application = new SelfEntityReference(this);
        
        if (properties.mgmt) {
            mgmt = (AbstractManagementContext) properties.remove("mgmt")
        }

        setAttribute(SERVICE_UP, false)
    }
    
    /**
     * Default start will start all Startable children
     */
    public void start(Collection<? extends Location> locations) {
        this.locations.addAll(locations)
        
		StartableMethods.start(this, locations);
		
        setAttribute(SERVICE_UP, true)
        deployed = true
        
        log.info("Started application "+this);
    }

    /**
     * Default stop will stop all Startable children
     */
    public void stop() {
        log.info("Stopping application "+this);
        
    	setAttribute(SERVICE_UP, false)
		StartableMethods.stop(this);
		
        deployed = false
        
        //TODO review mgmt destroy lifecycle
        //  we don't necessarily want to forget all about the app on stop, 
        //since operator may be interested in things recently stopped;
        //but that could be handled by the impl at management
        //(keeping recently unmanaged things)  
        //  however unmanaging must be done last, _after_ we stop children and set attributes 
        getManagementContext().unmanage(this)
        
        log.info("Stopped application "+this);
    }

    public void restart() {
        throw new UnsupportedOperationException()
    }

    @Override
    public synchronized AbstractManagementContext getManagementContext() {
        if (mgmt) return mgmt

        //TODO how does user override?  expect he annotates a field in this class, then look up that field?
        //(do that here)

        mgmt = new LocalManagementContext()
        if (deployed) {
            mgmt.manage(this)
        }
        return mgmt
    }
 
    public boolean isDeployed() {
        // TODO How to tell if we're deployed? What if sub-class overrides start 
        return deployed
    }
}
