package brooklyn.location.basic;

import java.util.Map;

import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;

public class LocationCreationUtils {

    /** uses reflection to create a location with of the same type as the parent, 
     * with the given parent, assuming a Map constructor */
    @SuppressWarnings("unchecked")
    public static <T extends AbstractLocation> T newSubLocation(Map<?,?> newFlags, T parent) {
        return (T) newSubLocationOfType(newFlags, parent, parent.getClass());
    }
    
    /** uses reflection to create a location with of the given type, 
     * with the given parent, assuming a Map constructor */
    public static <T extends AbstractLocation> T newSubLocationOfType(Map<?,?> newFlags, AbstractLocation parent, Class<T> newLocationType) {
        // TODO shouldn't have to copy config bag as it should be inherited (but currently it is not used inherited everywhere; just most places)
        ConfigBag newConfig = ConfigBag.newInstanceCopying(parent.getConfigBag()).
                configure(AbstractLocation.PARENT_LOCATION, parent).
                putAll(newFlags);
        try {
            T result = newLocationType.getConstructor(Map.class).newInstance(newConfig.getAllConfig());
            // mark flags which have been used
            result.getConfigBag().copy(newConfig);
            return result;
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }

    }
}
