package brooklyn.mementos;

import java.util.Collection;

public interface BrooklynMemento {

    public EntityMemento getEntityMemento(String id);

    public LocationMemento getLocationMemento(String id);
    
    public Collection<String> getApplicationIds();
    
    public Collection<String> getTopLevelLocationIds();

    public Collection<String> getEntityIds();
    
    public Collection<String> getLocationIds();

    public Map<String, EntityMemento> getEntityMementos();

    public Map<String, LocationMemento> getLocationMementos();
}
