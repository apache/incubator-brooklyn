package brooklyn.mementos;

import java.io.Serializable;
import java.util.Map;

/**
 * Represents a manifest of the entities etc in the overall memento.
 * 
 * @author aled
 */
public interface BrooklynMementoManifest extends Serializable {

    public Map<String, String> getEntityIdToType();

    public Map<String, String> getLocationIdToType();

    public Map<String, String> getPolicyIdToType();
}
