package brooklyn.entity.drivers.downloads;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.entity.drivers.downloads.DownloadResolverRegistry.DownloadRequirement;
import brooklyn.entity.drivers.downloads.DownloadResolverRegistry.DownloadTargets;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;
import com.google.common.base.Objects;

import freemarker.template.TemplateException;

public class DownloadResolvers {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DownloadResolvers.class);

    private DownloadResolvers() {}
    
    /**
     * Converts the basevalue by substituting things in the form ${key} for values specific
     * to a given entity driver. The keys used are:
     * <ul>
     *   <li>driver: the driver instance (e.g. can do freemarker.org stuff like ${driver.osTag} to call {@code driver.getOsTag()})
     *   <li>entity: the entity instance
     *   <li>type: the fully qualified type name of the entity
     *   <li>simpletype: the unqualified type name of the entity
     *   <li>addon: the name of the add-on, or null if for the entity's main artifact
     *   <li>version: the version for this entity (or of the add-on), or not included if null
     * </ul>
     * 
     * Additional substitution keys (and values) can be defined using {@link DownloadRequirement.getProperties()}; these
     * override the default substitutions listed above.
     */
    public static String substitute(DownloadRequirement req, String basevalue) {
        return substitute(basevalue, getBasicSubscriptions(req));
    }

    public static Map<String,Object> getBasicSubscriptions(DownloadRequirement req) {
        EntityDriver driver = req.getEntityDriver();
        String addon = req.getAddonName();
        Map<String, ?> props = req.getProperties();
        
        if (addon == null) {
            return MutableMap.<String,Object>builder()
                    .putAll(DownloadResolvers.getBasicEntitySubstitutions(driver))
                    .putAll(props)
                    .build();
        } else {
            return MutableMap.<String,Object>builder()
                    .putAll(DownloadResolvers.getBasicAddonSubstitutions(driver, addon))
                    .putAll(props)
                    .build();
        }
    }
    
    public static Map<String,Object> getBasicEntitySubstitutions(EntityDriver driver) {
        Entity entity = driver.getEntity();
        String type = entity.getEntityType().getName();
        String simpleType = type.substring(type.lastIndexOf(".")+1);
        String version = entity.getAttribute(Attributes.VERSION);
        if (version == null) version = entity.getConfig(ConfigKeys.SUGGESTED_VERSION);
        
        return MutableMap.<String,Object>builder()
                .put("entity", entity)
                .put("driver", driver)
                .put("type", type)
                .put("simpletype", simpleType)
                .putIfNotNull("version", version)
                .build();
    }

    public static Map<String,Object> getBasicAddonSubstitutions(EntityDriver driver, String addon) {
        return MutableMap.<String,Object>builder()
                .putAll(getBasicEntitySubstitutions(driver))
                .put("addon", addon)
                .build();
    }

    public static String substitute(String basevalue, Map<String,?> subs) {
        try {
            return new Templater().processTemplate(basevalue, subs);
        } catch (TemplateException e) {
            throw new IllegalArgumentException("Failed to process driver download '"+basevalue+"'", e);
        }
    }

    public static Function<DownloadRequirement, DownloadTargets> substituter(Function<? super DownloadRequirement, String> basevalueProducer, Function<? super DownloadRequirement, ? extends Map<String,?>> subsProducer) {
        // FIXME Also need default subs (entity, driver, simpletype, etc)
        return new Substituter(basevalueProducer, subsProducer);
    }

    protected static class Substituter implements Function<DownloadRequirement, DownloadTargets> {
        private final Function<? super DownloadRequirement, String> basevalueProducer;
        private final Function<? super DownloadRequirement, ? extends Map<String,?>> subsProducer;
        
        Substituter(Function<? super DownloadRequirement, String> baseValueProducer, Function<? super DownloadRequirement, ? extends Map<String,?>> subsProducer) {
            this.basevalueProducer = checkNotNull(baseValueProducer, "basevalueProducer");
            this.subsProducer = checkNotNull(subsProducer, "subsProducer");
        }
        
        @Override
        public DownloadTargets apply(DownloadRequirement input) {
            String basevalue = basevalueProducer.apply(input);
            Map<String, ?> subs = subsProducer.apply(input);
            String result = (basevalue != null) ? substitute(basevalue, subs) : null;
            return (result != null) ? BasicDownloadTargets.builder().addPrimary(result).build() : BasicDownloadTargets.empty();
        }
        
        @Override public String toString() {
            return Objects.toStringHelper(this).add("basevalue", basevalueProducer).add("subs", subsProducer).toString();
        }
    }
}
