package brooklyn.mementos;

import java.util.Map;

import brooklyn.entity.rebind.RebindSupport;

/**
 * Represents the state of an enricher, so that it can be reconstructed (e.g. after restarting brooklyn).
 * 
 * @see RebindSupport
 */
public interface EnricherMemento extends Memento {

    Map<String, Object> getConfig();
}
