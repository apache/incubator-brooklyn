package brooklyn.location.basic;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.config.ConfigBag;

import com.google.common.base.Strings;

/**
 * @author aledsage
 **/
public class LocalhostPropertiesFromBrooklynProperties extends LocationPropertiesFromBrooklynProperties {

    // TODO Once delete support for deprecated "location.localhost.*" then can get rid of this class, and use
    // LocationPropertiesFromBrooklynProperties directly
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(LocalhostPropertiesFromBrooklynProperties.class);

    @Override
    public Map<String, Object> getLocationProperties(String provider, String namedLocation, Map<String, ?> properties) {
        if (Strings.isNullOrEmpty(namedLocation) && Strings.isNullOrEmpty(provider)) {
            throw new IllegalArgumentException("Neither cloud provider/API nor location name have been specified correctly");
        }

        ConfigBag result = ConfigBag.newInstance();
        
        result.putAll(transformDeprecated(getGenericLocationSingleWordProperties(properties)));
        result.putAll(transformDeprecated(getMatchingSingleWordProperties("brooklyn.location.", properties)));
        result.putAll(transformDeprecated(getMatchingProperties("brooklyn.location.localhost.", "brooklyn.localhost.", properties)));
        if (!Strings.isNullOrEmpty(namedLocation)) result.putAll(transformDeprecated(getNamedLocationProperties(namedLocation, properties)));
        setLocalTempDir(properties, result);
        
        return result.getAllConfigRaw();
    }
}
