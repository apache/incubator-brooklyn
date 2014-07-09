package brooklyn.mementos;

import java.util.Map;

import brooklyn.entity.rebind.RebindSupport;

/**
 * Represents the state of an policy, so that it can be reconstructed (e.g. after restarting brooklyn).
 * 
 * @see RebindSupport
 * 
 * @author aled
 */
public interface PolicyMemento extends Memento {

    Map<String, Object> getConfig();
}
