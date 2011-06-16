package brooklyn.entity;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

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
    String getId();
    String getDisplayName();
    EntitySummary getSummary();
    
    EntityClass getEntityClass();
    
    Application getApplication();

    /**
     * ad hoc map for storing, e.g. description, icons, etc
     */
    Map<?,?> getPresentationAttributes();

    /**
     * Mutable properties on this entity.
     * 
     * Allows one to put arbitrary properties on entities which makes life much easier/dynamic, 
     * though we lose something in type safety.
     * <p>
     * e.g. jmxHost / jmxPort are handled as properties.
     */
    @Deprecated // want to just use getAttributes(); needs confirmed and refactored
    Map<String, Object> getProperties();
    
    Map<String,Object> getAttributes();

    // TODO the owner is the parent that strictly contains this entity
//    Group getOwner();
    Collection<Group> getParents();
    void addParent(Group e);

    <T> void updateAttribute(Sensor<T> attribute, T val);
    
//    void subscribe(EventFilter filter, EventListener listener);
//    void subscribe(Predicate<Entity> entities, EventFilter filter, EventListener listener);

    <T> void subscribe(String entityId, String sensorname, EventListener<T> listener);
 
    <T> void raiseEvent(Event<T> event);
    
    Collection<Location> getLocations();
}
