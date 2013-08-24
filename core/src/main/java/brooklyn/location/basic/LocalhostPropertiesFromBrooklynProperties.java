package brooklyn.location.basic;

import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.ConfigKeys;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;

/**
 * @author aledsage
 **/
public class LocalhostPropertiesFromBrooklynProperties extends LocationPropertiesFromBrooklynProperties {

    // TODO Once delete support for deprecated "location.localhost.*" then can get rid of this class, and use
    // LocationPropertiesFromBrooklynProperties directly
    
    private static final Logger LOG = LoggerFactory.getLogger(LocalhostPropertiesFromBrooklynProperties.class);

    @Override
    public Map<String, Object> getLocationProperties(String provider, String namedLocation, Map<String, ?> properties) {
        if (Strings.isNullOrEmpty(namedLocation) && Strings.isNullOrEmpty(provider)) {
            throw new IllegalArgumentException("Neither cloud provider/API nor location name have been specified correctly");
        }

        Map<String, Object> result = Maps.newHashMap();
        
        if (!Strings.isNullOrEmpty(namedLocation)) {
            String namedProvider = getNamedProvider(namedLocation, properties);
            if (!Strings.isNullOrEmpty(provider)) {
                if (namedProvider!=null && !provider.equals(namedProvider)) 
                    throw new IllegalStateException("Conflicting configuration: provider="+provider+"; namedLocation="+namedLocation+"; namedProvider="+namedProvider);
            } else if (Strings.isNullOrEmpty(namedProvider)) {
                throw new IllegalStateException("Missing configuration: no named provider for named location "+namedLocation);
            }
            provider = namedProvider;
        }
        
        result.putAll(transformDeprecated(getGenericLocationSingleWordProperties(properties)));
        result.putAll(transformDeprecated(getMatchingSingleWordProperties("brooklyn.location.", properties)));
        result.putAll(transformDeprecated(getMatchingProperties("brooklyn.location.localhost.", "brooklyn.localhost.", properties)));
        if (!Strings.isNullOrEmpty(namedLocation)) result.putAll(transformDeprecated(getNamedLocationProperties(namedLocation, properties)));
        String brooklynDataDir = (String) properties.get(ConfigKeys.BROOKLYN_DATA_DIR.getName());
        if (brooklynDataDir != null && brooklynDataDir.length() > 0) {
            result.put("localTempDir", new File(brooklynDataDir));
        }
        
        return result;
    }
}
