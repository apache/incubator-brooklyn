package brooklyn.entity.drivers;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.StringConfigMap;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.util.MutableMap;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Based on the contents of brooklyn properties, sets up rules for resolving where to
 * download artifacts from, for installing entities. 
 * 
 * By default, these rules override the DOWNLOAD_URL defined on the entities in code.
 * Global properties can be specified that apply to all entities. Entity-specific properties
 * can also be specified (which override the global properties for that entity type).
 * 
 * Below is an example of global properties that can be specified. The semicolon-separated list
 * of URLs will be tried in-order until one succeeds. The fallback url says to use that if all
 * other URLs fail (or no others are specified). The "substitutions" are available when processing 
 * the URL (see the "template" description below). 
 * <pre>
 * {@code
 * brooklyn.downloads.all.url=http://myurl1; http://myurl2
 * brooklyn.downloads.all.fallbackurl=http://myurl3
 * brooklyn.downloads.all.substitutions.mykey1=myval1
 * }
 * </pre>
 * 
 * Similarly, entity-specific properties can be defined. All "global properties" will also apply
 * to this entity type, unless explicitly overridden.
 * <pre>
 * {@code
 * brooklyn.downloads.entity.TomcatServer.url=http://mytomcaturl1
 * brooklyn.downloads.entity.TomcatServer.fallbackurl=http://myurl3
 * brooklyn.downloads.entity.TomcatServer.substitutions.mykey2=myval2
 * }
 * </pre>
 * 
 * If no explicit URLs are supplied, then by default it will use the DOWNLOAD_URL attribute
 * of the entity  (if supplied), followed by the fallbackurl if that fails. 
 * 
 * A URL can be a "template", where things of the form ${mykey1} will be substituted for the value
 * of mykey1 provided for that entity. The freemarker template engine is used to convert URLs 
 * (see {@link http://freemarker.org}). For example, one could use the URL:
 * <pre>
 * {@code
 * http://repo.acme.com/${simpletype}-${version}.${driver.downloadSuffix!tar.gz}
 * }
 * </pre>
 * As well as additional substitutions defined in the properties, the following are available 
 * automatically for a template:
 * <ul>
 *   <li>entity: the {@link Entity} instance
 *   <li>driver: the {@link EntityDriver} instance being used for the Entity
 *   <li>simpletype: the unqualified name of the entity type
 *   <li>type: the fully qualified name of the entity type
 *   <li>version: the version number of the entity to be installed
 * </ul>
 */
public class DownloadPropertiesResolver implements Function<EntityDriver, List<String>> {
    
    /* TODO: I thought about adding the following (but it's currently not true):
     * If an empty URL is supplied (e.g. {@code brooklyn.downloads.all.url=}) then it disables use 
     * of the entity's DOWNLOAD_URL attribute, going straight to the fallbackurl or entity-specific
     * overrides. For example, an enterprise may use this to ensure that entities never go to the
     * public internet during installation.
     * 
     * The problem is that the DownloadPropertiesResolver will return an empty list of URLs if all.url 
     * is empty, so it will automatically go on to the next resolver. We could instead encourage use of
     * something like:
     *   brooklyn.downloads.all.url=DISABLED
     * so that everything failed, trying to download a resource at the url"DISABLED"
     */

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DownloadPropertiesResolver.class);

    public static final String DOWNLOAD_CONF_PREFIX = "brooklyn.downloads.";

    private final StringConfigMap config;

    public DownloadPropertiesResolver(StringConfigMap config) {
        this.config = config;
    }
    
    public List<String> apply(EntityDriver driver) {
        List<Rule> rules = generateRules();
        for (Rule rule : rules) {
            if (rule.matches(driver)) {
                List<String> result = rule.resolve(driver);
                if (result != null && !result.isEmpty()) return result;
            }
        }
        
        return null;
    }
    
    /**
     * Produces a set of URL-generating rules, based on the brooklyn properties. These
     * rules will be applied in-order until one of them returns a non-empty result.
     */
    private List<Rule> generateRules() {
        List<Rule> result = Lists.newArrayList();
        Map<String, String> subconfig = filterAndStripPrefix(config.asMapWithStringKeys(), DOWNLOAD_CONF_PREFIX);

        // If exists, use things like:
        //   brooklyn.downloads.all.fallbackurl=...
        //   brooklyn.downloads.all.url=...
        //   brooklyn.downloads.all.substitutions.foo=...
        // But only if not overridden by more entity-specify value
        Map<String, String> forall = filterAndStripPrefix(subconfig, "all.");
        String fallbackUrlForAll = forall.get("fallbackurl");
        String urlForAll = forall.get("url");
        Map<String,String> substitutionsForAll = filterAndStripPrefix(forall, "substitutions.");
        
        // If exists, use things like:
        //   brooklyn.downloads.entity.JBoss7Server.url=...
        //   brooklyn.downloads.JBoss7Server.substitutions.foo=...
        Map<String, String> forSpecificEntities = filterAndStripPrefix(subconfig, "entity.");
        Map<String, Map<String,String>> splitBySpecificEntity = splitByPrefix(forSpecificEntities);
        for (Map.Entry<String, Map<String,String>> entry : splitBySpecificEntity.entrySet()) {
            String entityType = entry.getKey();
            Map<String, String> forentity = entry.getValue();
            String urlForEntity = forentity.get("url");
            if (urlForEntity == null) urlForEntity = urlForAll;
            String fallbackUrlForEntity = forentity.get("fallbackurl");
            if (fallbackUrlForEntity == null) fallbackUrlForEntity = fallbackUrlForAll;
            Map<String,String> substitutionsForEntity = MutableMap.<String, String>builder()
                    .putAll(substitutionsForAll)
                    .putAll(filterAndStripPrefix(forentity, "substitutions."))
                    .build();
            
            result.add(new EntitySpecificRule(entityType, urlForEntity, fallbackUrlForEntity, substitutionsForEntity));
        }

        if (!forall.isEmpty()) {
            result.add(new UniversalRule(urlForAll, fallbackUrlForAll, substitutionsForAll));
        }
        
        return result;
    }

    /**
     * Returns a sub-map of config for keys that started with the given prefix, but where the returned
     * map's keys do not include the prefix.
     */
    private static Map<String,String> filterAndStripPrefix(Map<String,?> config, String prefix) {
        Map<String,String> result = Maps.newLinkedHashMap();
        for (Map.Entry<String,?> entry : config.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix)) {
                Object value = entry.getValue();
                result.put(key.substring(prefix.length()), (value == null) ? null : value.toString());
            }
        }
        return result;
    }
    
    /**
     * Splits the map up into multiple maps, using the key's prefix up to the first dot to 
     * tell which map to include it in. This prefix is used as the key in the map-of-maps, and
     * is omitted in the contained map.
     * 
     * For example, given [a.b:v1, a.c:v2, d.e:v3], it will return [ a:[b:v1, c:v2], d:[e:v3] ]
     */
    private static Map<String,Map<String,String>> splitByPrefix(Map<String,String> config) {
        Map<String,Map<String,String>> result = Maps.newLinkedHashMap();
        
        for (Map.Entry<String,String> entry : config.entrySet()) {
            String key = entry.getKey();
            String keysuffix = key.substring(key.indexOf(".")+1);
            String keyprefix = key.substring(0, key.length()-keysuffix.length()-1);
            String value = entry.getValue();
            
            Map<String,String> submap = result.get(keyprefix);
            if (submap == null) {
                submap = Maps.newLinkedHashMap();
                result.put(keyprefix, submap);
            }
            submap.put(keysuffix, value);
        }
        return result;
    }
    
    /**
     * Resolves the download url, given an EntityDriver, with the following rules:
     * <ol>
     *   <li>If url is not null, split and trim it on ";" and use
     *   <li>If url is null, retrive entity's Attributes.DOWNLOAD_URL and use if non-null
     *   <li>If fallbackUrl is not null, split and trim it on ";" and use
     * <ol>
     * 
     * For each of the resulting Strings, transforms them (using freemarker syntax for
     * substitutions). Returns the list.
     */
    private static abstract class Rule {
        private final String url;
        private final String fallbackUrl;
        private final Map<String,String> additionalSubs;
        
        Rule(String url, String fallbackUrl, Map<String,String> additionalSubs) {
            this.url = url;
            this.fallbackUrl = fallbackUrl;
            this.additionalSubs = checkNotNull(additionalSubs, "additionalSubs");
        }
        
        abstract boolean matches(EntityDriver driver);
        
        List<String> resolve(EntityDriver driver) {
            List<String> result = Lists.newArrayList();
            List<String> baseurls = Lists.newArrayList();
            if (Strings.isEmpty(url)) {
                String defaulturl = driver.getEntity().getAttribute(Attributes.DOWNLOAD_URL);
                if (defaulturl != null) baseurls.add(defaulturl);
            } else {
                String[] parts = url.split(";");
                for (String part : parts) {
                    if (!part.isEmpty()) baseurls.add(part.trim());
                }
            }
            if (fallbackUrl != null) {
                String[] parts = fallbackUrl.split(";");
                for (String part : parts) {
                    if (!part.isEmpty()) baseurls.add(part.trim());
                }
            }

            for (String baseurl : baseurls) {
                result.add(DownloadResolvers.substitute(driver, baseurl, Functions.constant(additionalSubs)));
            }
            return result;
        }
    }

    /**
     * Rule for generating URLs that applies to all entities, if a more specific rule 
     * did not exist or failed to find a match.
     */
    private static class UniversalRule extends Rule {
        UniversalRule(String url, String fallbackUrl, Map<String,String> additionalSubs) {
            super(url, fallbackUrl, additionalSubs);
        }
        
        boolean matches(EntityDriver driver) {
            return true;
        }
    }
    
    /**
     * Rule for generating URLs that applies to only the entity of the given type.
     */
    private static class EntitySpecificRule extends Rule {
        private final String entityType;

        EntitySpecificRule(String entityType, String url, String fallbackUrl, Map<String,String> additionalSubs) {
            super(url, fallbackUrl, additionalSubs);
            this.entityType = checkNotNull(entityType, "entityType");
        }
        
        boolean matches(EntityDriver driver) {
            String actualType = driver.getEntity().getEntityType().getName();
            String actualSimpleType = actualType.substring(actualType.lastIndexOf(".")+1);
            return entityType.equalsIgnoreCase(actualSimpleType);
        }
    }
}
