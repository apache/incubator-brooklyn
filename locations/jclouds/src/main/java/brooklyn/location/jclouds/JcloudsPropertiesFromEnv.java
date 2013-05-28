package brooklyn.location.jclouds;

import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigUtils;
import brooklyn.entity.basic.ConfigKeys;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * <p>
 * The properties to use for a jclouds location, loaded from environment
 * variables / system properties
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
public class JcloudsPropertiesFromEnv {

    public static final Logger log = LoggerFactory.getLogger(JcloudsPropertiesFromEnv.class);

    public static Map<String, Object> getJcloudsPropertiesFromEnv(String providerOrApi, Map<String, Object> properties) {
        return getJcloudsPropertiesFromEnv(providerOrApi, null, properties);
    }
    /*
     * This implementation considers only named properties and provider-specific properties
     * Legacy properties are not supported 
     */
    public static Map<String, Object> getJcloudsPropertiesFromEnv(String providerOrApi, String locationName, Map<String, Object> properties) {
        String provider = providerOrApi;
        Map<String, Object> jcloudsProperties = Maps.newHashMap();
        String brooklynDataDir = (String) properties.get(ConfigKeys.BROOKLYN_DATA_DIR.getName());
        if (brooklynDataDir != null && brooklynDataDir.length() > 0) {
            jcloudsProperties.put("localTempDir", new File(brooklynDataDir));
        }
        // named properties are preferred over providerOrApi properties
        if(!Strings.isNullOrEmpty(locationName)) {
            provider = getProviderFromNamedProperty(locationName, properties);
            jcloudsProperties.putAll(getNamedJcloudsProperties(locationName, properties));
        } else if(!Strings.isNullOrEmpty(providerOrApi)) {
            jcloudsProperties.putAll(getProviderOrApiJcloudsProperties(providerOrApi, properties));
        } else {
            throw new IllegalArgumentException("Neither cloud provider/API nor location name have been specified correctly");
        }
        jcloudsProperties.put("provider", provider);
        return sanitize(jcloudsProperties);
    }

    private static Map<String, Object> getProviderOrApiJcloudsProperties(String providerOrApi, Map<String, Object> properties) {
        String prefix = String.format("brooklyn.jclouds.%s.", providerOrApi);
        return ConfigUtils.filterForPrefixAndStrip(properties, prefix).asMapWithStringKeys();
    }

    private static Map<String, Object> getNamedJcloudsProperties(String locationName, Map<String, Object> properties) {
        String prefix = String.format("brooklyn.location.named.%s.", locationName);
        return ConfigUtils.filterForPrefixAndStrip(properties, prefix).asMapWithStringKeys();
    }

    private static String getProviderFromNamedProperty(String locationName, Map<String, Object> properties) {
        return Iterables.get(Splitter.on(":").split((String) properties.get(String.format("brooklyn.location.named.%s", locationName))), 1);
    }

    private static Map<String, Object> sanitize(Map<String, Object> properties) {
        Map<String, Object> sanitizedProperties = Maps.newHashMap(properties);
        for (String key : properties.keySet()) {
            if(!key.startsWith("jclouds.") && key.contains("-")) {
                log.warn("Deprecated use of old-style property for '{}', it will be ignored.", new Object[] {key});
                sanitizedProperties.remove(key);
            } 
        }  
        return sanitizedProperties;
    }

}
