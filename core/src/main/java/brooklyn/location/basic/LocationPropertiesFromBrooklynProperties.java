package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigUtils;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

/**
 * The properties to use for locations, loaded from brooklyn.properties file.
 * 
 * @author aledsage
 **/
public class LocationPropertiesFromBrooklynProperties {

    private static final Logger LOG = LoggerFactory.getLogger(LocationPropertiesFromBrooklynProperties.class);

    @SuppressWarnings("deprecation")
    protected static final Map<String, String> DEPRECATED_KEYS_MAPPING = new DeprecatedKeysMappingBuilder(LOG)
            .camelToHyphen(LocationConfigKeys.DISPLAY_NAME)
            .camelToHyphen(LocationConfigKeys.PRIVATE_KEY_FILE)
            .camelToHyphen(LocationConfigKeys.PRIVATE_KEY_DATA)
            .camelToHyphen(LocationConfigKeys.PRIVATE_KEY_PASSPHRASE)
            .camelToHyphen(LocationConfigKeys.PUBLIC_KEY_FILE)
            .camelToHyphen(LocationConfigKeys.PUBLIC_KEY_DATA)
            .camelToHyphen(LocationConfigKeys.CALLER_CONTEXT)
            .build();
    
    /**
     * Finds the properties that apply to location, stripping off the prefixes.
     * 
     * Order of preference (in ascending order) is:
     * <ol>
     * <li>brooklyn.location.*
     * <li>brooklyn.location.provider.*
     * <li>brooklyn.location.named.namedlocation.*
     * </ol>
     * <p>
     * Converts deprecated hyphenated properties to the non-deprecated camelCase format. 
     */
    public Map<String, Object> getLocationProperties(String provider, String namedLocation, Map<String, ?> properties) {
        Map<String, Object> result = Maps.newHashMap();
        
        // named properties are preferred over providerOrApi properties
        if (!Strings.isNullOrEmpty(provider)) result.put("provider", provider);
        result.putAll(transformDeprecated(getGenericLocationSingleWordProperties(properties)));
        if (!Strings.isNullOrEmpty(provider)) result.putAll(transformDeprecated(getScopedLocationProperties(provider, properties)));
        if (!Strings.isNullOrEmpty(namedLocation)) result.putAll(transformDeprecated(getNamedLocationProperties(namedLocation, properties)));
        String brooklynDataDir = (String) properties.get(BrooklynConfigKeys.BROOKLYN_DATA_DIR.getName());
        if (brooklynDataDir != null && brooklynDataDir.length() > 0) {
            result.put("localTempDir", new File(brooklynDataDir));
        }
        
        return result;
    }

    /**
     * Gets the named provider (e.g. if using a property like {@code brooklyn.location.named.myfavourite=localhost}, then
     * {@code getNamedProvider("myfavourite", properties)} will return {@code "localhost"}).
     */
    protected String getNamedProvider(String namedLocation, Map<String, ?> properties) {
        String key = String.format("brooklyn.location.named.%s", namedLocation);
        return (String) properties.get(key);
    }
    
    /**
     * Returns those properties in the form "brooklyn.location.xyz", where "xyz" is any
     * key that does not contain dots. We do this special (sub-optimal!) filtering
     * because we want to exclude brooklyn.location.named.*, brooklyn.location.jclouds.*, etc.
     * We only want those properties that are to be generic for all locations.
     * 
     * Strips off the prefix in the returned map.
     */
    protected Map<String, Object> getGenericLocationSingleWordProperties(Map<String, ?> properties) {
        return getMatchingSingleWordProperties("brooklyn.location.", properties);
    }

    /**
     * Gets all properties that start with {@code "brooklyn.location."+scopeSuffix+"."}, stripping off
     * the prefix in the returned map.
     */
    protected Map<String, Object> getScopedLocationProperties(String scopeSuffix, Map<String, ?> properties) {
        checkArgument(!scopeSuffix.startsWith("."), "scopeSuffix \"%s\" should not start with \".\"", scopeSuffix);
        checkArgument(!scopeSuffix.endsWith("."), "scopeSuffix \"%s\" should not end with \".\"", scopeSuffix);
        String prefix = String.format("brooklyn.location.%s.", scopeSuffix);
        return ConfigUtils.filterForPrefixAndStrip(properties, prefix).asMapWithStringKeys();
    }

