package brooklyn.entity.basic

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.Collection
import java.util.Map
import java.util.concurrent.ConcurrentHashMap

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.location.Location
import brooklyn.management.ManagementContext
import brooklyn.management.internal.AbstractManagementContext
import brooklyn.management.internal.LocalManagementContext
import brooklyn.util.internal.EntityStartUtils
import brooklyn.util.internal.SerializableObservableMap

public abstract class AbstractApplication extends AbstractGroup implements Application {
    final ObservableMap entities = new SerializableObservableMap(new ConcurrentHashMap<String,Entity>());
    private volatile AbstractManagementContext mgmt = null;
    private boolean deployed = false
    
    public AbstractApplication(Map properties=[:]) {
        super(properties)
    }
    
    public void registerEntity(Entity entity) {
        entities.put entity.id, entity
    }
    
    Collection<Entity> getEntities() { entities.values() }

    private static class ClosurePropertyChangeListener implements PropertyChangeListener {
        Closure closure;
        public ClosurePropertyChangeListener(Closure c) { closure=c }
        public void propertyChange(PropertyChangeEvent event) {
            closure.call(event)
        }
    }
    
    public void addEntityChangeListener(PropertyChangeListener listener) {
        entities.addPropertyChangeListener listener;
    }

    protected void initApplicationRegistrant() {
        // do nothing; we register ourself later
    }

    // record ourself as an entity in the entity list
    { registerWithApplication this }
    
    /**
     * Default start will start all Startable children
     */
    public void start(Collection<Location> locs) {
        getManagementContext()
        EntityStartUtils.startGroup this, locs
        deployed = true
    }
    
    public AbstractManagementContext getManagementContext() {
        AbstractManagementContext result = mgmt
        if (result==null) synchronized (this) {
            result = mgmt
            if (result!=null) return result
            
            //TODO how does user override?  expect he annotates a field in this class, then look up that field?
            //(do that here)
            
            if (result==null)
                result = new LocalManagementContext()
            result.registerApplication(this)
            mgmt = result
        }
        result
    }
 
    public boolean isDeployed() {
        // TODO How to tell if we're deployed? What if sub-class overrides start 
        return deployed
    }
}
