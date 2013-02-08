package brooklyn.entity.drivers;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.StringConfigMap;
import brooklyn.entity.basic.Attributes;
import brooklyn.util.MutableMap;

import com.beust.jcommander.internal.Lists;
import com.beust.jcommander.internal.Maps;
import com.google.common.base.Function;
import com.google.common.base.Functions;

/**
 * Supports things like:
 * 
//Applies to all downloads URLs (unless there is a more specific thing defined for a particular entity)
//The "driver.downloadSuffix" means to reflectively call driver.getDownloadSuffix()
brooklyn.downloads.all.url=http://repo.acme.com/${simpletype}-${version}.${driver.downloadSuffix}

//This gives a specific URL for download AS7
brooklyn.downloads.entity.JBoss7Server.url=http://repo.acme.com/as7/jboss-as-${version}.tar.gz

//This is the default MySqlNode url (so if didn't define "all.url" above, could miss out this first line)
//This replaces a specific substitution: ${driver.mirrorUrl} will get this value instead of calling driver.getMirrorUrl()
brooklyn.downloads.entity.MySqlNode.url=http://dev.mysql.com/get/Downloads/MySQL-5.5/mysql-${version}-${driver.osTag}.tar.gz/from/${driver.mirrorUrl}
brooklyn.downloads.entity.MySqlNode.substitutions.driver.mirrorUrl=http://www.mirrorservice.org/sites/ftp.mysql.com/

 * @param config
 * @return
 */
public class DownloadPropertiesResolver implements Function<EntityDriver, String> {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DownloadPropertiesResolver.class);

    public static final String DOWNLOAD_CONF_PREFIX = "brooklyn.downloads.";

    private final StringConfigMap config;

    public DownloadPropertiesResolver(StringConfigMap config) {
        this.config = config;
    }
    
    public String apply(EntityDriver driver) {
        List<Rule> rules = generateRules();
        for (Rule rule : rules) {
            if (rule.matches(driver)) {
                String result = rule.resolve(driver);
                if (result != null) return result;
            }
        }
        
        return null;
    }
    
    private List<Rule> generateRules() {
        List<Rule> result = Lists.newArrayList();
        Map<String, String> subconfig = filterAndStripPrefix(config.asMapWithStringKeys(), DOWNLOAD_CONF_PREFIX);
        
        Map<String, String> forall = filterAndStripPrefix(subconfig, "all.");
        String urlForAll = forall.get("url");
        Map<String,String> substitutionsForAll = filterAndStripPrefix(forall, "substitutions.");
        
        Map<String, String> forSpecificEntities = filterAndStripPrefix(subconfig, "entity.");
        Map<String, Map<String,String>> splitBySpecificEntity = splitByPrefix(forSpecificEntities);
        for (Map.Entry<String, Map<String,String>> entry : splitBySpecificEntity.entrySet()) {
            String entityType = entry.getKey();
            Map<String, String> forentity = entry.getValue();
            String urlForEntity = forentity.get("url");
            if (urlForEntity == null) urlForEntity = urlForAll;
            Map<String,String> substitutionsForEntity = MutableMap.<String, String>builder()
                    .putAll(substitutionsForAll)
                    .putAll(filterAndStripPrefix(forentity, "substitutions."))
                    .build();
            
            result.add(new EntitySpecificRule(entityType, urlForEntity, substitutionsForEntity));
        }

        if (!forall.isEmpty()) {
            result.add(new UniversalRule(urlForAll, substitutionsForAll));
        }
        
        return result;
    }
    
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
    
    private static abstract class Rule {
        private final String url;
        private final Map<String,String> additionalSubs;
        
        Rule(String url, Map<String,String> additionalSubs) {
            this.url = url;
            this.additionalSubs = checkNotNull(additionalSubs, "additionalSubs");
        }
        
        abstract boolean matches(EntityDriver driver);
        
        String resolve(EntityDriver driver) {
            String baseurl = (url == null) ? driver.getEntity().getAttribute(Attributes.DOWNLOAD_URL) : url;
            if (baseurl == null) return null;
            return DownloadResolvers.substitute(driver, baseurl, Functions.constant(additionalSubs));
        }
    }
    
    private static class UniversalRule extends Rule {
        UniversalRule(String url, Map<String,String> additionalSubs) {
            super(url, additionalSubs);
        }
        
        boolean matches(EntityDriver driver) {
            return true;
        }
    }
    
    private static class EntitySpecificRule extends Rule {
        private final String entityType;

        EntitySpecificRule(String entityType, String url, Map<String,String> additionalSubs) {
            super(url, additionalSubs);
            this.entityType = checkNotNull(entityType, "entityType");
        }
        
        boolean matches(EntityDriver driver) {
            String actualType = driver.getEntity().getEntityType().getName();
            String actualSimpleType = actualType.substring(actualType.lastIndexOf(".")+1);
            return entityType.equalsIgnoreCase(actualSimpleType);
        }
    }
}
