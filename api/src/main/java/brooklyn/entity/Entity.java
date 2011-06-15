package brooklyn.entity;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/**
 * The basic interface for an OverPaaS entity.
 * 
 * @see AbstractEntity
 */
public interface Entity extends Serializable {
    String getId();
    String getDisplayName();
    EntitySummary getSummary();
    
    Application getApplication();

    /**
     * ad hoc map for storing, e.g. description, icons, etc
     */
    Map getPresentationAttributes();

    /**
     * Mutable properties on this entity.
     * 
     * Allows one to put arbitrary properties on entities which makes life much easier/dynamic, 
     * though we lose something in type safety.
     * <p>
     * e.g. jmxHost / jmxPort are handled as properties.
     */
    Map<String, Object> getProperties();

    Collection<Group> getParents();
    void addParent(Group e);

    Collection<Field> getSensors();
    Collection<Method> getEffectors();
    
//    void subscribe(EventFilter filter, EventListener listener);
//    void subscribe(Predicate<Entity> entities, EventFilter filter, EventListener listener);
//
//    void raiseEvent(SensorEvent<?> event);
}
