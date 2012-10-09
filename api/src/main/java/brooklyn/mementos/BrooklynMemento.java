package brooklyn.mementos;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * Represents an entire persisted Brooklyn management context, with all its entities and locations.
 * 
 * The referential integrity of this memento is not guaranteed. For example, an entity memento might
 * reference a child entity that does not exist. This is an inevitable consequence of not using a
 * stop-the-world persistence strategy, and is essential for a distributed brooklyn to be performant.
 * 
 * Code using this memento should be tolerant of such inconsistencies (e.g. log a warning about the 
 * missing entity, and then ignore dangling references when constructing the entities/locations, so
 * that code will not subsequently get NPEs when iterating over children for example).
 * 
 * @author aled
 */
public interface BrooklynMemento extends Serializable {

    public EntityMemento getEntityMemento(String id);

    public LocationMemento getLocationMemento(String id);
    
    public PolicyMemento getPolicyMemento(String id);
    
    public Collection<String> getApplicationIds();
    
    public Collection<String> getTopLevelLocationIds();

    public Collection<String> getEntityIds();
    
    public Collection<String> getLocationIds();

    public Collection<String> getPolicyIds();

    public Map<String, EntityMemento> getEntityMementos();

    public Map<String, LocationMemento> getLocationMementos();

    public Map<String, PolicyMemento> getPolicyMementos();
}
