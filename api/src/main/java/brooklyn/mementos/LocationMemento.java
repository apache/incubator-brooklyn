package brooklyn.mementos;

import java.util.Map;
import java.util.Set;

import brooklyn.entity.rebind.RebindSupport;

/**
 * Represents the state of an location, so that it can be reconstructed (e.g. after restarting brooklyn).
 * 
 * @see RebindSupport
 * 
 * @author aled
 */
public interface LocationMemento extends TreeNode, Memento {

    Map<String, Object> getLocationConfig();
    Set<String> getLocationConfigUnused();
    String getLocationConfigDescription();
}
