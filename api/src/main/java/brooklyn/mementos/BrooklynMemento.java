package brooklyn.mementos;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

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
