package brooklyn.entity;

import java.io.Serializable;
import java.util.Collection;

import brooklyn.event.Event;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
import brooklyn.location.Location;

/**
 * The basic interface for an OverPaaS entity.
 * 
 * @see AbstractEntity
 */
public interface Entity extends Serializable {

    /**
     * @return The unique identifier for this entity.
     */
    String getId();
    
    /**
     * A display name; recommended to be a concise single-line description.
     */
    String getDisplayName();
    
    /**
     * Information about the type of this entity; analogous to Java's object.getClass.
     */
    EntityClass getEntityClass();
    
    Application getApplication();
    
    /**
     * The "owner" of this entity, if null if no owner. The owner is normally the entity 
     * responsible for creating/destroying this entity.
     */
    Group getOwner();
    
    /**
     * The groups that this entity is a member of. Groupings can be used to allow easy 
     * management/monitoring of a group of entities.
     */
    Collection<Group> getGroups();
    
//    void subscribe(EventFilter filter, EventListener listener);
//    void subscribe(Predicate<Entity> entities, EventFilter filter, EventListener listener);

    <T> void subscribe(String entityId, String sensorname, EventListener<T> listener);
 
    <T> void raiseEvent(Event<T> event);
    
    Collection<Location> getLocations();
}
