package brooklyn.mementos;

import java.util.Map;
import java.util.Set;


public interface LocationMemento extends TreeNode, Memento {

    Map<String, Object> getLocationConfig();
    Set<String> getLocationConfigUnused();
    String getLocationConfigDescription();

    /**
     * The keys in {@link getLocationConfig()} that reference other locations.
     * 
     * The initialization of these fields will be deferred until we can guarantee these objects have all 
     * been created.
     */
    Set<String> getLocationConfigReferenceKeys();

}
