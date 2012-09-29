package brooklyn.mementos;

import java.util.Map;
import java.util.Set;


public interface LocationMemento extends TreeNode, Memento {

    String getType();
    
    String getDisplayName();

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
