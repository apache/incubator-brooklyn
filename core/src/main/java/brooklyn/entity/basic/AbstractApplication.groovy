package brooklyn.entity.basic

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.trait.Changeable
import brooklyn.location.Location
import brooklyn.management.internal.AbstractManagementContext
import brooklyn.management.internal.LocalManagementContext
import brooklyn.util.internal.EntityStartUtils
import brooklyn.util.internal.SerializableObservableMap
import java.beans.PropertyChangeListener
import java.util.concurrent.ConcurrentHashMap

public abstract class AbstractApplication extends AbstractGroup implements Application, Changeable {

    private volatile AbstractManagementContext mgmt = null;
    private boolean deployed = false
    
    public AbstractApplication(Map properties=[:]) {
        super(properties)
        this.@application = new SelfEntityReference(this);
        
        if (properties.mgmt) {
            mgmt = (AbstractManagementContext) properties.remove("mgmt")
        }
    }
    
    /**
     * Default start will start all Startable children
     */
    public void start(Collection<Location> locs) {
        getManagementContext().manage(this)
        
        EntityStartUtils.startGroup this, locs
        deployed = true
    }

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
