package brooklyn.location.basic;

import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.BrooklynConfigKeys;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;

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

        Map<String, Object> result = Maps.newHashMap();
        
        result.putAll(transformDeprecated(getGenericLocationSingleWordProperties(properties)));
        result.putAll(transformDeprecated(getMatchingSingleWordProperties("brooklyn.location.", properties)));
        result.putAll(transformDeprecated(getMatchingProperties("brooklyn.location.localhost.", "brooklyn.localhost.", properties)));
        if (!Strings.isNullOrEmpty(namedLocation)) result.putAll(transformDeprecated(getNamedLocationProperties(namedLocation, properties)));
        String brooklynDataDir = (String) properties.get(BrooklynConfigKeys.BROOKLYN_DATA_DIR.getName());
        if (brooklynDataDir != null && brooklynDataDir.length() > 0) {
            result.put("localTempDir", new File(brooklynDataDir));
        }
        
        return result;
    }
}