    /**
     * Gets all properties that start with the given {@code fullPrefix}, stripping off
     * the prefix in the returned map.
     */
    protected Map<String, Object> getMatchingProperties(String fullPrefix, Map<String, ?> properties) {
        return ConfigUtils.filterForPrefixAndStrip(properties, fullPrefix).asMapWithStringKeys();
    }

    /**
     * Gets all properties that start with either of the given prefixes. The {@code fullPreferredPrefix} 
     * properties will override any duplicates in {@code fullDeprecatedPrefix}. If there are any
     * properties that match the {@code fullDeprecatedPrefix}, then a warning will be logged.
     * 
     * @see #getMatchingProperties(String, Map)
     */
    protected Map<String, Object> getMatchingProperties(String fullPreferredPrefix, String fullDeprecatedPrefix, Map<String, ?> properties) {
        Map<String, Object> deprecatedResults = getMatchingProperties(fullDeprecatedPrefix, properties);
        Map<String, Object> results = getMatchingProperties(fullPreferredPrefix, properties);
        
        if (deprecatedResults.size() > 0) {
            LOG.warn("Deprecated use of properties prefix "+fullDeprecatedPrefix+"; instead use "+fullPreferredPrefix);
            return MutableMap.<String, Object>builder()
                    .putAll(deprecatedResults)
                    .putAll(results)
                    .build();
        } else {
            return results;
        }
    }

    /**
     * Gets all properties that start with the given {@code fullPrefix}, stripping off
     * the prefix in the returned map.
     * 
     * Returns only those properties whose key suffix is a single word (i.e. contains no dots).
     * We do this special (sub-optimal!) filtering because we want sub-scoped things 
     * (e.g. could want brooklyn.location.privateKeyFile, but not brooklyn.location.named.*). 
     */
    protected Map<String, Object> getMatchingSingleWordProperties(String fullPrefix, Map<String, ?> properties) {
        BrooklynProperties filteredProperties = ConfigUtils.filterForPrefixAndStrip(properties, fullPrefix);
        return ConfigUtils.filterFor(filteredProperties, Predicates.not(Predicates.containsPattern("\\."))).asMapWithStringKeys();
    }

    /**
     * Gets all single-word properties that start with either of the given prefixes. The {@code fullPreferredPrefix} 
     * properties will override any duplicates in {@code fullDeprecatedPrefix}. If there are any
     * properties that match the {@code fullDeprecatedPrefix}, then a warning will be logged.
     * 
     * @see #getMatchingSingleWordProperties(String, Map)
     */
    protected Map<String, Object> getMatchingSingleWordProperties(String fullPreferredPrefix, String fullDeprecatedPrefix, Map<String, ?> properties) {
        Map<String, Object> deprecatedResults = getMatchingSingleWordProperties(fullDeprecatedPrefix, properties);
        Map<String, Object> results = getMatchingSingleWordProperties(fullPreferredPrefix, properties);
        
        if (deprecatedResults.size() > 0) {
            LOG.warn("Deprecated use of properties prefix "+fullDeprecatedPrefix+"; instead use "+fullPreferredPrefix);
            return MutableMap.<String, Object>builder()
                    .putAll(deprecatedResults)
                    .putAll(results)
                    .build();
        } else {
            return results;
        }
    }

    protected Map<String, Object> getNamedLocationProperties(String locationName, Map<String, ?> properties) {
        checkArgument(!Strings.isNullOrEmpty(locationName), "locationName should not be blank");
        String prefix = String.format("brooklyn.location.named.%s.", locationName);
        return ConfigUtils.filterForPrefixAndStrip(properties, prefix).asMapWithStringKeys();
    }

    protected Map<String, Object> transformDeprecated(Map<String, ?> properties) {
        Map<String,Object> result = Maps.newLinkedHashMap();
        Map<String, String> deprecatedKeysMapping = getDeprecatedKeysMapping();
        
        for (Map.Entry<String,?> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (deprecatedKeysMapping.containsKey(key)) {
                String transformedKey = deprecatedKeysMapping.get(key);
                LOG.warn("Deprecated key {}, transformed to {}; will not be supported in future versions", new Object[] {key, transformedKey});
                result.put(transformedKey, value);
            } else {
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    protected Map<String,String> getDeprecatedKeysMapping() {
        return DEPRECATED_KEYS_MAPPING;
    }
}
