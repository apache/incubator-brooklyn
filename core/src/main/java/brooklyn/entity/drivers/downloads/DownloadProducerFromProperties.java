package brooklyn.entity.drivers.downloads;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.StringConfigMap;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadRequirement;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadTargets;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
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
 * Below is an example of realistic configuration for an enterprise who have an in-house 
 * repository that must be used for everything, rather than going out to the public internet. 
 * <pre>
 * {@code
 * // FIXME Check format for including addonname- only if addonname is non-null?
 * // FIXME Use this in a testng test case
 * brooklyn.downloads.all.url=http://downloads.acme.com/brookyn/repository/${simpletype}/${simpletype}-${addon?? addon-}${version}.${fileSuffix!.tar.gz}
 * }
 * </pre>
 * 
 * To illustrate the features and variations one can use, below is an example of global 
 * properties that can be specified. The semicolon-separated list of URLs will be tried  in-order
 * until one succeeds. The fallback url says to use that if all other URLs fail (or no others are
 * specified). 
 * <pre>
 * {@code
 * brooklyn.downloads.all.url=http://myurl1/${simpletype}-${version}.tar.gz; http://myurl2/${simpletype}-${version}.tar.gz
 * brooklyn.downloads.all.fallbackurl=http://myurl3/${simpletype}-${version}.tar.gz
 * }
 * </pre>
 * 
 * Similarly, entity-specific properties can be defined. All "global properties" will also apply
 * to this entity type, unless explicitly overridden.
 * <pre>
 * {@code
 * brooklyn.downloads.entity.tomcatserver.url=http://mytomcaturl1/tomcat-${version}.tar.gz
 * brooklyn.downloads.entity.tomcatserver.fallbackurl=http://myurl2/tomcat-${version}.tar.gz
 * }
 * </pre>
 * 
 * Downloads for entity-specific add-ons can also be defined. All "global properties" will also apply
 * to this entity type, unless explicitly overridden.
 * <pre>
 * {@code
 * brooklyn.downloads.entity.nginxcontroller.addon.stickymodule.url=http://myurl1/nginx-stickymodule-${version}.tar.gz
 * brooklyn.downloads.entity.nginxcontroller.addon.stickymodule.fallbackurl=http://myurl2/nginx-stickymodule-${version}.tar.gz
 * }
 * </pre>
 * 
 * If no explicit URLs are supplied, then by default it will use the DOWNLOAD_URL attribute
 * of the entity  (if supplied), followed by the fallbackurl if that fails. 
 * 
 * A URL can be a "template", where things of the form ${version} will be substituted for the value
 * of "version" provided for that entity. The freemarker template engine is used to convert URLs 
 * (see <a href="http://freemarker.org>http://freemarker.org</a>). For example, one could use the URL:
 * <pre>
 * {@code
 * http://repo.acme.com/${simpletype}-${version}.${fileSuffix!tar.gz}
 * }
 * </pre>
 * The following substitutions are available automatically for a template:
 * <ul>
 *   <li>entity: the {@link Entity} instance
 *   <li>driver: the {@link EntityDriver} instance being used for the Entity
 *   <li>simpletype: the unqualified name of the entity type
 *   <li>type: the fully qualified name of the entity type
 *   <li>addon: the name of the entity add-on, or null if it's the core entity artifact
 *   <li>version: the version number of the entity to be installed (or of the add-on)
 * </ul>
 */
public class DownloadProducerFromProperties implements Function<DownloadRequirement, DownloadTargets> {
    
    /* FIXME: expose config for canContinueResolving.
     * ... then it uses only the overrides in the properties file. This, in combination with
     * setting something like {@code brooklyn.downloads.all.url=http://acme.com/repo/${simpletype}/${simpletype}-${version}.tar.gz},
     * allows an enterprise to ensure that entities never go to the public internet during installation.
     * 
     * But also need to override things like nginx downlaod url for the stick module and pcre.
     */

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DownloadProducerFromProperties.class);

    public static final String DOWNLOAD_CONF_PREFIX = "brooklyn.downloads.";

    private final StringConfigMap config;

    public DownloadProducerFromProperties(StringConfigMap config) {
        this.config = config;
    }
    
    public DownloadTargets apply(DownloadRequirement downloadRequirement) {
        List<Rule> rules = generateRules();
        BasicDownloadTargets.Builder result = BasicDownloadTargets.builder();
        for (Rule rule : rules) {
            if (rule.matches(downloadRequirement.getEntityDriver(), downloadRequirement.getAddonName())) {
                result.addAll(rule.resolve(downloadRequirement));
            }
        }
        
        return result.build();
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
        // But only if not overridden by more entity-specify value
        Map<String, String> forall = filterAndStripPrefix(subconfig, "all.");
        String fallbackUrlForAll = forall.get("fallbackurl");
        String urlForAll = forall.get("url");
        
        // If exists, use things like:
        //   brooklyn.downloads.entity.JBoss7Server.url=...
        Map<String, String> forSpecificEntities = filterAndStripPrefix(subconfig, "entity.");
        Map<String, Map<String,String>> splitBySpecificEntity = splitByPrefix(forSpecificEntities);
        for (Map.Entry<String, Map<String,String>> entry : splitBySpecificEntity.entrySet()) {
            String entityType = entry.getKey();
            Map<String, String> forentity = entry.getValue();
            String urlForEntity = forentity.get("url");
            if (urlForEntity == null) urlForEntity = urlForAll;
            String fallbackUrlForEntity = forentity.get("fallbackurl");
            if (fallbackUrlForEntity == null) fallbackUrlForEntity = fallbackUrlForAll;
            
            result.add(new EntitySpecificRule(entityType, urlForEntity, fallbackUrlForEntity));
            
            // If exists, use things like:
            //   brooklyn.downloads.entity.nginxcontroller.addon.stickymodule.url=...
            Map<String, String> forSpecificAddons = filterAndStripPrefix(forentity, "addon.");
            Map<String, Map<String,String>> splitBySpecificAddon = splitByPrefix(forSpecificAddons);
            for (Map.Entry<String, Map<String,String>> entry2 : splitBySpecificAddon.entrySet()) {
                String addonName = entry2.getKey();
                Map<String, String> foraddon = entry2.getValue();
                String urlForAddon = foraddon.get("url");
                if (urlForAddon == null) urlForAddon = urlForEntity;
                String fallbackUrlForAddon = foraddon.get("fallbackurl");
                if (fallbackUrlForEntity == null) fallbackUrlForAddon = fallbackUrlForEntity;
                
                result.add(new EntityAddonSpecificRule(entityType, addonName, urlForAddon, fallbackUrlForAddon));
            }
        }

        if (!forall.isEmpty()) {
            result.add(new UniversalRule(urlForAll, fallbackUrlForAll));
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
        
        Rule(String url, String fallbackUrl) {
            this.url = url;
            this.fallbackUrl = fallbackUrl;
        }
        
        abstract boolean matches(EntityDriver driver, String addon);
        
        DownloadTargets resolve(DownloadRequirement req) {
            EntityDriver driver = req.getEntityDriver();
            
            List<String> primaries = Lists.newArrayList();
            List<String> fallbacks = Lists.newArrayList();
            if (Strings.isEmpty(url)) {
                String defaulturl = driver.getEntity().getAttribute(Attributes.DOWNLOAD_URL);
                if (defaulturl != null) primaries.add(defaulturl);
            } else {
                String[] parts = url.split(";");
                for (String part : parts) {
                    if (!part.isEmpty()) primaries.add(part.trim());
                }
            }
            if (fallbackUrl != null) {
                String[] parts = fallbackUrl.split(";");
                for (String part : parts) {
                    if (!part.isEmpty()) fallbacks.add(part.trim());
                }
            }

            BasicDownloadTargets.Builder result = BasicDownloadTargets.builder();
            for (String baseurl : primaries) {
                result.addPrimary(DownloadSubstituters.substitute(req, baseurl));
            }
            for (String baseurl : fallbacks) {
                result.addFallback(DownloadSubstituters.substitute(req, baseurl));
            }
            return result.build();
        }
    }

    /**
     * Rule for generating URLs that applies to all entities, if a more specific rule 
     * did not exist or failed to find a match.
     */
    private static class UniversalRule extends Rule {
        UniversalRule(String url, String fallbackUrl) {
            super(url, fallbackUrl);
        }
        
        @Override
        boolean matches(EntityDriver driver, String addon) {
            return true;
        }
    }
    
    /**
     * Rule for generating URLs that applies to only the entity of the given type.
     */
    private static class EntitySpecificRule extends Rule {
        private final String entityType;

        EntitySpecificRule(String entityType, String url, String fallbackUrl) {
            super(url, fallbackUrl);
            this.entityType = checkNotNull(entityType, "entityType");
        }
        
        @Override
        boolean matches(EntityDriver driver, String addon) {
            String actualType = driver.getEntity().getEntityType().getName();
            String actualSimpleType = actualType.substring(actualType.lastIndexOf(".")+1);
            return addon == null && entityType.equalsIgnoreCase(actualSimpleType);
        }
    }
    
    /**
     * Rule for generating URLs that applies to only the entity of the given type.
     */
    private static class EntityAddonSpecificRule extends Rule {
        private final String entityType;
        private final String addonName;

        EntityAddonSpecificRule(String entityType, String addonName, String url, String fallbackUrl) {
            super(url, fallbackUrl);
            this.entityType = checkNotNull(entityType, "entityType");
            this.addonName = checkNotNull(addonName, "addonName");
        }
        
        @Override
        boolean matches(EntityDriver driver, String addon) {
            String actualType = driver.getEntity().getEntityType().getName();
            String actualSimpleType = actualType.substring(actualType.lastIndexOf(".")+1);
            return addonName.equals(addon) && entityType.equalsIgnoreCase(actualSimpleType);
        }
    }
}
