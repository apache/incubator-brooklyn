package brooklyn.entity.basic

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.Collection
import java.util.Map
import java.util.concurrent.ConcurrentHashMap

public abstract class AbstractApplication extends AbstractGroup implements Application {
    public AbstractApplication(Map properties=[:]) {
        super(properties, null)
    }
    
    final ObservableMap entities = new SerializableObservableMap(new ConcurrentHashMap<String,Entity>());
 
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
    public void start(Map properties=[:]) {
        EntityStartUtils.startGroup properties, this
    }
}
