package brooklyn.entity.drivers.downloads;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadRequirement;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadTargets;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Function;
import com.google.common.base.Objects;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

public class DownloadSubstituters {

    private static final Logger LOG = LoggerFactory.getLogger(DownloadSubstituters.class);

    static {
        // TODO in Freemarker 2.4 SLF4J may be auto-selected and we can remove this;
        // for now, we need it somewhere, else we get j.u.l logging; 
        // since this is the main place it is used, let's do it here
        try {
            LOG.debug("Configuring Freemarker logging for Brooklyn to use SLF4J");
            freemarker.log.Logger.selectLoggerLibrary(freemarker.log.Logger.LIBRARY_SLF4J);
        } catch (Exception e) {
            LOG.warn("Error setting Freemarker logging: "+e, e);
        }
    }
    
    private DownloadSubstituters() {}
    
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
     * Additional substitution keys (and values) can be defined using {@link DownloadRequirement#getProperties()}; these
     * override the default substitutions listed above.
     */
    public static String substitute(DownloadRequirement req, String basevalue) {
        return substitute(basevalue, getBasicSubstitutions(req));
    }

    /** @deprecated since 0.6.0 use getBasicSubstitutions (method was misnamed) */
    @Deprecated
    public static Map<String,Object> getBasicSubscriptions(DownloadRequirement req) {
        return getBasicSubstitutions(req);
    }
    public static Map<String,Object> getBasicSubstitutions(DownloadRequirement req) {
        EntityDriver driver = req.getEntityDriver();
        String addon = req.getAddonName();
        Map<String, ?> props = req.getProperties();
        
        if (addon == null) {
            return MutableMap.<String,Object>builder()
                    .putAll(getBasicEntitySubstitutions(driver))
                    .putAll(props)
                    .build();
        } else {
            return MutableMap.<String,Object>builder()
                    .putAll(getBasicAddonSubstitutions(driver, addon))
                    .putAll(props)
                    .build();
        }
    }
    
    public static Map<String,Object> getBasicEntitySubstitutions(EntityDriver driver) {
        Entity entity = driver.getEntity();
        String type = entity.getEntityType().getName();
        String simpleType = type.substring(type.lastIndexOf(".")+1);
        String version = entity.getConfig(BrooklynConfigKeys.SUGGESTED_VERSION);
        
        String v2 = entity.getAttribute(Attributes.VERSION);
        if (v2!=null && !v2.equals(version)) {
            // Attributes.VERSION was deprecated in 0.5.0 but was preferred here without warning in 0.6.0
            // now warn on use of deprecated key when it is different
            LOG.warn("Using deprecated key "+Attributes.VERSION+", value "+v2+", which differs from the " +
            		"preferred key "+BrooklynConfigKeys.SUGGESTED_VERSION+", value "+version+"; old key will be retired shortly!");
            version = v2;
        }
        
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

    public static String substitute(String basevalue, Map<String,?> substitutions) {
        try {
            Configuration cfg = new Configuration();
            StringTemplateLoader templateLoader = new StringTemplateLoader();
            templateLoader.putTemplate("config", basevalue);
            cfg.setTemplateLoader(templateLoader);
            Template template = cfg.getTemplate("config");
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Writer out = new OutputStreamWriter(baos);
            template.process(substitutions, out);
            out.flush();
            
            return new String(baos.toByteArray());
        } catch (IOException e) {
            LOG.warn("Error processing template '"+basevalue+"'", e);
            throw Exceptions.propagate(e);
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
