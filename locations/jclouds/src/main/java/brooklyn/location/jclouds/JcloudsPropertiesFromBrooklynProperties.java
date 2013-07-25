package brooklyn.location.jclouds;

import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigUtils;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.basic.DeprecatedKeysMappingBuilder;

import com.google.common.base.Predicates;
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
 * brooklyn.jclouds.PROVIDER.key
 * </li>
 * </ul>
 * 
 * <p>
 * A number of properties are also supported, listed in the {@code JcloudsLocationConfig}
 * </p>
 * 
 * @author andrea
 **/
public class JcloudsPropertiesFromBrooklynProperties {

    public static final Logger log = LoggerFactory.getLogger(JcloudsPropertiesFromBrooklynProperties.class);

    @SuppressWarnings("deprecation")
    private static final Map<String, String> DEPRECATED_KEYS_MAPPING = new DeprecatedKeysMappingBuilder(log)
            .camelToHyphen(JcloudsLocation.PRIVATE_KEY_FILE)
            .camelToHyphen(JcloudsLocation.PRIVATE_KEY_DATA)
            .camelToHyphen(JcloudsLocation.PRIVATE_KEY_PASSPHRASE)
            .camelToHyphen(JcloudsLocation.PUBLIC_KEY_FILE)
            .camelToHyphen(JcloudsLocation.PUBLIC_KEY_DATA)
            .camelToHyphen(JcloudsLocation.IMAGE_ID)
            .camelToHyphen(JcloudsLocation.IMAGE_NAME_REGEX)
            .camelToHyphen(JcloudsLocation.IMAGE_DESCRIPTION_REGEX)
            .camelToHyphen(JcloudsLocation.HARDWARE_ID)
            .camelToHyphen(JcloudsLocation.CALLER_CONTEXT)
            .build();
    
    /**
     * Finds the properties that apply to this provider/region, stripping off the prefixes.
     * <p>
     * This implementation considers only named properties and provider-specific properties
     * Legacy properties are not supported.
     */
    public static Map<String, Object> getJcloudsProperties(String providerOrApi, String regionName, String locationName, Map<String, Object> properties) {
        if(Strings.isNullOrEmpty(locationName) && Strings.isNullOrEmpty(providerOrApi)) {
            throw new IllegalArgumentException("Neither cloud provider/API nor location name have been specified correctly");
        }

        Map<String, Object> jcloudsProperties = Maps.newHashMap();
        String provider = getProviderName(providerOrApi, locationName, properties);
        
        // named properties are preferred over providerOrApi properties
        jcloudsProperties.put("provider", provider);
        jcloudsProperties.putAll(transformDeprecated(getGenericJcloudsProperties(providerOrApi, properties)));
        jcloudsProperties.putAll(transformDeprecated(getRegionJcloudsProperties(providerOrApi, regionName, properties)));
        jcloudsProperties.putAll(transformDeprecated(getProviderOrApiJcloudsProperties(providerOrApi, properties)));
        jcloudsProperties.putAll(transformDeprecated(getNamedJcloudsProperties(locationName, properties)));
        String brooklynDataDir = (String) properties.get(ConfigKeys.BROOKLYN_DATA_DIR.getName());
        if (brooklynDataDir != null && brooklynDataDir.length() > 0) {
            jcloudsProperties.put("localTempDir", new File(brooklynDataDir));
        }
        

        return jcloudsProperties;
    }

    private static String getProviderName(String providerOrApi, String locationName, Map<String, Object> properties) {
        String provider = providerOrApi;
        if(!Strings.isNullOrEmpty(locationName)) {
            String providerDefinition = (String) properties.get(String.format("brooklyn.location.named.%s", locationName));
            if (providerDefinition!=null)
                provider = getProviderFromDefinition(providerDefinition);
        }
        return provider;
    }
    
    private static Map<String, Object> getRegionJcloudsProperties(String providerOrApi, String regionName, Map<String, Object> properties) {
        if(providerOrApi == null) return Maps.newHashMap();
        String prefix = (regionName != null) ? 
                String.format("brooklyn.jclouds.%s@%s.", providerOrApi, regionName) :
                String.format("brooklyn.jclouds.%s.", providerOrApi);
        return ConfigUtils.filterForPrefixAndStrip(properties, prefix).asMapWithStringKeys();
    }

    private static Map<String, Object> getProviderOrApiJcloudsProperties(String providerOrApi, Map<String, Object> properties) {
        if(providerOrApi == null) return Maps.newHashMap();
        String prefix = String.format("brooklyn.jclouds.%s.", providerOrApi);
        return ConfigUtils.filterForPrefixAndStrip(properties, prefix);
    }

    private static Map<String, Object> getGenericJcloudsProperties(String providerOrApi, Map<String, Object> properties) {
        if(providerOrApi == null) return Maps.newHashMap();
        String prefix = "brooklyn.jclouds.";
        BrooklynProperties filteredProperties = ConfigUtils.filterForPrefixAndStrip(properties, prefix);
        return ConfigUtils.filterFor(filteredProperties, Predicates.not(Predicates.containsPattern("\\."))).asMapWithStringKeys();
    }

    private static Map<String, Object> getNamedJcloudsProperties(String locationName, Map<String, Object> properties) {
        if(locationName == null) return Maps.newHashMap();
        String prefix = String.format("brooklyn.location.named.%s.", locationName);
        return ConfigUtils.filterForPrefixAndStrip(properties, prefix).asMapWithStringKeys();
    }

    private static String getProviderFromDefinition(String definition) {
        return Iterables.get(Splitter.on(":").split(definition), 1);
    }

    private static Map<String, Object> transformDeprecated(Map<String, ? extends Object> properties) {
        Map<String,Object> result = Maps.newLinkedHashMap();
        
        for (Map.Entry<String,?> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (DEPRECATED_KEYS_MAPPING.containsKey(key)) {
                String transformedKey = DEPRECATED_KEYS_MAPPING.get(key);
                log.warn("Deprecated key {}, transformed to {}; will not be supported in future versions", new Object[] {key, transformedKey});
                result.put(transformedKey, value);
            } else {
                result.put(key, value);
            }
        }
        
        return result;
    }
}
