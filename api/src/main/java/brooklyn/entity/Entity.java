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
    
    
    //FIXME should these be here?  or is Abstract good enough?
    
    /**
     * Mutable attributes on this entity.
     * 
     * This can include activity information and status information (e.g. AttributeSensors), as well as
     * arbitrary internal properties which can make life much easier/dynamic (though we lose something in type safety)
     * e.g. jmxHost / jmxPort are handled as attributes
     */
    Map<String,Object> getAttributes();

    // TODO the owner is the parent that strictly contains this entity
//    Group getOwner();
    // TODO Entity.getParents() makes me think of containment relationships too much. I'd prefer groups?
    Collection<Group> getParents();
    void addParent(Group e);

    <T> void updateAttribute(Sensor<T> attribute, T val);
    
//    void subscribe(EventFilter filter, EventListener listener);
//    void subscribe(Predicate<Entity> entities, EventFilter filter, EventListener listener);

    <T> void subscribe(String entityId, String sensorname, EventListener<T> listener);
 
    <T> void raiseEvent(Event<T> event);
    
    Collection<Location> getLocations();
}
