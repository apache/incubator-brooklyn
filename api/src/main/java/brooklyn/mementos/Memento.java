package brooklyn.mementos;

import java.io.Serializable;
import java.util.Map;

import brooklyn.entity.rebind.RebindSupport;

/**
 * Represents the internal state of something in brooklyn, so that it can be reconstructed (e.g. after restarting brooklyn).
 * 
 * @see RebindSupport
 * 
 * @author aled
 */
public interface Memento extends Serializable {

    /**
     * The version of brooklyn used when this memento was generated.
     */
    String getBrooklynVersion();
    
    String getId();
    
    public String getType();
    
    public String getDisplayName();
    
    /**
     * A (weakly-typed) property set for this memento.
     * These can be used to avoid sub-classing the entity memento, but developers can sub-class to get strong typing if desired.
     */
    public Object getCustomField(String name);

    public Map<String, ? extends Object> getCustomFields();
}
