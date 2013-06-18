package brooklyn.location.jclouds;

import java.io.File;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.config.ConfigUtils;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.basic.LocationConfigKeys;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

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
        warnOfDeprecated(properties);

        Map<String, Object> jcloudsProperties = Maps.newHashMap();
        String provider = getProviderName(providerOrApi, locationName, properties);
        
        // named properties are preferred over providerOrApi properties
        jcloudsProperties.put("provider", provider);
        jcloudsProperties.putAll(getRegionJcloudsProperties(providerOrApi, regionName, properties));
        jcloudsProperties.putAll(getProviderOrApiJcloudsProperties(providerOrApi, properties));
        jcloudsProperties.putAll(getNamedJcloudsProperties(locationName, properties));
        String brooklynDataDir = (String) properties.get(ConfigKeys.BROOKLYN_DATA_DIR.getName());
        if (brooklynDataDir != null && brooklynDataDir.length() > 0) {
            jcloudsProperties.put("localTempDir", new File(brooklynDataDir));
        }
        //return sanitizeJcloudsProperties(jcloudsProperties);
        return jcloudsProperties;
    }

    private static String getProviderName(String providerOrApi, String locationName, Map<String, Object> properties) {
        String provider = providerOrApi;
        if(!Strings.isNullOrEmpty(locationName)) {
            provider = getProviderFromNamedProperty(locationName, properties);
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
        return ConfigUtils.filterForPrefixAndStrip(properties, prefix).asMapWithStringKeys();
    }

    private static Map<String, Object> getNamedJcloudsProperties(String locationName, Map<String, Object> properties) {
        if(locationName == null) return Maps.newHashMap();
        String prefix = String.format("brooklyn.location.named.%s.", locationName);
        return ConfigUtils.filterForPrefixAndStrip(properties, prefix).asMapWithStringKeys();
    }

    private static String getProviderFromNamedProperty(String locationName, Map<String, Object> properties) {
        return Iterables.get(Splitter.on(":").split((String) properties.get(String.format("brooklyn.location.named.%s", locationName))), 1);
    }

    private static void warnOfDeprecated(Map<String, Object> properties) {
        Set<String> deprecatedKeys = Sets.newLinkedHashSet();
        Set<ConfigKey<?>> configKeys = Sets.union(JcloudsLocation.getAllSupportedProperties(), getSupportedConfigKeysOn(LocationConfigKeys.class));
        
        for (ConfigKey<?> configKey : configKeys) {
            String camelCaseName = configKey.getName();
            String hyphenCaseName = convertFromCamelToProperty(camelCaseName);
            deprecatedKeys.add("brooklyn.jclouds."+camelCaseName);
            deprecatedKeys.add(camelCaseName);
            
            if (!camelCaseName.equals(hyphenCaseName)) {
                deprecatedKeys.add("brooklyn.jclouds."+hyphenCaseName);
                deprecatedKeys.add(hyphenCaseName);
            }
        }
        
        for (String key : properties.keySet()) {
            if (deprecatedKeys.contains(key)) {
                log.warn("Deprecated use of configuration key '{}'; will be ignored; keys should be prefixed with brooklyn.jclouds.{PROVIDER} or brooklyn.jclouds.named.{NAME}", key);
            }
        }
    }
    
    private static String convertFromCamelToProperty(String word) {
        StringBuilder result = new StringBuilder();
        for (char c: word.toCharArray()) {
            if (Character.isUpperCase(c)) {
                result.append("-");
            }
            result.append(Character.toLowerCase(c));
        }
        return result.toString();
    }

    private static Set<ConfigKey<?>> getSupportedConfigKeysOn(Class<?> clazz) {
        return ImmutableSet.copyOf(Iterables.transform(ConfigUtils.getStaticKeysOnClass(clazz),
                new Function<HasConfigKey<?>,ConfigKey<?>>() {
                    @Override @Nullable
                    public ConfigKey<?> apply(@Nullable HasConfigKey<?> input) {
                        return input.getConfigKey();
                    }
                }));
    }
}
