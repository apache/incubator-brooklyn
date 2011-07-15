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
    final ObservableMap entities = new SerializableObservableMap(new ConcurrentHashMap<String,Entity>());

    private volatile AbstractManagementContext mgmt = null;
    private boolean deployed = false
    
    public AbstractApplication(Map properties=[:]) {
        super(properties)
        if(properties.mgmt) {
            mgmt = (AbstractManagementContext) properties.remove("mgmt")
            mgmt.registerApplication(this)
        }

        // record ourself as an entity in the entity list
        registerWithApplication this
    }
    
    public void registerEntity(Entity entity) {
        entities.put entity.id, entity
    }
    
    Collection<Entity> getEntities() { entities.values() }

    @Override
    public void addEntityChangeListener(PropertyChangeListener listener) {
        entities.addPropertyChangeListener listener;
    }

    protected void initApplicationRegistrant() {
        // do nothing; we register ourself later
    }

    /**
     * Default start will start all Startable children
     */
    public void start(Collection<Location> locs) {
        getManagementContext()
        EntityStartUtils.startGroup this, locs
        deployed = true
    }

    public synchronized AbstractManagementContext getManagementContext() {
        if (mgmt) return mgmt

        //TODO how does user override?  expect he annotates a field in this class, then look up that field?
        //(do that here)

        mgmt = new LocalManagementContext()
        mgmt.registerApplication(this)
        return mgmt
    }
 
    public boolean isDeployed() {
        // TODO How to tell if we're deployed? What if sub-class overrides start 
        return deployed
    }
}
