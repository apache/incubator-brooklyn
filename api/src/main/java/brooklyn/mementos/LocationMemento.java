package brooklyn.mementos;

import java.util.List;
import java.util.Map;
import java.util.Set;


public interface LocationMemento extends Memento {

    String getType();
    
    String getId();
    
    String getDisplayName();

    String getParent();
    
    List<String> getChildren();
    
	Map<String, Object> getLocationProperties();
	
    Map<String, Object> getFlags();

    /**
     * The keys in {@link getFlags()} that reference other locations.
     * 
     * The initialization of these fields will be deferred until we can guarantee these objects have all 
     * been created.
     */
    Set<String> getLocationReferenceFlags();
    
    Object getCustomProperty(String name);

	Map<String, ? extends Object> getCustomProperties();

}
