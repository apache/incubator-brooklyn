package brooklyn.location.jclouds;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.collections.Lists;
import org.testng.internal.annotations.Sets;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigUtils;
import brooklyn.entity.basic.ConfigKeys;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
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

    public static Map<String, Object> getJcloudsProperties(String providerOrApi, Map<String, Object> properties) {
        return getJcloudsProperties(providerOrApi, null, properties);
    }
    /*
     * This implementation considers only named properties and provider-specific properties
     * Legacy properties are not supported 
     */
    public static Map<String, Object> getJcloudsProperties(String providerOrApi, String locationName, Map<String, Object> properties) {
        if(Strings.isNullOrEmpty(locationName) && Strings.isNullOrEmpty(providerOrApi)) {
            throw new IllegalArgumentException("Neither cloud provider/API nor location name have been specified correctly");
        }
        Map<String, Object> jcloudsProperties = Maps.newHashMap();
        String provider = getProviderName(providerOrApi, locationName, properties);
        // named properties are preferred over providerOrApi properties
        jcloudsProperties.put("provider", provider);
        properties = sanitize(provider, properties);
        jcloudsProperties.putAll(getProviderOrApiJcloudsProperties(providerOrApi, properties));
        jcloudsProperties.putAll(getNamedJcloudsProperties(locationName, properties));
        String brooklynDataDir = (String) properties.get(ConfigKeys.BROOKLYN_DATA_DIR.getName());
        if (brooklynDataDir != null && brooklynDataDir.length() > 0) {
            jcloudsProperties.put("localTempDir", new File(brooklynDataDir));
        }
        return sanitizeJcloudsProperties(jcloudsProperties);
    }

    private static String getProviderName(String providerOrApi, String locationName, Map<String, Object> properties) {
        String provider = providerOrApi;
        if(!Strings.isNullOrEmpty(locationName)) {
            provider = getProviderFromNamedProperty(locationName, properties);
        }
        return provider;
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

    private static Map<String, Object> sanitize(String provider, Map<String, Object> properties) {
        Map<String, Object> sanitizedProperties = Maps.newHashMap(properties);
        
        for (String key : properties.keySet()) {
            Iterable<String> splittedKey = Splitter.on(".").split(key);
            if(!Iterables.get(splittedKey, 0).equals("brooklyn")) {
                log.warn("Key doesn't start with 'brooklyn' prefix - Unsupported old-style property for '{}', it will be ignored.", new Object[] {key});
                sanitizedProperties.remove(key);
            } else if(key.startsWith("brooklyn.jclouds.") && !Iterables.get(splittedKey, 2).equals(provider)) {
                log.warn("Key doesn't start with 'brooklyn.jclouds.{PROVIDER} prefix' - Unsupported old-style property for '{}', it will be ignored.", new Object[] {key});
                sanitizedProperties.remove(key);
            } 
        }
        return sanitizedProperties;
    }
    
    private static Map<String, Object> sanitizeJcloudsProperties(Map<String, Object> properties) {
        Map<String, Object> sanitizedProperties = Maps.newHashMap(properties);
        Set<String> supportedProperties = Sets.newHashSet();
        Field[] fields = JcloudsLocation.class.getFields();
        for (Field field : fields) {
            if(field.getType().equals(ConfigKey.class)) {
                try {
                    String property = ((ConfigKey<?>)JcloudsLocation.class.getField(field.getName()).get(JcloudsLocation.class)).getName();
                    supportedProperties.add(property);
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }
        }
        for (ConfigKey<?> configKey : JcloudsLocation.SUPPORTED_TEMPLATE_BUILDER_PROPERTIES.keySet()) {
            supportedProperties.add(configKey.getName());
        }
        for (ConfigKey<?> configKey : JcloudsLocation.SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES.keySet()) {
            supportedProperties.add(configKey.getName());
        }
        for (String key : properties.keySet()) {
            if (!supportedProperties.contains(key)) {
                log.warn("Key '{}' is unsupported by JcloudsLocation.", new Object[] { key });
                sanitizedProperties.remove(key);
            }
        }
        return sanitizedProperties;
    }

}
