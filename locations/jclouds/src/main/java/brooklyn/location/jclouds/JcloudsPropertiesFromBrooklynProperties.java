package brooklyn.location.jclouds;

import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigUtils;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.location.basic.DeprecatedKeysMappingBuilder;
import brooklyn.location.basic.LocationPropertiesFromBrooklynProperties;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * <p>
 * The properties to use for a jclouds location, loaded from brooklyn.properties file
 * </p>
 * 
 * Preferred format is:
 * 
 * <ul>
 * <li>
 * brooklyn.location.named.NAME.key 
 * </li>
 * <li>
 * brooklyn.location.jclouds.PROVIDER.key
 * </li>
 * </ul>
 * 
 * <p>
 * A number of properties are also supported, listed in the {@code JcloudsLocationConfig}
 * </p>
 * 
 * @author andrea
 **/
public class JcloudsPropertiesFromBrooklynProperties extends LocationPropertiesFromBrooklynProperties {

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsPropertiesFromBrooklynProperties.class);

    @SuppressWarnings("deprecation")
    private static final Map<String, String> DEPRECATED_JCLOUDS_KEYS_MAPPING = new DeprecatedKeysMappingBuilder(LOG)
            .putAll(LocationPropertiesFromBrooklynProperties.DEPRECATED_KEYS_MAPPING)
            .camelToHyphen(JcloudsLocation.IMAGE_ID)
            .camelToHyphen(JcloudsLocation.IMAGE_NAME_REGEX)
            .camelToHyphen(JcloudsLocation.IMAGE_DESCRIPTION_REGEX)
            .camelToHyphen(JcloudsLocation.HARDWARE_ID)
            .build();

    @Override
    public Map<String, Object> getLocationProperties(String provider, String namedLocation, Map<String, ?> properties) {
        throw new UnsupportedOperationException("Instead use getJcloudsProperties(String,String,String,Map)");
    }
    
    /**
     * @see LocationPropertiesFromBrooklynProperties#getLocationProperties(String, String, Map)
     */
    public Map<String, Object> getJcloudsProperties(String providerOrApi, String regionName, String namedLocation, Map<String, ?> properties) {
        if(Strings.isNullOrEmpty(namedLocation) && Strings.isNullOrEmpty(providerOrApi)) {
            throw new IllegalArgumentException("Neither cloud provider/API nor location name have been specified correctly");
        }

        Map<String, Object> jcloudsProperties = Maps.newHashMap();
        String provider = getProviderName(providerOrApi, namedLocation, properties);
        
        // named properties are preferred over providerOrApi properties
        jcloudsProperties.put("provider", provider);
        jcloudsProperties.putAll(transformDeprecated(getGenericLocationSingleWordProperties(properties)));
        jcloudsProperties.putAll(transformDeprecated(getGenericJcloudsSingleWordProperties(providerOrApi, properties)));
        jcloudsProperties.putAll(transformDeprecated(getProviderOrApiJcloudsProperties(providerOrApi, properties)));
        jcloudsProperties.putAll(transformDeprecated(getRegionJcloudsProperties(providerOrApi, regionName, properties)));
        if (!Strings.isNullOrEmpty(namedLocation)) jcloudsProperties.putAll(transformDeprecated(getNamedJcloudsProperties(namedLocation, properties)));
        String brooklynDataDir = (String) properties.get(BrooklynConfigKeys.BROOKLYN_DATA_DIR.getName());
        if (brooklynDataDir != null && brooklynDataDir.length() > 0) {
            jcloudsProperties.put("localTempDir", new File(brooklynDataDir));
        }
        

        return jcloudsProperties;
    }

    protected String getProviderName(String providerOrApi, String locationName, Map<String, ?> properties) {
        String provider = providerOrApi;
        if (!Strings.isNullOrEmpty(locationName)) {
            String providerDefinition = (String) properties.get(String.format("brooklyn.location.named.%s", locationName));
            if (providerDefinition!=null)
                provider = getProviderFromDefinition(providerDefinition);
        }
        return provider;
    }
    
    protected String getProviderFromDefinition(String definition) {
        return Iterables.get(Splitter.on(":").split(definition), 1);
    }

    protected Map<String, Object> getGenericJcloudsSingleWordProperties(String providerOrApi, Map<String, ?> properties) {
        if (Strings.isNullOrEmpty(providerOrApi)) return Maps.newHashMap();
        String deprecatedPrefix = "brooklyn.jclouds.";
        String preferredPrefix = "brooklyn.location.jclouds.";
        return getMatchingSingleWordProperties(preferredPrefix, deprecatedPrefix, properties);
    }

    protected Map<String, Object> getProviderOrApiJcloudsProperties(String providerOrApi, Map<String, ?> properties) {
        if (Strings.isNullOrEmpty(providerOrApi)) return Maps.newHashMap();
        String preferredPrefix = String.format("brooklyn.location.jclouds.%s.", providerOrApi);
        String deprecatedPrefix = String.format("brooklyn.jclouds.%s.", providerOrApi);
        
        return getMatchingProperties(preferredPrefix, deprecatedPrefix, properties);
    }

    protected Map<String, Object> getRegionJcloudsProperties(String providerOrApi, String regionName, Map<String, ?> properties) {
        if (Strings.isNullOrEmpty(providerOrApi) || Strings.isNullOrEmpty(regionName)) return Maps.newHashMap();
        String preferredPrefix = String.format("brooklyn.location.jclouds.%s@%s.", providerOrApi, regionName);
        String deprecatedPrefix = String.format("brooklyn.jclouds.%s@%s.", providerOrApi, regionName);
        
        return getMatchingProperties(preferredPrefix, deprecatedPrefix, properties);
    }

    protected Map<String, Object> getNamedJcloudsProperties(String locationName, Map<String, ?> properties) {
        if(locationName == null) return Maps.newHashMap();
        String prefix = String.format("brooklyn.location.named.%s.", locationName);
        return ConfigUtils.filterForPrefixAndStrip(properties, prefix).asMapWithStringKeys();
    }

    @Override
    protected Map<String, String> getDeprecatedKeysMapping() {
        return DEPRECATED_JCLOUDS_KEYS_MAPPING;
    }
}
